# DVRP 动态 BP 闭环与命令行模块

> 状态：已完成（小实例生产动态BP闭环；无大规模性能承诺）
>
> 最后更新：2026-10-26
>
> 实际调用顺序见[方法导读](DVRP动态BP闭环流程与方法导读.md)，稳定边界见[设计07](../设计/M1-标准BP-DVRP/07-动态BP闭环与命令行文件与接口.md)。

## 1. 当前能力与边界

新增 `dvrp_solve.exe`：完整读取八字段TXT，逐披露事件调用已有单阶段BP，复核并接收整数路线，最后结清所有服务与返场，输出实际累计距离。原 `dvrp.exe` 仍只读摘要；原版 `packages/`、动态状态机及BP内部算法未改。

每次运行显式选择RF／起点专属有向弧、普通标签／前向桶图中的一个组合；整次运行不切换。仅BP的Optimal经转换、动态可行性及成本核对通过后才执行。所有超时（含已有解）、节点/资源/迭代上限、数值未解决或错误均停止，不使用旧方案兜底。

阶段最优是现有浮点容差内的BP闭合；Completed表示所有事件处理完且所有车辆返场，不表示预知未来请求的动态全程最优。求解的现实耗时不改变仿真时钟。

## 2. 文件与接口

新增1个生产hpp、2个生产cpp；测试1个cpp、1个CMake脚本、2个TXT。已有源码只改应用CMake。

| 文件（相对应用目录） | 职责 |
| --- | --- |
| `src/simulation/include/simulation_controller.hpp` | SimulationResult、StageBpSummary、run接口与退出码 |
| `src/simulation/src/simulation_controller.cpp` | 单一事件循环、BP接线、状态原子提交和结构化日志 |
| `src/solve_main.cpp` | 独立CLI，解析选项、复用reader、构造动态控制器并运行 |
| `test/simulation/simulation_test.cpp` | 真实BP闭环、逐快照MIP核对、停止/异常/输出故障 |
| `test/simulation/check_cli.cmake` | 精确校验CLI退出码及输出 |
| `test/simulation/fixtures/{three_events,infeasible}.txt` | 完整三事件例和格式合法但不可行例 |

```cpp
auto result = DVRP_SimulationController{}.run(
    DynamicController(info_vertex, capacity, instance_name),
    BpOptions(BranchStrategy::RyanFoster, PricingBackend::BucketGraph),
    &std::cout);
```

run按值拥有新的DynamicController；不支持中途恢复。结果保存最终有效状态、动态阶段记录、BP摘要及停止阶段详细诊断。成功阶段不保留整棵BP树；各阶段重新建模型与定价上下文，不复用旧列、dual或限制。

## 3. 构建与运行

在仓库根目录运行，CPLEX_ROOT按本机修改：

```powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-simulation -DDVRP_BUILD_SOLVER=ON -DDVRP_BUILD_SIMULATION_TESTS=ON -DROUTEOPT_SOLVER=CPLEX "-DCPLEX_ROOT=D:/Applications/Development Tools/CPLEX_Studio221/cplex"
cmake --build build/dvrp-simulation --config Release --parallel 8
ctest --test-dir build/dvrp-simulation -C Release --output-on-failure
.\build\dvrp-simulation\Release\dvrp_solve.exe .\packages_dvrp\application\dvrp\test\simulation\fixtures\three_events.txt --branching rf --pricing bucket
```

三事件例在0/5/15规划，时间48全部返场，实际总距离28；客户3、车辆1。换成 `--branching arc` 或 `--pricing plain` 即可运行其他组合。

可选 `--stage-seconds 60 --max-nodes 10000` 是每阶段BP预算，不是整个仿真预算；0允许作为预算边界。LP/节点CG/pricing/标签/图预算仍生效，RUN记录显示所有配置上限。未知、重复、缺失或非法参数报错；不支持-u或静默缩小输入。

只构建生产入口时使用新构建目录、`-DBUILD_TESTING=OFF -DDVRP_BUILD_SOLVER=ON`，再 `cmake --build <目录> --config Release --target dvrp_solve`；不需要任何测试开关或测试源码。两个新开关均默认OFF，默认reader构建不查找CPLEX。当前仅验证CPLEX，不声称Gurobi已接通。

## 4. 输出、失败和成本

| 记录前缀 | 内容 |
| --- | --- |
| INPUT / RUN | 完整实例规模、车辆数元数据、所选策略和有效配置上限 |
| STAGE / START | 时间、下一事件、待规划客户、起点key/位置/时间/剩余容量及固定成本 |
| SOLVE / ROUTE | 原BP状态、见证、界/gap、节点与标签统计、耗时及路线；executed表示计划已接收，不代表未来路程已发生 |
| ADVANCE | 已结算事件区间或最终返场的实际距离增量 |
| SUMMARY / WALL | 完成或停止、最后结算时刻、实际距离、客户/车辆数及耗时 |

