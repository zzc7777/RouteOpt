# 多起点 RMP：文件与接口方案

> 状态：已完成（保留已落地的架构与接口；不是完整 BP）
>
> 最后更新：2026-10-15
>
> 原需求已合并到[实现说明](../../实现说明/DVRP多起点RMP模块.md)；详细方法见[导读](../../实现说明/DVRP多起点RMP流程与方法导读.md)。模型依据为[数学模型 v4](../../模型和推导/DVRP%20数学模型-v4.md)。

## 1. 先看模块边界

本模块是“拥有一份 LP 及当前路线列的控制器”，不是动态状态对象，也不是完整根节点或阶段求解器。

```text
DynamicController.buildStageProblem()              已实现
              ↓ StageProblem（本阶段值快照）
测试驱动 / 未来 StageSolver                         后者未实现
              ↓ 创建并调用
DVRP_RmpController：建行 → 初始列 → LP → dual        已实现
              ↑ 新路线                  ↓
              └──── 独立 pricing ───────┘           已完成测试接线
```

本次下游只得到 RMP 结果，不能调用 `acceptSolution()` 执行它。将来根节点持有一份 RMP 并协调 pricing；搜索树还要保存分支条件、界和父子关系，不能把本模块等同于 `BbNode`。

## 2. 已落地文件边界

以下路径相对 `packages_dvrp/application/dvrp/`，2个生产文件、1个测试文件及已有CMake修改均已落地。

| 类别 | 路径 | 职责 |
| --- | --- | --- |
| 生产头文件，1 个 | `src/node/include/rmp_controller.hpp` | 一个控制器及少量行、列、结果、配置类型的声明 |
| 生产实现，1 个 | `src/node/src/rmp_controller.cpp` | 校验快照/路线、行列映射、初始列、两阶段 LP、加列和资源释放 |
| 测试文件，1 个 | `test/rmp/rmp_test.cpp` | 手算矩阵、人工变量、全集 LP 对照及增量加列测试 |
| 修改已有文件，1 个 | `CMakeLists.txt` | 可选 RMP 测试目标及 CPLEX 构建隔离 |

沿用原版 `src/node` 的主问题归属，但不复制整个 `BbNode` 及其 cuts、枚举、桶图依赖。本次加列只是将已给定路线写入模型，暂放同一实现文件；以后 pricing 接入时再评估是否需要独立 `add_column`，不预先拆层。

已经存在的 `StageProblem`、`StageRoute`、DVRP `Solver` 副本及 `routeOptDistance` 继续使用；不新增 `problem_data.hpp`、第二套阶段数据或通用求解器框架。保留复制片段的原来源和许可证。

现有 reader、dynamic、`main.cpp`、原版 `packages/` 和 Java reference 不改。生产 RMP 不包含或链接测试枚举器；只有测试目标同时编译对照工具。

## 3. 数据契约

下列类型同放新头文件，不为每个名称单建文件。

| 类型 | 保存什么 |
| --- | --- |
| `RmpRow` | 行类型（客户/外部起点）、原 ID/key、连续 LP 行号 |
| `RmpColumn` | 稳定列 ID、`StageRoute`、重算的真实成本、LP 列号；不含人工列 |
| `RmpDuals` | 按客户 ID 和外部 key 分开的两组 dual；不提供原始起点 dual |
| `RmpLpResult` | `stageId`、模型修订号、Phase-I/II、LP 状态、可选目标/dual、路线变量与人工量、诊断 |
| `RmpOptions` | 单次 LP 优化时限，默认 60 秒；本轮单线程，数值容差沿用现有口径 |
| `RmpModelSnapshot` | 只读诊断快照：从 solver 回读的行、列、目标、边界及系数，用于测试实际建模结果 |

`RmpLpResult` 的路线变量按稳定列 ID 对应，人工量按行对应；没有有效结果时用空值，不用 0 冒充。只在最优且复核通过时提供供定价使用的 dual、lambda 和 reduced cost。Phase-I 目标是人工量，不命名为路线成本；Phase-II 才报告路线成本及加一次固定成本后的目标。

行号按 `customers` 的快照顺序、再按 `externalStarts` 顺序生成。客户 ID 与车辆 key 即使数值相等也不是同一行；不采用 `customerId - 1` 或把 key 当数组下标。这仅是 LP 编号转换，不构建 pricing 的图或桶编号。

## 4. 公开方法与谁来调用

命名空间为 `RouteOpt::Application::DVRP`，完整声明见生产头文件。

