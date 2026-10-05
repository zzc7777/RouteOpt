# RouteOpt 加列与列生命周期

> 状态：已确认
>
> 最后更新：2026-10-04

## 1. Add-column 为什么单独存在

pricing 只负责寻找客户序列，RMP 需要的是一个 LP 变量及其对每条约束行的系数。`CVRP_AddColumnController` 是两者之间的转换层：

```text
SequenceInfo
  -> 完整路线成本
  -> 客户/cut/分支行系数
  -> reduced cost 复核
  -> Solver 新变量
```

因此它不是简单的 `vector.push_back()`。

## 2. 输入接口

controller 构造时长期引用：

- pricing 的 `new_cols`；
- 距离矩阵；
- pricing 产生的负 reduced-cost 标签信息；
- rank-1 系数计算器；
- pricing 路线池。

进入一个具体 `BbNode` 时，`updatePtr()` 再绑定：

- 当前节点的 `cols`；
- 当前节点的 `Solver`；
- 当前 RCC、rank-1 cuts 和分支约束；
- enumeration 列池信息。

## 3. 如何把客户序列翻译成列

对于客户序列 `[i1, i2, ..., im]`，当前代码固定解释为：

```text
0 -> i1 -> i2 -> ... -> im -> 0
```

然后计算：

| LP 内容 | 计算方式 |
| --- | --- |
| 目标系数 | 上述完整路线的弧成本之和 |
| 客户行 | 客户在序列中每出现一次，系数加 1 |
| 车辆行 | 每条路线固定为 1 |
| RCC 行 | 调用 RCC coefficient controller |
| rank-1 行 | 调用 rank-1 coefficient getter |
| 分支行 | 路线每使用一次目标无向边，系数加 1 |

这些系数组成列主序的稀疏矩阵 `mat`。

## 4. 为什么再次计算 reduced cost

labeling 内部使用改写后的弧成本快速找路线；add-column 会用主问题真实列重新计算：

$$
\bar c_r=c_r-\pi^T A_r.
$$

只有满足

```text
reduced_cost < RC_TOLERANCE
```

的候选才真正加入 LP。这一步可以发现 pricing 状态与主问题系数不一致的问题。

## 5. 提交给 Solver

通过检查的候选被整理成：

```text
solver_obj   每个新变量的目标系数
solver_beg   每列非零项起点
solver_ind   非零项所在行
solver_val   非零系数
```

随后调用 `Solver::XaddVars()` 批量增加变量，并把相应 `SequenceInfo` 追加到 `BbNode::cols`。

Solver 的列顺序和 `BbNode::cols` 的顺序必须保持一致，因为读取 LP 解和分支统计时直接用相同下标对应。

## 6. 一条路线的生命周期

```text
labeling 找到负 reduced-cost 路径
  -> 恢复为 pricing.new_cols
  -> add-column 重算成本和行系数
  -> 通过 reduced-cost 检查
  -> 加入当前 BbNode::Solver
  -> 加入当前 BbNode::cols
  -> 参与后续 LP、cuts 和分支统计
```

分支后：

- 子节点通常继承父节点当前列；
- “禁止边”分支删除使用该边的不兼容列；
- 后续 pricing 仍可产生满足新分支条件的其他路线；
- 列清理和 enumeration 还可能删除或转存列。

## 7. 初始列与定价列的区别

单客户初始列由 `constructInitialColumns()` 直接组装 Solver 数组，不经过 pricing。进入节点后，它们和 pricing 加入的普通列一样存放在 `BbNode::cols`。

人工列则用于维持初始 LP 可行性，不应解释为真实路线。

## 8. DVRP 接入暴露出的静态假设

| 当前逻辑 | DVRP 所需变化 |
| --- | --- |
| 首弧固定从 `0` 出发 | 按路线 `startKey` 计算 |
| 末弧固定返回 `0` | 返回独立的 return depot |
| 每条路线都有车辆行系数 1 | 原始起点列与外部起点列使用不同主问题行 |
| 分支系数按无向边统计 | M1 若使用有向、起点专属分支，必须按相同键计算 |
| `SequenceInfo` 没有起点 | 目标列对象必须显式携带起点身份 |

## 9. 源码入口

```text
packages/application/cvrp/src/add_column/include/add_column_controller.hpp
packages/application/cvrp/src/add_column/src/add_column.cpp
packages/application/cvrp/src/pricing/include/write_columns_from_pricing.hpp
packages/application/cvrp/src/main/src/call_read_node_in.cpp
```
