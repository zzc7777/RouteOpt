# DVRP 定价 MIP 独立对照测试

> 状态：已完成（仅测试工具，不是生产 pricing 或完整 BP）
>
> 最后更新：2026-10-18
>
> 生产能力见[定价模块](DVRP多起点定价模块.md)；本页同时承担本测试工具的流程与方法导读。

## 1. 为什么增加这个工具

普通版和桶图版共享资源扩展、支配与回溯；答案相同不能排除共享错误。本工具独立建立单条 elementary 路线的 MIP，用 CPLEX 的可行解和下界核对每个起点的最小 reduced cost。

它不同于[完整集合划分对照器](DVRP小实例集合划分对照求解器.md)：后者先枚举路线再选择车队；这里直接选择一条路径的弧和客户，不预先枚举路线。主问题、生产 pricing、动态模块、reader CLI 和原版 packages 均未修改。

新增文件位于 `packages_dvrp/application/dvrp/test/pricing/mip_reference/`：

| 文件 | 职责 |
| --- | --- |
| [pricing_mip.hpp](../../../packages_dvrp/application/dvrp/test/pricing/mip_reference/pricing_mip.hpp) | 输入输出、状态、路线检查及证据判定接口；不包含 CPLEX 头 |
| [pricing_mip.cpp](../../../packages_dvrp/application/dvrp/test/pricing/mip_reference/pricing_mip.cpp) | 独立路径模型、Solver 资源释放、路线恢复与上下界 |
| [pricing_mip_test.cpp](../../../packages_dvrp/application/dvrp/test/pricing/mip_reference/pricing_mip_test.cpp) | 穷举校准、双版本核对、动态快照、测试 CG、支配后缀检查 |

另修改现有 CMake 注册目标与9组测试；不建立新 library，不增加生产入口。

## 2. 输入、模型和输出

`solvePricingMip(stage, duals, startKey, seconds)` 只读输入，同步求解一个指定起点，返回结果值对象；不保存输入引用或改变 RMP。Solver 环境和模型由本次调用独占，退出时释放。

内部重新编号源S、客户、终点D，保留原ID和车辆key映射。模型包括：

- 二元弧变量x、客户选择变量y；源恰好流出一次、终点恰好流入一次，每个选中客户入度和出度均为1；
- 原始起点不允许S→D，外部起点允许；客户并非全部必须访问，这是定价而不是车队求解；
- 总需求不超过本起点 remainingCapacity，时间从 startTime 传播，检查服务开始窗口和最晚返场H；
- 顺序变量u：选中客户弧i→j要求u_j≥u_i+1，显式排除所有客户环，包括零旅行/服务时间的小环。

这些约束的连接性依据是：入出度限制把选中弧分解为一条S→D路径与可能的客户环；顺序约束沿环相加会得到0≥环长度，因此环不可能存在。最终只有一条不重复客户的路径。

时间变量允许等待；对给定序列，若存在可行时间安排，按最早到达、等待至窗口开启、服务完成立即出发重算不会更晚，因此仍可行。输出始终按这个最早执行规则检查，不把MIP任意时间变量当作车辆执行计划。

时间条件使用由变量上界推导的M，不猜巨大常数：客户服务开始时间在[0,H+1e-6]，客户弧的M为H+1e-6+服务时间+旅行时间。未选客户允许时间0，其窗口只在y=1时约束。容量、窗口和返场采用与生产相同的1e-6业务容差；CPLEX可行性/整数容差设为1e-9，MIP相对/绝对gap均设为0。

Phase-I的目标是−Σπ_i y_i，Phase-II增加Σc_ij x_ij。外部起点的常数−μ_k在读取目标和下界后同时扣除；原始起点不扣μ，固定成本不进入定价。

## 3. 实际执行顺序与方法

### 3.1 先校准 MIP 自身：againstEnumeration()

对最多5客户的小阶段调用原独立路线枚举器，计算每个起点、两个Phase的真实最小rc，再核对MIP下界和结果。随后同一阶段核对普通标签、桶图与MIP。

