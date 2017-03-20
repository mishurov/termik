cmake_minimum_required(VERSION 3.4.1)

set(source_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src")
set(distribution_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../distribution)

#find_package(Git REQUIRED)

#add_custom_target(core)
#add_custom_command(TARGET core PRE_BUILD
#  BYPRODUCTS ${tensorflow_core}
#  COMMAND ${GIT_EXECUTABLE} init
#  COMMAND ${GIT_EXECUTABLE} pull --tags https://github.com/tensorflow/tensorflow
#  COMMAND ${GIT_EXECUTABLE} checkout v1.0.1
#  COMMAND ./configure
#  COMMAND bazel build //tensorflow/examples/android:tensorflow_native_libs --crosstool_top=//external:android/#crosstool --cpu=${ANDROID_ABI} --host_crosstool_top=@bazel_tools//tools/cpp:toolchain
#  WORKING_DIRECTORY "${source_DIR}"
#  COMMENT "Downloading and building core..."
#  VERBATIM
#)

set(prebuilt_url
 "https://ci.tensorflow.org/view/Nightly/job/nightly-android/75/artifact/*zip*/archive.zip")

if (NOT EXISTS ${source_DIR}/archive.zip)
    file(DOWNLOAD ${prebuilt_url} ${source_DIR}/archive.zip)
endif()

add_custom_target(prebuilt)
add_custom_command(TARGET prebuilt PRE_BUILD
  COMMAND unzip -u archive.zip
  COMMAND rm -rf ${distribution_DIR}/tensorflow
  COMMAND mv ${source_DIR}/archive/out ${distribution_DIR}/tensorflow
  DEPENDS ${source_DIR}/archive.zip
  WORKING_DIRECTORY ${source_DIR}
  COMMENT "Downloading prebuilt libraries..."
  VERBATIM
)

# dummy hack to make tarrget for graddle
add_library(TensorFlow SHARED ../dummy.c)
add_dependencies(TensorFlow prebuilt)
