cmake_minimum_required(VERSION 3.4.1)
include(ExternalProject)

set(source_DIR "${CMAKE_CURRENT_SOURCE_DIR}")
set(distribution_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../distribution)
set(byproduct ${distribution_DIR}/opencv/sdk/native/libs/${ANDROID_ABI}/libopencv_highgui.a)
ExternalProject_Add(OpenCV_external
            PREFIX ${source_DIR}
            GIT_REPOSITORY https://github.com/opencv/opencv.git
            GIT_TAG 3.2.0
            GIT_SHALLOW 1
            CMAKE_ARGS
                -DCMAKE_TOOLCHAIN_FILE=./platforms/android/android.toolchain.cmake
                -DANDROID_NDK=${ANDROID_NDK}
                -DANDROID_ABI=${ANDROID_ABI}
               # -DCMAKE_INSTALL_PREFIX=${distribution_DIR}/opencv
                -DBUILD_opencv_java=OFF
                -DBUILD_opencv_python=OFF
            BUILD_DIR ${distribution_DIR}/opencv
            BUILD_BYPRODUCTS ${byproduct}
            )

#add_library(OpenCV_imported STATIC IMPORTED)
#set_target_properties(OpenCV_imported PROPERTIES IMPORTED_LOCATION ${byproduct})
#add_dependencies(OpenCV_imported OpenCV_external)

# dummy hack to make tarrget for graddle
add_library(OpenCV SHARED ../dummy.c)
add_dependencies(OpenCV OpenCV_external)

#target_link_libraries(OpenCV OpenCV_imported)
