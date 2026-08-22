# RouteOpt DVRP 开发约定（初版）

> 本文件是 `dvrp` 分支的初版项目上下文，记录当前代码事实、参考实现和待确认边界。它不是最终设计文档；在边界语义确认后应继续迭代。除非另有说明，本文中的路径均相对于仓库根目录。

## 目标与范围

本分支的目标是在现有 RouteOpt VRPTW 分支定价框架外增加 DVRP（Dynamic Vehicle Routing Problem）动态层。DVRP 的一次决策不是直接改变 VRPTW 的静态算法，而是：

1. 根据当前时间和上一阶段路线生成一个动态阶段实例；
2. 将等待车辆和执行中的车辆统一转换成多个可出发起点；
3. 对当前阶段尚未完成的客户求解一个多起点、带容量和时间窗的 VRPTW；
4. 运行到下一个动态事件，再更新状态并重复求解；
5. 汇总各阶段实际已经行驶的距离。

当前只允许在 `dvrp` 分支上开发。`reference/dvrp` 是 Java 参考实现和测试材料，不应被当作 C++ 生产代码直接编译或改写；`reference/docs/数学模型.md` 是模型依据。

## 用户确认的硬性要求

以下要求是 DVRP 适配工作的工程约束，后续设计和实现必须保持一致：

1. **独立入口**：DVRP 必须作为独立的应用入口或可执行目标存在，不得改变现有 CVRP/VRPTW 的默认入口、命令行语义、实例解析和求解流程。用户未选择 DVRP 时，原有 CVRP/VRPTW 行为应保持不变。
2. **最小输入扩展**：DVRP 使用与 Solomon VRPTW 完全相同的 `txt` 布局，只在每个节点数据行末尾增加 `DISCLOSURE_TIME`。DVRP 的解析、校验和错误提示仍在独立入口边界内完成，但应最大限度复用现有 VRPTW 读取逻辑，不改变静态入口的默认行为。
3. **先界定再接入**：实现前必须明确“首期需要实现的 DVRP 能力”、暂不实现的能力，以及它们如何映射到 RouteOpt 的现有框架。每个接入点都要有输入、输出、所有权和测试边界；不得以大范围修改现有模块的方式直接尝试接入。

DVRP 的独立入口可以复用 RouteOpt 的公共求解器、资源和列生成组件，但复用不等于共享静态应用的全局状态。任何需要支持多起点的修改，都必须证明不会破坏单车场 CVRP/VRPTW。

## DVRP 输入格式方向

DVRP 的生产输入采用现有 Solomon VRPTW `txt` 格式的最小扩展：`VEHICLE` 段、车辆数、容量、`CUSTOMER` 段和前 7 个节点字段全部保持不变，只在客户表标题和每个节点数据行末尾增加 `DISCLOSURE_TIME`。车场行的披露时间固定为 0，客户行取 Java Excel 的 `Time_Disclosure`。

实例文件不增加格式版本、目标函数、速度、舍入规则、动态程度或 solver 参数。上述算法和运行配置继续由代码、CMake 或命令行管理。原始实例不保存动态外部起点；外部起点在事件发生时由车辆状态生成。具体格式见 `reference/docs/DVRP实例格式V1.md`。

建议把 DVRP 实例放在独立目录（例如 `packages/application/dvrp/instance/`），与 `packages/application/cvrp/instance/` 下的 CVRP/VRPTW 实例分开；参考实例继续保留在 `reference/dvrp`，不作为生产构建输入。

## 首期实现边界和接入顺序

首期目标是建立一条可验证的最小闭环，而不是一次性迁移 Java reference 的全部加速策略：

1. **输入层**：新增 DVRP 专用 `txt` 读取器、数据模型和命令行入口；读取后进行完整校验，并输出规范化摘要。
2. **动态层**：实现动态事件排序、阶段快照、客户释放/完成判定、车辆状态推进、外部起点生成和跨阶段实际距离累计。
3. **阶段求解层**：先实现一个可测试的多起点 VRPTW 阶段求解器，明确每条列的起点身份、起点时间和剩余容量；客户覆盖、外部起点等式约束和原始车场车辆约束必须与数学模型一致。
4. **RouteOpt 接入层**：优先复用公共 solver、时间窗资源和列生成机制；只有在多起点语义有独立测试后，才逐项评估 `cuts`、`branching`、`enumeration`、`two-stage` 等目前依赖单车场的模块。暂未适配的模块必须显式关闭并记录原因。
5. **验证层**：用手工小实例和 Java reference 做阶段状态、路线可行性及总距离对照，再进行 Solomon/benchmark 集成测试。DVRP 的构建和运行必须有独立命令，不要求用户修改原有 CVRP/VRPTW 命令。

