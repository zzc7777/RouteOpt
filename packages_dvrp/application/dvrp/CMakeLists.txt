cmake_minimum_required(VERSION 3.15)
project(dvrp VERSION 1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif ()

include(CTest)

set(DVRP_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/read_data/include")
set(ROUTEOPT_CONFIG_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../common/config")
set(DVRP_READER_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/src/read_data/src/read_data.cpp")

add_executable(dvrp
    src/main.cpp
    ${DVRP_READER_SOURCE}
)

target_include_directories(dvrp PRIVATE
    ${DVRP_INCLUDE_DIR}
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
