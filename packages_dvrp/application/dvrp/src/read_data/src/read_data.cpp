/*
 * Copyright (c) 2025 Zhengzhong (Ricky) You.
 * All rights reserved.
 * Software: RouteOpt
 * License: GPL-3.0
 */

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdlib>
#include <iomanip>
#include <limits>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include "read_data_controller.hpp"
#include "route_opt_macro.hpp"

namespace RouteOpt::Application::DVRP {
    namespace {
        struct SourceLine {
            std::size_t number;
            std::string text;
        };

        std::string trimCopy(const std::string &input) {
            const auto first = input.find_first_not_of(" \t\r\n");
            if (first == std::string::npos) {
                return {};
            }
            const auto last = input.find_last_not_of(" \t\r\n");
            return input.substr(first, last - first + 1);
        }

        std::string toUpperAscii(std::string input) {
            std::transform(input.begin(), input.end(), input.begin(), [](const unsigned char ch) {
                return static_cast<char>(std::toupper(ch));
            });
            return input;
        }

        bool isSectionLine(const std::string &line, const std::string &section) {
            return toUpperAscii(trimCopy(line)) == toUpperAscii(section);
        }

        bool containsToken(const std::string &line, const std::string &token) {
            return toUpperAscii(line).find(toUpperAscii(token)) != std::string::npos;
        }

        [[noreturn]] void fatalInputError(const std::string &path,
                                          const std::size_t line,
                                          const std::string &reason) {
            RouteOpt::logMessage<RouteOpt::ErrorType::ERROR_EXIT>(
                path + ":" + std::to_string(line) + ": " + reason,
                __FILE__, __LINE__, __func__);
            std::abort();
        }

        [[noreturn]] void fatalFileError(const std::string &path, const std::string &reason) {
            RouteOpt::logMessage<RouteOpt::ErrorType::ERROR_EXIT>(
                path + ": " + reason,
                __FILE__, __LINE__, __func__);
            std::abort();
        }

        void requireFinite(const std::vector<double> &values,
                           const std::string &path,
                           const std::size_t line) {
            for (const double value : values) {
                if (!std::isfinite(value)) {
                    fatalInputError(path, line, "all fields must be finite numbers");
                }
            }
        }

        bool isIntegerValue(const double value) {
            return std::isfinite(value) && std::trunc(value) == value;
        }

        std::vector<double> parseExactNumbers(const SourceLine &source,
                                              const std::size_t expected,
                                              const std::string &path) {
            std::stringstream stream(source.text);
            std::vector<double> values;
            values.reserve(expected);
            for (std::size_t i = 0; i < expected; ++i) {
                double value = 0.0;
                if (!(stream >> value)) {
                    fatalInputError(path, source.number,
                                    "expected exactly " + std::to_string(expected) + " numeric fields");
                }
                values.push_back(value);
            }

            std::string extra;
            if (stream >> extra) {
                fatalInputError(path, source.number,
                                "expected exactly " + std::to_string(expected) + " numeric fields");
            }
            requireFinite(values, path, source.number);
            return values;
        }

        void requireInteger(const double value, const std::string &field,
                            const SourceLine &source, const std::string &path) {
            if (!isIntegerValue(value)) {
                fatalInputError(path, source.number, field + " must be an integer");
            }
        }

        std::size_t findOnlySection(const std::vector<SourceLine> &lines,
                                    const std::string &section,
                                    const std::string &path) {
            std::size_t found = std::numeric_limits<std::size_t>::max();
            int count = 0;
            for (std::size_t i = 0; i < lines.size(); ++i) {
                if (isSectionLine(lines[i].text, section)) {
                    found = i;
                    ++count;
                }
            }
            if (count != 1) {
                fatalFileError(path, "expected exactly one " + section + " section");
            }
            return found;
        }

        std::size_t nextNonEmpty(const std::vector<SourceLine> &lines,
                                 std::size_t begin,
                                 const std::size_t end,
                                 const std::string &path,
                                 const std::string &reason) {
            for (std::size_t i = begin; i < end; ++i) {
                if (!trimCopy(lines[i].text).empty()) {
                    return i;
                }
            }
            fatalFileError(path, reason);
        }
    }

