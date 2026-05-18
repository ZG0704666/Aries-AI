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

    add_link_options("-Wl,-z,max-page-size=16384")
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

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

        # 某些网络环境下 github.com 可能超时，因此这里提前准备依赖本地归档。
        # 这些文件名与上游 cmake 脚本的 possible_file_locations 保持一致。
        set(ARIES_KALDI_NATIVE_FBANK_ARCHIVE "${CMAKE_BINARY_DIR}/kaldi-native-fbank-1.22.3.tar.gz")
        if(NOT EXISTS "${ARIES_KALDI_NATIVE_FBANK_ARCHIVE}")
            set(ARIES_KALDI_NATIVE_FBANK_FALLBACK_URL "https://codeload.github.com/csukuangfj/kaldi-native-fbank/tar.gz/refs/tags/v1.22.3")
            message(STATUS "Prefetching kaldi-native-fbank from fallback URL: ${ARIES_KALDI_NATIVE_FBANK_FALLBACK_URL}")

            file(DOWNLOAD
                    "${ARIES_KALDI_NATIVE_FBANK_FALLBACK_URL}"
                    "${ARIES_KALDI_NATIVE_FBANK_ARCHIVE}"
                    EXPECTED_HASH "SHA256=9176cc66fc7ce1edf85cf355b06e320c57db6297df74277f575183468893cf61"
                    STATUS ARIES_KALDI_NATIVE_FBANK_DOWNLOAD_STATUS
                    TLS_VERIFY ON
                    TIMEOUT 180
                    INACTIVITY_TIMEOUT 30)

            list(GET ARIES_KALDI_NATIVE_FBANK_DOWNLOAD_STATUS 0 ARIES_KALDI_NATIVE_FBANK_DOWNLOAD_CODE)
            list(GET ARIES_KALDI_NATIVE_FBANK_DOWNLOAD_STATUS 1 ARIES_KALDI_NATIVE_FBANK_DOWNLOAD_MESSAGE)
            if(NOT ARIES_KALDI_NATIVE_FBANK_DOWNLOAD_CODE EQUAL 0)
                file(REMOVE "${ARIES_KALDI_NATIVE_FBANK_ARCHIVE}")
                message(WARNING
                        "Failed to prefetch kaldi-native-fbank archive (code=${ARIES_KALDI_NATIVE_FBANK_DOWNLOAD_CODE}): ${ARIES_KALDI_NATIVE_FBANK_DOWNLOAD_MESSAGE}.\n"
                        "CMake will continue and may still try remote fetch via original URLs.")
            else()
                message(STATUS "Prepared local kaldi-native-fbank archive: ${ARIES_KALDI_NATIVE_FBANK_ARCHIVE}")
            endif()
        endif()

        set(ARIES_KALDIFST_ARCHIVE "${CMAKE_BINARY_DIR}/kaldifst-1.7.17.tar.gz")
        if(NOT EXISTS "${ARIES_KALDIFST_ARCHIVE}")
            set(ARIES_KALDIFST_FALLBACK_URL "https://codeload.github.com/k2-fsa/kaldifst/tar.gz/refs/tags/v1.7.17")
            message(STATUS "Prefetching kaldifst from fallback URL: ${ARIES_KALDIFST_FALLBACK_URL}")

            file(DOWNLOAD
                    "${ARIES_KALDIFST_FALLBACK_URL}"
                    "${ARIES_KALDIFST_ARCHIVE}"
                    EXPECTED_HASH "SHA256=c4b701a23a400bda8032586b02c7e0d5e813a765832df60c23e6df9e62b010f4"
                    STATUS ARIES_KALDIFST_DOWNLOAD_STATUS
                    TLS_VERIFY ON
                    TIMEOUT 180
                    INACTIVITY_TIMEOUT 30)

            list(GET ARIES_KALDIFST_DOWNLOAD_STATUS 0 ARIES_KALDIFST_DOWNLOAD_CODE)
            list(GET ARIES_KALDIFST_DOWNLOAD_STATUS 1 ARIES_KALDIFST_DOWNLOAD_MESSAGE)
            if(NOT ARIES_KALDIFST_DOWNLOAD_CODE EQUAL 0)
                file(REMOVE "${ARIES_KALDIFST_ARCHIVE}")
                message(WARNING
                        "Failed to prefetch kaldifst archive (code=${ARIES_KALDIFST_DOWNLOAD_CODE}): ${ARIES_KALDIFST_DOWNLOAD_MESSAGE}.\n"
                        "CMake will continue and may still try remote fetch via original URLs.")
            else()
                message(STATUS "Prepared local kaldifst archive: ${ARIES_KALDIFST_ARCHIVE}")
            endif()
        endif()

        # kaldi-native-fbank 在构建时还会下载 kissfft。
        set(ARIES_KISSFFT_ARCHIVE "${CMAKE_BINARY_DIR}/kissfft-febd4caeed32e33ad8b2e0bb5ea77542c40f18ec.zip")
        if(NOT EXISTS "${ARIES_KISSFFT_ARCHIVE}")
            set(ARIES_KISSFFT_FALLBACK_URL "https://codeload.github.com/mborgerding/kissfft/zip/febd4caeed32e33ad8b2e0bb5ea77542c40f18ec")
            message(STATUS "Prefetching kissfft from fallback URL: ${ARIES_KISSFFT_FALLBACK_URL}")

            file(DOWNLOAD
                    "${ARIES_KISSFFT_FALLBACK_URL}"
                    "${ARIES_KISSFFT_ARCHIVE}"
                    EXPECTED_HASH "SHA256=497103e664168ebe39580b757adbe616f6cf85a16572af581ca7bc42d0ab13fd"
                    STATUS ARIES_KISSFFT_DOWNLOAD_STATUS
                    TLS_VERIFY ON
                    TIMEOUT 180
                    INACTIVITY_TIMEOUT 30)

            list(GET ARIES_KISSFFT_DOWNLOAD_STATUS 0 ARIES_KISSFFT_DOWNLOAD_CODE)
            list(GET ARIES_KISSFFT_DOWNLOAD_STATUS 1 ARIES_KISSFFT_DOWNLOAD_MESSAGE)
            if(NOT ARIES_KISSFFT_DOWNLOAD_CODE EQUAL 0)
                file(REMOVE "${ARIES_KISSFFT_ARCHIVE}")
                message(WARNING
                        "Failed to prefetch kissfft archive (code=${ARIES_KISSFFT_DOWNLOAD_CODE}): ${ARIES_KISSFFT_DOWNLOAD_MESSAGE}.\n"
                        "CMake will continue and may still try remote fetch via original URLs.")
            else()
                message(STATUS "Prepared local kissfft archive: ${ARIES_KISSFFT_ARCHIVE}")
            endif()
        endif()

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
