cmake_minimum_required(VERSION 3.10)
project(cvrp VERSION 3.0)


set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif ()

option(ENABLE_VALGRIND_MEM_CHECK "Enable Valgrind Memory Check" OFF)

set(ROUTEOPT_SOLVER "GUROBI" CACHE STRING "Solver backend: GUROBI or CPLEX")
set_property(CACHE ROUTEOPT_SOLVER PROPERTY STRINGS GUROBI CPLEX)
string(TOUPPER "${ROUTEOPT_SOLVER}" ROUTEOPT_SOLVER)

if (ROUTEOPT_SOLVER STREQUAL "GUROBI")
    set(ENABLE_GUROBI ON CACHE BOOL "Enable Gurobi" FORCE)
    set(ENABLE_CPLEX OFF CACHE BOOL "Enable CPLEX" FORCE)
    set(ROUTEOPT_SOLVER_TYPE 0)
elseif (ROUTEOPT_SOLVER STREQUAL "CPLEX")
    set(ENABLE_GUROBI OFF CACHE BOOL "Enable Gurobi" FORCE)
    set(ENABLE_CPLEX ON CACHE BOOL "Enable CPLEX" FORCE)
    set(ROUTEOPT_SOLVER_TYPE 1)
else ()
    message(FATAL_ERROR "Unsupported ROUTEOPT_SOLVER=${ROUTEOPT_SOLVER}; choose GUROBI or CPLEX")
endif ()

if (ENABLE_VALGRIND_MEM_CHECK)
    add_compile_definitions(VALGRIND_MEM_CHECK)
endif ()


set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/bin)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../external external_build)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../common common_build)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../branching branching_build)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../rounded_cap_cuts rcc_build)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../rank1_cuts rank1_cuts_build)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../deluxing deluxing_build)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/lib/cmake_modules")
find_package(HGS REQUIRED)
find_package(ZLIB REQUIRED)

# Find the required packages of the external
include_directories(
        ${BOOST_INCLUDE_DIRS} # no lib
        ${EIGEN_INCLUDE_DIRS} # no lib
        ${CVRPSEP_INCLUDE_DIRS}
        ${GUROBI_INCLUDE_DIRS}
        ${CPLEX_INCLUDE_DIRS}
        ${XGB_INCLUDE_DIRS}
        ${HGS_INCLUDE_DIRS}
)


# Include directories
include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/src/main/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/vrptw/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/add_column/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/cuts/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/pricing/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/l2b/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/read_data/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/heuristics/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/initial/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/node/include
        ${CMAKE_CURRENT_SOURCE_DIR}/src/two_stage/include
)


file(GLOB_RECURSE CVRP_SOURCES
        ${CMAKE_CURRENT_SOURCE_DIR}/src/main/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/vrptw/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/add_column/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/cuts/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/pricing/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/l2b/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/read_data/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/heuristics/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/initial/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/node/src/*.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/main.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/src/two_stage/src/*.cpp
)


add_executable(${PROJECT_NAME} ${CVRP_SOURCES}
)

if (WIN32)
    target_compile_definitions(${PROJECT_NAME} PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN)
endif ()

target_compile_definitions(${PROJECT_NAME} PRIVATE SOLVER_TYPE=${ROUTEOPT_SOLVER_TYPE})

set(LABEL_ASSIGN_OVERRIDE "" CACHE STRING "Override LABEL_ASSIGN for CVRP pricing")
if (LABEL_ASSIGN_OVERRIDE)
    target_compile_definitions(${PROJECT_NAME} PRIVATE LABEL_ASSIGN_OVERRIDE=${LABEL_ASSIGN_OVERRIDE})
endif ()


target_link_libraries(${PROJECT_NAME}
        PRIVATE
        COMMON_INCLUDES
        BRANCHING_INCLUDES
        ROUNDED_CAP_CUTS_INCLUDES
        RANK1_CUTS_INCLUDES
        DELUXING_INCLUDES
        ${GUROBI_LIBRARIES}
        ${CPLEX_LIBRARIES}
        ${CVRPSEP_LIBRARIES}
        ${XGB_LIBRARIES}
        ${HGS_LIBRARIES}
        ZLIB::ZLIB
)

if (WIN32)
    get_filename_component(XGB_LIBRARY_DIR "${XGB_LIBRARIES}" DIRECTORY)
    set(XGB_RUNTIME_LIBRARY "${XGB_LIBRARY_DIR}/xgboost.dll")
    if (EXISTS "${XGB_RUNTIME_LIBRARY}")
        add_custom_command(TARGET ${PROJECT_NAME} POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy_if_different
                "${XGB_RUNTIME_LIBRARY}" "$<TARGET_FILE_DIR:${PROJECT_NAME}>")
    endif ()
endif ()
