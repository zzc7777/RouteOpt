# DVRP 多起点定价：流程与方法导读

> 状态：已完成（对应当前双版本定价源码）
>
> 最后更新：2026-10-18
>
> 能力、构建命令和验证记录见[模块说明](DVRP多起点定价模块.md)，稳定接口见[设计](../设计/M1-标准BP-DVRP/03-多起点定价文件与接口.md)。本文只讲实际执行，不把测试控制循环称为完整 BP。

## 1. 先明确：谁调用谁

Pricing 不决定“整支车队怎么跑”，而是回答：**给定本阶段和这轮 dual，从各起点出发，还有没有值得加入主问题的可行路线？**

~~~text
测试 columnGeneration(stage, backend)
  ├─ RMP.solveLp()：给出当前 LP 的 dual
  ├─ Pricing.price(duals, options)：给出各起点最小 rc 及负列
  ├─ RMP.addRoutes()：复核候选后加入 LP
  └─ 再求 LP，重新取 dual
~~~

目前这个循环位于[pricing_rmp_test.cpp](../../../packages_dvrp/application/dvrp/test/pricing/pricing_rmp_test.cpp)，未来生产根控制器尚未实现。main.cpp 仍是 reader。

StageProblem、RmpDuals、StageRoute 是数据；DVRP_PricingController 才是执行方法的对象。Label、Bucket、Work 都是内部工作区，不传给动态控制器，也不是车辆实际状态。

## 2. 一次性准备阶段：构造函数

实际方法：[DVRP_PricingController(stage) → PreparedStage](../../../packages_dvrp/application/dvrp/src/pricing/src/pricing_controller.cpp)。

输入是动态层已构造的 StageProblem。先复制，再检查客户、起点、时间、容量和返回点是否合法；例如外部起点所代表的承诺客户不能仍出现在本阶段待服务集合中。

然后建立内部连续编号。假如原客户为5、8，内部变成1、2；当前搜索源是0，独立返回点是3。外部车辆 key=42 仍是42，它不是第42号数组节点。

客户之间及客户到返回点的距离一次计算，各起点共享；从具体起点出发的距离随后单独计算。构造结束只获得可查询的静态输入，尚无路线、标签或 LP 解。

## 3. 开始一轮：price()

公开方法：[price(duals, options)](../../../packages_dvrp/application/dvrp/src/pricing/src/pricing_controller.cpp)。

它检查：

- dual 是否属于同一 stageId，是否包含全部且仅包含本阶段客户/外部起点；
- dual 是否有限，Phase 是否为可行性或成本阶段；
- 版本选项和预算是否合法。

负 dual 合法，不会因为是负数被拒绝。modelRevision 被带回结果，是否仍对应最新 RMP 由调用方检查；pricing 自己不持有 RMP，不能自动知道它是否刚被加列。

随后先求原始起点，再按阶段顺序求每个外部起点。所有起点共用本轮时限；任何一个未完成，都不能声称整轮无负列。每轮重建工作区，上一轮 dual 的标签和成本界不会残留。

## 4. 建立一个起点的搜索：Work()

内部方法：[Work 的构造函数](../../../packages_dvrp/application/dvrp/src/pricing/src/pricing_controller.cpp)。

它生成源标签，记录：

| 字段 | 起始值及意义 |
| --- | --- |
| vertex | 0，当前逻辑起点 |
| time | startTime；外部车辆完成承诺客户后的出发时间 |
| load | 0；只累计本次续行新服务客户，不重复扣除过去载量 |
| visited | 空；承诺客户已不在本阶段客户集合 |
| cost | 0；本条可选续行路线的真实成本 |
| rc | 原始起点0；外部起点减一次该车的 mu |
| predecessor | 无；此后用稳定标签下标回溯 |

剩余容量限制使用 remainingCapacity。车辆过去已用容量已经体现在该字段，不能再从剩余容量扣一次。

Work 只拥有一个起点的标签；求完就销毁，再建立下一个起点的 Work。不能拿外部42的标签去支配外部43的标签。

