# RouteOpt 标准 BP 调用链

> 状态：已确认
>
> 最后更新：2026-10-04

## 1. 这份文档解决什么问题

本文从源码解释 RouteOpt 如何把 VRPTW 数据传到主问题、定价、加列和分支。RouteOpt 当前完整流程实际还包含 cuts、enumeration 等加速模块；本文只抽取 M1 需要复用的标准 Branch-and-Price 骨架。

## 2. 总流程

```text
实例 TXT
  -> CVRP_ReadDataController：读成 info_vertex 等基础数据
  -> InitialController：生成距离、需求、时间窗和 solver 环境
  -> CVRPSolver / VRPTW：持有数据和各功能 controller
  -> BbNode 根节点：建立只有少量路线的受限主问题 RMP
  -> BBTController：循环处理搜索树节点
       -> 列生成：解 RMP -> 取 dual -> pricing -> 加入负 reduced-cost 路线
       -> cuts：原 RouteOpt 会执行；M1 标准 BP 暂时关闭
       -> 整数解：更新上界并结束该节点
       -> 分数解：选一条分数边，生成“禁用边/必须用边”两个子节点
  -> 搜索树为空或超时
  -> 输出最优路线、上下界和节点数
```

## 3. 数据怎样进入求解器

| 步骤 | 调用者与代码位置 | 输入 | 输出 |
| --- | --- | --- | --- |
| 选择应用 | `packages/application/cvrp/src/main.cpp` | 命令行、编译期 `app_type` | `CVRPSolver` 或其子类 `VRPTW` |
| 读取文件 | `src/read_data` | 实例路径 | `num_vehicle`、`cap`、`dim`、二维 `info_vertex` |
| 静态预处理 | `src/initial/src/initial_controller.cpp` | `info_vertex` | 距离矩阵、需求、时间窗、服务时间、solver 环境 |
| 组装功能 | `src/main/include/cvrp.hpp` | 上述共享数据 | pricing、add-column、cuts 等 controller |
| 建根模型 | `src/node/src/node.cpp` | 客户数、车辆数、solver | 客户覆盖行、车辆行和高成本人工列 |
| 加初始路线 | `src/main/src/call_read_node_in.cpp` | 距离矩阵 | 每个客户一条 `0 -> i -> 0` 单客户列 |
| 启动搜索 | `packages/branching/bbt/include/workflow.hpp` | 根 `BbNode` 与应用回调 | 被不断求解、分支和回收的搜索树 |

这里使用 C++ 引用传递数据：reader 直接填写 `CVRPSolver` 的成员，后续 controller 再持有这些成员的引用。它类似 Java 中多个 service 共享同一个 `ProblemData` 对象，并不是每一层重新复制一份实例。

静态 VRPTW 没有另一套顶层求解流程。`VRPTW extends CVRPSolver`，主要覆写时间窗相关的可行性检查；主问题、列生成和分支框架仍来自 `CVRPSolver`。

当前静态根模型最后还有一条车辆行：普通路线的系数为 1，约束是路线变量之和 `>= num_vehicle`。它不是 DVRP v4 的“原始车场车辆上限”，后续不能原样沿用。

## 4. 一个树节点内部的列生成

每个 `BbNode` 持有自己的 LP 模型、当前路线列、cuts、分支约束和节点下界。其核心循环位于 `src/main/src/cg.cpp`：

```python
while True:
    lp_solution = solve(current_RMP)
    try_update_integer_upper_bound(lp_solution)
    duals = read_dual_values(current_RMP)
    price_all_constraints(duals)       # 把主问题 dual 变成定价成本
    routes = pricing(duals)            # 找 reduced cost < 0 的可行路线
    add_columns(current_RMP, routes)
    if exact_pricing_found_no_route:
        break                          # 当前节点的 LP 松弛已完整求解
```

RouteOpt 默认依次运行 LIGHT heuristic pricing、HEAVY heuristic pricing 和 EXACT pricing。启发式阶段用于较快找到列；只有精确定价确认不存在负 reduced-cost 列，才证明当前节点的列生成结束。

