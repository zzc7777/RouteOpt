# DVRP 多起点定价模块

> 状态：已完成（普通/桶图双版本，支持RF和专属有向弧限制；不是完整BP）
>
> 最后更新：2026-10-25
>
> 详细执行顺序见[流程与方法导读](DVRP多起点定价流程与方法导读.md)；稳定接口见[文件与接口](../设计/M1-标准BP-DVRP/03-多起点定价文件与接口.md)。已完成需求 V3 合并到本文，不再保留并行需求。

## 1. 当前能力

给定同一个 StageProblem 和 RmpDuals，逐起点寻找 elementary 可行路线的最小 reduced cost；支持 Phase-I/II。调用方显式选择：

| 版本 | 实际处理方式 |
| --- | --- |
| PlainLabeling | 普通标签修正算法：每个客户的非支配标签集＋工作队列；不构建桶图 |
| BucketGraph | RouteOpt 前向桶图骨架的副本适配：客户×时间 bin、后继弧、bin 内 SCC、桶内/跨桶支配、已完成 bin 的成本界 |

两版共用资源检查和支配判据，不共用标签调度循环。桶图不是完整原版配置；不含双向扩展/拼接、ng、cuts、成本弧消除/jump arcs、二维/自适应桶或启发式定价。

生产文件在 src/pricing；现由[根列生成控制器](DVRP根节点列生成模块.md)及测试调用。已有reader CLI未改变，尚无分支树或动态自动求解。

2026-10-25扩展：可传入 `BranchContext`；共用扩展、返场及支配检查已支持RF/起点专属有向弧，dual须与该上下文匹配。具体规则、正确性依据及新增测试见[分支模块](DVRP双策略分支与受限列生成模块.md)和[方法导读](DVRP双策略分支流程与方法导读.md)。下文无分支实验保留其原验证范围。

## 2. 文件与接口

路径相对 packages_dvrp/application/dvrp/；共 **2 个生产头文件、4 个生产 cpp、1 个测试头文件、6 个测试 cpp**，另修改 CMake：

| 文件 | 职责 |
| --- | --- |
| [pricing_controller.hpp](../../../packages_dvrp/application/dvrp/src/pricing/include/pricing_controller.hpp) | 公开控制器、版本/预算、候选与状态 |
| [pricing_internal.hpp](../../../packages_dvrp/application/dvrp/src/pricing/include/pricing_internal.hpp) | 阶段数组、标签、桶、工作区等内部类型 |
| [pricing_controller.cpp](../../../packages_dvrp/application/dvrp/src/pricing/src/pricing_controller.cpp) | 校验、快照、逐起点调用；资源、支配、回溯及独立复核 |
| [labeling_plain.cpp](../../../packages_dvrp/application/dvrp/src/pricing/src/labeling_plain.cpp) | 无桶工作队列 |
| [bucket_graph.cpp](../../../packages_dvrp/application/dvrp/src/pricing/src/bucket_graph.cpp) | 桶后继表、bin 内 SCC 和拓扑顺序 |
| [labeling_bucket.cpp](../../../packages_dvrp/application/dvrp/src/pricing/src/labeling_bucket.cpp) | 前向桶循环、跨桶支配、区间末排序与成本界 |
| [pricing_test.cpp](../../../packages_dvrp/application/dvrp/test/pricing/pricing_test.cpp) | 无 solver 的资源/图结构/独立枚举对照 |
| [pricing_property_test.cpp](../../../packages_dvrp/application/dvrp/test/pricing/pricing_property_test.cpp) | 独立子集/排列穷举、随机、变形、浮点及编号上限测试；不调用旧枚举器 |
| [pricing_rmp_test.cpp](../../../packages_dvrp/application/dvrp/test/pricing/pricing_rmp_test.cpp) | 两版分别驱动真实 RMP，并与完整 LP 对照 |
| [pricing_instance_test.cpp](../../../packages_dvrp/application/dvrp/test/pricing/pricing_instance_test.cpp) | 真实TXT初始阶段的限时定价实验、两版相同dual对照；不链接穷举器 |
| [mip_reference/](../../../packages_dvrp/application/dvrp/test/pricing/mip_reference/)（1头、2cpp） | 独立单路径MIP、路线复核、逐轮CG与支配后缀审核；不属于生产pricing |

