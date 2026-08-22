# DVRP 数据读取功能需求

## 1. 文档状态

- 状态：第一阶段边界已确认，待实现
- 对应功能：DVRP 输入层的 TXT 实例读取、校验和读取摘要
- 实现状态：第一阶段已实现，待提交
- 实现前置条件：已满足；若实现中发现与现有代码事实冲突，须先回到本文修订

本文只界定一个单功能：沿用 RouteOpt 原 VRPTW reader 的组织方式，把 DVRP Solomon TXT 实例读取到入口持有的原始字段和二维数组中，并证明读取结果正确。本文不设计动态状态更新、多起点阶段模型、pricing、割或分支，也不在本阶段提前封装动态层数据对象。

## 2. 原 RouteOpt 数据读取功能

### 2.1 代码位置和文件职责

原数据读取模块位于：

```text
packages/application/cvrp/src/read_data/
  include/read_data_controller.hpp  类定义、成员引用和读取入口
  include/read_data_macro.hpp       READ_NO_LINE、INITIAL_UB 默认值
  src/read_data.cpp                 命令行、文件选择和各格式解析实现
```

相邻调用和消费模块为：

```text
packages/application/cvrp/src/main/include/cvrp.hpp
packages/application/cvrp/src/initial/include/initial_controller.hpp
packages/application/cvrp/src/initial/src/initial_controller.cpp
packages/application/cvrp/src/main.cpp
```

### 2.2 原入口和调用链

静态 CVRP/VRPTW 的入口链路为：

```text
main(argc, argv)
  -> new CVRPSolver(argc, argv) 或 new VRPTW(argc, argv)
  -> 构造 CVRPSolver
  -> 构造其成员 CVRP_ReadDataController
  -> CVRP_ReadDataController::getData(argc, argv)
       -> ub = INITIAL_UB
       -> generateInstancePath(argc, argv)
       -> takeDataFromFile()
            -> 打开实例文件
            -> extractName()
            -> 根据编译期 app_type 选择解析器
            -> 关闭实例文件
  -> 构造 InitialController
  -> 将原始数据转换为需求、时间窗和距离矩阵
```

`app_type` 定义在 `cvrp_macro.hpp`，使用 `if constexpr` 在编译期选择 CVRP 或 VRPTW。程序不会根据文件扩展名或文件内容自动选择应用类型。

### 2.3 原命令行功能

`generateInstancePath()` 当前支持：

```text
cvrp.exe <instance-path>   直接使用第一个位置参数作为实例路径
-d <list-path>            指定批量任务列表
-n <line-number>          读取批量列表中的指定行
-u <upper-bound>          设置初始上界
-b <tree-path>            指定 two-stage 节点保存/恢复路径
```

`-d/-n` 对应的列表行包含“实例路径 + 已知上界”。这些参数既包含输入文件选择，也混入了求解上界和节点恢复配置。

### 2.4 原数据所有权

原 reader 不拥有最终实例数据，也不返回一个完整实例对象。`CVRPSolver` 拥有：

```text
f_name                    实例路径
ins_name                  实例名称
tree_path                 two-stage 路径
num_vehicle               车辆数
cap                       车辆容量
dim                       节点总数
info_vertex               原始节点二维数组
ub                        初始上界
```

`CVRP_ReadDataController` 使用 `std::reference_wrapper` 保存这些成员的引用，并在构造期间直接修改它们。因此原实现具有以下特点：

- 创建 reader 对象会立即发生文件 I/O；
- reader 与 `CVRPSolver` 的成员布局和构造顺序耦合；
- 调用者不能仅通过返回值判断读取结果；
- 读取结果依赖二维数组中的数字下标语义。

### 2.5 原标准 Solomon VRPTW 解析流程

标准 Solomon 文件由 `parseVRPTWOrRVRPSTW()` 处理：

```text
从文件开头向后寻找包含 NUMBER 的行
  -> 读取下一行的车辆数和容量
继续寻找包含 CUST NO. 的客户标题行
  -> 跳过标题后的一个空行
逐行读取节点
  -> 每行读取前 7 个数值
  -> 空行或 MAX_NUM_CUSTOMERS 上限结束
  -> 实际读取行数写入 dim
```

