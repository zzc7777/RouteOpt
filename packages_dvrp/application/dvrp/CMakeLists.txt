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
option(DVRP_BUILD_PRICING_TESTS "Build plain/bucket pricing tests (solver optional)" OFF)
option(DVRP_BUILD_ROOT_CG_TESTS "Build production root column-generation tests (CPLEX)" OFF)

set(DVRP_PRICING_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/pricing")
set(DVRP_PRICING_SOURCES
    ${DVRP_PRICING_DIR}/src/pricing_controller.cpp
    ${DVRP_PRICING_DIR}/src/labeling_plain.cpp
    ${DVRP_PRICING_DIR}/src/bucket_graph.cpp
    ${DVRP_PRICING_DIR}/src/labeling_bucket.cpp)

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

if (DVRP_BUILD_PRICING_TESTS)
    if (NOT BUILD_TESTING)
        message(FATAL_ERROR "DVRP pricing tests require BUILD_TESTING=ON")
    endif ()
    add_executable(dvrp_pricing_test test/pricing/pricing_test.cpp ${DVRP_PRICING_SOURCES}
        test/stage_reference/stage_route_enumerator.cpp ${DVRP_DYNAMIC_SOURCE})
    target_include_directories(dvrp_pricing_test PRIVATE
        ${DVRP_PRICING_DIR}/include src/node/include test/stage_reference
        ${DVRP_DYNAMIC_INCLUDE_DIR} ${ROUTEOPT_CONFIG_INCLUDE_DIR})
    if (MSVC)
        target_compile_options(dvrp_pricing_test PRIVATE /utf-8)
    endif ()
    foreach (case hand resources nonmetric empty zero random validation limits lifecycle size graph dominance boundary dense)
        add_test(NAME dvrp_pricing_${case} COMMAND dvrp_pricing_test ${case})
        set_tests_properties(dvrp_pricing_${case} PROPERTIES TIMEOUT 120)
    endforeach ()
    add_executable(dvrp_pricing_property_test test/pricing/pricing_property_test.cpp
        ${DVRP_PRICING_SOURCES} ${DVRP_DYNAMIC_SOURCE})
    target_include_directories(dvrp_pricing_property_test PRIVATE
        ${DVRP_PRICING_DIR}/include src/node/include
        ${DVRP_DYNAMIC_INCLUDE_DIR} ${ROUTEOPT_CONFIG_INCLUDE_DIR})
    if (MSVC)
        target_compile_options(dvrp_pricing_property_test PRIVATE /utf-8)
    endif ()
    foreach (case fuzz_17 fuzz_4217 fuzz_8391 fuzz_20261015 seven metamorphic numeric limits bitset)
        add_test(NAME dvrp_pricing_property_${case} COMMAND dvrp_pricing_property_test ${case})
        set_tests_properties(dvrp_pricing_property_${case} PROPERTIES TIMEOUT 300)
    endforeach ()
endif ()

if (DVRP_BUILD_REFERENCE_SOLVER OR DVRP_BUILD_RMP_TESTS OR DVRP_BUILD_ROOT_CG_TESTS)
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
    if (DVRP_BUILD_ROOT_CG_TESTS)
        add_executable(dvrp_root_cg_test test/root_cg/root_cg_test.cpp
            src/column_generation/src/root_cg_controller.cpp src/node/src/rmp_controller.cpp
            ${DVRP_PRICING_SOURCES} ${DVRP_DYNAMIC_SOURCE}
            ${DVRP_REFERENCE_DIR}/stage_route_enumerator.cpp
            ${DVRP_REFERENCE_DIR}/stage_reference_solver.cpp
            ${DVRP_SOLVER_DIR}/src/solver_cplex.cpp)
        target_include_directories(dvrp_root_cg_test PRIVATE
            src/column_generation/include src/node/include ${DVRP_PRICING_DIR}/include)
        configure_dvrp_solver_test(dvrp_root_cg_test)
        foreach (case hand phases fractional infeasible empty random bounds numeric context
                duplicate limits status validation lifecycle size)
            add_test(NAME dvrp_root_cg_${case} COMMAND dvrp_root_cg_test ${case})
            set_tests_properties(dvrp_root_cg_${case} PROPERTIES TIMEOUT 180)
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
                full_lp fractional pricing size lifecycle status time_limit per_call_limit)
            add_test(NAME dvrp_rmp_${case} COMMAND dvrp_rmp_test ${case})
            set_tests_properties(dvrp_rmp_${case} PROPERTIES TIMEOUT 120)
        endforeach ()
        if (DVRP_BUILD_PRICING_TESTS)
            add_executable(dvrp_pricing_rmp_test test/pricing/pricing_rmp_test.cpp
                ${DVRP_PRICING_SOURCES} src/node/src/rmp_controller.cpp
                ${DVRP_REFERENCE_DIR}/stage_route_enumerator.cpp
                ${DVRP_REFERENCE_DIR}/stage_reference_solver.cpp
                ${DVRP_SOLVER_DIR}/src/solver_cplex.cpp ${DVRP_DYNAMIC_SOURCE})
            target_include_directories(dvrp_pricing_rmp_test PRIVATE ${DVRP_PRICING_DIR}/include src/node/include)
            configure_dvrp_solver_test(dvrp_pricing_rmp_test)
            foreach (case basic initial infeasible fractional random empty interrupted stress)
                add_test(NAME dvrp_pricing_rmp_${case} COMMAND dvrp_pricing_rmp_test ${case})
                set_tests_properties(dvrp_pricing_rmp_${case} PROPERTIES TIMEOUT 120)
            endforeach ()
            # Real-instance pricing probe: deliberately no route enumerator linked.
            add_executable(dvrp_pricing_instance_test test/pricing/pricing_instance_test.cpp
                ${DVRP_PRICING_SOURCES} src/node/src/rmp_controller.cpp
                ${DVRP_SOLVER_DIR}/src/solver_cplex.cpp ${DVRP_DYNAMIC_SOURCE} ${DVRP_READER_SOURCE})
            target_include_directories(dvrp_pricing_instance_test PRIVATE
                ${DVRP_PRICING_DIR}/include src/node/include ${DVRP_INCLUDE_DIR})
            configure_dvrp_solver_test(dvrp_pricing_instance_test)
            add_test(NAME dvrp_pricing_instance_smoke COMMAND dvrp_pricing_instance_test
                "${CMAKE_CURRENT_SOURCE_DIR}/instance/c101_D_25_E_25.txt" 2)
            set_tests_properties(dvrp_pricing_instance_smoke PROPERTIES TIMEOUT 90)

            # Independent single-path MIP oracle, isolated under test/. Neither label
            # backend calls it. Enumeration is invoked ONLY by the <=5-customer tests.
            add_executable(dvrp_pricing_mip_test
                test/pricing/mip_reference/pricing_mip.cpp
                test/pricing/mip_reference/pricing_mip_test.cpp
                ${DVRP_PRICING_SOURCES} src/node/src/rmp_controller.cpp
                ${DVRP_REFERENCE_DIR}/stage_route_enumerator.cpp
                ${DVRP_REFERENCE_DIR}/stage_reference_solver.cpp
                ${DVRP_SOLVER_DIR}/src/solver_cplex.cpp ${DVRP_DYNAMIC_SOURCE} ${DVRP_READER_SOURCE})
            target_include_directories(dvrp_pricing_mip_test PRIVATE
                ${DVRP_PRICING_DIR}/include src/node/include ${DVRP_INCLUDE_DIR})
            configure_dvrp_solver_test(dvrp_pricing_mip_test)
            foreach (case hand random numeric certificate multistart cg dominance validation)
                add_test(NAME dvrp_pricing_mip_${case} COMMAND dvrp_pricing_mip_test ${case})
                set_tests_properties(dvrp_pricing_mip_${case} PROPERTIES TIMEOUT 300)
            endforeach ()
            add_test(NAME dvrp_pricing_mip_real COMMAND dvrp_pricing_mip_test real_cg
                "${CMAKE_CURRENT_SOURCE_DIR}/instance/r101_D_25_E_25.txt" 10 10)
            set_tests_properties(dvrp_pricing_mip_real PROPERTIES TIMEOUT 300)
        endif ()
    endif ()
endif ()
