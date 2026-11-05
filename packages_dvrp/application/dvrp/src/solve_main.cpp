/* DVRP adaptation of RouteOpt. Copyright (c) 2025 Zhengzhong (Ricky) You.
 * License: GPL-3.0. Independent solving CLI; the reader-only main is unchanged. */
#include "simulation_controller.hpp"
#include "read_data_controller.hpp"
#include <charconv>
#include <chrono>
#include <cmath>
#include <iomanip>
#include <iostream>
#include <set>
#include <stdexcept>
#include <string_view>

using namespace RouteOpt::Application::DVRP;
namespace {
void help() {
    std::cout << "Usage: dvrp_solve <instance.txt> --branching rf|arc --pricing plain|bucket\n"
                 "                  [--stage-seconds N] [--max-nodes N]\n"
                 "       dvrp_solve --help\n"
                 "Defaults: 60 seconds and 10000 node CG calls PER STAGE; component budgets also apply.\n"
                 "Timeout stops even with an incumbent. No customer truncation or known upper bound.\n"
                 "Exit: 0 completed/help, 1 input/runtime/error, 2 arguments, 3 stage infeasible,\n"
                 "      4 time limit, 5 node/resource/iteration limit.\n";
}
struct Arguments {
    std::string file;
    BpOptions options{BranchStrategy::None,PricingBackend::PlainLabeling};
};
Arguments parse(int argc, char** argv) {
    Arguments out; std::set<std::string> seen;
    for (int i=1;i<argc;++i) {
        std::string key=argv[i];
        if (!key.empty() && key[0]!='-') {
            if (!out.file.empty()) throw std::invalid_argument("exactly one input file is required");
            out.file=key; continue;
        }
        if (key!="--branching" && key!="--pricing" && key!="--stage-seconds" && key!="--max-nodes")
            throw std::invalid_argument("unknown option: "+key);
        if (!seen.insert(key).second) throw std::invalid_argument("duplicate option: "+key);
        if (++i==argc) throw std::invalid_argument("missing value for "+key);
        const std::string_view value=argv[i];
        if (key=="--branching") {
            if(value=="rf")out.options.strategy=BranchStrategy::RyanFoster;
            else if(value=="arc")out.options.strategy=BranchStrategy::StartDirectedArc;
            else throw std::invalid_argument("--branching must be rf or arc");
        } else if (key=="--pricing") {
            if(value=="plain")out.options.nodeCg.pricing.backend=PricingBackend::PlainLabeling;
            else if(value=="bucket")out.options.nodeCg.pricing.backend=PricingBackend::BucketGraph;
            else throw std::invalid_argument("--pricing must be plain or bucket");
        } else if (key=="--stage-seconds") {
            double seconds{}; const auto p=std::from_chars(value.data(),value.data()+value.size(),seconds);
            if(p.ec!=std::errc{} || p.ptr!=value.data()+value.size() || !std::isfinite(seconds) || seconds<0)
                throw std::invalid_argument("--stage-seconds must be a finite nonnegative number");
            out.options.timeLimitSeconds=seconds;
        } else {
            std::size_t nodes{}; const auto p=std::from_chars(value.data(),value.data()+value.size(),nodes);
            if(p.ec!=std::errc{} || p.ptr!=value.data()+value.size())
                throw std::invalid_argument("--max-nodes must be a nonnegative integer in size_t range");
            out.options.maxNodes=nodes;
        }
    }
    if(out.file.empty() || !seen.count("--branching") || !seen.count("--pricing"))
        throw std::invalid_argument("file, --branching and --pricing are required");
    return out;
}
}
int main(int argc, char** argv) {
    const auto began=std::chrono::steady_clock::now();
    if(argc==2 && std::string_view(argv[1])=="--help") { help(); std::cout.flush(); return std::cout ? 0 : 1; }
    Arguments args;
    try { args=parse(argc,argv); }
    catch(const std::invalid_argument& e) { std::cerr << e.what() << '\n'; help(); return 2; }
    try {
        std::string path,name; int vehicles=0,dim=0; double capacity=0;
        std::vector<std::vector<double>> vertices;
        char* readerArgs[]{argv[0],args.file.data()};
        DVRP_ReadDataController reader(2,readerArgs,path,name,vehicles,capacity,dim,vertices);
        std::cout << std::setprecision(17) << "INPUT path=" << std::quoted(path) << " vehicles_metadata=" << vehicles
                  << " capacity=" << capacity << " customers=" << dim-1 << '\n';
        std::cout.flush(); if(!std::cout)throw std::runtime_error("input summary output failed");
        DynamicController dynamic(vertices,capacity,name);
        auto result=DVRP_SimulationController{}.run(std::move(dynamic),args.options,&std::cout);
        if(result.status!=SimulationStatus::Completed)std::cerr << result.message << '\n';
        std::cout << "WALL seconds=" << std::chrono::duration<double>(std::chrono::steady_clock::now()-began).count() << '\n';
        std::cout.flush(); if(!std::cout)throw std::runtime_error("final output failed");
        return simulationExitCode(result);
    } catch(const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}
