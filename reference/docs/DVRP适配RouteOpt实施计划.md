# DVRP 适配 RouteOpt 实施计划

## 1. 目的和结论

本计划回答一个核心问题：当前 DVRP 能否采用 RouteOpt 的列生成、定价和分支定界框架实现。结论是可以，但适配对象应当是“每个动态阶段的多起点 VRPTW”，而不是把现有 CVRP/VRPTW 入口直接改成动态程序。

推荐的总体结构是：

```text
DVRP txt
  -> DynamicProblemData
  -> DynamicStageState
  -> DynamicStateUpdater
  -> MultiOriginStageSolver
  -> 推进到下一个事件并累计实际距离
  -> 重复求解下一阶段
```

RouteOpt 的公共 solver、距离计算、时间窗资源、标签扩展和列生成机制可以复用；DVRP 的输入、动态状态、阶段模型、起点约束和事件循环必须有独立所有权。现有 CVRP/VRPTW 用户不选择 DVRP 时，原命令、实例格式和结果必须保持不变。

## 2. 范围和非目标

### 2.1 首期范围

- 独立的 `packages/application/dvrp` 应用和可执行目标。
- 与 Solomon VRPTW 完全一致、仅新增 `DISCLOSURE_TIME` 列的 `txt` 输入格式及严格校验。
- 一个物理原始车场、一个逻辑返回车场，以及任意数量的动态外部起点。
- 客户披露事件、服务完成判定、车辆状态推进、弧上插值和阶段快照。
- 每个外部起点恰好选择一条路线；原始车场可派出非负数量的标准车辆。
- 阶段内客户恰好覆盖一次、容量和时间窗可行、路线回到共同返回车场。
- 所有阶段实际行驶距离的累计和机器可读日志。

### 2.2 首期非目标

- 不直接编译或改写 `reference/dvrp` 的 Java 源码。
- 不让旧 CVRP/VRPTW 入口自动识别 DVRP 字段。
- 不一次性迁移所有 cuts、复杂 branching、enumeration、HGS 和 two-stage 持久化。
- 不用已知最优上界、文件名约定或隐藏参数替代动态状态建模。
- 不承诺直接支持 Java reference 的 Excel 输入；Excel 字段只作为语义参考。

## 3. 事实依据和来源优先级

出现冲突时按下列优先级处理：

1. 已确认的用户语义和 `reference/docs/数学模型.md`。
2. Java reference 的可执行行为和测试，特别是 `DVRPDynamicSolver`、`DynamicStateUpdater`、`DVRPMasterProblem`、`ForwardLabeling`。
3. RouteOpt 当前实现和静态回归结果。
4. benchmark 约定、性能实验和临时实现选择。

数学模型中 `Q^k` 的文字同时出现“已用容量”和“当前状态容量”，而 Java 字段名 `remainingCapacity` 实际按“已用容量 + 新需求 <= Q”使用。实现前必须统一内部命名和不变量，不能只按字段名推断含义。

## 4. 已确认的 DVRP 语义

- 客户集合按 `Time_Disclosure` 分阶段释放；未披露客户不进入当前阶段的覆盖行。
- 当前阶段已披露且未完成的客户进入 `remainingCustomerIds`。
- 已服务完成、已不可重新分配，或因车场最晚出发时间失去可行性的客户不再进入下一阶段。
- 正在执行的车辆在客户节点等待/服务时，外部起点位置是该节点；在弧上行驶时按出发时刻和当前时刻比例插值。
- 每个外部起点只有一辆车，下一阶段必须有一条以该起点为起点的路线。
- 原始车场起点和外部起点都使用同一个客户集合及共同返回车场；所有路线最后回到逻辑返回节点。
- 外部车辆带有阶段开始时的容量状态；新服务需求不得使总需求超过标准车辆容量。
- DVRP 的全局目标是跨阶段实际行驶距离总和。阶段优化目标是当前阶段所选路线成本，但已执行的路径片段不得在下一阶段重复计费。