    DVRP_ReadDataController::DVRP_ReadDataController(
        int argc, char *argv[], std::string &f_name, std::string &ins_name,
        int &num_vehicle, double &cap, int &dim,
        std::vector<std::vector<double> > &info_vertex)
        : f_name_ref(f_name),
          ins_name_ref(ins_name),
          num_vehicle_ref(num_vehicle),
          cap_ref(cap),
          dim_ref(dim),
          info_vertex_ref(info_vertex) {
        getData(argc, argv);
    }

    void DVRP_ReadDataController::getData(int argc, char *argv[]) {
        generateInstancePath(argc, argv);
        takeDataFromFile();
    }

    void DVRP_ReadDataController::generateInstancePath(int argc, char *argv[]) {
        if (argc != 2 || argv == nullptr || argv[1] == nullptr ||
            std::string(argv[1]).empty() || argv[1][0] == '-') {
            throw std::invalid_argument("usage: dvrp <instance.txt>");
        }
        f_name_ref.get() = argv[1];
    }

    void DVRP_ReadDataController::takeDataFromFile() {
        file.open(f_name_ref.get());
        if (!file.is_open()) {
            fatalFileError(f_name_ref.get(), "file not found or cannot be opened");
        }

        extractName();
        parseVRPTWOrRVRPSTW();
        file.close();
    }

    void DVRP_ReadDataController::extractName() {
        const std::string path = f_name_ref.get();
        const auto separator = path.find_last_of("/\\");
        const auto file_start = separator == std::string::npos ? 0 : separator + 1;
        const auto extension = path.find_last_of('.');
        const auto file_end = extension != std::string::npos && extension >= file_start
                                  ? extension
                                  : path.size();
        ins_name_ref.get() = path.substr(file_start, file_end - file_start);
        if (ins_name_ref.get().empty()) {
            fatalFileError(path, "instance name is empty");
        }
    }

