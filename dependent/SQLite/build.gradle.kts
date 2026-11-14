plugins {
    alias(libs.plugins.android.library)
}

android {

    namespace = "org.sqlite.database"
    buildToolsVersion = "37.0.0"
    ndkVersion = "30.0.16248370"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk { 
            // Building with NDK_DEBUG=1 for mips crashes the compiler in ndk 14. 
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
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

    //sourceSets.main.jni.srcDirs = [] //disable automatic ndk-build call
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    lint {
        // 将警告视为警告,不要作为错误
        warningsAsErrors = false
        // 出现错误时终止构建
        abortOnError = true
        // 禁用某些检查
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

    useLibrary("android.test.base") // for android.test.AndroidTestCase
    useLibrary("android.test.runner") // for android.test.MoreAsserts
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    androidTestImplementation("androidx.annotation:annotation:1.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")
    androidTestImplementation("androidx.test:rules:1.2.0")
    testImplementation("junit:junit:4.13")
}
