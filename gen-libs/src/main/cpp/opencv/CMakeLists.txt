cmake_minimum_required(VERSION 3.4.1)
include(ExternalProject)

set(source_DIR "${CMAKE_CURRENT_SOURCE_DIR}")
set(distribution_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../distribution)
set(byproduct ${distribution_DIR}/opencv/sdk/native/libs/${ANDROID_ABI}/libopencv_highgui.a)
find_package(ZLIB REQUIRED)
ExternalProject_Add(OpenCV_external
            DOWNLOAD_DIR ${source_DIR}/src
            SOURCE_DIR ${source_DIR}/src
            PREFIX ${source_DIR}/${ANDROID_ABI}
            GIT_REPOSITORY https://github.com/opencv/opencv.git
            GIT_TAG 3.2.0
            GIT_SHALLOW 1
            CMAKE_ARGS
                -DCMAKE_TOOLCHAIN_FILE=./platforms/android/android.toolchain.cmake
                -DANDROID_NDK=${ANDROID_NDK}
                -DANDROID_ABI=${ANDROID_ABI}
                -DBUILD_opencv_java=OFF
                -DBUILD_opencv_python=OFF
                -DBUILD_ANDROID_EXAMPLES=OFF
                -DCMAKE_INSTALL_PREFIX=${distribution_DIR}/opencv
            BUILD_BYPRODUCTS ${byproduct}
            )

# dummy hack to make tarrget for graddle
add_library(OpenCV SHARED ../dummy.c)
add_dependencies(OpenCV OpenCV_external)
