# DVRP 动态调度流程与方法导读

> 状态：已完成（当前动态模块的代码导读；不表示阶段 BP 已实现）。
>
> 最后更新：2026-10-06。
>
> 配套入口：[动态状态与事件推进模块](DVRP动态状态与事件推进模块.md)。构建、验证记录和长期限制由该文档维护。

本文面向没有参与 C++ 编写的读者，按“业务逻辑 → 实际方法 → 数据变化”解释当前实现，不逐行翻译源码。

核心逻辑：保留已经执行的历史和当前不可改变的承诺任务，重新安排其余客户。动态模块负责执行与检查路线，不负责自动求出路线。

## 1. 先认清文件和数据对象

| 源码入口 | 负责什么 |
| --- | --- |
| [dynamic_data.hpp](../../../packages_dvrp/application/dvrp/src/dynamic/include/dynamic_data.hpp) | 定义阶段、起点、路线、车辆状态和记录等数据对象 |
| [dynamic_controller.hpp](../../../packages_dvrp/application/dvrp/src/dynamic/include/dynamic_controller.hpp) | 声明公开调用方法，保存内部执行计划 |
| [dynamic_controller.cpp](../../../packages_dvrp/application/dvrp/src/dynamic/src/dynamic_controller.cpp) | 实现本文讲解的方法 |
| [dynamic_test.cpp](../../../packages_dvrp/application/dvrp/test/dynamic_test.cpp) | 用手工路线调用这些接口，检查状态和距离 |

可以把数据对象理解为 Java 的数据类；`DynamicController` 则是持有数据并执行操作的控制器。

| 对象 | 含义与归属 |
| --- | --- |
| `DynamicState` | 控制器内部的当前实际状态：时间、客户集合、车辆状态、实际累计距离 |
| `VehiclePlan` | 控制器内部的车辆时间表：已保留的执行前缀、后续访问和返场安排 |
| `StageProblem` | 从当前状态生成、交给阶段求解器的快照，不是根节点或 LP 模型 |
| `StageSolution` | 调用方提交的阶段编号、求解状态及路线选择；每条路线只有起点身份和客户顺序 |
| `RouteExecution` / `StageRecord` | 动态层独立重算的路线时间表，以及本阶段输入、结果和成本记录 |

控制器复制 reader 数据并拥有自己的状态；返回给调用方的阶段、状态和记录是值副本，不会被下一阶段原地改写。

## 2. 总调用链：谁负责调用谁

下面是调用方组织这些接口的顺序。当前由测试驱动；[main.cpp](../../../packages_dvrp/application/dvrp/src/main.cpp) 仍只读取并打印摘要，没有这条自动求解循环。

```text
DVRP_ReadDataController(...)：读入原始实例
  ↓
DynamicController(...)：初始化，内部调用 refreshState(true)
  ↓
buildStageProblem()：返回当前阶段问题
  ↓
阶段求解器【尚未接入；当前测试手工提供 StageSolution】
  ↓
acceptSolution(...)
  ├─ 检查结果状态；不可行、超时或错误时停止，不进入下面的推进
  ├─ validateRoute()：逐条校验，并重算时间表
  ├─ 检查整体覆盖，保留执行前缀并替换后续计划
  └─ refreshState(false)：按新计划更新当前状态
  ↓
nextEventTime()
  ├─ 有下一事件 → advanceTo(time)
  │                 ├─ settleDistance(time)
  │                 └─ refreshState(true)
  │                 然后回到 buildStageProblem()
  └─ 无下一事件且仍在运行 → finish()
                             ├─ settleDistance(最终返场时间)
                             └─ refreshState(false)
```

外部调用初始化、生成阶段、接收结果、查询事件、推进和收尾；`refreshState`、`validateRoute`、`settleDistance` 是内部辅助方法，不由下游求解器直接调用。

## 3. 按业务顺序理解每一步

### 3.1 读取实例：`DVRP_ReadDataController(...)`

构造读取器时读取并校验 TXT，填充调用方的 `info_vertex`、容量、实例名等变量。八列中最后一列是披露时间；详细格式见[数据读取模块](DVRP数据读取模块.md)。

这一步只有原始资料，还没有动态车辆或阶段问题。文件中的车辆数只作为元数据，不形成动态阶段的车队上限。

### 3.2 初始化：`DynamicController(...)`

接收 `info_vertex`、容量和实例名，复制数据并检查基本合法性；将时间0与客户披露时间严格排序、去重，建立事件表。初始没有车辆计划，累计实际距离为0。

内部调用 `refreshState(true)`，确定时间0已经公开的客户。多个同刻披露合为一个阶段；到达、开始服务、完成服务本身不额外触发重规划。

如果时间0没有公开客户，调用方仍需提交空路线结果，再推进到下一事件，不能跳过阶段接收过程。

