# RouteOpt 定价与标签算法

> 状态：已确认
>
> 最后更新：2026-10-04

## 1. Pricing 的输入和输出

定价子问题的目标是从尚未显式加入 RMP 的可行路线中，找出 reduced cost 小于 0 的路线。

输入：

- 当前 RMP 所有行的 dual；
- 距离成本、需求、容量、时间窗和服务时间；
- 当前 cuts 和分支限制；
- bucket graph、ng-memory 和标签内存。

输出：

```text
new_cols: vector<SequenceInfo>
```

每个 `SequenceInfo` 只包含客户访问顺序。pricing 不直接修改 LP，add-column 会重新计算列系数后提交。

## 2. Dual 如何变成弧成本

RMP 基本 dual 顺序为：

```text
[客户 1 dual, ..., 客户 n dual, 车辆行 dual, cuts/分支行 dual]
```

`pricePartitioning()` 首先生成 `chg_cost_mat4_vertex`。对客户边：

$$
\tilde c_{ij}=c_{ij}-\frac{1}{2}(\pi_i+\pi_j).
$$

对车场—客户边：

$$
\tilde c_{0i}=c_{0i}-\frac{1}{2}(\pi_i+\pi_V).
$$

一条静态路线中每个客户连接两条边，两个一半的客户 dual 合起来正好扣除一次；两条车场边同理扣除一次车辆行 dual。

之后继续叠加：

- RCC dual 对弧成本的修改；
- rank-1 cut 的标签状态；
- “禁止边”分支对弧的封锁；
- “必须使用边”分支行 dual 的贡献。

## 3. 图是怎样表示的

RouteOpt 没有提前构建完整的“标签状态图”，而是分成两层。

### 3.1 客户图

客户之间的基础关系主要由矩阵隐式保存：

```text
cost_mat4_vertex[i][j]                 真实成本
chg_cost_mat4_vertex[i][j]             定价成本
resource_across_arcs_[i][j]            时间/容量资源变化
```

### 3.2 Bucket graph

每个 `BbNode` 显式持有 bucket graph：

```text
all_forward_buckets[vertex][resource_bucket]
all_backward_buckets[vertex][resource_bucket]
```

每个 bucket 保存普通 `bucket_arcs`、跨区间 `jump_arcs` 和标签容器。默认 VRPTW 以时间作为主要分桶资源，容量仍在每个标签中检查。

桶图是当前 labeling 实现的基础结构，不是像 cuts 一样可以直接关闭的插件。

## 4. Label 保存什么

当前 `Label` 的核心状态包括：

| 状态 | 含义 |
| --- | --- |
| `end_vertex` | 部分路线当前结束的客户 |
| `res` | 时间、容量等资源状态 |
| `rc` | 累积 reduced cost |
| `pi` | ng-route 访问记忆 |
| `p_label` | 前驱标签，用于恢复完整序列 |
| `r1c` | rank-1 cut 的定价状态 |

一个 bucket 可以同时容纳很多标签。bucket 表示“节点和主资源区间”，标签才表示一条具体的部分路线。

## 5. 标签怎样扩展

初始标签表示从节点 `0` 前往一个客户。对部分路径末端 `i` 扩展到客户 `j` 时，主要执行：

```python
if ng_memory_forbids(j):
    reject

new_time = max(ready[j], time + service[i] + travel[i][j])
new_capacity_state = extend_capacity(...)

if new_time > due[j] or capacity_infeasible:
    reject
if cannot_eventually_return_to_depot:
    reject

new_rc = rc + changed_cost[i][j]
update_ng_memory_and_cut_state()
apply_dominance()
```

可行的新标签按新资源值放入对应客户的 bucket，再等待后续扩展。

## 6. 支配规则

同一节点附近的标签数量可能指数增长。RouteOpt 会删除“以后不可能优于另一个标签”的状态。

EXACT 阶段至少比较：

- reduced cost；
-资源状态；
- ng 访问记忆；
- rank-1 cut 状态。

直观上，如果标签 A 的成本不高、资源不差、未来访问限制不更严格，那么被 A 覆盖的标签 B 不需要继续扩展。

LIGHT 和 HEAVY 使用更弱、更激进的检查，以较快产生列，但不能证明不存在负 reduced-cost 路线。

## 7. 前向、后向与拼接

VRPTW 的时间资源具有方向性。当前配置会生成前向和后向标签：

```text
0 -> ... -> i          forward label
j -> ... -> 0          backward label
```

在中间弧 `i -> j` 上检查资源、访问记忆和 cut 状态兼容后进行拼接。负 reduced-cost 的拼接结果进入 `negative_rc_label_tuple`。

`writeColumnsInPricingPool()` 再沿前驱指针恢复客户序列，写入 `new_cols`。

## 8. ng-route 和动态增强

当前标签不会始终记录完整访问集合，而是使用 ng-memory：

- 初始邻域大小为 8；
- 标签只记住对应 ng 集合中的访问历史；
- 因此定价松弛中暂时可能出现重复客户；
- 根节点会检查正值路线中的小循环，通过 DSSR/NG augmentation 扩大记忆；
- 当前单个 ng 集合上限为 16。

ng-route 是状态压缩机制。它和 RMP 的客户覆盖等式不是同一件事。

## 9. 三个 Pricing 阶段

`solveLPInLabeling()` 默认依次运行：

| 阶段 | 目的 | 能否证明没有负列 |
| --- | --- | --- |
| LIGHT | 快速产生少量候选 | 不能 |
| HEAVY | 使用更多资源检查产生候选 | 不能 |
| EXACT | 完整搜索当前 bucket/ng-memory 定义的定价空间 | 能证明本轮无负列 |

EXACT 完成且 add-column 没有加入新列时，本轮列生成结束。根节点仍可能因 ng-memory 增强再次启动列生成。

## 10. 与其他模块的接口

```text
RMP
  -- dual、cuts、branch constraints --> priceConstraints

priceConstraints
  -- changed arc costs -------------> labeling

labeling
  -- negative label pairs ----------> route reconstruction

route reconstruction
  -- new_cols ----------------------> add-column
```

## 11. DVRP 接入暴露出的静态假设

1. 只有节点 `0` 能初始化标签；
2. 所有标签使用统一的初始时间和容量；
3. 所有路线返回同一个节点 `0`，且起点和终点未分离；
4. pricing 输出不携带起点身份；
5. 分支约束默认同时作用于共享图中的两个方向。

## 12. 源码入口

```text
packages/application/cvrp/src/pricing/include/cvrp_pricing_controller.hpp
packages/application/cvrp/src/pricing/include/set_resource_in_bucket_graph.hpp
packages/application/cvrp/src/pricing/include/run_labeling.hpp
packages/application/cvrp/src/pricing/include/pricing_functors.hpp
packages/application/cvrp/src/pricing/include/dominance.hpp
packages/application/cvrp/src/pricing/include/write_columns_from_pricing.hpp
packages/application/cvrp/src/pricing/src/price_constraints.cpp
packages/application/cvrp/src/main/src/cg.cpp
```
