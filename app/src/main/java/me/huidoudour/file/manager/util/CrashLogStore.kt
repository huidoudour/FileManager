package me.huidoudour.file.manager.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.edit
import me.huidoudour.file.manager.R
import org.acra.ACRAConstants
import org.acra.ReportField
import org.acra.file.ReportLocator
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * 崩溃报告的读取、分享与清理。
 *
 * 汇集两个来源:
 * - ACRA: Java 未处理异常，写入应用私有目录 `app_ACRA-unapproved/<时间>.stacktrace`
 * - xCrash: Native 崩溃与 ANR，写入应用私有目录 `files/xcrash/tombstone_*.xcrash`
 *
 * 两者都在崩溃时同步落盘，这里负责在下次启动时读取，供弹窗与设置页展示。
 */
object CrashLogStore {

    /** xCrash 日志目录名，位于应用私有 files 目录下 */
    const val XCRASH_LOG_DIR_NAME = "xcrash"

    private const val PREFS_NAME = "file_manager_prefs"
    private const val KEY_LAST_SHOWN_REPORT = "crash_last_shown_report"

    /** ACRA 报告最多保留数量 (xCrash 由库自身按 Java/Native/ANR 各 10 份维护) */
    private const val MAX_REPORTS = 20

    /** xCrash 报告用于展示的最大字符数, 避免超大文本拖慢 UI */
    private const val MAX_XCRASH_TEXT = 96 * 1024

    private const val NA = "N/A"

    private const val TOMBSTONE_PREFIX = "tombstone_"
    private const val XCRASH_TRACE_SUFFIX = ".trace.xcrash"
    private const val XCRASH_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

    /** xCrash 崩溃报告后缀: Java / Native / ANR */
    private val xcrashSuffixes = listOf(".java.xcrash", ".native.xcrash", ".anr.xcrash")

    /** 崩溃报告来源 */
    enum class Source { ACRA, XCRASH }

    private val crashTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /** 一份崩溃报告的可展示信息 */
    data class CrashLog(
        val file: File,
        val source: Source,
        val crashTime: String,
        val versionName: String,
        val androidVersion: String,
        val phoneModel: String,
        val exceptionSummary: String,
        val stackTrace: String
    )

    /** 读取全部崩溃报告（ACRA + xCrash，按时间倒序） */
    fun list(context: Context): List<CrashLog> = runCatching {
        allReportFiles(context).mapNotNull(::parse)
    }.getOrDefault(emptyList())