VRPTW 的 `info_vertex` 固定为七列：

| 下标 | 字段 |
| ---: | --- |
| 0 | ID |
| 1 | X 坐标 |
| 2 | Y 坐标 |
| 3 | 需求 |
| 4 | 时间窗开始 |
| 5 | 时间窗结束 |
| 6 | 服务时间 |

现有 parser 只读取前七个值。如果输入行末尾存在 `DISCLOSURE_TIME`，该值会留在 `stringstream` 中并被静默忽略。

### 2.6 原第二类 VRPTW 格式

`parseVRPTWSecondType()` 首先搜索 `Travel Time Matrix` 标记。如果找到，则读取：

```text
Dimension
Capacity
ub
Vertex Information 的七列节点数据
```

如果没有找到该标记，文件流回到开头，再调用标准 Solomon parser。当前 DVRP 生产实例不采用这种格式。

### 2.7 原 CVRP 解析

`parseCVRPSolver()` 读取 TSPLIB 风格的：

```text
NAME / DIMENSION / CAPACITY / VEHICLES
NODE_COORD_SECTION
DEMAND_SECTION
DEPOT_SECTION
```

CVRP 的 `info_vertex` 只有 ID、X、Y、需求四列。这条解析链与 DVRP 输入无关，但与 VRPTW parser 位于同一个 `read_data.cpp` 中。

### 2.8 原读取结果的后续使用

`InitialController` 接收 `info_vertex` 后：

```text
info_vertex[i][1..2] -> 欧氏距离矩阵 cost_mat4_vertex
info_vertex[i][3]    -> demand[i]
info_vertex[i][4]    -> earliest_time[i]
info_vertex[i][5]    -> latest_time[i]
info_vertex[i][6]    -> service_time[i]
info_vertex[0][5]    -> 时间范围 H
```

后续 pricing、主问题和可行性检查使用这些派生数组，不再读取原 TXT 文件。

### 2.9 原实现的关键假设和限制

- reader 同时负责命令行、实例路径、上界、two-stage 路径和文件解析；
- CVRP/VRPTW 类型是编译期选择；
- 标准 VRPTW 只有七个节点字段；
- `info_vertex` 依靠数字下标表达字段含义；
- 节点数量先按 `MAX_NUM_CUSTOMERS` 分配；
- 标准 Solomon parser 对缺失段落、读取失败、字段数错误、重复 ID 和非法时间窗缺少完整诊断；
- 查找 `NUMBER` 和 `CUST NO.` 的循环没有可靠的文件结束失败分支；
- reader 默认单车场，节点 0 具有固定车场语义。

## 3. Java 参考输入语义

Java reference 的 `ExcelReader` 读取八个字段：

```text
ID, Coord_X, Coord_Y, Demand,
Time_Earliest, Time_Latest, Time_Service, Time_Disclosure
```

其中 `Time_Disclosure` 写入 `Customer.callInTime`。Java 还会从配置读取车辆容量、初始车辆数、速度和舍入参数，并从 Excel 最后一行读取逻辑返回车场。

C++ DVRP TXT 已经按 `DVRP实例格式V1.md` 做出不同边界：

- 车辆数和容量继续来自 Solomon `VEHICLE` 段；
- 速度、舍入和 solver 配置不进入实例文件；
- Excel 的逻辑返回车场行不写入 TXT；
- TXT 只在 Solomon 七列末尾增加 `DISCLOSURE_TIME`。

## 4. DVRP 数据读取目标

### 4.1 功能目标

新增 DVRP 专用读取模块，实现：

```text
DVRP TXT
  -> 沿用原 VRPTW reader 的入口和解析流程
  -> 解析车辆、容量、车场和八列节点数据
  -> 校验结构与数值语义
  -> 通过外部引用写入入口持有的字段和 info_vertex
  -> 独立命令行输出规范化摘要
```

### 4.2 成功标准

- 不修改现有 CVRP/VRPTW reader 和命令行行为；
- 正确读取现有 DVRP 实例，且不依赖文件名推断客户数、车辆数或容量；
- 第八列不丢失，并能通过 `info_vertex[i][7]` 访问；
- 非法输入必须明确失败，错误包含文件路径、行号和原因；
- reader 可以不安装、不查找、不链接 CPLEX/Gurobi 独立构建和测试；
- 后续动态层可以在下一阶段把这些原始字段单独封装成动态数据对象。