`randomOracle()` 固定种子20261018产生60个阶段，含0～5客户和0～2外部起点、正负dual、零资源、可行和不可行情形。原5客户对照器与既有7客户性质测试的上限不变；大实例入口不调用枚举。

### 3.2 建模和求解：solvePricingMip() → Model::build()

检查阶段、起点和dual的键/数值，独立计算截断欧氏距离，再建立上述弧模型。复用已复制的 Solver API，不调用生产距离、标签扩展、支配或桶图方法。

CPLEX的addVars接口忽略变量类型参数，因此在加入变量后显式设置并读取类型核验，防止误求LP松弛。仅“无客户的原始起点不允许空路线”直接解析判不可行，其余正常建模。

### 3.3 恢复路线：Model::extract() → checkPricingRoute()

读取二元弧，检查变量范围和整数性，从源沿后继走到终点；检查没有断链、重复点或未连接分量，且路径客户与y一致。

`checkPricingRoute()` 从原始数据独立重算最早服务时间、累计需求、返场、真实距离和rc。该函数也用于检查两版标签算法返回的路线，不调用生产可行性检查。MIP目标必须与恢复路线的rc一致。

例如hand用例：客户11在(2,0)、29在(4,0)，需求各2、dual各12。原始车场在(0,0)，路线0→11→29→返回的距离8，rc=8−12−12=−16。外部42在(3,0)、T=10、剩余容量2、μ=3，只能再服务一人；服务11再返场距离3，rc=3−12−3=−12。这个区别能检查起点资源和μ是否正确传递。

### 3.4 核对最小值：audit() → certifyPricingMinimum()

`audit()` 先逐起点求独立MIP，再让两版用相同阶段和dual定价。检查上下文、所有起点、返回路线及负列集合；某个起点正确不能替代其他起点的核对。

对已独立验证的标签路线rc值v和MIP有效下界L，有L≤真正最小rc≤v。只有v−L在绝对容差1e-6内才确认最小值。相同的可行解而没有闭合下界不算通过；MIP找到更好的路线或下界与可行见证冲突则失败。

若标签声称没有任何可行路线，需MIP证明不可行。`Unfinished`可保留已有解和下界，但不能仅凭已有解确认最优；下界仍可用于上述区间核对。生产标签中断仍清空整轮候选，本工具不会绕过该边界。

### 3.5 动态快照与逐轮列生成：dynamicStage() / cg()

`dynamicStage()` 给动态模块一个12客户手工实例：时间0接受两条合法路线，推进到披露时刻5。得到10个待规划客户及2个外部起点，出发时间分别30、35，剩余容量均4；承诺客户已排除。`multistart()`还额外加入一个明确的合成零剩余容量起点，检查空客户续行。

`cg()` 复用RMP，逐轮求dual并调用上述audit，交替使用两版生产pricing的候选加列；MIP只审核，不供列。涵盖可行性阶段、中间轮和接近0的终止轮。这个循环仍在test中，不是生产根控制器。

Phase-II结束时，`finalLpCertificate()`独立检查每个客户和外部起点的LP覆盖、非负变量与总成本，并用MIP下界复核完整路线LP的下界。若有n客户、各起点定价下界L_k，则保守下界为：

~~~text
Σ客户dual + Σ外部dual + n·min(0,L_origin) + Σ外部min(0,L_k)
~~~

因为每条原始路线至少覆盖一个客户，原始路线变量总和≤n；每个外部起点的变量总和=1。此处成本不含固定成本，上下界在浮点容差内核对；这没有给生产BP新增可剪枝下界接口。小阶段另外与完整集合划分LP比较。

### 3.6 直接检查支配：dominance()

独立计算一个4客户阶段的所有合法前缀，对生产`Work::dominates()`判定成立的标签对，从小实例全集取出被支配标签的每个可行后缀，再拼到支配者之后独立核验。检查替代后仍合法且rc不更大。这是针对选定用例的语义审计，不是穷尽所有标签的形式化证明。

## 4. 构建、测试与实测证据