## 5. 当前 RouteOpt 与目标架构

### 5.1 当前静态假设

现有生产代码主要位于 `packages/application/cvrp`，公共接口位于 `packages/common`。下列假设不能直接用于 DVRP：

- `SequenceInfo` 只保存客户序列，默认不含节点 `0`。
- `add_column` 和 VRPTW 可行性检查默认路线是 `0 -> customers -> 0`，时间从 `0` 开始传播。
- `BbNode::buildModel` 为客户覆盖行加一个静态车辆数约束。
- pricing bucket graph 只有一个出发/返回车场桶，许多数组直接访问索引 `0`。
- cuts、branching、enumeration、HGS 和 two-stage 文件包含单车场或固定 `dim` 假设。

### 5.2 目标模块

建议新建以下 DVRP 边界，名称可在实现时微调但所有权要保持：

```text
packages/application/dvrp/
  CMakeLists.txt
  src/main/              独立 CLI 和退出码
  src/read_data/         txt 解析、校验、规范化摘要
  src/model/             DynamicProblemData、客户、车场、事件
  src/dynamic/           阶段快照、状态推进、弧上插值
  src/stage_solver/      多起点阶段求解器接口和验证器
  src/master/            阶段列模型和起点约束
  src/pricing/           多起点标签定价适配
  src/result/             阶段日志、路线和总距离输出
  instance/              DVRP 生产 txt 实例
```

阶段求解器通过接口依赖公共 RouteOpt 资源；动态层不依赖静态 CVRP 的全局状态。每次阶段求解应新建或完整重置 master、列池和 pricing graph，不能复用带有上一阶段起点约束的模型。

## 6. 输入和数据契约

### 6.1 生产 txt 格式

V1 的完整输入契约见 `reference/docs/DVRP实例格式V1.md`。它严格复制现有 Solomon VRPTW 布局，仅在节点数据末尾增加 `DISCLOSURE_TIME`：

```text
C101

VEHICLE
NUMBER     CAPACITY
  25         200

CUSTOMER
CUST NO.  XCOORD.  YCOORD.  DEMAND  READY TIME  DUE DATE  SERVICE TIME  DISCLOSURE_TIME

    0      40        50         0         0       1236         0                 0
    1      45        68        10       912        967        90               948
```

车辆数、容量、客户 ID、坐标、需求、时间窗和服务时间继续使用原 VRPTW 的位置和语义。外部起点和逻辑返回节点由程序运行时生成，不写入文件；算法配置也不进入实例。

### 6.2 校验规则

解析器必须拒绝：节点行不是 8 个数值、重复 ID、非法数字、负需求、容量越界、时间窗反转和披露时间越界。解析成功后输出客户数、事件数和容量摘要。

### 6.3 阶段数据类型

建议用值语义/快照：

```text
DynamicProblemData: 静态客户、距离/旅行时间、容量、原始参数
DynamicStageState: currentTime、routeStartTime、remainingCustomerIds、
                   servedCustomerIds、originDepot、returnDepot、externalStarts
ExternalStart: stableId、x/y、earliest/latest、capacityState、vehicleCount=1
StageRoute: startKey、returnKey、customerSequence、startTime、loadState、cost
```

客户 ID、外部起点 ID、返回节点 ID 与内部数组索引必须通过显式映射转换，不能假设外部 ID 连续或等于数组位置。

## 7. 路线列契约

DVRP 首期不要修改所有静态代码都使用的 `SequenceInfo`。先定义 DVRP 专用 `StageRouteColumn`，至少包含：

- `startKey`：原始车场或某个外部起点的稳定 ID；
- `returnKey`：共同返回车场 ID；
- `customerSequence`：仅客户 ID，不能把起点身份藏在序列中；
- `startTime`、`capacityState`、`servedDemand`；
- `cost`：从当前阶段起点到返回车场的完整路线成本；
- 可选的标签/分支所需资源摘要。

