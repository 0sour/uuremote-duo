package com.sour.duoremote.ui.streaming

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.data.DuoSettings
import com.sour.duoremote.session.SessionController
import com.sour.duoremote.ui.components.feedbackAction
import com.sour.duoremote.ui.theme.DuoMonoStyle
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val VK_ESC = 27
private const val VK_BACKSPACE = 8
private const val VK_TAB = 9
private const val VK_ENTER = 13
private const val VK_SHIFT = 16
private const val VK_CTRL = 17
private const val VK_ALT = 18
private const val VK_SPACE = 32
private const val VK_LEFT = 37
private const val VK_UP = 38
private const val VK_RIGHT = 39
private const val VK_DOWN = 40
private const val VK_WIN = 91

private val PUNCT_VK = mapOf(
    '-' to 189, '=' to 187, '[' to 219, ']' to 221, '\\' to 220,
    ';' to 186, '\'' to 222, ',' to 188, '.' to 190, '/' to 191,
    '`' to 192,
)

/** ASCII symbols that require Shift on a US layout. */
private val SHIFTED_SYMBOLS = mapOf(
    '!' to '1', '@' to '2', '#' to '3', '$' to '4', '%' to '5',
    '^' to '6', '&' to '7', '*' to '8', '(' to '9', ')' to '0',
    '_' to '-', '+' to '=', '{' to '[', '}' to ']', '|' to '\\',
    ':' to ';', '"' to '\'', '<' to ',', '>' to '.', '?' to '/',
    '~' to '`',
)

