package app.podium.clock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podium.AppGraph
import app.podium.core.designsystem.component.FocusList
import app.podium.core.designsystem.component.ListInputEffect
import app.podium.core.designsystem.component.LocalScreenInsets
import app.podium.core.designsystem.component.MenuRow
import app.podium.core.designsystem.component.MessageState
import app.podium.core.designsystem.l10n.LocalAppStrings
import app.podium.core.designsystem.symbol.PodiumSymbol
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.designsystem.type.PodiumText
import app.podium.core.interaction.InputTargetEffect
import app.podium.core.interaction.PodiumInput
import app.podium.core.interaction.WheelButton
import app.podium.core.interaction.WheelContext
import app.podium.core.interaction.rememberFocusListState
import app.podium.feature.library.LibraryState
import app.podium.player.api.PlaybackController
import app.podium.quran.AllSurahs
import app.podium.quran.toPodiumTrack
import app.podium.radio.RadioCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ClockScreen(
    place: ClockPlace,
    repository: ClockRepository,
    graph: AppGraph,
    controller: PlaybackController,
    navigate: (ClockPlace) -> Unit,
    onBack: () -> Unit,
) {
    when (place) {
        ClockPlace.Menu -> ClockMainMenu(
            repository = repository,
            navigate = navigate,
        )
        ClockPlace.WorldClock -> WorldClockScreen(
            repository = repository,
            navigate = navigate,
        )
        ClockPlace.AddWorldCity -> AddWorldCityScreen(
            repository = repository,
        )
        ClockPlace.AlarmsList -> AlarmsListScreen(
            repository = repository,
            navigate = navigate,
        )
        is ClockPlace.EditAlarm -> EditAlarmScreen(
            alarmId = place.alarmId,
            repository = repository,
            navigate = navigate,
            onBack = onBack,
        )
        is ClockPlace.PickSoundType -> PickSoundTypeScreen(
            alarmId = place.alarmId,
            repository = repository,
            navigate = navigate,
            onBack = onBack,
        )
        is ClockPlace.PickSoundItem -> PickSoundItemScreen(
            alarmId = place.alarmId,
            soundType = place.soundType,
            repository = repository,
            graph = graph,
            onBack = onBack,
        )
        ClockPlace.Stopwatch -> StopwatchScreen()
        ClockPlace.Timer -> TimerScreen()
    }
}

private data class ClockMenuRow(
    val label: String,
    val value: String?,
    val action: () -> Unit,
)

@Composable
private fun ClockMainMenu(
    repository: ClockRepository,
    navigate: (ClockPlace) -> Unit,
) {
    val strings = LocalAppStrings.current
    val cityIds by repository.selectedCityIds.collectAsStateWithLifecycle()
    val alarms by repository.alarms.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current

    val rows = remember(strings, cityIds, alarms) {
        val activeCount = alarms.count { it.enabled }
        listOf(
            ClockMenuRow(
                label = strings.worldClock,
                value = "${cityIds.size}",
                action = { navigate(ClockPlace.WorldClock) },
            ),
            ClockMenuRow(
                label = strings.alarms,
                value = if (activeCount > 0) "$activeCount" else "${alarms.size}",
                action = { navigate(ClockPlace.AlarmsList) },
            ),
            ClockMenuRow(
                label = strings.stopwatch,
                value = null,
                action = { navigate(ClockPlace.Stopwatch) },
            ),
            ClockMenuRow(
                label = strings.timer,
                value = null,
                action = { navigate(ClockPlace.Timer) },
            ),
        )
    }

    val focus = rememberFocusListState("clock_main_menu")
    val activate: (Int) -> Unit = { idx -> rows.getOrNull(idx)?.action?.invoke() }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = { it.label },
        contentPadding = insets.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        MenuRow(
            label = item.label,
            focused = focused,
            value = item.value,
            showChevron = true,
        )
    }
}

@Composable
private fun WorldClockScreen(
    repository: ClockRepository,
    navigate: (ClockPlace) -> Unit,
) {
    val strings = LocalAppStrings.current
    val cityIds by repository.selectedCityIds.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick = System.currentTimeMillis()
        }
    }

    val cities = remember(cityIds, tick) { repository.worldCities() }
    val rows = remember(strings, cities, tick) {
        buildList {
            val addLabel = if (strings is app.podium.core.designsystem.l10n.ArabicStrings) "+ إضافة / إزالة مدن" else "+ Add / Remove Cities"
            val addVal = if (strings is app.podium.core.designsystem.l10n.ArabicStrings) "تخصيص" else "Customize"
            add(Triple("__ADD__", addLabel, addVal))
            for (c in cities) {
                add(Triple(c.id, "${c.name} (${c.relativeOffsetLabel()})", c.currentTimeFormatted()))
            }
        }
    }

    val focus = rememberFocusListState("world_clock_list")
    val activate: (Int) -> Unit = { idx ->
        val item = rows.getOrNull(idx)
        if (item?.first == "__ADD__") {
            navigate(ClockPlace.AddWorldCity)
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = rows,
        state = focus,
        key = { it.first },
        contentPadding = insets.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        MenuRow(
            label = item.second,
            focused = focused,
            value = item.third,
            showChevron = item.first == "__ADD__",
        )
    }
}

