#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

#include "read_data_controller.hpp"

namespace {
    namespace fs = std::filesystem;

    struct ReadResult {
        std::string fileName;
        std::string instanceName;
        int vehicles;
        double capacity;
        int dimension;
        std::vector<std::vector<double> > values;
    };

    ReadResult readInstance(const fs::path &path) {
        std::string program = "dvrp_read_data_test";
        std::string file = path.string();
        char *argv[] = {program.data(), file.data(), nullptr};
        std::string fileName;
        std::string instanceName;
        int vehicles = 0;
        double capacity = 0.0;
        int dimension = 0;
        std::vector<std::vector<double> > values;
        RouteOpt::Application::DVRP::DVRP_ReadDataController reader(
            2, argv, fileName, instanceName, vehicles, capacity, dimension, values);
        return {fileName, instanceName, vehicles, capacity, dimension, values};
    }

    void require(const bool condition, const std::string &message) {
        if (!condition) {
            throw std::runtime_error(message);
        }
    }
}

int main(int argc, char *argv[]) {
    if (argc != 3) {
        std::cerr << "usage: dvrp_read_data_test <fixture> <instance-directory>\n";
        return 2;
    }

    try {
        const auto fixture = readInstance(argv[1]);
        require(fixture.instanceName == "minimal_valid", "instance name mismatch");
        require(fixture.vehicles == 2, "vehicle count mismatch");
        require(fixture.capacity == 100.0, "capacity mismatch");
        require(fixture.dimension == 3, "fixture dimension mismatch");
        require(fixture.values.size() == 3 && fixture.values[0].size() == 8,
                "fixture shape mismatch");
        const std::vector<double> expected_customer{1, 10, 20, 5, 0, 100, 10, 25};
        require(fixture.values[1] == expected_customer, "DISCLOSURE_TIME or customer fields mismatch");

        const fs::path instance_directory = argv[2];
        std::size_t file_count = 0;
        for (const auto &entry : fs::directory_iterator(instance_directory)) {
            if (!entry.is_regular_file() || entry.path().extension() != ".txt") {
                continue;
            }
            ++file_count;
            const auto result = readInstance(entry.path());
            if (entry.path().filename() == "r101_D_25_E_25_1.txt") {
                require(result.dimension == 81, "80-customer fixture dimension mismatch");
            } else {
                require(result.dimension == 101,
                        "expected 100 customers in " + entry.path().filename().string());
            }
            require(result.values.size() == static_cast<std::size_t>(result.dimension),
                    "dimension and data size mismatch in " + entry.path().filename().string());
        }
        require(file_count == 434, "unexpected DVRP instance count");
        std::cout << "reader tests passed: " << file_count << " instances\n";
        return 0;
    } catch (const std::exception &error) {
        std::cerr << "reader tests failed: " << error.what() << '\n';
        return 1;
    }
}