公开方法为 DVRP_PricingController(stage)、price(duals, options)。控制器复制阶段并预计算客户弧，调用方不需延长原输入寿命；每次定价重新创建标签、桶图和成本界。不同阶段必须重建控制器。

Pricing 不持有 Solver，不写 LP，不修改动态状态。候选使用原有 StageRoute，原客户 ID 和 startKey 均保留。调用方检查结果后交给 RMP.addRoutes()，后者独立校验、重算成本并写入模型。

## 3. 已落地的业务边界

- 内部当前源为 0、客户为 1..n、共同返回为 n+1；车辆 key 与客户 ID 分离。不同起点不能互相支配。
- 起点时间来自 startTime，后续容量来自 remainingCapacity；不再服务外部起点所代表的承诺客户。
- 提前到达等待，检查服务开始时间窗、需求、服务时间及 H。实际时间用 double；桶不改变或舍入时间。
- 距离沿用 routeOptDistance。直接返场失败仍可扩展客户，不使用不适合截断距离的“三角不等式”剪枝。
- 原始路线至少含一位客户；外部路线允许可行的直接返场。已完成/未披露客户不在输入阶段中。
- Phase-I：rc = −客户 dual 之和−对应外部 dual；Phase-II 再加真实距离。原始路线不减外部 dual；固定成本不进入 rc。两阶段均使用真实旅行时间。
- 完整 visited 禁止重复访问。每起点返回最小 rc 和一条见证；仅最小 rc < −1e−5 时放入负列候选。并列最优可返回不同序列。
- 不使用已知上界、名称特判或静默截断搜索；超时/资源耗尽不冒充精确完成。

## 4. 结果、预算与正确性含义

| 状态/字段 | 调用方可以怎样理解 |
| --- | --- |
| Complete | 所有起点搜索结束；不代表一定没有负列 |
| Complete 且 candidates 为空 | 未找到低于负列阈值的路线；根CG仍须用逐起点最小rc核对数值下界和gap，不能仅据此宣布LP收敛 |
| hasFeasibleRoute=false | 此起点无可行路线；最小 rc 为空，不用 0 代替 |
| TimeLimit / ResourceLimit / Error | 整轮 starts/candidates 清空；不得据此判无负列或完整阶段不可行 |
| stageId / modelRevision / phase | 结论所属上下文；pricing 无法知道 RMP 最新版本，调用方必须核对 |

默认整轮 price 时限 60 秒，所有起点共用；包含起点准备、建图、SCC、扩展、回溯和检查，采用协作式时间检查，不是强制进程中断。构造函数的一次性阶段复制/客户距离矩阵预处理不在 price 计时内。

默认每起点最多保留 200000 个标签（包含失效前驱），每客户 25 个桶，100000 个桶节点、5000000 个后继/依赖邻接条目。图预算在分配前检查；依赖条目包含正反邻接，按构建量保守累计，不等于实际存活弧数。这些是规模保护，不是总进程内存字节上限。

统计给出保留/扩展标签、支配比较、成本界跳过、SCC 扫描、跨桶扩展/支配、桶规模及建图/搜索耗时。计数通常跨起点累计；peakRetainedLabels 是单起点最大保留记录数，不是实测进程峰值内存。每次调用都重建图，不声称已实现缓存。

输入阶段非法在构造时抛异常；price 内非法 dual/选项或非有限运算返回 Error。内存分配失败返回 ResourceLimit。已失效标签记录不删除，以保留后继回溯链。

## 5. 构建与验证

仓库根目录执行；下面是本机 CPLEX 22.1 路径，其他机器需替换：

