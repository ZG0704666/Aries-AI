cmake_minimum_required(VERSION 3.22.1)

project(aries_sherpa_native)

# 可选的本机依赖源码缓存。将依赖预先解压到内部 APFS 卷后，FetchContent
# 不再联网或在外置卷解压，规避 GitHub TLS 失败和 macFUSE AppleDouble ._* 文件。
set(ARIES_CMAKE_DEPS_CACHE "$ENV{HOME}/Library/Caches/AriesCMakeDeps")
function(aries_use_cached_dependency fetch_content_name directory_name)
    set(source_dir "${ARIES_CMAKE_DEPS_CACHE}/${directory_name}")
    if(EXISTS "${source_dir}/CMakeLists.txt")
        set(FETCHCONTENT_SOURCE_DIR_${fetch_content_name} "${source_dir}" CACHE PATH "" FORCE)
    endif()
endfunction()
aries_use_cached_dependency(KALDI_NATIVE_FBANK "kaldi-native-fbank-1.22.3")
aries_use_cached_dependency(KALDIFST "kaldifst-1.7.17")
aries_use_cached_dependency(JSON "json-3.12.0")
aries_use_cached_dependency(KISSFFT "kissfft-febd4caeed32e33ad8b2e0bb5ea77542c40f18ec")
aries_use_cached_dependency(OPENFST "openfst-sherpa-onnx-2024-06-13")

function(force_release_flags_for_debug target_name)
    if(TARGET ${target_name})
        # 保持 App 变体为 Debug，但重型 native 库使用接近 Release 的优化参数编译。
        target_compile_definitions(${target_name} PRIVATE $<$<CONFIG:Debug>:NDEBUG>)
        target_compile_options(${target_name} PRIVATE $<$<CONFIG:Debug>:-O3 -g0>)
    endif()
endfunction()

if(ANDROID)
    option(ARIES_ENABLE_SHERPA_NCNN "当 submodule 可用时构建 sherpa-ncnn native 模块。" ON)

    add_link_options("-Wl,-z,max-page-size=16384")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

    set(SHERPA_NCNN_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/sherpa-ncnn")
    set(SHERPA_NCNN_CMAKELISTS "${SHERPA_NCNN_SOURCE_DIR}/CMakeLists.txt")
    set(NCNN_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/ncnn")
    set(NCNN_CMAKELISTS "${NCNN_SOURCE_DIR}/CMakeLists.txt")

    if(NOT ARIES_ENABLE_SHERPA_NCNN)
        message(STATUS "ARIES_ENABLE_SHERPA_NCNN=OFF，跳过 sherpa-ncnn native 构建。")
    elseif(NOT EXISTS "${SHERPA_NCNN_CMAKELISTS}" OR NOT EXISTS "${NCNN_CMAKELISTS}")
        message(FATAL_ERROR
                "缺少 native submodule 源码，无法构建离线语音功能。\n"
                "期望存在: ${SHERPA_NCNN_CMAKELISTS}\n"
                "期望存在: ${NCNN_CMAKELISTS}\n"
                "请执行: git submodule update --init --recursive")
    else()
        # 作为 Android Studio externalNativeBuild 的一部分构建 sherpa-ncnn。
        # 使用本地 submodule，避免下载到不同版本的 ncnn 构建结果。
        list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
        list(APPEND CMAKE_MODULE_PATH "${SHERPA_NCNN_SOURCE_DIR}/cmake")
        include_directories("${SHERPA_NCNN_SOURCE_DIR}")

        set(FETCHCONTENT_SOURCE_DIR_NCNN "${NCNN_SOURCE_DIR}" CACHE PATH "" FORCE)

        set(SHERPA_NCNN_ENABLE_PORTAUDIO OFF CACHE BOOL "" FORCE)
        set(SHERPA_NCNN_ENABLE_BINARY OFF CACHE BOOL "" FORCE)
        set(SHERPA_NCNN_ENABLE_TEST OFF CACHE BOOL "" FORCE)
        set(SHERPA_NCNN_ENABLE_C_API OFF CACHE BOOL "" FORCE)
        set(SHERPA_NCNN_ENABLE_JNI ON CACHE BOOL "" FORCE)
        set(SHERPA_NCNN_ENABLE_PYTHON OFF CACHE BOOL "" FORCE)
        set(SHERPA_NCNN_ENABLE_FFMPEG_EXAMPLES OFF CACHE BOOL "" FORCE)
        set(SHERPA_NCNN_ENABLE_GENERATE_INT8_SCALE_TABLE OFF CACHE BOOL "" FORCE)

        # 将 sherpa/ncnn 静态链接进 libsherpa-ncnn-jni，避免 OpenMP 异常中止问题。
        set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
        set(NCNN_OPENMP OFF CACHE BOOL "" FORCE)

        add_subdirectory(
                "${SHERPA_NCNN_SOURCE_DIR}"
                "${CMAKE_BINARY_DIR}/sherpa_ncnn_build"
        )

        force_release_flags_for_debug(ncnn)
        force_release_flags_for_debug(sherpa-ncnn-core)
        force_release_flags_for_debug(sherpa-ncnn-jni)
    endif()
endif()
