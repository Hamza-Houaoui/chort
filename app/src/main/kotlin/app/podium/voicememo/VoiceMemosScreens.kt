package app.podium.voicememo

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podium.core.designsystem.component.FocusList
import app.podium.core.designsystem.component.KeyboardAwayOnLeave
import app.podium.core.designsystem.component.ListInputEffect
import app.podium.core.designsystem.component.LocalMiniature
import app.podium.core.designsystem.component.LocalOverlayHost
import app.podium.core.designsystem.component.LocalScreenInsets
import app.podium.core.designsystem.component.MenuAction
import app.podium.core.designsystem.component.MenuPreview
import app.podium.core.designsystem.component.MenuRow
import app.podium.core.designsystem.component.MenuSpec
import app.podium.core.designsystem.component.PaperTextField
import app.podium.core.designsystem.l10n.LocalAppStrings
import app.podium.core.designsystem.symbol.PodiumSymbol
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.designsystem.theme.Spacing
import app.podium.core.designsystem.type.PodiumText
import app.podium.core.interaction.rememberFocusListState
import app.podium.core.interaction.rememberPodiumHaptics
import app.podium.player.api.PlaybackController

private sealed interface MemoRow {
    val key: String
    data object Meter : MemoRow { override val key = "meter" }
    data object Record : MemoRow { override val key = "record" }
    data object Stop : MemoRow { override val key = "stop" }
    data class Section(val title: String) : MemoRow { override val key = "sec:$title" }
    data class Item(val memo: VoiceMemo) : MemoRow { override val key = "memo:${memo.uri}" }
}

/**
 * Voice Memos (like the iPod's): Record starts the microphone, a live level meter and timer show
 * it working; Pause / Resume and Stop & save. Below, the saved memos: Center plays or stops one,
 * a long press offers Rename, Share and Delete.
 */
@Composable
fun VoiceMemosScreen(
    controller: PlaybackController,
    onRename: (uri: Uri, currentName: String) -> Unit,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val store = remember { VoiceMemoStore.get(context) }
    val memos by store.memos.collectAsStateWithLifecycle()
    val recorder by store.recorder.collectAsStateWithLifecycle()
    val levels by store.levels.collectAsStateWithLifecycle()
    val playing by store.playing.collectAsStateWithLifecycle()
    val position by store.playPosition.collectAsStateWithLifecycle()
    val overlay = LocalOverlayHost.current
    val haptics = rememberPodiumHaptics()
    var denied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { store.refresh() }
    // Leaving the screen stops a memo playing; a recording carries on until it's stopped.
    DisposableEffect(Unit) { onDispose { store.stopPlayback() } }

    val startRecording: () -> Unit = {
        controller.pause()
        if (store.start()) haptics.confirm() else haptics.reject()
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) startRecording()
    }

    val recording = recorder as? RecorderState.Recording
    val rows = remember(memos, recording != null, denied) {
        buildList {
            add(MemoRow.Meter)
            add(MemoRow.Record)
            if (recording != null) add(MemoRow.Stop)
            if (denied) add(MemoRow.Section("CHORT needs microphone access to record. Allow it in the system settings."))
            if (memos.isEmpty()) {
                add(MemoRow.Section("No voice memos yet."))
            } else {
                add(MemoRow.Section("${memos.size} memo" + if (memos.size == 1) "" else "s"))
                memos.forEach { add(MemoRow.Item(it)) }
            }
        }
    }

    val focus = rememberFocusListState("voice-memos")
    val activate: (Int) -> Unit = { index ->
        when (val row = rows.getOrNull(index)) {
            MemoRow.Record -> when {
                recording != null -> { haptics.toggle(on = recording.paused); store.togglePause() }
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startRecording()
                else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
            MemoRow.Stop -> { haptics.confirm(); store.stop() }
            is MemoRow.Item -> {
                if (playing != row.memo.uri) controller.pause()
                store.togglePlay(row.memo)
            }
            else -> Unit
        }
    }
    val longPress: (Int) -> Unit = { index ->
        val row = rows.getOrNull(index)
        if (row is MemoRow.Item) {
            val memo = row.memo
            overlay.show(
                MenuSpec(
                    memo.name,
                    listOf(
                        MenuAction(if (playing == memo.uri) "Stop" else "Play") { activate(index) },
                        MenuAction("Rename") { onRename(memo.uri, memo.name) },
                        MenuAction("Share") { store.share(memo) },
                        MenuAction("Delete") {
                            overlay.show(MenuSpec("Delete \"${memo.name}\"?", listOf(MenuAction("Delete") { store.delete(memo.uri) })))
                        },
                    ),
                ),
            )
        }
    }
    ListInputEffect(focus, onActivate = activate, onLongPress = longPress)

    FocusList(
        items = rows,
        state = focus,
        key = { it.key },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        onLongPress = longPress,
        focusable = { it !is MemoRow.Section && it !is MemoRow.Meter },
        preview = { MenuPreview.Instrument },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            MemoRow.Meter -> RecorderMeter(recording, levels)
            MemoRow.Record -> MenuRow(
                label = when {
                    recording == null -> strings.record
                    recording.paused -> strings.resume
                    else -> strings.pause
                },
                focused = focused,
                leading = if (recording != null && !recording.paused) PodiumSymbol.Pause else PodiumSymbol.Play,
                showChevron = false,
            )
            MemoRow.Stop -> MenuRow(strings.stopAndSave, focused, leading = PodiumSymbol.Check, showChevron = false)
            is MemoRow.Section -> PodiumText(
                text = row.title,
                style = PodiumTheme.type.caption,
                color = PodiumTheme.colors.labelSecondary,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.l, vertical = 6.dp)
                    .semantics { heading() },
            )
            is MemoRow.Item -> {
                val memo = row.memo
                val isPlaying = playing == memo.uri
                val value = if (isPlaying) {
                    "${VoiceMemoStore.formatDuration(position)} / ${VoiceMemoStore.formatDuration(memo.durationMs)}"
                } else {
                    VoiceMemoStore.formatDuration(memo.durationMs)
                }
                MenuRow(
                    label = memo.name,
                    focused = focused,
                    value = value,
                    leading = if (isPlaying) PodiumSymbol.Pause else PodiumSymbol.Note,
                    showChevron = false,
                )
            }
        }
    }
}

