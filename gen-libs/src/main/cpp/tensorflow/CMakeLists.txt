cmake_minimum_required(VERSION 3.4.1)

set(download_DIR ${CMAKE_CURRENT_SOURCE_DIR}/download)
set(distribution_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../distribution)

find_package(Git REQUIRED)

add_custom_target(TF_headers)
add_custom_command(TARGET TF_headers PRE_BUILD
  COMMAND mkdir -p ${distribution_DIR}/tensorflow/tensorflow
  COMMAND cd ./tensorflow/tensorflow
  COMMAND ${GIT_EXECUTABLE} init
  COMMAND ${GIT_EXECUTABLE} pull --tags https://github.com/tensorflow/tensorflow
  COMMAND ${GIT_EXECUTABLE} checkout baa85cbf5e51a21f58bc28ef9eedc122e6118eb8
  WORKING_DIRECTORY ${distribution_DIR}
  COMMENT "Downloading source..."
  VERBATIM
)


set(prebuilt_url
 "https://ci.tensorflow.org/view/Nightly/job/nightly-android/75/artifact/*zip*/archive.zip")

if (NOT EXISTS ${download_DIR}/archive.zip)
    file(DOWNLOAD ${prebuilt_url} ${download_DIR}/archive.zip)
endif()

add_custom_target(TF_prebuilt)
add_custom_command(TARGET TF_prebuilt PRE_BUILD
  COMMAND unzip -u archive.zip
  COMMAND cp -ru ${download_DIR}/archive/out ${distribution_DIR}/tensorflow/
  DEPENDS ${download_DIR}/archive.zip
  WORKING_DIRECTORY ${download_DIR}
  COMMENT "Downloading prebuilt libraries..."
  VERBATIM
)
add_dependencies(TF_prebuilt TF_headers)

# dummy hack to make tarrget for graddle
add_library(TensorFlow SHARED ../dummy.c)
add_dependencies(TensorFlow TF_prebuilt)

