# DVRP 多起点 RMP 模块

> 状态：已完成（根 RMP 能力，不是完整列生成或 BP）
>
> 最后更新：2026-10-14
>
> 详细执行顺序见[流程与方法导读](DVRP多起点RMP流程与方法导读.md)；稳定边界见[文件与接口](../设计/M1-标准BP-DVRP/02-多起点RMP文件与接口.md)。

## 1. 当前能力与调用边界

接收 `StageProblem` 和部分可行 `StageRoute`，建立独立连续 LP，支持两阶段人工变量、初始列、增量加列、求解与有效 dual 输出。数学语义遵循[v4](../模型和推导/DVRP%20数学模型-v4.md)。

```text
测试程序（未来为阶段 BP 控制器）
  → DVRP_RmpController(stage)
  → addInitialRoutes / addRoutes
  → solveLp → Phase-I 人工量 / Phase-II 成本 / dual
  → 未来 pricing 返回新路线，再 addRoutes
```

当前最后一条反馈在测试中由扫描枚举全集模拟，没有生产标签算法。`dvrp.exe` 仍只读取实例，RMP 不输出可执行的 `StageSolution`。

## 2. 文件与所有权

| 文件 | 职责 |
| --- | --- |
| [rmp_controller.hpp](../../../packages_dvrp/application/dvrp/src/node/include/rmp_controller.hpp) | 数据契约与控制器公开入口；不包含 CPLEX 头文件 |
| [rmp_controller.cpp](../../../packages_dvrp/application/dvrp/src/node/src/rmp_controller.cpp) | 阶段/路线复核、稀疏建模、增量加列、两阶段求解和资源管理 |
| [rmp_test.cpp](../../../packages_dvrp/application/dvrp/test/rmp/rmp_test.cpp) | 手算、全集 LP 对照、测试定价及边界测试 |
| [CMakeLists.txt](../../../packages_dvrp/application/dvrp/CMakeLists.txt) | 可选 RMP 测试目标、CPLEX 隔离，与原对照目标共享构建配置 |

控制器复制阶段快照，独占 solver model/env；禁止复制控制器。内部 `ModelOwner` 在正常析构和构造失败时释放资源。`columns()` 返回只读引用，后续加列可能使引用失效，需长期保留时由调用方复制。

建模模式来源于原版 `BbNode::buildModel` 和加列模块，保留 GPL-3.0 来源；只使用 `packages_dvrp/common/solver_interface` 副本。生产 RMP 不依赖测试枚举器，不修改原版 `packages/`。

## 3. 实际模型规则

- 每个待规划客户一条覆盖等式，每个外部起点一条选路等式，右端均为1；没有原始车队数量约束。
- 行按快照客户顺序、再按外部起点顺序排列；原 ID/key 通过不同映射转成连续行号。
- 路线显式携带 `startKey`，只访问本阶段客户且不重复；原始路线至少访问一人，外部路线允许直接返场。
- 根据各起点自己的 `startTime`、`remainingCapacity`，计算行驶、等待、服务和返场；使用 `routeOptDistance` 和 `1e-6` 可行性容差。
- 真实成本由 RMP 重算；真实变量非负连续、不显式增加冗余上界1；不把人工列放入 `StageRoute`。
- 初始列尝试各外部起点直接返场及各起点单客户路线；不可行候选跳过，不据此证明整个阶段不可行。
- 同起点、同有序序列复用已有稳定列 ID；不同顺序或不同起点不合并。
- 人工 solver 列为 `0..行数-1`；真实路线 ID 从0开始，solver 列号为“行数＋路线 ID”。本版不删列。
- `fixedCostAfterTau` 不进入列目标和 reduced cost，只在 Phase-II 的 `totalPlannedCost` 中加一次。

## 4. 两阶段与结果有效性

Phase-I 为每行建立一个非负人工变量，真实列目标系数为0，最小化人工量；没有真实路线也可求解。Phase-II 将人工变量固定为0，人工目标清零，恢复真实路线成本。

`enterCostPhase()` 只接受控制器内部最新一次、已复核且人工总量不超过容差的 Phase-I 最优结果；不能用外部伪造结果授权切换。切换后必须重新求 LP 并复核真实等式，不直接宣布成功。

`solveLp()` 返回：

