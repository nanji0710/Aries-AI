/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.ncnn.ModelConfig
import com.k2fsa.sherpa.ncnn.RecognizerConfig
import com.k2fsa.sherpa.ncnn.SherpaNcnn
import com.k2fsa.sherpa.ncnn.getDecoderConfig
import com.k2fsa.sherpa.ncnn.getFeatureExtractorConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 基于sherpa-ncnn的本地语音识别实现 sherpa-ncnn是一个轻量级、高性能的语音识别引擎，比Vosk更适合移动端 参考:
 * https://github.com/k2-fsa/sherpa-ncnn
 */
@SuppressLint("MissingPermission")
class SherpaSpeechRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "SherpaSpeechRecognizer"
        private const val SAMPLE_RATE = 16000

        /** 模型目录名（下载落点与 assets 约定目录一致，见 [SherpaModelRepository]）。 */
        private const val MODEL_DIR_NAME = SherpaModelRepository.MODEL_DIR_NAME

        /** 运行模型所需的 7 个文件（缺任意一个都无法创建识别器）。 */
        private val MODEL_FILE_NAMES = SherpaModelRepository.MODEL_FILE_NAMES

        // 候选模型目录（按优先级依次尝试）：
        // 1. 文档约定的嵌套布局  sherpa-models/<modelDirName>/
        // 2. legacy 嵌套布局     sherpa-models/models/<modelDirName>/
        // 3. 平铺布局            sherpa-models/  （模型文件直接放在 sherpa-models/ 根目录下）
        private val CANDIDATE_ASSET_DIRS =
                listOf(
                        "sherpa-models/$MODEL_DIR_NAME",
                        "sherpa-models/models/$MODEL_DIR_NAME",
                        "sherpa-models",
                )

        /** 模型目录准备（校验 + 拷贝）的进程级互斥锁：避免多个识别器实例并发拷贝上百 MB 资源。 */
        private val modelDirLock = Any()
    }

    /** 初始化失败原因，用于向用户给出可操作的提示（而不是笼统的「初始化失败」）。 */
    enum class InitFailure {
        /** native 库未打进 APK（sherpa-ncnn submodule 未构建）。 */
        NATIVE_LIBRARY_UNAVAILABLE,

        /** 模型资源未打进 APK（assets/sherpa-models 下缺 7 个模型文件）。 */
        MODEL_ASSETS_MISSING,

        /** native 库与模型资源都缺（仓库默认状态）。 */
        NATIVE_LIBRARY_AND_MODEL_ASSETS_MISSING,

        /** 模型未下载（2026-10 起模型移出 APK，需在设置页下载到 filesDir）。 */
        MODEL_NOT_DOWNLOADED,

        /** 库与资源都在，但引擎创建失败。 */
        RECOGNIZER_CREATE_FAILED,
    }

    @Volatile private var initFailure: InitFailure? = null

    /** 上一次初始化失败的原因；初始化成功时为 null。 */
    fun lastInitFailure(): InitFailure? = initFailure

    /** 上一次失败原因对应的用户可读文案资源 id；成功时为 null。 */
    fun lastFailureMessageRes(): Int? =
            when (initFailure) {
                InitFailure.NATIVE_LIBRARY_UNAVAILABLE ->
                        com.ai.phoneagent.R.string.speech_model_failure_native_missing
                InitFailure.MODEL_ASSETS_MISSING ->
                        com.ai.phoneagent.R.string.speech_model_failure_assets_missing
                InitFailure.NATIVE_LIBRARY_AND_MODEL_ASSETS_MISSING ->
                        com.ai.phoneagent.R.string.speech_model_failure_native_and_assets_missing
                InitFailure.MODEL_NOT_DOWNLOADED ->
                        com.ai.phoneagent.R.string.speech_model_not_downloaded
                InitFailure.RECOGNIZER_CREATE_FAILED ->
                        com.ai.phoneagent.R.string.speech_model_failure_recognizer_create_failed
                null -> null
            }

    /**
     * 只读探测模型资源是否可用（不拷贝、不建目录）：
     * 1. filesDir 下已缓存的目标目录是否完整（首次运行拷贝成功后即命中）；
     * 2. APK assets 中任一候选目录是否包含全部 7 个模型文件。
     */
    private fun hasModelAssets(): Boolean {
        val cachedComplete =
                CANDIDATE_ASSET_DIRS.any { assetDir ->
                    val dir = File(context.filesDir, assetDir.substringAfterLast('/'))
                    isModelDirComplete(dir)
                }
        if (cachedComplete) return true
        return CANDIDATE_ASSET_DIRS.any { assetDir ->
            runCatching {
                        val entries = context.assets.list(assetDir)?.toSet().orEmpty()
                        MODEL_FILE_NAMES.all { entries.contains(it) }
                    }
                    .getOrDefault(false)
        }
    }

    /** 目录是否包含全部模型文件（存在、是文件且非空）。 */
    private fun isModelDirComplete(dir: File): Boolean {
        if (!dir.exists()) return false
        return MODEL_FILE_NAMES.all { name ->
            val f = File(dir, name)
            f.exists() && f.isFile && f.length() > 0
        }
    }

    /** 目录中缺失的模型文件名列表（目录不存在时返回全部）。 */
    private fun missingModelFiles(dir: File): List<String> {
        if (!dir.exists()) return MODEL_FILE_NAMES
        return MODEL_FILE_NAMES.filterNot { name ->
            val f = File(dir, name)
            f.exists() && f.isFile && f.length() > 0
        }
    }

    /** 识别结果回调 */
    interface RecognitionListener {
        /** 部分识别结果（实时中间结果） */
        fun onPartialResult(text: String)

        /** 最终识别结果 */
        fun onResult(text: String)

        /** 最终结果（识别结束） */
        fun onFinalResult(text: String)

        /** 音量振幅 (0.0 ~ 1.0) */
        fun onAmplitude(amplitude: Float) {}

        /** 识别出错 */
        fun onError(exception: Exception)

        /** 识别超时 */
        fun onTimeout()
    }

    private var recognizer: SherpaNcnn? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + scopeJob)

    private var listener: RecognitionListener? = null
    private var isInitialized = false
    private var isListening = false
    private val finalResultEmitted = AtomicBoolean(false)

    private fun releaseAudioRecord() {
        val ar = audioRecord ?: return
        audioRecord = null
        runCatching {
            if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                ar.stop()
            }
        }
        runCatching { ar.release() }
    }

    /**
     * 初始化语音识别引擎
     * @return true表示初始化成功
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) return true

        Log.d(TAG, "Initializing sherpa-ncnn...")
        return try {
            withContext(Dispatchers.IO) {
                initFailure = null

                // 模型已移出 APK（原本占 124.5 MB）：filesDir 未下载、assets 也没打包时，
                // 直接报告「未下载」，由设置页引导下载，而不是笼统的「初始化失败」。
                if (!hasModelAssets()) {
                    initFailure = InitFailure.MODEL_NOT_DOWNLOADED
                    Log.i(
                            TAG,
                            "Speech model is not downloaded yet: " +
                                    SherpaModelRepository.modelDir(context).absolutePath
                    )
                    return@withContext false
                }

                if (!SherpaNcnn.isNativeLibraryReady()) {
                    val assetsAvailable = hasModelAssets()
                    initFailure =
                            if (assetsAvailable) InitFailure.NATIVE_LIBRARY_UNAVAILABLE
                            else InitFailure.NATIVE_LIBRARY_AND_MODEL_ASSETS_MISSING
                    Log.e(
                            TAG,
                            "Sherpa native library is unavailable: " +
                                    "${SherpaNcnn.nativeLibraryErrorMessage()}; " +
                                    "modelAssetsAvailable=$assetsAvailable"
                    )
                    return@withContext false
                }

                val created = createRecognizer()
                if (!created || recognizer == null) {
                    if (initFailure == null) {
                        initFailure = InitFailure.RECOGNIZER_CREATE_FAILED
                    }
                    Log.e(TAG, "Failed to create sherpa-ncnn recognizer (reason=${initFailure})")
                    return@withContext false
                }

                Log.d(TAG, "sherpa-ncnn initialized successfully")
                isInitialized = true
                true
            }
        } catch (e: Exception) {
            initFailure = InitFailure.RECOGNIZER_CREATE_FAILED
            Log.e(TAG, "Failed to initialize sherpa-ncnn", e)
            false
        }
    }

    /** 检查是否已初始化 */
    fun isReady(): Boolean = isInitialized

    /** 检查是否正在录音识别 */
    fun isListening(): Boolean = isListening

    @Throws(IOException::class)
    private fun copyAssetDirRecursive(
            assetDir: String,
            targetDir: File,
            overwrite: Boolean = false,
    ): File {
        if (overwrite && targetDir.exists()) {
            runCatching { targetDir.deleteRecursively() }
        } else if (!overwrite && targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
            Log.d(TAG, "Model files already exist in cache: ${targetDir.absolutePath}")
            return targetDir
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("Failed to create target directory: ${targetDir.absolutePath}")
        }

        val assetManager = context.assets
        val entries = assetManager.list(assetDir)
        if (entries.isNullOrEmpty()) {
            throw IOException("Asset directory '$assetDir' is empty or does not exist.")
        }

        entries.forEach { entry ->
            val assetPath = "$assetDir/$entry"
            val outputFile = File(targetDir, entry)
            val children = assetManager.list(assetPath)
            if (!children.isNullOrEmpty()) {
                copyAssetDirRecursive(assetPath, outputFile, overwrite = true)
            } else {
                outputFile.parentFile?.let { parent ->
                    if (!parent.exists()) {
                        parent.mkdirs()
                    }
                }
                // 空目录与文件在 AssetManager.list() 下都表现为"无子项"。
                // 对空目录调用 open() 会抛异常；这里容错跳过，避免中断整个模型拷贝。
                runCatching {
                    assetManager.open(assetPath).use { inputStream ->
                        FileOutputStream(outputFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }.onFailure { e ->
                    Log.w(TAG, "Skip non-file asset entry '$assetPath': ${e.message}")
                    runCatching { if (outputFile.exists() && outputFile.length() == 0L) outputFile.delete() }
                }
            }
        }

        return targetDir
    }

    private fun createRecognizer(): Boolean {
        val requiredFiles = MODEL_FILE_NAMES

        val localModelDir =
                CANDIDATE_ASSET_DIRS.firstNotNullOfOrNull { assetDir ->
                    ensureModelDirReady(
                            assetDir = assetDir,
                            targetRootDir = context.filesDir,
                            requiredFiles = requiredFiles
                    )
                }
                        ?: run {
                            initFailure = InitFailure.MODEL_ASSETS_MISSING
                            Log.e(
                                    TAG,
                                    "No complete sherpa model assets found. Expected " +
                                            "${requiredFiles.joinToString()} under one of " +
                                            "$CANDIDATE_ASSET_DIRS"
                            )
                            return false
                        }

        val featConfig =
                getFeatureExtractorConfig(sampleRate = SAMPLE_RATE.toFloat(), featureDim = 80)

        val modelConfig =
                ModelConfig(
                        encoderParam =
                                File(localModelDir, "encoder_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        encoderBin =
                                File(localModelDir, "encoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                        decoderParam =
                                File(localModelDir, "decoder_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        decoderBin =
                                File(localModelDir, "decoder_jit_trace-pnnx.ncnn.bin").absolutePath,
                        joinerParam =
                                File(localModelDir, "joiner_jit_trace-pnnx.ncnn.param")
                                        .absolutePath,
                        joinerBin =
                                File(localModelDir, "joiner_jit_trace-pnnx.ncnn.bin").absolutePath,
                        tokens = File(localModelDir, "tokens.txt").absolutePath,
                        numThreads = 4,
                        useGPU = false
                )

        val decoderConfig = getDecoderConfig(method = "greedy_search", numActivePaths = 4)

        val recognizerConfig =
                RecognizerConfig(
                        featConfig = featConfig,
                        modelConfig = modelConfig,
                        decoderConfig = decoderConfig,
                        // Push-to-talk: keep recording while user is holding the button,
                        // and finalize only on explicit stopListening().
                        enableEndpoint = false,
                        rule1MinTrailingSilence = 2.4f,
                        rule2MinTrailingSilence = 1.2f,
                        rule3MinUtteranceLength = 20.0f,
                        hotwordsFile = "",
                        hotwordsScore = 1.5f
                )

        return try {
            recognizer =
                    SherpaNcnn(
                            config = recognizerConfig,
                            assetManager = null // Force using newFromFile
                    )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SherpaNcnn instance", e)
            recognizer = null
            false
        }
    }

    private fun ensureModelDirReady(
            assetDir: String,
            targetRootDir: File,
            requiredFiles: List<String>,
    ): File? {
        val dirName = assetDir.substringAfterLast('/')
        val targetDir = File(targetRootDir, dirName)

        fun isComplete(): Boolean {
            if (!targetDir.exists()) return false
            return requiredFiles.all { name ->
                val f = File(targetDir, name)
                f.exists() && f.isFile && f.length() > 0
            }
        }

        fun missingFiles(): List<String> =
                requiredFiles.filterNot { name ->
                    val f = File(targetDir, name)
                    f.exists() && f.isFile && f.length() > 0
                }

        if (isComplete()) return targetDir

        synchronized(modelDirLock) {
            // 双检：并发初始化（MainActivity 与 AutomationViewModel 各持一个实例）时，
            // 先到的实例可能已经完成拷贝，这里直接复用，避免重复拷贝上百 MB 资源。
            if (isComplete()) return targetDir

            if (targetDir.exists()) {
                Log.w(
                        TAG,
                        "Model cache is incomplete. Re-copying model files: ${targetDir.absolutePath}"
                )
                runCatching { targetDir.deleteRecursively() }
            }

            return try {
                copyAssetDirRecursive(assetDir, targetDir, overwrite = true)
                // 关键校验：copyAssetDirRecursive 对「目录里只有 README.md」这类不完整资源不会抛异常，
                // 若不校验就会把一个缺模型文件的目录当成成功结果交给 native 层，
                // 失败原因被掩盖成泛化的「引擎创建失败」。
                if (!isComplete()) {
                    Log.e(
                            TAG,
                            "Model assets copied from '$assetDir' are incomplete. Missing: " +
                                    "${missingFiles().joinToString()}"
                    )
                    runCatching { targetDir.deleteRecursively() }
                    null
                } else {
                    targetDir
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy model assets from '$assetDir'.", e)
                null
            }
        }
    }

    /**
     * 开始语音识别
     * @param listener 识别结果回调
     */
    fun startListening(listener: RecognitionListener) {
        if (!isInitialized) {
            listener.onError(IllegalStateException("Speech recognizer not initialized"))
            return
        }
        if (isListening) {
            return
        }

        this.listener = listener
        finalResultEmitted.set(false)
        recognizer?.reset(false)

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)

        if (minBufferSize <= 0) {
            listener.onError(
                    IllegalStateException("AudioRecord.getMinBufferSize failed: $minBufferSize")
            )
            return
        }

        val ar =
                try {
                    AudioRecord(
                            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            SAMPLE_RATE,
                            channelConfig,
                            audioFormat,
                            minBufferSize * 2
                    )
                } catch (e: Exception) {
                    listener.onError(e)
                    return
                }

        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { ar.release() }
            listener.onError(IllegalStateException("AudioRecord init failed, state=${ar.state}"))
            return
        }

        audioRecord = ar

        val started =
                runCatching {
                    ar.startRecording()
                    true
                }
                        .getOrElse {
                            listener.onError(it as? Exception ?: RuntimeException(it))
                            false
                        }

        if (!started) {
            releaseAudioRecord()
            return
        }

        isListening = true
        Log.d(TAG, "Started recording")

        recordingJob =
                scope.launch {
                    try {
                        val bufferSize = minBufferSize
                        val audioBuffer = ShortArray(bufferSize)
                        var lastText = ""

                        while (isActive && isListening) {
                            val ret = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                            if (ret < 0) {
                                withContext(Dispatchers.Main) {
                                    listener.onError(IOException("AudioRecord.read failed: $ret"))
                                }
                                isListening = false
                                break
                            }
                            if (ret > 0) {
                                // 计算振幅 (RMS)
                                var sum = 0.0
                                for (i in 0 until ret) {
                                    sum += audioBuffer[i] * audioBuffer[i]
                                }
                                val rms = Math.sqrt(sum / ret)
                                // 简单的归一化处理 (32768 为最大值，但语音输入通常到不了那么大，这里取一个经验值 3000-5000)
                                val amplitude = (rms / 3000.0).toFloat().coerceIn(0f, 1f)
                                withContext(Dispatchers.Main) {
                                    listener?.onAmplitude(amplitude)
                                }

                                val samples = FloatArray(ret) { i -> audioBuffer[i] / 32768.0f }
                                val currentRecognizer = recognizer ?: break

                                currentRecognizer.acceptSamples(samples)
                                while (currentRecognizer.isReady()) {
                                    currentRecognizer.decode()
                                }

                                val isEndpoint = currentRecognizer.isEndpoint()
                                val text = currentRecognizer.text

                                if (text.isNotBlank() && lastText != text) {
                                    lastText = text
                                    withContext(Dispatchers.Main) {
                                        if (isEndpoint) {
                                            listener.onResult(text)
                                        } else {
                                            listener.onPartialResult(text)
                                        }
                                    }
                                }

                                if (isEndpoint) {
                                    currentRecognizer.reset(false)
                                    isListening = false
                                    if (finalResultEmitted.compareAndSet(false, true)) {
                                        withContext(Dispatchers.Main) {
                                            listener.onFinalResult(lastText)
                                        }
                                    }
                                    break
                                }
                            }
                        }

                        Log.d(TAG, "Recording loop ended.")
                    } finally {
                        releaseAudioRecord()
                    }
                }
    }

    /** 停止语音识别 */
    fun stopListening() {
        if (!isListening) return

        Log.d(TAG, "Stopping recognition...")
        isListening = false
        recordingJob?.cancel()

        // Finalize recognition
        recognizer?.inputFinished()
        val text = recognizer?.text ?: ""
        if (finalResultEmitted.compareAndSet(false, true)) {
            listener?.onFinalResult(text)
        }

        releaseAudioRecord()
    }

    /** 取消语音识别（不返回结果） */
    fun cancel() {
        isListening = false
        recordingJob?.cancel()
        finalResultEmitted.set(true)
        releaseAudioRecord()
    }

    /** 释放资源 */
    fun shutdown() {
        cancel()
        scope.cancel()
        recognizer = null
        isInitialized = false
    }
}