首期明确不承诺的内容包括：直接支持 Java reference 的 Excel 输入、一次性迁移全部 cuts/branching 加速策略、把动态事件字段加入静态 VRPTW 文件后由旧入口自动识别，以及通过已知上界或实例命名约定替代真正的 DVRP 状态建模。

## 参考材料

- 数学模型：`reference/docs/数学模型.md`
- Java 动态外层：`reference/dvrp/src/main/java/org/zzc/bpcsolver/algorithm/DVRPDynamicSolver.java`
- Java 状态更新：`reference/dvrp/src/main/java/org/zzc/bpcsolver/problem/update/DynamicStateUpdater.java`
- Java 动态实例：`reference/dvrp/src/main/java/org/zzc/bpcsolver/problem/entity/DynamicInstance.java`
- Java BPC 主问题：`reference/dvrp/src/main/java/org/zzc/bpcsolver/model/DVRPMasterProblem.java`
- Java BPC 和多图定价：`reference/dvrp/src/main/java/org/zzc/bpcsolver/algorithm/BPCSolver.java`
- Java 精确 IP 对照：`reference/dvrp/src/main/java/org/zzc/bpcsolver/exact/OriginalMipDVRPSolver.java`
- Java 动态输入：`reference/dvrp/src/main/java/org/zzc/bpcsolver/problem/loader/ExcelReader.java`
- Java 资源和 benchmark：`reference/dvrp/src/main/resources/`
- Java 测试：`reference/dvrp/src/test/`

动态 Solomon Excel 实例至少需要以下字段：

```text
ID, Coord_X, Coord_Y, Demand, Time_Earliest, Time_Latest,
Time_Service, Time_Disclosure
```

`Time_Disclosure` 对应 Java 的 `Customer.callInTime`，表示客户何时向调度系统公开。

## 已确认的 DVRP 语义

### 动态阶段

Java 参考实现的主链路为：

```text
ProblemData
  -> DynamicStateUpdater.createInitialInstance
  -> BPCSolver.solve(stage)
  -> DynamicStateUpdater.update(previous stage, previous solution, current time)
  -> BPCSolver.solve(next stage)
```

决策时间由原始车场最早时间和所有客户 `callInTime` 去重、排序得到。每个阶段只把当前已经公开且尚未完成的客户放入 `remainingCustomerIds`。

阶段状态至少包含：

```text
problemData              静态客户、距离、容量和时间参数
currentTime              当前动态决策时间
routeStartTime           路线/label 使用的时钟起点
originDepot              原始车场及当前可用车辆数
returnDepot              原始车场的返回节点
remainingCustomerIds     当前必须覆盖的客户
servedCustomerIds        已完成或已不可重新分配的客户
externalDepots           执行中车辆对应的外部起点
```

### 外部车场

数学模型把执行中的每辆车建模成一个外部车场 `O^E`，每个外部车场只有一辆车，并且必须选出一条以它为起点的路线。等待车辆从原始车场 `O` 出发，原始车场可以派出任意数量的车辆。所有路线最终都返回原始车场的返回节点 `n+1`。

Java `DynamicStateUpdater` 的状态更新规则包括：

- 路线已经开始但尚未返回的车辆转成外部车场；
- 车辆在节点等待/服务时，外部车场位置取该节点；
- 车辆在弧上行驶时，按当前时间和弧出发时间的比例插值位置；
- 外部车场车辆数为 1；
- 外部车场的剩余容量由该路线在当前时刻已经完成的需求计算；
- 已返回车辆重新计入原始车场可用车辆数；
- 已完成服务，或已经错过原始车场最晚出发时刻的客户，不再进入下一阶段。

### 目标和约束

当前模型目标是所有阶段路线实际行驶距离的总和，不是优先最小车辆数。阶段内需满足：