@Composable
private fun AddWorldCityScreen(
    repository: ClockRepository,
) {
    val selectedIds by repository.selectedCityIds.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current

    val focus = rememberFocusListState("world_clock_add_city")
    val activate: (Int) -> Unit = { idx ->
        ALL_WORLD_CITIES.getOrNull(idx)?.let { city ->
            repository.toggleWorldCity(city.id)
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = ALL_WORLD_CITIES,
        state = focus,
        key = { it.id },
        contentPadding = insets.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { city, _, focused ->
        val isSelected = city.id in selectedIds
        MenuRow(
            label = "${city.name}, ${city.country}",
            focused = focused,
            value = if (isSelected) "✓ Added" else city.currentTimeFormatted(),
            showChevron = false,
        )
    }
}

@Composable
private fun AlarmsListScreen(
    repository: ClockRepository,
    navigate: (ClockPlace) -> Unit,
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val alarms by repository.alarms.collectAsStateWithLifecycle()
    val insets = LocalScreenInsets.current

    var ignoringBattery by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true)
    }

    val rows = remember(strings, alarms, ignoringBattery) {
        val isAr = strings is app.podium.core.designsystem.l10n.ArabicStrings
        buildList {
            add(Triple("__NEW__", if (isAr) "+ منبه جديد (WakeIQ)" else "+ New WakeIQ Alarm", if (isAr) "إضافة" else "Add"))
            add(
                Triple(
                    "__BG__",
                    if (isAr) "حماية الخلفية والتشغيل" else "Background & Reboot Guard",
                    if (ignoringBattery) (if (isAr) "✓ مفعل" else "✓ Active") else (if (isAr) "اضغط للسماح" else "Tap to Allow"),
                ),
            )
            for (a in alarms) {
                add(
                    Triple(
                        a.id,
                        "${a.timeFormatted} • ${a.soundTitle}",
                        if (a.enabled) (if (isAr) "مفعل (${a.daysSummary})" else "ON (${a.daysSummary})") else (if (isAr) "معطل" else "OFF"),
                    ),
                )
            }
        }
    }

    val focus = rememberFocusListState("clock_alarms_list")
    val activate: (Int) -> Unit = { idx ->
        val row = rows.getOrNull(idx)
        when (row?.first) {
            null -> Unit
            "__NEW__" -> {
                val created = repository.createAlarm()
                navigate(ClockPlace.EditAlarm(created.id))
            }
            "__BG__" -> {
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                    ignoringBattery = true
                }
            }
            else -> navigate(ClockPlace.EditAlarm(row.first))
        }
    }
    val longPress: (Int) -> Unit = { idx ->
        val row = rows.getOrNull(idx)
        val alarm = row?.first?.let { repository.findAlarm(it) }
        if (alarm != null) {
            repository.updateAlarm(alarm.copy(enabled = !alarm.enabled))
        }
    }
    ListInputEffect(focus, onActivate = activate, onLongPress = longPress)

    FocusList(
        items = rows,
        state = focus,
        key = { it.first },
        contentPadding = insets.listPadding(),
        onActivate = activate,
        onLongPress = longPress,
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        MenuRow(
            label = item.second,
            focused = focused,
            value = item.third,
            showChevron = true,
        )
    }
}

private enum class TimeEditStep { NONE, HOUR, MINUTE }

@Composable
private fun EditAlarmScreen(
    alarmId: String,
    repository: ClockRepository,
    navigate: (ClockPlace) -> Unit,
    onBack: () -> Unit,
) {
    val alarms by repository.alarms.collectAsStateWithLifecycle()
    val alarm = remember(alarms, alarmId) { alarms.find { it.id == alarmId } }
    val insets = LocalScreenInsets.current

    if (alarm == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(PodiumSymbol.Settings, "Alarm Not Found", "Press Menu to go back.")
        }
        return
    }

    var timeEditStep by remember { mutableStateOf(TimeEditStep.NONE) }
    val focus = rememberFocusListState("edit_alarm_${alarm.id}")

    val rows = remember(alarm, timeEditStep) {
        val timeValue = when (timeEditStep) {
            TimeEditStep.NONE -> "${alarm.timeFormatted} (Tap OK to set)"
            TimeEditStep.HOUR -> "[${String.format(Locale.US, "%02d", alarm.hour)}]:${String.format(Locale.US, "%02d", alarm.minute)} (Turn Wheel)"
            TimeEditStep.MINUTE -> "${String.format(Locale.US, "%02d", alarm.hour)}:[${String.format(Locale.US, "%02d", alarm.minute)}] (Turn Wheel)"
        }
        listOf(
            "Time" to timeValue,
            "Status" to if (alarm.enabled) "ON" else "OFF",
            "Wake Sound (${alarm.soundType.label})" to alarm.soundTitle,
            "WakeIQ Smart Window" to "${alarm.smartWindowMinutes} min before",
            "WakeIQ Gradual Ramp" to "${alarm.rampDurationMinutes} min fade-in",
            "Repeat" to alarm.daysSummary,
            "Test WakeIQ Alarm Now" to "▶ Ring Now",
            "Delete Alarm" to "Remove",
        )
    }

    val activateRow: (Int) -> Unit = { idx ->
        when (idx) {
            0 -> timeEditStep = TimeEditStep.HOUR
            1 -> repository.updateAlarm(alarm.copy(enabled = !alarm.enabled))
            2 -> navigate(ClockPlace.PickSoundType(alarm.id))
            3 -> {
                val windows = listOf(0, 5, 10, 15, 30)
                val next = windows[(windows.indexOf(alarm.smartWindowMinutes) + 1) % windows.size]
                repository.updateAlarm(alarm.copy(smartWindowMinutes = next))
            }
            4 -> {
                val ramps = listOf(1, 3, 5, 10, 15)
                val next = ramps[(ramps.indexOf(alarm.rampDurationMinutes) + 1) % ramps.size]
                repository.updateAlarm(alarm.copy(rampDurationMinutes = next))
            }
            5 -> {
                val nextDays = when {
                    alarm.daysOfWeek.isEmpty() -> setOf(1, 2, 3, 4, 5)
                    alarm.daysOfWeek == setOf(1, 2, 3, 4, 5) -> setOf(1, 2, 3, 4, 5, 6, 7)
                    alarm.daysOfWeek.size == 7 -> setOf(6, 7)
                    else -> emptySet()
                }
                repository.updateAlarm(alarm.copy(daysOfWeek = nextDays))
            }
            6 -> repository.triggerAlarmNow(alarm)
            7 -> {
                repository.deleteAlarm(alarm.id)
                onBack()
            }
        }
    }

    if (timeEditStep == TimeEditStep.NONE) {
        ListInputEffect(focus, activateRow)
    } else {
        InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
            when (input) {
                is PodiumInput.Rotate -> {
                    if (timeEditStep == TimeEditStep.HOUR) {
                        val nextH = ((alarm.hour + input.detents) % 24 + 24) % 24
                        repository.updateAlarm(alarm.copy(hour = nextH, enabled = true))
                    } else {
                        val nextM = ((alarm.minute + input.detents) % 60 + 60) % 60
                        repository.updateAlarm(alarm.copy(minute = nextM, enabled = true))
                    }
                    true
                }
                is PodiumInput.Press -> when (input.button) {
                    WheelButton.CENTER -> {
                        timeEditStep = if (timeEditStep == TimeEditStep.HOUR) TimeEditStep.MINUTE else TimeEditStep.NONE
                        true
                    }
                    WheelButton.MENU -> {
                        timeEditStep = TimeEditStep.NONE
                        true
                    }
                    else -> false
                }
                else -> false
            }
        }
    }

    FocusList(
        items = rows,
        state = focus,
        key = { it.first },
        contentPadding = insets.listPadding(),
        onActivate = activateRow,
        modifier = Modifier.fillMaxSize(),
    ) { pair, idx, focused ->
        MenuRow(
            label = pair.first,
            focused = focused,
            value = pair.second,
            showChevron = idx == 2,
        )
    }
}