## 5. 功能范围

### 5.1 本功能包含

- DVRP 独立 TXT reader；
- 沿用原 RouteOpt 成员字段和 `info_vertex` 二维数组；
- 标准 Solomon 八字段解析；
- 数据格式和基础语义校验；
- 实例名称提取；
- 最小独立命令行入口；
- 读取结果摘要；
- reader 单元测试和全量实例读取测试；
- 独立 CMake/CTest 配置。

### 5.2 本功能不包含

- Excel 读取；
- CVRP TSPLIB 读取；
- `Travel Time Matrix` 第二类格式；
- 距离矩阵和旅行时间计算；
- 逻辑返回车场生成；
- 披露事件排序和动态阶段快照；
- 外部起点生成；
- 主问题、pricing、割、分支和列生成；
- `-u` 已知上界；
- `-b` 静态 two-stage 节点恢复；
- `-d/-n` 批量任务列表；
- CPLEX/Gurobi 后端选择。

“不包含”表示 DVRP 首期不带入这些功能，不表示删除或改变原 CVRP/VRPTW 中已有的实现。

CPLEX/Gurobi 后端原本也不由 `read_data` 选择。现有 CVRP/VRPTW 在 `packages/application/cvrp/CMakeLists.txt` 中通过 `ROUTEOPT_SOLVER=GUROBI|CPLEX` 选择后端，再生成编译定义 `SOLVER_TYPE`。DVRP reader 首期不调用求解器，所以可以完全不依赖该配置；后续接入阶段求解器时，再沿用相同的 CMake 选择方式。

## 6. 输入契约

### 6.1 文件布局

唯一支持的生产格式为 `DVRP实例格式V1.md` 中的 Solomon 最小扩展：

```text
C101

VEHICLE
NUMBER     CAPACITY
  25         200

CUSTOMER
CUST NO. ... SERVICE TIME DISCLOSURE_TIME

0 40 50 0 0 1236 0 0
1 45 68 10 912 967 90 948
```

每个节点行必须正好包含：

```text
ID X Y DEMAND READY_TIME DUE_DATE SERVICE_TIME DISCLOSURE_TIME
```

### 6.2 结构规则

- 第一条非空行是 Solomon 基准名称；该行需要存在，但第一阶段不新增字段保存它；
- 必须且只能有一个 `VEHICLE` 段；
- `VEHICLE` 段必须包含 `NUMBER` 和 `CAPACITY` 标题及一行两个数值；
- 必须且只能有一个 `CUSTOMER` 段和一个包含 `CUST NO.` 的标题行；
- 客户标题必须包含 `DISCLOSURE_TIME`；
- 标题后的空行允许存在，也允许省略；
- 节点数据之间允许空行；
- 可选 `EOF` 表示节点数据结束；
- 节点数据开始后，除空行和 `EOF` 外，不允许其他非数值内容；
- 至少包含一行车场和一行客户。

### 6.3 数值规则

- 所有数值必须是有限值，拒绝 `NaN` 和正负无穷；
- 车辆数和容量必须是正整数；
- ID 和需求必须是整数；
- 第一条节点记录必须是车场，车场 ID 必须为 0；
- 车场需求、服务时间和披露时间必须为 0；
- 客户 ID 必须按文件顺序严格为 `1..n`，不得重复或跳号；
- 客户需求必须位于 `[0, vehicleCapacity]`；
- `readyTime <= dueDate`；
- `serviceTime >= 0`；
- `0 <= disclosureTime <= dueDate`。

reader 不校验“所有客户总需求能否由给定车辆数完成”，也不执行完整路线可行性判断；这些属于求解层。

### 6.4 实例名称

沿用原 RouteOpt 的 `ins_name`：从实例文件路径中去掉目录和扩展名，例如：

```text
c101_D_25_E_25.txt -> c101_D_25_E_25
```

文件第一条非空行仍保留 Solomon 基准名称（例如 `C101`），但第一阶段不为它新增输出字段。多个动态变体通过各自的文件名区分。

