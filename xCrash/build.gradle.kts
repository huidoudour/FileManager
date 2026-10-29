plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "xcrash.lib"
    // 本机仅安装 android-37.0 平台, 不指定 minor 版本
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 28
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        // 向引用本库的 App 传播混淆 keep 规则 (防止 R8 混淆 NativeHandler 导致 JNI 符号失配)
        consumerProguardFiles("proguard-rules.pro")

        // 需要调试 native 崩溃时通过 -PuseASAN 开启 AddressSanitizer
        @Suppress("UnstableApiUsage")
        if (providers.gradleProperty("useASAN").isPresent) {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DANDROID_ARM_MODE=arm", "-DUSEASAN=ON")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isJniDebuggable = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // 本机已安装的 CMake 版本
            version = "4.1.2"
        }
    }

    // 使用本机已安装的 NDK (仅 r30)
    ndkVersion = "30.0.16248370"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf(
            "HardcodedText",           // 允许硬编码文本(调试阶段)
            "SetTextI18n",             // 允许文本拼接
            "DefaultLocale",           // 允许默认Locale
            "SdCardPath",              // 允许硬编码路径(系统工具)
            "UseTomlInstead",          // 暂不强制使用版本目录
            "ObsoleteSdkInt",          // 允许过时的SDK版本检查
            "UnusedResources",         // 允许未使用资源(可能被动态引用)
            "Overdraw",                // 允许过度绘制
            "UselessParent",           // 允许冗余父布局
            "Autofill",                // 不强制自动填充提示
            "FragmentTagUsage",        // 允许使用fragment标签
            "GradleDependency",        // 不强制更新依赖
            "NewerVersionAvailable"    // 不强制更新到最新版本
        )
    }
}
