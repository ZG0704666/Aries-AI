cmake_minimum_required(VERSION 3.22.1)

project(aries_sherpa_native)

function(force_release_flags_for_debug target_name)
    if(TARGET ${target_name})
        # 保持 App 变体为 Debug，但重型 native 库使用接近 Release 的优化参数编译。
        target_compile_definitions(${target_name} PRIVATE $<$<CONFIG:Debug>:NDEBUG>)
        target_compile_options(${target_name} PRIVATE $<$<CONFIG:Debug>:-O3 -g0>)
    endif()
endfunction()

if(ANDROID)
    option(ARIES_ENABLE_SHERPA_NCNN "当 submodule 可用时构建 sherpa-ncnn native 模块。" ON)

    set(SHERPA_NCNN_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/sherpa-ncnn")
    set(SHERPA_NCNN_CMAKELISTS "${SHERPA_NCNN_SOURCE_DIR}/CMakeLists.txt")
    set(NCNN_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/ncnn")
    set(NCNN_CMAKELISTS "${NCNN_SOURCE_DIR}/CMakeLists.txt")

    if(NOT ARIES_ENABLE_SHERPA_NCNN)
        message(STATUS "ARIES_ENABLE_SHERPA_NCNN=OFF，跳过 sherpa-ncnn native 构建。")
    elseif(NOT EXISTS "${SHERPA_NCNN_CMAKELISTS}" OR NOT EXISTS "${NCNN_CMAKELISTS}")
        message(WARNING
                "缺少 native submodule 源码，已跳过 sherpa-ncnn native 构建。\n"
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