## 7. 输出数据结构

第一阶段不新增 `DvrpNode` 或 `DvrpInstance`，沿用原 RouteOpt reader 的输出形式。DVRP 入口拥有以下字段，reader 通过引用写入：

```cpp
std::string f_name;
std::string ins_name;
int num_vehicle;
double cap;
int dim;
std::vector<std::vector<double>> info_vertex;
```

`info_vertex` 由原 VRPTW 的七列扩展为八列：

| 下标 | 字段 |
| ---: | --- |
| 0 | ID |
| 1 | X 坐标 |
| 2 | Y 坐标 |
| 3 | 需求 |
| 4 | 时间窗开始 |
| 5 | 时间窗结束 |
| 6 | 服务时间 |
| 7 | 披露时间 `DISCLOSURE_TIME` |

所有权和阶段边界：

- DVRP 入口拥有上述字段，reader 与原实现一样保存其引用并在构造期间写入；
- reader 不创建外部起点、返回车场、动态状态或任何求解器对象；
- 后续动态层另行定义需求文档，再把这些原始字段封装成适合动态计算的数据对象；
- 本阶段不为了后续动态层提前改变原 reader 的数据组织方式。

## 8. Reader 接口和处理流程

### 8.1 对外接口

接口沿用原 `CVRP_ReadDataController` 的构造方式，只去掉本阶段不支持的上界、two-stage 和批量任务字段：

```cpp
class DVRP_ReadDataController {
public:
    DVRP_ReadDataController(
        int argc,
        char* argv[],
        std::string& f_name,
        std::string& ins_name,
        int& num_vehicle,
        double& cap,
        int& dim,
        std::vector<std::vector<double>>& info_vertex);
};
```

调用示例：

```cpp
DVRP_ReadDataController reader(
    argc, argv, f_name, ins_name,
    num_vehicle, cap, dim, info_vertex);
```

与原 RouteOpt 一致，构造 reader 时立即解析命令行路径并读取文件。读取结果通过外部引用写入，不返回完整实例对象。

### 8.2 内部流程

```text
构造 DVRP_ReadDataController
  -> getData(argc, argv)
  -> generateInstancePath(argc, argv)
  -> 检查路径并打开文件
  -> 沿用原 extractName() 提取 ins_name
  -> 定位并校验 VEHICLE 段
  -> 读取 num_vehicle 和 cap
  -> 定位并校验 CUSTOMER 段与 DISCLOSURE_TIME 标题
  -> 逐行解析正好 8 个数值
  -> 对每行执行数值和 ID 校验
  -> 写入 info_vertex 并记录 dim
```

解析器继续使用 `std::getline`、`std::stringstream`、固定的 `MAX_NUM_CUSTOMERS` 上限和二维数组。严格校验必须补足可靠的 EOF、字段数量、数值和语义失败分支；超过节点上限时明确报错，不允许静默截断。

### 8.3 错误行为

- 文件不存在、无法打开、结构缺失或数值非法时抛出统一的读取异常；
- 错误文本格式至少包含：

```text
<path>:<line>: <reason>
```

- 对无法归属到具体行的错误使用：

```text
<path>: <reason>
```

- 不允许解析失败后使用默认 0 继续运行；
- 不允许静默忽略第九个字段或其他尾随文本。

## 9. 独立命令行入口

CLI 是 Command Line Interface（命令行接口）的缩写，指用户在 PowerShell 中输入命令运行程序。这里的 CLI 只用于验证“文件已经被正确读取”，不是最终求解器界面。

首期命令为：

```text
dvrp.exe <instance.txt>
dvrp.exe --help
```

职责边界沿用原 RouteOpt 形式：

- `main.cpp` 创建并持有读取结果字段；
- `DVRP_ReadDataController` 接收 `argc/argv`，解析实例路径并读取文件；
- `main.cpp` 在 reader 构造完成后使用读取结果输出摘要；
- 参数错误返回退出码 2；
- 文件或校验错误返回退出码 1；
- 成功返回退出码 0。

读取成功时输出稳定摘要：

