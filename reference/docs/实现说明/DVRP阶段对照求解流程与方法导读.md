# DVRP 阶段对照求解：流程与方法导读

> 状态：已完成（对应测试工具的当前代码）
>
> 最后更新：2026-10-15（更新下游接线状态，工具方法不变）

本文解释“一个阶段如何变成完整 LP/MIP，再把整数路线交回动态层”。运行命令、测试证据和长期限制统一见[模块说明](DVRP小实例集合划分对照求解器.md)，不在这里重复维护。

## 1. 先分清三个角色

- `StageProblem`：动态控制器给出的本阶段数据，不负责求解，也不包含未来披露客户。
- `EnumeratedStage`：该阶段副本及全部可行路线，是数据容器，不是根节点或标签算法。
- `solveFullLp / solveFullMip`：将全集转成 CPLEX 数学模型并求解，返回值对象，不向外传 solver 指针。

实际调用由 [stage_reference_test.cpp](../../../packages_dvrp/application/dvrp/test/stage_reference/stage_reference_test.cpp) 驱动。`main.cpp` 没有调用它们，生产 BP 还没有接入。

```text
测试构造阶段 / DynamicController.buildStageProblem()
  → enumerateStage(stage) → 阶段副本和全部可行路线
      ├→ computeReducedCosts(all, duals)：检查定价
      ├→ solveFullLp(all)：参考 LP 值及 dual，不驱动车辆
      └→ solveFullMip(all)：整数 StageSolution
             → DynamicController.acceptSolution()
             → 下一披露事件 / finish()
```

## 2. 第一步：枚举全部可行路线

公开入口是 `enumerateStage()`，内部使用 `validateStage()` 和 `routeCost()`，代码在 [stage_route_enumerator.cpp](../../../packages_dvrp/application/dvrp/test/stage_reference/stage_route_enumerator.cpp)。

先验证输入：规模最多5客户/5外部起点；ID、起点身份、坐标、时间、容量等合法。外部起点的坐标直接取 `StartSpec.location`，不能要求承诺客户仍在本阶段待服务客户表中。

随后针对每个起点列出所有不重复客户序列，例如两个客户5、8：

```text
原始0： [5]、[8]、[5,8]、[8,5]
外部42：[]、[5]、[8]、[5,8]、[8,5]
```

原始0没有空路线；外部42的空序列表示直接返场。不同起点即使坐标相同也不合并；不同顺序即使成本相同也不合并。

每个候选交给 `routeCost()`：从专属 `startTime` 出发，逐个检查需求累计、行驶、提前到达等待、服务开始不晚于 dueDate，再加服务时间；最后检查返回车场不晚于 H。通过才保存起点 key、原客户 ID 顺序和真实成本。

内部递归只用连续下标遍历数组；输出仍是原客户 ID。车辆 key 与客户 ID 即使相同，也不会混用。

这里不采用 pricing 的标签、NG 或支配规则，且失败的直接返场候选不剪掉后续序列。全部检查完成后才构造 `EnumeratedStage`；输入错误抛异常，没有“枚举一半也当全集”的返回方式。

## 3. 第二步：把路线转成矩阵

入口 `solveFullLp()` / `solveFullMip()` 都调用内部 `buildModel()`；内部 `RowMap` 负责业务 ID 到行号的转换。代码见 [stage_reference_solver.cpp](../../../packages_dvrp/application/dvrp/test/stage_reference/stage_reference_solver.cpp)。

行的顺序是本阶段客户，随后是外部起点。每行 RHS 均为1，关系均为等式。原始起点没有车辆数行；每条已枚举路线成为一列，列顺序与 `all.routes()` 一致。

沿用测试例子：时间100，原始容量100；外部42在 `(10,0)`，130可出发、剩余40；客户5/8在 `(12,0)` / `(14,0)`，需求10/20、服务0、dueDate180；车场 `(0,0)`，H=200。

| 路线列（只列全集中的三条） | 客户5行 | 客户8行 | 外部42行 | 成本 |
| --- | --- | --- | --- | --- |
| 42 → 5 → 8 → 返回 | 1 | 1 | 1 | 18 |
| 42 → 返回 | 0 | 0 | 1 | 10 |
| 0 → 5 → 8 → 返回 | 1 | 1 | 0 | 28 |

成本18来自 `2 + 2 + 14`；130出发、148返场。固定成本7不写入这张矩阵，只在输出阶段成本时加成25。

LP 令列变量非负连续。每列至少属于一个 `=1` 行，所以已隐含变量不超过1，不再人为添加上界 dual。MIP 则明确设二元类型和 `[0,1]` 上下界。

特别注意：复制来的 CPLEX `addVars()` 不使用类型参数，所以这里另外调用 `setVTypeArray()`，然后回读类型。模型建立后还逐项回读行数、列数、等式、RHS、目标和每个系数，检查实际交给 CPLEX 的矩阵。

没有人工列：若完整路线集合仍无法覆盖客户/外部起点，模型就是真正不可行；这与未来“只有少量初始列”的 RMP 不同。

