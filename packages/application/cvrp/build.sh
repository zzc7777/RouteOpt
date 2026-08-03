#!/bin/bash

rm -rf build bin

mkdir build

cd build

cmake -DCMAKE_BUILD_TYPE=Release ..

if command -v nproc >/dev/null 2>&1; then
    JOBS=$(nproc)
elif command -v sysctl >/dev/null 2>&1; then
    JOBS=$(sysctl -n hw.ncpu)
else
    JOBS=4
fi

make -j"${JOBS}"

cd ..

./bin/cvrp instance/vrptw/solomon-100/c101.txt
