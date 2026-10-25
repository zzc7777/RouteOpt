# DVRP 双策略分支与受限列生成模块

> 状态：已完成（候选、限制与受限LP；不是完整BP）
>
> 最后更新：2026-10-25
>
> 执行方法见[详细导读](DVRP双策略分支流程与方法导读.md)，数学等价性和支配依据见[设计05](../设计/M1-标准BP-DVRP/05-双策略分支与受限列生成文件与接口.md)。

## 1. 已有能力及边界

同一个阶段可选择两种策略，各自支持普通标签或桶图定价：

- RF：客户/外部起点元素是否由同一路线覆盖；不要求客户相邻。
- 起点专属有向弧：仅统计指定 `startKey`、指定方向的连接，包含出发、客户间、返场及外部直接返场弧。

已实现候选流量、最接近0.5的选择、累计左右限制、受限定价、Phase-I恢复及指定子节点LP。未实现搜索树、整数恢复、全局上界、车辆数分支、ng、双向标签或动态自动派车。`main.cpp` 和 `dvrp.exe` 的读取行为不变。

```text
调用者 → solve(stage, context, options) → RMP ↔ 受限pricing
      ← 节点LP及匹配的列/下界快照
      → selectCandidate → makeChildren → 分别solve左右子节点
```

调用者目前是测试程序，未来才是共同BP树；没有偷偷用完整MIP代替生产列生成。

## 2. 文件及稳定接口

新增生产文件仅两个，位于应用功能目录，不占用未来通用BBT目录：

| 文件 | 职责 |
| --- | --- |
| [branching.hpp](../../../packages_dvrp/application/dvrp/src/branching/include/branching.hpp) | RF元素、带类型弧端点、决策、上下文、候选及4类公开函数 |
| [branching.cpp](../../../packages_dvrp/application/dvrp/src/branching/src/branching.cpp) | 规范化、路线许可、候选累计及左右限制；无Solver调用 |
| [branching_test.cpp](../../../packages_dvrp/application/dvrp/test/branching/branching_test.cpp) | 无solver的语义、独立枚举定价、支配后缀和异常测试 |
| [branching_cg_test.cpp](../../../packages_dvrp/application/dvrp/test/branching/branching_cg_test.cpp) | 显式分支行的完整LP/MIP、正式受限CG及版本/中断验证 |

已有RMP、pricing、根CG各增加带 `BranchContext` 的构造/求解重载；旧接口委托给空根上下文。`RootCgResult` 名称保留，新重载可返回指定子节点LP。上下文按值保存，含阶段内节点ID、策略和排序去重的完整决策；不会将客户ID、车辆key混作数组下标。

RMP构造为 `DVRP_RmpController(stage, context, RmpOptions{})`，第三参数显式填写以避免与旧 `RmpOptions` 重载的花括号调用歧义。pricing为 `DVRP_PricingController(stage, context)`。

修改范围仍为设计05的7个已有生产文件及CMake。两版标签主循环、桶图构造、动态、reader、solver副本和原版 `packages/` 未改。扩展/支配/返场检查集中在原共用 `Work` 方法中。

## 3. 实际规则

RF分开侧禁止同现；一起侧允许同现或同缺。外部元素以路线起点身份判断，外部空路线也覆盖该外部行。原始起点没有RF元素。

有向弧0侧只禁指定起点、方向；1侧通过等价列过滤实现。例如强制K的 `i→j`，所有含i或j的路线必须属于K且连续经过该连接；不含端点的路线仍可用。原始起点强制 `Source→j` 不会限制其他原始路线的发车。

不增加分支行或dual：客户/外部等式仍为原模型。RF一起侧和弧1侧的等价推导见设计05；RMP初始列过滤，显式违规加列报错且整批不写入。矛盾历史不视为输入错误，交由Phase-I和受限完整定价确认不可行。

RF支配增加“分支相关客户的visited状态相同”；外部真假在同一起点中固定。弧限制编译成客户起点禁用、指定前驱/后继及禁弧；冲突不会被后写规则覆盖。桶内/跨桶最终支配共用这些条件，原图仅作允许图的超集。

每个子节点重新建模型；不复用父Solver、dual或标签，也暂不热启动父列。LP、dual、pricing及界同时核对阶段、节点、完整决策、版本和Phase。数值下界沿用经设计05核对的 `D+m*delta` 向下修正，delta必须来自该节点完整定价。

候选用原lambda累计，整性/覆盖容差为 `TOLERANCE=1e-6`。`NoFractionalCandidate` 仅表示未找到超过容差的候选，不表示lambda整数或节点整数最优；恢复整数见证留给下一模块。

## 4. 测试与复现

