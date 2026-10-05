# RouteOpt 分支与搜索树

> 状态：已确认
>
> 最后更新：2026-10-04

## 1. 搜索树的职责

列生成求得的是当前节点完整主问题的 LP 松弛。若路线变量仍为分数，需要通过 Branch-and-Bound 增加约束，直到得到整数路线组合或节点被剪枝。

`BBTController` 管理搜索树，`BbNode` 保存一个具体树节点的优化状态。

## 2. 一个节点的处理顺序

`workflow.hpp` 的主循环可以简化为：

```python
while tree:
    node = pop_best_node()
    solve_node_by_column_generation(node)
    separate_cuts(node)

    if node_is_terminated(node):
        discard(node)
        continue

    candidates = obtain_fractional_edges(node)
    edge = select_candidate(candidates)
    left, right = impose_branching(node, edge)
    push(left, right)
```

搜索树本身通过回调调用 CVRP/VRPTW 的 pricing、cutting、候选测试和分支实现。

## 3. 分支候选如何产生

`BbNode::obtainSolEdgeMap()` 读取：

- 当前节点每条路线的 LP 值 `x_r`；
- 对应 `SequenceInfo.col_seq`。

对路线中的每对相邻节点，将方向归一化为：

```text
{min(i,j), max(i,j)}
```

并累计：

$$
q_{\{i,j\}}=\sum_r a_{\{i,j\},r}x_r.
$$

所以当前 RouteOpt 分支变量是无向边流量，不是有向弧，也不保存车辆或起点身份。

单客户路线 `0 -> i -> 0` 对车场边只计一次，避免把同一无向边重复计为 2。

## 4. 候选怎样选择

候选系统从分数边中选择一个分支对象。当前完整程序还可能使用：

- LP testing；
- 启发式/精确 CG testing；
- BKF 控制测试数量；
- L2B 模型选择候选。

这些属于候选选择增强，不改变最终分支含义。标准分支的核心只需要从分数边中选择一个合法候选。

## 5. 两个子节点的含义

对候选无向边 `e={i,j}`：

```text
禁止分支：q_e = 0
必须分支：q_e = 1
```

当前实现为了减少复制：

- 原节点被原地改成“禁止边”一侧；
- 复制出的新节点成为“必须使用边”一侧。

### 5.1 禁止边一侧

执行三件事：

1. 删除当前 LP 中所有使用该边的路线列；
2. 从 forward bucket graph 删除 `i -> j` 和 `j -> i`；
3. VRPTW 同时从 backward bucket graph 删除两个方向。

该侧不一定增加一条显式 LP 行，因为列删除和 pricing 图封锁已经保证边流量为 0。

### 5.2 必须使用边一侧

增加一条等式：

$$
\sum_r a_{e,r}x_r=1.
$$

已有路线按是否使用该无向边取得系数。后续 pricing 中，该分支行的 dual 会同时进入 `i -> j` 和 `j -> i` 的定价成本。

## 6. 为什么子节点必须重新 Pricing

分支改变了可行路线集合和 reduced cost：

- 禁止边后，旧的最优路线可能消失；
- 必须用边后，分支行产生新的 dual；
- 父节点中不是负 reduced-cost 的路线，在子节点中可能变成必要路线。

因此不能只在父节点现有列中做普通 Branch-and-Bound，每个子节点都要重新列生成。

## 7. `BbNode` 复制哪些状态

非 enumeration 状态下，子节点主要继承：

- 当前 Solver 模型；
- 路线列 `cols`；
- RCC 和 rank-1 cuts；
- 已有分支约束；
- bucket graph 和拓扑顺序；
- 当前节点下界及统计信息。

然后再应用本次分支的差异。

## 8. 节点终止与整棵树结束

节点可能因为以下原因结束：

- LP 下界不能改善全局上界；
- 得到可行整数解并更新上界；
- 模型不可行；
- 定价或全局时间限制触发；
- enumeration/MIP 等增强模块完成节点。

搜索树为空时，或达到全局终止条件时，`BBTController` 输出全局下界和探索节点数。

## 9. DVRP 接入暴露出的静态假设

| 当前分支键 | DVRP 风险 |
| --- | --- |
| 无向 `{i,j}` | 时间窗路径本身有方向，且多起点分配可能被聚合掩盖 |
| 不含起点身份 | 不同外部车辆的 `0.5 + 0.5` 可能聚合为整数 |
| 车场统一为节点 `0` | 原始起点、外部起点和返回节点语义不同 |
| 一张共享 bucket graph | 起点专属分支不能无条件删除其他起点的同名弧 |

## 10. 源码入口

```text
packages/branching/bbt/include/workflow.hpp
packages/branching/bbt/include/bbt_controller.hpp
packages/application/cvrp/src/main/include/call_branching.hpp
packages/application/cvrp/src/main/src/add_brc.cpp
packages/application/cvrp/src/pricing/src/price_constraints.cpp
packages/application/cvrp/src/node/include/node.hpp
```
