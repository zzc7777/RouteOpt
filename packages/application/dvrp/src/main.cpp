/*
 * Copyright (c) 2025 Zhengzhong (Ricky) You.
 * All rights reserved.
 * Software: RouteOpt
 * License: GPL-3.0
 */

#include <cmath>
#include <iomanip>
#include <iostream>
#include <set>
#include <stdexcept>
#include <string>
#include <vector>

#include "read_data_controller.hpp"

namespace {
    void printHelp() {
        std::cout << "Usage: dvrp <instance.txt>\n"
                  << "       dvrp --help\n";
    }
}

int main(int argc, char *argv[]) {
    if (argc == 2 && std::string(argv[1]) == "--help") {
        printHelp();
        return 0;
    }

    std::string file_name;
    std::string instance_name;
    int vehicle_count = 0;
    double capacity = 0.0;
    int dimension = 0;
    std::vector<std::vector<double> > info_vertex;

    try {
        RouteOpt::Application::DVRP::DVRP_ReadDataController reader(
            argc, argv, file_name, instance_name, vehicle_count,
            capacity, dimension, info_vertex);

        std::set<double> disclosure_events;
        int initially_disclosed = 0;
        double disclosure_min = 0.0;
        double disclosure_max = 0.0;
        for (int i = 1; i < dimension; ++i) {
            const double disclosure = info_vertex[i][7];
            disclosure_events.insert(disclosure);
            if (disclosure == 0.0) {
                ++initially_disclosed;
            }
            if (i == 1 || disclosure < disclosure_min) {
                disclosure_min = disclosure;
            }
            if (i == 1 || disclosure > disclosure_max) {
                disclosure_max = disclosure;
            }
        }

        std::cout << std::setprecision(15)
                  << "instance_name=" << instance_name << '\n'
                  << "vehicles=" << vehicle_count << '\n'
                  << "capacity=" << capacity << '\n'
                  << "customers=" << dimension - 1 << '\n'
                  << "initially_disclosed=" << initially_disclosed << '\n'
                  << "disclosure_events=" << disclosure_events.size() << '\n'
                  << "disclosure_min=" << disclosure_min << '\n'
                  << "disclosure_max=" << disclosure_max << '\n';
        return 0;
    } catch (const std::invalid_argument &error) {
        std::cerr << error.what() << '\n';
        printHelp();
        return 2;
    } catch (const std::exception &error) {
        std::cerr << error.what() << '\n';
        return 1;
    }
}