## 5. 两版共用的三个动作

实际方法：[Work::extend / insert / complete](../../../packages_dvrp/application/dvrp/src/pricing/src/pricing_controller.cpp)。

### 5.1 extend(parent, j)：再服务客户 j

先看 visited；访问过 j 就拒绝。否则由上一标签的服务完成时间加旅行时间得到到达时间，提前到达则等到 readyTime。检查服务开始不超过 dueDate、累计需求不超过该起点剩余容量。

新标签保存服务完成时间、累计距离、已用容量及 visited。时间已超过最晚返场 H 也直接拒绝；但不因“现在直接回场太晚”就拒绝所有后续扩展。

rc 的更新相当于：

~~~python
next_rc = old_rc + distance_weight * travel_distance - customer_dual[j]
~~~

Phase-I 的 distance_weight=0，Phase-II=1。即使权重为0，旅行仍消耗真实时间，并记录真实距离。

### 5.2 insert() 与 dominates()：只保留必要标签

同起点、同末端，若 A 的时间、已用容量、rc 均不大于 B，且 A 的 visited 是 B 的子集，则 A 支配 B。

直觉是：B 还能走的后半段，A 也能走，且不会更贵。完整访问集合条件非常重要，不能只比较“更早、更便宜”。

新标签被旧标签支配就不入队；新标签支配旧标签则把旧标签标为失效。记录本身不删除，因为其他标签可能仍把它作为前驱。比较不使用“略差也当作更优”的宽松容差。

普通版对同末端所有有效标签检查；桶版先做同桶检查，更早桶的检查在扩展前进行。

### 5.3 complete(id)：尝试结束路线

从标签末端连接共同返回点，检查到达不超过 H；可行则计算整条路线 rc，并与该起点当前最优见证比较。

原始源标签不能直接结束，保证原始路线至少服务一人。外部源标签可以直接返场，所以没有待服务客户时仍能为外部车提供路线。

结束失败不意味着不能继续扩展。例如截断距离下，(1.1,0)直接回原点需1.1，经(0.55,0)客户两段各0.5，合计1.0；H=1时后者可行。两版及对照测试均保留这种可能性。

## 6. 普通版：runPlain()

实现：[labeling_plain.cpp](../../../packages_dvrp/application/dvrp/src/pricing/src/labeling_plain.cpp)。

每个末端客户有一组有效标签，另有一个待扩展队列，初始放入源标签。

每次取一个标签：已失效或已扩展就跳过；否则先尝试返场，再逐个尝试未访问客户。资源可行且未被支配的新标签加入对应末端集合和队尾。直到队列为空。

允许负 reduced-cost 弧，不能按“第一次到某客户就是最优”处理；同一客户可以有多种资源/访问历史的标签。这是普通标签修正算法，不构建桶、桶弧、SCC，也不把桶数量设为1来冒充无桶。

## 7. 桶图版：多了什么，顺序如何

### 7.1 buildBucketGraph()：先确定调度结构

实现：[bucket_graph.cpp](../../../packages_dvrp/application/dvrp/src/pricing/src/bucket_graph.cpp)。

沿用 RouteOpt 节点×bin 组织，按服务完成时间划分共同区间，容量仍保留在标签中。每个桶有后继客户列表；本版未做成本弧消除，保守保留不同客户间连接。

每个 bin 内，以区间下界作乐观资源传播，找出哪些客户连接可能留在本 bin，建立依赖图。topologicalComponents() 计算 SCC 和缩点后的拓扑顺序，孤立客户也必须包含。

这只是保证处理先后顺序，不是实际路线。实际标签扩展后仍以真实时间落桶，例如从109出发、旅行3，应落到112所属的桶，不能留在乐观下界对应的桶。

### 7.2 runBucket()：时间区间 → SCC → 待扩展标签

实现：[labeling_bucket.cpp](../../../packages_dvrp/application/dvrp/src/pricing/src/labeling_bucket.cpp)。

先尝试外部直接返场，再由源向各客户播种标签；之后按 bin 从早到晚，在每个 bin 内按 SCC 拓扑顺序处理。

