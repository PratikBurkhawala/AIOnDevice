cmake_minimum_required(VERSION 3.22.1)
project(ai_on_device_llama_engine C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(llama.cpp)

add_library(ai_on_device_llama_jni SHARED
    ai_on_device_llama_jni.cpp
)

find_library(log-lib log)

target_include_directories(ai_on_device_llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

target_link_libraries(ai_on_device_llama_jni
    llama
    ${log-lib}
)