- 每个剩余客户恰好服务一次；
- 每个外部车场恰好有一条出发路线；
- 原始车场出发路线数量为非负；
- 外部车辆已经使用的容量加新服务需求不超过车辆总容量；
- 原始车场车辆按标准容量计算；
- 所有路线满足容量和时间窗；
- 所有路线返回 `returnDepot`。

数学文档把 `Q^k` 描述成外部车辆的“已用容量/当前状态容量”，而 Java 字段名是 `remainingCapacity`，实际约束使用“已服务需求 + 新需求 <= 总容量”。这两个命名必须在实现前统一，暂时不要仅按字段名推断含义。

## RouteOpt 当前架构和不可直接复用的假设

生产代码主要位于 `packages/application/cvrp`，公共求解器和数据结构位于 `packages/common`，外部依赖位于 `packages/external`。

静态入口链路：

```text
packages/application/cvrp/src/main.cpp
  -> CVRPSolver 或 VRPTW
  -> BbNode / BBTController
  -> 主问题 LP/MIP、列生成、pricing、割和分支
```

关键模块：

- `src/read_data`：命令行和实例文件解析；
- `src/initial`：距离矩阵、需求、时间窗资源和 solver 环境初始化；
- `src/node`：根节点主问题、列池和分支节点状态；
- `src/pricing`：容量/时间窗资源、bucket graph、标签定价和枚举；
- `src/add_column`：把 `SequenceInfo` 列加入 LP；
- `src/vrptw`：VRPTW 时间窗可行性检查；
- `src/main`：列生成、割、分支和求解流程；
- `src/cuts`、`packages/rounded_cap_cuts`、`packages/rank1_cuts`：割和对应 reduced cost；
- `src/two_stage`：节点持久化和 two-stage 文件。

当前静态 RouteOpt 的重要不变量：

1. 节点数组按 `0 .. dim-1` 编号，节点 `0` 是唯一车场；客户通常是 `1 .. dim-1`。
2. `SequenceInfo::col_seq` 不包含节点 `0`，只保存客户序列。
3. 加列和 VRPTW 可行性检查默认路线是 `0 -> col_seq -> 0`。
4. `BbNode::buildModel` 创建客户覆盖行，并在最后一行建立车辆数 `>=` 约束。
5. pricing bucket graph 的 `0` 号桶是唯一出发/返回车场；大量代码直接访问 `cost_mat4_vertex[*][0]` 或 `all_*_buckets[0]`。
6. `CVRP_Pricing`、`InitialController`、`add_column` 和许多割/分支逻辑都以一个固定 `dim` 和一个固定车场为前提。
7. `VRPTW::checkRouteTimeWindowFeasibility` 从时间 `0`、节点 `0` 开始传播，并用节点 `0` 的时间窗检查回场。

因此，DVRP 不能只通过增加 `Time_Disclosure` 字段或复制一个 VRPTW 输入文件实现。最先需要解决的是“列/路线如何携带起点身份、外部车辆容量与起点时间”，以及“每个起点的主问题约束和 pricing graph 如何表达”。

## 建议的目标分层

实现时保持动态壳和静态求解器分离：

```text
DynamicProblemData       静态客户、距离、时间窗、披露时间
DynamicStageState        当前时间、剩余客户、服务车辆、外部起点
DynamicStateUpdater      根据上一阶段路线推进车辆状态
DynamicStageSolver       对一个阶段调用 RouteOpt 的多起点 VRPTW
DVRPSimulationController 事件循环、实际距离累计、阶段结果输出
```

阶段求解器至少应能接受：

- 原始车场起点及可用车辆数；
- 零个或多个外部起点，每个起点的位置、最早出发时间、剩余容量和车辆数；
- 当前剩余客户及其时间窗、服务时间和需求；
- 共同的返回车场；
- 本阶段目标为路线成本之和。

路线/列对象必须显式保存 `startDepotId`（或等价的 machine/start key），不能再隐含地假设所有列从 `0` 出发。客户覆盖仍应按客户 ID 聚合；起点约束按起点 key 聚合；外部起点必须是等式约束，原始车场车辆约束的方向和上界要依据最终模型确认。

数学模型把原始车场拆成出发节点 `O` 和返回节点 `n+1`。实现中应保持这两个语义分离，避免用同一个索引同时做出发时间传播和返回时间传播。

