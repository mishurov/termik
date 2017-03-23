cmake_minimum_required(VERSION 3.4.1)

file(GLOB inference_SRC
     src/main/cpp/*.cc
     src/main/cpp/object_tracking/*.cc)

add_library(native-lib
            SHARED
            src/main/cpp/native-lib.cpp)

include_directories(../distribution/tensorflow/tensorflow)

#target_link_libraries(native-lib)