### 3.3 生成求解输入：`buildStageProblem()`

要求仿真仍在运行、当前阶段尚未接收结果。读取 `DynamicState`，返回新的 `StageProblem`，不改变时间或车辆计划。

- 客户：仅当前已公开、尚未完成、也不属于当前承诺任务的客户；不暴露未来客户。
- 原始起点：`startKey=0`，当前时刻出发，完整容量，可启用不限数量的新车。
- 外部起点：由行驶、等待、服务或同刻刚完成的车辆生成；位置为承诺客户或刚完成客户的位置，时间为完成承诺后的可用时间，容量为完成承诺后的剩余容量。
- 共同返回车场：独立字段，位置与原始车场相同，最晚返回时刻为车场 `DUE_DATE`，记为 H。
- 固定成本：完成当前承诺的剩余行驶距离，以及正在返场车辆的剩余返场距离。

返场中的车辆不生成外部起点；已返场车辆不重新加入待命池。外部起点的正整数 key 是稳定车辆身份，不是客户 ID 或未来 pricing 的数组下标。

### 3.4 规划路线：阶段求解接口尚未实现

下游接收 `StageProblem`，应返回 `StageSolution`：阶段编号、状态、每条路线的 `startKey` 和客户访问顺序。目前测试手工给这些结果。

动态模块不调用 RMP、标签或分支。后续求解器还需适配编号、资源、根节点与多起点模型，不能把现有快照直接当作已适配的 RouteOpt 内部对象。

### 3.5 接收结果：`acceptSolution(solution)`

先取得当前阶段快照，检查结果的 `stageId`，避免把旧阶段路线用在新阶段。对于 `Infeasible`、两种超时状态和 `Error`，保存诊断记录并停止；超时已有整数解也不执行。真实计时与中止求解尚未接入。

对于 `Feasible` 或 `Optimal`，逐条调用 `validateRoute()`，然后检查整体客户恰好覆盖一次、每个外部起点恰好使用一次。这里只验证可执行性，不证明最优性，也不自行证明不可行。

`validateRoute()` 从起点的 `startTime` 和位置开始传播：行驶得到到达时刻，提前到达则等待到 `readyTime`，随后服务；检查服务开始不晚于 `dueDate`、需求不超过起点剩余容量，最后检查返场不晚于 H。原始路线不能为空，外部路线允许空客户直接返场，但仍检查 H。

时间表和真实路线成本由动态层重算，不由求解器任意指定延迟发车时间；服务完成后立即出发。`routeOptDistance()` 提供与原版 VRPTW 一致的弧成本和旅行时间。

全部验证通过后，才一次性替换车辆计划：原始路线分配新车辆编号；已有车辆保留已执行部分和当前承诺，只删除并替换可调整的后续部分。固定返场车辆的计划保持不变。

最后保存阶段记录，令 `awaitingSolution=false`，调用 `refreshState(false)` 按新路线更新当前状态。非法路线或陈旧结果抛异常，不会部分派车或修改记录；这与收到明确失败状态后停止仿真不同。

### 3.6 查询与推进：`nextEventTime()`、`advanceTo(time)`

`nextEventTime()` 只返回下一披露时刻，不推进时间；停止或没有后续事件时返回空值。空值本身不表示仿真成功，还需要检查状态。

`advanceTo(time)` 要求当前阶段已经接收方案，而且参数必须恰好是下一个事件，不能跳过请求。内部先调用 `settleDistance(time)` 结算实际距离并更新时间，再增加阶段编号、令 `awaitingSolution=true`，最后调用 `refreshState(true)`。

`awaitingSolution` 表示阶段是否等待方案，不表示车辆是否等待时间窗，也不表示等待某辆车完成服务。

### 3.7 判断现状：`refreshState(holdCompletionAtEvent)`

该方法不推进时间，而是用“当前时间 + 车辆执行时间表”重算现状，不需要逐秒仿真。它先识别已经完成的访问，再找第一个尚未完成的承诺客户，计算实际位置、完成时间、实际已占容量、完成承诺后的剩余容量和固定剩余距离。

所有访问结束后，根据返场时间区分 `Returning` 和 `Finished`。实际已占容量从已完成访问重新计算，不在旧值上反复加需求，因此多次披露不会重复扣容量。

完成车辆判断后，再取披露时间不晚于当前时间的客户，得到“已公开 − 已完成 − 当前承诺”的待规划集合。旧计划中尚未成为承诺的后续客户，也会重新进入规划，并非只安排新客户。

参数只控制执行边界：进入事件时用 `true`，若客户恰好此刻首次被处理为完成，就留在该客户位置先重规划，不执行旧计划下一段；接收新方案后用 `false`，按新计划立即继续。同刻判断处理浮点运算尾差，但披露事件本身严格区分。

### 3.8 最终收尾：`finish()`

