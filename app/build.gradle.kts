import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

// ── Git 版本控制 ──
val backVersionCode = 20
val baseVersionCode = 10
val baseVersionName = "26.10.09"

fun Project.gitCommitCount(): Int = try {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
} catch (_: Exception) { backVersionCode }

fun Project.gitHash(): String = try {
    providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }
        .standardOutput.asText.get().trim()
} catch (_: Exception) {
    SimpleDateFormat("MMddHHmm").format(Date())
}

val appVersionCode = baseVersionCode + gitCommitCount()
val appVersionName = "${baseVersionName}.${gitCommitCount()}.${gitHash()}"

android {
    namespace = "me.huidoudour.file.manager"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    defaultConfig {
        applicationId = namespace
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            // 覆盖全部主流 ABI (含 32 位设备)
            // 注: ffmpeg-kit 仅提供 arm64-v8a / x86_64 原生库, 32 位机型上音视频预览自动回退系统打开
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }
    val useSignKey = rootProject.hasProperty("storeFile") &&
            rootProject.hasProperty("storePassword") &&
            rootProject.hasProperty("keyAlias") &&
            rootProject.hasProperty("keyPassword")
    signingConfigs {
        if (useSignKey) {
            create("sign_key") {
                storeFile = file(rootProject.property("storeFile") as String)
                storePassword = rootProject.property("storePassword") as String
                keyAlias = rootProject.property("keyAlias") as String
                keyPassword = rootProject.property("keyPassword") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }
    buildTypes {
        debug {
            signingConfig = if (useSignKey) {
                signingConfigs.getByName("sign_key")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = false
            }
            signingConfig = if (useSignKey) {
                signingConfigs.getByName("sign_key")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
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
            "NewerVersionAvailable",   // 不强制更新到最新版本
            "SelectedPhotoAccess",     // 忽略照片权限警告
            "PhotoAndVideoPolicy"      // 忽略照片权限警告
        )
        checkOnly += setOf(
            "NotSibling",              // 必须检查布局引用错误
            "DuplicateIds",            // 必须检查重复ID
            "UnknownId"                // 必须检查未知ID引用
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    "baselineProfile"(project(":baseline"))

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    implementation(libs.androidx.compose.ui.test.manifest)
    //noinspection UseTomlInstead
    implementation("androidx.compose.ui:ui-tooling-preview:1.13.0-alpha03")

    // FFmpeg (音视频解析/缩略图/波形) 与 media3 (播放 / 媒体会话系统控制 / 后台播放)
    implementation(libs.dev.ffmpegkit.min)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
}
