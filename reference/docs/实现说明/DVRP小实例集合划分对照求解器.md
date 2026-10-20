# DVRP 小实例集合划分对照求解器

> 状态：已完成（测试工具，不是生产 BP）
>
> 最后更新：2026-10-15（更新下游RMP/pricing入口；本工具行为及原验证记录不变）
>
> 依据：[数学模型 v4](../模型和推导/DVRP%20数学模型-v4.md)；详细执行过程见[流程与方法导读](DVRP阶段对照求解流程与方法导读.md)。

## 1. 当前能做什么

对一个 `StageProblem` 完整枚举可行 elementary 路线，再由 CPLEX 求完整集合划分 LP / 二元 MIP。它是后续算法的“小实例标准答案”，不是弧变量 MIP，也不是 RouteOpt 的 enumeration 加速模块。

| 后续核对对象 | 本工具提供的依据 |
| --- | --- |
| pricing | 路线全集、给定同一组 dual 后的每条路线 reduced cost |
| 根节点列生成 | 完整路线 LP 的最优值及对偶可行性检查 |
| 阶段 BP | 完整路线 MIP 的整数路线、最优值与界 |

现已用于[双版本pricing](DVRP多起点定价模块.md)的独立枚举对照及RMP接线测试；生产根列生成控制器和BP尚未实现，不能把模块对照称为完整BP验证。每阶段最优也不等于预知未来客户的全程DVRP最优。

## 2. 入口和文件

四个文件集中在 [test/stage_reference](../../../packages_dvrp/application/dvrp/test/stage_reference/)：

| 文件 | 职责 |
| --- | --- |
| `stage_reference.hpp` | 四个公开函数、只读路线全集及 LP/MIP 结果；不包含 CPLEX 头文件 |
| `stage_route_enumerator.cpp` | 阶段校验、完整枚举、资源传播与 reduced cost；不调用 solver |
| `stage_reference_solver.cpp` | 行列转换、LP/MIP、结果复核、状态转换及资源释放 |
| `stage_reference_test.cpp` | 手算、边界、状态、动态自动连跑的测试入口 |

基础接口从原版复制到 `packages_dvrp/common/solver_interface/`：`solver.hpp`、`solver_macro.hpp`、`solver_cplex.hpp`、`solver_cplex.cpp`，保留许可证；DVRP 副本补充终止状态、最优界、绝对 gap 和变量类型查询。CPLEX 查找模块复制到 `packages_dvrp/external/cmake_modules/`。

只修改 DVRP 已有 CMake 注册可选目标。原版 `packages/`、Java reference、reader、动态生产代码和 `main.cpp` 均未修改；`dvrp.exe` 仍只读取文件，不接受本工具的测试参数。

## 3. 已实现的模型边界

- 每阶段允许 0–5 个客户、0–5 个外部起点；超限抛输入错误，不截断、不报告不可行。
- `EnumeratedStage` 拥有输入副本，只在完整枚举成功后返回；不能把一个阶段的列池配给另一个阶段。
- 保留每个起点、每种不重复客户顺序；不按同一客户集合取最便宜路线，不做 ng、支配或启发式删列。
- 按各自出发时间和剩余容量传播资源，允许提前到达等待；检查服务开始时间窗和服务后的最晚返场时间 H。
- 原始起点只生成非空客户路线；外部起点可直接返场，但必须可行；同位置不同车辆仍分别选路。
- 客户覆盖 `=1`、外部起点选路 `=1`；没有原始车场车辆数约束，不读取 TXT `NUMBER` 或 benchmark 值。
- LP 为非负连续变量、不显式加上界1；MIP 显式设二元变量并回读类型验证。没有人工列或大罚值。
- 弧成本与时间复用 `routeOptDistance`，可行性容差 `1e-6`。固定成本只在报告中加一次，不写入路线成本或 reduced cost。
- 枚举不因“某前缀直接返场不可行”而剪掉延伸：截断距离不保证严格三角不等式。

## 4. 结果与错误

LP 返回状态、可选目标、所有路线变量、客户/外部起点 dual 和 reduced cost；只有最优且复核通过才提供这些参考值。LP 分数解不转成 `StageSolution`。

MIP 返回现有 `StageSolution`，另带可选路线成本、阶段计划成本、路线成本最优界、CPLEX 原始状态和耗时。有整数解时复核全部覆盖/起点等式、整数性及真实成本；成功方案仍交动态层独立检查。

明确区分最优、可行但未证明最优、不可行、超时有解、超时无解和错误。`CPXMIP_OPTIMAL_TOL` 保守映射为 `Feasible`，不是严格闭合界的 `Optimal`；未知/模糊状态报告错误。超时已有解只保留诊断，动态层也停止。

