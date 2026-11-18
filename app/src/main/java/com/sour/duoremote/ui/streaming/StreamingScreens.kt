package com.sour.duoremote.ui.streaming

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sour.duoremote.data.DuoSettings
import com.sour.duoremote.protocol.rtc.RtcRuntime
import com.sour.duoremote.session.SessionController
import com.sour.duoremote.session.StreamStats
import com.sour.duoremote.ui.input.VideoTouchMapper
import com.sour.duoremote.ui.input.VideoTouchPoint
import com.sour.duoremote.ui.theme.DuoMonoStyle
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun RemoteVideo(
    track: VideoTrack,
    modifier: Modifier = Modifier,
    directTouch: Boolean = false,
    session: SessionController? = null,
    onCursorMove: ((Float, Float) -> Unit)? = null,
    onFirstFrame: () -> Unit = {},
    scale: String = "fit",
) {
    val context = LocalContext.current
    var firstFrame by remember(track) { mutableStateOf(false) }
    var frameSize by remember(track) { mutableStateOf(0 to 0) }
    val videoSize = remember(track) { AtomicReference(0 to 0) }
    val renderer = remember(track) {
        SurfaceViewRenderer(context).apply {
            init(
                RtcRuntime.eglBase.eglBaseContext,
                object : RendererCommon.RendererEvents {
                    override fun onFirstFrameRendered() {
                        firstFrame = true
                        onFirstFrame()
                    }

                    override fun onFrameResolutionChanged(
                        videoWidth: Int,
                        videoHeight: Int,
                        rotation: Int,
                    ) {
                        videoSize.set(videoWidth to videoHeight)
                        frameSize = videoWidth to videoHeight
                    }
                },
            )
            setScalingType(scalingType(scale))
            setEnableHardwareScaler(true)
            setMirror(false)
        }
    }
    LaunchedEffect(renderer, scale) {
        renderer.setScalingType(scalingType(scale))
    }
    DisposableEffect(renderer, track) {
        track.addSink(renderer)
        onDispose {
            track.removeSink(renderer)
            renderer.release()
        }
    }
    val currentOnCursorMove = rememberUpdatedState(onCursorMove)
    val touchModifier = if (directTouch && session != null) {
        Modifier.pointerInput(session, scale) {
            val touchSlop = viewConfiguration.touchSlop
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
            fun mapPoint(x: Float, y: Float): VideoTouchPoint? {
                val (videoWidth, videoHeight) = videoSize.get()
                return VideoTouchMapper.map(
                    x = x,
                    y = y,
                    viewWidth = size.width.toFloat(),
                    viewHeight = size.height.toFloat(),
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    scaleMode = scale,
                )
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                val startAt = down.uptimeMillis
                var last = down.position
                var travel = 0f
                var dragging = false
                var twoFinger = false
                var scrollTravel = 0f
                mapPoint(down.position.x, down.position.y)?.let {
                    session.mouseAbsolute(it.x, it.y)
                    currentOnCursorMove.value?.invoke(it.x, it.y)
                }
                while (true) {
                    val event = if (!twoFinger && travel <= touchSlop && !dragging) {
                        val wait = startAt + longPressTimeout - SystemClock.uptimeMillis()
                        withTimeoutOrNull(wait.coerceAtLeast(1L)) { awaitPointerEvent() }
                    } else {
                        awaitPointerEvent()
                    }
                    if (event == null) {
                        if (mapPoint(last.x, last.y) != null) {
                            session.mouseButton(1, true)
                            dragging = true
                        }
                        continue
                    }
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        twoFinger = true
                        val delta = pressed
                            .map { it.positionChange() }
                            .reduce { a, b -> a + b } / pressed.size.toFloat()
                        scrollTravel += delta.getDistance()
                        if (delta.y != 0f) {
                            session.scroll(0, delta.y.roundToInt())
                        }
                        event.changes.forEach { it.consume() }
                    } else {
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull { it.pressed }
                            ?: break
                        val delta = change.position - last
                        last = change.position
                        travel += delta.getDistance()
                        if (!twoFinger && (dragging || travel > touchSlop)) {
                            mapPoint(change.position.x, change.position.y)?.let {
                                session.mouseAbsolute(it.x, it.y)
                    currentOnCursorMove.value?.invoke(it.x, it.y)
                            }
                            change.consume()
                        }
                    }
                }
                when {
                    dragging -> session.mouseButton(1, false)
                    twoFinger && scrollTravel <= touchSlop &&
                        SystemClock.uptimeMillis() - startAt < 320L -> {
                        mapPoint(last.x, last.y)?.let {
                            session.mouseAbsolute(it.x, it.y)
                    currentOnCursorMove.value?.invoke(it.x, it.y)
                            session.mouseClick(2)
                        }
                    }

                    !twoFinger && travel <= touchSlop -> {
                        mapPoint(last.x, last.y)?.let {
                            session.mouseAbsolute(it.x, it.y)
                    currentOnCursorMove.value?.invoke(it.x, it.y)
                            session.mouseClick(1)
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }
    BoxWithConstraints(modifier.background(Color.Black).clipToBounds()) {
        // SurfaceViewRenderer's scaling type only affects its own measurement; Compose's
        // fillMaxSize would force it back to the container and make "fit" behave like "fill".
        // Size the surface from the frame aspect instead, and crop/letterbox with the outer box.
        val density = LocalDensity.current
        val (videoWidth, videoHeight) = frameSize
        val viewWidth = constraints.maxWidth.toFloat()
        val viewHeight = constraints.maxHeight.toFloat()
        val videoModifier = if (videoWidth > 0 && videoHeight > 0 && viewWidth > 0f && viewHeight > 0f) {
            val fit = min(viewWidth / videoWidth, viewHeight / videoHeight)
            val renderScale = when (scale) {
                "fill" -> max(viewWidth / videoWidth, viewHeight / videoHeight)
                "original" -> min(1f, fit)
                else -> fit
            }
            with(density) {
                Modifier.requiredSize(
                    width = (videoWidth * renderScale).toDp(),
                    height = (videoHeight * renderScale).toDp(),
                )
            }
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { renderer },
            modifier = videoModifier.align(Alignment.Center),
        )
        if (directTouch && session != null) {
            // AndroidView/SurfaceView consumes touches before the parent Box sees them, so the
            // gesture layer must be a transparent sibling drawn above the video surface.
            Box(Modifier.matchParentSize().then(touchModifier))
        }
        if (!firstFrame) {
            Text(
                "正在等待画面…",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF7C8796),
            )
        }
    }
}

private fun scalingType(scale: String): RendererCommon.ScalingType = when (scale) {
    "fill" -> RendererCommon.ScalingType.SCALE_ASPECT_FILL
    "original" -> RendererCommon.ScalingType.SCALE_ASPECT_BALANCED
    else -> RendererCommon.ScalingType.SCALE_ASPECT_FIT
}

private fun streamChip(settings: DuoSettings): String {
    val resolution = when (settings.resolution) {
        "1280x720" -> "720p"
        "window" -> "窗口"
        else -> "1080p"
    }
    return "$resolution · ${settings.fps}"
}

@Composable
fun StreamingVideoTop(
    track: VideoTrack,
    title: String,
    status: String?,
    stats: StreamStats,
    session: SessionController,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    diagnostics: Boolean = true,
    directTouch: Boolean = false,
    onRemoteCursor: ((Float, Float) -> Unit)? = null,
    viewOnly: Boolean = false,
    videoScale: String = "fit",
) {
    val colors = LocalDuoColors.current
    BackHandler(onBack = onMenu)
    Box(modifier.fillMaxSize().background(Color.Black)) {
        RemoteVideo(
            track = track,
            modifier = Modifier.fillMaxSize(),
            directTouch = directTouch,
            session = session,
            onCursorMove = onRemoteCursor,
            scale = videoScale,
        )
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(DuoRadius.pill))
                .background(Color(0x9E080C11))
                .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(DuoRadius.pill))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (status != null) Color(0xFF6BE3A4) else Color(0xFFFFC46B), CircleShape),
            )
            Text(
                if (stats.width > 0 && diagnostics) {
                    String.format(
                        Locale.US,
                        "%d×%d · %.0f FPS · %.0f ms",
                        stats.width,
                        stats.height,
                        stats.fps,
                        stats.rttMs,
                    )
                } else {
                    title
                },
                style = DuoMonoStyle.copy(fontSize = 10.sp),
                color = Color(0xC7E8EDF4),
                maxLines = 1,
            )
        }
        if (viewOnly) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 44.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(DuoRadius.pill))
                    .background(colors.warning.copy(alpha = 0.16f))
                    .border(1.dp, colors.warning.copy(alpha = 0.42f), RoundedCornerShape(DuoRadius.pill))
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "仅观看 · 不发送输入",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colors.warning,
                )
            }
        }
    }
}