```text
instance_name=<沿用 extractName() 得到的实例名>
vehicles=<初始车辆数>
capacity=<车辆容量>
customers=<客户数>
initially_disclosed=<disclosureTime 为 0 的客户数>
disclosure_events=<不同披露时间数量>
disclosure_min=<客户最小披露时间>
disclosure_max=<客户最大披露时间>
```

摘要用于验证 reader，不代表最终 DVRP 求解日志格式。

## 10. 相对原实现的改动清单

### 10.1 复用和保留

- 保留标准 Solomon 的实例名称、`VEHICLE`、`NUMBER/CAPACITY`、`CUSTOMER` 和前七列语义；
- 保留从命令行传入实例路径的基本使用方式；
- 参考原 `parseVRPTWOrRVRPSTW()` 的总体解析顺序，在 DVRP 目录中独立实现，不共享 parser 代码；
- 保留外部引用、构造时读取、二维 `info_vertex`、数字下标、固定节点上限和手工实例名提取；
- 保留 reader 同时解析命令行实例路径和文件的组织方式；
- 复用原有 C++ 标准流，不引入第三方 parser；
- 保持节点 0 是原始车场、客户为 `1..n` 的输入语义；
- 保持现有 CVRP/VRPTW reader、入口和构建行为不变。

这里的“参考”只表示两者遵循相似的 Solomon 文件读取流程，不表示抽取或调用同一个公共 parser。静态 VRPTW 与 DVRP 是两个独立入口，允许各自保留少量重复的解析代码。

### 10.2 DVRP 首期不带入

- CVRP TSPLIB parser；
- `Travel Time Matrix` 第二类 parser；
- `-d/-n` 批量任务列表；
- `-u` 已知上界；
- `-b` two-stage 节点路径；
- `INITIAL_UB` 和 `READ_NO_LINE`；
- 编译期 `app_type` 分支；
- HGS、solver 和 pricing 初始化。

这些内容仍保留在原 CVRP/VRPTW 应用中。

### 10.3 需要改变

- 节点字段由七个变为八个，新增 `DISCLOSURE_TIME`；
- 宽松、静默的解析改为严格字段数和语义校验；

第一阶段只保留以上两项变化。返回对象、具名节点、显式 `read(path)`、`std::filesystem::path::stem()`、动态增长容器以及 reader/CLI 分离等改进均推迟到数据读取阶段完成后再单独评估，不在本功能中顺带重构。

### 10.4 需要新增

- `DVRP_ReadDataController` 的独立实现；
- DVRP 独立 CLI；
- reader 独立 CMake library 和测试目标；
- 最小合法 fixture、错误输入测试和全量实例读取测试；
- 第八列和披露时间统计。

### 10.5 不修改的原文件

本功能预期不修改：

```text
packages/application/cvrp/src/read_data/**
packages/application/cvrp/src/initial/**
packages/application/cvrp/src/main.cpp
packages/application/cvrp/src/main/include/cvrp.hpp
```

如果实现中发现必须修改上述文件，应停止编码，先更新本文说明原因、兼容方案和回归范围，再由用户确认。

## 11. 计划文件和实现顺序

计划在 `packages/application/dvrp` 中新增：

```text
CMakeLists.txt
src/main.cpp
src/read_data/include/read_data_controller.hpp
src/read_data/src/read_data.cpp
test/read_data_test.cpp
test/fixtures/minimal_valid.txt
```

这里的 `fixture` 指专门供自动测试使用的固定输入文件，作用类似 Java 单元测试中 `src/test/resources` 下的测试数据。`minimal_valid.txt` 只包含一个车场和两个客户，数据足够小，可以人工逐项核对 reader 是否读对。

“reader 测试骨架”指先创建一个类似 JUnit 测试类的 C++ 测试程序，先写清楚输入和期望结果，例如“车辆数应为 2、客户数应为 2、客户 1 的披露时间应为 5”。它不是算法骨架，也不包含求解逻辑。后文统一称为“reader 自动测试程序”。

实现顺序：

```text
1. 确认并修订本文
2. 按原接口定义 DVRP_ReadDataController 的外部引用
3. 建立最小合法测试实例和 reader 自动测试程序
4. 建立独立 CMake/CTest 目标
5. 复制原 VRPTW 解析流程并实现第八列和严格校验
6. 实现 main.cpp 摘要入口
7. 运行单元测试、真实实例测试和全量读取测试
8. 检查原 cvrp 目录未发生修改
```

