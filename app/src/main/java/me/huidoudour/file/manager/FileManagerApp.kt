package me.huidoudour.file.manager

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import me.huidoudour.file.manager.util.CrashLogStore
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.StringFormat
import xcrash.Errno
import xcrash.XCrash

/**
 * 应用入口 — 初始化崩溃捕获
 *
 * ACRA 记录 Java 未处理异常（`app_ACRA-unapproved`），
 * xCrash 记录 Native 崩溃与 ANR（`files/xcrash`），
 * 下次启动时由 [CrashLogStore] 读取并提示用户。
 * 两者都不配置上传，崩溃日志不会离开设备。
 */
class FileManagerApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        initAcra()
        initXCrash()
    }

    private fun initAcra() {
        ACRA.init(
            this,
            CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig::class.java)
                .withReportFormat(StringFormat.JSON)
                // 保留历史报告，启动时由 CrashLogStore.prune() 限制数量
                .withDeleteUnapprovedReportsOnApplicationStart(false)
                // 让系统继续显示崩溃对话框，用户能立即感知异常
                .withAlsoReportToAndroidFramework(true)
        )
    }

    /**
     * xCrash 负责 Native 崩溃与 ANR，Java 崩溃交给 ACRA，避免同一异常落两份报告。
     *
     * 初始化失败（例如 16KB page size 设备无法加载未对齐的 native 库）只返回错误码，
     * 不会影响应用本身运行，只是缺少 Native/ANR 捕获能力。
     * 本地自编译的 xcrash-android-lib.aar 已按 16KB 对齐（app/deps），正常情况下不会再走这条分支。
     */
    private fun initXCrash() {
        val result = XCrash.init(
            this,
            XCrash.InitParameters()
                .setLogDir(File(filesDir, CrashLogStore.XCRASH_LOG_DIR_NAME).absolutePath)
                .disableJavaCrashHandler()
        )
        if (result != Errno.OK) {
            Log.w(TAG, "xCrash init failed, code=$result")
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 清理超出保留上限的旧报告，避免长期累积占用空间
        Thread({ CrashLogStore.prune(this) }, "crash-log-prune").start()
    }

    private companion object {
        const val TAG = "FileManagerApp"
    }
}