~~~powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-pricing -DDVRP_BUILD_PRICING_TESTS=ON -DDVRP_BUILD_RMP_TESTS=ON -DDVRP_BUILD_REFERENCE_SOLVER=ON -DROUTEOPT_SOLVER=CPLEX "-DCPLEX_ROOT=D:/Applications/Development Tools/CPLEX_Studio221/cplex"
cmake --build build/dvrp-pricing --config Release -j 8
ctest --test-dir build/dvrp-pricing -C Release --output-on-failure
cmake --build build/dvrp-pricing --config Debug -j 8
ctest --test-dir build/dvrp-pricing -C Debug --output-on-failure
~~~

无 CPLEX 也能验证两版定价：

~~~powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-pricing-plain -DDVRP_BUILD_PRICING_TESTS=ON -DDVRP_BUILD_RMP_TESTS=OFF -DDVRP_BUILD_REFERENCE_SOLVER=OFF
cmake --build build/dvrp-pricing-plain --config Release -j 8
ctest --test-dir build/dvrp-pricing-plain -C Release --output-on-failure
~~~

2026-10-15，MSVC 19.44，测试加强后的结果如下；共23组无solver定价测试（原14组＋9组性质测试）、8组RMP接线及1组真实实例2客户子集冒烟。BUILD_TESTING=OFF 且打开定价测试时配置会明确失败。

| 配置 | CTest结果 |
| --- | --- |
| 三个测试开关ON，CPLEX，Release | 88/88 |
| 同上，Debug | 88/88 |
| 仅定价测试ON，无CPLEX，Release | 48/48 |
| 仅定价测试ON，无CPLEX，MSVC AddressSanitizer | 48/48，未报告内存访问错误 |
| 三个开关OFF，默认Release | 25/25 |

核心证据：100组固定种子小阶段、两种 Phase、1/3/25 桶与枚举三方核对；5客户/5外部起点的1955条完整路线；零资源 SCC、跨桶支配、浮点桶边界、非度量返场反例、预算中断。另有12客户紧容量例，证明生产代码没有套用对照器的5客户限制，不作为大实例性能证据。

RMP 接线测试使用真正的两版 pricing 供列，枚举器只核对；最终与完整 LP 一致，包括人工量正的完整不可行情形、Phase-I→II、LP30/MIP40分数例。性能计数可运行 Release/dvrp_pricing_test.exe dense 查看；不能从小例推断桶图一定更快。

本次进一步验证没有修改生产算法，新增证据为：

| 测试 | 核对内容 |
| --- | --- |
| 1000个随机阶段，4个固定种子 | 两种Phase × 普通版/五种桶数量（1/2/7/25/64），共12000次定价，对照独立子集/排列穷举 |
| 8个7客户阶段 | 每个起点完整排列；其中1例有54799条可行路线，两版最小rc及见证与穷举一致 |
| 100个变形阶段 | 编号/顺序/镜像/固定成本不影响答案；外部dual偏移只影响对应起点 |
| 浮点、预算及数组边界 | 负列阈值和桶边界两侧、返场容差、部分起点完成后中断清空、恢复重试；63/64/65/1001客户编号及1002拒绝 |
| 100个RMP阶段，300次CG | 固定普通版、固定桶图及交替切换，59个完整LP可行、41个不可行，均与完整LP一致 |

1001客户例使用容量1、已知解析答案，检查编号/位集及图构造边界，不是大型VRPTW搜索性能测试。新增穷举独立实现距离及资源，不调用生产扩展/支配或旧5客户枚举器；它是测试内的7客户工具，不改变CPLEX对照器的规模限制。原测试仍保留。

内存检查可在安装MSVC AddressSanitizer的本机复现（运行库目录按安装版本调整）：

