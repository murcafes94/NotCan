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
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)
if(NOT TARGET common)
    add_subdirectory(${LLAMA_SRC}/common build-common)
endif()

set(UPSTREAM_AI_CHAT ${LLAMA_SRC}/examples/llama.android/lib/src/main/cpp/ai_chat.cpp)
file(READ ${UPSTREAM_AI_CHAT} AI_CHAT_SOURCE)
string(REPLACE "#include \"logging.h\"" "#include \"logging_compat.h\"" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE "constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;" "constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE "constexpr int   BATCH_SIZE              = 512;" "constexpr int   BATCH_SIZE              = 256;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE "constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;" "constexpr float DEFAULT_SAMPLER_TEMP    = 0.1f;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

# Detect LFM by the model description once loaded.
string(REPLACE
    "static common_sampler                   * g_sampler;"
    "static common_sampler                   * g_sampler;\nstatic bool                              g_notcan_lfm = false;"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE
    "    g_model = model;\n    return 0;"
    "    g_model = model;\n    char notcan_model_desc[256] = {};\n    llama_model_desc(g_model, notcan_model_desc, sizeof(notcan_model_desc));\n    std::string notcan_desc(notcan_model_desc);\n    g_notcan_lfm = notcan_desc.find(\"LFM2\") != std::string::npos || notcan_desc.find(\"lfm2\") != std::string::npos;\n    LOGi(\"NotCan model mode: %s · %s\", g_notcan_lfm ? \"LFM ChatML\" : \"generic template\", notcan_model_desc);\n    return 0;"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

# Do not apply the incremental generic formatter to LFM. NotCan adds the exact
# documented ChatML framing below and asks the tokenizer to parse special tokens.
string(REPLACE
    "const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());"
    "const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get()) && !g_notcan_lfm;"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

string(REPLACE
    "    std::string formatted_system_prompt(system_prompt);\n    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);"
    "    std::string formatted_system_prompt(system_prompt);\n    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);\n    if (g_notcan_lfm) {\n        formatted_system_prompt = std::string(\"<|startoftext|><|im_start|>system\\n\") + formatted_system_prompt + \"<|im_end|>\\n\";\n    }"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE
    "const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,\n                                               has_chat_template, has_chat_template);"
    "const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,\n                                               has_chat_template, has_chat_template || g_notcan_lfm);"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

string(REPLACE
    "    std::string formatted_user_prompt(user_prompt);\n    env->ReleaseStringUTFChars(juser_prompt, user_prompt);"
    "    std::string formatted_user_prompt(user_prompt);\n    env->ReleaseStringUTFChars(juser_prompt, user_prompt);\n    if (g_notcan_lfm) {\n        formatted_user_prompt = std::string(\"<|im_start|>user\\n\") + formatted_user_prompt + \"<|im_end|>\\n<|im_start|>assistant\\n\";\n    }"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE
    "auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template);"
    "auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template || g_notcan_lfm);"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

# LFM control tokens are protocol, never user-visible content. The model should
# normally terminate with EOG, but stopping on a leaked control token is safer.
string(REPLACE
    "    auto new_token_chars = common_token_to_piece(g_context, new_token_id);\n    cached_token_chars += new_token_chars;"
    "    auto new_token_chars = common_token_to_piece(g_context, new_token_id);\n    if (g_notcan_lfm && new_token_chars.rfind(\"<|\", 0) == 0) {\n        LOGw(\"NotCan stopped leaked LFM control token: %s\", new_token_chars.c_str());\n        return nullptr;\n    }\n    cached_token_chars += new_token_chars;"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE
    "        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());"
    "        if (!g_notcan_lfm) chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

set(PATCHED_AI_CHAT ${CMAKE_CURRENT_BINARY_DIR}/ai_chat_compat.cpp)
file(WRITE ${PATCHED_AI_CHAT} "${AI_CHAT_SOURCE}")

add_library(${CMAKE_PROJECT_NAME} SHARED ${PATCHED_AI_CHAT})

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>)

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
        common
        android
        log)