| 方法 | 输入 → 输出 | 主要行为 |
| --- | --- | --- |
| `DVRP_RmpController(stage, options)` | 快照 → 独立 RMP 对象 | 校验并复制阶段，建立等式行及人工变量，初始为 Phase-I |
| `addInitialRoutes()` | 无 → 更新列池 | 尝试各外部起点直接返场、各起点单客户路线，仅加入可行列 |
| `addRoutes(routes)` | `vector<StageRoute>` → 对应的稳定列 ID | 校验、重算成本、去重，写入真实路线列 |
| `solveLp()` | 当前模型 → `RmpLpResult` | 解当前阶段的 LP，读取并复核原始/对偶结果 |
| `enterCostPhase()` | 当前有效的零人工量结果 → Phase-II | 将人工变量固定为 0，恢复真实成本，旧结果失效；之后必须重新求 LP |
| `inspectModel()` | 当前模型 → `RmpModelSnapshot` | 只读回查实际 LP，不暴露可修改的 Solver 指针 |
| `columns()` | 无 → 真实列只读引用 | 查看稳定ID、起点/序列、真实成本和solver列号；加列可能使引用失效 |

测试程序现在负责调用这些方法；未来由列生成控制器调用。构造和初始列分开，使测试能先验证“只有人工变量”的模型，不增加隐藏的测试模式。重复调用 `addInitialRoutes()` 不重复建列。

控制器拥有阶段副本、行列映射和独立 model/env，析构及异常退出均释放。禁止浅拷贝控制器，跨事件重建；暂不设计子节点复制接口。头文件通过一个私有实现对象隐藏 CPLEX 句柄，定义仍在同一个 `.cpp`，不增加文件或继承体系。

输入批次先完整校验，再写 LP；非法路线不会先加入半批列。若 solver API 在写入中失败，标记该控制器失效并停止使用，不假称已回滚成功。加列或切换目标后增加修订号；旧快照可以用于日志，不能作为当前模型的定价/最优性依据。

## 5. 行列转换的具体例子

阶段含客户 5、8，原始起点 key=0，外部起点 key=42；外部起点位于已承诺客户 3，3 不进入本阶段覆盖行。输入路线只给起点和客户顺序，下面成本均需从快照重算。

| 路线 | 客户5行（0） | 客户8行（1） | 外部42行（2） |
| --- | --- | --- | --- |
| `StageRoute{0, {5}}` | 1 | 0 | 0 |
| `StageRoute{42, {8}}` | 0 | 1 | 1 |
| `StageRoute{42, {}}` | 0 | 0 | 1 |
| `StageRoute{42, {5,8}}` | 1 | 1 | 1 |

前三行是候选初始列，最后一行可由独立 pricing 提供；均须可行才能加入。外部42直接返场列并非可有可无的“零列”：它覆盖外部42行，并有真实返场成本。

假设最近 Phase-II 解的 dual 为 `pi[5]=6, pi[8]=7, mu[42]=4`，某条可行的 `42→5→8→返回` 成本为 15，则其 reduced cost 为 `15-6-7-4=-2`。pricing 输出 `{42,{5,8}}`，RMP 重算成本/系数后加入并重新求解。这组数只演示接口，不声称是现有实例的最优 dual。

## 6. 初始可行性：两阶段法

令当前真实路线矩阵为 `A`、路线变量为 `lambda`，每个等式行增加一个人工变量 `a_h >= 0`：

```text
Phase-I： min sum(a_h)        s.t. A * lambda + a = 1, lambda >= 0
Phase-II：min sum(c_r lambda_r)    同样等式，所有 a_h 固定为 0
```

由于本次所有行右端都是 1，`lambda=0, a=1` 给出初始辅助 LP 可行点。人工变量单独索引，不塞入 `StageRoute`、不计入真实路线数，也不交给分支候选统计。

真实列始终保存真实成本；写入 Phase-I 时目标系数为 0，进入 Phase-II 时统一恢复。人工变量固定为 0 后不删除，以保持 LP 列索引稳定；不将固定人工列的 reduced cost 当作真实路线最优性条件。

### 6.1 何时能切换，何时能说不可行

- Phase-I 最优人工总量在容差内为 0：可尝试切换，不需要再证明 Phase-I 无负列，因为目标下界就是 0。
- 固定人工为 0 后重新优化并验证真实行残差；若数值上不能建立可行解，报告数值/恢复错误，不靠四舍五入人工量宣布成功，更不能报告完整阶段不可行。
- Phase-I 最优人工量仍为正：继续向 pricing 请求可行性列；本模块不输出阶段级 `SolveStatus::Infeasible`。
- 后续精确定价穷尽全部允许路线且无负列时，人工量仍为正，才可证明完整 LP 不可行；该完整性证明由列生成控制层负责。
- Phase-II 当前 RMP 最优不等于完整 LP 最优；只有完整定价终止后，控制层才能将相应值作为 BP 节点界。LP 可行/最优也不等于整数路线可执行。

当前仅根模型：不提供分支增行、列删除或重新打开 Phase-I 的接口。RF 子节点如何恢复可行性，留到分支模块设计，不能假设根模型接口已经覆盖所有子节点情况。

### 6.2 dual 给 pricing 时必须带什么

除阶段身份、修订号、客户/外部起点 dual，还必须带 Phase-I/II，以决定真实路线成本的权重 `w`：

```text
w = 0（Phase-I）或 1（Phase-II）
原始路线 rc = w * cost - sum(customer dual)
外部路线 rc = w * cost - sum(customer dual) - external dual
```

