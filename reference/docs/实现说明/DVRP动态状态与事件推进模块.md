# DVRP 动态状态与事件推进模块

> 状态：已完成（动态模块；不包含阶段 BP）。
>
> 最后更新：2026-10-15（更新下游RMP/pricing入口；动态代码及原验证记录不变）。
>
> 语义依据：[数学模型 v4](../模型和推导/DVRP%20数学模型-v4.md)。

本文保留能力、接口和验证摘要。按业务顺序理解每个方法及时间100/130的例子，请读[动态调度流程与方法导读](DVRP动态调度流程与方法导读.md)。

## 1. 当前能做什么

接收 reader 的八字段数据，生成动态事件和 `StageProblem`；接收手工、已实现的小实例对照或未来BP给出的整数路线，校验后推进车辆，累计实际距离，直到下一事件或全部返场。

`dvrp.exe` 的 CLI 仍只读取实例。动态模块已编入该目标，但尚未在 `main.cpp` 接入自动求解；目前通过 `dvrp_dynamic_test` 独立运行。不能据此声称已经完成 DVRP 的 BP 求解。

## 2. 文件职责

路径均相对 `packages_dvrp/application/dvrp/`：

| 文件 | 职责 |
| --- | --- |
| `src/dynamic/include/dynamic_data.hpp` | 阶段、起点、路线、车辆、状态和记录等值类型 |
| `src/dynamic/include/dynamic_controller.hpp` | 动态控制器接口与私有执行计划 |
| `src/dynamic/src/dynamic_controller.cpp` | 事件、时间轨迹、状态投影、结果校验和距离结算 |
| `test/dynamic_test.cpp` | 手工路线验收、60组事件分割检查及 reader 集成 |
| `CMakeLists.txt` | 编译动态源文件，注册动态测试；该动态测试目标不链接 solver，可选阶段对照目标另行链接 |

原版 `packages/`、DVRP reader 与输入格式未改变。距离函数从原 VRPTW 初始化逻辑适配，保留来源与 GPL-3.0 标注。

## 3. 数据怎样流转

```text
reader 的 info_vertex、capacity、instanceName
  -> DynamicController（复制输入，拥有实际状态和执行计划）
  -> StageProblem（当前客户、各起点、独立返回车场、固定成本）
  -> 手工路线 / 小实例MIP对照 / 未来阶段BP
  -> StageSolution（阶段编号、状态、每条路线的起点和客户顺序）
  -> 校验并接续执行 -> 下一事件
```

阶段快照只包含当前待规划客户，不暴露未来客户。客户保持原输入 ID；`startKey=0` 表示不限车辆的原始起点，正数表示稳定的物理车辆 ID，两者不是 pricing 数组编号。返回车场是独立字段。

`state()`、`buildStageProblem()`、`records()` 均返回值副本；下一阶段不修改调用方保存的旧快照。下游如何转成连续数组、根 RMP 或 bucket graph，仍须单独适配。

## 4. 固定调用顺序

| 方法 | 使用条件和作用 |
| --- | --- |
| 构造控制器 | 从时间0初始化；复制 reader 数据，不接收车辆数上限 |
| `buildStageProblem()` | 当前阶段尚未接收结果时，生成独立阶段快照 |
| `acceptSolution(solution)` | 校验 stageId、客户唯一覆盖、外部起点唯一选择、容量、时间窗及 H |
| `nextEventTime()` | 查询下一披露事件；无事件或停止时返回空值 |
| `advanceTo(nextTime)` | 接收本阶段路线后，只能推进到下一事件，不能跳过请求 |
| `finish()` | 最后事件已接收路线后，执行至全部返场并结清距离 |

初始无公开客户时也要接收空路线结果；若还有未来披露，不能提前结束。非法路线或陈旧结果抛异常，不改变实际状态、车辆编号或阶段记录。

## 5. 已实现的业务规则

- 披露时刻严格去重排序，同刻请求只形成一个阶段；先结算执行、完成服务，再释放请求。
- 前往、等待或服务中的客户属于不可转交的承诺任务；阶段客户 = 已公开 − 已完成 − 当前承诺。
- 恰好在事件时刻完成服务时，保留该客户位置的外部起点，不沿旧计划继续出发；接收新结果后立即出发。
- 原始车辆于当前阶段时间立即发车，允许提前到客户处等待；同刻披露先纳入规划，未出发车辆不生成外部起点。
- 外部起点在承诺客户位置，时间为服务完成时刻；剩余容量 = Q − 完成承诺后的载货量。投影不提前收货、不重复扣减。
- 每个外部起点必须选择一条后续路线，可为空客户直接返场，但也检查 H；原始起点路线不能为空。
- 返场车辆轨迹固定；已返场车辆不复用。新原始路线分配新车辆编号，不受 TXT `NUMBER` 限制。
- 时间窗约束服务开始而非完成时刻；所有路线必须满足车场 `DUE_DATE` 定义的 H（沿用可行性容差）。
- 最后事件后仍执行所有剩余服务和返场，不能在最后一次求解后直接结束。

