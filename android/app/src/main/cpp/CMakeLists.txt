cmake_minimum_required(VERSION 3.22.1)
project("jnibridge")

add_library(jnibridge SHARED jni_bridge.cpp)

find_library(log-lib log)
target_link_libraries(jnibridge ${log-lib})
