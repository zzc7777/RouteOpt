# DVRP 共同 BP 搜索树与整数恢复模块

> 状态：已完成（单阶段BP；现由独立动态循环/CLI调用）
>
> 最后更新：2026-10-28
>
> 按方法阅读见[详细导读](DVRP共同BP搜索树与整数恢复流程与方法导读.md)，稳定接口及恢复依据见[设计06](../设计/M1-标准BP-DVRP/06-共同BP搜索树与整数恢复文件与接口.md)。

## 1. 完成了什么

现在可以把一个 `StageProblem` 交给 `DVRP_BpController::solve`，自动求节点LP、选分支、创建子节点、恢复并校验整数路线，得到单阶段BP结果。

同一套树支持四种组合：RF／起点专属有向弧 × 普通标签／桶图。每次solve固定一种组合；不增加车辆数分支，不调用生产MIP补救，不使用已知最优上界。

```text
StageProblem → BP树 → 节点CG（两阶段RMP ↔ 受限pricing）
                     → 整数恢复／分支／剪枝
            ← BpResult：见证、界、状态及节点日志
            → toStageSolution：显式转换，不执行车辆
```

本模块仍只负责单阶段求解，不执行车辆。完整动态接线现由[独立SimulationController与dvrp_solve入口](DVRP动态BP闭环与命令行模块.md)完成：显式转换结果后交给动态层验证与执行。旧 `main.cpp`、reader、dynamic源码不变，`dvrp.exe`仍只读文件。

## 2. 文件与入口

新增2个生产头文件、2个生产cpp、2个测试cpp：

| 文件 | 职责 |
| --- | --- |
| [bbt_controller.hpp](../../../packages_dvrp/branching/bbt/include/bbt_controller.hpp) | 通用值节点队列、active节点、父子替换和全局界，不依赖CPLEX或客户数据 |
| [bp_controller.hpp](../../../packages_dvrp/application/dvrp/src/branch_and_price/include/bp_controller.hpp) | `BpOptions/BpResult/IntegerWitness`、节点记录及公开接口 |
| [bp_controller.cpp](../../../packages_dvrp/application/dvrp/src/branch_and_price/src/bp_controller.cpp) | 单阶段预算、节点CG调度、上界、剪枝、分支和停止 |
| [integer_recovery.cpp](../../../packages_dvrp/application/dvrp/src/branch_and_price/src/integer_recovery.cpp) | RF/弧结构恢复、独立可行性和成本复核、结果转换 |
| [bp_tree_test.cpp](../../../packages_dvrp/application/dvrp/test/branch_and_price/bp_tree_test.cpp) | 无solver树、恢复、数值、原子性和状态测试 |
| [bp_solver_test.cpp](../../../packages_dvrp/application/dvrp/test/branch_and_price/bp_solver_test.cpp) | 真正BP、完整MIP独立对照、逐节点预算中断和真实TXT子集 |

已有根CG头/cpp仅补充每轮可选 `PricingStatistics` 并记录；CMake增加默认OFF的两个测试开关。原版 `packages/`、现有RMP/分支/pricing算法、Java reference及模型未变。

```cpp
BpOptions options(BranchStrategy::RyanFoster, PricingBackend::BucketGraph);
auto result = DVRP_BpController{}.solve(stage, options);
auto solution = toStageSolution(result); // 转换不等于执行
```

默认整次BP时限60秒、最多10000次节点CG；`nodeCg`中的LP/定价/标签/迭代预算仍生效。总时限包含构造、校验和释放；不可中断API不承诺硬实时退出。输入只读、结果为值，每次solve重新创建树、节点模型和历史。

## 3. 关键行为

- 按有效下界最小优先，平局按节点ID；未求解子节点继承父界，但重新建RMP并做两阶段CG。
- 取出的节点保留为active，两个孩子全部建立成功才替换父；内存异常不能丢掉尚未解决的分区。
- 近1的lambda只用来提出路线候选。RF无候选时尝试按“客户＋外部起点”覆盖块恢复；有向弧按起点/方向核对并从正值列去重完整序列，不拼造路线。
- 所有严格正lambda都参与结构检查；极小正列引起覆盖冲突时可以返回数值未解决，不偷偷删除后宣称证明成立。
- 接受整数见证前独立重算客户/外部行恰好一次、容量、时间窗、返场、分支限制和真实成本；固定成本只加一次。见证仅给上界，不单独证明最优。
- 使用匹配阶段、节点、完整限制和LP版本的Phase-II保守下界。队列、active及容差终结分区都计入全局界；队列清空不自动伪造gap=0。
- 全局容差为 `absTol + relTol * max(1, abs(U))`，默认1e-6及1e-8，按路线成本比较；报告实际数值gap，不写成有理数最优证明。
- 无分数候选却无法恢复、或恢复后界不闭合且不能再分支，返回 `NumericalUnresolved`。

## 4. 结果及停止边界

`BpResult`包含可选整数见证、路线/总成本上下界、绝对/相对gap、策略/后端、逐节点上下文和CG迭代、标签汇总、剩余节点与中断位置。有解看 `incumbent`，不能用路线是否为空判断：空阶段的空路线也是合法解。

| BP状态 | 转为已有StageSolution |
| --- | --- |
| Optimal且见证与有效界一致 | Optimal，表示显式容差内闭合 |
| Infeasible且无见证 | Infeasible |
| TimeLimit | 按是否有见证区分两个超时状态；均不能继续派车 |
| NodeLimit / ResourceLimit / IterationLimit / NumericalUnresolved / Error | Error并保留原因和诊断路线；精细状态仍在BpResult |

