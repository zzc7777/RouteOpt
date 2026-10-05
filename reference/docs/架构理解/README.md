# RouteOpt 架构理解索引

> 状态：有效
>
> 最后更新：2026-10-04

本目录只记录经过源码核对的 RouteOpt 当前行为，用于理解原系统。DVRP 的目标方案写在 `reference/docs/设计/`，不得把尚未实现的 DVRP 设计写成 RouteOpt 现状。

## 阅读顺序

| 顺序 | 文档 | 解决的问题 |
| --- | --- | --- |
| 1 | [01-RouteOpt标准BP调用链.md](01-RouteOpt标准BP调用链.md) | 从入口到搜索树的整体数据流、对象所有权和模块边界 |
| 2 | [02-RouteOpt受限主问题RMP.md](02-RouteOpt受限主问题RMP.md) | RMP 有哪些行和列，根模型如何启动 |
| 3 | [03-RouteOpt定价与标签算法.md](03-RouteOpt定价与标签算法.md) | dual 如何进入 pricing，桶图和标签如何产生路线 |
| 4 | [04-RouteOpt加列与列生命周期.md](04-RouteOpt加列与列生命周期.md) | pricing 路线如何转换为 LP 变量并进入节点列池 |
| 5 | [05-RouteOpt分支与搜索树.md](05-RouteOpt分支与搜索树.md) | 分数边如何生成两个子节点并重新列生成 |
| 6 | [06-RouteOpt核心与增强模块.md](06-RouteOpt核心与增强模块.md) | 哪些是当前定价核心，哪些是 cuts、枚举、学习等增强功能 |

## 最小理解路径

如果只想先掌握标准 Branch-and-Price，阅读 01—05 即可。第 06 篇用于决定 DVRP M1 应关闭哪些功能。

```text
RMP 求解
  -> 读取 dual
  -> pricing 生成负 reduced-cost 路线
  -> add-column 加入 RMP
  -> 无负列后检查整数性
  -> 分数解进入 branching
```

数据读取模块已经单独记录在 `reference/docs/实现说明/DVRP数据读取模块.md`，这里不重复解释文件格式。
