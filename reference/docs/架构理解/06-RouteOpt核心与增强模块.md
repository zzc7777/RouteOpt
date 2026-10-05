# RouteOpt 核心与增强模块

> 状态：已确认
>
> 最后更新：2026-10-04

## 1. 为什么要区分核心和增强模块

RouteOpt 的生产流程不是最小 Branch-and-Price，而是加入了多种加速方法。适配 DVRP 时需要分清：

- 没有它，现有算法就无法运行的核心结构；
- 可以暂时关闭、以后再验证的增强功能；
- 当前是否真的有统一开关。

## 2. 当前核心结构

| 模块 | 当前作用 | 能否直接关闭 |
| --- | --- | --- |
| `Solver` 包装 | 统一调用 CPLEX/Gurobi | 不能；只能选择后端 |
| RMP / `BbNode` | 保存节点 LP、列和约束 | 不能 |
| exact labeling | 证明本轮不存在负 reduced-cost 列 | 标准 BP 不能关闭 |
| bucket graph | 组织标签、资源区间和可扩展弧 | 不能用现有配置关闭 |
| add-column | 把路线转换为 LP 变量 | 不能 |
| 基本搜索树和分支 | 把 LP 松弛推进到整数解 | 完整 BP 不能关闭 |

桶图不是独立插件。`runLabeling()` 直接遍历 `all_forward_buckets/all_backward_buckets`；若移除桶图，需要另写一种 pricing 后端。

## 3. 集成在流程中的增强功能

| 功能 | 当前入口 | 当前开关情况 |
| --- | --- | --- |
| LIGHT/HEAVY pricing | `callLabeling()` | 局部 `if_open_heur`，不是统一配置 |
| 动态 ng/DSSR | 根节点 `augmentNGRound()` | VRPTW 根节点自动调用，无独立总开关 |
| arc elimination | `callLabeling()` 完成后 | 自动调用，无独立总开关 |
| RCC/rank-1 cuts | `callCutting()` | 搜索树总会调用 cutting 回调，无全局布尔开关 |
| enumeration | `callEnumeration()` | 满足流程条件时调用，无独立总开关 |
| HGS | `HGSController` | `IF_HGS_HEURISTIC` 编译期常量，当前为 `false` |
| L2B | candidate selection | `ml_type` 编译期枚举，当前为 `ML_USE_MODEL` |
| BKF/多阶段候选测试 | `BBTController` | `IF_BKF` 编译期常量控制 |
| two-stage 节点 I/O | read/write node | 输入路径和 `IF_WRITE_NODE_OUT` 共同控制 |

当前项目没有一份 YAML/JSON 或命令行配置统一管理这些算法功能。

## 4. 构建层配置

`packages/application/cvrp/CMakeLists.txt` 提供：

```text
ROUTEOPT_SOLVER=GUROBI|CPLEX
ENABLE_VALGRIND_MEM_CHECK=ON|OFF
```

求解器后端属于 CMake 构建配置，不由运行时算法代码切换。

## 5. 参数和功能开关的区别

以下内容是参数调节，不等于关闭模块：

- bucket 数量和步长；
- `LABEL_ASSIGN` 标签内存上限；
- 每轮最多生成多少列；
- ng 邻域大小；
- pricing 时间阈值；
- LP 线程数。

例如把 bucket 数设为 1 仍然使用 bucket graph，不能理解为“关闭桶图”。

## 6. M1 已确认边界

当前里程碑明确暂不包含：

- RCC、rank-1 cuts 和其他高级 cuts；
- enumeration；
- HGS；
- L2B；
- two-stage；
- strong branching 和性能专项优化。

这些是 DVRP M1 的目标边界，不表示静态 RouteOpt 已经具备对应的统一关闭开关。DVRP 适配层需要显式建立自己的功能配置，且不能修改静态入口默认行为。

## 7. 建议的配置边界

后续 DVRP 设计应把两类内容分开：

```text
必选 pricing backend
  bucket_labeling + exact_pricing

可选 feature flags
  heuristic_pricing
  dynamic_ng
  arc_elimination
  cuts
  enumeration
  hgs
  learning_to_branch
  strong_branching
  two_stage_io
```

这只是配置职责划分；具体默认值以 DVRP 设计文档为准。

## 8. 源码入口

```text
packages/application/cvrp/src/main/include/cvrp_macro.hpp
packages/application/cvrp/src/main/src/call_pricing.cpp
packages/application/cvrp/src/main/src/call_cutting.cpp
packages/application/cvrp/src/main/src/call_enumeration.cpp
packages/application/cvrp/src/main/src/call_dynamic_ng.cpp
packages/application/cvrp/CMakeLists.txt
```
