cmake_minimum_required(VERSION 3.4.1)


# OpenCV
set(OpenCV ${CMAKE_CURRENT_SOURCE_DIR}/../distribution/opencv)

add_library(opencv SHARED IMPORTED)
set_target_properties(opencv PROPERTIES IMPORTED_LOCATION
    ${OpenCV}/sdk/native/libs/${ANDROID_ABI}/libopencv_java3.so)

include_directories(${OpenCV}/sdk/native/jni/include)

# Application
Set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall")
add_library(processing SHARED src/main/jni/processing.cpp)
target_link_libraries(
    processing
    opencv
    android
    atomic
    log
)