任一节点未完成就停止本阶段，不跳过它去求兄弟节点。保存已验证见证与继承下界，不把中断定价结果当作证明。`toStageSolution`不重新求解，调用者应传入未篡改的结果；动态调用方仍会独立验证执行路线。

## 5. 构建与测试

在仓库根目录，CPLEX路径按本机修改。仅新模块加基础回归：

```powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-bp-min -DDVRP_BUILD_BP_TESTS=ON -DDVRP_BUILD_BP_SOLVER_TESTS=ON -DROUTEOPT_SOLVER=CPLEX "-DCPLEX_ROOT=D:/Applications/Development Tools/CPLEX_Studio221/cplex"
cmake --build build/dvrp-bp-min --config Release --parallel 8
ctest --test-dir build/dvrp-bp-min -C Release --output-on-failure
```

本轮全量配置另开启 `DVRP_BUILD_REFERENCE_SOLVER`、`DVRP_BUILD_RMP_TESTS`、`DVRP_BUILD_PRICING_TESTS`、`DVRP_BUILD_ROOT_CG_TESTS`、`DVRP_BUILD_BRANCHING_TESTS`、`DVRP_BUILD_BRANCHING_CG_TESTS`，使用 `build/dvrp-bp`。只跑新模块可加 `-R dvrp_bp_`；Debug对应 `--config Debug`、`-C Debug`。

2026-10-26，MSVC19.44、CPLEX22.1：新增9组无solver＋11组CPLEX测试，全量Release、Debug均 **155/155通过**；无solver MSVC AddressSanitizer **69/69通过**；全部选项默认OFF的Release **25/25通过**，包括434实例读取。不使用 `-u` 或测试MIP上界启动BP。

主要证据：

- 三客户容量2：根LP30、BP整数40，四种组合都实际分支，与完整MIP一致。
- 100个固定种子随机阶段（2～5客户、0～3外部起点），400次正式BP对照独立全集MIP，总计57次分裂；oracle不向生产CG供列或上界。
- 五客户例：RF深度4、13次节点CG；有向弧深度5、39次节点CG。两版定价分别相同；枚举所有提前节点预算，含已有整数解时中断，核对保留解/全局界和未完成状态。
- RF同覆盖不同顺序、微小正列冲突、外部空返回、非连续ID/同号客户与车辆、损坏成本、旧快照、子节点分配异常回滚均有测试。
- 20客户＋6外部起点的手工阶段四组合均得40、26条路线，不使用枚举；只是规模边界检查，不是性能结论。
- `c101_D_25_E_25.txt` 时间0有79个已披露客户，取前12个测试；四组合均根节点闭合，成本119，路线独立核查，不穷举、不提前披露客户。不是完整100客户或动态BP。

真实总时限0、组件定价时限0、节点/标签/迭代预算均测试；超时有/无解的转换另有确定性测试，不将模拟状态当作观测到的“CPLEX超时已有解”。

无solver内存检测：只开启 `DVRP_BUILD_BP_TESTS`、`DVRP_BUILD_BRANCHING_TESTS`、`DVRP_BUILD_PRICING_TESTS`，加 `/fsanitize=address /Zi` 和链接 `/DEBUG`，运行时将MSVC的 `bin/Hostx64/x64` 加到当前shell PATH。命令格式见[分支模块](DVRP双策略分支与受限列生成模块.md#4-测试与复现)，构建目录改为 `build/dvrp-bp-asan`。不覆盖CPLEX库内部。

静态回归复用原Release程序、不带 `-u`：C204的UB=LB=588.1、程序115.03秒，独立检查3条路线、100客户覆盖、容量700、时间窗、返场及距离通过。R204外部180秒截止未完成并停止，本轮未见崩溃；保留历史标签扩容访问冲突风险，不能报告静态回归全部通过。原 `packages/` 无源码差异；与其他验证并行，耗时不作性能比较。

静态复现：在 `packages/application/cvrp` 运行 `./bin/Release/cvrp.exe ./instance/vrptw/solomon-100/c204.txt` 或 `r204.txt`，后者加外部限时；日志为忽略的 `build/dvrp-bp/static-{c204,r204}.log/.err`，不提交build。

### 5.1 静态 VRPTW 规模筛查（2026-10-26）

完整条件、四组合结果表、超时见证、规模边界及复现信息已独立整理到[DVRP静态VRPTW规模测试结果](DVRP静态VRPTW规模测试结果.md)，本节不再重复维护明细。

本轮为六个Solomon代表文件的原ID前缀子集，披露全0、单原始起点、保留容量/时间窗、距离单目标；四组合串行，30秒主筛查及部分60秒确认，不使用已知上界或MIP补解。共102次完整运行：67次最优、35次超时，其中4次有可行整数解；全部71份见证通过独立可行性与成本复核。

10客户各例四组合均快速最优，部分较难例15客户需数十秒，R101／50四组合约34.8～43.1秒最优；不能推广为通用规模上限或动态多起点性能结论。此次实验未改生产算法。

## 6. 限制与下一步

首版每节点重新建模和定价，不热启动；完整elementary记忆及深层RF仍可能爆标签。无100客户性能承诺；仅验证CPLEX，Gurobi未接入本测试构建。资源预算不是进程内存字节上限，真实OOM只能尽力保留诊断。

[设计07](../设计/M1-标准BP-DVRP/07-动态BP闭环与命令行文件与接口.md)已完成动态事件循环与独立CLI接线：每阶段调用本入口，只有最优状态通过复核才接收路线，超时即停止，最后核对实际距离。运行方式和最新端到端证据见[闭环模块说明](DVRP动态BP闭环与命令行模块.md)；M1小实例原型待用户验收，不能据此承诺100客户性能。
