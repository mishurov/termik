cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_VERBOSE_MAKEFILE on)


if (ANDROID_ABI MATCHES "^armeabi-v7a$")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfloat-abi=softfp -mfpu=neon")
elseif(ANDROID_ABI MATCHES "^arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -ftree-vectorize")
endif()


set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} \
                    -std=c++11 -fno-exceptions \
                    -fno-rtti -O2 -Wno-narrowing \
                    -fPIE")

get_filename_component(
    IMAGE_UTILS_SRC_DIR
    ${CMAKE_SOURCE_DIR}/src/main/cpp/image_utils
    ABSOLUTE
)

add_library(image_utils SHARED
    ${IMAGE_UTILS_SRC_DIR}/imageutils_jni.cc
    ${IMAGE_UTILS_SRC_DIR}/rgb2yuv.cc
    ${IMAGE_UTILS_SRC_DIR}/yuv2rgb.cc
)

target_include_directories(image_utils PRIVATE
                           ${IMAGE_UTILS_SRC_DIR})

target_link_libraries(
    image_utils
    android
    log
    jnigraphics
    m
    atomic
    z
)

add_library(
    processing
    SHARED
    src/main/cpp/processing.cc
)

set(OPENCV_SRC ${CMAKE_SOURCE_DIR}/../downloads/OpenCV-android-sdk/sdk)

target_include_directories(
    processing PRIVATE
    ${OPENCV_SRC}/native/jni/include
)

add_library(opencv SHARED IMPORTED)

set_target_properties(
    opencv
    PROPERTIES
    IMPORTED_LOCATION
    ${OPENCV_SRC}/native/libs/${ANDROID_ABI}/libopencv_java3.so
)

target_link_libraries(
    processing
    opencv
    log
)

