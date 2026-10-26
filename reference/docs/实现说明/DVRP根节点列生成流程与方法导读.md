# DVRP 根节点列生成：流程与方法导读

> 状态：已完成（对应当前根CG源码）
>
> 最后更新：2026-10-26
>
> 能力、命令、验证和限制统一见[模块说明](DVRP根节点列生成模块.md)。本文解释实际代码，不是下一阶段计划。

## 1. 先看谁负责做什么

已有RMP像“保存当前路线表并求LP的对象”，pricing像“拿到价格后找新路线的对象”。本次新增的根控制器负责反复调用它们，直到有结束证据或预算用尽。

```text
调用者给 StageProblem + RootCgOptions
  → DVRP_RootCgController::solve
      → DVRP_RmpController：建模、LP、dual、加列
      → DVRP_PricingController：普通/桶图标签，逐起点最小rc
      → RootCgDetail：检查结果、计算数值界、判断能否结束
  ← RootCgResult
```

公开入口只有 `solve`；`RootCgOptions/Result/Snapshot/Iteration/Bound` 都是数据对象，不负责执行算法。`RootCgDetail` 是同一cpp中的内部小函数，不是新类或可替换solver框架。

源代码入口：[声明](../../../packages_dvrp/application/dvrp/src/column_generation/include/root_cg_controller.hpp)、[循环及内部方法](../../../packages_dvrp/application/dvrp/src/column_generation/src/root_cg_controller.cpp)。当前由测试调用，`main.cpp` 没接入求解。

## 2. 接收阶段：`solve(stage, options)`

输入阶段已经决定了当前客户和外部起点。根控制器不处理披露事件、不移动车辆，也不重新计算“谁是承诺客户”。

例如：客户5、8，原始起点0，车辆42的外部起点位于客户3、时间10、剩余容量2。根控制器原样交给RMP和pricing。连续LP行号、标签顶点号仍在各自模块内部转换；42不是客户下标，客户3也不会被重新覆盖。

方法开始就记录总时钟，检查选项，然后创建本次调用独占的两个控制器。它们各自保存阶段副本；不缓存上一个事件的模型。默认调用 `rmp.addInitialRoutes()`，关闭该选项则从纯人工变量启动，主要用于核对可行性定价。

## 3. 求当前LP：`rmp.solveLp(seconds)`

每轮先计算剩余总时间，将它与RMP单次上限取较小值。新增重载只影响本次LP时限，不改变模型版本；无参 `solveLp()` 每次重新恢复构造时默认上限。

RMP先求解，再检查覆盖行、外部起点行、变量、dual和目标一致性。只有 `OptimalRmp` 才能向下游pricing提供dual。超时或错误就在这里停止，不能拿未完成LP的数值去定价。

根控制器通过内部 `validateLp` 再核对阶段、版本、Phase及dual键集合，然后立即保存：

```text
RootCgSnapshot = 本次LP结果 + 本次真实列池的值副本
```

假设本轮LP有5列，随后又加了2列，却在下一轮前超时：返回的最后有效快照仍然是“5个lambda＋5条列”，不是“5个lambda＋7条列”。日志可以记录后续新增2列，但不会混入旧解。

## 4. 若还在找可行解：`enterCostPhase()` 或 Phase-I 定价

Phase-I的目标是人工变量总量，不是距离。

- 人工量大于 `1e-6`：继续用本次Phase-I dual定价，真实路线的距离目标权重为0。
- 人工量不超过 `1e-6`：调用 `rmp.enterCostPhase()`，固定人工变量为0，恢复路线距离目标，进入下一轮重新求LP。

第二种情况不要求先做一次Phase-I定价，因为人工目标非负，已经达到0。它也不意味着整数路线已找到。切换会更新模型版本，旧dual不能继续使用。

下一轮若真实LP无法正常求解，返回错误，不将“切换失败”当作完整阶段不可行。

## 5. 找新路线：`pricing.price(duals, options)`

根控制器把当前dual和剩余预算传给已选择的后端。普通标签和桶图标签不会在中途自动切换。每次调用仍逐个处理原始起点和全部外部起点，共用本轮时限。

返回内容有两类：每个起点的最小rc及可行路线见证；每个起点至多一条达到 `rc<-1e-5` 的候选。无可行路线的起点用空最小值，不伪造0。

`classifyPricingStatus()` 先处理未完成状态；根循环不消费其部分候选。完整结果才进入 `validatePricingResult()`：