/** The timer and a live level meter (a scrolling waveform of the microphone) while recording. */
@Composable
private fun RecorderMeter(recording: RecorderState.Recording?, levels: List<Float>) {
    val colors = PodiumTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PodiumText(
            text = VoiceMemoStore.formatDuration(recording?.elapsedMs ?: 0L),
            style = PodiumTheme.type.title.copy(fontSize = 30.sp, lineHeight = 36.sp),
            color = if (recording != null && !recording.paused) colors.labelPrimary else colors.labelSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PodiumText(
            text = when {
                recording == null -> "Ready to record"
                recording.paused -> "Paused"
                else -> "Recording…"
            },
            style = PodiumTheme.type.caption,
            color = colors.labelSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val active = colors.labelPrimary
        val rest = colors.labelTertiary
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(top = Spacing.s),
        ) {
            val bars = VoiceMemoStore.LEVELS
            val gap = 2.dp.toPx()
            val w = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(1f)
            val padded = List(bars - levels.size) { 0f } + levels
            padded.forEachIndexed { i, level ->
                val h = (size.height * (0.06f + 0.94f * level)).coerceAtLeast(2f)
                drawRoundRect(
                    color = if (recording != null && level > 0f) active else rest,
                    topLeft = Offset(i * (w + gap), (size.height - h) / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2f, w / 2f),
                )
            }
        }
    }
}

/** Names a memo: the keyboard, then Save name. */
@Composable
fun RenameVoiceMemoScreen(uri: Uri, currentName: String, onDone: () -> Unit) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val store = remember { VoiceMemoStore.get(context) }
    var name by rememberSaveable(uri) { mutableStateOf(currentName) }
    val fieldFocus = remember { FocusRequester() }
    val haptics = rememberPodiumHaptics()
    val save: () -> Unit = {
        if (name.isBlank()) haptics.reject() else {
            store.rename(uri, name)
            haptics.confirm()
            onDone()
        }
    }
    val rows = listOf("field", "done")
    val focus = rememberFocusListState("rename-memo")
    if (!LocalMiniature.current) {
        LaunchedEffect(Unit) {
            fieldFocus.requestFocus()
            focus.focus(1)
        }
        KeyboardAwayOnLeave()
    }
    val activate: (Int) -> Unit = { i -> if (i == 0) fieldFocus.requestFocus() else save() }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = rows,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        focusable = { it == "done" },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        if (row == "field") {
            PaperTextField(name, { name = it }, fieldFocus, save, placeholder = strings.saveName, description = strings.saveName)
        } else {
            MenuRow(strings.saveName, focused, enabled = name.isNotBlank(), showChevron = false)
        }
    }
}