| 概念 | 在 RouteOpt 中是什么 | Java/Python 类比 |
| --- | --- | --- |
| 列 `SequenceInfo` | 一条只保存客户序列的路线，默认起终点都是节点 `0` | `Route(customerIds)` |
| RMP 变量 | 当前已知每条路线的选择量 `x_r` | `Map<Route, LPVariable>` |
| 客户覆盖行 | 每个客户恰好被所选路线覆盖一次 | `for customer: sum(x_r) == 1` |
| dual | RMP 约束的边际价值 | pricing 的“客户奖励/约束价格” |
| pricing | 带容量、时间窗和分支限制的资源约束最短路 | 按 dual 生成更划算的新路线 |
| add-column | 计算路线成本和各行系数，再调用 solver 加变量 | `model.addVariable(route)` |

`src/add_column/src/add_column.cpp` 仍显式按 `0 -> customer sequence -> 0` 计算成本，并给每条路线添加客户覆盖系数和车辆行系数。这是 DVRP 多起点适配最直接的单车场假设之一。

## 5. 列生成结束后怎样分支

`BBTController` 是通用搜索树调度器，`main.cpp` 把 pricing、cutting、候选提取和施加分支等回调传给它。每个节点的顺序是：

```text
取出一个节点 -> 完整列生成 ->（原程序做 cuts）-> 判断是否终止
  -> 若仍是分数解，汇总每条无向边在 LP 路线中的使用量 q_{i,j}
  -> 从 0 < q_{i,j} < 1 的边中选择候选
  -> 左子节点：q_{i,j} = 0
  -> 右子节点：q_{i,j} = 1
  -> 子节点放回树中，并在子节点重新列生成
```

边使用量在 `call_branching.hpp` 的 `BbNode::obtainSolEdgeMap` 中由路线变量汇总。候选筛选和测试由 `packages/branching` 完成；实际创建两个子节点位于 `src/main/src/add_brc.cpp`。禁用边的一侧会删除相关列和 pricing 图中的弧；必须用边的一侧会增加等式约束。搜索持续到树为空或达到时间限制。

## 6. 用 Java/Python 方式理解主要类

| C++ 类 | 可以先理解成 |
| --- | --- |
| `CVRPSolver` | 应用级 facade/service，保存实例并协调各算法模块 |
| `VRPTW` | `class VRPTW extends CVRPSolver`，补充时间窗行为 |
| `BbNode` | 一个搜索树节点及其独立 LP 模型状态 |
| `BBTController` | 接收函数回调的通用 Branch-and-Bound 循环 |
| `CVRP_Pricing` | 根据 dual 生成新路线的 route generator |
| `CVRP_AddColumnController` | 把 `Route` 翻译成 solver 变量和约束系数的 adapter |
| `Solver` | 对 CPLEX/Gurobi API 的统一包装 |

## 7. DVRP 后续不能保持不变的接口

动态外层可以继续把一个阶段交给静态求解器，`BBTController` 的总体循环也可以复用。但阶段求解接口至少需要改变四处：

1. 路线列必须显式携带原始起点或外部起点身份，不能只保存客户序列；
2. 主问题除客户覆盖外，还要为每个外部起点建立“恰好选一条路线”的等式，原始车场不使用现有车辆数行；
3. pricing 必须接收各起点不同的最早时间和剩余容量，并统一返回独立的 return depot；
4. 分支统计必须正确区分客户边、原始起点边和车辆专属外部起点边。

这四点只是源码事实推导出的接入边界，不是最终设计；具体数据结构应在动态状态需求确认后进入技术设计。

## 8. 推荐阅读顺序

若只想掌握主链路，依次看：`main.cpp` → `cvrp.hpp` 构造函数 → `call_read_node_in.cpp` → `cg.cpp` → `add_column.cpp` → `workflow.hpp` → `add_brc.cpp`。无需先读 cuts、标签支配和机器学习候选选择的内部细节。