列生成、master、解码器和可行性验证器都只能通过该契约读取起点和资源。若后续证明公共 `SequenceInfo` 扩展不会破坏静态 ABI，才评估增加可选 `startKey`；在此之前不把 DVRP 字段加入静态结构并改变旧入口行为。

## 8. 阶段主问题

对每个可行阶段路线列 `r` 建立二进制变量 `lambda_r`，最小化所选列成本总和：

```text
min sum(cost_r * lambda_r)

sum_r a[i,r] * lambda_r = 1       for each remaining customer i
sum_{r:start(r)=e} lambda_r = 1   for each external start e
sum_{r:start(r)=origin} lambda_r >= 0
lambda_r in {0,1}
```

共同返回车场通过路线列本身保证。若最终车队是有限的，应把原始车场约束改成明确的 `<= availableVehicles`；如果模型允许无限标准车辆，`>= 0` 在数学上是冗余约束，但仍应保留语义说明，不能误认为静态 `num_vehicle` 上限。

阶段 master 的客户行和外部起点行都是一等约束。起点行 dual 必须进入对应起点的 pricing reduced cost；不能只把客户 dual 传给现有单车场定价。

## 9. 多起点定价

### 9.1 第一版：枚举列验证

先为每个起点生成小规模的全部可行路线，接入阶段 master，验证覆盖、外部起点等式、容量、时间窗和结果解码。该版本的目的不是性能，而是隔离主问题和动态状态错误。

### 9.2 第二版：起点独立标签图

对原始车场和每个外部起点各建立一张 pricing graph，共享客户节点和返回节点，但起点的坐标、最早时间、容量状态不同。标签初始资源必须使用：

- 当前阶段 `routeStartTime` 或起点最早时间；
- 原始车场标准容量，或外部起点的当前容量状态；
- 对应起点约束 dual；
- 客户覆盖 dual、分支 dual 和已启用割的 dual。

每张图只产生以自身起点开始、以共同返回节点结束的列。应支持并行求解不同起点，但先保证串行确定性和日志可复现。

### 9.3 reduced cost 和列加入

路线的原始成本、客户系数、起点系数、分支/割系数必须在同一个 `StageRouteColumn` 上计算。`add_column` 不得再隐式补 `0 -> sequence -> 0`。所有新增起点列都要经过独立的路线可行性验证器。

## 10. 动态事件和状态更新

`DynamicStateUpdater` 负责状态，不负责优化。事件循环职责如下：

1. 从原始车场最早时间和客户披露时间构造去重、排序后的事件时间线。
2. 创建初始阶段，释放当前客户并初始化可用原始车辆。
3. 调用阶段求解器得到各起点路线。
4. 将路线从当前时间推进到下一事件，判断车辆在节点等待/服务、弧上行驶还是已返回。
5. 对弧上车辆按已行驶比例插值得到外部起点，并扣减已经完成的服务需求。
6. 把已返回车辆计入原始车场可用车辆，把完成服务或不可再分配的客户移入完成集合。
7. 累计本时间段实际行驶距离，生成下一阶段快照。

事件同一时刻的处理顺序、服务是否不可抢占、恰好到达事件时归属哪一状态必须在测试前定稿。阶段日志至少输出当前时间、下一事件、剩余客户、外部起点、路线起点和序列、阶段目标、实际距离、状态和耗时。

## 11. RouteOpt 模块影响矩阵

