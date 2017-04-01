cmake_minimum_required(VERSION 3.4.1)

project(gen-libs)

add_subdirectory(src/main/cpp/opencv)
add_subdirectory(src/main/cpp/tensorflow)
add_subdirectory(src/main/cpp/openblas)