~~~powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-pricing-asan -DDVRP_BUILD_PRICING_TESTS=ON -DDVRP_BUILD_RMP_TESTS=OFF -DDVRP_BUILD_REFERENCE_SOLVER=OFF "-DCMAKE_CXX_FLAGS=/DWIN32 /D_WINDOWS /GR /EHsc /fsanitize=address /Zi" "-DCMAKE_EXE_LINKER_FLAGS_RELWITHDEBINFO=/DEBUG /INCREMENTAL:NO"
cmake --build build/dvrp-pricing-asan --config RelWithDebInfo -j 6
$env:PATH='C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207/bin/Hostx64/x64;'+$env:PATH
ctest --test-dir build/dvrp-pricing-asan -C RelWithDebInfo --output-on-failure -j 2
~~~

该检查覆盖此次执行的reader/动态/定价路径，不包含CPLEX内部、未执行路径或所有类型的未定义行为，也不构成所有输入正确的数学证明。

原 packages 无源码变化。首轮实现时复用原 Release 可执行文件、不带 -u：C204=588.1（程序日志约113秒），独立复核100客户覆盖、容量、时间窗及3条路线成本通过；R204在180秒外部限时的检查中未取得完整结果，不报告静态回归全部通过。后续本次测试加强未改生产算法，没有重复静态性能运行。历史R204标签扩容访问冲突风险未在本模块修复。

### 5.1 真实较大实例定价实验（无穷举）

以下不是完整VRP求解时间。2026-10-15，Release/CPLEX，逐个运行；每次price限15秒、20万保留标签，桶版25桶。空RMP的Phase-I dual与单客户初始列RMP的Phase-II dual分别取一次，两版用同一份dual；没有CG迭代、分支或已知上界。

文件均位于instance/，后缀为 `_D_25_E_25.txt`，各含100客户；严格使用时间0已披露客户，均无外部起点，不提前公开未来请求。

| 实例 / 本次客户数 | Phase-I 普通 / 桶图 | Phase-II 普通 / 桶图 |
| --- | --- | --- |
| c101 / 前20个已披露客户 | 完成11.31s / 8.14s，最小rc均−9 | 完成6.14s / 4.20s，最小rc均−552.8 |
| c101 / 前50个已披露客户 | 均15秒中断 | 均15秒中断 |
| c101 / 完整初始79客户 | 均15秒中断 | 均15秒中断 |
| r101 / 完整初始80客户 | 均15秒中断 | 均15秒中断 |
| rc101 / 完整初始79客户 | 均15秒中断 | 均15秒中断 |
| c204 / 完整初始81客户 | 均15秒中断 | 均15秒中断 |
| r204 / 完整初始79客户 | 均15秒中断 | 均15秒中断 |

20客户返回路线独立检查通过；Phase-I不优化真实距离，所以并列最优见证的距离可以不同。其余24次price均返回TimeLimit并清空候选，没有触发20万标签上限或崩溃。每次约1.65～1.91亿次支配比较；桶图构建约0.006～0.028秒，不能把这批中断结果解释为桶图已解决规模问题。没有大实例最小rc的穷举证明，也没有大规模多外部起点实验。

复现C101完整初始阶段（退出码3表示实验未完成，不是可行性错误）：

~~~powershell
cmake --build build/dvrp-pricing --config Release --target dvrp_pricing_instance_test -j 6
.\build\dvrp-pricing\Release\dvrp_pricing_instance_test.exe .\packages_dvrp\application\dvrp\instance\c101_D_25_E_25.txt 0 15 200000
~~~

参数依次为文件、最多取多少已披露客户（0=全部）、每次定价秒数、保留标签上限；改成20可复现子集例。程序输出阶段规模、每版状态/耗时/标签/支配统计、见证及校验；独立函数流程见导读第12节。测试日志可留在build内，不提交构建产物。

#### 缩减规模补测（同日、相同预算）

按用户要求，只截取时间0已披露客户的前N个，保留坐标、容量、需求、时间窗及服务时间，不改原TXT或生产算法。共补测12组配置、48次price：34次Complete、14次TimeLimit；8组在两个Phase均完成双版本对照，4组未完成全部对照。所有34条返回见证独立检查通过，无Error或ResourceLimit，未调用穷举器。