@Composable
fun KeyboardPanel(
    ui: StreamingUiState,
    settings: DuoSettings,
    session: SessionController,
    compact: Boolean,
) {
    val colors = LocalDuoColors.current
    val context = LocalContext.current
    val engine = remember(context) { PinyinEngine.get(context) }
    val candidates = ui.candidates

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface2)
                    .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                    .clickable { ui.imeMode = ImeMode.BUILTIN }
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.Keyboard,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    if (ui.composition.isNotEmpty()) ui.composition
                    else if (ui.englishMode) "English 模式：直接发送按键…"
                    else "内置拼音输入中文，或唤起系统输入法…",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ui.composition.isNotEmpty()) colors.accent else colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.width(1.dp).height(16.dp).background(colors.accent))
            }
            Box(
                Modifier
                    .width(62.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface1)
                    .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                    .clickable { ui.imeMode = ImeMode.SYSTEM },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "系统",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colors.textPrimary,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface2)
                .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (ui.composition.isNotEmpty()) {
                Text(
                    ui.composition,
                    style = DuoMonoStyle.copy(fontSize = 10.sp),
                    color = colors.accent,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .drawBehind {
                            drawLine(
                                colors.accent,
                                Offset(0f, size.height),
                                Offset(size.width, size.height),
                                1.dp.toPx(),
                            )
                        },
                )
                candidates.forEachIndexed { index, word ->
                    Box(
                        Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (index == ui.candidateIndex) colors.accentSoft else Color.Transparent)
                            .clickable { commitCandidate(ui, session, index) }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            word,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (index == ui.candidateIndex) colors.accent else colors.textPrimary,
                        )
                    }
                }
            } else {
                Text(
                    if (ui.englishMode) "English · 按键直接发送" else "内置拼音 · 空格上屏首选",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "内置拼音 · 空格上屏首选",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textTertiary,
                maxLines = 1,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (ui.symbolPage) {
                    KeyboardRow {
                        Key("Esc", 1.3f, special = true) { session.tapKey(VK_ESC) }
                        "!@#$%^&*()".forEach { Key(it.toString(), 1f) { sendCharacter(ui, session, engine, settings, it) } }
                        Key("⌫", 1.3f, special = true, repeat = true) { backspace(ui, session, engine, settings) }
                    }
                    KeyboardRow {
                        Key("Tab", 1.3f, special = true) { session.tapKey(VK_TAB) }
                        "-_=+[]{}".forEach { Key(it.toString(), 1f) { sendCharacter(ui, session, engine, settings, it) } }
                        Key("\\", 1f) { sendCharacter(ui, session, engine, settings, '\\') }
                        Key("|", 1f) { sendCharacter(ui, session, engine, settings, '|') }
                    }
                    KeyboardRow {
                        Key("Caps", 1.3f, special = true) { session.tapKey(20) }
                        ";:'\",.<>".forEach { Key(it.toString(), 1f) { sendCharacter(ui, session, engine, settings, it) } }
                        Key("/", 1f) { sendCharacter(ui, session, engine, settings, '/') }
                        Key("?", 1f) { sendCharacter(ui, session, engine, settings, '?') }
                        Key("Enter", 1.8f, special = true) { enter(ui, session) }
                    }
                    KeyboardRow {
                        Key("Shift", 1.8f, special = true, active = VK_SHIFT in ui.modifiers) {
                            toggleModifier(ui, session, VK_SHIFT)
                        }
                        "`~€¥©®°±".forEach { Key(it.toString(), 1f) { sendCharacter(ui, session, engine, settings, it) } }
                        Key("×", 1f) { sendCharacter(ui, session, engine, settings, '×') }
                        Key("÷", 1f) { sendCharacter(ui, session, engine, settings, '÷') }
                        Key("Shift", 1.8f, special = true, active = VK_SHIFT in ui.modifiers) {
                            toggleModifier(ui, session, VK_SHIFT)
                        }
                    }
                } else {
                    KeyboardRow {
                        Key("Esc", 1.3f, special = true) { session.tapKey(VK_ESC) }
                        "1234567890".forEach { Key(it.toString(), 1f) { sendCharacter(ui, session, engine, settings, it) } }
                        Key("-", 1f) { sendCharacter(ui, session, engine, settings, '-') }
                        Key("=", 1f) { sendCharacter(ui, session, engine, settings, '=') }
                        Key("⌫", 1.3f, special = true, repeat = true) { backspace(ui, session, engine, settings) }
                    }
                    KeyboardRow {
                        Key("Tab", 1.3f, special = true) { session.tapKey(VK_TAB) }
                        "QWERTYUIOP".forEach { Key(it.toString(), 1f) { sendCharacter(ui, session, engine, settings, it.lowercaseChar()) } }
                        Key("[", 1f) { sendCharacter(ui, session, engine, settings, '[') }
                        Key("]", 1f) { sendCharacter(ui, session, engine, settings, ']') }
                        Key("\\", 1.3f) { sendCharacter(ui, session, engine, settings, '\\') }
                    }
                    KeyboardRow {
                        Key("Caps", 1.3f, special = true) { session.tapKey(20) }
                        "ASDFGHJKL".forEach { Key(it.toString(), 1f) { sendCharacter(ui, session, engine, settings, it.lowercaseChar()) } }
                        Key(";", 1f) { sendCharacter(ui, session, engine, settings, ';') }
                        Key("'", 1f) { sendCharacter(ui, session, engine, settings, '\'') }
                        Key("Enter", 1.8f, special = true) { enter(ui, session) }
                    }
                    KeyboardRow {
                        Key("Shift", 1.8f, special = true, active = VK_SHIFT in ui.modifiers) {
                            toggleModifier(ui, session, VK_SHIFT)
                        }
                        "ZXCVBNM".forEach { Key(it.toString(), 1f) { sendCharacter(ui, session, engine, settings, it.lowercaseChar()) } }
                        Key(",", 1f) { sendCharacter(ui, session, engine, settings, ',') }
                        Key(".", 1f) { sendCharacter(ui, session, engine, settings, '.') }
                        Key("/", 1f) { sendCharacter(ui, session, engine, settings, '/') }
                        Key("Shift", 1.8f, special = true, active = VK_SHIFT in ui.modifiers) {
                            toggleModifier(ui, session, VK_SHIFT)
                        }
                    }
                }
                KeyboardRow {
                    Key("Ctrl", 1.3f, mod = true, active = VK_CTRL in ui.modifiers) { toggleModifier(ui, session, VK_CTRL) }
                    Key("中/英", 1.3f, mod = true, active = !ui.englishMode) {
                        toggleLanguage(ui, engine, settings)
                    }
                    Key("Win", 1f, mod = true, active = VK_WIN in ui.modifiers) { toggleModifier(ui, session, VK_WIN) }
                    Key(if (ui.symbolPage) "ABC" else "?123", 1.2f, mod = true) {
                        ui.symbolPage = !ui.symbolPage
                    }
                    Key("Alt", 1.3f, mod = true, active = VK_ALT in ui.modifiers) { toggleModifier(ui, session, VK_ALT) }
                    Key("Space", 3.8f) { space(ui, session) }
                    Key("Alt", 1.3f, mod = true, active = VK_ALT in ui.modifiers) { toggleModifier(ui, session, VK_ALT) }
                    Key("←", 1f, special = true, repeat = true) { session.tapKey(VK_LEFT) }
                    Key("↑", 1f, special = true, repeat = true) { session.tapKey(VK_UP) }
                    Key("↓", 1f, special = true, repeat = true) { session.tapKey(VK_DOWN) }
                    Key("→", 1f, special = true, repeat = true) { session.tapKey(VK_RIGHT) }
                }
            }
        }
    }
}

