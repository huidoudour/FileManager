package me.huidoudour.file.manager.signing

import android.content.Context
import android.os.Environment
import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** 签名阶段 (由界面映射成对应文案) */
enum class SignStage { READING, SIGNING, WRITING }

/** 签名结果: 产物文件与耗时 */
data class SignResult(val output: File, val elapsedMs: Long)

/**
 * 应用内 APK 签名执行器 (移植自 cache/ApkSign 的 ApkSignTask)。
 *
 * 流程: 原 APK → 缓存副本 → apksig 签名 (V1/V2/V3 可选) → 写出产物。
 * 先拷到缓存再签，避免输入输出落在同一个文件上，
 * 也兼容只读/加密的外部存储目录。
 */
object ApkSignTask {

    /**
     * 后台签名。
     *
     * @param onStage 阶段回调，已切回主线程，可直接更新界面状态
     */
    suspend fun sign(
        context: Context,
        source: File,
        output: File,
        v1: Boolean,
        v2: Boolean,
        v3: Boolean,
        identity: SignerIdentity,
        onStage: (SignStage) -> Unit = {}
    ): SignResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val tempIn = File(context.cacheDir, "sign_in_$start.apk")
        val tempOut = File(context.cacheDir, "sign_out_$start.apk")
        try {
            notifyStage(onStage, SignStage.READING)
            source.copyTo(tempIn, overwrite = true)

            notifyStage(onStage, SignStage.SIGNING)
            // apksig 9.x 起私钥需通过 KeyConfig 传入 (本地私钥用 KeyConfig.Jca 包装)
            val signerConfig = ApkSigner.SignerConfig.Builder(
                identity.alias, KeyConfig.Jca(identity.privateKey), identity.certificates
            ).build()
            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(tempIn)
                .setOutputApk(tempOut)
                .setV1SigningEnabled(v1)
                .setV2SigningEnabled(v2)
                .setV3SigningEnabled(v3)
                .setV4SigningEnabled(false)
                .build()
                .sign()

            notifyStage(onStage, SignStage.WRITING)
            tempOut.copyTo(output, overwrite = true)
            SignResult(output, System.currentTimeMillis() - start)
        } finally {
            tempIn.delete()
            tempOut.delete()
        }
    }

    /**
     * 生成产物路径: 优先与原 APK 同目录 (xxx-signed.apk, 重名自动加序号)，
     * 目录不可写时退到 Download/ApkSign/。
     */
    fun resolveOutput(source: File): File {
        val name = source.name
        val stem = if (name.endsWith(".apk", ignoreCase = true)) name.dropLast(4) else name
        val sourceDir = source.parentFile
        if (sourceDir != null && sourceDir.canWrite()) {
            return uniqueFile(sourceDir, "$stem-signed.apk")
        }
        val fallbackDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ApkSign"
        )
        if (!fallbackDir.exists() && !fallbackDir.mkdirs()) {
            throw IOException("无法创建输出目录: ${fallbackDir.absolutePath}")
        }
        return uniqueFile(fallbackDir, "$stem-signed.apk")
    }

    /** 同名文件已存在时依次尝试 "name (1).apk"、"name (2).apk"… */
    private fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val stem = fileName.removeSuffix(".apk")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($index).apk")
            index++
        }
        return candidate
    }

    private suspend fun notifyStage(onStage: (SignStage) -> Unit, stage: SignStage) {
        withContext(Dispatchers.Main) { onStage(stage) }
    }
}