无客户且无外部起点时直接返回空最优方案；完整列池为空但存在必选行时直接报告不可行。上述解析情况不伪造 CPLEX 状态码。许可或 API 错误报告 `Error`，不冒充不可行；每次调用独立创建并释放 model/env。

默认单次优化上限60秒、单线程、相对/绝对 MIP gap 为0。`enumerationSeconds` 记录枚举耗时；`solveSeconds` 记录整个求解函数的耗时（含建模、复核和释放），并不只计优化调用。时间参数不是整次动态仿真的总预算。

## 5. 构建与运行

在仓库根目录运行；下列安装路径是本机实测位置，不是源码中的硬编码路径：

```powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-reference -DDVRP_BUILD_REFERENCE_SOLVER=ON -DROUTEOPT_SOLVER=CPLEX "-DCPLEX_ROOT=D:/Applications/Development Tools/CPLEX_Studio221/cplex"
cmake --build build/dvrp-reference --config Release -j 8
ctest --test-dir build/dvrp-reference -C Release --output-on-failure
```

只运行新对照测试：

```powershell
ctest --test-dir build/dvrp-reference -C Release -R dvrp_reference_ --output-on-failure
.\build\dvrp-reference\Release\dvrp_stage_reference_test.exe dynamic
```

测试程序不传参数时运行全部15组；也可传 `lp`、`mip`、`fractional`、`time_limit` 等测试名，不是传实例路径。测试数据由该测试文件构造。

默认 `DVRP_BUILD_REFERENCE_SOLVER=OFF`，不查找/链接 CPLEX，仍可构建并运行原25项测试。启用时必须 `BUILD_TESTING=ON`；后端目前只接受 `ROUTEOPT_SOLVER=CPLEX`。Windows 会复制匹配的 DLL 到测试 exe 同目录；特殊安装可指定 `DVRP_CPLEX_RUNTIME`。

## 6. 验证证据

2026-10-07，本机 Windows x64、VS2022 / MSVC19.44、CPLEX22.1.0：Release / Debug 均 **40/40 CTest 通过**；关闭对照选项后 Release **25/25 通过**，包含434个TXT读取。未使用 `-u` 或已知最优上界。

| 测试组 | 已验证结果 |
| --- | --- |
| enumeration / limits | 两客户一外部起点恰好9条；五客户五外部起点1955条，完整 LP/MIP 可解；六客户或六外部起点拒绝 |
| resources / nonmetric / validation | 专属容量/时间、等待/服务/H、ID/key 分离、截断距离反例、非法输入和输入副本 |
| duals / lp / mip | 手算路线最优18，固定7后阶段成本25；实际行列系数、dual、reduced cost 与互补性 |
| fractional | 同一阶段 LP=30、MIP=40，防止把连续模型当成整数模型 |
| empty / infeasible | 空阶段、同位置不同外部车辆、缺覆盖列、各行有列但互相冲突的真正不可行模型 |
| status / lifecycle | LP/MIP 状态码区分；实际 CPLEX 参数越界返回错误；连续20次 LP+MIP 独立调用 |
| dynamic | MIP 自动求解0、5、15三个阶段；承诺与车辆身份不变，最终实际距离28；模拟超时有/无解均不派车 |
| time_limit | 实际传入零秒上限；本次仍得到 LP状态1 / MIP状态101，即最优，未实际触发超时 |

CPLEX 配置入口对不支持的 Gurobi 后端及 `BUILD_TESTING=OFF` 明确报错。原版源码无差异；本轮未重跑静态 C204/R204，历史 R204 风险见[动态模块记录](DVRP动态状态与事件推进模块.md#8-构建与测试)。

## 7. 限制与后续

- 这是有界规模的测试工具，不用于434个百客户实例求解，不含根 RMP、标签、列生成、分支或 cuts。
- elementary 完整 LP 不能直接要求与未收紧的 ng 松弛 LP 相等；当前 reduced-cost 接口不包含分支行 dual。
- 最优结论仍采用浮点求解器和容差，不是有理数精确证明；多个最优路线不要求顺序一致。
- 未验证 Gurobi、Linux/macOS、真实许可证缺失；重复调用不是专用内存泄漏检测。真实超时有解/无解的映射及动态停止经确定性测试，未宣称本机已触发两种真实超时。
- [多起点RMP](DVRP多起点RMP模块.md)已用本工具完成对照；下一步确认生产pricing接口，再继续验证根列生成。总体设计中的pricing / 分支选择仍需单独确认。