## Harness / 测试要求

每次修改都应让行为可以被小实例和日志复现。优先建立以下测试层次：

1. **静态回归**：现有静态 Solomon `C204`、`R204` 的 VRPTW 结果和可行性不回退。不要把 `-u` 已知上界当作正确性证明。
2. **状态更新单测**：覆盖客户释放、服务完成、车辆返回、节点等待、弧中行驶插值、容量扣减和外部车场 ID 连续性。
3. **阶段模型单测**：检查客户恰好覆盖一次、每个外部起点恰好出发一次、原始车场车辆数量、容量和时间窗。
4. **事件循环测试**：使用 2--5 个客户的手工实例，验证阶段顺序、下一事件时间和跨阶段实际距离累计。
5. **参考一致性测试**：对相同的小实例，同时运行 Java reference 和 C++ 实现，比较阶段状态、路线可行性和总距离；差异必须说明是 rounding 或明确的策略差异。
6. **构建/集成测试**：使用现有 CMake 配置构建 `Release`，再运行一个不带已知最优上界的最小 DVRP 实例。CPLEX/Gurobi 通过 CMake 的 `ROUTEOPT_SOLVER` 选择，不在算法代码中硬编码切换。

建议每个阶段输出可机器读取的记录，至少包括：`currentTime`、下一事件时间、剩余客户、外部起点、各路线起点和序列、阶段目标、实际行驶距离、求解状态及耗时。这样才能定位“状态更新错误”和“阶段求解慢”是两类不同问题。

## 当前待确认边界

在这些问题确认前，不要把临时选择写成不可逆的全局约定：

- `Q^k` 的正式含义究竟是已用容量还是剩余容量；内部字段名、标签资源和约束方向如何统一。
- 车辆在客户服务中、等待中、弧上行驶中以及刚好到达事件时，客户和车辆分别处于什么状态。
- 客户服务完成的判定是 `arrival + service <= currentTime`，还是包含其他事件/不可抢占规则。
- 已经从原始车场出发但尚未形成外部车场的车辆，如何计入下一阶段可用车辆。
- 原始车场车辆是有限车队、阶段内可重复使用，还是按模型允许的任意数量；`num_vehicle` 与最大车辆数如何解释。
- 旅行时间、距离、弧中位置插值和时间窗边界的 rounding 规则，必须与 benchmark 和 Java reference 对齐。
- 动态阶段目标是每阶段完整路线成本，还是只累计从当前时间到下一事件的实际行驶距离；跨阶段路线不能重复计费。
- 外部起点 ID、客户 ID、返回节点 ID 与现有 `0 .. dim-1` 数组的映射方式，以及是否需要从“按数组索引”改为“按节点 key 查表”。
- 是否保留现有 cuts、branching、enumeration、two-stage node I/O；这些模块目前大量假设单一车场，应逐项验证后再启用。
- 外部起点数量变化时 `BbNode`、bucket graph、列池和 solver model 的生命周期；禁止复用带有旧阶段起点约束的模型而不重建。

## 工作流程

- 先更新本文件和测试 harness，再进行算法改动；每次只引入一个可验证的行为变化。
- 修改前先阅读目标模块和相邻接口，优先复用 RouteOpt 现有 solver、资源和列生成抽象，不复制一套平行算法。
- 动态层优先使用值语义/快照，避免上一阶段对象被下一阶段原地修改后无法复现。
- 不修改 `reference/dvrp` 作为“适配 C++”的捷径；若发现参考实现与数学模型冲突，记录差异并先确认语义。
- 不通过已知最优上界、特殊实例名或隐藏参数掩盖不可行/未收敛状态。性能实验可以使用 `-u`，正确性测试必须明确记录是否使用。
- 代码变更后按风险运行最小测试集，并保留构建命令、实例路径、solver 后端、参数覆盖和输出摘要。

## 初始阅读结论

Java reference 已经实现了动态外层、多起点图、DVRP 主问题和动态状态测试；RouteOpt 已经实现了成熟的静态 VRPTW 列生成、割和分支，但核心列结构仍是单车场模型。第一阶段的工程任务不是马上接入所有 cuts，而是先建立一个可测试的“多起点阶段模型 + 动态状态快照”，并用小实例证明它与数学模型及 Java 状态更新一致。