@Composable
private fun KeyboardRow(
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Key(
    label: String,
    weight: Float,
    active: Boolean = false,
    mod: Boolean = false,
    special: Boolean = false,
    repeat: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDuoColors.current
    val isSpecial = mod || special
    val feedback = feedbackAction(onClick)
    val action = if (repeat) onClick else feedback
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    active -> colors.accentSoft
                    isSpecial -> colors.surface2
                    else -> colors.surface3
                },
            )
            .border(
                1.dp,
                if (active) colors.accent else colors.outline,
                RoundedCornerShape(8.dp),
            )
            .then(
                if (repeat) Modifier.keyRepeat(action) else Modifier.clickable(onClick = action),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (isSpecial) 11.sp else 12.sp,
                fontWeight = if (isSpecial) FontWeight.Medium else FontWeight.SemiBold,
            ),
            color = when {
                active -> colors.accent
                isSpecial -> colors.textSecondary
                else -> colors.textPrimary
            },
            maxLines = 1,
        )
    }
}

private fun Modifier.keyRepeat(onClick: () -> Unit): Modifier = pointerInput(onClick) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        onClick()
        var nextAt = SystemClock.uptimeMillis() + viewConfiguration.longPressTimeoutMillis
        while (true) {
            val wait = (nextAt - SystemClock.uptimeMillis()).coerceAtLeast(1L)
            val event = withTimeoutOrNull(wait) { awaitPointerEvent() }
            if (event == null) {
                onClick()
                nextAt = SystemClock.uptimeMillis() + 55L
            } else {
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
            }
        }
    }
}

private fun toggleModifier(ui: StreamingUiState, session: SessionController, vk: Int) {
    if (vk in ui.modifiers) {
        ui.modifiers = ui.modifiers - vk
        session.key(vk, false)
    } else {
        ui.modifiers = ui.modifiers + vk
        session.key(vk, true)
    }
}

private fun toggleLanguage(ui: StreamingUiState, engine: PinyinEngine, settings: DuoSettings) {
    ui.englishMode = !ui.englishMode
    ui.composition = ""
    ui.candidates = emptyList()
    ui.candidateIndex = 0
}

