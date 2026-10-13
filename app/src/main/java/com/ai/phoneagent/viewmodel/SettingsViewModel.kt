package com.ai.phoneagent.viewmodel

import android.app.Application
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ai.phoneagent.R
import com.ai.phoneagent.core.security.ApiConfigSignature
import com.ai.phoneagent.data.preferences.AppPreferencesRepository
import com.ai.phoneagent.net.AriesApiClient
import com.ai.phoneagent.net.AriesOidcAuthManager
import com.ai.phoneagent.net.AutoGlmClient
import com.ai.phoneagent.net.ModelScopeModelDownloader
import com.ai.phoneagent.speech.SherpaModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn

class SettingsViewModel(
    application: Application,
    private val prefs: AppPreferencesRepository,
    private val ariesOidcAuthManager: AriesOidcAuthManager,
    private val ariesApiClient: AriesApiClient,
    private val modelScopeModelDownloader: ModelScopeModelDownloader,
) : AndroidViewModel(application) {

    enum class SettingsPage {
        Home,
        ModelApi,
        Membership,
        Appearance,
        About,
    }

    enum class ApiMode {
        Official,
        ThirdParty,
        Local,
        Aries,
    }

    private var remoteApiOk: Boolean? = null
    private var remoteApiChecking: Boolean = false
    private var apiCheckSeq: Int = 0
    private var lastCheckedApiKey: String = ""
    private var qwenDownloadInFlight: Boolean = false
    private var speechDownloadInFlight: Boolean = false
    private var apiInputTag: String = ""
    private var apiModePersistJob: Job? = null

    var localModelReady by mutableStateOf(false)
        private set

    var currentPage by mutableStateOf(SettingsPage.Home)
        private set

    var pageTransitionForward by mutableStateOf(true)
        private set

    var apiInputText by mutableStateOf("")
        private set

    var useThirdPartyApi by mutableStateOf(false)
        private set

    var useLocalModel by mutableStateOf(false)
        private set

    var useAriesApi by mutableStateOf(false)
        private set

    var currentApiMode by mutableStateOf(ApiMode.Official)
        private set

    var apiBaseUrlText by mutableStateOf("")
        private set

    var apiModelText by mutableStateOf("")
        private set

    var apiStatusText by mutableStateOf("")
        private set

    var apiStatusPositive by mutableStateOf(false)
        private set

    var qwenButtonText by mutableStateOf("")
        private set

    var qwenButtonEnabled by mutableStateOf(true)
        private set

    // ─── 离线语音模型（sherpa-ncnn，已移出 APK，需首次下载） ────
    var speechModelReady by mutableStateOf(false)
        private set

    var speechButtonText by mutableStateOf("")
        private set

    var speechButtonEnabled by mutableStateOf(true)
        private set

    var speechDownloadProgressText by mutableStateOf<String?>(null)
        private set

    // ─── Aries API 区段可见性（隐藏特性，需连点 xuanyu.xyla 20 次解锁） ────
    var showAriesApiSection by mutableStateOf(false)
        private set

    // ─── Aries 登录状态 ──────────────────────────────────────────────────────
    var ariesLoggedInUser by mutableStateOf("")
        private set

    // ─── Aries 已选模型 ──────────────────────────────────────────────────────
    var ariesSelectedModel by mutableStateOf("")
        private set

    // ─── Aries 模型选择对话框状态 ────────────────────────────────────────────
    var showAriesModelDialog by mutableStateOf(false)
        private set

    var ariesAvailableModels by mutableStateOf<List<AriesApiClient.ModelInfo>>(emptyList())
        private set

    /** 正在拉取模型列表；用于在对话框内显示加载态，避免「点了没反应」。 */
    var ariesModelLoading by mutableStateOf(false)
        private set

    /** 模型列表拉取失败原因 / 未登录提示；null 表示当前无错误。 */
    var ariesModelError by mutableStateOf<String?>(null)
        private set

    // ─── 登录对话框状态 ──────────────────────────────────────────────────────
    var showAriesLoginDialog by mutableStateOf(false)
        private set

    var ariesLoginUsername by mutableStateOf("")
        private set

    var ariesLoginPassword by mutableStateOf("")
        private set

    var ariesLoginLoading by mutableStateOf(false)
        private set

    var ariesLoginError by mutableStateOf<String?>(null)
        private set

    /**
     * [AppPreferencesRepository.activeAriesApiKeyFlow] 的本地缓存。
     *
     * 原先 [updateStatusText] 每次调用都在调用线程上执行 `runBlocking` + AndroidKeyStore 解密，
     * 而它会被 Compose 点击回调（更换模型、切换模式）直接调用，是「有概率卡死/点不动」的主因之一。
     */
    private var activeAriesApiKey: String = ""

    /** 已保存的通用 API Key 缓存，避免 [resolveApiKeyFromInput] 在主线程做 runBlocking 读取。 */
    private var cachedApiKey: String = ""

    init {
        // 外部存储 stat（getExternalFilesDir + 多次 exists/length）不放在构造函数主线程里执行
        viewModelScope.launch {
            localModelReady =
                withContext(Dispatchers.IO) {
                    modelScopeModelDownloader.isQwen35ModelReady(getApplication())
                }
            updateQwenDownloadButtonState()
            updateStatusText()
        }
        // 离线语音模型（已移出 APK）的就绪状态同样不能在构造线程上做磁盘 stat
        viewModelScope.launch {
            speechModelReady =
                withContext(Dispatchers.IO) {
                    SherpaModelRepository.isModelReady(getApplication())
                }
            updateSpeechDownloadButtonState()
        }
        restoreSettings()
        // 响应式缓存当前生效的 Aries API Key（含 KeyStore 解密，全程在 IO 上）
        viewModelScope.launch {
            // flowOn 把上游（含 KeyStore 解密）推到 IO，collect 回调仍在主线程更新状态
            prefs.activeAriesApiKeyFlow.flowOn(Dispatchers.IO).collect { key ->
                activeAriesApiKey = key
                if (useAriesApi) updateStatusText()
            }
        }
        // 响应式监听隐藏模型入口解锁状态（AboutViewModel 切换后实时生效）
        viewModelScope.launch {
            prefs.ariesApiSectionUnlockedFlow.collect { unlocked ->
                showAriesApiSection = unlocked
                if (!unlocked && (currentApiMode == ApiMode.Local || currentApiMode == ApiMode.Aries)) {
                    applyApiModeState(ApiMode.Official)
                    apiCheckSeq++
                    remoteApiOk = null
                    remoteApiChecking = false
                    lastCheckedApiKey = ""
                    persistApiMode(clearCheckResults = true)
                    updateStatusText()
                }
            }
        }
        // 响应式监听已登录用户
        viewModelScope.launch {
            prefs.ariesLoggedInUserFlow.collect { user ->
                ariesLoggedInUser = user
            }
        }
        // 响应式监听 Aries 已选模型
        viewModelScope.launch {
            prefs.ariesSelectedModelFlow.collect { model ->
                ariesSelectedModel = model
            }
        }
    }

    /**
     * 恢复设置页状态。
     *
     * 原实现在调用线程（= `init` 期间的主线程）上做约 12 次 `*Blocking()` DataStore 读，
     * 其中 [AppPreferencesRepository.getApiKeyBlocking] 还会触发 AndroidKeyStore 解密，
     * 是「打开设置页 / 点更换模型时有概率卡住」的主要来源。
     * 现在改为先在 IO 上读一份快照，再回主线程写入 Compose 状态。
     */
    fun restoreSettings() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { readSettingsSnapshot() }
            applySettingsSnapshot(snapshot)
        }
    }

    private class SettingsSnapshot(
        val savedApiKey: String,
        val useThirdParty: Boolean,
        val useLocal: Boolean,
        val useAries: Boolean,
        val ariesSectionUnlocked: Boolean,
        val ariesLoggedInUser: String,
        val ariesSelectedModel: String,
        val apiBaseUrl: String,
        val apiModel: String,
        val lastCheckSig: String,
        val hasLastCheckOk: Boolean,
        val lastCheckOk: Boolean,
    )

    private suspend fun readSettingsSnapshot(): SettingsSnapshot {
        val hasLastCheckOk = prefs.hasApiLastCheckOk()
        return SettingsSnapshot(
            savedApiKey = prefs.getApiKey(),
            useThirdParty = prefs.getApiUseThirdParty(),
            useLocal = prefs.getApiUseLocalModel(),
            useAries = prefs.getUseAriesApi(),
            ariesSectionUnlocked = prefs.getAriesApiSectionUnlocked(),
            ariesLoggedInUser = prefs.getAriesLoggedInUser(),
            ariesSelectedModel = prefs.getAriesSelectedModel(),
            apiBaseUrl = prefs.getApiThirdPartyBaseUrl(),
            apiModel = prefs.getApiThirdPartyModel(),
            lastCheckSig = prefs.getApiLastCheckSig(),
            hasLastCheckOk = hasLastCheckOk,
            lastCheckOk = if (hasLastCheckOk) prefs.getApiLastCheckOk() else false,
        )
    }

    private fun applySettingsSnapshot(snapshot: SettingsSnapshot) {
        val saved = snapshot.savedApiKey
        cachedApiKey = saved
        apiInputTag = saved
        apiInputText = maskKey(saved)
        showAriesApiSection = snapshot.ariesSectionUnlocked
        val restoredMode =
            resolveApiMode(
                useThirdParty = snapshot.useThirdParty,
                useLocal = snapshot.useLocal && snapshot.ariesSectionUnlocked,
                useAries = snapshot.useAries && snapshot.ariesSectionUnlocked,
            )
        applyApiModeState(restoredMode)
        ariesLoggedInUser = snapshot.ariesLoggedInUser
        ariesSelectedModel = snapshot.ariesSelectedModel
        apiBaseUrlText = snapshot.apiBaseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        apiModelText = snapshot.apiModel.ifBlank { AutoGlmClient.DEFAULT_MODEL }
        normalizePersistedApiModeIfNeeded(
            restoredThirdParty = snapshot.useThirdParty,
            restoredLocal = snapshot.useLocal,
            restoredAries = snapshot.useAries,
        )

        val currentSig =
            apiConfigSignature(
                apiKey = saved,
                baseUrl = resolveApiBaseUrl(),
                model = resolveApiModel(),
            )
        if (saved.isNotBlank() && snapshot.hasLastCheckOk && snapshot.lastCheckSig == currentSig) {
            remoteApiOk = snapshot.lastCheckOk
            remoteApiChecking = false
            lastCheckedApiKey = saved
            apiStatusText =
                stringRes(
                    if (snapshot.lastCheckOk) {
                        R.string.settings_api_available
                    } else {
                        R.string.settings_api_failed
                    },
                )
        } else {
            remoteApiOk = null
            remoteApiChecking = false
            lastCheckedApiKey = ""
            apiStatusText = stringRes(R.string.m3t_sidebar_api_not_checked)
        }
        updateQwenDownloadButtonState()
        updateStatusText()
    }

    fun refreshLocalModelState() {
        viewModelScope.launch {
            localModelReady =
                withContext(Dispatchers.IO) {
                    modelScopeModelDownloader.isQwen35ModelReady(getApplication())
                }
            updateQwenDownloadButtonState()
            updateStatusText()
        }
    }

    fun openModelApiPage() {
        pageTransitionForward = true
        currentPage = SettingsPage.ModelApi
    }

    fun openMembershipPage() {
        pageTransitionForward = true
        currentPage = SettingsPage.Membership
    }

    fun openHomePage() {
        pageTransitionForward = false
        currentPage = SettingsPage.Home
    }

    fun navigateTo(page: SettingsPage) {
        pageTransitionForward = page != SettingsPage.Home
        currentPage = page
    }

    fun onApiModeChange(mode: ApiMode, onToast: (String) -> Unit) {
        // 注意：这里不能再用 `apiModePersistJob?.isActive` 做守卫。
        // persistApiMode 自身已经会 cancel 上一个写入任务，而旧守卫会在上一次写入尚未落盘时
        // 直接 return，连 applyApiModeState 都不执行 —— 表现为「点了模式没有任何反应」，且是否命中取决于时序。
        if (mode == currentApiMode) return
        if (!showAriesApiSection && (mode == ApiMode.Local || mode == ApiMode.Aries)) {
            onToast(stringRes(R.string.settings_model_api_aries_login_required))
            return
        }
        applyApiModeState(mode)
        apiCheckSeq++
        remoteApiOk = null
        remoteApiChecking = false
        lastCheckedApiKey = ""
        persistApiMode(clearCheckResults = true)
        if (mode == ApiMode.Local && !localModelReady) {
            onToast(stringRes(R.string.m3t_sidebar_local_model_not_ready))
        }
        updateStatusText()
    }

    fun onApiInputChanged(value: String) {
        apiInputTag = ""
        apiInputText = value
        onApiConfigChanged(clearApiValue = value.isBlank())
    }

    fun onUseThirdPartyChange(checked: Boolean) {
        onApiModeChange(if (checked) ApiMode.ThirdParty else ApiMode.Official) {}
    }

    fun onApiBaseUrlChange(value: String) {
        apiBaseUrlText = value
        if (useThirdPartyApi) {
            onApiConfigChanged(clearApiValue = false)
        }
    }

    fun onApiModelChange(value: String) {
        apiModelText = value
        if (useThirdPartyApi) {
            onApiConfigChanged(clearApiValue = false)
        }
    }

    fun onUseLocalModelChange(checked: Boolean, onToast: (String) -> Unit) {
        onApiModeChange(if (checked) ApiMode.Local else ApiMode.Official, onToast)
    }

    fun onUseAriesApiChange(checked: Boolean) {
        onApiModeChange(if (checked) ApiMode.Aries else ApiMode.Official) {}
    }

    fun pasteApiKey(context: Context, onToast: (String) -> Unit) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val pasted =
            clipboard?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
                ?.trim()
                .orEmpty()
        if (pasted.isBlank()) {
            onToast(stringRes(R.string.settings_clipboard_empty))
            return
        }
        apiInputTag = ""
        apiInputText = pasted
        onToast(stringRes(R.string.settings_api_key_pasted))
        onApiConfigChanged(clearApiValue = false)
    }

    fun openApiKeyPage(context: Context) {
        runCatching {
            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }
    }

    fun onApiConfigChanged(clearApiValue: Boolean) {
        apiCheckSeq++
        remoteApiOk = null
        remoteApiChecking = false
        lastCheckedApiKey = ""
        viewModelScope.launch {
            prefs.writeApiConfig(
                removeApiKey = clearApiValue,
                useThirdParty = useThirdPartyApi,
                useLocalModel = useLocalModel,
                thirdPartyBaseUrl = apiBaseUrlText.trim(),
                thirdPartyModel = apiModelText.trim(),
                clearCheckResults = true,
            )
            prefs.setUseAriesApi(useAriesApi)
        }
        updateStatusText()
    }

    fun checkApiConnection(onToast: (String) -> Unit) {
        if (useLocalModel) {
            // 本地模型就绪判定要读外部存储（多次 exists/length），必须在 IO 上做：
            // 本函数由「验证连接」按钮的 Compose 点击回调（主线程）直接调用。
            viewModelScope.launch {
                val ready =
                    withContext(Dispatchers.IO) {
                        modelScopeModelDownloader.isQwen35ModelReady(getApplication())
                    }
                localModelReady = ready
                updateQwenDownloadButtonState()
                apiStatusPositive = ready
                apiStatusText =
                    stringRes(
                        if (ready) {
                            R.string.m3t_sidebar_local_model_ready
                        } else {
                            R.string.m3t_sidebar_local_model_not_ready
                        },
                    )
                if (!ready) {
                    onToast(stringRes(R.string.m3t_sidebar_local_model_not_ready))
                }
            }
            return
        }

        if (useAriesApi) {
            val cached = activeAriesApiKey.trim()
            if (cached.isNotBlank()) {
                startApiCheck(
                    key = cached,
                    baseUrl = AriesApiClient.ARIES_API_V1_BASE_URL,
                    model = ariesSelectedModel.ifBlank { AutoGlmClient.DEFAULT_MODEL },
                    force = true,
                    onToast = onToast,
                )
                return
            }
            // 缓存尚未就绪时再走一次异步读取，避免在主线程 runBlocking（KeyStore 解密）
            viewModelScope.launch {
                val key = withContext(Dispatchers.IO) { prefs.getActiveAriesApiKey() }.trim()
                if (key.isBlank()) {
                    onToast(stringRes(R.string.settings_model_api_aries_login_required))
                    return@launch
                }
                activeAriesApiKey = key
                startApiCheck(
                    key = key,
                    baseUrl = AriesApiClient.ARIES_API_V1_BASE_URL,
                    model = ariesSelectedModel.ifBlank { AutoGlmClient.DEFAULT_MODEL },
                    force = true,
                    onToast = onToast,
                )
            }
            return
        }

        val key = resolveApiKeyFromInput()
        if (key.isBlank()) {
            onToast(stringRes(R.string.settings_api_key_required))
            return
        }

        viewModelScope.launch { prefs.setApiKey(key) }
        cachedApiKey = key
        apiInputTag = key
        apiInputText = maskKey(key)
        val baseUrl = resolveRemoteApiBaseUrl()
        val model = resolveRemoteApiModel()
        startApiCheck(key = key, baseUrl = baseUrl, model = model, force = true, onToast = onToast)
    }

    fun enqueueQwenDownloads(onToast: (String) -> Unit) {
        if (qwenDownloadInFlight) return
        qwenDownloadInFlight = true
        updateQwenDownloadButtonState()
        viewModelScope.launch {
            try {
                val result = modelScopeModelDownloader.enqueueQwen35Downloads(getApplication())
                result
                    .onSuccess {
                        localModelReady =
                            withContext(Dispatchers.IO) {
                                modelScopeModelDownloader.isQwen35ModelReady(getApplication())
                            }
                        val message =
                            when {
                                it.enqueuedCount > 0 ->
                                    stringRes(
                                        R.string.m3t_sidebar_qwen_download_summary_format,
                                        it.enqueuedCount,
                                        it.skippedCount,
                                        it.targetDir,
                                    )
                                it.skippedCount > 0 -> stringRes(R.string.m3t_sidebar_qwen_download_cached)
                                else -> stringRes(R.string.m3t_sidebar_qwen_download_enqueued)
                            }
                        onToast(message)
                    }
                    .onFailure { err ->
                        onToast(
                            stringRes(
                                R.string.m3t_sidebar_qwen_download_failed_format,
                                err.message?.trim().orEmpty().ifBlank {
                                    stringRes(R.string.update_download_failed_unknown)
                                },
                            ),
                        )
                    }
            } finally {
                // 无论成功、失败还是协程被取消都要复位，否则按钮会永久停留在
                // 「正在准备下载…」的禁用态，用户观感同样是「点不动」。
                qwenDownloadInFlight = false
                updateQwenDownloadButtonState()
                updateStatusText()
            }
        }
    }

    /** 刷新离线语音模型（sherpa-ncnn，已移出 APK）的就绪状态与按钮文案。 */
    fun refreshSpeechModelState() {
        viewModelScope.launch {
            speechModelReady =
                    withContext(Dispatchers.IO) {
                        SherpaModelRepository.isModelReady(getApplication())
                    }
            updateSpeechDownloadButtonState()
        }
    }

    /**
     * 下载离线语音模型（约 135 MB）。模型不再随安装包分发，首次使用前需下载到 filesDir；
     * 下载完成后 [SherpaSpeechRecognizer] 的 ensureModelDirReady 会直接命中已存在的目录。
     */
    fun enqueueSpeechModelDownload(onToast: (String) -> Unit) {
        if (speechDownloadInFlight) return
        speechDownloadInFlight = true
        speechDownloadProgressText = stringRes(R.string.speech_download_preparing)
        updateSpeechDownloadButtonState()
        viewModelScope.launch {
            try {
                var lastPercent = -1
                val result =
                        SherpaModelRepository.downloadIfNeeded(getApplication()) { downloaded, total ->
                            if (total <= 0L) return@downloadIfNeeded
                            val percent = ((downloaded * 100) / total).toInt()
                            if (percent == lastPercent) return@downloadIfNeeded
                            lastPercent = percent
                            val text =
                                    stringRes(
                                            R.string.speech_download_progress_format,
                                            formatBytes(downloaded),
                                            formatBytes(total),
                                    )
                            viewModelScope.launch {
                                speechDownloadProgressText = text
                                speechButtonText = text
                            }
                        }
                result
                        .onSuccess {
                            speechModelReady = true
                            onToast(stringRes(R.string.speech_download_complete))
                        }
                        .onFailure { err ->
                            onToast(
                                    stringRes(
                                            R.string.speech_download_failed_format,
                                            err.message?.trim().orEmpty().ifBlank {
                                                stringRes(R.string.update_download_failed_unknown)
                                            },
                                    ),
                            )
                        }
            } finally {
                speechDownloadInFlight = false
                speechDownloadProgressText = null
                speechModelReady =
                        withContext(Dispatchers.IO) {
                            SherpaModelRepository.isModelReady(getApplication())
                        }
                updateSpeechDownloadButtonState()
                updateStatusText()
            }
        }
    }

    private fun updateSpeechDownloadButtonState() {
        speechButtonEnabled = !speechDownloadInFlight
        speechButtonText =
                when {
                    speechDownloadInFlight ->
                            speechDownloadProgressText ?: stringRes(R.string.speech_download_preparing)
                    speechModelReady -> stringRes(R.string.speech_download_ready)
                    else -> stringRes(R.string.speech_download)
                }
    }

    private fun formatBytes(bytes: Long): String =
            when {
                bytes >= 1024L * 1024L * 1024L ->
                        String.format(
                                java.util.Locale.US,
                                "%.1f GB",
                                bytes / 1024.0 / 1024.0 / 1024.0,
                        )
                bytes >= 1024L * 1024L ->
                        String.format(java.util.Locale.US, "%.0f MB", bytes / 1024.0 / 1024.0)
                bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
                else -> "$bytes B"
            }

    fun startApiCheck(
        key: String,
        baseUrl: String,
        model: String,
        force: Boolean,
        onToast: (String) -> Unit,
    ) {
        val normalizedBaseUrl = baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
        val validationError = validateBaseUrlSecurity(normalizedBaseUrl)
        if (validationError != null) {
            apiStatusText = stringRes(R.string.settings_api_unsafe)
            apiStatusPositive = false
            onToast(validationError)
            return
        }
        maybeWarnInsecureHttpBaseUrl(normalizedBaseUrl, onToast)
        remoteApiChecking = true
        remoteApiOk = null
        lastCheckedApiKey = key.trim()
        apiStatusText = stringRes(R.string.settings_api_checking)
        updateStatusText()

        val seq = ++apiCheckSeq
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    AutoGlmClient.checkApiDetailed(
                        apiKey = key.trim(),
                        baseUrl = normalizedBaseUrl,
                        model = model.ifBlank { AutoGlmClient.DEFAULT_MODEL },
                    )
                }
            if (seq != apiCheckSeq) return@launch

            remoteApiChecking = false
            remoteApiOk = result.ok
            apiStatusText =
                stringRes(
                    if (result.ok) {
                        R.string.settings_api_available
                    } else {
                        R.string.settings_api_failed
                    },
                )
            val secretsStored = prefs.writeApiConfig(
                apiKey = key.trim(),
                lastCheckKey = key.trim(),
                lastCheckOk = result.ok,
                lastCheckTime = System.currentTimeMillis(),
                lastCheckSig = apiConfigSignature(key.trim(), normalizedBaseUrl, model),
            )
            if (!secretsStored) {
                onToast(stringRes(R.string.settings_api_save_encryption_unavailable))
            }
            if (!result.ok && force) {
                onToast(formatApiCheckFailureReason(result.statusCode, result.message))
            }
            updateStatusText()
        }
    }

    fun updateQwenDownloadButtonState() {
        when {
            qwenDownloadInFlight -> {
                qwenButtonEnabled = false
                qwenButtonText = stringRes(R.string.m3t_sidebar_qwen_download_preparing)
            }

            localModelReady -> {
                qwenButtonEnabled = true
                qwenButtonText = stringRes(R.string.m3t_sidebar_qwen_download_ready)
            }

            else -> {
                qwenButtonEnabled = true
                qwenButtonText = stringRes(R.string.m3t_sidebar_qwen_download)
            }
        }
    }

    fun updateStatusText() {
        if (useAriesApi) {
            // 读缓存而不是 prefs.getActiveAriesApiKeyBlocking()：后者会在调用线程上做
            // AndroidKeyStore 解密，而本方法会被 Compose 点击回调直接调用。
            val hasAriesKey = activeAriesApiKey.isNotBlank()
            apiStatusPositive = remoteApiOk == true || (remoteApiOk == null && hasAriesKey)
            if (!remoteApiChecking) {
                apiStatusText =
                    when {
                        remoteApiOk == true -> stringRes(R.string.settings_api_available)
                        remoteApiOk == false -> stringRes(R.string.settings_api_failed)
                        hasAriesKey -> stringRes(R.string.settings_model_api_aries_ready)
                        else -> stringRes(R.string.settings_model_api_aries_login_required)
                    }
            }
        } else if (useLocalModel) {
            apiStatusPositive = localModelReady
            apiStatusText =
                stringRes(
                    if (localModelReady) {
                        R.string.m3t_sidebar_local_model_ready
                    } else {
                        R.string.m3t_sidebar_local_model_not_ready
                    },
                )
        } else {
            apiStatusPositive = remoteApiOk == true
            if (!remoteApiChecking && remoteApiOk == null) {
                apiStatusText = stringRes(R.string.m3t_sidebar_api_not_checked)
            }
        }
    }

    fun resolveApiKeyFromInput(): String {
        val displayed = apiInputText
        val tagKey = apiInputTag.trim()
        val savedKey = cachedApiKey.trim()
        return when {
            tagKey.isNotBlank() && displayed == maskKey(tagKey) -> tagKey
            savedKey.isNotBlank() && displayed == maskKey(savedKey) -> savedKey
            displayed.contains("*") && savedKey.isNotBlank() -> savedKey
            else -> displayed
        }.trim()
    }

    fun resolveApiBaseUrl(): String {
        if (useAriesApi) return AriesApiClient.ARIES_API_V1_BASE_URL
        if (useLocalModel) return AutoGlmClient.DEFAULT_BASE_URL
        return resolveRemoteApiBaseUrl()
    }

    fun resolveApiModel(): String {
        if (useAriesApi) return AriesApiClient.ARIES_CHAT_MODEL
        if (useLocalModel) return ModelScopeModelDownloader.QWEN35_MODEL_NAME
        return resolveRemoteApiModel()
    }

    private fun resolveRemoteApiBaseUrl(): String {
        if (!useThirdPartyApi) return AutoGlmClient.DEFAULT_BASE_URL
        return apiBaseUrlText.trim().ifBlank { AutoGlmClient.DEFAULT_BASE_URL }
    }

    private fun resolveRemoteApiModel(): String {
        if (!useThirdPartyApi) return AutoGlmClient.DEFAULT_MODEL
        return apiModelText.trim().ifBlank { AutoGlmClient.DEFAULT_MODEL }
    }

    fun maskKey(raw: String): String {
        if (raw.length <= 8) return raw
        return raw.take(4) + "*".repeat(raw.length - 8) + raw.takeLast(4)
    }

    fun formatApiCheckFailureReason(statusCode: Int?, message: String?): String {
        val cleanMessage = message?.trim().orEmpty()
        return when {
            statusCode != null && cleanMessage.isNotBlank() ->
                stringRes(R.string.settings_api_failed_http_message, statusCode, cleanMessage)

            statusCode != null ->
                stringRes(R.string.settings_api_failed_http, statusCode)

            cleanMessage.isNotBlank() ->
                stringRes(R.string.settings_api_failed_message, cleanMessage)

            else ->
                stringRes(R.string.settings_api_failed_generic)
        }
    }

    fun apiConfigSignature(apiKey: String, baseUrl: String, model: String): String {
        val mode =
            when {
                useAriesApi -> "aries"
                useThirdPartyApi -> "third_party"
                else -> "default"
            }
        // 与 MainActivity 共用同一规范：密钥材料经 SHA-256 哈希化，签名不含原始 Key
        return ApiConfigSignature.compute(
            apiKey = apiKey,
            baseUrl = baseUrl.ifBlank { AutoGlmClient.DEFAULT_BASE_URL },
            model = model.ifBlank { AutoGlmClient.DEFAULT_MODEL },
            mode = mode,
        )
    }

    fun validateBaseUrlSecurity(baseUrl: String): String? {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        val host = parsed?.host?.lowercase()
        if (scheme.isNullOrBlank() || host.isNullOrBlank()) {
            return stringRes(R.string.settings_api_invalid_url)
        }
        if (scheme != "https" && scheme != "http") {
            return stringRes(R.string.settings_api_invalid_scheme)
        }
        return null
    }

    fun maybeWarnInsecureHttpBaseUrl(baseUrl: String, onToast: (String) -> Unit) {
        val parsed = runCatching { Uri.parse(baseUrl.trim()) }.getOrNull() ?: return
        val scheme = parsed.scheme?.lowercase()
        val host = parsed.host?.lowercase()
        val localHosts = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")
        if (scheme == "http" && host !in localHosts) {
            onToast(stringRes(R.string.settings_api_http_warning))
        }
    }

    // ─── Aries SSO 登录 ────────────────────────────────────────────────────

    fun openAriesLoginDialog() {
        ariesLoginUsername = ""
        ariesLoginPassword = ""
        ariesLoginError = null
        showAriesLoginDialog = true
    }

    fun dismissAriesLoginDialog() {
        showAriesLoginDialog = false
        ariesLoginLoading = false
        ariesLoginError = null
    }

    fun onAriesLoginUsernameChange(value: String) {
        ariesLoginUsername = value
        ariesLoginError = null
    }

    fun onAriesLoginPasswordChange(value: String) {
        ariesLoginPassword = value
        ariesLoginError = null
    }

    fun submitAriesSsoLogin(activity: Activity, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        // 防重入：第二次点击会覆盖 AriesApiOAuthActivity.pendingSession，
        // 使第一次登录的协程永久挂起（ariesLoginLoading 也就再也不会复位）。
        if (ariesLoginLoading) return
        ariesLoginLoading = true
        ariesLoginError = null
        viewModelScope.launch {
            val result = ariesOidcAuthManager.signIn(activity)
            ariesLoginLoading = false
            if (result.success) {
                val tokenStored = prefs.setAriesApiKey(result.accessToken)
                if (!tokenStored) {
                    // 远端认证虽成功，但本机无法安全持久化 Token 时不得提交登录态。
                    // 主动清理临时会话，避免 UI 显示成功而重启后实际丢失凭据。
                    ariesOidcAuthManager.signOut()
                    val message = stringRes(R.string.settings_api_save_encryption_unavailable)
                    ariesLoginError = message
                    onError(message)
                    return@launch
                }
                val displayName = result.displayName.ifBlank { stringRes(R.string.aries_sso_user_display) }
                prefs.setAriesLoggedInUser(displayName)
                applyApiModeState(ApiMode.Aries)
                persistApiModeState(clearCheckResults = true)
                ariesLoggedInUser = displayName
                showAriesLoginDialog = false
                remoteApiOk = null
                remoteApiChecking = false
                lastCheckedApiKey = ""
                updateStatusText()
                onSuccess(stringRes(R.string.aries_login_success, displayName))
            } else {
                ariesLoginError = result.message.ifBlank { stringRes(R.string.aries_login_failed) }
                onError(ariesLoginError ?: "")
            }
        }
    }

    fun ariesLogout() {
        viewModelScope.launch {
            ariesOidcAuthManager.signOut()
            prefs.setAriesLoggedInUser("")
            prefs.setAriesApiKey("")
            ariesLoggedInUser = ""
            if (currentApiMode == ApiMode.Aries) {
                applyApiModeState(ApiMode.Official)
                persistApiModeState(clearCheckResults = true)
                remoteApiOk = null
                remoteApiChecking = false
                lastCheckedApiKey = ""
            }
            updateStatusText()
        }
    }

    // ─── Aries 模型选择 ────────────────────────────────────────────────

    /**
     * 打开 Aries 模型选择对话框。
     *
     * 关键点（修复「添加模型时卡死/点了没反应」）：
     * 1. 不在调用线程（Compose 点击回调 = 主线程）做 `getActiveAriesApiKeyBlocking()`，
     *    该调用包含 AndroidKeyStore 解密，会直接卡住一帧甚至更久；
     * 2. 立即打开对话框展示加载态，避免网络慢时界面毫无反馈；
     * 3. 失败/列表为空时给出可见错误与重试入口，而不是静默 return。
     */
    fun openAriesModelSelectionDialog(onToast: (String) -> Unit = {}) {
        if (ariesAvailableModels.isNotEmpty()) {
            ariesModelError = null
            showAriesModelDialog = true
            return
        }
        if (ariesModelLoading) return
        ariesModelLoading = true
        ariesModelError = null
        showAriesModelDialog = true
        viewModelScope.launch {
            val key = withContext(Dispatchers.IO) { prefs.getActiveAriesApiKey() }.trim()
            if (key.isBlank()) {
                ariesModelLoading = false
                val message = stringRes(R.string.settings_model_api_aries_login_required)
                ariesModelError = message
                onToast(message)
                return@launch
            }
            val result = withContext(Dispatchers.IO) { ariesApiClient.fetchModels(key) }
            ariesModelLoading = false
            result
                .onSuccess { models ->
                    ariesAvailableModels = models
                    if (models.isEmpty()) {
                        val message = stringRes(R.string.aries_model_list_empty)
                        ariesModelError = message
                        onToast(message)
                    } else {
                        ariesModelError = null
                    }
                }
                .onFailure { err ->
                    val message =
                        err.message?.trim()?.takeIf { it.isNotBlank() }
                            ?: stringRes(R.string.aries_model_list_failed)
                    ariesModelError = message
                    onToast(message)
                }
        }
    }

    fun retryAriesModelSelection(onToast: (String) -> Unit = {}) {
        ariesAvailableModels = emptyList()
        ariesModelError = null
        openAriesModelSelectionDialog(onToast)
    }

    fun dismissAriesModelDialog() {
        showAriesModelDialog = false
        ariesModelError = null
    }

    fun selectAriesModel(modelId: String) {
        ariesSelectedModel = modelId
        showAriesModelDialog = false
        viewModelScope.launch { prefs.setAriesSelectedModel(modelId) }
        updateStatusText()
    }

    private fun resolveApiMode(
        useThirdParty: Boolean = useThirdPartyApi,
        useLocal: Boolean = useLocalModel,
        useAries: Boolean = useAriesApi,
    ): ApiMode =
        when {
            useAries -> ApiMode.Aries
            useLocal -> ApiMode.Local
            useThirdParty -> ApiMode.ThirdParty
            else -> ApiMode.Official
        }

    private fun applyApiModeState(mode: ApiMode) {
        currentApiMode = mode
        useThirdPartyApi = mode == ApiMode.ThirdParty
        useLocalModel = mode == ApiMode.Local
        useAriesApi = mode == ApiMode.Aries
    }

    private fun normalizePersistedApiModeIfNeeded(
        restoredThirdParty: Boolean,
        restoredLocal: Boolean,
        restoredAries: Boolean,
    ) {
        if (
            restoredThirdParty == useThirdPartyApi &&
            restoredLocal == useLocalModel &&
            restoredAries == useAriesApi
        ) {
            return
        }
        persistApiMode(clearCheckResults = false)
    }

    private fun persistApiMode(clearCheckResults: Boolean) {
        apiModePersistJob?.cancel()
        apiModePersistJob = viewModelScope.launch {
            persistApiModeState(clearCheckResults = clearCheckResults)
        }
    }

    private suspend fun persistApiModeState(clearCheckResults: Boolean) {
        prefs.writeApiConfig(
            useThirdParty = useThirdPartyApi,
            useLocalModel = useLocalModel,
            clearCheckResults = clearCheckResults,
        )
        prefs.setUseAriesApi(useAriesApi)
    }

    private fun stringRes(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
