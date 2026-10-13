package com.ai.phoneagent.speech

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 离线语音识别模型（sherpa-ncnn streaming zipformer 中英双语）的仓库层。
 *
 * 2026-10 起模型不再打进 APK（原本占 124.5 MB，是安装包体积的 53%），改为首次使用时下载到
 * `filesDir/<MODEL_DIR_NAME>/`。`SherpaSpeechRecognizer` 的加载路径本来就支持「文件已在
 * filesDir 就直接用」，因此下载完成后无需任何额外适配即可加载。
 *
 * 下载特性：
 * - 单飞（[downloadMutex]）：MainActivity 与 AutomationViewModel 可能同时触发初始化；
 * - 断点续传：单文件写入 `<name>.part`，支持 `Range` 续传，校验字节数后原子改名；
 * - 逐文件字节数校验（encoder.bin 单个 127 MB，中断代价高，必须能续传）；
 * - 多源回退：[SOURCE_BASE_URLS] 依次尝试（国内优先 hf-mirror，其次 huggingface.co）。
 */
object SherpaModelRepository {

    private const val TAG = "SherpaModelRepository"

    const val MODEL_DIR_NAME = "sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13"

    private const val USER_AGENT = "PhoneAgent"

    /** 模型文件与期望字节数（与官方 release 包逐一核对过）。 */
    data class ModelFile(val name: String, val bytes: Long)

    val MODEL_FILES: List<ModelFile> =
            listOf(
                    ModelFile("encoder_jit_trace-pnnx.ncnn.param", 161_888L),
                    ModelFile("encoder_jit_trace-pnnx.ncnn.bin", 127_364_056L),
                    ModelFile("decoder_jit_trace-pnnx.ncnn.param", 439L),
                    ModelFile("decoder_jit_trace-pnnx.ncnn.bin", 6_412_296L),
                    ModelFile("joiner_jit_trace-pnnx.ncnn.param", 490L),
                    ModelFile("joiner_jit_trace-pnnx.ncnn.bin", 7_350_724L),
                    ModelFile("tokens.txt", 56_317L),
            )

    val MODEL_FILE_NAMES: List<String> = MODEL_FILES.map { it.name }

    /** 全部模型文件合计字节数（141,346,210 B ≈ 134.8 MiB）。 */
    val TOTAL_BYTES: Long = MODEL_FILES.sumOf { it.bytes }

    /** 下载源（按顺序尝试）；均为 HuggingFace 上的同一仓库。 */
    private val SOURCE_BASE_URLS =
            listOf(
                    "https://hf-mirror.com/csukuangfj/$MODEL_DIR_NAME/resolve/main",
                    "https://huggingface.co/csukuangfj/$MODEL_DIR_NAME/resolve/main",
            )

    private val downloadMutex = Mutex()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }

    /** 模型目录：`filesDir/<MODEL_DIR_NAME>`（与 [SherpaSpeechRecognizer] 的缓存快路径一致）。 */
    fun modelDir(context: Context): File = File(context.filesDir, MODEL_DIR_NAME)

    /** 模型是否已完整下载（逐文件比对字节数）。 */
    fun isModelReady(context: Context): Boolean =
            MODEL_FILES.all { spec ->
                val file = File(modelDir(context), spec.name)
                file.isFile && file.length() == spec.bytes
            }

    /** 模型是否缺失（需要下载）。 */
    fun isModelMissing(context: Context): Boolean = !isModelReady(context) && !hasBundledAssets(context)

    /** APK assets 中是否仍带完整模型（离线开发/回归时保留的兼容路径）。 */
    fun hasBundledAssets(context: Context): Boolean =
            runCatching {
                        val entries = context.assets.list("sherpa-models/$MODEL_DIR_NAME")?.toSet().orEmpty()
                        MODEL_FILE_NAMES.all { entries.contains(it) }
                    }
                    .getOrDefault(false)

    /** 缺失（或大小不符）的文件名列表。 */
    fun missingFiles(context: Context): List<String> =
            MODEL_FILES.filterNot { spec ->
                        val file = File(modelDir(context), spec.name)
                        file.isFile && file.length() == spec.bytes
                    }
                    .map { it.name }

    /**
     * 下载（或续传）全部模型文件。已在位且字节数正确的文件会被跳过。
     *
     * @param onProgress 回调参数为 (已就绪字节数, 总字节数)，在 IO 线程调用。
     */
    suspend fun downloadIfNeeded(
            context: Context,
            onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> =
            withContext(Dispatchers.IO) {
                downloadMutex.withLock {
                    runCatching {
                        val dir = modelDir(context)
                        if (!dir.exists() && !dir.mkdirs()) {
                            throw IOException("无法创建模型目录: ${dir.absolutePath}")
                        }
                        if (dir.usableSpace in 1 until TOTAL_BYTES) {
                            throw IOException("存储空间不足，需要约 ${TOTAL_BYTES / 1024 / 1024} MB 可用空间")
                        }
                        var completed = 0L
                        MODEL_FILES.forEach { spec ->
                            val target = File(dir, spec.name)
                            if (target.isFile && target.length() == spec.bytes) {
                                completed += spec.bytes
                                onProgress(completed, TOTAL_BYTES)
                            } else {
                                downloadFile(spec, target) { written ->
                                    onProgress(completed + written, TOTAL_BYTES)
                                }
                                completed += spec.bytes
                                onProgress(completed, TOTAL_BYTES)
                            }
                        }
                        if (!isModelReady(context)) {
                            throw IOException("模型文件校验失败，缺少: ${missingFiles(context).joinToString()}")
                        }
                        dir
                    }
                }
            }

    /** 逐源尝试下载单个文件，成功后原子改名。 */
    private fun downloadFile(spec: ModelFile, target: File, onBytes: (Long) -> Unit) {
        val part = File(target.parentFile, "${spec.name}.part")
        var lastError: Exception? = null
        SOURCE_BASE_URLS.forEach { baseUrl ->
            try {
                downloadFileFrom(baseUrl, spec, part, onBytes)
                if (part.length() != spec.bytes) {
                    throw IOException("${spec.name} 字节数不符: ${part.length()} != ${spec.bytes}")
                }
                if (target.exists() && !target.delete()) {
                    throw IOException("无法覆盖旧文件: ${target.name}")
                }
                if (!part.renameTo(target)) {
                    throw IOException("无法重命名 ${part.name}")
                }
                return
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "从 $baseUrl 下载 ${spec.name} 失败，尝试下一个源", e)
            }
        }
        throw IOException("下载 ${spec.name} 失败: ${lastError?.message}", lastError)
    }

    /** 单次 HTTP 下载（支持 `Range` 续传），写入 `<name>.part`。 */
    private fun downloadFileFrom(baseUrl: String, spec: ModelFile, part: File, onBytes: (Long) -> Unit) {
        val existing = if (part.isFile) part.length() else 0L
        val resumable = existing > 0L && existing < spec.bytes
        val request =
                Request.Builder()
                        .url("$baseUrl/${spec.name}")
                        .header("User-Agent", USER_AGENT)
                        .apply { if (resumable) header("Range", "bytes=$existing-") }
                        .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("响应为空")
            val append = response.code == 206 && resumable
            if (!append && part.exists()) part.delete()
            RandomAccessFile(part, "rw").use { raf ->
                if (append) raf.seek(existing) else raf.setLength(0L)
                var written = if (append) existing else 0L
                onBytes(written)
                body.byteStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        raf.write(buffer, 0, read)
                        written += read
                        onBytes(written)
                    }
                }
            }
        }
    }
}