例如 Q=100，车已载40，正前往需求20的 A，预计130完成服务；在100和110两次事件中，外部起点均为 A、130、剩余40，实际载货均仍为40。到130完成后实际载货才变为60。

## 6. 数值与距离

`routeOptDistance` 与原 VRPTW 一致：平方距离转 `float` 后开方，再带 `1e-6` 容差向下截断到一位小数。该弧距离同时作为成本和旅行时间。

途中位置按原弧端点和行驶进度插值，仅供位置与固定成本统计，不能用于改道。部分弧不再次取整，等待和服务不计距离。

阶段记录区分固定剩余成本、后续路线成本、到下一事件实际距离。全局距离只累加实际执行部分，绝不相加各阶段完整计划成本。

服务/到达比较只用机器精度级误差处理加法尾差；披露时刻不按可行性容差合并。时间窗及 H 的可行性容差仍为 `1e-6`。

## 7. 求解状态与诊断

`Feasible` / `Optimal` 的整数路线通过校验后接收；本模块只验证可执行性，不验证最优性。路线成本和最早执行轨迹由动态层重算，不接收任意延迟发车的时间安排。

`Infeasible`、两种 `TimeLimit` 和 `Error` 都停止仿真；超时即使有可行路线也不执行，不沿用旧计划、不报告成功。`records()` 保存当时阶段、收到的状态/路线和原因；失败记录的成本未验证，以 `executed=false` 区分。

记录目前保存在内存，调用方可取值；没有磁盘恢复或自动 JSON 导出。动态模块本身不管理 solver 计时；已完成的可选对照工具负责向CPLEX传时限，生产BP的时限联动仍待接入。

## 8. 构建与测试

在仓库根目录运行：

```powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp -DCMAKE_BUILD_TYPE=Release
cmake --build build/dvrp --config Release -j 8
ctest --test-dir build/dvrp -C Release --output-on-failure
```

查看 reader → 动态执行的小实例记录：

```powershell
.\build\dvrp\Release\dvrp_dynamic_test.exe reader .\packages_dvrp\application\dvrp\test\fixtures\minimal_valid.txt
```

该例按手工路线执行时间0、25、100三个阶段，最终距离89.2；不是优化结果。测试路线均未使用 `-u` 或隐藏上界。

Release 和 Debug 均为25项 CTest 通过：原 reader/CLI 9项（含434实例读取）及动态16项。Release 测试不依赖会被关闭的 C++ `assert`。

| 动态测试名后缀 | 验证重点 |
| --- | --- |
| events、empty、reader | 同刻事件、无未来信息、输入所有权、空初始阶段、端到端手工路线 |
| commitment、loaded | 行驶/等待/服务、同刻完成、实际与投影容量 |
| return、external、external_deadline | 固定返场、不复用、独立起点、直接返场也受 H 限制 |
| distance、partition | 弧插值、不重复计费、60组事件分割下的距离守恒 |
| validation、input | 非法/重复/缺失客户、起点、容量、输入和错误结果原子拒绝 |
| time、arithmetic | 时间窗、H、零时间链、浮点尾差与严格披露时间 |
| stop、stop_later | 失败状态区分、已有解也停止、保留阶段信息 |

Java 旧实现的途中改道、返场复用、仅累计完整弧距离与 v4 冲突，分别由 commitment / return / distance 验证预期差异。本轮核对了 Java 源码，未运行 Java 二进制对照；参考目录未提供独立 Maven/Gradle 构建入口。

静态回归（原版现有 Release、CPLEX、缓存 `LABEL_ASSIGN_OVERRIDE=320000`，均不带 `-u`）：C204 返回0，UB=LB=588.1，约95.6秒，并独立核验100客户覆盖、容量、时间窗及返场。R204 未通过：最后记录445.85秒，随后在 labeling 的扩容警告后退出 `-1073741819`（`0xC0000005`，访问冲突）；该时间不是最终总耗时。原版源码未改，运行程序不包含动态模块，不能把此结果归为动态测试通过或声称静态回归全部通过。之后执行 `cmake --build packages/application/cvrp/build --config Release -j 8` 也构建成功；上述运行记录来自该次增量构建前的程序，未再次长跑 R204。

复现：在 `packages/application/cvrp` 下执行 `./bin/Release/cvrp.exe ./instance/vrptw/solomon-100/c204.txt` 或 `r204.txt`。本轮日志位于忽略提交的 `build/dvrp/static-c204.log`、`static-r204.log`。原版 R204 的扩容异常需另行诊断，不在本模块中改动原算法。

## 9. 下游边界

[小实例集合划分对照求解器](DVRP小实例集合划分对照求解器.md)已完成，通过相同 `StageProblem` / `StageSolution` 接口在测试中自动连跑MIP阶段，并由本模块重验路线。[多起点RMP](DVRP多起点RMP模块.md)及[双版本pricing](DVRP多起点定价模块.md)已实现并完成测试接线，但尚无生产根列生成控制器或阶段BP；动态生产代码没有因此修改。下一步确定正式根列生成接口；当前 `dvrp.exe` 仍不提供自动求解CLI，也未做大实例性能优化。
