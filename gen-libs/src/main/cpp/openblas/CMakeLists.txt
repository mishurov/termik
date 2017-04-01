cmake_minimum_required(VERSION 3.4.1)

set(source_DIR ${CMAKE_CURRENT_SOURCE_DIR})
set(distribution_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../distribution)
set(download_DIR ${source_DIR}/download)


set(target "")
set(ANDROID_GCC "")
if(${ANDROID_ABI} STREQUAL armeabi)
    set(target "ARMV5")
    set(gcc_pref "arm-linux-androideabi")
elseif(${ANDROID_ABI} STREQUAL armv7a)
    set(target "ARMV7")
    set(gcc_pref "arm-linux-androideabi")
elseif(${ANDROID_ABI} STREQUAL arm64-v8a)
    set(target "arch-arm64")
    set(gcc_pref "aarch64-linux-android")
elseif(${ANDROID_ABI} STREQUAL mips)
    set(target "LOONGSON3A")
    set(gcc_pref "mipsel-linux-android")
elseif(${ANDROID_ABI} STREQUAL mips64)
    set(target "LOONGSON3A BINARY=64")
    set(gcc_pref "mips64el-linux-android")
elseif(${ANDROID_ABI} STREQUAL x86)
    set(target "ATOM")
    set(gcc_pref "i686-linux-android")
elseif(${ANDROID_ABI} STREQUAL x86_64)
    set(target "ATOM BINARY=64")
    set(gcc_pref "x86_64-linux-android")
else()
    message(FATAL_ERROR "Invalid Android ABI: ${ANDROID_ABI}.")
endif()

set(tc_dir "")
if(${ANDROID_ABI} STREQUAL x86)
    set(tc_dir "x86")
elseif(${ANDROID_ABI} STREQUAL x86_64)
    set(tc_dir "x86_64")
else()
    set(tc_dir ${gcc_pref})
endif()


set(ANDROID_SYSTEM_LIBRARY_PATH
    "${ANDROID_NDK}/platforms/${ANDROID_PLATFORM}/arch-${ANDROID_SYSROOT_ABI}")
set(EXT_TOOLCHAIN_ROOT
    ${source_DIR}/toolchains/${tc_dir}-4.9)

set(ENV{PATH} ${EXT_TOOLCHAIN_ROOT}/bin:$ENV{PATH})

set(byproduct ${distribution_DIR}/openblas/${ANDROID_ABI}/lib/libopenblas.a)
include(ExternalProject)
ExternalProject_Add(OpenBLAS_external
    DOWNLOAD_DIR ${source_DIR}/src
    SOURCE_DIR ${source_DIR}/src
    PREFIX ${source_DIR}/intermediates/${ANDROID_ABI}
    INSTALL_DIR ${distribution_DIR}/openblas/${ANDROID_ABI}
    GIT_REPOSITORY https://github.com/xianyi/OpenBLAS.git
    GIT_TAG v0.2.19
    GIT_SHALLOW 1
    CONFIGURE_COMMAND ""
    BUILD_IN_SOURCE 1
    BUILD_COMMAND PATH=${ANDROID_TOOLCHAIN_ROOT}/bin:$ENV{PATH} make CFLAGS+=-DLAPACK_COMPLEX_CPP CFLAGS+=--sysroot=${ANDROID_SYSTEM_LIBRARY_PATH} TARGET=${target} HOSTCC=gcc CC=${gcc_pref}-gcc AR=${gcc_pref}-ar NOFORTRAN=1 libs
    INSTALL_COMMAND make PREFIX=${distribution_DIR}/openblas/${ANDROID_ABI} install
)

# dummy hack to make target for graddle
add_library(OpenBLAS SHARED ../dummy.c)
add_dependencies(OpenBLAS OpenBLAS_external)
