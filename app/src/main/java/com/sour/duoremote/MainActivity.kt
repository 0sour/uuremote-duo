package com.sour.duoremote

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.sour.duoremote.data.DuoSettings
import com.sour.duoremote.data.SecureStore
import com.sour.duoremote.data.SettingsStore
import com.sour.duoremote.protocol.api.UuApiClient
import com.sour.duoremote.protocol.file.FileEntry
import com.sour.duoremote.protocol.proto.ConnectOptions
import com.sour.duoremote.protocol.rtc.RtcRuntime
import com.sour.duoremote.session.FileTransferState
import com.sour.duoremote.session.SessionController
import com.sour.duoremote.session.SessionKeepAliveService
import com.sour.duoremote.ui.AppRoute
import com.sour.duoremote.ui.components.GamepadHelpOverlay
import com.sour.duoremote.ui.controlcenter.ControlCenterScreen
import com.sour.duoremote.ui.controlcenter.ControlCenterState
import com.sour.duoremote.ui.controlcenter.ControlField
import com.sour.duoremote.ui.controlcenter.controlRows
import com.sour.duoremote.ui.files.FilesScreen
import com.sour.duoremote.ui.files.FilesTab
import com.sour.duoremote.ui.files.FilesTopPreview
import com.sour.duoremote.ui.files.FilesUiState
import com.sour.duoremote.ui.files.LocalEntry
import com.sour.duoremote.ui.files.LocalFileAccess
import com.sour.duoremote.ui.files.LocalFilesUiState
import com.sour.duoremote.ui.files.LocalLocation
import com.sour.duoremote.ui.files.LocalPickerMode
import com.sour.duoremote.ui.files.LocalPickerScreen
import com.sour.duoremote.ui.files.LocalRootKind
import com.sour.duoremote.ui.files.PendingDownload
import com.sour.duoremote.ui.home.BlackHost
import com.sour.duoremote.ui.home.DemoDevice
import com.sour.duoremote.ui.home.DualBottomScreen
import com.sour.duoremote.ui.home.EmptyDevice
import com.sour.duoremote.ui.home.HomeFocus
import com.sour.duoremote.ui.home.HomeUiState
import com.sour.duoremote.ui.home.SingleHomeScreen
import com.sour.duoremote.ui.home.TopDetailScreen
import com.sour.duoremote.ui.host.DuoPresentation
import com.sour.duoremote.ui.host.HostCoordinator
import com.sour.duoremote.ui.host.HostLayout
import com.sour.duoremote.ui.host.ScreenMode
import com.sour.duoremote.ui.host.SingleHost
import com.sour.duoremote.ui.input.GamepadMapper
import com.sour.duoremote.ui.input.InputRouter
import com.sour.duoremote.ui.screens.account.AccountSingleScreen
import com.sour.duoremote.ui.screens.account.AccountTopPreview
import com.sour.duoremote.ui.screens.keysetup.KeySetupScreen
import com.sour.duoremote.ui.screens.login.LoginBottomPane
import com.sour.duoremote.ui.screens.login.LoginController
import com.sour.duoremote.ui.screens.login.LoginMode
import com.sour.duoremote.ui.screens.login.LoginSingleScreen
import com.sour.duoremote.ui.screens.login.LoginTopPane
import com.sour.duoremote.ui.settings.SettingsBottomScreen
import com.sour.duoremote.ui.settings.SettingsCategory
import com.sour.duoremote.ui.settings.SettingsSingleScreen
import com.sour.duoremote.ui.settings.SettingsTopPreview
import com.sour.duoremote.ui.settings.SettingsUiState
import com.sour.duoremote.ui.settings.settingsRows
import com.sour.duoremote.ui.streaming.DualStreamingBottom
import com.sour.duoremote.ui.streaming.ImeMode
import com.sour.duoremote.ui.streaming.SingleStreamingScreen
import com.sour.duoremote.ui.streaming.StreamingUiState
import com.sour.duoremote.ui.streaming.StreamingVideoTop
import com.sour.duoremote.ui.streaming.ToolTab
import com.sour.duoremote.ui.streaming.activateToolkitBody
import com.sour.duoremote.ui.theme.DuoRemoteTheme
import com.sour.duoremote.ui.theme.LocalDuoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private enum class Stage { KEY_SETUP, LOGIN, HOME }

    private val host = HostCoordinator()
    private val inputRouter = InputRouter()
    private val streamingUi = StreamingUiState()
    private val homeUi = HomeUiState()
    private val settingsUi = SettingsUiState()
    private val controlCenter = ControlCenterState()
    private val filesUi = FilesUiState()
    private val localFilesUi = LocalFilesUiState()
    private val selectedId = mutableStateOf("")
    private var duoSettings by mutableStateOf(DuoSettings())

    private val folderGrantLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> onFolderGrantResult(uri) }

    private lateinit var secure: SecureStore
    private lateinit var settingsStore: SettingsStore
    private var hasKey by mutableStateOf(false)
    private var hasToken by mutableStateOf(false)
    private var api by mutableStateOf<UuApiClient?>(null)
    private var loginController by mutableStateOf<LoginController?>(null)
    private var sessionController by mutableStateOf<SessionController?>(null)
    private var activeDeviceId by mutableStateOf<String?>(null)
    private var activeDeviceName by mutableStateOf<String?>(null)
    private var route by mutableStateOf<AppRoute>(AppRoute.Home)
    private var settingsReturnRoute by mutableStateOf<AppRoute>(AppRoute.Home)
    private var filesReturnRoute by mutableStateOf<AppRoute>(AppRoute.Home)
    private var showHelp by mutableStateOf(false)
    private var accountName by mutableStateOf("本机账号")
    private var accountIndex by mutableStateOf(0)
    private var accountConfirmLogout by mutableStateOf(false)
    private var devices by mutableStateOf<List<DemoDevice>>(emptyList())
    private var deviceError by mutableStateOf<String?>(null)
    private var devicesLoading by mutableStateOf(false)
    private var homeInitialFocusApplied = false

    private lateinit var displayManager: DisplayManager
    private var presentation: DuoPresentation? = null

    private val effectiveGamepadPassthrough: Boolean
        get() = duoSettings.gamepadPassthrough && !duoSettings.viewOnly

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refreshDisplays()

        override fun onDisplayRemoved(displayId: Int) = refreshDisplays()

        override fun onDisplayChanged(displayId: Int) = refreshDisplays()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        secure = SecureStore(this)
        settingsStore = SettingsStore(this)
        hasKey = secure.hasSigningKey()
        hasToken = !secure.token().isNullOrBlank()

        lifecycleScope.launch {
            settingsStore.settings.collect { saved ->
                duoSettings = saved
                host.screenMode = saved.screenMode
                host.singleHost = saved.singleHost
                inputRouter.style = saved.gamepadStyle
                sessionController?.configure(
                    gamepadPassthrough = saved.gamepadPassthrough,
                    gamepadVibration = saved.vibration,
                    autoReconnect = saved.autoReconnect,
                    clipboardSync = saved.clipboardSync,
                    gamepadDeadzone = saved.gamepadDeadzone,
                    invertLeftY = saved.invertLeftY,
                    invertRightY = saved.invertRightY,
                    viewOnly = saved.viewOnly,
                )
            }
        }

        displayManager = getSystemService(DisplayManager::class.java)
        refreshDisplays()
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))

        // Compose BackHandlers in the active page win first; this callback covers read-only top
        // panes (account/login/top detail) and the bottom Presentation's fallback path.
        onBackPressedDispatcher.addCallback(this) {
            handleSystemBack()
        }

        setContent {
            DuoRemoteTheme(
                theme = duoSettings.theme,
                focusRingStyle = duoSettings.focusRingStyle,
                hapticFeedback = duoSettings.hapticFeedback,
                soundFeedback = duoSettings.soundFeedback,
            ) {
                val stage = when {
                    !hasKey -> Stage.KEY_SETUP
                    !hasToken -> Stage.LOGIN
                    else -> Stage.HOME
                }
                val layout = host.layout

                val fileEntriesSize = sessionController?.fileState?.entries?.size ?: 0
                LaunchedEffect(fileEntriesSize) {
                    filesUi.clamp(fileEntriesSize)
                }

                LaunchedEffect(
                    streamingUi.localMenu,
                    streamingUi.imeMode,
                    route,
                    showHelp,
                ) {
                    val suppressed = streamingUi.localMenu ||
                        streamingUi.imeMode != ImeMode.HIDDEN ||
                        route != AppRoute.Home ||
                        showHelp
                    if (suppressed) streamingUi.dragLock = false
                    sessionController?.setInputSuppressed(suppressed)
                }

                LaunchedEffect(hasKey) {
                    if (hasKey && api == null) {
                        val key = withContext(Dispatchers.IO) { secure.signingKey() }
                        if (key == null) {
                            hasKey = false
                        } else {
                            val client = UuApiClient(secure, key)
                            api = client
                            loginController?.dispose()
                            loginController = LoginController(
                                api = client,
                                scope = this@MainActivity.lifecycleScope,
                                onLoggedIn = { hasToken = true },
                            )
                        }
                    }
                }

                LaunchedEffect(stage, route, layout, host.hasSecondaryDisplay, api, loginController) {
                    syncPresentation(stage, layout)
                }

                LaunchedEffect(hasToken, api) {
                    val client = api
                    if (hasToken && client != null) {
                        if (sessionController == null) {
                            sessionController = SessionController(
                                context = this@MainActivity,
                                api = client,
                                scope = this@MainActivity.lifecycleScope,
                                eglContext = RtcRuntime.eglBase.eglBaseContext,
                            ).also {
                                it.configure(
                                    gamepadPassthrough = duoSettings.gamepadPassthrough,
                                    gamepadVibration = duoSettings.vibration,
                                    autoReconnect = duoSettings.autoReconnect,
                                    clipboardSync = duoSettings.clipboardSync,
                                    gamepadDeadzone = duoSettings.gamepadDeadzone,
                                    invertLeftY = duoSettings.invertLeftY,
                                    invertRightY = duoSettings.invertRightY,
                                    viewOnly = duoSettings.viewOnly,
                                )
                            }
                        }
                        loadDevices()
                        loadAccountName(client)
                    }
                }

                LaunchedEffect(devices) {
                    if (devices.none { it.id == selectedId.value }) {
                        selectedId.value = devices.firstOrNull()?.id ?: ""
                    }
                    if (!homeInitialFocusApplied) {
                        homeUi.reset(devices.isNotEmpty())
                        homeInitialFocusApplied = true
                    } else if (devices.isEmpty()) {
                        homeUi.toActions()
                    }
                }

                LaunchedEffect(route, sessionController?.videoTrack) {
                    if ((route == AppRoute.ControlCenter || route == AppRoute.Files) &&
                        sessionController?.videoTrack == null
                    ) {
                        route = AppRoute.Home
                        filesReturnRoute = AppRoute.Home
                        localFilesUi.close()
                        streamingUi.localMenu = false
                    }
                }

                LaunchedEffect(duoSettings.viewOnly) {
                    if (duoSettings.viewOnly) {
                        sessionController?.cancelFileTransfer()
                        localFilesUi.close()
                        if (route == AppRoute.Files) closeFiles()
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    ActivityContent(stage, layout)
                    // DUAL keeps the top read-only, so help belongs to the bottom pane only.
                    if (showHelp && layout == HostLayout.SINGLE_TOP) {
                        GamepadHelpOverlay(onClose = { showHelp = false })
                    }
                }
            }
        }
    }

    @Composable
    private fun ActivityContent(stage: Stage, layout: HostLayout) {
        when (stage) {
            Stage.KEY_SETUP -> KeySetupScreen(
                secure = secure,
                onReady = { hasKey = true },
                onExit = { finish() },
            )

            Stage.LOGIN -> {
                val controller = loginController
                when {
                    api == null || controller == null -> LoadingScreen("正在准备登录…")

                    layout == HostLayout.DUAL -> LoginTopPane(controller = controller)

                    layout == HostLayout.SINGLE_TOP -> LoginSingleScreen(
                        controller = controller,
                        onBack = ::handleLoginBack,
                    )

                    else -> BlackHost(modifier = Modifier.fillMaxSize())
                }
            }

            Stage.HOME -> when (route) {
                AppRoute.Settings -> when (layout) {
                    HostLayout.DUAL -> SettingsTopPreview(
                        settings = duoSettings,
                        onBack = ::closeSettings,
                        modifier = Modifier.fillMaxSize(),
                    )

                    HostLayout.SINGLE_TOP -> SettingsSingleScreen(
                        settings = duoSettings,
                        uiState = settingsUi,
                        onChange = ::updateSettings,
                        onBack = ::closeSettings,
                        modifier = Modifier.fillMaxSize(),
                    )

                    HostLayout.SINGLE_BOTTOM -> BlackHost(modifier = Modifier.fillMaxSize())
                }

                AppRoute.Account -> {
                    when (layout) {
                        HostLayout.DUAL -> AccountTopPreview(
                            accountName = accountName,
                            deviceCount = devices.size,
                            onlineCount = devices.count { it.online },
                            version = "v${BuildConfig.VERSION_NAME}",
                            status = homeStatus(),
                            modifier = Modifier.fillMaxSize(),
                        )

                        HostLayout.SINGLE_TOP -> AccountSingleScreen(
                            accountName = accountName,
                            deviceCount = devices.size,
                            onlineCount = devices.count { it.online },
                            version = "v${BuildConfig.VERSION_NAME}",
                            selectedIndex = accountIndex,
                            confirmLogout = accountConfirmLogout,
                            status = homeStatus(),
                            onRefresh = ::accountRefresh,
                            onLogout = ::accountLogout,
                            onBack = ::accountBack,
                            modifier = Modifier.fillMaxSize(),
                        )

                        HostLayout.SINGLE_BOTTOM -> BlackHost(modifier = Modifier.fillMaxSize())
                    }
                }

                AppRoute.ControlCenter -> {
                    val video = sessionController?.videoTrack
                    val client = sessionController
                    val selected = selectedDevice() ?: EmptyDevice
                    when {
                        video != null && client != null && layout == HostLayout.DUAL -> StreamingVideoTop(
                            track = video,
                            title = activeDeviceName ?: selected.name,
                            status = client.message,
                            stats = client.stats,
                            session = client,
                            onMenu = ::closeControlCenter,
                            modifier = Modifier.fillMaxSize(),
                            diagnostics = duoSettings.diagnostics,
                            directTouch = duoSettings.directTouch,
                            onRemoteCursor = streamingUi::moveCursorTo,
                            viewOnly = duoSettings.viewOnly,
                            videoScale = duoSettings.videoScale,
                        )

                        video != null && client != null && layout == HostLayout.SINGLE_TOP -> ControlCenterScreen(
                            state = controlCenter,
                            settings = duoSettings,
                            onAdjust = ::adjustControlCenter,
                            onApply = ::applyControlCenter,
                            onCancel = ::cancelControlCenter,
                            onClose = ::closeControlCenter,
                            onOpenHelp = { showHelp = true },
                            modifier = Modifier.fillMaxSize(),
                        )

                        layout == HostLayout.DUAL -> TopDetailScreen(
                            device = selected,
                            onlineCount = devices.count { it.online },
                            preferenceSummary = preferenceSummary(),
                            sessionStatus = homeStatus(),
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> BlackHost(modifier = Modifier.fillMaxSize())
                    }
                }

                AppRoute.Files -> {
                    val video = sessionController?.videoTrack
                    val client = sessionController
                    when {
                        video != null && client != null && layout == HostLayout.DUAL -> FilesTopPreview(
                            state = client.fileState,
                            modifier = Modifier.fillMaxSize(),
                        )

                        video != null && client != null && layout == HostLayout.SINGLE_TOP ->
                            FilesRouteContent(client, Modifier.fillMaxSize())

                        else -> BlackHost(modifier = Modifier.fillMaxSize())
                    }
                }

                AppRoute.Home -> {
                    val selected = selectedDevice() ?: EmptyDevice
                    val video = sessionController?.videoTrack
                    val client = sessionController
                    when {
                        video != null && client != null && layout == HostLayout.DUAL -> StreamingVideoTop(
                            track = video,
                            title = activeDeviceName ?: selected.name,
                            status = client.message,
                            stats = client.stats,
                            session = client,
                            onMenu = { streamingUi.localMenu = true },
                            modifier = Modifier.fillMaxSize(),
                            diagnostics = duoSettings.diagnostics,
                            directTouch = duoSettings.directTouch,
                            onRemoteCursor = streamingUi::moveCursorTo,
                            viewOnly = duoSettings.viewOnly,
                            videoScale = duoSettings.videoScale,
                        )

                        video != null && client != null && layout == HostLayout.SINGLE_TOP -> SingleStreamingScreen(
                            track = video,
                            title = activeDeviceName ?: selected.name,
                            status = client.message,
                            stats = client.stats,
                            ui = streamingUi,
                            settings = duoSettings,
                            session = client,
                            gamepadPassthrough = effectiveGamepadPassthrough,
                            onToggleGamepad = ::toggleGamepadPassthrough,
                            onOpenSettings = { openSettings() },
                            onOpenControlCenter = ::openControlCenter,
                            onDisconnect = ::disconnectSession,
                            onReconnect = ::reconnectSession,
                            onBackToList = ::disconnectSession,
                            modifier = Modifier.fillMaxSize(),
                        )

                        layout == HostLayout.DUAL -> TopDetailScreen(
                            device = selected,
                            onlineCount = devices.count { it.online },
                            preferenceSummary = preferenceSummary(),
                            sessionStatus = homeStatus(),
                            modifier = Modifier.fillMaxSize(),
                        )

                        layout == HostLayout.SINGLE_BOTTOM -> BlackHost(modifier = Modifier.fillMaxSize())

                        else -> SingleHomeScreen(
                            devices = devices,
                            selectedId = selectedId.value,
                            hostLabel = host.label,
                            homeUi = homeUi,
                            onSelect = { selectedId.value = it.id },
                            onConfirm = ::connectSession,
                            onRefresh = { refreshDevices() },
                            onAccount = ::openAccount,
                            onOpenSettings = { openSettings() },
                            onOpenPreferences = ::openPreferences,
                            onBack = ::handleBack,
                            preferenceSummary = preferenceSummary(),
                            sessionStatus = homeStatus(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    /**
     * The remote file page and the in-app local picker share one host slot. Keeping this in one
     * function ensures the Activity and the bottom Presentation render the same local state.
     */
    @Composable
    private fun FilesRouteContent(client: SessionController, modifier: Modifier = Modifier) {
        if (localFilesUi.visible) {
            LocalPickerScreen(
                state = localFilesUi,
                onBack = ::localBack,
                onSelectRoot = ::selectLocalRoot,
                onGrantFolder = ::grantLocalFolder,
                onRevokeFolder = ::revokeLocalFolder,
                onOpen = ::openLocalEntry,
                onUp = ::localBack,
                onRefresh = ::refreshLocal,
                onSelect = localFilesUi::select,
                onConfirm = ::confirmLocalPicker,
                modifier = modifier,
            )
        } else {
            FilesScreen(
                state = client.fileState,
                ui = filesUi,
                onList = { path ->
                    client.listFiles(path)
                    filesUi.select(0)
                },
                onCancel = { client.cancelFileTransfer() },
                onClose = ::closeFiles,
                onUpload = ::openLocalUpload,
                onDownload = ::openLocalDownload,
                modifier = modifier,
            )
        }
    }

    @Composable
    private fun PresentationContent(stage: Stage, layout: HostLayout) {
        val video = sessionController?.videoTrack
        val client = sessionController
        when (stage) {
            Stage.LOGIN -> {
                val controller = loginController
                when {
                    controller == null -> LoadingScreen("正在准备登录…")
                    layout == HostLayout.DUAL -> LoginBottomPane(
                        controller = controller,
                        onBack = ::handleLoginBack,
                    )

                    else -> LoginSingleScreen(
                        controller = controller,
                        onBack = ::handleLoginBack,
                    )
                }
            }

            Stage.HOME -> when (route) {
                AppRoute.Settings -> if (layout == HostLayout.DUAL) {
                    SettingsBottomScreen(
                        settings = duoSettings,
                        uiState = settingsUi,
                        onChange = ::updateSettings,
                        onBack = ::closeSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SettingsSingleScreen(
                        settings = duoSettings,
                        uiState = settingsUi,
                        onChange = ::updateSettings,
                        onBack = ::closeSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                AppRoute.Account -> AccountSingleScreen(
                    accountName = accountName,
                    deviceCount = devices.size,
                    onlineCount = devices.count { it.online },
                    version = "v${BuildConfig.VERSION_NAME}",
                    selectedIndex = accountIndex,
                    confirmLogout = accountConfirmLogout,
                    status = homeStatus(),
                    onRefresh = ::accountRefresh,
                    onLogout = ::accountLogout,
                    onBack = ::accountBack,
                    modifier = Modifier.fillMaxSize(),
                )

                AppRoute.ControlCenter -> if (video != null && client != null) {
                    ControlCenterScreen(
                        state = controlCenter,
                        settings = duoSettings,
                        onAdjust = ::adjustControlCenter,
                        onApply = ::applyControlCenter,
                        onCancel = ::cancelControlCenter,
                        onClose = ::closeControlCenter,
                        onOpenHelp = { showHelp = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BlackHost(modifier = Modifier.fillMaxSize())
                }

                AppRoute.Files -> if (video != null && client != null) {
                    FilesRouteContent(client, Modifier.fillMaxSize())
                } else {
                    BlackHost(modifier = Modifier.fillMaxSize())
                }

                AppRoute.Home -> when {
                    video != null && client != null && layout == HostLayout.DUAL -> DualStreamingBottom(
                        title = activeDeviceName ?: "正在串流",
                        status = client.message,
                        stats = client.stats,
                        ui = streamingUi,
                        settings = duoSettings,
                        session = client,
                        gamepadPassthrough = effectiveGamepadPassthrough,
                        onToggleGamepad = ::toggleGamepadPassthrough,
                        onOpenSettings = { openSettings() },
                        onOpenControlCenter = ::openControlCenter,
                        onDisconnect = ::disconnectSession,
                        onReconnect = ::reconnectSession,
                        onBackToList = ::disconnectSession,
                        modifier = Modifier.fillMaxSize(),
                    )

                    video != null && client != null -> SingleStreamingScreen(
                        track = video,
                        title = activeDeviceName ?: "正在串流",
                        status = client.message,
                        stats = client.stats,
                        ui = streamingUi,
                        settings = duoSettings,
                        session = client,
                        gamepadPassthrough = effectiveGamepadPassthrough,
                        onToggleGamepad = ::toggleGamepadPassthrough,
                        onOpenSettings = { openSettings() },
                        onOpenControlCenter = ::openControlCenter,
                        onDisconnect = ::disconnectSession,
                        onReconnect = ::reconnectSession,
                        onBackToList = ::disconnectSession,
                        modifier = Modifier.fillMaxSize(),
                    )

                    layout == HostLayout.DUAL -> DualBottomScreen(
                        devices = devices,
                        selectedId = selectedId.value,
                        homeUi = homeUi,
                        onSelect = { selectedId.value = it.id },
                        onConfirm = ::connectSession,
                        onRefresh = { refreshDevices() },
                        onAccount = ::openAccount,
                        onOpenSettings = { openSettings() },
                        onOpenPreferences = ::openPreferences,
                        onBack = ::handleBack,
                        preferenceSummary = preferenceSummary(),
                        sessionStatus = homeStatus(),
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> SingleHomeScreen(
                        devices = devices,
                        selectedId = selectedId.value,
                        hostLabel = host.label,
                        homeUi = homeUi,
                        onSelect = { selectedId.value = it.id },
                        onConfirm = ::connectSession,
                        onRefresh = { refreshDevices() },
                        onAccount = ::openAccount,
                        onOpenSettings = { openSettings() },
                        onOpenPreferences = ::openPreferences,
                        onBack = ::handleBack,
                        preferenceSummary = preferenceSummary(),
                        sessionStatus = homeStatus(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Stage.KEY_SETUP -> BlackHost(modifier = Modifier.fillMaxSize())
        }
    }

    private fun setPickerKeepAlive(enabled: Boolean) {
        val intent = Intent(this, SessionKeepAliveService::class.java)
        runCatching {
            if (enabled) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                stopService(intent)
            }
        }
    }

    private fun onFolderGrantResult(uri: Uri?) {
        setPickerKeepAlive(false)
        if (uri == null) {
            if (localFilesUi.visible && localFilesUi.authPrompt) {
                localFilesUi.showAuthPrompt("未授权文件夹，仍可使用应用目录")
            }
            return
        }
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val granted = runCatching {
            contentResolver.takePersistableUriPermission(uri, readWrite)
        }.isSuccess
        if (!granted) {
            val readOnly = runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (localFilesUi.visible) {
                localFilesUi.showAuthPrompt(
                    if (readOnly) {
                        "该文件夹只授予了读取权限，请重新选择并允许编辑"
                    } else {
                        "无法保存文件夹授权，请重试"
                    },
                )
            }
            return
        }
        updateSettings(duoSettings.copy(localTreeUri = uri.toString()))
        localFilesUi.updateTreeAvailable(true)
        if (localFilesUi.visible) showAuthorizedRoot()
    }

    private fun grantLocalFolder() {
        if (!localFilesUi.visible) return
        setPickerKeepAlive(true)
        runCatching { folderGrantLauncher.launch(null) }
            .onFailure {
                setPickerKeepAlive(false)
                localFilesUi.reportError("无法打开系统授权页")
            }
    }

    private fun revokeLocalFolder() {
        val raw = duoSettings.localTreeUri.trim()
        if (raw.isNotEmpty()) {
            val uri = runCatching { Uri.parse(raw) }.getOrNull()
            if (uri != null) {
                val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                if (runCatching {
                        contentResolver.releasePersistableUriPermission(uri, readWrite)
                    }.isFailure
                ) {
                    runCatching {
                        contentResolver.releasePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
        }
        updateSettings(duoSettings.copy(localTreeUri = ""))
        localFilesUi.updateTreeAvailable(false)
        showAppRoot()
        localFilesUi.note("已撤销文件夹授权，当前使用应用目录")
    }

    private fun authorizedTreeUri(): Uri? {
        val raw = duoSettings.localTreeUri.trim()
        if (raw.isEmpty()) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        return uri.takeIf { LocalFileAccess.hasTreePermission(this, it) }
    }

    private fun showAppRoot() {
        localFilesUi.updateTreeAvailable(authorizedTreeUri() != null)
        val root = LocalFileAccess.appRoot(this)
        localFilesUi.beginAt(root)
        loadLocal(root)
    }

    private fun showAuthorizedRoot() {
        val uri = authorizedTreeUri()
        if (uri == null) {
            localFilesUi.updateTreeAvailable(false)
            localFilesUi.showAuthPrompt("尚未授权文件夹，或授权已被系统收回")
            return
        }
        val root = LocalFileAccess.authorizedRoot(this, uri)
        if (root == null) {
            localFilesUi.updateTreeAvailable(false)
            localFilesUi.showAuthPrompt("无法读取该文件夹，请重新授权")
            return
        }
        localFilesUi.updateTreeAvailable(true)
        localFilesUi.beginAt(root)
        loadLocal(root)
    }

    private fun selectLocalRoot(root: LocalRootKind) {
        when (root) {
            LocalRootKind.APP -> showAppRoot()
            LocalRootKind.AUTHORIZED -> showAuthorizedRoot()
        }
    }

    private fun loadLocal(location: LocalLocation) {
        val token = localFilesUi.loadToken
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { LocalFileAccess.list(this@MainActivity, location) }
            }
            result.onSuccess { entries ->
                localFilesUi.loaded(token, location, entries)
            }.onFailure { error ->
                if (location.root == LocalRootKind.AUTHORIZED) {
                    localFilesUi.updateTreeAvailable(false)
                }
                localFilesUi.failed(
                    token,
                    "无法读取文件夹：${error.message ?: "权限或存储不可用"}",
                )
            }
        }
    }

    private fun openLocalEntry(entry: LocalEntry) {
        if (!entry.isDirectory) return
        val current = localFilesUi.current ?: return
        val next = when (current.root) {
            LocalRootKind.APP -> LocalLocation(
                root = LocalRootKind.APP,
                label = entry.name,
                path = entry.path,
            )

            LocalRootKind.AUTHORIZED -> {
                val documentId = entry.documentId
                if (documentId == null) {
                    localFilesUi.reportError("无法打开该文件夹")
                    return
                }
                LocalLocation(
                    root = LocalRootKind.AUTHORIZED,
                    label = entry.name,
                    path = entry.path,
                    treeUri = current.treeUri,
                    documentId = documentId,
                )
            }
        }
        localFilesUi.push(next)
        loadLocal(next)
    }

    private fun localBack() {
        if (!localFilesUi.visible) return
        if (localFilesUi.authPrompt) {
            localFilesUi.close()
            return
        }
        val target = localFilesUi.pop()
        if (target == null) {
            localFilesUi.close()
        } else {
            loadLocal(target)
        }
    }

    private fun refreshLocal() {
        if (!localFilesUi.visible || localFilesUi.authPrompt) return
        val current = localFilesUi.current ?: return
        localFilesUi.retry()
        loadLocal(current)
    }

    private fun openLocalUpload() {
        val client = sessionController ?: return
        if (duoSettings.viewOnly) return
        filesUi.error = null
        localFilesUi.open(
            mode = LocalPickerMode.UPLOAD,
            remoteDir = client.fileState.path,
            pending = null,
        )
        if (authorizedTreeUri() != null) showAuthorizedRoot() else showAppRoot()
    }

    private fun openLocalDownload(entry: FileEntry) {
        val client = sessionController ?: return
        if (duoSettings.viewOnly) return
        if (entry.isDirectory) {
            filesUi.error = "文件夹下载暂未支持"
            return
        }
        val remotePath = entry.fullPath.ifBlank {
            FileTransferState.childOf(client.fileState.path, entry.name)
        }
        filesUi.error = null
        localFilesUi.open(
            mode = LocalPickerMode.DOWNLOAD,
            remoteDir = client.fileState.path,
            pending = PendingDownload(entry.name, remotePath, entry.size),
        )
        if (authorizedTreeUri() != null) showAuthorizedRoot() else showAppRoot()
    }

    private fun confirmLocalPicker() {
        when (localFilesUi.mode) {
            LocalPickerMode.UPLOAD -> confirmLocalUpload()
            LocalPickerMode.DOWNLOAD -> confirmLocalDownload()
        }
    }

    private fun confirmLocalUpload() {
        val client = sessionController
        if (client == null || duoSettings.viewOnly) {
            localFilesUi.reportError("会话未连接")
            return
        }
        val entry = localFilesUi.selected
        if (entry == null || entry.isDirectory) {
            localFilesUi.reportError("请选择一个文件")
            return
        }
        val size = LocalFileAccess.sizeOf(this, entry)
        if (size < 0L) {
            localFilesUi.reportError("无法读取文件大小")
            return
        }
        val input = LocalFileAccess.openInput(this, entry)
        if (input == null) {
            localFilesUi.reportError("无法打开该文件")
            return
        }
        val remoteDir = localFilesUi.remoteDir
        localFilesUi.close()
        filesUi.error = null
        filesUi.tab = FilesTab.QUEUE
        val started = client.uploadFile(
            name = entry.name,
            size = size,
            modifiedTime = entry.modifiedTime,
            input = input,
            remoteDir = remoteDir,
        )
        if (!started) filesUi.error = client.fileState.error ?: "无法开始上传"
    }

    private fun confirmLocalDownload() {
        val client = sessionController
        if (client == null || duoSettings.viewOnly) {
            localFilesUi.reportError("会话未连接")
            return
        }
        val pending = localFilesUi.pendingDownload
        if (pending == null) {
            localFilesUi.reportError("没有待下载文件")
            return
        }
        val location = localFilesUi.current
        if (location == null) {
            localFilesUi.reportError("请选择保存文件夹")
            return
        }
        val output = LocalFileAccess.createOutput(this, location, pending.name)
        if (output == null) {
            localFilesUi.reportError("无法在此文件夹创建文件")
            return
        }
        val started = client.downloadFile(
            remotePath = pending.remotePath,
            name = pending.name,
            size = pending.size,
            output = output.stream,
        )
        if (!started) {
            output.delete()
            localFilesUi.reportError(client.fileState.error ?: "无法开始下载")
            return
        }
        localFilesUi.close()
        filesUi.error = null
        filesUi.tab = FilesTab.QUEUE
    }

    private fun connectSession(device: DemoDevice) {
        activeDeviceId = device.id
        activeDeviceName = device.name
        route = AppRoute.Home
        showHelp = false
        streamingUi.resetForNewSession()
        controlCenter.close()
        sessionController?.connect(device.id, duoSettings.toConnectOptions())
    }

    private fun disconnectSession() {
        sessionController?.disconnect()
        activeDeviceId = null
        activeDeviceName = null
        route = AppRoute.Home
        settingsReturnRoute = AppRoute.Home
        filesReturnRoute = AppRoute.Home
        filesUi.reset()
        localFilesUi.close()
        showHelp = false
        streamingUi.resetForNewSession()
        controlCenter.close()
    }

    private fun reconnectSession() {
        val id = activeDeviceId ?: return
        sessionController?.connect(id, duoSettings.toConnectOptions())
    }

    private fun openSettings(initial: SettingsCategory = settingsUi.category) {
        streamingUi.localMenu = false
        settingsReturnRoute = if (route == AppRoute.ControlCenter) AppRoute.ControlCenter else AppRoute.Home
        settingsUi.open(initial)
        route = AppRoute.Settings
    }

    private fun openPreferences() {
        openSettings(SettingsCategory.PICTURE)
    }

    private fun closeSettings() {
        settingsUi.confirmReset = false
        route = settingsReturnRoute
        settingsReturnRoute = AppRoute.Home
    }

    private fun openAccount() {
        route = AppRoute.Account
        accountIndex = 0
        accountConfirmLogout = false
    }

    private fun closeAccount() {
        route = AppRoute.Home
        accountConfirmLogout = false
    }

    private fun openControlCenter() {
        if (sessionController?.videoTrack == null) return
        streamingUi.localMenu = false
        controlCenter.open(duoSettings)
        route = AppRoute.ControlCenter
    }

    private fun closeControlCenter() {
        controlCenter.cancel(duoSettings)
        controlCenter.close()
        route = AppRoute.Home
    }

    private fun openFiles() {
        val client = sessionController ?: return
        if (client.videoTrack == null || route == AppRoute.Files || duoSettings.viewOnly) return
        filesReturnRoute = if (route == AppRoute.ControlCenter) AppRoute.ControlCenter else AppRoute.Home
        filesUi.reset()
        localFilesUi.close()
        route = AppRoute.Files
        client.listFiles()
    }

    private fun closeFiles() {
        localFilesUi.close()
        route = filesReturnRoute
        filesReturnRoute = AppRoute.Home
    }

    private fun cancelControlCenter() {
        controlCenter.cancel(duoSettings)
    }

    private fun applyControlCenter() {
        val next = controlCenter.applyTo(duoSettings)
        updateSettings(next)
        controlCenter.syncAfterApply(next)
        route = AppRoute.Home
        reconnectSession()
    }

    private fun adjustControlCenter(field: ControlField, delta: Int) {
        val result = controlCenter.adjust(field, delta, duoSettings)
        result.draft?.let(controlCenter::updateDraft)
        result.settings?.let(::updateSettings)
        if (result.openHelp) showHelp = true
        if (result.openFiles) openFiles()
    }

    private fun moveAccountSelection(delta: Int) {
        accountIndex = (accountIndex + delta + 3) % 3
        accountConfirmLogout = false
    }

    private fun activateAccount() {
        when (accountIndex) {
            0 -> accountRefresh()
            1 -> accountLogout()
            2 -> accountBack()
        }
    }

    private fun accountRefresh() {
        accountIndex = 0
        accountConfirmLogout = false
        refreshDevices()
    }

    private fun accountLogout() {
        accountIndex = 1
        if (accountConfirmLogout) logoutAccount() else accountConfirmLogout = true
    }

    private fun accountBack() {
        accountIndex = 2
        closeAccount()
    }

    private fun logoutAccount() {
        sessionController?.disconnect()
        activeDeviceId = null
        activeDeviceName = null
        route = AppRoute.Home
        settingsReturnRoute = AppRoute.Home
        showHelp = false
        streamingUi.resetForNewSession()
        controlCenter.close()
        secure.clearSession()
        devices = emptyList()
        selectedId.value = ""
        deviceError = null
        devicesLoading = false
        accountName = "本机账号"
        homeInitialFocusApplied = false
        accountConfirmLogout = false
        loginController?.dispose()
        loginController = api?.let { client ->
            LoginController(
                api = client,
                scope = lifecycleScope,
                onLoggedIn = { hasToken = true },
            )
        }
        hasToken = false
    }

    private fun updateSettings(next: DuoSettings) {
        val previous = duoSettings
        host.screenMode = next.screenMode
        host.singleHost = next.singleHost
        // Keep persistence in sync with the layout actually in use. A stale AUTO/singleHost
        // snapshot must not overwrite a manual single-screen choice when an unrelated row changes.
        val resolved = next.copy(
            screenMode = when {
                next.screenMode == ScreenMode.AUTO && host.layout != HostLayout.DUAL -> ScreenMode.SINGLE
                else -> next.screenMode
            },
            singleHost = when (host.layout) {
                HostLayout.SINGLE_TOP -> SingleHost.TOP
                HostLayout.SINGLE_BOTTOM -> SingleHost.BOTTOM
                else -> next.singleHost
            },
        )
        duoSettings = resolved
        if (resolved.audio != previous.audio) {
            sessionController?.setAudioPlayout(resolved.audio && !streamingUi.muted)
        }
        sessionController?.configure(
            gamepadPassthrough = resolved.gamepadPassthrough,
            gamepadVibration = resolved.vibration,
            autoReconnect = resolved.autoReconnect,
            clipboardSync = resolved.clipboardSync,
            gamepadDeadzone = resolved.gamepadDeadzone,
            invertLeftY = resolved.invertLeftY,
            invertRightY = resolved.invertRightY,
            viewOnly = resolved.viewOnly,
        )
        lifecycleScope.launch { settingsStore.save(resolved) }
    }

    private fun toggleGamepadPassthrough() {
        updateSettings(duoSettings.copy(gamepadPassthrough = !duoSettings.gamepadPassthrough))
    }

    private fun activateLocalMenu(index: Int) {
        when (index) {
            0 -> streamingUi.localMenu = false
            1 -> openControlCenter()
            2 -> toggleGamepadPassthrough()
            3 -> {
                streamingUi.localMenu = false
                openSettings()
            }

            4 -> disconnectSession()
        }
    }

    private fun refreshDevices() {
        lifecycleScope.launch { loadDevices() }
    }

    private fun homeStatus(): String? =
        sessionController?.message
            ?: deviceError
            ?: if (devicesLoading) "正在加载设备…" else null

    private fun preferenceSummary(): String {
        val resolution = when (duoSettings.resolution) {
            "1280x720" -> "720p"
            "window" -> "跟随窗口"
            else -> "1080p"
        }
        val quality = when (duoSettings.quality) {
            "smooth" -> "流畅"
            "standard" -> "标准"
            else -> "高清"
        }
        val bitrate = if (duoSettings.bitrateMbps <= 0) "自动码率" else "${duoSettings.bitrateMbps} Mbps"
        return "$resolution · ${duoSettings.fps} FPS · $quality · $bitrate"
    }

    private suspend fun loadDevices() {
        val client = api ?: return
        devicesLoading = true
        deviceError = null
        try {
            val remote = withContext(Dispatchers.IO) { client.getDevices() }
            devices = remote.map { it.toUi() }
        } catch (e: Exception) {
            deviceError = e.message ?: "设备列表获取失败"
        } finally {
            devicesLoading = false
        }
    }

    private suspend fun loadAccountName(client: UuApiClient) {
        runCatching {
            val info = withContext(Dispatchers.IO) { client.getUserInfo() }
            val user = info.get("user")?.takeIf { it.isJsonObject }?.asJsonObject ?: info
            user.get("nickname")?.takeIf { !it.isJsonNull }?.asString
                ?: user.get("user_name")?.takeIf { !it.isJsonNull }?.asString
                ?: user.get("mobile")?.takeIf { !it.isJsonNull }?.asString
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { accountName = it }
    }

    private fun UuApiClient.Device.toUi(): DemoDevice = DemoDevice(
        id = id,
        name = name,
        os = os,
        online = online,
        lastSeen = formatLastSeen(lastSeen) ?: "—",
        resolution = raw.get("resolution")?.let {
            if (it.isJsonPrimitive) runCatching { it.asString }.getOrNull() else null
        } ?: "—",
    )

    private fun formatLastSeen(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val number = value.toLongOrNull() ?: return value
        val millis = if (number < 10_000_000_000L) number * 1000L else number
        return runCatching {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
        }.getOrNull()
    }

    private fun DuoSettings.toConnectOptions(): ByteArray {
        val width: Int
        val height: Int
        when (resolution) {
            "1280x720" -> {
                width = 1280
                height = 720
            }

            else -> {
                width = 1920
                height = 1080
            }
        }
        val qualityValue = when (quality) {
            "smooth" -> 1
            "standard" -> 2
            else -> 3
        }
        return ConnectOptions.encode(
            width = width,
            height = height,
            fps = fps,
            quality = qualityValue,
            maxBitrateMbps = bitrateMbps,
            followLocal = resolution == "window",
        )
    }

    private fun selectHost(layout: HostLayout) {
        val mode: ScreenMode
        val single: SingleHost
        when (layout) {
            HostLayout.DUAL -> {
                host.useDual()
                mode = ScreenMode.DUAL
                single = SingleHost.AUTO
            }

            HostLayout.SINGLE_TOP -> {
                host.useSingleTop()
                mode = ScreenMode.SINGLE
                single = SingleHost.TOP
            }

            HostLayout.SINGLE_BOTTOM -> {
                host.useSingleBottom()
                mode = ScreenMode.SINGLE
                single = SingleHost.BOTTOM
            }
        }
        lifecycleScope.launch {
            settingsStore.setScreenMode(mode)
            settingsStore.setSingleHost(single)
        }
    }

    private fun handleBack() {
        if (host.layout == HostLayout.SINGLE_BOTTOM) {
            // Never leave a black top Activity behind: return to the top single layout.
            host.useSingleTop()
        } else {
            finish()
        }
    }

    private fun handleSystemBack() {
        if (showHelp) {
            showHelp = false
            return
        }
        if (!hasToken) {
            handleLoginBack()
            return
        }
        if (localFilesUi.visible) {
            localBack()
            return
        }
        when (route) {
            AppRoute.Settings -> closeSettings()
            AppRoute.Account -> closeAccount()
            AppRoute.ControlCenter -> closeControlCenter()
            AppRoute.Files -> {
                if (filesUi.tab == FilesTab.QUEUE) {
                    filesUi.tab = FilesTab.BROWSE
                } else {
                    val parent = sessionController?.fileState?.parentPath()
                    if (parent != null) {
                        sessionController?.listFiles(parent)
                        filesUi.select(0)
                    } else {
                        closeFiles()
                    }
                }
            }

            AppRoute.Home -> {
                if (sessionController?.videoTrack != null) {
                    streamingUi.localMenu = true
                } else {
                    handleBack()
                }
            }
        }
    }

    private fun handleLoginBack() {
        val controller = loginController
        when {
            controller?.editing == true -> controller.editing = false
            controller?.mode == LoginMode.SMS -> controller.switchMode(LoginMode.QR)
            host.layout == HostLayout.SINGLE_BOTTOM -> host.useSingleTop()
            else -> finish()
        }
    }

    private fun dismissPresentation() {
        if (presentation?.isShowing == true) {
            presentation?.dismiss()
        }
        presentation = null
    }

    private fun syncPresentation(
        stage: Stage,
        layout: HostLayout,
    ) {
        dismissPresentation()
        if (stage == Stage.KEY_SETUP) return
        if (layout == HostLayout.SINGLE_TOP) return

        val display = findSecondaryDisplay() ?: return

        val created = DuoPresentation(
            context = this,
            display = display,
            onBack = {
                runOnUiThread { handleSystemBack() }
            },
            onKeyEvent = { event ->
                if (handleGlobalKey(event)) {
                    true
                } else {
                    when (stage) {
                        Stage.LOGIN -> handleLoginKey(stage, event)
                        Stage.HOME -> when (route) {
                            AppRoute.Settings -> handleSettingsKey(event)
                            AppRoute.Account -> handleAccountKey(event)
                            AppRoute.ControlCenter -> handleControlCenterKey(event)
                            AppRoute.Files -> handleFilesKey(event)
                            AppRoute.Home -> when {
                                sessionController?.videoTrack != null -> handleStreamingKey(event)
                                else -> handleHomeKey(event)
                            }
                        }

                        else -> false
                    }
                }
            },
            onGenericMotionEvent = { event -> handleStreamingMotion(event) },
            normalizeKey = ::normalizeEvent,
        ) {
            DuoRemoteTheme(
                theme = duoSettings.theme,
                focusRingStyle = duoSettings.focusRingStyle,
                hapticFeedback = duoSettings.hapticFeedback,
                soundFeedback = duoSettings.soundFeedback,
            ) {
                Box(Modifier.fillMaxSize()) {
                    PresentationContent(stage, layout)
                    if (showHelp && layout != HostLayout.SINGLE_TOP) {
                        GamepadHelpOverlay(onClose = { showHelp = false })
                    }
                }
            }
        }
        created.show()
        presentation = created
    }

    private fun refreshDisplays() {
        val hasSecondary = findSecondaryDisplay() != null
        if (host.hasSecondaryDisplay != hasSecondary) {
            host.hasSecondaryDisplay = hasSecondary
        }
    }

    private fun findSecondaryDisplay(): Display? {
        return displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    /**
     * Gamepad keys may be routed to the Activity window rather than the Presentation.
     * Handle login shortcuts here as well so Y/X/A work regardless of which screen owns focus.
     * Home, settings, account and the control center share the same state objects on both hosts.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(raw: KeyEvent): Boolean {
        // Normalize face buttons once, before either our own handlers or Compose see the event.
        val event = normalizeEvent(raw)
        if (hasKey && hasToken && handleGlobalKey(event)) return true
        if (hasKey && !hasToken && handleLoginKey(Stage.LOGIN, event)) return true
        if (hasKey && hasToken) {
            val handled = when (route) {
                AppRoute.Settings -> handleSettingsKey(event)
                AppRoute.Account -> handleAccountKey(event)
                AppRoute.ControlCenter -> handleControlCenterKey(event)
                AppRoute.Files -> handleFilesKey(event)
                AppRoute.Home -> when {
                    sessionController?.videoTrack != null -> handleStreamingKey(event)
                    else -> handleHomeKey(event)
                }
            }
            if (handled) return true
        }
        // Some pad stacks synthesize DPAD_CENTER alongside BUTTON_A; never let the ghost click
        // activate whatever Compose node happens to hold internal focus.
        if (hasKey && hasToken && event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) return true
        return super.dispatchKeyEvent(event)
    }

    private fun normalizeEvent(event: KeyEvent): KeyEvent {
        val mapped = inputRouter.normalizeFaceKey(event.keyCode)
        if (mapped == event.keyCode) return event
        return KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (hasKey && hasToken && handleStreamingMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private fun handleGlobalKey(event: KeyEvent): Boolean {
        if (showHelp) {
            if (event.action == KeyEvent.ACTION_UP && event.keyCode in setOf(
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BUTTON_START,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                )
            ) {
                showHelp = false
            }
            return isPadKey(event.keyCode) ||
                event.keyCode == KeyEvent.KEYCODE_BACK ||
                event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                event.keyCode == KeyEvent.KEYCODE_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        }
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BUTTON_START) {
            val passthroughOwnsStart = route == AppRoute.Home &&
                sessionController?.videoTrack != null &&
                effectiveGamepadPassthrough &&
                !streamingUi.localMenu &&
                streamingUi.imeMode == ImeMode.HIDDEN
            if (!passthroughOwnsStart) {
                showHelp = true
                return true
            }
        }
        return false
    }

    /**
     * The Presentation owns the toolkit visually, but the Activity window may still receive pad
     * events. Route them into the shared streaming state so a stray B cannot background the app.
     */
    private fun handleStreamingKey(event: KeyEvent): Boolean {
        if (sessionController?.videoTrack == null) return false
        val code = event.keyCode
        if (streamingUi.localMenu) {
            if (event.action != KeyEvent.ACTION_UP) return isPadKey(code)
            return when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    streamingUi.localMenuIndex = (streamingUi.localMenuIndex + 4) % 5
                    true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    streamingUi.localMenuIndex = (streamingUi.localMenuIndex + 1) % 5
                    true
                }

                // Canonical A is the physical bottom B (confirm); canonical B is physical A.
                KeyEvent.KEYCODE_BUTTON_A -> {
                    activateLocalMenu(streamingUi.localMenuIndex)
                    true
                }

                KeyEvent.KEYCODE_BUTTON_B -> {
                    streamingUi.localMenu = false
                    true
                }

                else -> isPadKey(code)
            }
        }
        if (streamingUi.imeMode != ImeMode.HIDDEN) {
            if (event.action != KeyEvent.ACTION_UP) return isPadKey(code)
            if (code == KeyEvent.KEYCODE_BUTTON_B) {
                streamingUi.imeMode = ImeMode.HIDDEN
                return true
            }
            return isPadKey(code)
        }
        if (effectiveGamepadPassthrough) {
            return sessionController?.handleGamepadKey(code, event.action) == true
        }
        if (event.action != KeyEvent.ACTION_UP) return isPadKey(code)
        val keyboardDefault = if (duoSettings.keyboardDefault == "system") {
            ImeMode.SYSTEM
        } else {
            ImeMode.BUILTIN
        }
        return when (code) {
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                sessionController?.releaseAllKeys()
                sessionController?.releaseAllMouseButtons()
                streamingUi.moveTab(-1, keyboardDefault)
                true
            }

            KeyEvent.KEYCODE_BUTTON_R1 -> {
                sessionController?.releaseAllKeys()
                sessionController?.releaseAllMouseButtons()
                streamingUi.moveTab(1, keyboardDefault)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                streamingUi.moveToolkitFocus(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                streamingUi.moveToolkitFocus(1)
                true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                streamingUi.moveToolkitRow(down = false)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                streamingUi.moveToolkitRow(down = true)
                true
            }

            KeyEvent.KEYCODE_BUTTON_A -> {
                if (streamingUi.toolkitFocusRow == 0) {
                    sessionController?.releaseAllKeys()
                    sessionController?.releaseAllMouseButtons()
                    streamingUi.activateToolkitFocus(
                        onSystemIme = { openSystemIme() },
                        keyboardDefault = keyboardDefault,
                    )
                } else {
                    activateToolkitBody(
                        ui = streamingUi,
                        session = sessionController ?: return true,
                        onOpenSettings = { openSettings() },
                        onReconnect = ::reconnectSession,
                        onBackToList = ::disconnectSession,
                    )
                }
                true
            }

            KeyEvent.KEYCODE_BUTTON_B -> {
                streamingUi.localMenu = true
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true

            else -> false
        }
    }

    private fun handleStreamingMotion(event: MotionEvent): Boolean {
        if (route != AppRoute.Home) return false
        if (sessionController?.videoTrack == null) return false
        if (!effectiveGamepadPassthrough) return false
        if (streamingUi.localMenu || streamingUi.imeMode != ImeMode.HIDDEN) return false
        return sessionController?.handleGamepadMotion(event) == true
    }

    private fun isPadKey(code: Int): Boolean =
        GamepadMapper.controlForKey(code) != null ||
            code == KeyEvent.KEYCODE_BUTTON_L2 ||
            code == KeyEvent.KEYCODE_BUTTON_R2

    private fun openSystemIme() {
        streamingUi.tab = ToolTab.KEYBOARD
        streamingUi.imeMode = ImeMode.SYSTEM
    }

    private fun handleHomeKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        val code = event.keyCode
        val devicesCount = devices.size

        if (code == KeyEvent.KEYCODE_BUTTON_B) {
            if (homeUi.focus == HomeFocus.ACTIONS && devicesCount > 0) {
                homeUi.toList()
            } else {
                handleBack()
            }
            return true
        }

        if (code == KeyEvent.KEYCODE_BUTTON_X) {
            openAccount()
            return true
        }

        if (code == KeyEvent.KEYCODE_BUTTON_Y) {
            refreshDevices()
            return true
        }

        if (code == KeyEvent.KEYCODE_BUTTON_A) {
            if (homeUi.focus == HomeFocus.ACTIONS) {
                activateHomeAction(homeUi.actionIndex)
            } else {
                selectedDevice()?.let(::connectSession)
            }
            return true
        }

        return when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (homeUi.focus == HomeFocus.ACTIONS) {
                    homeUi.toList()
                } else {
                    moveHomeSelection(-1)
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (homeUi.focus == HomeFocus.ACTIONS) {
                    true
                } else if (devicesCount == 0 || selectedIndex() >= devicesCount - 1) {
                    homeUi.toActions()
                    true
                } else {
                    moveHomeSelection(1)
                    true
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (homeUi.focus == HomeFocus.ACTIONS) {
                    homeUi.moveAction(-1)
                    true
                } else {
                    false
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (homeUi.focus == HomeFocus.ACTIONS) {
                    homeUi.moveAction(1)
                    true
                } else {
                    false
                }
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true

            else -> false
        }
    }

    private fun activateHomeAction(index: Int) {
        when (index) {
            0 -> selectedDevice()?.let(::connectSession)
            1 -> openPreferences()
            2 -> openSettings()
            3 -> openAccount()
        }
    }

    private fun handleAccountKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveAccountSelection(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveAccountSelection(1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_A -> {
                activateAccount()
                true
            }

            KeyEvent.KEYCODE_BUTTON_B -> {
                if (accountConfirmLogout) {
                    accountConfirmLogout = false
                } else {
                    closeAccount()
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true

            else -> false
        }
    }

    private fun handleSettingsKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        if (settingsUi.confirmReset) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                -> {
                    settingsUi.toggleConfirm()
                    true
                }

                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (settingsUi.confirmSelection == 1) {
                        settingsUi.confirmReset = false
                        updateSettings(DuoSettings())
                    } else {
                        settingsUi.confirmReset = false
                    }
                    true
                }

                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                    settingsUi.confirmReset = false
                    true
                }

                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true

                else -> false
            }
        }
        val rows = settingsRows(settingsUi.category, duoSettings, ::updateSettings) {
            settingsUi.confirmReset = true
            settingsUi.confirmSelection = 0
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                settingsUi.moveCategory(-1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_R1 -> {
                settingsUi.moveCategory(1)
                true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                settingsUi.moveRow(-1, rows.size)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                settingsUi.moveRow(1, rows.size)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                rows.getOrNull(settingsUi.rowIndex)?.adjust?.invoke(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                rows.getOrNull(settingsUi.rowIndex)?.adjust?.invoke(1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_A -> {
                rows.getOrNull(settingsUi.rowIndex)?.action?.invoke()
                true
            }

            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                closeSettings()
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true

            else -> false
        }
    }

    private fun handleControlCenterKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        val rows = controlRows(
            controlCenter.group,
            controlCenter.subTab,
            controlCenter.draft,
            duoSettings,
        )
        val pending = controlCenter.pendingCount(duoSettings)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_X -> {
                if (pending > 0) {
                    applyControlCenter()
                    true
                } else {
                    false
                }
            }

            KeyEvent.KEYCODE_BUTTON_Y -> {
                if (pending > 0) {
                    cancelControlCenter()
                    true
                } else {
                    false
                }
            }

            KeyEvent.KEYCODE_BUTTON_L1 -> {
                controlCenter.moveGroup(-1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_R1 -> {
                controlCenter.moveGroup(1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_L2 -> {
                controlCenter.moveSubTab(-1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_R2 -> {
                controlCenter.moveSubTab(1)
                true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                controlCenter.moveRow(-1, rows.size)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                controlCenter.moveRow(1, rows.size)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                adjustSelectedControl(rows, -1)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                adjustSelectedControl(rows, 1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_A -> {
                adjustSelectedControl(rows, 1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                closeControlCenter()
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true

            else -> false
        }
    }

    private fun handleFilesKey(event: KeyEvent): Boolean {
        if (localFilesUi.visible) return handleLocalPickerKey(event)
        val client = sessionController ?: return false
        if (event.action != KeyEvent.ACTION_UP) return isPadKey(event.keyCode)
        filesUi.error = null
        if (filesUi.tab == FilesTab.QUEUE) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_X -> {
                    client.cancelFileTransfer()
                    true
                }

                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BACK,
                -> {
                    filesUi.tab = FilesTab.BROWSE
                    true
                }

                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    filesUi.moveTab(-1)
                    true
                }

                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    filesUi.moveTab(1)
                    true
                }

                else -> isPadKey(event.keyCode)
            }
        }

        val entries = client.fileState.entries
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                filesUi.moveIndex(-1, entries.size)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                filesUi.moveIndex(1, entries.size)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                client.fileState.parentPath()?.let {
                    client.listFiles(it)
                    filesUi.select(0)
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> {
                val entry = entries.getOrNull(filesUi.index)
                when {
                    entry == null -> filesUi.error = "请选择一个文件"
                    entry.isDirectory -> {
                        client.listFiles(
                            entry.fullPath.ifBlank {
                                FileTransferState.childOf(client.fileState.path, entry.name)
                            },
                        )
                        filesUi.select(0)
                    }

                    else -> openLocalDownload(entry)
                }
                true
            }

            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                val parent = client.fileState.parentPath()
                if (parent != null) {
                    client.listFiles(parent)
                    filesUi.select(0)
                } else {
                    closeFiles()
                }
                true
            }

            KeyEvent.KEYCODE_BUTTON_X -> {
                openLocalUpload()
                true
            }

            KeyEvent.KEYCODE_BUTTON_Y -> {
                val entry = entries.getOrNull(filesUi.index)
                when {
                    entry == null -> filesUi.error = "请选择一个文件"
                    entry.isDirectory -> filesUi.error = "文件夹下载暂未支持"
                    else -> openLocalDownload(entry)
                }
                true
            }

            KeyEvent.KEYCODE_BUTTON_L1 -> {
                filesUi.moveTab(-1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_R1 -> {
                filesUi.moveTab(1)
                true
            }

            else -> isPadKey(event.keyCode)
        }
    }

    private fun handleLocalPickerKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return isPadKey(event.keyCode)
        localFilesUi.reportError(null)
        if (localFilesUi.authPrompt) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                -> {
                    grantLocalFolder()
                    true
                }

                KeyEvent.KEYCODE_BUTTON_X -> {
                    showAppRoot()
                    true
                }

                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BACK -> {
                    localFilesUi.close()
                    true
                }

                else -> isPadKey(event.keyCode)
            }
        }
        val entries = localFilesUi.entries
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                localFilesUi.moveIndex(-1, entries.size)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                localFilesUi.moveIndex(1, entries.size)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                localBack()
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            -> {
                val entry = localFilesUi.selected
                when {
                    entry != null && entry.isDirectory -> openLocalEntry(entry)
                    localFilesUi.mode == LocalPickerMode.UPLOAD -> confirmLocalUpload()
                    else -> confirmLocalDownload()
                }
                true
            }

            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BACK -> {
                localBack()
                true
            }

            KeyEvent.KEYCODE_BUTTON_X -> {
                refreshLocal()
                true
            }

            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> {
                when (localFilesUi.current?.root ?: LocalRootKind.APP) {
                    LocalRootKind.APP -> showAuthorizedRoot()
                    LocalRootKind.AUTHORIZED -> showAppRoot()
                }
                true
            }

            else -> isPadKey(event.keyCode)
        }
    }

    private fun adjustSelectedControl(
        rows: List<com.sour.duoremote.ui.controlcenter.ControlRow>,
        delta: Int,
    ) {
        val row = rows.getOrNull(controlCenter.rowIndex) ?: return
        if (!row.enabled) return
        val field = row.field ?: return
        adjustControlCenter(field, delta)
    }

    private fun selectedDevice(): DemoDevice? =
        devices.firstOrNull { it.id == selectedId.value } ?: devices.firstOrNull()

    private fun selectedIndex(): Int {
        val index = devices.indexOfFirst { it.id == selectedId.value }
        return if (index < 0) 0 else index
    }

    private fun moveHomeSelection(delta: Int) {
        if (devices.isEmpty()) return
        val current = selectedIndex()
        val next = (current + delta).coerceIn(0, devices.lastIndex)
        selectedId.value = devices[next].id
    }

    private fun handleLoginKey(stage: Stage, event: KeyEvent): Boolean {
        val controller = loginController ?: return false
        if (stage != Stage.LOGIN || event.action != KeyEvent.ACTION_UP) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (controller.editing) return false
                controller.moveFocus(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (controller.editing) return false
                controller.moveFocus(1)
                true
            }

            KeyEvent.KEYCODE_BUTTON_Y -> {
                if (controller.mode == LoginMode.QR) controller.refreshQr()
                true
            }

            KeyEvent.KEYCODE_BUTTON_X -> {
                controller.toggleMode()
                true
            }

            KeyEvent.KEYCODE_BUTTON_A -> {
                if (!controller.activateFocused()) handleLoginBack()
                true
            }

            KeyEvent.KEYCODE_BUTTON_B -> {
                handleLoginBack()
                true
            }

            // Some gamepad stacks also synthesize DPAD_CENTER for the A button.
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> true

            else -> false
        }
    }

    override fun onDestroy() {
        setPickerKeepAlive(false)
        displayManager.unregisterDisplayListener(displayListener)
        loginController?.dispose()
        sessionController?.disconnect()
        dismissPresentation()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        // Returning to the app restores audio and the same input-suppression state the overlays
        // expect; leaving onStop below keeps a live session but stops remote input.
        sessionController?.setAudioPlayout(duoSettings.audio && !streamingUi.muted)
        sessionController?.setInputSuppressed(
            streamingUi.localMenu ||
                streamingUi.imeMode != ImeMode.HIDDEN ||
                route != AppRoute.Home ||
                showHelp,
        )
    }

    override fun onStop() {
        // Keep the signaling/RTC session alive in the background, but never leave a remote
        // gamepad button or held key down, and silence remote audio while hidden.
        sessionController?.setInputSuppressed(true)
        sessionController?.setAudioPlayout(false)
        super.onStop()
    }
}

@Composable
private fun LoadingScreen(message: String) {
    val colors = LocalDuoColors.current
    Box(
        Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
            Text(
                message,
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}
