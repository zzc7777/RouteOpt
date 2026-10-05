# DVRP 数据读取模块实现说明

> 状态：已完成。
>
> 初版代码提交：`3102d11 feat(dvrp): add first-stage instance reader`。
>
> 最后更新及最近验证：2026-10-05，迁移到 `packages_dvrp` 后 Release 构建及 CTest 9/9 通过。

## 1. 模块作用

该模块提供独立的 `dvrp` 命令行入口，读取 Solomon VRPTW 文本格式的 DVRP 扩展实例。

扩展方式只有一项：在原有七个节点字段后增加 `DISCLOSURE_TIME`。模块负责严格校验输入并输出实例摘要，不执行动态调度或路径求解。

## 2. 代码位置

| 文件 | 实际职责 |
| --- | --- |
| `packages_dvrp/application/dvrp/CMakeLists.txt` | 建立独立 `dvrp` 和 `dvrp_read_data_test` 目标 |
| `packages_dvrp/application/dvrp/src/main.cpp` | CLI、帮助信息、读取摘要和退出码 |
| `packages_dvrp/application/dvrp/src/read_data/include/read_data_controller.hpp` | reader 类接口和外部引用成员 |
| `packages_dvrp/application/dvrp/src/read_data/src/read_data.cpp` | 文件读取、Solomon 段解析和严格校验 |
| `packages_dvrp/application/dvrp/tools/convert_xlsx_instances.py` | 将 Java Excel 数据转换为八字段 TXT |
| `packages_dvrp/application/dvrp/test/read_data_test.cpp` | 字段检查和 434 个实例批量读取测试 |
| `packages_dvrp/application/dvrp/test/fixtures/` | 最小合法输入和错误输入样例 |
| `packages_dvrp/application/dvrp/instance/` | 转换后的 DVRP TXT 实例 |
| `packages_dvrp/common/config/route_opt_macro.hpp` | 从原版复制的配置头文件，提供节点上限和错误机制；保留原内容及许可证 |

模块使用 C++20，编译源文件和配置头文件均来自 `packages_dvrp`，当前不链接 CPLEX、Gurobi、HGS、cuts 或 pricing。原 `packages` 中的静态求解模块保持不变；DVRP reader、CLI 和测试逻辑未因迁移改变。

## 3. 调用流程

```text
dvrp <instance.txt>
  -> main
  -> DVRP_ReadDataController 构造函数
  -> 检查命令行并保存文件路径
  -> 打开文件并提取实例名
  -> 解析 VEHICLE 和 CUSTOMER 段
  -> 校验八字段节点数据
  -> 返回读取结果
  -> main 输出披露时间摘要
```

reader 沿用原 RouteOpt 风格，通过构造函数接收外部引用并写入结果：

```text
fileName
instanceName
vehicleCount
capacity
dimension
info_vertex
```

第一阶段没有新增 `DvrpInstance` 对象。

## 4. 输入格式和节点字段

输入严格沿用 Solomon VRPTW 文本布局，只在客户标题和每个节点行末尾增加 `DISCLOSURE_TIME`：

```text
C101

VEHICLE
NUMBER     CAPACITY
  25         200

CUSTOMER
CUST NO.  XCOORD.  YCOORD.  DEMAND  READY TIME  DUE DATE  SERVICE TIME  DISCLOSURE_TIME

    0      40        50         0         0       1236         0                 0
    1      45        68        10       912        967        90               948
```

实例文件不保存 solver、线程数、距离策略、舍入规则、动态求解策略、已知最优值或运行时外部起点。这些内容由代码、CMake、命令行或后续动态状态生成。

`info_vertex[node][field]` 的字段含义为：

| 下标 | 字段 |
| --- | --- |
| 0 | ID |
| 1 | X 坐标 |
| 2 | Y 坐标 |
| 3 | DEMAND |
| 4 | READY_TIME |
| 5 | DUE_DATE |
| 6 | SERVICE_TIME |
| 7 | DISCLOSURE_TIME |

`dimension` 包含车场，客户数为 `dimension - 1`。TXT 中的车辆 `NUMBER` 当前会被读取并输出；按照数学模型 v4，它在未来阶段求解中只作为输入元数据，不应自动成为车队上限。

