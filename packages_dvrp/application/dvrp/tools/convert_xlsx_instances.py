#!/usr/bin/env python3
"""Append Excel disclosure times to Solomon VRPTW txt instances."""

from __future__ import annotations

import argparse
import math
import re
from pathlib import Path
from typing import Sequence

try:
    import openpyxl
except ImportError as exc:  # pragma: no cover - environment failure
    raise SystemExit("openpyxl is required: python -m pip install openpyxl") from exc


EXPECTED_HEADERS = (
    "ID",
    "Coord_X",
    "Coord_Y",
    "Demand",
    "Time_Earliest",
    "Time_Latest",
    "Time_Service",
    "Time_Disclosure",
)
DEFAULT_EXCLUSIONS = {
    "c101_D_25_E_25_1",
    "c101_D_25_E_25_2",
}
BASE_INSTANCE_PATTERN = re.compile(r"^(?P<base>(?:rc|c|r)\d{3})(?:_|$)", re.IGNORECASE)


class ConversionError(RuntimeError):
    pass


def parse_args() -> argparse.Namespace:
    repository_root = Path(__file__).resolve().parents[4]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source",
        type=Path,
        default=repository_root / "reference/dvrp/src/main/resources/DVRP_Instance",
    )
    parser.add_argument(
        "--solomon-dir",
        type=Path,
        default=repository_root / "packages/application/cvrp/instance/vrptw/solomon-100",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=repository_root / "packages_dvrp/application/dvrp/instance",
    )
    parser.add_argument("--exclude", action="append", default=[], metavar="STEM")
    return parser.parse_args()


def numeric_value(value: object, context: str) -> int | float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ConversionError(f"{context}: expected a numeric value, got {value!r}")
    if not math.isfinite(float(value)):
        raise ConversionError(f"{context}: value must be finite, got {value!r}")
    return value


def integer_value(value: object, context: str) -> int:
    number = numeric_value(value, context)
    rounded = round(float(number))
    if not math.isclose(float(number), rounded, abs_tol=1e-9):
        raise ConversionError(f"{context}: expected an integer, got {value!r}")
    return int(rounded)


def format_number(value: object) -> str:
    number = numeric_value(value, "output value")
    if float(number).is_integer():
        return str(int(number))
    return format(float(number), ".15f").rstrip("0").rstrip(".")


def parse_numeric_line(line: str) -> list[float] | None:
    parts = line.split()
    if not parts:
        return None
    try:
        return [float(part) for part in parts]
    except ValueError:
        return None


def read_excel_rows(path: Path) -> list[tuple[int | float, ...]]:
    workbook = openpyxl.load_workbook(path, read_only=True, data_only=False)
    try:
        if workbook.sheetnames != ["Sheet1"]:
            raise ConversionError(f"{path.name}: expected only Sheet1")
        sheet = workbook["Sheet1"]
        headers = tuple(sheet.cell(1, column).value for column in range(1, 9))
        if headers != EXPECTED_HEADERS:
            raise ConversionError(f"{path.name}: unexpected headers {headers!r}")

        rows: list[tuple[int | float, ...]] = []
        for row_number, cells in enumerate(sheet.iter_rows(min_row=2, max_col=8), start=2):
            if any(cell.data_type == "f" for cell in cells):
                raise ConversionError(f"{path.name}:{row_number}: formulas are not supported")
            values = tuple(cell.value for cell in cells)
            if all(value is None for value in values):
                continue
            rows.append(
                tuple(
                    numeric_value(value, f"{path.name}:{row_number}:{column_number}")
                    for column_number, value in enumerate(values, start=1)
                )
            )
        return rows
    finally:
        workbook.close()


def read_solomon_template(path: Path) -> tuple[int, int, list[tuple[float, ...]], str]:
    if not path.is_file():
        raise ConversionError(f"missing Solomon instance: {path}")
    text = path.read_text(encoding="utf-8")
    vehicle_count: int | None = None
    capacity: int | None = None
    static_rows: list[tuple[float, ...]] = []
    customer_header_seen = False
    for line in text.splitlines():
        if "CUST NO." in line:
            customer_header_seen = True
            continue
        values = parse_numeric_line(line)
        if values is None:
            continue
        if len(values) == 2 and vehicle_count is None:
            vehicle_count = integer_value(values[0], f"{path.name}: vehicle count")
            capacity = integer_value(values[1], f"{path.name}: capacity")
        elif customer_header_seen and len(values) >= 7:
            static_rows.append(tuple(values[:7]))
    if vehicle_count is None or capacity is None or not static_rows:
        raise ConversionError(f"{path.name}: invalid Solomon template")
    return vehicle_count, capacity, static_rows, text


def rows_equal(left: Sequence[Sequence[int | float]], right: Sequence[Sequence[int | float]]) -> bool:
    if len(left) != len(right):
        return False
    return all(
        len(a) == len(b)
        and all(math.isclose(float(x), float(y), abs_tol=1e-9) for x, y in zip(a, b))
        for a, b in zip(left, right)
    )


