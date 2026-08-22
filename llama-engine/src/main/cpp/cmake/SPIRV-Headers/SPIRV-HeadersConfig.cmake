set(_spirv_headers_root "")

foreach(_candidate
    "${ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers/include"
    "${CMAKE_ANDROID_NDK}/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers/include"
)
    if(EXISTS "${_candidate}/spirv/unified1/spirv.h")
        set(_spirv_headers_root "${_candidate}")
        break()
    endif()
endforeach()

if(NOT _spirv_headers_root)
    set(SPIRV-Headers_FOUND FALSE)
    return()
endif()

if(NOT TARGET SPIRV-Headers::SPIRV-Headers)
    add_library(SPIRV-Headers::SPIRV-Headers INTERFACE IMPORTED)
    target_include_directories(SPIRV-Headers::SPIRV-Headers INTERFACE "${_spirv_headers_root}")
endif()

set(SPIRV-Headers_FOUND TRUE)