## 5. 已实现的校验

文件结构：

- 文件非空且第一项为 Solomon 实例名；
- `VEHICLE` 和 `CUSTOMER` 段各出现一次且顺序正确；
- 存在唯一 `NUMBER/CAPACITY` 标题和客户标题；
- 客户标题包含 `DISCLOSURE_TIME`；
- `EOF` 后不允许出现非空内容。

车辆数据：

- 车辆数和容量必须各有一个数值；
- 两者必须为正整数。

节点数据：

- 每行必须正好八个有限数值；
- ID 必须严格为车场 `0`，随后客户 `1..n`；
- 需求必须为整数且位于 `[0, capacity]`；
- `readyTime <= dueDate`；
- 服务时间非负；
- `0 <= disclosureTime <= dueDate`；
- 车场需求、服务时间和披露时间必须为零；
- 至少包含一个车场和一个客户；
- 节点数不能超过 `MAX_NUM_CUSTOMERS`。

错误信息包含实例路径、输入行号和原因；格式或文件错误会以非零状态结束。

## 6. 命令行行为

配置和构建：

```powershell
cmake -S packages_dvrp/application/dvrp -B build/dvrp -DCMAKE_BUILD_TYPE=Release
cmake --build build/dvrp --config Release -j 8
```

若已有 `build/dvrp` 缓存仍指向迁移前的源码目录，首次配置使用 `cmake --fresh -S packages_dvrp/application/dvrp -B build/dvrp -DCMAKE_BUILD_TYPE=Release` 重建缓存；不要移动旧缓存或手工改生成的项目文件。本次 Windows 验证使用 Visual Studio 17 2022、x64。

运行：

```powershell
.\build\dvrp\Release\dvrp.exe .\packages_dvrp\application\dvrp\instance\c101_D_25_E_25.txt
```

支持的形式：

- `dvrp <instance.txt>`：读取并输出实例名、车辆数、容量、客户数和披露时间统计；
- `dvrp --help`：显示帮助，返回 `0`；
- 参数错误：显示用法，返回 `2`；
- 文件或内容错误：输出定位信息并以非零状态结束。

## 7. 实例转换工具

`convert_xlsx_instances.py` 从 Java reference 的 Excel 文件读取八个字段，并以对应 Solomon TXT 作为静态七字段模板：

- 静态字段一致时，完整保留 Solomon 文本，只追加披露时间列；
- 非标准客户规模重新渲染节点行；
- 检查 Excel 表头、数值、客户 ID、容量、时间窗和返回车场；
- 默认排除 `c101_D_25_E_25_1` 和 `c101_D_25_E_25_2`；
- 当前输出目录包含 434 个 TXT。

转换工具是数据准备脚本，不会在 `dvrp.exe` 运行时自动执行。默认输出为 `packages_dvrp/application/dvrp/instance`；Excel 来源仍在 `reference/dvrp`，静态模板仍取自原 `packages/application/cvrp/instance/vrptw/solomon-100`，不会修改这些源数据。

## 8. 验证结果

验证命令：

```powershell
ctest --test-dir build/dvrp -C Release --output-on-failure
```

2026-10-05 迁移后重新构建与测试的结果：

- 9/9 个 CTest 通过；
- 全部 434 个 TXT 均成功读取；
- 433 个实例包含 1 个车场和 100 个客户；
- `r101_D_25_E_25_1.txt` 包含 1 个车场和 80 个客户；
- 最小 fixture 的八个字段逐项匹配；
- 缺失披露时间、多余字段、非法时间窗、错误 ID、非法披露时间和错误标题均被拒绝；
- `packages/application/cvrp` 的静态入口没有因该模块而修改。

## 9. 当前限制

该模块目前只完成数据读取，不包含：

- 动态事件和车辆状态推进；
- `DynamicProblemData`、阶段快照或外部起点生成；
- 多起点主问题、pricing、列生成或分支；
- `-u`、`-b`、`-d/-n` 和 Travel Time Matrix；
- CPLEX/Gurobi 后端选择或实际路径求解。

后续动态层应在 reader 输出之上建立具名数据对象，但不需要修改现有八字段输入格式。