同一 SCC 内新标签可能回到刚扫描过的客户，因此反复扫描，直到该分量的待处理标签全部消耗。零旅行/服务时间不会被漏掉；visited 每次严格增加，也不会无限绕圈。

每个有效标签先查跨桶支配，再返场、扩展后继。实际扩展不得进入已完成的更早 bin 或同 bin 已完成 SCC；发现这种情况会报错而不是静默漏算。

### 7.3 checkIfDominated()：更早桶能否替代当前标签

只查同末端的更早 bin。时间更晚的桶无法以更早时间支配当前标签，不必反向检查。

检查时仍用完整的资源、rc、visited 条件。若某个更早标签支配它，当前标签失效；统计 crossBinDominated 记录这种情况。

### 7.4 sortLabelsInBinByRC()：完成一个 bin 后建立成本界

一个 bin 的所有 SCC 均完成后，清除失效列表项、按 rc 排序，记录本桶最小 rc 和截至本桶的前缀最小 rc。

以后检查更晚桶时，若前缀中最小 rc 都比当前标签更大，就不可能有支配者，可以跳过；排序列表扫描到 rc 过界也可停止。

这些界仅减少支配比较，不是阶段 LP 下界。只有已完成的更早 bin 才能用于此跳过逻辑；本版不据此删除定价弧，也不产生 jump arcs。

## 8. 恢复结果：result()

内部方法：[Work::result()](../../../packages_dvrp/application/dvrp/src/pricing/src/pricing_controller.cpp)。

搜索结束后，从最优标签沿 predecessor 回到源，反转序列，并把连续编号映射回原客户 ID。再独立重算时间、容量、真实成本和 rc，确认没有重复客户且与标签结果一致。

每个起点返回最小 rc 及一条见证路线；只有低于负列阈值才进入 candidates。没有可行路线时返回空最小值。不同访问顺序可能并列最优，不要求两版选择同一个序列。

price() 收集所有起点的结果并设置 Complete。异常或预算中断时清空整轮见证和候选，避免上层把不完整搜索当作证明。

## 9. 贯穿例子：外部42从130开始

当前 tau=100，原始/返回车场(0,0)，Q=10，H=200。外部42位于(10,0)，startTime=130、remainingCapacity=4；客户5在(12,0)、客户8在(14,0)，需求各2、服务各1、窗口[0,180]。

对续行 42→5→8→返回：

- Work 从130、load=0开始；
- extend(5)：132开始服务、133完成、load=2；
- extend(8)：135开始服务、136完成、load=4；
- complete()：150返场，真实距离2+2+14=18；
- Phase-II 若 pi5=8、pi8=8、mu42=4，rc=18−8−8−4=−2；
- result() 返回 StageRoute{startKey=42, customerSequence={5,8}}；并列反序也允许。

Phase-I 若三个 dual 均为1，rc=−3；真实距离和返场时间不变。这是手算测试用 dual，不声称是某个收敛 RMP 的解。

定价只回答这条路线值得加列，没有把车辆从100提前推进到130；实际车辆仍由动态控制器管理。

## 10. 下游接线与停止边界

测试 columnGeneration() 只在 RMP 最优且人工量为0后切 Phase-II，切换后重新求 LP，再取对应 dual。否则继续用 Phase-I 定价。

候选先送 RMP.addRoutes() 独立复核，再重新求解。Complete 且无负列时，与完整枚举 LP 核对；若显著负列全是模型已有列，则报一致性错误，不假装收敛。

LP30/MIP40 的测试明确：列生成完成仍可能是分数解，不能调用动态 acceptSolution() 直接派车。正式控制器如何提供可剪枝下界、怎样处理总预算及整数解，由后续模块决定。

资源/图结构/枚举对照入口为 pricing_test.cpp，RMP接线入口为 pricing_rmp_test.cpp；具体测试组和命令见模块说明。两版当前都不支持分支条件；未来 RF 改变允许后缀时，需要同时修改扩展、支配和完整性验证，而非只过滤最终候选。