沿用[定价模块的CPLEX配置](DVRP多起点定价模块.md)，需同时开启DVRP_BUILD_PRICING_TESTS和DVRP_BUILD_RMP_TESTS。无solver构建不编译本工具。仓库根目录执行：

~~~powershell
cmake --build build/dvrp-pricing --config Release --target dvrp_pricing_mip_test -j 6
ctest --test-dir build/dvrp-pricing -C Release -R "^dvrp_pricing_mip_" --output-on-failure
.\build\dvrp-pricing\Release\dvrp_pricing_mip_test.exe real .\packages_dvrp\application\dvrp\instance\c101_D_25_E_25.txt 20 15
.\build\dvrp-pricing\Release\dvrp_pricing_mip_test.exe real_cg .\packages_dvrp\application\dvrp\instance\r101_D_25_E_25.txt 20 15
~~~

`real`核对空RMP/单客户初始列RMP的两组dual；`real_cg`额外从空RMP运行逐轮审核的测试CG。参数是文件、时间0已披露客户的前N个、时限秒数；时限分别应用于一次起点MIP和一次生产price，不是整个CG的墙钟上限。CG另有限制250轮。默认10客户/10秒，本MIP工具保护上限50客户，与生产pricing上限无关。

退出0为本组核对通过，3为定价未完成、MIP证据不足、初始真实列不足或CG轮数耗尽，1为输入/运行/核对错误；原TXT不变，不提前披露请求，不使用已知上界。状态0/1/2/3在MIP日志内分别为Optimal/Infeasible/Unfinished/Error，不是进程退出码。

2026-10-18，新增9组CTest：hand、random、numeric、certificate、multistart、cg、dominance、validation、real。全部开关开启时Release/Debug均97/97通过；仅无solver定价开启时Release48/48，全部关闭时Release25/25。本轮没有对新增CPLEX工具运行AddressSanitizer。

| 独立证据 | 本次结果 |
| --- | --- |
| 60个随机小阶段，两个Phase，逐起点MIP/穷举/两版标签核对 | 全部通过 |
| 数值/连通性 | 零时间断开环、截断距离非度量例、零资源可行路径、负dual、负列阈值、时间窗/返场边界均通过 |
| 证据判定 | 相同incumbent但无下界/差距未闭合不算通过；发现更优路线或错误无路结论必须失败 |
| 支配后缀 | 1264对成立关系，其中1056对非平凡；1976个后缀替代检查通过 |
| 动态生成阶段的测试CG | 10客户＋2外部起点，Phase-I 8轮、Phase-II 6轮；路线LP=117、独立下界=117 |
| 真实R101前10客户的CTest CG | 路线LP=249.4，上下界差约5.7e-14 |
| 真实子集固定dual | C101/20、R101/20、RC101/20、C204/10、R204/10，两种Phase的最小rc均被MIP闭合下界核对 |
| 真实子集测试CG | R101/20：26轮Phase-I＋31轮Phase-II，LP=530.4；RC101/20：24＋37轮，LP=440.3；逐轮审核，最终独立上下界差均为0，非完整100客户结果 |

本机零秒MIP调用仍由预处理返回Optimal（raw101），不宣称触发真实超时；有解/无解及证据不足的判定由确定性测试覆盖。终端详细日志保留在build/dvrp-pricing/，不提交构建产物。

## 5. 限制

- CPLEX和模型实现也可能有错误，因此先用独立枚举校准；本次是多种独立证据，不是对所有输入的形式化证明。
- MIP没有标签/桶图依赖，但仍使用同一业务定义、Solver和浮点计算。极端尺度、边界容差和大规模未覆盖输入不作保证。
- 真实TXT子集均为时间0，无外部起点；多起点证据来自上述手工动态实例及随机阶段，不混称大规模真实动态实验。
- 无RF、cuts、ng、双向标签、生产根控制器、整数恢复或自动动态求解。LP目标不等于完整整数车队最优值。
- 本轮只改测试/CMake/文档，原版静态程序和DVRP生产算法不变；未重跑耗时静态C204/R204求解，既有R204风险仍保留。