## 4. 第三步：LP 怎样提供定价对照

`solveFullLp()` 调 `Solver::optimize()`。最优状态下取出路线变量、目标、行 dual 和 reduced cost，再由 `validatePrimal()` 核对等式和真实成本。

`computeReducedCosts()` 是独立公开入口，可以接收完整 LP 的 dual，也可以接收人工 dual 或以后同模型 RMP 的 dual：

```text
原始路线 rc = 路线成本 − 访问客户的 dual 之和
外部路线 rc = 路线成本 − 访问客户的 dual 之和 − 所属外部起点的 dual
```

dual 的键必须恰好对应当前客户和外部起点，不接受原始起点虚构的车辆数 dual。返回数组与路线全集逐项对应。

例如客户5、8的 dual 都是20，外部42的 dual 是5，则 `42→5→8→返回` 的 rc 为 `18−20−20−5=−27`。这个数是给 pricing 的已知答案，不是在要求当前完整 LP 的最优 dual 等于这些人工值。

完整 LP 最优结果还检查：所有 rc 非负、CPLEX rc 与独立计算一致、原始/对偶目标相同、正值列满足互补性。只有检查通过，才返回可用 LP 参考结果。对偶解可能不唯一，未来测试不要求 dual 逐项相同。

## 5. 第四步：MIP 怎样变成整数路线

`solveFullMip()` 使用另一份独立模型，调 `Solver::mipOptimize()`。只有状态表示有整数 incumbent 时才取解，检查变量接近0/1、所有等式和成本，再将选中列转为 `StageRoute`。`stageId` 原样带回。

`Optimal` 还要求可用最优界与路线目标一致；其余状态不能只凭“有路线”就称最优。无解时目标字段为空，不用0冒充最优值。阶段固定成本只影响 `totalPlannedCost`，不改变选路。

如何证明这里真的求了整数模型？测试 `fractional()` 设置3个同位置客户，每人需求1、车容量2，每条非空路线成本20：

- LP 可以取覆盖三对客户的路线各0.5，总成本30。
- 整数方案至少两辆车，总成本40。

自动测试同时核对30与40、LP确有分数值以及MIP返回两条原始路线，避免“LP刚好整数”掩盖变量类型错误。

## 6. 第五步：交回动态层，形成测试闭环

测试方法 `dynamic()` 构造三个客户，披露时刻为0、5、15；客户1在 `(10,0)`，服务20。时间0先派车服务客户1，完成时间30。

到时间5和15，动态层产生同一辆车的外部起点：客户1位置、可出发时间30、剩余容量90。MIP只安排其之后的客户，不能取消客户1。每轮将 `result.solution` 交给 `acceptSolution()`，由动态层独立检查覆盖、时间轨迹和成本。

```python
stage = controller.buildStageProblem()
all_routes = enumerateStage(stage)
result = solveFullMip(all_routes)
controller.acceptSolution(result.solution)
# 未停止时，推进到 nextEventTime()；最后调用 finish()
```

该例三个阶段均自动得到整数解，最终实际行驶距离28。阶段计划包含尚未执行的未来距离，不能把三个阶段的计划成本加起来。该测试是枚举 MIP 闭环，不是 BP 闭环。

人工构造的key42阶段只能单独求解，不能提交给没有42号车辆的控制器；真正接动态层时，阶段必须来自该控制器。

## 7. 异常、停止与资源所有权

`ModelOwner` 是 `.cpp` 内部资源管理对象：每次调用独立创建 model/env；正常返回或抛异常都释放，不允许浅拷贝。可理解为 Python 的上下文管理器，而不是动态层持有的业务对象。

`checked()` 将 CPLEX API 非零返回码转为异常；公开求解函数捕获后报告 `Error` 和原因。`Solver::classifyStatus()` 分别解释 LP/MIP 终止码；“超时没找到解”绝不等于“证明不可行”。

超时有整数解时可以保留路线和成本供诊断，但状态仍是 `TimeLimitWithSolution`，动态层停止，不继续派车；没有解则不查询不存在的 incumbent。LP 超时也不提供最优 dual 作为对照。

测试中的 `status()` 覆盖终止码、输入错误和实际API错误；`dynamic()` 模拟两种超时确认停止；`timeLimit()` 真的设置时限，但允许小模型在检查时限前求成最优。后者具体观察结果见模块说明，不与模拟测试混称。

## 8. 下游怎么使用

本工具直接用完整路线求LP/MIP；后续已完成的[双版本pricing测试](DVRP多起点定价模块.md)另外维护部分RMP列，由真实标签定价补列，并用本工具核对路线/rc及收敛LP目标。整数BP目标仍待正式BP完成后比较。只有路线定义一致时才直接比较，ng松弛与分支行需要另行扩展对照边界。

本模块不创建 `BbNode`、bucket graph 或搜索树。RMP与双版本pricing现在由独立模块实现；正式根列生成和BP仍需继续设计，不能把复制了 `Solver` 理解为原版静态算法已经完成多起点适配。