输出可重定向，字符串转义，缺失值为NA。最终距离只取 `cumulativeActualDistance`，不相加阶段计划成本。接收时路线成本和加固定成本后的总值均与BP见证核对；跨事件弧不重复取整。

正常停止沿用动态模块状态，另保留精细BpStatus；超时有路线也标记executed=false。接线/日志失败时，外层状态是StoppedError，而finalState可能保留上一次Running或已完成的有效快照，这不是允许继续运行。输出不是事务型持久化，断流可能留下不完整记录；应同时检查退出码，不能只寻找某条成功文本。

退出码：0=完成/帮助，1=输入/校验/数值/日志/运行错误，2=参数错误，3=当前阶段已证不可行，4=超时，5=节点/资源/迭代上限。非0的实际距离只是已执行的部分结果。

## 5. 验证证据

新增22项CTest：16组循环与接线测试＋6组CLI检查。MSVC19.44、CPLEX22.1下，全量Release/Debug均177/177通过，最小配置Release47/47、默认无solver25/25通过；MSVC AddressSanitizer（保留/EHsc）最小配置47/47通过，不覆盖CPLEX内部。生产目标在BUILD_TESTING=OFF下独立构建和运行成功。

- 手算三事件四组合均实际距离28；同刻服务完成、等待/服务、固定返场、已返回不复用、空初始和外部直接返场均检查。
- 16个固定种子五客户输入×四组合＝64次动态运行；实际产生216个阶段快照，四组合共864次BP与独立完整MIP核对；oracle不供列、不提供上界或派车。
- 三客户容量2的根LP30，经真实分支得到整数路线并执行，最终实际距离40。六客户两阶段例实际距离80；在第二阶段树的早/中/晚节点预算中断，真实观察到保留整数见证仍停止，实际距离保持5。
- 20客户、4事件的手工实例四组合距离40、20辆车；不穷举。只是规模/不复用检查，不是100客户性能结论。
- 所有停止状态有确定性注入；错误stageId/策略/后端、非法路线、缺证书、错误成本与输出失败不推进。超时已有解是注入测试；不把它说成真实观测到CPLEX超时有解。
- 完整 `c101_D_25_E_25.txt`（100客户、初始79已披露）未裁剪，RF＋桶图、每阶段3秒：首阶段TimeLimit、退出码4、实际距离0，没有派车。日志 `build/dvrp-simulation/c101-full.log/.err`。

全量配置在上述命令中另开启历史八个测试开关：`DVRP_BUILD_REFERENCE_SOLVER`、`DVRP_BUILD_RMP_TESTS`、`DVRP_BUILD_PRICING_TESTS`、`DVRP_BUILD_ROOT_CG_TESTS`、`DVRP_BUILD_BRANCHING_TESTS`、`DVRP_BUILD_BRANCHING_CG_TESTS`、`DVRP_BUILD_BP_TESTS`、`DVRP_BUILD_BP_SOLVER_TESTS`。本轮使用 `build/dvrp-bp`，Debug将构建/CTest配置改为Debug；日志为该目录的 `simulation-{release,debug}-ctest.log`。

ASAN复现使用独立目录 `build/dvrp-simulation-asan`，上述最小测试配置加 `"-DCMAKE_CXX_FLAGS=/EHsc /fsanitize=address /Zi" "-DCMAKE_EXE_LINKER_FLAGS=/DEBUG"`，Release构建；运行前将MSVC `bin/Hostx64/x64`加入PATH。检查的是编译插桩的项目代码，不覆盖CPLEX库内部；过程与结果见该目录build.log和ctest.log。

静态回归：复用原Release程序，C204/R204均不带-u。C204=588.1，程序120.35秒；独立重算3条路线、100客户、容量700、时间窗/返场和总距离通过。R204外部180秒截止仍未完成并停止，不报告静态回归全通过。与其他构建/测试并行，不作性能比较；日志为 `build/dvrp-simulation/static-{c204,r204}.log/.err`，原静态源码无差异。

## 6. 保留限制与下一步

不含动态ng、双向标签、cuts、热启动、批量任务、全仿真总时限、日志恢复或在线计算延迟。每步临时复制动态状态用于安全提交，有额外开销；真实OOM时不能保证还可分配完整诊断结果。标准BP在较大阶段仍可能爆标签或超时。

本模块让小实例动态BP原型可运行；M1教师汇报/用户验收仍单独跟踪。下一步先运行三事件例核对日志与已实现边界，再整理M1交付材料，不自动扩大到性能增强。