| 模块 | 复用程度 | 首期动作 | 风险/说明 |
| --- | --- | --- | --- |
| common solver、距离、基础资源 | 高 | 通过 DVRP target 链接复用 | 不引入 DVRP 全局状态 |
| read_data | 低 | 新建 DVRP txt reader | 不改静态 parser |
| initial | 中 | 抽取可复用初始化，DVRP 自己建立起点资源 | 起点和返回节点分离 |
| add_column | 中 | 复制/抽象为 StageRouteColumn 适配器 | 禁止硬编码 depot 0 |
| vrptw | 中 | 抽取参数化起点时间、坐标、返回节点的检查器 | 旧 `VRPTW` 行为保持不变 |
| node/master | 中 | 新建阶段 master，客户行+起点行 | 每阶段重建模型 |
| pricing | 中 | 先枚举，再多图标签定价 | 一个 depot bucket 假设需隔离 |
| branching | 低 | 首期关闭；随后从 `qij` 开始 | 现有无向边和单 depot 语义不一定适用 |
| cuts/RCC/rank-1 | 低 | 首期关闭并记录原因 | 逐个证明多起点系数正确后再启用 |
| enumeration/HGS | 低 | 首期不接入 | 依赖固定 depot 或静态全局模型 |
| two-stage I/O | 低 | 首期关闭 | 起点集合和列契约跨阶段变化 |
| CMake | 新增 | `packages/application/dvrp/CMakeLists.txt` 和独立 target | 共享库目标名不能冲突 |

## 12. 分阶段实施和验收门槛

| 阶段 | 工作 | 依赖 | 交付物 | 验证/退出门槛 |
| --- | --- | --- | --- | --- |
| P0 | 定稿语义、建立静态基线 | 无 | 决策记录、C204/R204 基线日志 | 旧 CVRP/VRPTW 构建和结果不回退 |
| P1 | DVRP target、txt 契约、模型和 CLI | P0 | 独立 CMake、reader、校验器、最小实例 | 缺字段/非法数据可诊断；不触碰旧入口 |
| P2 | 动态状态引擎 | P1 | 事件时间线、快照、状态推进、距离累计 | 2--5 客户手工实例覆盖节点等待、服务、弧上插值、返回车辆 |
| P3 | 枚举列阶段 master | P2 | `StageRouteColumn`、客户/起点约束、解码器 | 与手算/IP 对照；每个外部起点恰好一条路线 |
| P4 | 多起点 root column generation | P3 | 起点独立 graph、标签资源、reduced cost、列加入 | 无负 reduced cost 时停止；与枚举 master 对照 |
| P5 | 精确 branch-and-price | P4 | 首先支持 `qij` 或等价客户边分支 | 小实例 LP/IP 与精确 MIP 一致；旧分支测试不受影响 |
| P6 | 动态端到端循环 | P2、P4 | `DVRPSimulationController`、阶段日志、总距离 | Java reference 对照阶段状态、路线可行性和总距离 |
| P7 | 性能策略恢复 | P5、P6 | 逐项迁移 cuts、branching、enumeration/HGS | 每项策略有独立回归；默认关闭未验证策略 |
| P8 | benchmark、文档和发布门 | P7 | 实例集、运行说明、性能表、失败诊断 | Release 构建、无 `-u` 正确性测试、静态回归全通过 |

每个阶段只引入一种行为变化。阶段未达到退出门槛时，不应进入后续加速工作。

## 13. Harness 和回归策略

### 13.1 单元测试

- parser：正常、缺字段、重复 ID、时间窗反转、披露时间越界和不可行数据。
- 状态更新：客户释放/完成、节点等待/服务、弧上插值、容量扣减、车辆返回、外部起点 ID 连续性。
- 路线检查：起点、返回点、容量、时间窗、客户不重复、已用/剩余容量不变量。
- 阶段 master：客户恰好覆盖、外部起点恰好一次、原始车场车辆边界。

### 13.2 集成测试

用 2--5 个客户构造可手算实例，固定事件顺序和距离，检查每个阶段的快照和跨阶段实际距离。再把同一实例送入 Java reference 与 C++，比较阶段状态、路线可行性和总距离；若因浮点 rounding 有差异，必须在日志中明确。

### 13.3 静态回归和构建

