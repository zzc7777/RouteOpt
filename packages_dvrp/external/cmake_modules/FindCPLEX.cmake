# FindCPLEX.cmake

set(CPLEX_ROOT "" CACHE PATH "CPLEX installation root (the directory containing include and lib)")

if (NOT CPLEX_ROOT)
    foreach (_CPLEX_ENV CPLEX_STUDIO_DIR221 CPLEX_STUDIO_DIR201 CPLEX_STUDIO_DIR129)
        if (DEFINED ENV{${_CPLEX_ENV}} AND EXISTS "$ENV{${_CPLEX_ENV}}/cplex")
            set(CPLEX_ROOT "$ENV{${_CPLEX_ENV}}/cplex")
            break()
        endif ()
    endforeach ()
elseif (EXISTS "${CPLEX_ROOT}/cplex/include/ilcplex/cplex.h")
    set(CPLEX_ROOT "${CPLEX_ROOT}/cplex")
endif ()

find_path(CPLEX_INCLUDE_DIR
        NAMES ilcplex/cplex.h
        PATHS "${CPLEX_ROOT}/include"
        NO_DEFAULT_PATH)

set(_CPLEX_LIBRARY_DIRS
        "${CPLEX_ROOT}/lib/x64_windows_msvc14/stat_mda"
        "${CPLEX_ROOT}/lib/x86-64_windows/static_mt"
        "${CPLEX_ROOT}/lib/x86-64_linux/static_pic"
        "${CPLEX_ROOT}/lib/x86-64_linux/static_mt"
        "${CPLEX_ROOT}/lib/x86-64_osx/static_pic")

set(_CPLEX_LIBRARIES)
foreach (_CPLEX_LIBRARY_DIR ${_CPLEX_LIBRARY_DIRS})
    file(GLOB _CPLEX_CANDIDATES
            "${_CPLEX_LIBRARY_DIR}/cplex*.lib"
            "${_CPLEX_LIBRARY_DIR}/libcplex*.a")
    list(APPEND _CPLEX_LIBRARIES ${_CPLEX_CANDIDATES})
endforeach ()
list(SORT _CPLEX_LIBRARIES)
list(LENGTH _CPLEX_LIBRARIES _CPLEX_LIBRARY_COUNT)
if (_CPLEX_LIBRARY_COUNT GREATER 0)
    math(EXPR _CPLEX_LIBRARY_LAST "${_CPLEX_LIBRARY_COUNT} - 1")
    list(GET _CPLEX_LIBRARIES ${_CPLEX_LIBRARY_LAST} CPLEX_LIBRARY)
endif ()

set(CPLEX_INCLUDE_DIRS "${CPLEX_INCLUDE_DIR}")
set(CPLEX_LIBRARIES "${CPLEX_LIBRARY}")

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(CPLEX DEFAULT_MSG CPLEX_LIBRARY CPLEX_INCLUDE_DIR)

mark_as_advanced(CPLEX_INCLUDE_DIR CPLEX_LIBRARY)