    void DVRP_ReadDataController::parseVRPTWOrRVRPSTW() {
        std::vector<SourceLine> lines;
        std::string line;
        std::size_t line_number = 0;
        while (std::getline(file, line)) {
            lines.push_back({++line_number, line});
        }
        if (lines.empty()) {
            fatalFileError(f_name_ref.get(), "empty instance file");
        }

        const auto first_non_empty = nextNonEmpty(
            lines, 0, lines.size(), f_name_ref.get(), "missing Solomon instance name");
        if (trimCopy(lines[first_non_empty].text).empty()) {
            fatalFileError(f_name_ref.get(), "missing Solomon instance name");
        }
        if (isSectionLine(lines[first_non_empty].text, "VEHICLE") ||
            isSectionLine(lines[first_non_empty].text, "CUSTOMER")) {
            fatalInputError(f_name_ref.get(), lines[first_non_empty].number,
                            "missing Solomon instance name before sections");
        }

        const auto vehicle_section = findOnlySection(lines, "VEHICLE", f_name_ref.get());
        const auto customer_section = findOnlySection(lines, "CUSTOMER", f_name_ref.get());
        if (vehicle_section >= customer_section) {
            fatalFileError(f_name_ref.get(), "VEHICLE section must precede CUSTOMER section");
        }

        std::size_t vehicle_header = std::numeric_limits<std::size_t>::max();
        for (std::size_t i = vehicle_section + 1; i < customer_section; ++i) {
            if (containsToken(lines[i].text, "NUMBER") && containsToken(lines[i].text, "CAPACITY")) {
                if (vehicle_header != std::numeric_limits<std::size_t>::max()) {
                    fatalInputError(f_name_ref.get(), lines[i].number,
                                    "duplicate NUMBER/CAPACITY header");
                }
                vehicle_header = i;
            }
        }
        if (vehicle_header == std::numeric_limits<std::size_t>::max()) {
            fatalFileError(f_name_ref.get(), "missing NUMBER/CAPACITY header");
        }

        const auto vehicle_values = parseExactNumbers(
            lines[nextNonEmpty(lines, vehicle_header + 1, customer_section,
                               f_name_ref.get(), "missing vehicle count and capacity")],
            2, f_name_ref.get());
        const auto &vehicle_line = lines[nextNonEmpty(
            lines, vehicle_header + 1, customer_section,
            f_name_ref.get(), "missing vehicle count and capacity")];
        requireInteger(vehicle_values[0], "vehicle count", vehicle_line, f_name_ref.get());
        requireInteger(vehicle_values[1], "vehicle capacity", vehicle_line, f_name_ref.get());
        if (vehicle_values[0] <= 0 || vehicle_values[1] <= 0) {
            fatalInputError(f_name_ref.get(), vehicle_line.number,
                            "vehicle count and capacity must be positive");
        }
        num_vehicle_ref.get() = static_cast<int>(vehicle_values[0]);
        cap_ref.get() = vehicle_values[1];

        std::size_t customer_header = std::numeric_limits<std::size_t>::max();
        for (std::size_t i = customer_section + 1; i < lines.size(); ++i) {
            if (containsToken(lines[i].text, "CUST NO.")) {
                if (customer_header != std::numeric_limits<std::size_t>::max()) {
                    fatalInputError(f_name_ref.get(), lines[i].number,
                                    "duplicate CUST NO. header");
                }
                customer_header = i;
            }
        }
        if (customer_header == std::numeric_limits<std::size_t>::max()) {
            fatalFileError(f_name_ref.get(), "missing CUST NO. header");
        }
        if (!containsToken(lines[customer_header].text, "DISCLOSURE_TIME")) {
            fatalInputError(f_name_ref.get(), lines[customer_header].number,
                            "customer header must contain DISCLOSURE_TIME");
        }

        auto &info_vertex = info_vertex_ref.get();
        info_vertex.assign(MAX_NUM_CUSTOMERS, std::vector<double>(8, 0.0));
        std::size_t node_count = 0;
        bool eof_seen = false;
        for (std::size_t i = customer_header + 1; i < lines.size(); ++i) {
            const auto trimmed = trimCopy(lines[i].text);
            if (trimmed.empty()) {
                continue;
            }
            if (trimmed == "EOF") {
                eof_seen = true;
                continue;
            }
            if (eof_seen) {
                fatalInputError(f_name_ref.get(), lines[i].number,
                                "non-empty content is not allowed after EOF");
            }
            if (node_count >= MAX_NUM_CUSTOMERS) {
                fatalInputError(f_name_ref.get(), lines[i].number,
                                "node count exceeds MAX_NUM_CUSTOMERS");
            }

            const auto values = parseExactNumbers(lines[i], 8, f_name_ref.get());
            for (const auto value : values) {
                if (!std::isfinite(value)) {
                    fatalInputError(f_name_ref.get(), lines[i].number,
                                    "node fields must be finite numbers");
                }
            }
            requireInteger(values[0], "node ID", lines[i], f_name_ref.get());
            requireInteger(values[3], "demand", lines[i], f_name_ref.get());
            if (values[0] != static_cast<double>(node_count)) {
                fatalInputError(f_name_ref.get(), lines[i].number,
                                "node IDs must be 0, then customers 1..n in order");
            }
            if (values[3] < 0 || values[3] > cap_ref.get()) {
                fatalInputError(f_name_ref.get(), lines[i].number,
                                "demand must be between 0 and vehicle capacity");
            }
            if (values[4] > values[5]) {
                fatalInputError(f_name_ref.get(), lines[i].number,
                                "ready time must not exceed due date");
            }
            if (values[6] < 0) {
                fatalInputError(f_name_ref.get(), lines[i].number,
                                "service time must be non-negative");
            }
            if (values[7] < 0 || values[7] > values[5]) {
                fatalInputError(f_name_ref.get(), lines[i].number,
                                "disclosure time must satisfy 0 <= disclosure <= due date");
            }
            if (node_count == 0 && (values[0] != 0 || values[3] != 0 ||
                                    values[6] != 0 || values[7] != 0)) {
                fatalInputError(f_name_ref.get(), lines[i].number,
                                "the first node must be depot 0 with zero demand, service and disclosure time");
            }
            info_vertex[node_count] = values;
            ++node_count;
        }

        if (!eof_seen && node_count == 0) {
            fatalFileError(f_name_ref.get(), "CUSTOMER section contains no node data");
        }
        if (node_count < 2) {
            fatalFileError(f_name_ref.get(), "instance must contain a depot and at least one customer");
        }
        dim_ref.get() = static_cast<int>(node_count);
        info_vertex.resize(node_count);
    }
}
