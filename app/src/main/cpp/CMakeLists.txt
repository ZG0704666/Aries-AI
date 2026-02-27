cmake_minimum_required(VERSION 3.22.1)

project(aries_sherpa_native)

function(force_release_flags_for_debug target_name)
    if(TARGET ${target_name})
        # Keep app variant as Debug, but compile heavy native libs with release-like optimization.
        target_compile_definitions(${target_name} PRIVATE $<$<CONFIG:Debug>:NDEBUG>)
        target_compile_options(${target_name} PRIVATE $<$<CONFIG:Debug>:-O3 -g0>)
    endif()
endfunction()

if(ANDROID)
    set(SHERPA_NCNN_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/sherpa-ncnn")
    set(SHERPA_NCNN_CMAKELISTS "${SHERPA_NCNN_SOURCE_DIR}/CMakeLists.txt")
    if(NOT EXISTS "${SHERPA_NCNN_CMAKELISTS}")
        message(FATAL_ERROR
                "Missing sherpa-ncnn submodule contents: ${SHERPA_NCNN_CMAKELISTS}\n"
                "Run: git submodule update --init --recursive -- thirdparty/sherpa-ncnn")
    endif()

    # Build sherpa-ncnn as part of Android Studio externalNativeBuild.
    # Use local submodules to avoid downloading a different ncnn build.
    list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
    list(APPEND CMAKE_MODULE_PATH "${SHERPA_NCNN_SOURCE_DIR}/cmake")
    include_directories("${SHERPA_NCNN_SOURCE_DIR}")

    set(FETCHCONTENT_SOURCE_DIR_NCNN "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/ncnn" CACHE PATH "" FORCE)

    set(SHERPA_NCNN_ENABLE_PORTAUDIO OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_BINARY OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_TEST OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_C_API OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_JNI ON CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_PYTHON OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_FFMPEG_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(SHERPA_NCNN_ENABLE_GENERATE_INT8_SCALE_TABLE OFF CACHE BOOL "" FORCE)

    # Keep sherpa/ncnn static inside libsherpa-ncnn-jni and avoid OpenMP abort issues.
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