## 11. 怎样独立检查“没有漏掉更好的路线”

仅比较普通版与桶图版还不够：两版共用资源传播和支配，可能同时出现同一种错误。[pricing_property_test.cpp](../../../packages_dvrp/application/dvrp/test/pricing/pricing_property_test.cpp)因此提供另一条仅用于测试的计算链：

~~~text
exhaustive(stage)：列出每个客户子集的全部排列
  → evaluate(stage, start, sequence)：从头计算整条路线的资源与成本
  → rc(route, duals)：按公式直接计算 reduced cost
  → audit(...)：核对生产 pricing 的最小值、见证及负列集合
~~~

例如3个客户，除外部直接返场外，还列出3条单客户、6条双客户、6条三客户序列；对每个起点分别检查。它不剪标签、不建桶、不复用生产距离函数，也不因某条前缀返场失败就跳过较长路线。这里最多7客户是穷举测试的保护，不是生产定价上限，也没有更改原5客户CPLEX对照工具的上限。

audit 不要求两版返回同一条并列最优路线，但要求最小rc一致，返回路线真实可行、成本正确，且负列起点集合与独立答案一致。随机失败时输出种子、用例、阶段、dual、版本和桶数量，便于复现。

metamorphic 另外检查：只更改客户编号、车辆key、输入顺序、坐标镜像或固定成本时，答案应保持一致；只将某辆外部车的dual增加3.25时，仅该起点最小rc减少3.25。stress 则在真实RMP循环中分别固定或交替使用两版，每轮核对定价，结束时核对完整LP。这些都是测试入口，不改变生产调用链，也不能替代未来分支和完整动态BP的验收。

## 12. 真实较大实例怎样测：不调用穷举器

[pricing_instance_test.cpp](../../../packages_dvrp/application/dvrp/test/pricing/pricing_instance_test.cpp)通过原reader读取完整TXT，再用DynamicController.buildStageProblem()产生时间0阶段。例如C101文件有100客户，初始仅79客户披露，实验不会把其余21客户提前交给定价；指定20客户时明确标为这个阶段的前20客户子集。

main()先求空RMP的Phase-I dual，再调用probe()；随后加入可行初始列，人工量为0才进入Phase-II，并用新的真实dual再调用probe()。这两组是独立的固定dual实验，不是用Phase-I定价结果驱动完整CG；不因Phase-I搜索中断就伪称已经完成可行性定价。

probe()让两版逐个运行，使用相同阶段、dual及预算；checkResult()/checkRoute()独立重算见证的需求、时间、返场、成本和rc。两版均Complete时才比较最小值；任一中断时只核对结果已清空并报告无法比较。初始列仍有人工量时跳过Phase-II，不报告完整阶段不可行。

程序退出码0表示两组定价完成且一致，3表示预算中断或初始真实列不足，1表示校验/运行错误，2表示参数错误；不输出整数StageSolution，不推进动态事件。独立路线校验能检查可行性及成本，但没有穷举对照，不能排除两版共享错误；运行命令和实测边界统一见模块说明。

## 13. 新增的独立 MIP 审核链

第12节入口仍保留。另一个测试入口 `mip_reference/pricing_mip_test.cpp` 增加了独立最优性证据：`solvePricingMip()`直接建立单路径弧模型；`checkPricingRoute()`独立复核路线；`audit()`逐起点核对两版标签的最小rc是否被MIP下界夹住。较大子集不枚举路线。

其 `cg()` 每轮取得真实RMP dual，再审核两版定价，用标签返回的列继续求LP；MIP不供列。终止时 `finalLpCertificate()` 用各起点MIP下界核对完整路线LP的目标区间。另有 `dominance()` 独立检查被支配标签的可行后缀是否仍能接到支配者上。

这条链仍只在test中运行，不改变公开pricing接口或生产入口。具体输入输出、手算例子、状态与验证记录见[定价MIP独立对照测试](DVRP定价MIP独立对照测试.md)，不在此重复模型细节。
