cmake_minimum_required(VERSION 3.15)
project(dvrp VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif ()

include(CTest)

option(DVRP_BUILD_REFERENCE_SOLVER "Build the small complete-route CPLEX LP/MIP test tool" OFF)
option(DVRP_BUILD_RMP_TESTS "Build the multi-start RMP tests (CPLEX)" OFF)

set(DVRP_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/read_data/include")
set(ROUTEOPT_CONFIG_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../common/config")
set(DVRP_READER_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/src/read_data/src/read_data.cpp")
set(DVRP_DYNAMIC_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/dynamic/include")
set(DVRP_DYNAMIC_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/src/dynamic/src/dynamic_controller.cpp")

add_executable(dvrp
    src/main.cpp
    ${DVRP_READER_SOURCE}
    ${DVRP_DYNAMIC_SOURCE}
)

target_include_directories(dvrp PRIVATE
    ${DVRP_INCLUDE_DIR}
    ${DVRP_DYNAMIC_INCLUDE_DIR}
    ${ROUTEOPT_CONFIG_INCLUDE_DIR}
)

add_executable(dvrp_read_data_test
    test/read_data_test.cpp
    ${DVRP_READER_SOURCE}
)

target_include_directories(dvrp_read_data_test PRIVATE
    ${DVRP_INCLUDE_DIR}
    ${ROUTEOPT_CONFIG_INCLUDE_DIR}
)

if (BUILD_TESTING)
    add_executable(dvrp_dynamic_test test/dynamic_test.cpp ${DVRP_DYNAMIC_SOURCE} ${DVRP_READER_SOURCE})
    target_include_directories(dvrp_dynamic_test PRIVATE
        ${DVRP_DYNAMIC_INCLUDE_DIR} ${DVRP_INCLUDE_DIR} ${ROUTEOPT_CONFIG_INCLUDE_DIR})
    foreach (case events commitment return distance validation time stop external loaded
            external_deadline empty arithmetic stop_later partition input)
        add_test(NAME dvrp_dynamic_${case} COMMAND dvrp_dynamic_test ${case})
    endforeach ()
    add_test(NAME dvrp_dynamic_reader COMMAND dvrp_dynamic_test reader
        "${CMAKE_CURRENT_SOURCE_DIR}/test/fixtures/minimal_valid.txt")

    add_test(
        NAME dvrp_read_data_test
        COMMAND dvrp_read_data_test
            "${CMAKE_CURRENT_SOURCE_DIR}/test/fixtures/minimal_valid.txt"
            "${CMAKE_CURRENT_SOURCE_DIR}/instance"
    )

    add_test(
        NAME dvrp_cli_minimal
        COMMAND dvrp "${CMAKE_CURRENT_SOURCE_DIR}/test/fixtures/minimal_valid.txt"
    )
    set_tests_properties(dvrp_cli_minimal PROPERTIES
        PASS_REGULAR_EXPRESSION "customers=2"
    )

    foreach (invalid_fixture
            invalid_missing_disclosure.txt
            invalid_extra_field.txt
            invalid_time_window.txt
            invalid_customer_id.txt
            invalid_disclosure_time.txt
            invalid_header.txt)
        add_test(
            NAME dvrp_invalid_${invalid_fixture}
            COMMAND dvrp "${CMAKE_CURRENT_SOURCE_DIR}/test/fixtures/${invalid_fixture}"
        )
        set_tests_properties(dvrp_invalid_${invalid_fixture} PROPERTIES
            WILL_FAIL TRUE
        )
    endforeach ()

    add_test(NAME dvrp_cli_missing_argument COMMAND dvrp)
    set_tests_properties(dvrp_cli_missing_argument PROPERTIES WILL_FAIL TRUE)
endif ()

if (DVRP_BUILD_REFERENCE_SOLVER OR DVRP_BUILD_RMP_TESTS)
    if (NOT BUILD_TESTING)
        message(FATAL_ERROR "DVRP solver tests require BUILD_TESTING=ON")
    endif ()
    set(ROUTEOPT_SOLVER "CPLEX" CACHE STRING "DVRP solver backend (currently CPLEX only)")
    string(TOUPPER "${ROUTEOPT_SOLVER}" ROUTEOPT_SOLVER)
    if (NOT ROUTEOPT_SOLVER STREQUAL "CPLEX")
        message(FATAL_ERROR "DVRP solver tests currently support ROUTEOPT_SOLVER=CPLEX only")
    endif ()
    list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../../external/cmake_modules")
    find_package(CPLEX REQUIRED)
    set(DVRP_REFERENCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/test/stage_reference")
    set(DVRP_SOLVER_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../common/solver_interface")
    if (UNIX)
        find_package(Threads REQUIRED)
    endif ()
    # The Windows import library needs its matching runtime DLL beside this test exe.
    if (WIN32)
        get_filename_component(_cplex_root "${CPLEX_INCLUDE_DIR}/.." ABSOLUTE)
        get_filename_component(_cplex_name "${CPLEX_LIBRARY}" NAME_WE)
        find_file(DVRP_CPLEX_RUNTIME NAMES "${_cplex_name}.dll"
            PATHS "${_cplex_root}/bin/x64_win64" "${_cplex_root}/bin/x64_windows_msvc14"
            NO_DEFAULT_PATH)
        if (NOT DVRP_CPLEX_RUNTIME)
            message(FATAL_ERROR "Matching CPLEX DLL not found; set DVRP_CPLEX_RUNTIME explicitly")
        endif ()
    endif ()
    function(configure_dvrp_solver_test target)
        target_include_directories(${target} PRIVATE
            ${DVRP_REFERENCE_DIR} ${DVRP_DYNAMIC_INCLUDE_DIR} ${ROUTEOPT_CONFIG_INCLUDE_DIR}
            ${DVRP_SOLVER_DIR}/include ${CPLEX_INCLUDE_DIRS})
        target_compile_definitions(${target} PRIVATE SOLVER_TYPE=1)
        target_link_libraries(${target} PRIVATE ${CPLEX_LIBRARIES})
        if (MSVC)
            target_compile_options(${target} PRIVATE /utf-8)
        endif ()
        if (UNIX)
            target_link_libraries(${target} PRIVATE Threads::Threads ${CMAKE_DL_LIBS} m)
        endif ()
        if (WIN32)
            add_custom_command(TARGET ${target} POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy_if_different "${DVRP_CPLEX_RUNTIME}" "$<TARGET_FILE_DIR:${target}>"
                VERBATIM)
        endif ()
    endfunction()
    if (DVRP_BUILD_REFERENCE_SOLVER)
        add_executable(dvrp_stage_reference_test
            ${DVRP_REFERENCE_DIR}/stage_reference_test.cpp
            ${DVRP_REFERENCE_DIR}/stage_route_enumerator.cpp
            ${DVRP_REFERENCE_DIR}/stage_reference_solver.cpp
            ${DVRP_SOLVER_DIR}/src/solver_cplex.cpp ${DVRP_DYNAMIC_SOURCE})
        configure_dvrp_solver_test(dvrp_stage_reference_test)
        foreach (case enumeration resources nonmetric validation limits duals lp mip fractional
                empty infeasible status dynamic lifecycle time_limit)
            add_test(NAME dvrp_reference_${case} COMMAND dvrp_stage_reference_test ${case})
            set_tests_properties(dvrp_reference_${case} PROPERTIES TIMEOUT 120)
        endforeach ()
    endif ()
    if (DVRP_BUILD_RMP_TESTS)
        add_executable(dvrp_rmp_test test/rmp/rmp_test.cpp src/node/src/rmp_controller.cpp
            ${DVRP_REFERENCE_DIR}/stage_route_enumerator.cpp
            ${DVRP_REFERENCE_DIR}/stage_reference_solver.cpp
            ${DVRP_SOLVER_DIR}/src/solver_cplex.cpp ${DVRP_DYNAMIC_SOURCE})
        target_include_directories(dvrp_rmp_test PRIVATE src/node/include)
        configure_dvrp_solver_test(dvrp_rmp_test)
        foreach (case matrix phases initial resources validation identity duplicate empty infeasible
                full_lp fractional pricing size lifecycle status time_limit)
            add_test(NAME dvrp_rmp_${case} COMMAND dvrp_rmp_test ${case})
            set_tests_properties(dvrp_rmp_${case} PROPERTIES TIMEOUT 120)
        endforeach ()
    endif ()
endif ()
