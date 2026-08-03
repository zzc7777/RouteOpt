# RouteOpt Instances

Runtime instances are grouped by problem type:

```text
instance/
|-- cvrp/
|   `-- P-n20-k2.vrp
`-- vrptw/
    `-- solomon-100/
        |-- c101.txt
        `-- ...
```

The application type is selected at compile time by `app_type` in
`src/main/include/cvrp_macro.hpp`. Rebuild the executable after switching
between `APPLICATION_TYPE::CVRP` and `APPLICATION_TYPE::VRPTW`.

Example from the `packages/application/cvrp` directory:

```powershell
.\bin\Release\cvrp.exe .\instance\vrptw\solomon-100\c101.txt
```

Instance files are supplied at runtime, so adding or replacing data files does
not require rerunning CMake configuration.
