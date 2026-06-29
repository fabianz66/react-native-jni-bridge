cmake_minimum_required(VERSION 3.22.1)

# Must be "appmodules" — SoLoader looks for libappmodules.so at runtime
project("appmodules")

# Builds libappmodules.so with the default RN OnLoad.cpp + autolinking.
# REACT_ANDROID_DIR, PROJECT_BUILD_DIR, PROJECT_ROOT_DIR are injected by the RN Gradle Plugin.
include("${REACT_ANDROID_DIR}/cmake-utils/ReactNative-application.cmake")

# Separate library for our JNI bridge — does not affect libappmodules.so
add_library(jnibridge SHARED ${CMAKE_CURRENT_SOURCE_DIR}/../cpp/jni_bridge.cpp)
find_library(log-lib log)
target_link_libraries(jnibridge ${log-lib})