```text
cmake -S packages/application/cvrp -B build-cvrp -DCMAKE_BUILD_TYPE=Release
cmake --build build-cvrp --config Release
cmake -S packages/application/dvrp -B build-dvrp -DCMAKE_BUILD_TYPE=Release
cmake --build build-dvrp --config Release
```

DVRP target 通过现有 CMake 的 `ROUTEOPT_SOLVER=GUROBI|CPLEX` 选择后端，不在算法源码硬编码。正确性测试不使用 `-u` 已知上界；性能实验如需使用，必须单独标记。

## 14. 风险登记

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| `Q^k` 语义混用 | 容量约束反向或重复扣减 | 先定义 `usedCapacity`/`remainingCapacity` 不变量并单测 |
| 服务与事件同时发生 | 阶段客户集合不同 | 定义 tie-order，加入边界测试 |
| 弧上车辆可否改道不明 | 外部起点状态不一致 | 先按可重规划建模，作为显式决策记录 |
| 起点 ID 被当数组索引 | 越界或列错配 | 使用稳定 key 到连续索引的映射 |
| 复用旧 master | 上一阶段约束残留 | 每阶段重建 solver、行、列池和图 |
| 旧 cuts/branching 假设单 depot | 错误剪枝或错误 reduced cost | 首期关闭，逐项迁移并对照枚举模型 |
| 阶段路线成本重复累计 | 总目标错误 | 记录 planned cost 与 actual segment distance 两个字段 |
| 多起点定价性能不足 | 实例难以扩展 | 先保证枚举正确，再并行独立 graph 和剪枝 |

## 15. 待确认决策

以下决策必须在对应阶段进入代码前写入测试和文档：

- `Q^k` 是已用容量还是剩余容量，内部字段和标签资源如何命名。
- 车辆服务是否不可抢占，弧上车辆是否能在事件时立即改道。
- 事件 tie-order：先完成服务/推进车辆，还是先披露客户。
- 原始车场是无限车辆、有限车队，还是阶段内可重复使用。
- 外部车辆是否允许只返回车场的空路线。
- 距离、旅行时间、弧上比例和时间窗边界的 rounding 规则。
- 分支使用有向弧还是无向客户边；首期具体选择 `qij` 还是其他规则。
- 外部起点、返回节点和客户的稳定 ID 映射。
- 阶段主问题目标与跨阶段实际距离的精确定义。

## 16. Definition of Done

满足以下条件才认为 DVRP 首期适配完成：

1. `dvrp` 有独立入口、独立 txt parser、独立 CMake target，旧 CVRP/VRPTW 无行为变化。
2. 最小闭环能从 txt 读取、运行事件循环、求解至少两个阶段并输出总实际距离。
3. 多起点阶段 master 的客户覆盖、外部起点等式、容量、时间窗和返回车场约束均有自动测试。
4. 枚举列版本和标签定价版本在小实例上给出相同的阶段最优值和可行性。
5. Java reference 对照差异有记录；没有用已知上界掩盖不可行或未收敛。
6. 每阶段日志可机器读取，能区分状态更新错误、阶段模型错误和定价性能问题。
7. 未迁移的 cuts、branching、enumeration、HGS、two-stage 明确关闭并有后续任务，而不是隐式生效。

## 17. 建议提交顺序

```text
1. docs: add DVRP semantic decisions and txt contract
2. dvrp: add independent target and validated parser
3. dvrp: add dynamic stage state and event updater
4. dvrp: add enumerated multi-origin stage master
5. dvrp: add stage route feasibility and reference harness
6. dvrp: add multi-origin pricing/root column generation
7. dvrp: add exact branching behind feature flags
8. dvrp: migrate and benchmark optional cuts/performance strategies
9. docs: publish benchmark, regression and run instructions
```

每个提交应包含构建命令、实例路径、solver 后端、是否使用 `-u`、求解状态和输出摘要，便于回放和定位回归。
