cmake_minimum_required(VERSION 3.31.5)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        # Portable Android configuration: one generic ARM64 CPU backend, without runtime-specific
        # extensions that can crash on otherwise supported devices.
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

# Keep the pinned official JNI bridge, but replace its API-30 logger and tune context/batch sizes
# for a tablet-class Android device. 4k context + 256 batch substantially lowers transient RAM
# while keeping enough room for focused class sources.
set(UPSTREAM_AI_CHAT ${LLAMA_SRC}/examples/llama.android/lib/src/main/cpp/ai_chat.cpp)
file(READ ${UPSTREAM_AI_CHAT} AI_CHAT_SOURCE)
string(REPLACE "#include \"logging.h\"" "#include \"logging_compat.h\"" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE "constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;" "constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE "constexpr int   BATCH_SIZE              = 512;" "constexpr int   BATCH_SIZE              = 256;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
set(PATCHED_AI_CHAT ${CMAKE_CURRENT_BINARY_DIR}/ai_chat_compat.cpp)
file(WRITE ${PATCHED_AI_CHAT} "${AI_CHAT_SOURCE}")

add_library(${CMAKE_PROJECT_NAME} SHARED ${PATCHED_AI_CHAT})

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}
        ${LLAMA_SRC}/examples/llama.android/lib/src/main/cpp
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src
        ${LLAMA_SRC}/vendor)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        android
        log)