private fun sendCharacter(
    ui: StreamingUiState,
    session: SessionController,
    engine: PinyinEngine,
    settings: DuoSettings,
    char: Char,
) {
    if (!ui.englishMode && ui.composition.isNotEmpty() && char in '1'..'9') {
        val index = char.digitToInt() - 1
        if (index < ui.candidates.size) {
            commitCandidate(ui, session, index)
            return
        }
    }
    if (!ui.englishMode && char.isLetter()) {
        ui.composition += char.lowercaseChar()
        ui.candidates = engine.candidates(ui.composition, settings.candidateCount)
        ui.candidateIndex = 0
        return
    }
    val modifiers = ui.modifiers
    if (!ui.englishMode && char in ",.;'") {
        session.textInput(
            when (char) {
                ',' -> "，"
                '.' -> "。"
                ';' -> "；"
                '\'' -> "’"
                else -> char.toString()
            },
        )
        return
    }
    val shifted = SHIFTED_SYMBOLS[char]
    val base = shifted ?: char
    val vk = when {
        base.isLetter() -> base.uppercaseChar().code
        base.isDigit() -> base.code
        else -> PUNCT_VK[base]
    }
    if (vk == null) {
        // Symbols without a stable VK mapping (e.g. currency/degree signs) go as text.
        session.textInput(char.toString())
        return
    }
    if (modifiers.isEmpty() && shifted == null) {
        session.tapKey(vk)
        return
    }
    val shiftedHeld = shifted != null && VK_SHIFT !in modifiers
    if (shiftedHeld) session.key(VK_SHIFT, true)
    session.tapKey(vk)
    modifiers.forEach { session.key(it, false) }
    if (shiftedHeld) session.key(VK_SHIFT, false)
    if (modifiers.isNotEmpty()) ui.modifiers = emptySet()
}

private fun backspace(
    ui: StreamingUiState,
    session: SessionController,
    engine: PinyinEngine,
    settings: DuoSettings,
) {
    if (ui.composition.isNotEmpty()) {
        ui.composition = ui.composition.dropLast(1)
        ui.candidates = engine.candidates(ui.composition, settings.candidateCount)
        ui.candidateIndex = 0
    } else {
        session.tapKey(VK_BACKSPACE)
    }
}

private fun space(ui: StreamingUiState, session: SessionController) {
    if (ui.composition.isNotEmpty() && ui.candidates.isNotEmpty()) {
        commitCandidate(ui, session, 0)
    } else {
        session.tapKey(VK_SPACE)
    }
}

private fun enter(ui: StreamingUiState, session: SessionController) {
    if (ui.composition.isNotEmpty()) commitCandidate(ui, session, 0)
    session.tapKey(VK_ENTER)
}

private fun commitCandidate(ui: StreamingUiState, session: SessionController, index: Int) {
    val word = ui.candidates.getOrNull(index) ?: return
    session.textInput(word)
    ui.previewText = (ui.previewText + word).takeLast(80)
    ui.composition = ""
    ui.candidates = emptyList()
    ui.candidateIndex = 0
}

@Composable
fun ImeOverlay(
    ui: StreamingUiState,
    settings: DuoSettings,
    session: SessionController,
    onClose: () -> Unit,
) {
    val colors = LocalDuoColors.current
    val context = LocalContext.current
    val engine = remember(context) { PinyinEngine.get(context) }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg.copy(alpha = 0.96f))
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.accent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            ui.composition.isNotEmpty() -> ui.composition
                            ui.previewText.isNotEmpty() -> ui.previewText
                            ui.englishMode -> "English 模式"
                            else -> "拼音输入…"
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                        color = if (ui.composition.isNotEmpty()) colors.accent else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ui.composition.isEmpty()) {
                        Box(Modifier.width(1.dp).height(18.dp).background(colors.accent))
                    }
                    Text(
                        if (ui.imeMode == ImeMode.SYSTEM) "系统输入法" else "内置拼音",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Box(
                    Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface1)
                        .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                        .clickable { ui.imeMode = ImeMode.SYSTEM },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "系统输入法",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textPrimary,
                    )
                }
                Box(
                    Modifier
                        .width(64.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accent)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "完成",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.onAccent,
                    )
                }
            }
            if (ui.imeMode == ImeMode.SYSTEM) {
                SystemImePanel(ui, session, onClose)
            } else {
                BuiltinImePanel(ui, settings, session, engine)
            }
        }
    }
}

