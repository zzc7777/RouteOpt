# DVRP 动态 BP 闭环：流程与方法导读

> 状态：已完成（动态BP生产循环的方法导读）
>
> 最后更新：2026-10-26
>
> 用法、测试和限制统一见[模块说明](DVRP动态BP闭环与命令行模块.md)，这里解释实际方法与数据边界。

## 1. 三个角色，谁负责什么

可以类比Java：`solve_main`是程序启动方法；`DVRP_SimulationController`是组织整次运行的业务服务；`DynamicController`是保存车辆状态并执行方案的对象。单阶段BP仍是独立的优化服务。

```text
solve_main.main
  ├─ parse → reader → DynamicController
  └─ SimulationController.run
       └─ runWithSolver（只有一个循环；生产传入真实BP）
            ├─ buildStageProblem → BP.solve → toStageSolution
            ├─ 临时动态副本.acceptSolution → 核对成本 → 提交
            ├─ 临时动态副本.advanceTo / finish → 结算与日志 → 提交
            └─ SimulationResult
```

不会再新建一套客户数据模型，也不会让main直接操纵RMP、根节点或标签。`StageProblem`保持原接口；BP内部自己准备编号、起点资源、模型和树。

## 2. 启动：`main`和`parse`

源码：[solve_main.cpp](../../../packages_dvrp/application/dvrp/src/solve_main.cpp)。`parse`是文件内辅助函数，不是公开算法接口。

输入：完整TXT路径、必选分支和定价、可选阶段秒数及节点数。输出：路径及已有 `BpOptions`。它先检查未知参数、重复参数、数字是否合法；错误返回2，尚未读文件或开始求解。

随后main把参数整理为只有“程序名、文件路径”的两项，再调用原 `DVRP_ReadDataController`。因此reader不需要理解RF、桶图或预算，也不需要修改它的接口。

reader填充原来的 `info_vertex`、容量和实例名。main用这些数据构造DynamicController，打印车辆数元数据但不把它当作车队上限。旧main仍只读取；用户选择 `dvrp_solve` 才进入新流程。

## 3. 进入循环：`run`和`runWithSolver`

公开声明：[simulation_controller.hpp](../../../packages_dvrp/application/dvrp/src/simulation/include/simulation_controller.hpp)；实现：[simulation_controller.cpp](../../../packages_dvrp/application/dvrp/src/simulation/src/simulation_controller.cpp)。

`run`只负责给共用循环传入真实BP调用。`runWithSolver`持有自己的DynamicController和固定配置副本，拒绝已经接收过结果或推进过事件的控制器，不偷偷恢复旧仿真。

测试可通过 `SimulationDetail::runWithSolver` 换掉“求一个阶段”的调用，制造特定失败；CLI没有这个选择。这里不复制第二套循环，也不把测试MIP链接到生产程序。

## 4. 当前阶段：`buildStageProblem` → `BP.solve`

循环读取当前阶段快照和下一披露时间，先打印STAGE与START，然后把当前StageProblem交给BP。BP看不到原输入中尚未披露的客户。

例如时间5车辆正在去客户1，预计30完成服务，剩余容量90。传给BP的是“客户1位置、时间30、容量90”的外部起点，车辆实际仍在路上；不是把仿真时钟推进到30。

BP返回 `BpResult`：状态、整数见证、上下界、统计和诊断。每次求阶段都新建树；下一阶段不会沿用上一阶段的列池、dual、节点编号历史或分支限制。阶段时限从本次solve重新开始，计算的现实秒数不推动动态时间。

## 5. 接收前：身份、状态、路线、成本四层检查

1. 循环核对BP的stageId、分支策略和定价后端，防止串阶段或串配置。
2. `toStageSolution`沿用已有结果转换；`validateConversion`核对原BP状态与转换状态一致。没有证书的Optimal不能变成可执行方案。
3. 在临时DynamicController副本上调用 `acceptSolution`，由原动态模块检查客户覆盖、外部起点、容量、时间窗和返场期限，并重算时间表。
4. 如果是Optimal，再比较动态记录与BP见证的路线成本、加固定成本后的计划成本；不是只比较一个最终数字。