## 12. 测试需求

### 12.1 正常输入

- 1 个车场、2 个客户的最小 fixture；
- `c101_D_25_E_25.txt`，期望 100 个客户、25 辆车、容量 200；
- `r101_D_25_E_25_1.txt`，期望 80 个客户，证明不从文件名推断规模；
- 当前 `packages/application/dvrp/instance/` 下全部 434 个 TXT 均可读取。

### 12.2 错误输入

- 文件不存在；
- 空文件或缺少基准名称；
- 缺少或重复 `VEHICLE`、`NUMBER/CAPACITY`、`CUSTOMER`；
- 标题缺少 `DISCLOSURE_TIME`；
- 节点行只有 7 个字段或多于 8 个字段；
- 非数值、`NaN` 或无穷值；
- 非整数车辆数、容量、ID 或需求；
- 车辆数或容量非正；
- 首节点不是车场 0；
- 车场需求、服务时间或披露时间非零；
- 客户 ID 重复、跳号或乱序；
- 需求为负或超过容量；
- 时间窗反转；
- 服务时间为负；
- 披露时间为负或晚于 due date。

### 12.3 构建和隔离

- DVRP reader 在不链接 CPLEX/Gurobi 的情况下构建；
- 使用 CTest 运行测试；
- 原 CVRP/VRPTW 文件无 diff；
- 本功能不使用 `-u` 作为正确性条件。

## 13. 验收标准

满足以下条件后，数据读取功能才算完成：

1. 文档中确认的输入均能稳定写入入口字段和八列 `info_vertex`；
2. 八个节点字段逐项与 TXT 原值一致；
3. 全部非法输入按约定失败，错误可定位到文件和行；
4. 434 个现有实例全量读取通过；
5. CLI 能输出约定摘要和退出码；
6. reader 构建不依赖 solver；
7. 原 CVRP/VRPTW 入口、reader 和源文件保持不变；
8. 测试命令、结果和实例路径被记录在实现提交说明中。

## 14. 用户审阅结论和待确认项

### 14.1 已确认

1. 第一阶段不新增 `DvrpInstance`，读取结果继续使用原 RouteOpt 的外部引用和二维 `info_vertex`，动态层后续再单独封装；
2. 接受客户 ID 必须按文件顺序严格为 `1..n`；
3. 接受 `disclosureTime <= dueDate` 作为输入合法性要求；
4. 接受首期不支持 Travel Time Matrix、`-u`、`-b` 和 `-d/-n`；其中批量读取测试由自动测试程序遍历实例目录完成；
5. 接受第 9 节定义的 CLI 摘要作为第一阶段的人工验收输出，节点逐字段正确性由自动测试验证；
6. 不抽取公共 parser，也不修改原 VRPTW reader。DVRP 在独立目录中实现八字段 reader，允许与静态 VRPTW reader 存在少量重复逻辑。
7. 相对原 reader，第一阶段只改变两项：节点由七列扩展为八列，以及增加严格字段与语义校验；其余结构和接口尽可能保持原样。

### 14.2 待确认

本功能当前没有待确认项。进入实现前如果发现需求与现有代码事实冲突，应停止编码并回到本文补充边界。

### 14.3 实现验证记录

- 独立配置：`cmake -S packages/application/dvrp -B build/dvrp -DCMAKE_BUILD_TYPE=Release`
- 独立构建：`cmake --build build/dvrp --config Release -j 8`
- 测试：`ctest --test-dir build/dvrp -C Release --output-on-failure`
- 结果：9/9 个 CTest 通过；其中 reader 测试读取 `packages/application/dvrp/instance/` 下全部 434 个 TXT。
- 规模检查：433 个实例为 101 个节点（含车场），`r101_D_25_E_25_1.txt` 为 81 个节点。
- 手工 CLI：`c101_D_25_E_25.txt` 读取为 100 个客户，`r101_D_25_E_25_1.txt` 读取为 80 个客户。
- 静态隔离：`packages/application/cvrp` 没有源文件 diff。
