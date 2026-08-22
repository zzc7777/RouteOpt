# DVRP 实例格式 V1

## 1. 设计原则

DVRP 实例严格沿用 RouteOpt 当前的 Solomon VRPTW 文本格式，只在客户数据末尾增加一个 `DISCLOSURE_TIME` 字段。这样可以最大限度复用现有 VRPTW 读取逻辑，避免把算法配置、求解参数或派生元数据放进实例文件。

与原 VRPTW 相比，只有两处变化：

1. 客户表标题末尾增加 `DISCLOSURE_TIME`；
2. 车场和每个客户的数据行末尾增加一个披露时间数值。

其余内容，包括实例标题、`VEHICLE` 段、车辆数、容量、`CUSTOMER` 段、前 7 列、空行布局以及是否存在文件结束标记，均保持原 VRPTW 格式。文件不增加 `FORMAT_VERSION`、目标函数、速度、舍入规则或动态程度等字段。

## 2. 格式示例

```text
C101

VEHICLE
NUMBER     CAPACITY
  25         200

CUSTOMER
CUST NO.  XCOORD.   YCOORD.    DEMAND   READY TIME  DUE DATE   SERVICE   TIME   DISCLOSURE_TIME
 
    0      40         50          0          0       1236          0                    0
    1      45         68         10        912        967         90                  948
    2      45         70         30        825        870         90                  849
```

每个节点数据行共有 8 个数值：

```text
ID X Y DEMAND READY_TIME DUE_DATE SERVICE_TIME DISCLOSURE_TIME
```

## 3. 字段语义

- `ID`、坐标、需求、时间窗和服务时间与现有 Solomon VRPTW 完全相同。
- `DISCLOSURE_TIME` 表示客户向调度系统公开的时间。
- 车场节点 `0` 的 `DISCLOSURE_TIME` 固定为 `0`。
- 客户披露时间为 `0` 表示该客户在初始阶段已经公开。
- 披露时间只决定客户何时进入动态阶段，不改变客户原有时间窗。

## 4. 不写入实例的内容

下列信息继续由程序配置、CMake 或命令行控制，不写入 DVRP 实例：

- solver 后端及线程数；
- 距离和旅行时间计算策略；
- 舍入规则；
- 动态阶段求解策略；
- 目标函数名称；
- 已知最优值或上界；
- 格式版本和 Java 配置元数据。

动态产生的外部起点也不写入原始实例。它们由事件发生时的车辆位置、当前时间和容量状态生成。

## 5. 转换规则

转换工具为 `packages/application/dvrp/tools/convert_xlsx_instances.py`：

- 对标准实例逐行复制对应的 `packages/application/cvrp/instance/vrptw/solomon-100/*.txt`；
- 只从 Excel 的 `Time_Disclosure` 列读取并追加第 8 个数值；
- Excel 最后一行是 Java 使用的逻辑返回车场，原 Solomon 文件没有该行，因此不写入输出；
- `c101_D_25_E_25_1.xlsx` 和 `c101_D_25_E_25_2.xlsx` 默认排除。