def validate_excel_instance(path: Path, rows: Sequence[Sequence[int | float]], capacity: int) -> None:
    if len(rows) < 3:
        raise ConversionError(f"{path.name}: expected depot, customers, and return depot")
    routeopt_rows = rows[:-1]
    return_depot = rows[-1]
    customer_count = len(routeopt_rows) - 1
    if integer_value(routeopt_rows[0][0], f"{path.name}: depot id") != 0:
        raise ConversionError(f"{path.name}: depot id must be 0")
    for expected_id, row in enumerate(routeopt_rows[1:], start=1):
        if integer_value(row[0], f"{path.name}: customer id") != expected_id:
            raise ConversionError(f"{path.name}: customer ids must be exactly 1..n")
        demand = integer_value(row[3], f"{path.name}: demand")
        if not 0 <= demand <= capacity:
            raise ConversionError(f"{path.name}: demand is outside [0, capacity]")
        if float(row[4]) > float(row[5]):
            raise ConversionError(f"{path.name}: inverted time window")
        if float(row[6]) < 0 or not 0 <= float(row[7]) <= float(row[5]):
            raise ConversionError(f"{path.name}: invalid service or disclosure time")
    if integer_value(return_depot[0], f"{path.name}: return id") != customer_count + 1:
        raise ConversionError(f"{path.name}: return depot id must be n + 1")
    for column in (1, 2, 4, 5):
        if not math.isclose(float(routeopt_rows[0][column]), float(return_depot[column]), abs_tol=1e-9):
            raise ConversionError(f"{path.name}: return depot differs from origin depot")


def append_disclosures_to_template(
    template_text: str,
    routeopt_rows: Sequence[Sequence[int | float]],
) -> str:
    lines = template_text.splitlines()
    header_indices = [index for index, line in enumerate(lines) if "CUST NO." in line]
    if len(header_indices) != 1:
        raise ConversionError("Solomon template must contain exactly one customer header")
    header_index = header_indices[0]
    data_indices = [
        index
        for index in range(header_index + 1, len(lines))
        if (values := parse_numeric_line(lines[index])) is not None and len(values) >= 7
    ]
    if len(data_indices) != len(routeopt_rows):
        raise ConversionError("template row count differs from Excel row count")

    lines[header_index] += "   DISCLOSURE_TIME"
    for line_index, row in zip(data_indices, routeopt_rows):
        lines[line_index] += f"{format_number(row[7]):>18}"
    return "\n".join(lines) + ("\n" if template_text.endswith("\n") else "")


def render_solomon_row(row: Sequence[int | float]) -> str:
    values = [format_number(value) for value in row]
    widths = (5, 10, 11, 11, 11, 11, 11, 18)
    return "".join(f"{value:>{width}}" for value, width in zip(values, widths))


def render_nonstandard_instance(
    template_text: str,
    routeopt_rows: Sequence[Sequence[int | float]],
) -> str:
    lines = template_text.splitlines()
    header_index = next(index for index, line in enumerate(lines) if "CUST NO." in line)
    first_data_index = next(
        index
        for index in range(header_index + 1, len(lines))
        if (values := parse_numeric_line(lines[index])) is not None and len(values) >= 7
    )
    prefix = lines[:first_data_index]
    prefix[header_index] += "   DISCLOSURE_TIME"
    return "\n".join(prefix + [render_solomon_row(row) for row in routeopt_rows]) + "\n"


def main() -> int:
    args = parse_args()
    exclusions = DEFAULT_EXCLUSIONS | set(args.exclude)
    if not args.source.is_dir():
        raise ConversionError(f"source directory does not exist: {args.source}")
    source_files = sorted(path for path in args.source.glob("*.xlsx") if path.stem not in exclusions)
    if not source_files:
        raise ConversionError(f"no xlsx files to convert in {args.source}")

    args.output.mkdir(parents=True, exist_ok=True)
    exact_template_copies = 0
    nonstandard_files: list[str] = []
    for source_path in source_files:
        base_match = BASE_INSTANCE_PATTERN.match(source_path.stem)
        if not base_match:
            raise ConversionError(f"{source_path.name}: cannot determine Solomon base instance")
        base_instance = base_match.group("base").lower()
        template_path = args.solomon_dir / f"{base_instance}.txt"
        _, capacity, solomon_rows, template_text = read_solomon_template(template_path)
        excel_rows = read_excel_rows(source_path)
        validate_excel_instance(source_path, excel_rows, capacity)
        routeopt_rows = excel_rows[:-1]
        excel_static_rows = [tuple(row[:7]) for row in routeopt_rows]

        if len(excel_static_rows) == len(solomon_rows):
            if not rows_equal(excel_static_rows, solomon_rows):
                raise ConversionError(f"{source_path.name}: static fields differ from {template_path.name}")
            output_text = append_disclosures_to_template(template_text, routeopt_rows)
            exact_template_copies += 1
        else:
            output_text = render_nonstandard_instance(template_text, routeopt_rows)
            nonstandard_files.append(
                f"{source_path.name}: {len(routeopt_rows) - 1} customers; "
                f"{template_path.name} has {len(solomon_rows) - 1}"
            )

        (args.output / f"{source_path.stem}.txt").write_text(
            output_text,
            encoding="utf-8",
            newline="\n",
        )

    expected_names = {f"{path.stem}.txt" for path in source_files}
    missing = expected_names - {path.name for path in args.output.glob("*.txt")}
    if missing:
        raise ConversionError(f"missing generated files: {sorted(missing)[:5]}")

    print(f"Converted {len(source_files)} xlsx files to {args.output}")
    print(f"Copied Solomon text and appended one column for {exact_template_copies} files")
    print(f"Excluded: {', '.join(sorted(exclusions))}")
    for warning in nonstandard_files:
        print(f"WARNING: {warning}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ConversionError as error:
        raise SystemExit(f"conversion failed: {error}") from error