要求仍在运行、没有未来事件、最后阶段已接收方案。取所有车辆最晚返场时间，调用 `settleDistance()` 和 `refreshState(false)`，确认全部客户完成、全部启用车辆返场，才标记 `Completed`。

最终通过 `state()` 读取累计实际距离，通过 `records()` 查看阶段输入、结果与执行记录，通过 `eventTimes()` 查看事件表；均返回副本。记录目前在内存中，不是自动落盘的日志或恢复文件。

## 4. 贯穿例子：时间100，完成客户3的时间130

假设 A 车编号为1，Q=100，已完成客户占用40，正在前往需求20的客户3，预计130完成。旧计划是“车场 → 3 → 5 → 8 → 车场”，时间100又披露客户9。

调用 `advanceTo(100)` 后，`refreshState(true)` 记录的是100时刻的实际状态；再调用 `buildStageProblem()`，得到用于规划的未来起点。

| 对比项 | 实际状态 `DynamicState` 中的 A 车 | 阶段问题 `StageProblem` 中的 A 车起点 |
| --- | --- | --- |
| 时间 | `currentTime=100`，`availableTime=130` | `startTime=130` |
| 位置 | 正在去客户3的弧上，`phase=Driving` | 客户3的位置 |
| 客户3 | `committedCustomerId=3`，还没有完成 | 不进入本阶段客户覆盖集合 |
| 容量 | `actualLoad=40` | `remainingCapacity=100-40-20=40` |
| 身份 | `vehicleId=1` | `startKey=1`，不是新车 |

本阶段可重新安排客户5、8、9；A 车在100至130之间的承诺不可改变。如果新方案让 A 在3之后去9，`acceptSolution()` 保留去往并服务3的原时间表，再接上130出发去9的后续路线；5、8必须在整体方案中另有安排。

如果110又有披露，客户3仍是承诺，不能标成已完成，外部起点仍是客户3、130、剩余40。这里是“提前规划完成后的行动”，不是“提前实际完成服务”。

如果130恰好有披露，先完成3并更新已占容量为60，再以客户3、130为外部起点重规划；如果130没有披露，就按已接受的路线继续出发，不额外启动一个阶段。

## 5. 距离结算对应哪些方法

调用关系为 `settleDistance()` → `travelledUntil()` → `travelledOnArc()`：

- `travelledOnArc()`：按原弧长度、出发/到达时间，计算截至某时刻已经走过的部分；出发前为0，到达后为完整弧长。
- `travelledUntil()`：加总一辆车的访问弧和返场弧，得到截至某时刻的累计距离。
- `settleDistance()`：用新旧时刻累计值之差求本区间增量，更新全局距离和当前阶段的 `actualDistanceToNextEvent`。

例如整弧长度1.4，行驶时间0至1.4，途中事件在0.15、0.55和1.10；各段距离为0.15、0.40、0.55、0.30，理论合计仍为1.4。保留的承诺弧始终使用原长度和时间表；不根据插值坐标重新计算分段弧长，也不对各段再次截断。

`interpolate()` 计算实际位置，只用于状态展示；外部起点始终取客户位置。固定剩余距离同样由原弧长度减去已走距离计算，不由插值坐标重新算距离。

`double` 加减仍可能产生微小浮点误差，不能保证位级相等；现有 `distance` 和 `partition` 测试以容差检查分段守恒。当前没有在结束时额外重算“全部实际完整弧之和”的独立生产校验，不把该建议描述成已实现。

`StageRecord.totalPlannedCost` 是当前固定剩余成本加新选路线成本，含未来计划；`cumulativeActualDistance` 只含已经走过的距离。不能把多个阶段的完整计划成本相加。

## 6. 阅读源码和验证的最小路径

先看 `dynamic_controller.hpp` 的公开方法，再按本文第2节阅读 `.cpp`；需要理解字段时查 `dynamic_data.hpp`，不用先掌握所有 C++ 语法。

| 想核对的行为 | `dynamic_test.cpp` 中的函数 |
| --- | --- |
| 行驶、等待、服务和同刻完成 | `commitmentAndLoad()` |
| 时间100/110/130的容量投影 | `loadedVehicleProjection()` |
| 返场固定、已结束车辆不复用 | `fixedReturnAndNoReuse()` |
| 跨事件部分弧与总距离 | `distancePartitionAndTail()`、`partitionProperty()` |
| 错误路线不部分改变状态 | `atomicValidation()` |
| reader 到事件推进的完整手工例子 | `readerIntegration()` |

实际运行命令和已有验证结果见[模块说明第8节](DVRP动态状态与事件推进模块.md#8-构建与测试)，不在本导读维护第二套记录。当前阶段求解器、自动 BP 循环、真实求解超时联动仍未接入；后续任务与确认顺序见[当前里程碑](../里程碑/M1-标准BP-DVRP.md)。
