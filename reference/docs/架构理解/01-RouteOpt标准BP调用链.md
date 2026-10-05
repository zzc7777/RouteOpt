# RouteOpt 标准 BP 调用链

> 状态：已确认
>
> 最后更新：2026-10-04

## 1. 目标

本文只说明 RouteOpt 静态 VRPTW 从数据进入求解器，到 RMP、pricing、加列和分支的总体连接。各模块内部逻辑见本目录对应文档。

## 2. 总调用链

```text
实例 TXT
  -> CVRP_ReadDataController
       输出 dim、cap、num_vehicle、info_vertex
  -> InitialController
       输出距离矩阵、需求、时间窗、服务时间、solver 环境
  -> CVRPSolver / VRPTW
       持有静态数据和可复用 controller
  -> BbNode 根节点
       持有当前 RMP、路线列、cuts、分支约束、bucket graph
  -> BBTController
       循环处理树节点
       -> 完整列生成
       -> cuts
       -> 整数解则更新上界
       -> 分数解则生成两个子节点
  -> 搜索树为空或达到终止条件
  -> 输出路线、上下界和节点数
```

标准列生成闭环是：

```text
解 RMP
  -> 读取每一行约束的 dual
  -> pricing 寻找负 reduced-cost 路线
  -> add-column 把路线翻译为 LP 变量
  -> 再解 RMP
  -> 精确定价证明没有负列后结束
```

## 3. 模块接口总表

| 上游 | 传递的信息 | 下游 | 结果 |
| --- | --- | --- | --- |
| reader | `info_vertex`、容量、维度、车辆数 | `InitialController` | 标准化静态数据 |
| `InitialController` | 距离、需求、时间窗、服务时间 | `CVRPSolver` | 实例级共享数据 |
| `CVRPSolver` | 维度、车辆数、solver 环境 | `BbNode::buildModel` | 根 RMP |
| `BbNode::Solver` | LP 解 `X`、目标值、dual | CG controller | 定价输入和节点下界 |
| CG controller | dual、cuts、分支约束 | `CVRP_Pricing` | `new_cols` 候选路线 |
| `CVRP_Pricing` | `SequenceInfo` 客户序列 | add-column | 路线成本和稀疏列系数 |
| add-column | 新变量及各行系数 | `BbNode::Solver` | 扩充后的 RMP |
| `BbNode` | 路线变量值和客户序列 | branching | 无向边流量候选 |
| branching | 选中的边和方向 | 子 `BbNode` | 新分支约束和新树节点 |

## 4. 对象所有权

RouteOpt 不是每个模块都复制一份问题数据，而是通过引用和指针共享：

```text
CVRPSolver
  ├─ 拥有实例级静态数据
  ├─ 拥有一套 pricing controller
  └─ 拥有一套 add-column controller

每个 BbNode
  ├─ 拥有自己的 Solver 模型
  ├─ 拥有自己的 cols、cuts、branch constraints
  └─ 拥有自己的 bucket graph
```

处理某个节点前，`CVRPSolver::setEnv(node)` 会把共享 controller 重新绑定到当前节点：

```text
BbNode 的 buckets、拓扑顺序 -> pricing_controller.updatePtr(...)
BbNode 的 Solver、cols、约束 -> add_column_controller.updatePtr(...)
```

这里的 `setEnv` 是“切换当前算法上下文”，不是设置操作系统环境变量。

## 5. 搜索树怎样调用应用代码

`BBTController` 是通用模板，不知道容量和时间窗。`main.cpp` 把 CVRP/VRPTW 能力作为回调传入：

| 搜索树需要的能力 | 应用回调 |
| --- | --- |
| 初始化根节点 | `callReadNodeIn` |
| 完整求解节点 LP | `callPricingAtBeg` |
| 分离 cuts | `callCutting` |
| 提取候选边 | `BbNode::obtainSolEdgeMap` |
| 测试候选 | `processLPTesting/processCGTesting` |
| 生成子节点 | `imposeBranching` |

所以 `BBTController` 更像 Python 中接收函数参数的通用框架，而 `CVRPSolver` 提供具体 VRP 行为。

## 6. 静态 VRPTW 的关键假设

1. 节点 `0` 同时是所有路线的出发点和返回点；
2. `SequenceInfo` 只保存客户序列，隐含路线为 `0 -> customers -> 0`；
3. RMP 最后一行是路线数量 `>= num_vehicle`；
4. pricing 只有一组统一的起点时间和容量；
5. bucket graph 的 `0` 号桶承担唯一车场入口；
6. 分支按路线中的无向边汇总，不保存路线起点身份。

这些假设是 DVRP 多起点适配需要处理的边界，不代表目标设计已经确定。

## 7. 源码入口

```text
packages/application/cvrp/src/main.cpp
packages/application/cvrp/src/main/include/cvrp.hpp
packages/application/cvrp/src/main/src/call_read_node_in.cpp
packages/application/cvrp/src/main/src/set_env.cpp
packages/application/cvrp/src/main/src/cg.cpp
packages/branching/bbt/include/workflow.hpp
```
