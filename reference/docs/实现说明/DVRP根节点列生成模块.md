# DVRP 根节点列生成模块

> 状态：已完成（根LP及受限节点重载；不是整数BP）
>
> 最后更新：2026-10-25
>
> 方法执行顺序见[详细导读](DVRP根节点列生成流程与方法导读.md)，接口及下界证明见[设计04](../设计/M1-标准BP-DVRP/04-根节点列生成文件与接口.md)。

## 1. 现在能做什么

对一个 `StageProblem`，通过已有两阶段 RMP 和普通/桶图 pricing，独立求无分支的根 LP。生产循环不依赖测试枚举或 MIP 答案，不限制为5客户。

```text
测试入口 → RootCg.solve(stage, options)
           → 建 RMP/pricing → 求 LP → 定价 → 加列 → 再求 LP
           → Phase 切换 / 数值界核对 / 中断
        ← RootCgResult（LP快照、数值界、逐轮记录）
```

输出不是 `StageSolution`，不调用动态层 `acceptSolution()`。`dvrp.exe` 仍只读文件；整数恢复、BP树和动态自动求解尚未实现。

新增 `solve(stage, context, options)` 已共用同一个循环求受限节点LP，支持RF/专属有向弧；旧无分支接口不变。上下文随LP、dual、pricing、数值界和结果回传，迭代记录保存nodeId。完整规则及新增验证见[分支模块](DVRP双策略分支与受限列生成模块.md)。下文113项记录为该扩展前的根模块验收。

## 2. 文件、接口和所有权

路径均在 `packages_dvrp/application/dvrp/`：

| 文件 | 职责 |
| --- | --- |
| [root_cg_controller.hpp](../../../packages_dvrp/application/dvrp/src/column_generation/include/root_cg_controller.hpp) | `DVRP_RootCgController`、选项、快照、界、记录与结果；无CPLEX头 |
| [root_cg_controller.cpp](../../../packages_dvrp/application/dvrp/src/column_generation/src/root_cg_controller.cpp) | 两阶段反馈循环、有效性检查、数值界及预算 |
| [root_cg_test.cpp](../../../packages_dvrp/application/dvrp/test/root_cg/root_cg_test.cpp) | 调用正式入口，独立全集LP审核和异常边界 |

另修改 RMP 头/实现及其测试，加入 `solveLp(double seconds)`；修改已有 CMake，新增默认关闭的 `DVRP_BUILD_ROOT_CG_TESTS`。没有修改 pricing 算法、动态模块、reader、solver副本或原版 `packages/`。

`solve(const StageProblem&, const RootCgOptions&) const` 每次局部创建独立 RMP/pricing；输入原ID和起点key不变。控制器不缓存阶段，返回值只含值副本；退出时释放solver。快照中的 `lambda[id]` 与同一版本的 `columns[id]` 配对，不与后来新增的列混拼。

`RootCgOptions(PricingBackend::PlainLabeling)` 或 `BucketGraph` 显式选择后端；不自动切换。默认总时限60秒、最多10000次LP、生成初始列、绝对/相对gap容差为 `1e-6/1e-8`。LP与pricing各自的单次上限也保留，每次取与总剩余时间的较小值。

## 3. 结束状态意味着什么

| 状态 | 含义 |
| --- | --- |
| `LpConverged` | Phase-II真实LP有效；全部起点完整定价、无待加负列且数值差距闭合；不是整数最优 |
| `LpInfeasible` | Phase-I人工量正，完整定价给出的保守人工量下界仍大于 `1e-6` |
| `TimeLimit` | 总预算或组件时限触发；不消费中断pricing的部分候选 |
| `ResourceLimit` | 标签/图预算或传播到根入口的内存分配失败；不是不可行 |
| `IterationLimit` | 达到最大LP求解次数；不因目标不再下降而误停 |
| `NumericalUnresolved` | 无候选但界不闭合、数值溢出或界核对失败；不放宽阈值掩盖问题 |
| `Error` | 非法参数、旧版本/起点错配、不一致见证、重复负列无新增或组件错误 |

没有有效LP时 `snapshot` 为空；没有界时 `bound` 为空。中断可保留旧有效快照及版本，但不能将它当成本次收敛或剪枝依据。

Phase-I人工量归零后固定人工变量、恢复距离成本，并重新求LP；不要求再做一次Phase-I定价。每次完整pricing都校验阶段/版本/Phase/backend、起点全集、可行见证及候选一致性；加列由RMP再次独立检查。

## 4. 数值与预算边界

令 `m=客户行数+外部起点行数`、`D=sum(dual)`、`delta=min(0, 所有最小真实rc, Phase-I人工rc)`。使用 `D+m*delta` 的向下修正版作为完整LP数值下界，而非把RMP目标直接当下界。