    /**
     * 取出最近一次尚未提示过的崩溃报告。
     *
     * 只解析最新的一份文件，避免启动时读取全部历史报告；
     * 取到后立即记录已提示的文件名，避免用户每次启动都看到同一个弹窗；
     * 若用户未删除该报告，仍可在设置页的崩溃日志列表中查看。
     */
    fun latestUnseen(context: Context): CrashLog? {
        val latestFile = allReportFiles(context).firstOrNull() ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_SHOWN_REPORT, null) == latestFile.name) return null
        prefs.edit { putString(KEY_LAST_SHOWN_REPORT, latestFile.name) }
        return parse(latestFile)
    }

    fun delete(log: CrashLog): Boolean = runCatching {
        // ANR 报告可能附带同名的 .trace.xcrash 线程转储, 一并删除
        if (log.source == Source.XCRASH) {
            xcrashCompanions(log.file).forEach { it.delete() }
        }
        log.file.delete()
    }.getOrDefault(false)

    /** 清空全部崩溃报告（ACRA + xCrash），返回删除数量 */
    fun clear(context: Context): Int = runCatching {
        val acraCount = ReportLocator(context).unapprovedReports.count { it.delete() }
        val xcrashCount = xcrashDir(context).listFiles()?.count { it.isFile && it.delete() } ?: 0
        acraCount + xcrashCount
    }.getOrDefault(0)

    /**
     * 只保留最近的 [MAX_REPORTS] 份 ACRA 报告。
     * xCrash 报告由库自身按 Java/Native/ANR 各 10 份的上限维护，这里不重复处理。
     */
    fun prune(context: Context) {
        runCatching {
            acraFiles(context)
                .drop(MAX_REPORTS)
                .forEach { it.delete() }
        }
    }

    /** 全部报告文件（ACRA + xCrash），按修改时间倒序 */
    private fun allReportFiles(context: Context): List<File> =
        (acraFiles(context) + xcrashFiles(context))
            .sortedByDescending { it.lastModified() }

    /** ACRA 报告文件 */
    private fun acraFiles(context: Context): List<File> =
        ReportLocator(context).unapprovedReports
            .filter { it.isFile && it.name.endsWith(ACRAConstants.REPORTFILE_EXTENSION) }

    /** xCrash 报告目录 */
    private fun xcrashDir(context: Context): File =
        File(context.filesDir, XCRASH_LOG_DIR_NAME)

    /** xCrash 报告文件（不含 .trace.xcrash 线程转储） */
    private fun xcrashFiles(context: Context): List<File> =
        xcrashDir(context).listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith(TOMBSTONE_PREFIX) &&
                    xcrashSuffixes.any { file.name.endsWith(it) }
            }
            .orEmpty()

    /** xCrash ANR 报告附带的线程转储文件 */
    private fun xcrashCompanions(file: File): List<File> {
        val baseName = xcrashSuffixes
            .firstOrNull { file.name.endsWith(it) }
            ?.let { file.name.removeSuffix(it) }
            ?: return emptyList()
        return file.parentFile?.listFiles()
            ?.filter { it.isFile && it.name == baseName + XCRASH_TRACE_SUFFIX }
            .orEmpty()
    }

    /** 分享崩溃报告（纯文本，避免完整 JSON 超出 Intent 传输上限） */
    fun share(context: Context, log: CrashLog) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_share_subject))
                putExtra(Intent.EXTRA_TEXT, buildShareText(log))
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.crash_share_chooser))
            )
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.crash_share_failed, e.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun buildShareText(log: CrashLog): String = buildString {
        appendLine("File Manager 崩溃报告")
        appendLine("时间: ${log.crashTime}")
        appendLine("版本: ${log.versionName}")
        appendLine("设备: ${log.phoneModel} / Android ${log.androidVersion}")
        appendLine("异常: ${log.exceptionSummary}")
        appendLine()
        append(log.stackTrace)
    }

    private fun parse(file: File): CrashLog? =
        if (file.name.endsWith(ACRAConstants.REPORTFILE_EXTENSION)) {
            parseAcra(file)
        } else {
            parseXCrash(file)
        }

    private fun parseAcra(file: File): CrashLog? {
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val stackTrace = json.optString(ReportField.STACK_TRACE.name)
            .takeUnless { it.isBlank() || it == NA } ?: return null
        return CrashLog(
            file = file,
            source = Source.ACRA,
            crashTime = formatCrashTime(
                raw = json.optString(ReportField.USER_CRASH_DATE.name),
                fallback = file.lastModified()
            ),
            versionName = json.optString(ReportField.APP_VERSION_NAME.name).orNa(),
            androidVersion = json.optString(ReportField.ANDROID_VERSION.name).orNa(),
            phoneModel = json.optString(ReportField.PHONE_MODEL.name).orNa(),
            exceptionSummary = stackTrace.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty(),
            stackTrace = stackTrace
        )
    }

    /**
     * 解析 xCrash 的 tombstone 文本。
     *
     * 头部字段与 xCrash 的 Util.getLogHeader() 格式一致，例如 `Crash time: '2026-10-20T12:34:56.789+0800'`。
     */
    private fun parseXCrash(file: File): CrashLog? {
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        if (text.isBlank()) return null
        return CrashLog(
            file = file,
            source = Source.XCRASH,
            crashTime = formatDate(xcrashCrashTime(text, file)),
            versionName = xcrashHeader(text, "App version").orNa(),
            androidVersion = xcrashHeader(text, "OS version").orNa(),
            phoneModel = xcrashHeader(text, "Model").orNa(),
            exceptionSummary = xcrashSummary(text),
            stackTrace = text.take(MAX_XCRASH_TEXT)
        )
    }

    private fun xcrashHeader(text: String, key: String): String? =
        Regex("(?m)^${Regex.escape(key)}: '([^']*)'")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)

    /** 崩溃时间取自报告头部，缺失时回退到文件名里的微秒时间戳 */
    private fun xcrashCrashTime(text: String, file: File): Long {
        val raw = xcrashHeader(text, "Crash time")
        if (!raw.isNullOrBlank()) {
            runCatching {
                SimpleDateFormat(XCRASH_TIME_PATTERN, Locale.US).parse(raw)
            }.getOrNull()?.let { return it.time }
        }
        // 文件名: tombstone_<微秒时间戳>_<版本>__<进程名>.<类型>.xcrash
        val fromName = file.name.removePrefix(TOMBSTONE_PREFIX)
            .substringBefore('_')
            .toLongOrNull()
            ?.div(1000)
        return fromName ?: file.lastModified()
    }

    private fun xcrashSummary(text: String): String {
        if (xcrashHeader(text, "Crash type")?.contains("anr", ignoreCase = true) == true) {
            return "ANR"
        }
        val signalLine = text.lineSequence().firstOrNull { it.startsWith("signal ") }
        if (signalLine != null) return signalLine

        val javaTraceHead = text.substringAfter("java stacktrace:", "")
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
        if (javaTraceHead != null) return javaTraceHead.trim()

        return text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun formatCrashTime(raw: String, fallback: Long): String {
        if (raw.isBlank() || raw == NA) return formatDate(fallback)
        return runCatching {
            OffsetDateTime.parse(raw).format(crashTimeFormatter)
        }.getOrElse { formatDate(fallback) }
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    private fun String?.orNa(): String = takeUnless { it.isNullOrBlank() || it == NA } ?: NA
}