| 字段 | 含义 |
| --- | --- |
| `stageId / modelRevision / phase` | 阶段、模型版本、当前是可行性还是距离目标 |
| `OptimalRmp / TimeLimit / Error` | 仅说明当前 LP，不包含“完整阶段最优/不可行”状态 |
| `objective / artificialSum` | 当前优化目标、非负人工量总和 |
| `lambda / reducedCosts / artificial` | 按真实列 ID 或行号排列的解值 |
| `duals` | 按客户 ID、外部 key 分开的对偶值，同时携带阶段和版本 |
| `routeCost / totalPlannedCost` | 仅 Phase-II 最优且复核通过时提供 |
| `rawStatus / message / solveSeconds` | 原始 solver 状态、诊断及本次求解/复核耗时 |

只在最优且原始可行性、对偶可行性、互补性、目标一致性全部复核通过后提供证明质量的数据。不同最优解不要求 dual 向量相同。

加新列或切换目标会增加版本并清除内部旧结果；纯重复加列不改变版本。人工量正只表示还需可行性定价；只有上层精确定价或已穷尽路线的测试才能判定完整 LP 不可行。Phase-II RMP 成本不能在定价闭合前直接用于 BP 下界剪枝。

## 5. 异常与停止

- 无效输入在写入 solver 前拒绝；整批路线先检查，不能加入半批合法路线后才发现非法项。
- solver 写入/切换 API 失败后控制器失效，禁止继续使用，不假称已回滚。构造阶段 API 失败抛异常并释放已开资源。
- 求解 API/数值错误返回 `Error`，清除对偶/目标等有效数据；异常的辅助 LP 不可行不升级为完整阶段不可行。
- 超时返回 `TimeLimit`，不输出用于定价证明的目标/dual，也不能授权阶段切换；真实动态停止联动尚未接入。
- 空阶段解析求得零路线成本，不伪造 CPLEX 状态码；只有外部起点时仍保留所有必选行。

## 6. 构建与验证

从仓库根目录执行；CPLEX 路径是本机位置，不是源码硬编码：

```powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp-rmp -DDVRP_BUILD_RMP_TESTS=ON -DDVRP_BUILD_REFERENCE_SOLVER=ON -DROUTEOPT_SOLVER=CPLEX "-DCPLEX_ROOT=D:/Applications/Development Tools/CPLEX_Studio221/cplex"
cmake --build build/dvrp-rmp --config Release -j 8
ctest --test-dir build/dvrp-rmp -C Release --output-on-failure
cmake --build build/dvrp-rmp --config Debug -j 8
ctest --test-dir build/dvrp-rmp -C Debug --output-on-failure
```

仅运行 RMP：`ctest --test-dir build/dvrp-rmp -C Release -R dvrp_rmp_ --output-on-failure`。直接运行 `build/dvrp-rmp/Release/dvrp_rmp_test.exe phases` 可查看手算摘要；不带参数运行全部16组。

2026-10-14 本机 MSVC19.44、CPLEX22.1：Release / Debug 均 **56/56 CTest 通过**（原40项＋RMP16项）；仅开启RMP测试时Release **41/41通过**；两项 solver 开关均关闭后 Release **25/25通过**，含434实例读取。没有使用 `-u`。Gurobi 后端和关闭 `BUILD_TESTING` 的非法组合明确拒绝。

| 新增测试组 | 已验证证据 |
| --- | --- |
| matrix / phases / initial | 实际系数、连续 LP、人工量3→2→1→0、成本9→5、初始列和目标切换 |
| resources / validation / identity / duplicate | 起点时间/容量、等待/服务/H、截断距离、ID/key隔离、重复加列、非法批次原子性 |
| empty / infeasible | 空阶段、不同外部车辆分别返场、缺列及每行有列但整体冲突的不可行例 |
| full_lp / pricing / fractional | 固定种子12个小实例、1955列全集、测试定价闭合、LP30/MIP40 |
| size / lifecycle | 200客户＋20外部起点、220人工变量、快照不被调用方修改、20次独立资源生命周期 |
| status / time_limit | 真实参数 API 错误、内部终止状态映射；零秒 LP 本机仍返回状态1（最优） |

## 7. 限制及下一步

没有生产 pricing、ng、RF、树、cuts、列删除、子节点复制或重新开启 Phase-I。下一步先确定 pricing 需求及文件接口，再接根节点列生成。当前生产能力不受5客户限制，但没有真实大实例 BP 性能结论。

尚未实际触发 RMP 运行超时、增量写入中途 API 失败或数值失效；映射和失败处理有代码路径，不能冒充这些故障均已实测。重复创建不是专用泄漏检测；未验证 Gurobi/Linux/macOS。

本次未重跑静态 C204/R204；原 `packages/` 无代码差异，既有 R204 风险见[里程碑](../里程碑/M1-标准BP-DVRP.md)。结果采用浮点容差，不是有理数证明。