1. 阶段、版本、Phase、backend必须对应刚才的LP。
2. 原始起点与每个外部起点都必须出现且只出现一次。
3. 可行标志、最小值和见证必须一致；同一结果的复制字段精确一致。
4. `validateWitness()` 重算起点时间/容量、客户访问、等待、返场成本及rc；不同算法的重算值按现有浮点容差比较。
5. 负最小值对应的候选不能缺失、重复或替换成另一条路线。

这不是重新求一次定价，而是检查两模块之间传回的数据有没有错位。

## 6. 有候选：`rmp.addRoutes()` 后重新求LP

根控制器取出候选的 `StageRoute` 交给RMP。RMP独立检查整批路线、重算成本和行系数，按“起点＋有序序列”去重，写入LP后返回真实列ID。

`requireColumnProgress()` 检查至少增加一列；若pricing说有负列，但全是已有列，就停止并报错，不能无限循环。随后再次核对RMP成本与pricing成本，记录新增列数并进入下一轮。

这一轮目标可能不下降，仍必须继续。原因是退化LP可能先加入暂时不改善目标的列，几条列组合后才改善；“连续两次目标相同”不是停止依据。

## 7. 没候选：`computeBound()` 和 `classifyBound()`

没有候选只代表没有达到加列阈值的路线，不代表所有rc严格非负。

`computeBound()` 用全部起点最小rc、对偶和及Phase-I人工rc计算保守数值下界，记录修正与舍入余量。数学依据和实际guard公式见模块说明、设计04，不在本文重复。

`classifyBound()` 决定：

- Phase-I人工量正且下界也大于 `1e-6`：真实路线LP不可行。
- Phase-II：当前RMP可行成本与下界的gap小于规定绝对/相对容差，才是根LP收敛。
- 两者都不满足：数值证据不足，返回未完成，不能强行宣布收敛或不可行。

固定成本不影响选路和定价；仅在Phase-II给上下界各加一次。Phase-I的人工量下界不能标成距离下界。

## 8. 具体例子：为什么返回30不是派车方案

原始车场在 `(0,0)`，容量2；三个客户都在 `(10,0)`，需求各1，时间窗足够宽。

| 执行位置 | 内容 |
| --- | --- |
| `addInitialRoutes()` | 3条单客户路线，每条成本20，当前可行总成本60 |
| `enterCostPhase()` 后 | 开始优化距离，不再优化人工量 |
| `price()` → `addRoutes()` | 逐步加入双客户路线；中间可能LP目标停在40不变，仍继续 |
| 最后一次完整定价 | 3条分别覆盖两位客户的路线可各取0.5，总成本30，数值界闭合 |
| `RootCgResult` | `LpConverged`，LP成本30，保留分数lambda和列 |

完整集合划分MIP的整数最优为40。根控制器不将0.5四舍五入，也不会将这些路线交给动态车辆执行。下游共同BP树负责处理整数性。

## 9. 中断、日志和测试怎样对应

`remainingTime()` 在构造、初始列、LP、pricing、加列和验证边界复核总预算；上限为0时立即停止。最大迭代次数计算LP求解次数，Phase切换后的重新求解也计一次。对象释放后再次核对时钟，超总时限不会报告为按时收敛。

每条 `RootCgIteration` 记录Phase、版本、目标/人工量、加列数、有效最小rc、单次上限和耗时；没有可用数值就留空。已增加可选 `pricingStatistics`，在pricing返回后、检查总时限前写入，供BP累计调用/标签等统计，不把中断结果作为证明。最终结果有停止状态与原因；不是把全量标签写入日志。

测试入口为 [root_cg_test.cpp](../../../packages_dvrp/application/dvrp/test/root_cg/root_cg_test.cpp)：

- `hand/phases/fractional/random` 直接调用正式根循环，枚举器只审核路线及完整LP值，不供列。
- `bounds/numeric` 独立检查全集rc与下界，尤其微负rc不能被候选阈值吞掉。
- `context/duplicate/status` 直接测试正式循环共用的内部校验函数，属于确定性故障策略测试，不冒充真实solver故障。
- `limits/lifecycle/size` 通过真实入口检查预算、旧快照、阶段隔离及超过对照器5客户限制的情况。

下游共同BP树把闭合LP快照交给分支候选模块，再用 `solve(stage, context, options)` 求左右子节点。该重载重新建立带限制的RMP/pricing，共用本文循环，不增加分支行；见[分支方法导读](DVRP双策略分支流程与方法导读.md)及[BP树导读](DVRP共同BP搜索树与整数恢复流程与方法导读.md)。自动搜索树和整数恢复已实现，动态接回仍未接入。
