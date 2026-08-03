# Reference Materials

Use this directory for materials that support RouteOpt development but are not part of the build.

Suggested contents:

- Project papers and reading notes (`.md`, `.pdf`)
- Algorithm and implementation notes
- Raw or not-yet-adapted CVRP/VRPTW instances
- Format descriptions and conversion notes for benchmark data

Suggested organization:

```text
reference/
|-- papers/
|-- notes/
`-- instances/
    `-- raw/
```

Files in this directory are not read by CMake or the RouteOpt executable unless they are explicitly integrated later.
