cmake_minimum_required(VERSION 3.4.1)

set(source_DIR ${CMAKE_CURRENT_SOURCE_DIR})
set(distribution_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../distribution)
set(download_DIR ${source_DIR}/download)

# contrib
set(contrib_modules_DIR ${source_DIR}/opencv_contrib/modules)
add_custom_target(OpenCV_contrib)
find_package(Git REQUIRED)
add_custom_command(TARGET OpenCV_contrib
    PRE_BUILD
    COMMAND mkdir -p ${source_DIR}/opencv_contrib
    COMMAND cd ${source_DIR}/opencv_contrib
    COMMAND ${GIT_EXECUTABLE} init
    COMMAND ${GIT_EXECUTABLE} pull --tags https://github.com/opencv/opencv_contrib
    COMMAND ${GIT_EXECUTABLE} checkout 3.2.0
    WORKING_DIRECTORY ${source_DIR}
    COMMENT "Downloading contrib..."
    VERBATIM
)

# opencv
if(${ANDROID_ABI} STREQUAL "x86"
    OR ${ANDROID_ABI} STREQUAL "x86_64")
    set(WITH_IPP ON)
else()
    set(WITH_IPP OFF)
endif()

# old ndk don't support
if(${ANDROID_ABI} STREQUAL "armeabi")
    set(ANDROID_NATIVE_API_LEVEL 19)
else()
    set(ANDROID_NATIVE_API_LEVEL 23)
endif()

set(byproduct ${distribution_DIR}/opencv/sdk/native/libs/${ANDROID_ABI}/libopencv_highgui.a)
include(ExternalProject)
find_package(ZLIB REQUIRED)
ExternalProject_Add(OpenCV_external
    DOWNLOAD_DIR ${source_DIR}/src
    SOURCE_DIR ${source_DIR}/src
    PREFIX ${source_DIR}/intermediates/${ANDROID_ABI}
    GIT_REPOSITORY https://github.com/opencv/opencv.git
    GIT_TAG 3.2.0
    GIT_SHALLOW 1
    CMAKE_ARGS
        #-DCMAKE_PROJECT_OpenCV_INCLUDE=${CMAKE_CURRENT_SOURCE_DIR}/sdk.cmake
        -DCMAKE_INSTALL_PREFIX=${distribution_DIR}/opencv
        -DOPENCV_EXTRA_MODULES_PATH=${contrib_modules_DIR}
        -DCMAKE_TOOLCHAIN_FILE=./platforms/android/android.toolchain.cmake
        -DANDROID_NDK=${ANDROID_NDK}
        -DANDROID_NATIVE_API_LEVEL=${ANDROID_NATIVE_API_LEVEL}
        -DANDROID_ABI=${ANDROID_ABI}
        -DBUILD_ANDROID_SERVICE=OFF
        -DBUILD_ANDROID_EXAMPLES=OFF
        -DWITH_IPP=${WITH_IPP}
        -DWITH_TBB=ON
        -DWITH_CUDA=OFF
        -DWITH_OPENCL=OFF
        -DBUILD_EXAMPLES=OFF
        -DBUILD_TESTS=OFF
        -DBUILD_PERF_TESTS=OFF
        -DBUILD_DOCS=OFF
        -DANDROID_STL=gnustl_static
        -DBUILD_LIBPROTOBUF_FROM_SOURCES=ON
        -Dopencv_dnn_BUILD_TORCH_IMPORTER=OFF
    BUILD_BYPRODUCTS ${byproduct}
)

add_dependencies(OpenCV_external OpenCV_contrib)

# dummy hack to make tarrget for graddle
add_library(OpenCV SHARED ../dummy.c)
add_dependencies(OpenCV OpenCV_external)