@Composable
fun DualStreamingBottom(
    title: String,
    status: String?,
    stats: StreamStats,
    ui: StreamingUiState,
    settings: DuoSettings,
    session: SessionController,
    gamepadPassthrough: Boolean,
    onToggleGamepad: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(gamepadPassthrough) {
        if (!gamepadPassthrough) {
            repeat(3) {
                runCatching { rootFocus.requestFocus() }
                kotlinx.coroutines.delay(80)
            }
        }
    }
    val openSystemIme = {
        ui.tab = ToolTab.KEYBOARD
        ui.imeMode = ImeMode.SYSTEM
    }
    BackHandler { ui.localMenu = true }
    Box(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .then(
                if (!gamepadPassthrough) {
                    Modifier.onPreviewKeyEvent {
                        handleToolkitPadKey(
                            ui = ui,
                            session = session,
                            event = it.nativeKeyEvent,
                            keyboardDefault = if (settings.keyboardDefault == "system") {
                                ImeMode.SYSTEM
                            } else {
                                ImeMode.BUILTIN
                            },
                            onSystemIme = openSystemIme,
                        ) {
                            activateToolkitBody(ui, session, onOpenSettings, onReconnect, onBackToList)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        Column(Modifier.fillMaxSize()) {
            ToolkitToolbar(
                title = title,
                status = status,
                stats = stats,
                muted = ui.muted,
                gamepadPassthrough = gamepadPassthrough,
                onMenu = { ui.localMenu = true },
                onToggleMute = { toggleMute(ui, session) },
                onToggleGamepad = onToggleGamepad,
                onSettings = onOpenSettings,
                onDisconnect = onDisconnect,
                compact = false,
                diagnostics = settings.diagnostics,
                streamChip = streamChip(settings),
                viewOnly = settings.viewOnly,
                onOpenControlCenter = onOpenControlCenter,
            )
            ToolkitTabs(
                tab = ui.tab,
                onTab = {
                    session.releaseAllKeys()
                    session.releaseAllMouseButtons()
                    ui.selectTab(
                        it,
                        if (settings.keyboardDefault == "system") ImeMode.SYSTEM else ImeMode.BUILTIN,
                    )
                },
                onSystemIme = openSystemIme,
                compact = true,
                focusedIndex = if (!gamepadPassthrough && ui.toolkitFocusRow == 0) {
                    ui.toolkitFocusIndex
                } else {
                    null
                },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            ) {
                ToolkitBody(
                    ui = ui,
                    settings = settings,
                    session = session,
                    stats = stats,
                    compact = true,
                    onReconnect = onReconnect,
                    onBackToList = onBackToList,
                )
            }
            StreamFooter(gamepadPassthrough)
        }
        if (ui.imeMode != ImeMode.HIDDEN) {
            ImeOverlay(ui, settings, session) { ui.imeMode = ImeMode.HIDDEN }
        }
        if (ui.localMenu) {
            LocalMenuOverlay(
                gamepadPassthrough = gamepadPassthrough,
                ui = ui,
                onClose = { ui.localMenu = false },
                onToggleGamepad = onToggleGamepad,
                onSettings = onOpenSettings,
                onControlCenter = onOpenControlCenter,
                onDisconnect = onDisconnect,
            )
        }
    }
}

@Composable
fun SingleStreamingScreen(
    track: VideoTrack,
    title: String,
    status: String?,
    stats: StreamStats,
    ui: StreamingUiState,
    settings: DuoSettings,
    session: SessionController,
    gamepadPassthrough: Boolean,
    onToggleGamepad: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(gamepadPassthrough) {
        if (!gamepadPassthrough) {
            repeat(3) {
                runCatching { rootFocus.requestFocus() }
                kotlinx.coroutines.delay(80)
            }
        }
    }
    val openSystemIme = {
        ui.tab = ToolTab.KEYBOARD
        ui.imeMode = ImeMode.SYSTEM
    }
    BackHandler { ui.localMenu = true }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!gamepadPassthrough) {
                    Modifier.onPreviewKeyEvent {
                        handleToolkitPadKey(
                            ui = ui,
                            session = session,
                            event = it.nativeKeyEvent,
                            keyboardDefault = if (settings.keyboardDefault == "system") {
                                ImeMode.SYSTEM
                            } else {
                                ImeMode.BUILTIN
                            },
                            onSystemIme = openSystemIme,
                        ) {
                            activateToolkitBody(ui, session, onOpenSettings, onReconnect, onBackToList)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        LaunchedEffect(maxWidth) {
            ui.drawerFraction = if (maxWidth < 700.dp) 0.62f else 0.58f
        }
        RemoteVideo(
            track = track,
            modifier = Modifier.fillMaxSize(),
            directTouch = settings.directTouch,
            session = session,
            onCursorMove = ui::moveCursorTo,
            scale = settings.videoScale,
        )
        if (ui.drawerExpanded) {
            val fraction = ui.drawerFraction.coerceIn(0.30f, 0.68f)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(maxHeight * fraction)
                    .clip(RoundedCornerShape(topStart = DuoRadius.xl, topEnd = DuoRadius.xl))
                    .background(colors.surface1)
                    .border(
                        1.dp,
                        colors.outlineStrong,
                        RoundedCornerShape(topStart = DuoRadius.xl, topEnd = DuoRadius.xl),
                    ),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(20.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .width(34.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.outlineStrong)
                            .clickable { ui.drawerExpanded = false },
                    )
                }
                ToolkitToolbar(
                    title = title,
                    status = status,
                    stats = stats,
                    muted = ui.muted,
                    gamepadPassthrough = gamepadPassthrough,
                    onMenu = { ui.localMenu = true },
                    onToggleMute = { toggleMute(ui, session) },
                    onToggleGamepad = onToggleGamepad,
                    onSettings = onOpenSettings,
                    onDisconnect = onDisconnect,
                    onCollapse = { ui.drawerExpanded = false },
                    diagnostics = settings.diagnostics,
                    streamChip = streamChip(settings),
                    viewOnly = settings.viewOnly,
                    onOpenControlCenter = onOpenControlCenter,
                )
                ToolkitTabs(
                    tab = ui.tab,
                    onTab = {
                        ui.selectTab(
                            it,
                            if (settings.keyboardDefault == "system") ImeMode.SYSTEM else ImeMode.BUILTIN,
                        )
                    },
                    onSystemIme = openSystemIme,
                    compact = true,
                    focusedIndex = if (!gamepadPassthrough && ui.toolkitFocusRow == 0) {
                        ui.toolkitFocusIndex
                    } else {
                        null
                    },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                ) {
                    ToolkitBody(
                        ui = ui,
                        settings = settings,
                        session = session,
                        stats = stats,
                        compact = true,
                        onReconnect = onReconnect,
                        onBackToList = onBackToList,
                    )
                }
            }
        } else {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    // Keep clear of the system gesture strip; taps at the very bottom edge are
                    // swallowed by the OS on the Duo's lower screen.
                    .padding(end = 14.dp, bottom = 32.dp)
                    .clip(RoundedCornerShape(DuoRadius.lg))
                    .background(Color(0xCC0B0D10))
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
                    .clickable { ui.drawerExpanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.KeyboardArrowUp, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Text("工具箱", style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
            }
        }
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(DuoRadius.pill))
                .background(Color(0x9E080C11))
                .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(DuoRadius.pill))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (status != null) Color(0xFF6BE3A4) else Color(0xFFFFC46B), CircleShape),
            )
            Text(
                if (stats.width > 0 && settings.diagnostics) {
                    String.format(
                        Locale.US,
                        "%d×%d · %.0f FPS · %.0f ms",
                        stats.width,
                        stats.height,
                        stats.fps,
                        stats.rttMs,
                    )
                } else {
                    title
                },
                style = DuoMonoStyle.copy(fontSize = 10.sp),
                color = Color(0xC7E8EDF4),
                maxLines = 1,
            )
        }
        if (settings.viewOnly) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 44.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(DuoRadius.pill))
                    .background(colors.warning.copy(alpha = 0.16f))
                    .border(1.dp, colors.warning.copy(alpha = 0.42f), RoundedCornerShape(DuoRadius.pill))
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "仅观看 · 不发送输入",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colors.warning,
                )
            }
        }
        if (ui.imeMode != ImeMode.HIDDEN) {
            ImeOverlay(ui, settings, session) { ui.imeMode = ImeMode.HIDDEN }
        }
        if (ui.localMenu) {
            LocalMenuOverlay(
                gamepadPassthrough = gamepadPassthrough,
                ui = ui,
                onClose = { ui.localMenu = false },
                onToggleGamepad = onToggleGamepad,
                onSettings = onOpenSettings,
                onControlCenter = onOpenControlCenter,
                onDisconnect = onDisconnect,
            )
        }
    }
}

