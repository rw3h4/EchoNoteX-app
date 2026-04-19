cmake_minimum_required(VERSION 3.22.1)

project("echollm")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure llama.cpp (don't build examples or tests)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "llama.cpp: build examples" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "llama.cpp: build tests" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "llama.cpp: build server" FORCE)

# Enable SIMD for Android ARM64
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_definitions(GGML_USE_NEON)
endif()

# Add llama.cpp as a subdirectory
add_subdirectory(third_party/llama.cpp)

# Define our library
add_library(echollm SHARED
        echollm_jni.cpp
        echollm_inference.cpp
        echollm_inference.h
)

# Include directories
target_include_directories(echollm PRIVATE
        .
        third_party/llama.cpp/include
        third_party/llama.cpp/src
        third_party/llama.cpp/ggml/include
)

# Link against llama and log library
target_link_libraries(echollm
        llama
        log
)