下表列出每类实例本轮双版本均完成的较大子集；耗时为一次price，不含RMP求解或整个阶段求解：

| 实例 / 客户数 | Phase-I 普通 / 桶图（秒） | Phase-II 普通 / 桶图（秒） | 两版最小rc（Phase-I / II） |
| --- | --- | --- | --- |
| c101 / 20（复测） | 11.30 / 8.09 | 6.13 / 4.07 | −9 / −552.8 |
| r101 / 50 | 4.26 / 3.74 | 2.63 / 2.05 | −7 / −245.2 |
| rc101 / 40 | 0.46 / 0.25 | 0.44 / 0.24 | −8 / −598.1 |
| c204 / 10 | 0.56 / 0.33 | 1.18 / 0.54 | −10 / −334.3 |
| r204 / 10 | 0.59 / 0.29 | 1.36 / 0.78 | −10 / −297.8 |

其余完成配置为r101的20/40客户、rc101的20客户，两个Phase的最小rc也均一致。未完成配置全部保留：

- c101的25客户、c204与r204的15客户：两种Phase、两版均15秒中断。
- rc101的50客户：普通版两种Phase均15秒中断；桶图版分别11.49/8.63秒完成，最小rc为−9/−598.1，见证通过独立检查；由于普通版未完成，不能称为该规模双版本核对一致。

日志位于 `build/dvrp-pricing/reduced-<实例>-<客户数>.log`；例如将上述命令的文件改为 `r101_D_25_E_25.txt`、客户数改为50可复现。各配置仅运行一次，计时随机器负载变化；这些是特定客户子集和dual下的结果，不是通用规模上限或稳定加速比。仍无外部起点，仍非CG/BP；双版本一致与见证检查不替代独立最优性证明。本轮仅运行现有测试入口并更新记录，未改源码、重建或重跑完整CTest；上面的88/88等为前轮验证记录。

### 5.2 独立 MIP 核对（2026-10-18）

新增测试工具用独立弧模型求每个起点的最小rc，不复用标签扩展、支配或桶图。先用小实例全集校准MIP，再用MIP可行见证和闭合下界核对两版；只有相同答案而没有下界不算通过。路线还独立重算容量、时间、距离及rc。

新增9组CTest后，CPLEX Release/Debug均97/97通过；无solver定价Release48/48，默认Release25/25。覆盖60个随机阶段、10客户＋2外部起点动态快照、逐轮真实dual、支配后缀和数值/证据不足边界。

真实子集C101/20、R101/20、RC101/20、C204/10、R204/10的两组dual均通过独立最小rc核对；R101/20和RC101/20另完成逐轮审核的测试CG，路线LP分别530.4、440.3，独立上下界差均为0。不是完整100客户整数最优值，未改变生产调用链。

文件、模型连接性依据、实际方法、命令与限制统一见[定价MIP独立对照测试](DVRP定价MIP独立对照测试.md)。本轮仅测试/CMake/文档变化；5.1节保留的是前轮固定dual实验，不与本次测试CG混用。

## 6. 仍未实现

生产根/受限节点列生成及RF/专属有向弧适配已落地；尚无整数恢复、搜索树、ng、双向标签、cuts或CLI自动求解。已测试100客户输入中的79～81客户初始阶段，但未在上述预算内完成定价；尚无完整大实例BP性能结论。完整记忆的标签数量可能指数增长，RF进一步削弱支配，两版都会受显式预算限制。

本模块不输出StageSolution，不直接证明完整阶段整数不可行，不提供可立即剪枝的严格BP下界。生命周期、停止状态、总预算及数值下界由[CG模块](DVRP根节点列生成模块.md)负责；限制下的下界已按[设计05](../设计/M1-标准BP-DVRP/05-双策略分支与受限列生成文件与接口.md)核对，下一步另行设计共同搜索树与整数恢复。
