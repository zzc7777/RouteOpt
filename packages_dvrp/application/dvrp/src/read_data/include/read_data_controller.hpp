/*
 * Copyright (c) 2025 Zhengzhong (Ricky) You.
 * All rights reserved.
 * Software: RouteOpt
 * License: GPL-3.0
 */

#ifndef ROUTE_OPT_DVRP_READ_DATA_CONTROLLER_HPP
#define ROUTE_OPT_DVRP_READ_DATA_CONTROLLER_HPP

#include <fstream>
#include <functional>
#include <string>
#include <vector>

namespace RouteOpt::Application::DVRP {
    class DVRP_ReadDataController {
    public:
        DVRP_ReadDataController(int argc, char *argv[],
                                std::string &f_name, std::string &ins_name,
                                int &num_vehicle, double &cap, int &dim,
                                std::vector<std::vector<double> > &info_vertex);

        DVRP_ReadDataController() = delete;
        ~DVRP_ReadDataController() = default;

    private:
        std::ifstream file{};

        std::reference_wrapper<std::string> f_name_ref;
        std::reference_wrapper<std::string> ins_name_ref;
        std::reference_wrapper<int> num_vehicle_ref;
        std::reference_wrapper<double> cap_ref;
        std::reference_wrapper<int> dim_ref;
        std::reference_wrapper<std::vector<std::vector<double> > > info_vertex_ref;

        void getData(int argc, char *argv[]);
        void generateInstancePath(int argc, char *argv[]);
        void takeDataFromFile();
        void extractName();
        void parseVRPTWOrRVRPSTW();
    };
}

#endif // ROUTE_OPT_DVRP_READ_DATA_CONTROLLER_HPP