为什么临时复制？假如路线校验成功，但随后发现BP报告成本错了，就应整阶段拒绝，而不能把车辆后续计划已经改了一半。全部检查及本步骤日志输出成功，才移动提交副本。

`StageBpSummary.accepted`和日志executed仅表示该阶段计划已接收；未来距离并未因此发生。真正行驶要等advanceTo/finish结算。

## 6. 非最优或异常：停止，不继续循环

有效的Infeasible、TimeLimit及其他未完成状态会经原 `acceptSolution` 停止通道保存。即使BP有整数路线，只要超时或预算耗尽，也不派车、不推进、不沿用旧方案覆盖新请求。

错误stageId、错误成本、非法路线、转换失败或输出失败则由外层捕获：返回StoppedError，保存最后成功提交的状态、尝试求解的快照及已有BP诊断。不能把这种错误解释成模型不可行。

因此需区分两种字段：运行结果的status说明“这次运行是否完成”；finalState是“截至哪里可信”。例如日志失败时，运行停止，但finalState可以仍显示Running；调用者不能据此自行接着执行。

所有中途停止都不调用后续 `advanceTo/finish`；若仅最终摘要输出失败，车辆结算可能已经完成，但CLI仍返回错误，不能报告一次成功交付。

## 7. 时间推进：`advanceTo`或`finish`

有下一披露事件时，在临时副本上调用 `advanceTo(next)`。它沿已接受的轨迹结算到事件时刻，再完成服务判定、释放请求，准备下一阶段。新循环没有重写这些业务规则。

无下一事件时调用 `finish()`：执行完所有剩余服务和返场，检查全部完成。返回Completed后，才可生成完成摘要；最后一次BP成功本身还不够。

推进得到的时间和距离必须有限且不倒退；ADVANCE日志写出该区间增量。输出及检查失败则丢弃临时副本，保留之前的结算点。记录不是断点恢复文件，断流也不保证文本事务完整。

## 8. 三事件例，数据是怎样变的

读取 `test/simulation/fixtures/three_events.txt`，Q=100。客户1在x=10、服务20，客户2/3在x=12/14、服务0；披露时刻分别0/5/15。

| 调用阶段 | 给BP的客户和起点 | 推进后的实际结果 |
| --- | --- | --- |
| 时间0求解 | 客户1，原始起点 | advanceTo(5)：车辆1已走5，正在去客户1 |
| 时间5求解 | 客户2；外部1位于客户1，时间30、容量90 | advanceTo(15)：车辆到客户1并服务，累计实际10 |
| 时间15求解 | 客户2、3；相同外部1、时间30、容量90 | finish：继续客户2/3后返场，时间48、累计实际28 |

本例三个区间距离分别5、5、18。阶段5的固定剩余行驶5只用于该次规划成本核对；车辆随后走完这段时才实际计费。不会把固定成本再加到累计距离里，也不会把三次完整计划成本相加。

四组合在这个手算例均为28；一般动态实例可有等成本但不同顺序的阶段方案，它们可能导致不同的未来状态，不能强制全程总值一致。

## 9. 返回结果和测试入口

`summarize`只提取BP摘要，成功后不携带整棵树；失败时stoppedBp保留详细诊断。`summaryLog`输出最终状态与实际距离，`simulationExitCode`把业务结果映射为CLI退出码；只有0表示完成或帮助。

测试：[simulation_test.cpp](../../../packages_dvrp/application/dvrp/test/simulation/simulation_test.cpp)中events等用真实BP驱动；oracle对实际出现的同一快照使用独立MIP核对；stops/malformed/io是确定性故障注入；deepStop核对真实树预算中断且有见证仍停止。它们不能互相替代。

具体命令、数量和长期风险见[模块说明](DVRP动态BP闭环与命令行模块.md)。本导读不引入新的需求或算法策略。