@Composable
private fun PickSoundTypeScreen(
    alarmId: String,
    repository: ClockRepository,
    navigate: (ClockPlace) -> Unit,
    onBack: () -> Unit,
) {
    val alarms by repository.alarms.collectAsStateWithLifecycle()
    val alarm = remember(alarms, alarmId) { alarms.find { it.id == alarmId } }
    val insets = LocalScreenInsets.current
    val types = AlarmSoundType.entries

    val focus = rememberFocusListState("pick_sound_type_$alarmId")
    val activate: (Int) -> Unit = { idx ->
        val chosen = types.getOrNull(idx)
        if (chosen != null && alarm != null) {
            if (chosen == AlarmSoundType.MELODIC_CHIME) {
                repository.updateAlarm(
                    alarm.copy(
                        soundType = AlarmSoundType.MELODIC_CHIME,
                        soundId = "chime",
                        soundTitle = "Gentle Morning Chime",
                        soundSubtitle = "WakeIQ Default",
                    ),
                )
                onBack()
            } else {
                navigate(ClockPlace.PickSoundItem(alarmId, chosen))
            }
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = types,
        state = focus,
        key = { it.name },
        contentPadding = insets.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { t, _, focused ->
        MenuRow(
            label = t.label,
            focused = focused,
            value = if (alarm?.soundType == t) "✓ Active" else null,
            showChevron = t != AlarmSoundType.MELODIC_CHIME,
        )
    }
}

private data class SoundOptionItem(
    val id: String,
    val title: String,
    val subtitle: String,
)

@Composable
private fun PickSoundItemScreen(
    alarmId: String,
    soundType: AlarmSoundType,
    repository: ClockRepository,
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val alarms by repository.alarms.collectAsStateWithLifecycle()
    val alarm = remember(alarms, alarmId) { alarms.find { it.id == alarmId } }
    val insets = LocalScreenInsets.current

    val songsState by graph.library.songs.collectAsStateWithLifecycle()
    val downloads by graph.downloads.completedTracks.collectAsStateWithLifecycle()
    val ytmLikes by graph.online.likedTracks.collectAsStateWithLifecycle(initialValue = emptyList())
    val scLikes by graph.soundcloudRepository.likedTracks.collectAsStateWithLifecycle()
    val scCharts by graph.soundcloudRepository.chartsTracks.collectAsStateWithLifecycle()
    val radioFavs by graph.radioRepository.favorites.collectAsStateWithLifecycle()
    val radioRecents by graph.radioRepository.recents.collectAsStateWithLifecycle()
    var radioLoaded by remember { mutableStateOf<List<app.podium.radio.RadioStation>>(emptyList()) }

    LaunchedEffect(soundType) {
        when (soundType) {
            AlarmSoundType.RADIO -> {
                radioLoaded = graph.radioRepository.loadCategory(RadioCategory.TUNISIA)
            }
            AlarmSoundType.SOUNDCLOUD -> {
                graph.soundcloudRepository.loadLikes()
                graph.soundcloudRepository.loadCharts()
            }
            AlarmSoundType.YTM_LIKED -> {
                graph.online.refreshLibrary()
            }
            else -> Unit
        }
    }

    val options = remember(soundType, songsState, downloads, ytmLikes, scLikes, scCharts, radioFavs, radioRecents, radioLoaded) {
        val allLocalSongs = (songsState as? LibraryState.Ready)?.tracks.orEmpty()
        when (soundType) {
            AlarmSoundType.RADIO -> {
                val stations = (radioFavs + radioRecents + radioLoaded).distinctBy { it.id }
                stations.map { SoundOptionItem(it.id, it.name, it.country ?: "Radio") }
            }
            AlarmSoundType.ALBUM -> {
                val albums = allLocalSongs.mapNotNull { it.album }.distinctBy { it.id.toString() }
                albums.map { SoundOptionItem(it.id.toString(), it.title, it.albumArtist ?: "Album") }
            }
            AlarmSoundType.LOCAL_SONG -> {
                allLocalSongs.map { SoundOptionItem(it.id.toString(), it.title, it.artistDisplay.ifBlank { "Local Song" }) }
            }
            AlarmSoundType.DOWNLOADED -> {
                downloads.map { SoundOptionItem(it.id.toString(), it.title, it.artistDisplay.ifBlank { "Downloaded" }) }
            }
            AlarmSoundType.YTM_LIKED -> {
                buildList {
                    add(SoundOptionItem("__ALL_YTM_LIKES__", "All Liked Songs (Shuffle)", "YouTube Music"))
                    ytmLikes.forEach { t ->
                        add(SoundOptionItem(t.id.toString(), t.title, t.artistDisplay.ifBlank { "YouTube Music" }))
                    }
                }
            }
            AlarmSoundType.SOUNDCLOUD -> {
                val allSc = (scLikes + scCharts).distinctBy { it.id }
                buildList {
                    add(SoundOptionItem("__ALL_SC__", "SoundCloud Likes / Top Charts", "SoundCloud"))
                    allSc.forEach { t ->
                        add(SoundOptionItem(t.id.toString(), t.title, t.artistName))
                    }
                }
            }
            AlarmSoundType.QURAN -> {
                AllSurahs.map { s ->
                    SoundOptionItem(s.id.toString(), "${s.nameArabic} (${s.nameEnglish})", "${s.versesCount} آيات")
                }
            }
            AlarmSoundType.MELODIC_CHIME -> listOf(
                SoundOptionItem("chime", "Gentle Morning Chime", "WakeIQ Default"),
            )
        }
    }

    if (options.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(
                PodiumSymbol.Library,
                "No Items Found",
                "Add or load items in ${soundType.label} first, then select one here.",
            )
        }
        return
    }

    val focus = rememberFocusListState("pick_sound_item_${soundType.name}")
    val activate: (Int) -> Unit = { idx ->
        val item = options.getOrNull(idx)
        if (item != null && alarm != null) {
            repository.updateAlarm(
                alarm.copy(
                    soundType = soundType,
                    soundId = item.id,
                    soundTitle = item.title,
                    soundSubtitle = item.subtitle,
                ),
            )
            onBack()
        }
    }
    ListInputEffect(focus, activate)

    FocusList(
        items = options,
        state = focus,
        key = { it.id },
        contentPadding = insets.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { item, _, focused ->
        MenuRow(
            label = item.title,
            focused = focused,
            value = item.subtitle,
            showChevron = false,
        )
    }
}

private data class StopwatchLap(
    val number: Int,
    val splitMs: Long,
    val totalMs: Long,
)

private fun formatStopwatchMs(ms: Long): String {
    val mins = (ms / 60_000L)
    val secs = (ms / 1000L) % 60L
    val hundredths = (ms % 1000L) / 10L
    return String.format(Locale.US, "%02d:%02d.%02d", mins, secs, hundredths)
}

@Composable
private fun StopwatchScreen() {
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val insets = LocalScreenInsets.current
    val lang = app.podium.core.designsystem.l10n.LocalAppLanguage.current
    val haptics = app.podium.core.interaction.rememberPodiumHaptics()

    var running by remember { mutableStateOf(false) }
    var accumulatedMs by remember { mutableLongStateOf(0L) }
    var startUptimeMs by remember { mutableLongStateOf(0L) }
    var displayMs by remember { mutableLongStateOf(0L) }
    var laps by remember { mutableStateOf<List<StopwatchLap>>(emptyList()) }
    val lapListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(running) {
        if (running) {
            startUptimeMs = SystemClock.uptimeMillis()
            while (running) {
                displayMs = accumulatedMs + (SystemClock.uptimeMillis() - startUptimeMs)
                delay(30)
            }
        }
    }

    val recordLap = {
        val currentTotal = if (running) {
            accumulatedMs + (SystemClock.uptimeMillis() - startUptimeMs)
        } else {
            displayMs
        }
        if (currentTotal > 0L) {
            val prevTotal = laps.lastOrNull()?.totalMs ?: 0L
            val split = (currentTotal - prevTotal).coerceAtLeast(0L)
            val nextNum = laps.size + 1
            laps = laps + StopwatchLap(number = nextNum, splitMs = split, totalMs = currentTotal)
            haptics.confirm()
            scope.launch {
                lapListState.animateScrollToItem(0)
            }
        }
    }

    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        when (input) {
            is PodiumInput.Rotate -> {
                if (laps.isNotEmpty()) {
                    scope.launch {
                        val target = (lapListState.firstVisibleItemIndex + input.detents).coerceIn(0, (laps.size - 1).coerceAtLeast(0))
                        lapListState.animateScrollToItem(target)
                    }
                }
                true
            }
            is PodiumInput.Press -> when (input.button) {
                WheelButton.PLAY_PAUSE -> {
                    haptics.confirm()
                    if (running) {
                        accumulatedMs += SystemClock.uptimeMillis() - startUptimeMs
                        displayMs = accumulatedMs
                        running = false
                    } else {
                        running = true
                    }
                    true
                }
                WheelButton.CENTER -> {
                    if (running) {
                        // Record a Tour / Loop while running
                        recordLap()
                    } else {
                        // Start when paused/stopped
                        haptics.confirm()
                        running = true
                    }
                    true
                }
                WheelButton.PREVIOUS, WheelButton.NEXT -> {
                    haptics.confirm()
                    running = false
                    accumulatedMs = 0L
                    displayMs = 0L
                    laps = emptyList()
                    true
                }
                else -> false
            }
            else -> false
        }
    }

    val formatted = formatStopwatchMs(displayMs)
    val tourWord = when (lang.code) {
        "ar" -> "دورة"
        "fr" -> "Tour"
        "tr" -> "Tur"
        "ru" -> "Круг"
        else -> "Tour"
    }
    val hintText = when (lang.code) {
        "ar" -> if (running) "OK: تسجيل دورة • ⏯: إيقاف مؤقت • ⏮/⏭: تصفير" else "OK / ⏯: تشغيل • ⏮/⏭: تصفير"
        "fr" -> if (running) "OK: Tour • ⏯: Pause • ⏮/⏭: Réinitialiser" else "OK / ⏯: Démarrer • ⏮/⏭: Réinitialiser"
        "tr" -> if (running) "OK: Tur • ⏯: Duraklat • ⏮/⏭: Sıfırla" else "OK / ⏯: Başlat • ⏮/⏭: Sıfırla"
        "ru" -> if (running) "OK: Круг • ⏯: Пауза • ⏮/⏭: Сброс" else "OK / ⏯: Старт • ⏮/⏭: Сброс"
        else -> if (running) "OK: Tour (Loop) • ⏯: Pause • ⏮/⏭: Reset" else "OK / ⏯: Start • ⏮/⏭: Reset"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.top + 6.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Large Timer Display at the top
        BasicText(
            text = formatted,
            style = TextStyle(
                color = colors.labelPrimary,
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(4.dp))
        PodiumText(
            hintText,
            type.caption,
            colors.labelSecondary,
        )
        Spacer(Modifier.height(12.dp))

        // Divider line above Loops list
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.labelSecondary.copy(alpha = 0.22f)),
        )

        // Loops / Tours List
        val reversedLaps = remember(laps) { laps.asReversed() }
        androidx.compose.foundation.lazy.LazyColumn(
            state = lapListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(reversedLaps.size, key = { idx -> reversedLaps[idx].number }) { idx ->
                val lap = reversedLaps[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.labelPrimary.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = "$tourWord #${lap.number}",
                        style = TextStyle(
                            color = colors.labelPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    BasicText(
                        text = "+${formatStopwatchMs(lap.splitMs)}",
                        style = TextStyle(
                            color = colors.labelSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    BasicText(
                        text = formatStopwatchMs(lap.totalMs),
                        style = TextStyle(
                            color = colors.labelPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerScreen() {
    val context = LocalContext.current
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val insets = LocalScreenInsets.current
    val lang = app.podium.core.designsystem.l10n.LocalAppLanguage.current

    // Up to 24 hours (1440 minutes)
    val maxTimerMinutes = 24 * 60
    var targetMinutes by remember { mutableIntStateOf(10) }
    var running by remember { mutableStateOf(false) }
    var remainingSec by remember { mutableIntStateOf(10 * 60) }

    LaunchedEffect(running) {
        while (running && remainingSec > 0) {
            delay(1000)
            remainingSec--
            if (remainingSec == 0) {
                running = false
                ActiveAlarmController.startFallbackChime(context)
            }
        }
    }

    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        when (input) {
            is PodiumInput.Rotate -> {
                if (!running) {
                    val step = when {
                        kotlin.math.abs(input.detents) >= 3 && targetMinutes >= 120 -> input.detents * 15
                        kotlin.math.abs(input.detents) >= 2 && targetMinutes >= 60 -> input.detents * 5
                        else -> input.detents
                    }
                    targetMinutes = (targetMinutes + step).coerceIn(1, maxTimerMinutes)
                    remainingSec = targetMinutes * 60
                }
                true
            }
            is PodiumInput.Press -> when (input.button) {
                WheelButton.CENTER, WheelButton.PLAY_PAUSE -> {
                    ActiveAlarmController.stopFallbackChime()
                    running = !running
                    true
                }
                WheelButton.NEXT -> {
                    ActiveAlarmController.stopFallbackChime()
                    if (!running && remainingSec == targetMinutes * 60) {
                        // Fast jump +1 hour (+60 min) up to 24 hours
                        targetMinutes = (targetMinutes + 60).coerceIn(1, maxTimerMinutes)
                        remainingSec = targetMinutes * 60
                    } else {
                        running = false
                        remainingSec = targetMinutes * 60
                    }
                    true
                }
                WheelButton.PREVIOUS -> {
                    ActiveAlarmController.stopFallbackChime()
                    if (!running && remainingSec == targetMinutes * 60) {
                        // Fast jump -1 hour (-60 min) down to 1 min
                        targetMinutes = (targetMinutes - 60).coerceIn(1, maxTimerMinutes)
                        remainingSec = targetMinutes * 60
                    } else {
                        running = false
                        remainingSec = targetMinutes * 60
                    }
                    true
                }
                else -> false
            }
            else -> false
        }
    }

    val hours = remainingSec / 3600
    val mins = (remainingSec % 3600) / 60
    val secs = remainingSec % 60
    val formatted = if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    val targetLabel = if (targetMinutes >= 60) {
        val th = targetMinutes / 60
        val tm = targetMinutes % 60
        if (tm == 0) "${th}h" else "${th}h ${tm}m"
    } else {
        "$targetMinutes min"
    }

    val hintText = when (lang.code) {
        "ar" -> if (running) "المؤقت يعمل • OK للإيقاف المؤقت • ⏮/⏭ للتصفير" else "العجلة ($targetLabel) • ⏮/⏭ (±1 ساعة) • OK للتشغيل"
        "fr" -> if (running) "En cours • OK: Pause • ⏮/⏭: Réinitialiser" else "Molette ($targetLabel) • ⏮/⏭ (±1h) • OK: Démarrer"
        "tr" -> if (running) "Çalışıyor • OK: Duraklat • ⏮/⏭: Sıfırla" else "Tekerlek ($targetLabel) • ⏮/⏭ (±1s) • OK: Başlat"
        "ru" -> if (running) "Идёт отсчёт • OK: Пауза • ⏮/⏭: Сброс" else "Колесо ($targetLabel) • ⏮/⏭ (±1ч) • OK: Старт"
        else -> if (running) "Timer Running • OK: Pause • ⏮/⏭: Reset" else "Turn Wheel ($targetLabel) • ⏮/⏭ (±1h) • OK: Start"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = insets.top, bottom = 16.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = formatted,
            style = TextStyle(
                color = colors.labelPrimary,
                fontSize = if (hours > 0) 50.sp else 60.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(14.dp))
        PodiumText(
            hintText,
            type.caption,
            colors.labelSecondary,
        )
    }
}

/**
 * WakeIQ Fullscreen Ringing Overlay:
 * - Wakes the screen and keeps it on
 * - Starts the user's chosen music source (Radio, Album, Local Song, Download, YTM Liked, SoundCloud, or Quran)
 * - Ramps volume and screen brightness gently from 7% to 100% over [WakeAlarm.rampDurationMinutes]
 */
@Composable
fun WakeIQRingingOverlay(
    graph: AppGraph,
    controller: PlaybackController,
) {
    val alarm = ActiveAlarmController.ringingAlarm ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DisposableEffect(alarm.id) {
        val act = context.findActivity()
        act?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Start the selected sound source + WakeIQ Gradual Volume & Brightness Ramp
    LaunchedEffect(alarm.id) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxVol = (audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1)

        // Start at gentle low volume (WakeIQ N3 sleep protection)
        audio?.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)

        val startedSource = runCatching {
            when (alarm.soundType) {
                AlarmSoundType.RADIO -> {
                    val stations = graph.radioRepository.favorites.value +
                        graph.radioRepository.recents.value +
                        graph.radioRepository.loadCategory(RadioCategory.TUNISIA)
                    val st = stations.find { it.id == alarm.soundId } ?: stations.firstOrNull()
                    if (st != null) {
                        graph.radioRepository.rememberStations(listOf(st))
                        val t = st.toPodiumTrack()
                        graph.catalog.remember(listOf(t))
                        controller.playContext(listOf(t.id), 0, st.name)
                        ActiveAlarmController.stopFallbackChime()
                        true
                    } else {
                        false
                    }
                }
                AlarmSoundType.ALBUM -> {
                    val songs = (graph.library.songs.value as? LibraryState.Ready)?.tracks.orEmpty()
                    val albumSongs = songs.filter { it.album?.id?.toString() == alarm.soundId || it.album?.title == alarm.soundTitle }
                        .ifEmpty { songs.take(15) }
                    if (albumSongs.isNotEmpty()) {
                        graph.catalog.remember(albumSongs)
                        controller.playContext(albumSongs.map { it.id }, 0, alarm.soundTitle)
                        ActiveAlarmController.stopFallbackChime()
                        true
                    } else {
                        false
                    }
                }
                AlarmSoundType.LOCAL_SONG -> {
                    val songs = (graph.library.songs.value as? LibraryState.Ready)?.tracks.orEmpty()
                    val target = songs.find { it.id.toString() == alarm.soundId } ?: songs.firstOrNull()
                    if (target != null) {
                        graph.catalog.remember(listOf(target))
                        controller.playContext(listOf(target.id), 0, target.title)
                        ActiveAlarmController.stopFallbackChime()
                        true
                    } else {
                        false
                    }
                }
                AlarmSoundType.DOWNLOADED -> {
                    val dl = graph.downloads.completedTracks.value
                    val target = dl.find { it.id.toString() == alarm.soundId } ?: dl.firstOrNull()
                    if (target != null) {
                        graph.catalog.remember(listOf(target))
                        controller.playContext(listOf(target.id), 0, target.title)
                        ActiveAlarmController.stopFallbackChime()
                        true
                    } else {
                        false
                    }
                }
                AlarmSoundType.YTM_LIKED -> {
                    val likes = graph.online.likedTracks.firstOrNull().orEmpty()
                    val list = if (alarm.soundId == "__ALL_YTM_LIKES__") {
                        likes.shuffled()
                    } else {
                        val found = likes.find { it.id.toString() == alarm.soundId }
                        if (found != null) listOf(found) + likes.filterNot { it.id == found.id } else likes
                    }
                    if (list.isNotEmpty()) {
                        graph.catalog.remember(list)
                        controller.playContext(list.map { it.id }, 0, "WakeIQ YouTube Music")
                        ActiveAlarmController.stopFallbackChime()
                        true
                    } else {
                        false
                    }
                }
                AlarmSoundType.SOUNDCLOUD -> {
                    if (graph.soundcloudRepository.likedTracks.value.isEmpty()) {
                        graph.soundcloudRepository.loadLikes()
                    }
                    val allSc = (graph.soundcloudRepository.likedTracks.value + graph.soundcloudRepository.chartsTracks.value).distinctBy { it.id }
                    val podiumTracks = allSc.map { it.toPodiumTrack() }
                    val startIdx = allSc.indexOfFirst { it.id.toString() == alarm.soundId }.coerceAtLeast(0)
                    if (podiumTracks.isNotEmpty()) {
                        graph.catalog.remember(podiumTracks)
                        controller.playContext(podiumTracks.map { it.id }, startIdx, "WakeIQ SoundCloud")
                        ActiveAlarmController.stopFallbackChime()
                        true
                    } else {
                        false
                    }
                }
                AlarmSoundType.QURAN -> {
                    val surahId = alarm.soundId.toIntOrNull() ?: 1
                    val surah = AllSurahs.find { it.id == surahId } ?: AllSurahs.first()
                    val reciter = graph.quranRepository.activeReciter.value
                    val t = surah.toPodiumTrack(reciter)
                    graph.catalog.remember(listOf(t))
                    controller.playContext(listOf(t.id), 0, surah.nameArabic)
                    ActiveAlarmController.stopFallbackChime()
                    true
                }
                AlarmSoundType.MELODIC_CHIME -> false
            }
        }.getOrDefault(false)

        if (!startedSource) {
            ActiveAlarmController.startFallbackChime(context)
        }

        // Gradual volume ramp over rampDurationMinutes (leave screen brightness untouched)
        val rampTotalMs = (alarm.rampDurationMinutes.coerceIn(1, 30) * 60_000L)
        val startMs = SystemClock.uptimeMillis()

        while (ActiveAlarmController.ringingAlarm?.id == alarm.id) {
            val elapsed = SystemClock.uptimeMillis() - startMs
            val progress = (elapsed.toFloat() / rampTotalMs.toFloat()).coerceIn(0.08f, 1.0f)
            ActiveAlarmController.rampProgress = progress

            val targetVol = (maxVol * progress).roundToInt().coerceIn(1, maxVol)
            audio?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)

            delay(2000)
        }
    }

    val progress = ActiveAlarmController.rampProgress
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(alarm.id) {
        launch { graph.clockRepository.refreshWeather() }
        while (true) {
            delay(1000)
            nowTick = System.currentTimeMillis()
        }
    }
    val currentTime = remember(nowTick) {
        SimpleDateFormat("HH:mm", Locale.US).format(Date(nowTick))
    }

    val weather by graph.clockRepository.weatherReport.collectAsStateWithLifecycle()
    val snapshot by controller.snapshot.collectAsStateWithLifecycle()
    val lang = app.podium.core.designsystem.l10n.LocalAppLanguage.current
    var fallbackPlaying by remember { mutableStateOf(true) }

    val imAwakeLabel = when (lang.code) {
        "ar" -> "أنا مستيقظ (إيقاف)"
        "fr" -> "Je suis réveillé"
        "tr" -> "Uyandım (Kapat)"
        "ru" -> "Я проснулся"
        else -> "I'm Awake"
    }

    BackHandler {
        controller.pause()
        ActiveAlarmController.dismiss(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF0B0D14),
                        Color(0xFF08090E),
                    ),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 1. Dynamic Animated Weather Icon
            AnimatedWeatherIllustration(
                condition = weather.condition,
                modifier = Modifier.height(136.dp).fillMaxWidth(),
            )

            // 2. Large Today's Weather Report Card
            val cityDisplay = if (lang.code == "ar") weather.cityNameArabic else weather.cityName
            val condDisplay = weather.conditionLabel(lang.code)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(1.2.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(
                        text = cityDisplay,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    BasicText(
                        text = condDisplay,
                        style = TextStyle(
                            color = Color(0xFF93C5FD),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(
                        text = "${weather.temperatureC}°C",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    BasicText(
                        text = "▲ ${weather.highC}°  ▼ ${weather.lowC}°",
                        style = TextStyle(
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }

            // 3. Large Snooze Card with Actual Current Time (No "Snooze" word)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E2433),
                                Color(0xFF151924),
                            ),
                        ),
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                    .clickable {
                        controller.pause()
                        graph.clockRepository.snoozeAlarm(alarm, 9)
                    }
                    .padding(vertical = 26.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText(
                        text = currentTime,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 76.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.10f))
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        BasicText(
                            text = "💤 +9 min",
                            style = TextStyle(
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }

            // 4. I'm Awake Button with Integrated Sound Ramp Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF14291D))
                    .border(1.dp, Color(0xFF30D158).copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .clickable {
                        controller.pause()
                        ActiveAlarmController.dismiss(context)
                    },
            ) {
                // Progressive fill from left to right as sound ramps up
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                        .fillMaxHeight()
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF22C55E),
                                    Color(0xFF30D158),
                                ),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = imAwakeLabel,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        BasicText(
                            text = "🔊 ${(progress * 100).roundToInt()}%",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }

            // 5. Large Mini Player for the Active Alarm Media
            val activeItem = snapshot.item
            val isCatalogPlaying = snapshot.isActive && snapshot.intent == app.podium.player.api.PlayIntent.PLAY
            val isPlayingNow = if (snapshot.isActive) isCatalogPlaying else fallbackPlaying
            val mediaTitle = activeItem?.title?.takeIf { it.isNotBlank() } ?: alarm.soundTitle
            val mediaSubtitle = activeItem?.artistDisplay?.takeIf { it.isNotBlank() } ?: alarm.soundType.label

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF141822))
                    .border(1.2.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Large Artwork or Source Badge
                    Box(
                        modifier = Modifier
                            .height(66.dp)
                            .fillMaxWidth(0.20f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF23293A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val artUri = activeItem?.artworkUri
                        if (!artUri.isNullOrBlank()) {
                            app.podium.core.designsystem.artwork.ArtworkImage(
                                uri = artUri,
                                size = 66.dp,
                                fallbackText = mediaTitle,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            app.podium.core.designsystem.symbol.Symbol(
                                symbol = PodiumSymbol.Note,
                                color = Color(0xFF38BDF8),
                                size = 28.dp,
                                weight = 700,
                                filled = true,
                            )
                        }
                    }

                    // Title & Subtitle
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        BasicText(
                            text = mediaTitle,
                            maxLines = 1,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        BasicText(
                            text = mediaSubtitle,
                            maxLines = 1,
                            style = TextStyle(
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }

                // Large Transport Controls Row: Prev / Play-Pause / Next
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                if (snapshot.isActive) {
                                    controller.previous()
                                } else {
                                    ActiveAlarmController.startFallbackChime(context)
                                    fallbackPlaying = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        app.podium.core.designsystem.symbol.Symbol(
                            symbol = PodiumSymbol.Previous,
                            color = Color.White,
                            size = 21.dp,
                            weight = 700,
                            filled = true,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable {
                                if (snapshot.isActive) {
                                    if (isCatalogPlaying) controller.pause() else controller.play()
                                } else {
                                    val player = ActiveAlarmController.fallbackPlayer
                                    if (player?.isPlaying == true) {
                                        runCatching { player.pause() }
                                        fallbackPlaying = false
                                    } else {
                                        if (player != null) {
                                            runCatching { player.start() }
                                        } else {
                                            ActiveAlarmController.startFallbackChime(context)
                                        }
                                        fallbackPlaying = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        app.podium.core.designsystem.symbol.Symbol(
                            symbol = if (isPlayingNow) PodiumSymbol.Pause else PodiumSymbol.Play,
                            color = Color.White,
                            size = 24.dp,
                            weight = 700,
                            filled = true,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                if (snapshot.isActive) {
                                    controller.next()
                                } else {
                                    ActiveAlarmController.startFallbackChime(context)
                                    fallbackPlaying = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        app.podium.core.designsystem.symbol.Symbol(
                            symbol = PodiumSymbol.Next,
                            color = Color.White,
                            size = 21.dp,
                            weight = 700,
                            filled = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedWeatherIllustration(
    condition: WeatherCondition,
    modifier: Modifier = Modifier,
) {
    var phase by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            phase = ((SystemClock.uptimeMillis() - start) % 6000L) / 6000f
            delay(32)
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension * 0.28f
        val pulse = 1f + 0.06f * kotlin.math.sin(phase * 2f * Math.PI.toFloat())
        val drift = 8.dp.toPx() * kotlin.math.sin(phase * 2f * Math.PI.toFloat())

        fun drawCloudShape(centerX: Float, centerY: Float, scale: Float, color: Color) {
            val cw = r * 2.2f * scale
            val ch = r * 1.15f * scale
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(centerX - cw * 0.48f, centerY - ch * 0.15f),
                size = androidx.compose.ui.geometry.Size(cw * 0.96f, ch * 0.65f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(ch * 0.35f, ch * 0.35f),
            )
            drawCircle(
                color = color,
                radius = ch * 0.46f,
                center = androidx.compose.ui.geometry.Offset(centerX - cw * 0.16f, centerY - ch * 0.12f),
            )
            drawCircle(
                color = color,
                radius = ch * 0.38f,
                center = androidx.compose.ui.geometry.Offset(centerX + cw * 0.18f, centerY - ch * 0.04f),
            )
        }

        when (condition) {
            WeatherCondition.SUNNY -> {
                // Soft aura rings
                drawCircle(
                    color = Color(0xFFFFB703).copy(alpha = 0.15f),
                    radius = r * 1.65f * pulse,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                )
                drawCircle(
                    color = Color(0xFFFFD166).copy(alpha = 0.25f),
                    radius = r * 1.28f * pulse,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                )
                // 8 rotating sun rays
                for (i in 0 until 8) {
                    val angle = (i * 45f + phase * 360f) * (Math.PI / 180.0)
                    val cosA = kotlin.math.cos(angle).toFloat()
                    val sinA = kotlin.math.sin(angle).toFloat()
                    drawLine(
                        color = Color(0xFFFFD166),
                        start = androidx.compose.ui.geometry.Offset(cx + cosA * r * 1.22f, cy + sinA * r * 1.22f),
                        end = androidx.compose.ui.geometry.Offset(cx + cosA * r * 1.54f, cy + sinA * r * 1.54f),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
                // Core sun
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color(0xFFFFEA79), Color(0xFFF59E0B)),
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                )
            }

            WeatherCondition.CLEAR_NIGHT -> {
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.12f),
                    radius = r * 1.55f * pulse,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                )
                drawCircle(
                    color = Color(0xFFFDE047),
                    radius = r * 1.05f,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                )
                drawCircle(
                    color = Color(0xFF0E1422),
                    radius = r * 0.88f,
                    center = androidx.compose.ui.geometry.Offset(cx + r * 0.42f, cy - r * 0.25f),
                )
                // Twinkling stars
                val starAlpha = (0.4f + 0.6f * kotlin.math.abs(kotlin.math.sin(phase * 2f * Math.PI.toFloat()))).coerceIn(0f, 1f)
                drawCircle(Color.White.copy(alpha = starAlpha), radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx - r * 1.4f, cy - r * 0.7f))
                drawCircle(Color.White.copy(alpha = 1f - starAlpha * 0.5f), radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx + r * 1.3f, cy + r * 0.6f))
            }

            WeatherCondition.PARTLY_CLOUDY -> {
                // Sun peeking from top-left
                val sx = cx - r * 0.45f
                val sy = cy - r * 0.35f
                drawCircle(
                    color = Color(0xFFFFB703).copy(alpha = 0.22f),
                    radius = r * 1.05f * pulse,
                    center = androidx.compose.ui.geometry.Offset(sx, sy),
                )
                drawCircle(
                    color = Color(0xFFF59E0B),
                    radius = r * 0.78f,
                    center = androidx.compose.ui.geometry.Offset(sx, sy),
                )
                // Drifting front cloud
                drawCloudShape(cx + r * 0.18f + drift * 0.6f, cy + r * 0.22f, 1.05f, Color(0xFFE2E8F0))
            }

            WeatherCondition.CLOUDY -> {
                drawCloudShape(cx - r * 0.32f - drift * 0.5f, cy - r * 0.18f, 0.92f, Color(0xFF64748B))
                drawCloudShape(cx + r * 0.15f + drift * 0.7f, cy + r * 0.15f, 1.12f, Color(0xFFCBD5E1))
            }

            WeatherCondition.RAINY -> {
                drawCloudShape(cx + drift * 0.4f, cy - r * 0.22f, 1.08f, Color(0xFF94A3B8))
                // Falling animated rain drops
                for (i in -2..2) {
                    val dropPhase = (phase * 3f + (i + 2) * 0.22f) % 1f
                    val startX = cx + i * r * 0.36f
                    val startY = cy + r * 0.35f + dropPhase * r * 0.95f
                    drawLine(
                        color = Color(0xFF38BDF8).copy(alpha = 1f - dropPhase * 0.6f),
                        start = androidx.compose.ui.geometry.Offset(startX, startY),
                        end = androidx.compose.ui.geometry.Offset(startX - 4.dp.toPx(), startY + 9.dp.toPx()),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }

            WeatherCondition.STORM -> {
                drawCloudShape(cx + drift * 0.4f, cy - r * 0.24f, 1.12f, Color(0xFF475569))
                // Lightning bolt
                val flashAlpha = if ((phase * 6f).toInt() % 2 == 0) 1f else 0.35f
                val bolt = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx + r * 0.05f, cy + r * 0.22f)
                    lineTo(cx - r * 0.22f, cy + r * 0.78f)
                    lineTo(cx + r * 0.04f, cy + r * 0.78f)
                    lineTo(cx - r * 0.12f, cy + r * 1.32f)
                    lineTo(cx + r * 0.32f, cy + r * 0.64f)
                    lineTo(cx + r * 0.06f, cy + r * 0.64f)
                    close()
                }
                drawPath(bolt, Color(0xFFFACC15).copy(alpha = flashAlpha))
            }

            WeatherCondition.SNOWY -> {
                drawCloudShape(cx + drift * 0.4f, cy - r * 0.22f, 1.08f, Color(0xFFCBD5E1))
                for (i in -2..2) {
                    val snowPhase = (phase * 2f + (i + 2) * 0.25f) % 1f
                    val sx = cx + i * r * 0.38f + kotlin.math.sin((snowPhase + i) * 6f) * 4.dp.toPx()
                    val sy = cy + r * 0.38f + snowPhase * r * 0.90f
                    drawCircle(
                        color = Color.White.copy(alpha = 1f - snowPhase * 0.5f),
                        radius = 3.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(sx, sy),
                    )
                }
            }
        }
    }
}
