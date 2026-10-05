# RouteOpt 受限主问题 RMP

> 状态：已确认
>
> 最后更新：2026-10-04

## 1. RMP 的职责

完整集合划分模型可能有指数数量的路线变量。RouteOpt 的 Restricted Master Problem（RMP）只保存当前已经发现的路线：

```text
RMP = 主问题的全部约束 + 当前已知的一小部分路线变量
```

pricing 找到新路线后，RMP 才增加相应变量。约束行通常保持不变，增长的是列。

## 2. 当前静态模型的行

`BbNode::buildModel()` 创建 `dim` 行：

| 行号 | 含义 | 方向与右端 |
| --- | --- | --- |
| `0 .. dim-2` | 客户 `1 .. dim-1` 的覆盖约束 | `= 1` |
| `dim-1` | 路线/车辆数量行 | `>= num_vehicle` |

因此静态主问题骨架可以写成：

$$
\sum_r a_{ir}\lambda_r=1,\qquad i=1,\ldots,n,
$$

$$
\sum_r\lambda_r\ge \texttt{num\_vehicle}.
$$

这里第二行不是车队数量上限。`num_vehicle` 在初始化时还可能被最低车辆数逻辑更新。

当 cuts 或“必须使用边”的分支约束出现后，RMP 会在基本行后继续增加新行。

## 3. 路线列

每个普通路线变量对应一个 `SequenceInfo`：

```text
SequenceInfo.col_seq = [i1, i2, ..., im]
隐含完整路线        = 0 -> i1 -> i2 -> ... -> im -> 0
```

普通路线列包含：

- 目标系数：完整路线的行驶成本；
- 客户行系数：路线访问该客户的次数；
- 车辆行系数：1；
- 已存在 cut 和分支行的相应系数。

路线变量由 LP 求解时是连续变量，只有 Branch-and-Price 完成后才要求得到整数路线组合。

## 4. 根 RMP 如何获得初始可行性

根节点分两步启动。

### 4.1 高成本人工列

`BbNode::buildModel()` 先加入一条人工列：

- 覆盖所有客户行；
- 车辆行系数为 `num_vehicle`；
- 目标系数为很大的 `OBJ_ARTIFICIAL`。

它的作用是让最初没有足够真实路线时，LP 仍能求解并产生 dual。它不代表实际车辆路线。

### 4.2 单客户真实列

`constructInitialColumns()` 再为每个客户加入：

```text
0 -> i -> 0
```

该列在客户 `i` 行的系数为 1，在车辆行的系数也为 1，目标系数为往返成本。

## 5. RMP 对外提供什么

每轮 LP 求解后，节点 `Solver` 提供：

| 输出 | 使用者 | 用途 |
| --- | --- | --- |
| 当前目标值 | 节点/搜索树 | 节点下界和剪枝 |
| 每列变量值 `X` | 上界检查、cuts、branching | 判断整数性和汇总边流量 |
| 每行 dual | pricing | 计算路线 reduced cost |

pricing 不读取完整 LP 矩阵来生成路线，只接收 dual、当前 cuts、分支约束以及静态资源数据。

## 6. 每个树节点为什么有独立 RMP

`BbNode` 自己持有：

- `Solver` 模型；
- 当前 `cols`；
- RCC、rank-1 cuts；
- 分支约束；
- 节点目标值和终止状态。

分支时子节点从父节点复制模型和列，再加入“必须使用”约束或删除“禁止使用”路线。父子节点随后分别列生成。

## 7. 与其他模块的接口

```text
initial/root builder
  -> RMP：约束行、人工列、单客户列

RMP Solver
  -> pricing：dual
  -> branching：路线变量值

add-column
  -> RMP：新路线的成本和稀疏列系数
```

## 8. DVRP 接入时暴露出的静态假设

| 当前事实 | DVRP 不能原样沿用的原因 |
| --- | --- |
| 只有客户覆盖行和一条车辆行 | 外部起点需要各自的路径选择等式 |
| 所有路线车辆行系数都是 1 | 原始起点路线不限数量，不能使用现有车辆行 |
| 人工列按静态行结构构造 | 多起点 RMP 需要重新设计 Phase-I 可行性 |
| 列没有起点身份 | 无法计算外部起点行系数和对应 dual |

## 9. 源码入口

```text
packages/application/cvrp/src/node/src/node.cpp
packages/application/cvrp/src/node/include/node.hpp
packages/application/cvrp/src/main/src/call_read_node_in.cpp
packages/application/cvrp/src/main/src/cg.cpp
```
