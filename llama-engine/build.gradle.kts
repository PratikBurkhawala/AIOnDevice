plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val androidNdkVersion = "27.0.12077973"
val androidSdkDir = System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: "${System.getProperty("user.home")}/Android/Sdk"
val vulkanHeadersDir = listOf(
    "${projectDir}/src/main/cpp/third_party/Vulkan-Headers/include",
    "$androidSdkDir/ndk/25.1.8937393/sources/third_party/vulkan/src/include",
    "$androidSdkDir/ndk/23.1.7779620/sources/third_party/vulkan/src/include",
    "$androidSdkDir/ndk/$androidNdkVersion/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include",
).firstOrNull { file("$it/vulkan/vulkan.hpp").exists() || file("$it/vulkan/vulkan.h").exists() }
    ?: "$androidSdkDir/ndk/$androidNdkVersion/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include"
val spirvHeadersDir = "$androidSdkDir/ndk/$androidNdkVersion/sources/third_party/shaderc/third_party/spirv-tools/external/spirv-headers/include"

android {
    namespace = "com.example.aiondevicebenchmark.llama"
    compileSdk = 34
    ndkVersion = androidNdkVersion

    defaultConfig {
        minSdk = 28

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-I$spirvHeadersDir")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DBUILD_SHARED_LIBS=OFF",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_BUILD_APP=OFF",
                    "-DLLAMA_CURL=OFF",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF",
                    "-DVulkan_INCLUDE_DIR=$vulkanHeadersDir",
                    "-DVulkan_LIBRARY=$androidSdkDir/ndk/$androidNdkVersion/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/28/libvulkan.so",
                    "-DVulkan_GLSLC_EXECUTABLE=$androidSdkDir/ndk/$androidNdkVersion/shader-tools/linux-x86_64/glslc",
                    "-DSPIRV-Headers_DIR=${projectDir}/src/main/cpp/cmake/SPIRV-Headers",
                    "-DGGML_VULKAN_SHADERS_GEN_TOOLCHAIN=${projectDir}/src/main/cpp/cmake/HostVulkanShadersToolchain.cmake",
                    "-DGGML_VULKAN=ON",
                )
            }
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":engine"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
