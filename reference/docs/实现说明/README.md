# DVRP 已实现模块索引

> 状态：已确认；最后更新：2026-10-28。

本目录记录已经落地并完成验证的 DVRP 模块和测试工具，明确区分生产入口与测试入口。

它与其他文档目录的区别：

- `模型和推导/`：问题定义和数学模型；
- `需求/`：准备实现什么以及业务边界；
- `设计/`：复杂功能准备如何实现，按需创建；
- `实现说明/`：代码当前实际上能做什么。

## 模块状态

| 模块 | 状态 | 代码位置 | 文档 |
| --- | --- | --- | --- |
| DVRP 数据读取 | 已实现并通过测试 | `packages_dvrp/application/dvrp` | [DVRP数据读取模块.md](DVRP数据读取模块.md) |
| DVRP 动态状态与事件推进 | 已实现并通过手工路线测试 | `packages_dvrp/application/dvrp/src/dynamic` | [模块说明](DVRP动态状态与事件推进模块.md)、[流程与方法导读](DVRP动态调度流程与方法导读.md) |
| 小实例完整集合划分对照 | 已实现完整枚举、CPLEX LP/MIP及测试动态闭环 | `packages_dvrp/application/dvrp/test/stage_reference` | [模块说明](DVRP小实例集合划分对照求解器.md)、[流程与方法导读](DVRP阶段对照求解流程与方法导读.md) |
| 多起点 RMP | 已实现根LP、两阶段人工变量、加列和dual | `packages_dvrp/application/dvrp/src/node` | [模块说明](DVRP多起点RMP模块.md)、[流程与方法导读](DVRP多起点RMP流程与方法导读.md) |
| 多起点定价 | 已实现普通无桶/RouteOpt前向桶图，已完成RMP测试接线 | `packages_dvrp/application/dvrp/src/pricing` | [模块说明](DVRP多起点定价模块.md)、[流程与方法导读](DVRP多起点定价流程与方法导读.md) |
| 定价MIP独立对照 | 已实现独立单路径MIP、逐起点最小rc及测试CG审核；不属于生产求解器 | `packages_dvrp/application/dvrp/test/pricing/mip_reference` | [说明与方法导读](DVRP定价MIP独立对照测试.md) |
| 根节点列生成 | 已实现两阶段RMP/pricing正式循环、数值界及总预算 | `packages_dvrp/application/dvrp/src/column_generation` | [模块说明](DVRP根节点列生成模块.md)、[流程与方法导读](DVRP根节点列生成流程与方法导读.md) |
| 双策略分支与受限列生成 | 已实现RF/起点专属有向弧候选、限制及受限节点LP，两版pricing均支持 | `packages_dvrp/application/dvrp/src/branching`及既有RMP/pricing/CG | [模块说明](DVRP双策略分支与受限列生成模块.md)、[流程与方法导读](DVRP双策略分支流程与方法导读.md) |
| 共同BP搜索树与整数恢复 | 已实现单阶段BP、四种组合、整数校验、全局界和停止状态 | `packages_dvrp/branching/bbt`及应用`src/branch_and_price` | [模块说明](DVRP共同BP搜索树与整数恢复模块.md)、[流程与方法导读](DVRP共同BP搜索树与整数恢复流程与方法导读.md) |
| 动态BP闭环与命令行 | 已实现逐事件BP、失败停止、实际距离核对和独立dvrp_solve入口 | 应用`src/simulation`及`src/solve_main.cpp` | [模块说明](DVRP动态BP闭环与命令行模块.md)、[流程与方法导读](DVRP动态BP闭环流程与方法导读.md) |

单阶段BP经显式转换和动态复核，已通过 `dvrp_solve.exe` 自动驱动事件循环；`dvrp.exe`仍只读取。小实例闭环通过不代表能稳定求解完整100客户，也不代表后续增强已实现。

## 实验结果

- [DVRP静态VRPTW规模测试结果](DVRP静态VRPTW规模测试结果.md)：2026-10-26完成的102次静态BP运行，比较两种分支和两版定价；含完整结果表、超时说明、规模边界与汇报用结论。

## 怎样阅读

- 想知道“已经实现什么、怎样运行和验证”：先读模块说明。
- 想知道“业务逻辑如何对应到方法、数据怎样变化”：读详细导读；动态模块已单独拆出，避免扩充简短入口。
- 导读面向未参与 C++ 编写的用户，按实际调用顺序解释，并提供具体例子和源码入口；简单功能可在模块说明内承载，不强制拆文件。

## 维护规则

- 以实际代码和可重复测试为准，不以需求或计划作为完成依据；
- 每个功能完成后都要持久化详细导读；模块行为发生变化时，同一批修改必须同步更新受影响的模块说明和导读；
- 每份说明应包含入口、文件职责、数据流、实际行为、验证方式和已知限制；
- 未实现内容只能写入“已知限制”或需求文档，不能描述成现有能力。