@Composable
private fun BuiltinImePanel(
    ui: StreamingUiState,
    settings: DuoSettings,
    session: SessionController,
    engine: PinyinEngine,
) {
    val colors = LocalDuoColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface1)
                .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (ui.composition.isNotEmpty()) {
                Text(
                    ui.composition,
                    style = DuoMonoStyle.copy(fontSize = 13.sp),
                    color = colors.accent,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .drawBehind {
                            drawLine(
                                colors.accent,
                                Offset(0f, size.height),
                                Offset(size.width, size.height),
                                1.dp.toPx(),
                            )
                        },
                )
                ui.candidates.forEachIndexed { index, word ->
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (index == ui.candidateIndex) colors.accentSoft else Color.Transparent)
                            .clickable { commitCandidate(ui, session, index) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            word,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                            color = if (index == ui.candidateIndex) colors.accent else colors.textPrimary,
                        )
                    }
                }
            } else {
                Text(
                    if (ui.englishMode) "English · 按键直接发送" else "拼音候选",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (ui.composition.isNotEmpty() && ui.candidates.isNotEmpty()) {
                    "本地词典 · ${ui.candidateIndex + 1}/${ui.candidates.size} · 数字键选择"
                } else {
                    "内置拼音 · 空格上屏首选"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
                maxLines = 1,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface1)
                .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "远端输入位置",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium,
                ),
                color = colors.textTertiary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ui.previewText.ifEmpty { "候选上屏后写入电脑光标处" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = if (ui.previewText.isEmpty()) colors.textTertiary else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ui.previewText.isNotEmpty()) {
                    Box(Modifier.width(1.dp).height(18.dp).background(colors.accent))
                }
            }
            Text(
                if (ui.englishMode) "英文模式：按键以 VK 发送；不显示候选。"
                else "组合中的拼音只在本机显示；候选上屏后才写入电脑。",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = colors.textTertiary,
            )
        }

        Text(
            "内置键盘 · 本地拼音词典 · 空格上屏首选",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
            color = colors.textTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (ui.symbolPage) {
                ImeRow {
                    "!@#$%^&*()".forEach {
                        ImeKey(it.toString(), 1f) {
                            sendCharacter(ui, session, engine, settings, it)
                        }
                    }
                }
                ImeRow {
                    "-_=+[]{}".forEach {
                        ImeKey(it.toString(), 1f) {
                            sendCharacter(ui, session, engine, settings, it)
                        }
                    }
                    ImeKey("\\", 1f) { sendCharacter(ui, session, engine, settings, '\\') }
                    ImeKey("|", 1f) { sendCharacter(ui, session, engine, settings, '|') }
                }
                ImeRow {
                    ";:'\",.<>".forEach {
                        ImeKey(it.toString(), 1f) {
                            sendCharacter(ui, session, engine, settings, it)
                        }
                    }
                    ImeKey("/", 1f) { sendCharacter(ui, session, engine, settings, '/') }
                    ImeKey("?", 1f) { sendCharacter(ui, session, engine, settings, '?') }
                }
                ImeRow {
                    ImeKey("ABC", 1.4f, mod = true) { ui.symbolPage = false }
                    ImeKey("中/英", 1.4f, mod = true, active = !ui.englishMode) {
                        toggleLanguage(ui, engine, settings)
                    }
                    ImeKey("Space", 4f, mod = true) { space(ui, session) }
                    ImeKey("⌫", 1.6f, mod = true, repeat = true) { backspace(ui, session, engine, settings) }
                    ImeKey("Enter", 1.6f, mod = true) { enter(ui, session) }
                }
            } else {
                ImeRow {
                    "QWERTYUIOP".forEach {
                        ImeKey(it.toString(), 1f) {
                            sendCharacter(ui, session, engine, settings, it.lowercaseChar())
                        }
                    }
                }
                ImeRow {
                    "ASDFGHJKL".forEach {
                        ImeKey(it.toString(), 1f) {
                            sendCharacter(ui, session, engine, settings, it.lowercaseChar())
                        }
                    }
                }
                ImeRow {
                    ImeKey("?123", 1.4f, mod = true) { ui.symbolPage = true }
                    "ZXCVBNM".forEach {
                        ImeKey(it.toString(), 1f) {
                            sendCharacter(ui, session, engine, settings, it.lowercaseChar())
                        }
                    }
                    ImeKey("⌫", 1.6f, mod = true, repeat = true) { backspace(ui, session, engine, settings) }
                }
                ImeRow {
                    ImeKey("中/英", 1.4f, mod = true, active = !ui.englishMode) {
                        toggleLanguage(ui, engine, settings)
                    }
                    ImeKey("，", 1f, mod = true) { sendCharacter(ui, session, engine, settings, ',') }
                    ImeKey("Space", 4f, mod = true) { space(ui, session) }
                    ImeKey("。", 1f, mod = true) { sendCharacter(ui, session, engine, settings, '.') }
                    ImeKey("Enter", 1.6f, mod = true) { enter(ui, session) }
                }
            }
        }
        Text(
            "中文候选经 text_input 发送；英文/控制键走 kbd_press；空格提交首选，数字键选候选。",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = colors.textTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ImeRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ImeKey(
    label: String,
    weight: Float,
    active: Boolean = false,
    mod: Boolean = false,
    repeat: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDuoColors.current
    val feedback = feedbackAction(onClick)
    val action = if (repeat) onClick else feedback
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    active -> colors.accentSoft
                    mod -> colors.surface3
                    else -> colors.surface2
                },
            )
            .border(
                1.dp,
                if (active) colors.accent else colors.outline,
                RoundedCornerShape(8.dp),
            )
            .then(
                if (repeat) Modifier.keyRepeat(action) else Modifier.clickable(onClick = action),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (mod) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = when {
                active -> colors.accent
                mod -> colors.textTertiary
                else -> colors.textSecondary
            },
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SystemImePanel(ui: StreamingUiState, session: SessionController, onClose: () -> Unit) {
    val colors = LocalDuoColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var sentText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // The Presentation window needs a frame before its focus owner can accept the IME.
        delay(120)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DuoRadius.md))
                .background(colors.accentSoft)
                .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(DuoRadius.md))
                .padding(10.dp),
        ) {
            Text(
                "系统输入法 · 候选和键盘由 Gboard/搜狗绘制；上屏文字会发送到电脑光标处。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(DuoRadius.md))
                .background(colors.surface1)
                .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.md))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("远端输入位置", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    value = next
                    if (next.composition == null) {
                        val text = next.text
                        if (text.length >= sentText.length && text.startsWith(sentText)) {
                            val addition = text.substring(sentText.length)
                            if (addition.isNotEmpty()) session.textInput(addition)
                        } else {
                            // The IME replaced or deleted text; mirror the edit on the remote side.
                            val common = text.commonPrefixWith(sentText).length
                            repeat(sentText.length - common) { session.tapKey(VK_BACKSPACE) }
                            if (text.length > common) session.textInput(text.substring(common))
                        }
                        sentText = text
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(DuoRadius.md))
                    .background(colors.surfaceInset)
                    .border(1.dp, colors.accent.copy(alpha = 0.55f), RoundedCornerShape(DuoRadius.md))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .focusRequester(focus),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                "在此输入，候选由系统输入法显示…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                            )
                        }
                        inner()
                    }
                },
            )
            Text(
                "候选上屏后写入电脑；拼音组合文本只在本机显示。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "系统键盘区域（由系统绘制）· 若键盘未弹出，点一下输入框",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("返回内置键盘") {
                    keyboard?.hide()
                    ui.imeMode = ImeMode.BUILTIN
                }
                SmallButton("完成", primary = true, onClick = {
                    keyboard?.hide()
                    onClose()
                })
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    val colors = LocalDuoColors.current
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(DuoRadius.md))
            .background(if (primary) colors.accent else colors.surface2)
            .border(1.dp, if (primary) colors.accent else colors.outline, RoundedCornerShape(DuoRadius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (primary) colors.onAccent else colors.textPrimary,
        )
    }
}