仓库根目录，CPLEX路径按本机替换：

```powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-branching -DDVRP_BUILD_REFERENCE_SOLVER=ON -DDVRP_BUILD_RMP_TESTS=ON -DDVRP_BUILD_PRICING_TESTS=ON -DDVRP_BUILD_ROOT_CG_TESTS=ON -DDVRP_BUILD_BRANCHING_TESTS=ON -DDVRP_BUILD_BRANCHING_CG_TESTS=ON -DROUTEOPT_SOLVER=CPLEX "-DCPLEX_ROOT=D:/Applications/Development Tools/CPLEX_Studio221/cplex"
cmake --build build/dvrp-branching --config Release --parallel 8
ctest --test-dir build/dvrp-branching -C Release --output-on-failure
cmake --build build/dvrp-branching --config Debug --parallel 8
ctest --test-dir build/dvrp-branching -C Debug --output-on-failure
```

仅新模块：`ctest --test-dir build/dvrp-branching -C Release -R dvrp_branching_ --output-on-failure`。

无solver构建只开 `DVRP_BUILD_BRANCHING_TESTS`、`DVRP_BUILD_PRICING_TESTS`，其余默认OFF；两个新开关独立，不要求把旧根测试也打开。

2026-10-25，MSVC19.44/CPLEX22.1：新增12组无solver、10组CPLEX测试；全量Release/Debug均 **135/135通过**，无solver Release及MSVC AddressSanitizer均 **60/60通过**，所有选项关闭的默认构建 **25/25通过**（含434实例读取）。未用已知最优上界。内存检测仅覆盖无solver路径，不包含CPLEX库内部。

本机内存检测复现（运行库路径随MSVC版本调整，只修改当前PowerShell的PATH）：

```powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-branching-asan -DDVRP_BUILD_BRANCHING_TESTS=ON -DDVRP_BUILD_PRICING_TESTS=ON "-DCMAKE_CXX_FLAGS=/DWIN32 /D_WINDOWS /GR /EHsc /fsanitize=address /Zi" "-DCMAKE_EXE_LINKER_FLAGS=/DEBUG"
cmake --build build/dvrp-branching-asan --config Release --parallel 6
$env:PATH="C:/Program Files (x86)/Microsoft Visual Studio/2022/BuildTools/VC/Tools/MSVC/14.44.35207/bin/Hostx64/x64;"+$env:PATH
ctest --test-dir build/dvrp-branching-asan -C Release --output-on-failure
```

独立证据包括：

- RF三客户例父LP30，两子LP40，受限单列不足时Phase-I恢复成功。
- 200个2～5客户随机阶段、800次双后端/双Phase定价，对照完整枚举最小rc；另覆盖全部类型首尾弧、直接返场、同号客户/车辆、多层冲突、零需求/零距离及RF支配后缀。
- 80个2～5客户受限随机阶段、160次生产CG，对照“全集列＋显式分支等式”的CPLEX LP；没有用生产过滤构造答案。
- 对22个完整整数覆盖进行0/1分割检查，6类候选核对父MIP等于两子MIP最优值的较小者；803个支配后缀验证不漏可行延续。同覆盖、不同顺序的分数路线不能误报整数解。
- 200客户受限RMP构造、批量写入原子性、旧dual/节点串用、时间/标签/迭代中断及空限制回归。

枚举工具仍最多5客户/5外部起点；这是测试保护，生产无该限制。测试程序输出两版定价调用次数、标签数和耗时；这些不是正式大实例性能结论。浮点测试不是所有输入的数学证明。

静态回归使用原Release程序，不带 `-u`：C204的UB=LB=588.1、程序118.29秒，独立检查3条路线的100客户覆盖、容量、时间窗及成本通过；R204在180秒外部限时内未完成，已停止，本轮未见崩溃。由于与其他验证有并行，不作性能比较。原版源码无差异；历史R204标签扩容访问冲突风险仍保留，不能报告静态回归全部通过。

复现静态命令：在 `packages/application/cvrp` 下运行 `./bin/Release/cvrp.exe ./instance/vrptw/solomon-100/c204.txt` 或 `r204.txt`；R204需外部限时。日志在忽略的 `build/dvrp-branching/static-{c204,r204}.log/.err`，不提交build产物。

## 5. 风险和下一步

深层RF会削弱支配，完整访问记忆本身也可能指数增长；不承诺100客户性能。只验证CPLEX，不宣称Gurobi已适配。无分数候选时的生产整数恢复、全局树终止和动态接回仍未实现；不能把该模块结果交给 `acceptSolution()`。

下一步先写共同BP搜索树与整数恢复的简版需求及接口，复用本模块两种策略；不再复制两套树。