定价的可行路线集合不随优化阶段改变；不能为了找 Phase-I 列放松容量或时间窗。pricing 必须支持两种目标，而非只在人工量正时继续按真实成本找负列。

真实路线变量为连续非负、不显式加上界1；每列都覆盖至少一个客户行或外部行，因此其值已被等式限制。对偶校验只针对当前模型，不把所有尚未生成路线都已对偶可行作为 RMP 自身的承诺。

## 7. 路线校验与复用来源

- 按 `startKey` 查起点，从其 `startTime`、`remainingCapacity` 出发；逐客户累积需求、按旅行时间/等待/服务推进，到独立 `returnDepot` 检查 `deadline`。
- 坐标及数值必须有限，客户/key 唯一，起点类型、时间及容量遵守现有 `StageProblem` 约定；序列不可重复客户或包含非本阶段客户。空原始路线拒绝。
- 使用既有 `routeOptDistance` 和容差；不因某条单客户/直接返场路线不可行而断定其所有延伸不可行，截断距离不保证三角不等式。
- 来源：[原根模型](../../../../packages/application/cvrp/src/node/src/node.cpp)、[原初始列](../../../../packages/application/cvrp/src/main/src/call_read_node_in.cpp)、[原加列](../../../../packages/application/cvrp/src/add_column/src/add_column.cpp)。复制必要建模模式并保留来源，不搬入无关依赖。
- 使用 DVRP 已有 `newModel/addConstraints/addVars/changeObj/setColUpper/optimize/getX/getDual/getRC` 等 Solver 接口；按当前核对，暂不需要新增 solver 接口文件。
- 生产端独立校验路线；测试枚举器保留自己的资源传播与成本检查，仅共享既定距离数值函数，避免生产和对照共同依赖同一个路线校验实现。

## 8. 状态与构建边界

`RmpLpResult` 的状态只说明本次 LP：`OptimalRmp`、`TimeLimit` 或 `Error`。最优人工量为正通过 Phase-I 及人工量表达，不叫阶段不可行。根辅助模型本应可行；意外 solver 不可行/无界作为诊断错误处理，不升级为完整阶段证明。

每次优化时限默认60秒，单线程；这不是阶段 BP 的总预算。超时不提供用于最优定价证明的 dual，保留原始状态/诊断；动态层的超时停止以后由 StageSolver 统一接入。浮点校验沿用现有容差，不宣称有理数精确证明。

`DVRP_BUILD_RMP_TESTS` 默认OFF：启用时要求 `BUILD_TESTING=ON` 和 `ROUTEOPT_SOLVER=CPLEX`，注册 `dvrp_rmp_test` 的16组测试。原对照选项继续独立；任一 solver 测试选项开启时才查找 CPLEX，复用查找与 DLL 配置，不另写通用构建框架。

新测试目标编译 RMP、DVRP solver/dynamic 必需源文件及对照工具的枚举/LP 实现，不编译对照工具自己的 `main`。生产源码不反向依赖测试。当前不额外建 reader/RMP library；以后阶段求解入口可直接使用这两份生产文件。

两项 solver 选项均关闭时仍保留原无 CPLEX 构建；`dvrp.exe` 仍只读取，不新增求解命令行。Gurobi 明确不支持，不静默切换后端。安装路径由 `CPLEX_ROOT` 提供，不硬编码个人路径。

## 9. 验证边界

| 验证组 | 核对内容 |
| --- | --- |
| 映射/初始列 | 回读实际系数、边界和目标；空阶段、只有外部起点、非连续/同值 ID 与 key；直接返场及单客户列合法性 |
| 路线/增量 | 重算时间、容量、H、成本；同路线幂等、不同顺序保留；非法批次不改变模型；API失败使模型停止使用 |
| Phase-I/II | 仅人工列 → 人工量正 → 补真实列 → 人工量零 → 固定人工/恢复成本；旧结果失效；零人工仍可能分数 |
| 全集对照 | 加齐枚举路线后与 `solveFullLp` 比较目标、原始/对偶可行性及互补性，不比较具体 dual 向量 |
| 测试定价 | 在测试内扫描全集，用当前阶段目标计算 rc，补负列直至结束；同时覆盖“未补足”与真正不可行 |
| 状态/隔离 | 时限、API/数值错误、资源释放、多阶段独立模型；不改输入快照，不接受伪造全局证明 |

Phase-II 对照可使用已有 `computeReducedCosts`；Phase-I 在测试中按零真实成本独立计算，不能直接套用它的真实成本公式。只比较同一 elementary 可行域，不把这个测试循环称为桶图定价或生产 BP。

实际命令、通过记录及未触发的故障类型统一见[模块说明](../../实现说明/DVRP多起点RMP模块.md#6-构建与验证)，不将上表的设计要求全部冒充故障实测。双版本pricing已完成，见[稳定接口](03-多起点定价文件与接口.md)；下一步设计正式根列生成，子节点可行性恢复另行处理。