@Composable
private fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val colors = LocalDuoColors.current
    IconButton(onClick = onClick, modifier = Modifier.size(28.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (danger) colors.error else colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun toggleMute(ui: StreamingUiState, session: SessionController) {
    ui.muted = !ui.muted
    session.setAudioPlayout(!ui.muted)
}

@Composable
private fun StreamFooter(gamepadPassthrough: Boolean) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 16.dp)
            .drawBehind {
                drawLine(
                    colors.outline.copy(alpha = 0.45f),
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    1.dp.toPx(),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FooterHint("手柄", if (gamepadPassthrough) "直通电脑" else "本地操作")
        if (gamepadPassthrough) {
            // Every pad key goes to the PC in passthrough; system back (Select) stays local.
            FooterHint("SELECT", "本地菜单")
        } else {
            FooterHint("A", "本地菜单")
        }
        FooterHint("触摸", "工具箱")
        Spacer(Modifier.weight(1f))
        Text(
            if (gamepadPassthrough) "START 属于远端手柄" else "START 打开手柄说明",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun FooterHint(key: String, action: String) {
    val colors = LocalDuoColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            key,
            style = DuoMonoStyle.copy(fontSize = 9.sp),
            color = colors.textSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surface1)
                .border(1.dp, colors.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Text(
            action,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

/**
 * Activates one content-row control in the current tab. Kept outside Compose so the Activity
 * window can drive the same focus tree when the bottom display does not own input focus.
 */
internal fun activateToolkitBody(
    ui: StreamingUiState,
    session: SessionController,
    onOpenSettings: () -> Unit,
    onReconnect: () -> Unit,
    onBackToList: () -> Unit,
) {
    when (ui.tab) {
        ToolTab.TOUCHPAD -> when (ui.toolkitBodyIndex) {
            0 -> touchpadClick(ui, session, 1)
            1 -> touchpadClick(ui, session, 16)
            2 -> touchpadClick(ui, session, 2)
            3 -> toggleDragLock(ui, session)
            4 -> onOpenSettings()
        }

        ToolTab.SHORTCUTS -> ToolkitShortcuts.getOrNull(ui.toolkitBodyIndex)?.let {
            session.shortcut(it.keys)
        }

        ToolTab.STATS -> when (ui.toolkitBodyIndex) {
            0 -> onReconnect()
            1 -> onBackToList()
        }

        ToolTab.KEYBOARD -> Unit
    }
}

/**
 * Local-operation pad navigation: tab row first, then the content row.
 */
private fun handleToolkitPadKey(
    ui: StreamingUiState,
    session: SessionController,
    event: KeyEvent,
    keyboardDefault: ImeMode,
    onSystemIme: () -> Unit,
    onActivateBody: () -> Unit,
): Boolean {
    if (event.action != KeyEvent.ACTION_UP) return false
    if (ui.imeMode != ImeMode.HIDDEN || ui.localMenu) return false
    return when (event.keyCode) {
        KeyEvent.KEYCODE_BUTTON_L1 -> {
            session.releaseAllKeys()
            session.releaseAllMouseButtons()
            ui.moveTab(-1, keyboardDefault)
            true
        }

        KeyEvent.KEYCODE_BUTTON_R1 -> {
            session.releaseAllKeys()
            session.releaseAllMouseButtons()
            ui.moveTab(1, keyboardDefault)
            true
        }

        KeyEvent.KEYCODE_DPAD_LEFT -> {
            ui.moveToolkitFocus(-1)
            true
        }

        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            ui.moveToolkitFocus(1)
            true
        }

        KeyEvent.KEYCODE_DPAD_UP -> {
            ui.moveToolkitRow(down = false)
            true
        }

        KeyEvent.KEYCODE_DPAD_DOWN -> {
            ui.moveToolkitRow(down = true)
            true
        }

        KeyEvent.KEYCODE_BUTTON_A -> {
            if (ui.toolkitFocusRow == 0) {
                ui.activateToolkitFocus(onSystemIme, keyboardDefault)
            } else {
                onActivateBody()
            }
            true
        }

        KeyEvent.KEYCODE_BUTTON_B -> {
            ui.localMenu = true
            true
        }

        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true

        else -> false
    }
}