具体计算（`eps` 为 double 机器精度）：

```text
A = sum(abs(dual)); W = max(见证成本, abs(各起点最小rc), 0)
rcGuard  = 64*eps*(客户数+2)*(1+abs(RMP目标)+A+W)
C        = m*(delta-rcGuard)
sumGuard = 64*eps*(m+2)*(1+A+abs(C))
L_safe   = nextafter(D+C-sumGuard, -infinity)
gap      = RMP目标-L_safe
```

空模型解析返回0。两个guard均随结果记录；对偶和/目标不一致或上下界反转不会将gap截为0。200行、最小rc `-5e-6` 虽未达到加列阈值 `-1e-5`，仍扣除至少0.001。默认gap不足以闭合时返回 `NumericalUnresolved`。

这依赖已有浮点定价、可行性与求解容差，不是有理数或区间算术证书；guard仅是显式舍入余量，不是对所有算法误差的证明。固定成本只在Phase-II的上下界/计划成本加一次，不影响pricing或gap。

总时钟包含构造、初始列、LP、pricing、验证和局部对象释放；各步骤边界检查，构造/solver API不可立即打断，不承诺硬实时截止。数量预算不等于整个进程的内存字节上限。

## 5. 构建和验证

仓库根目录执行；CPLEX路径按本机修改：

```powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-root-cg -DDVRP_BUILD_ROOT_CG_TESTS=ON -DDVRP_BUILD_REFERENCE_SOLVER=ON -DDVRP_BUILD_RMP_TESTS=ON -DDVRP_BUILD_PRICING_TESTS=ON -DROUTEOPT_SOLVER=CPLEX "-DCPLEX_ROOT=D:/Applications/Development Tools/CPLEX_Studio221/cplex"
cmake --build build/dvrp-root-cg --config Release --parallel 8
ctest --test-dir build/dvrp-root-cg -C Release --output-on-failure
cmake --build build/dvrp-root-cg --config Debug --parallel 8
ctest --test-dir build/dvrp-root-cg -C Debug --output-on-failure
```

只测根循环：`ctest --test-dir build/dvrp-root-cg -C Release -R dvrp_root_cg_ --output-on-failure`；手算入口：`build/dvrp-root-cg/Release/dvrp_root_cg_test.exe fractional`。

新增15组根CG测试及1组RMP时限测试：手算成本5/固定成本后12；LP30/MIP40与目标停滞；100随机阶段、200次正式根求解对照完整LP；两类不可行、空阶段、20客户+6起点；200行微负rc修正、人工rc、版本/候选错误、迭代/标签/图上限和生命周期。

2026-10-25，MSVC19.44、CPLEX22.1：四项测试开关全开，Release/Debug均 **113/113通过**；仅根CG开关开启Release **40/40**；全部关闭的无solver默认构建 **25/25**，含434实例读取；无CPLEX、仅pricing开关开启Release **48/48**。`BUILD_TESTING=OFF` 却开启根CG测试的组合按预期拒绝。新增测试没有使用已知最优上界。

真实入口验证了总预算0、pricing预算0、迭代、标签、图数量中断；确定性策略函数验证错误上下文、重复负列和状态分类。本机零秒CPLEX LP仍可返回最优，不能称为已实际触发LP超时，也未注入所有solver/API故障。日志保存在忽略的 `build/dvrp-root-cg/`，不提交build产物。

静态回归复用原版Release程序，不带 `-u`：C204得到UB=LB=588.1，程序时间94.84秒（含进程启动的外部计时约97.7秒）；独立核对3条路线的100客户覆盖、容量、时间窗、返场及成本通过。R204在180秒外部时限内未完成，已停止；本轮未见崩溃，历史标签扩容访问冲突风险仍保留，不能称为静态回归全部通过。原版 `packages/` 无源码变更。

复现静态运行：在 `packages/application/cvrp` 下执行 `./bin/Release/cvrp.exe ./instance/vrptw/solomon-100/c204.txt` 或 `r204.txt`。本次日志为 `build/dvrp-root-cg/static-{c204,r204}.log/.err`；程序内置时限不等于本次外部180秒检查，运行R204需自行设置外部截止。

## 6. 限制与下游

只验证CPLEX/MSVC；未验证Gurobi或新模块的专用内存检查。未强制注入真实OOM、solver增量写入中途失败；零秒LP可能仍返回最优，状态映射不冒充已实测真实LP超时。

RF和专属有向弧已通过等价列过滤接入，不新增分支行；受限路线仍至少覆盖一行，`m`的数值下界推导已在设计05核对。下一步为共同搜索树与整数恢复；以后若新增分支行或改变列系数，仍须重新核对dual及下界。
