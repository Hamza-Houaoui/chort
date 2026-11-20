package app.podium.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.podium.core.designsystem.component.FocusList
import app.podium.core.designsystem.component.ListInputEffect
import app.podium.core.designsystem.component.LocalMiniature
import app.podium.core.designsystem.component.LocalScreenInsets
import app.podium.core.designsystem.component.MenuPreview
import app.podium.core.designsystem.component.MenuRow
import app.podium.core.designsystem.l10n.LocalAppStrings
import app.podium.core.designsystem.component.MessageState
import app.podium.core.designsystem.component.ProgressBar
import app.podium.core.designsystem.shell.DeviceAppearance
import app.podium.core.designsystem.symbol.PodiumSymbol
import app.podium.core.designsystem.theme.DisplayTheme
import app.podium.core.designsystem.theme.DisplayBackground
import app.podium.core.designsystem.theme.LocalPodiumType
import app.podium.core.designsystem.theme.LyricsTheme
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.designsystem.theme.Spacing
import app.podium.core.designsystem.theme.TextContrast
import app.podium.core.designsystem.type.DisplayFont
import app.podium.core.designsystem.type.LyricsFont
import app.podium.core.designsystem.type.LyricsTypeface
import app.podium.core.designsystem.type.LocalTypographyPreset
import app.podium.core.designsystem.type.PodiumText
import app.podium.core.designsystem.type.TypographyPreset
import app.podium.core.interaction.FocusListState
import app.podium.core.interaction.InputTargetEffect
import app.podium.core.interaction.PodiumInput
import app.podium.core.interaction.WheelButton
import app.podium.core.interaction.WheelContext
import app.podium.core.interaction.rememberFocusListState
import app.podium.core.interaction.rememberPodiumHaptics
import kotlin.math.roundToInt

/*
 * Settings ▸ Appearance (D-41, PODIUM_CUSTOMIZATION.md §8): the device body (finish, grain, glitter)
 * and the virtual display (theme, font, background, contrast), each one more Podium menu. Choices
 * are tried on the device itself as the Wheel turns; Center keeps them, Menu puts the old look back.
 */

/** A 0–1 level in Appearance, edited with the Wheel and previewed live ([LevelScreen]). */
enum class AppearanceLevel(val label: String, val step: Float) {
    Grain("Grain", 0.04f),
    GlitterAmount("Glitter amount", 0.05f),
    GlitterGlow("Glitter glow", 0.05f),
    GlitterDensity("Glitter density", 0.05f),
    GlitterSize("Glitter size", 0.05f),
    GlitterOpacity("Glitter opacity", 0.05f),
    BackgroundOpacity("Background opacity", 0.05f),
    ;

    fun read(a: DeviceAppearance): Float = when (this) {
        Grain -> a.grain
        GlitterAmount -> a.glitter.amount
        GlitterGlow -> a.glitter.glow
        GlitterDensity -> a.glitter.density
        GlitterSize -> a.glitter.size
        GlitterOpacity -> a.glitter.opacity
        BackgroundOpacity -> a.screen.imageOpacity
    }

    fun write(a: DeviceAppearance, value: Float): DeviceAppearance {
        val v = value.coerceIn(0f, 1f)
        return when (this) {
            Grain -> a.copy(grain = v)
            GlitterAmount -> a.copy(glitter = a.glitter.copy(amount = v))
            GlitterGlow -> a.copy(glitter = a.glitter.copy(glow = v))
            GlitterDensity -> a.copy(glitter = a.glitter.copy(density = v))
            GlitterSize -> a.copy(glitter = a.glitter.copy(size = v))
            GlitterOpacity -> a.copy(glitter = a.glitter.copy(opacity = v))
            BackgroundOpacity -> a.copy(screen = a.screen.copy(imageOpacity = v))
        }
    }

    /** Why this level can't be adjusted right now (title, next step), or null when it can. */
    fun unavailable(a: DeviceAppearance): Pair<String, String>? = when (this) {
        Grain -> if (a.isGlass) "Grain needs a solid finish" to "Choose a finish other than Glass, then come back here." else null
        GlitterAmount, GlitterGlow, GlitterDensity, GlitterSize, GlitterOpacity -> when {
            a.isGlass -> "Glitter needs a solid finish" to "Choose a finish other than Glass, then come back here."
            !a.glitter.enabled -> "Glitter is off" to "Turn Glitter on in Device body, then come back here."
            else -> null
        }
        BackgroundOpacity -> if (a.screen.backgroundFor(a.display) != DisplayBackground.IMAGE) {
            "No background picture" to "Choose Image under Background first."
        } else null
    }
}

/** Whether the chosen background picture can be shown (the app reads it). */
enum class BackgroundImageStatus { NONE, READY, UNAVAILABLE }

private enum class AppearanceRow {
    DeviceBody,
    VirtualDisplay,
    Mode,
    ChortArt,
    NowPlayingLayout,
    HideNowPlayingTray,
    FullScreen,
    TitleBarBattery,
}

/** Settings ▸ Appearance: the body, the display inside it, visual Modes, CHORT Art, Now Playing, and display chrome. */
@Composable
fun AppearanceScreen(
    repository: DeviceSettingsRepository,
    onDeviceBody: () -> Unit,
    onVirtualDisplay: () -> Unit,
    onMode: () -> Unit = {},
    onChortArt: () -> Unit = {},
) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val nowPlayingStyle by repository.nowPlayingStyle.collectAsStateWithLifecycle()
    val hideNowPlayingTray by repository.hideNowPlayingTray.collectAsStateWithLifecycle()
    val immersive by repository.immersive.collectAsStateWithLifecycle()
    val titleBarBattery by repository.titleBarBattery.collectAsStateWithLifecycle()
    val haptics = rememberPodiumHaptics()
    val strings = LocalAppStrings.current
    val rows = AppearanceRow.entries
    val focus = rememberFocusListState("appearance")
    val canvas = PodiumTheme.colors.canvas
    val activate: (Int) -> Unit = { index ->
        when (rows[index]) {
            AppearanceRow.DeviceBody -> onDeviceBody()
            AppearanceRow.VirtualDisplay -> onVirtualDisplay()
            AppearanceRow.Mode -> onMode()
            AppearanceRow.ChortArt -> onChortArt()
            AppearanceRow.NowPlayingLayout -> {
                val next = nowPlayingStyle.next()
                haptics.toggle(on = next != app.podium.core.designsystem.shell.NowPlayingStyle.MODERN)
                repository.setNowPlayingStyle(next)
            }
            AppearanceRow.HideNowPlayingTray -> {
                haptics.toggle(on = !hideNowPlayingTray)
                repository.setHideNowPlayingTray(!hideNowPlayingTray)
            }
            AppearanceRow.FullScreen -> {
                haptics.toggle(on = !immersive)
                repository.setImmersive(!immersive)
            }
            AppearanceRow.TitleBarBattery -> if (!immersive) haptics.reject() else {
                haptics.toggle(on = !titleBarBattery)
                repository.setTitleBarBattery(!titleBarBattery)
            }
        }
    }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = rows,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { row ->
            when (row) {
                AppearanceRow.DeviceBody -> appearance.baseArgb?.let { MenuPreview.Swatch(Color(it)) } ?: MenuPreview.Instrument
                AppearanceRow.VirtualDisplay -> MenuPreview.Swatch(canvas)
                AppearanceRow.Mode -> MenuPreview.Swatch(Color(appearance.screen.menuGradientArgb))
                else -> MenuPreview.Instrument
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            AppearanceRow.DeviceBody -> MenuRow(strings.deviceBody, focused)
            AppearanceRow.VirtualDisplay -> MenuRow(strings.virtualDisplay, focused)
            AppearanceRow.Mode -> MenuRow(strings.appearanceMode, focused)
            AppearanceRow.ChortArt -> MenuRow(
                "CHORT Art",
                focused,
                value = when (strings) {
                    is app.podium.core.designsystem.l10n.ArabicStrings -> "ملصقات، مساعدة، إيقاف"
                    is app.podium.core.designsystem.l10n.FrenchStrings -> "Autocollants, aide, éteindre"
                    is app.podium.core.designsystem.l10n.TurkishStrings -> "Çıkartmalar, yardım, kapat"
                    is app.podium.core.designsystem.l10n.RussianStrings -> "Стикеры, помощь, выкл."
                    else -> "Stickers, help, turn off"
                },
            )
            AppearanceRow.NowPlayingLayout -> MenuRow(
                strings.nowPlayingLayout,
                focused,
                value = when (nowPlayingStyle) {
                    app.podium.core.designsystem.shell.NowPlayingStyle.MODERN -> strings.modern
                    app.podium.core.designsystem.shell.NowPlayingStyle.NOSTALGIC -> strings.nostalgic
                    app.podium.core.designsystem.shell.NowPlayingStyle.ARCH -> strings.nowPlayingArch
                },
                showChevron = false,
            )
            AppearanceRow.HideNowPlayingTray -> MenuRow(
                strings.hideNowPlayingTray,
                focused,
                value = if (hideNowPlayingTray) strings.hidesWhenIdle else strings.alwaysShown,
                showChevron = false,
            )
            AppearanceRow.FullScreen -> MenuRow(
                strings.fullScreen,
                focused,
                value = if (immersive) strings.on else strings.off,
                showChevron = false,
            )
            AppearanceRow.TitleBarBattery -> MenuRow(
                strings.batteryInTitleBar,
                focused,
                value = if (!immersive) strings.fullScreenOnly else if (titleBarBattery) strings.shown else strings.hidden,
                enabled = immersive,
                showChevron = false,
            )
        }
    }
}

private enum class ModeRow { MenuGradient, GradientColor, SubMenuPreview, BreadcrumbArch, HeaderTitle, StatusPill }

/** Settings ▸ Appearance ▸ Mode: toggle Mode 1 (curved gradient + color), Mode 2 (blurred sub-menu preview), Mode 3 (left breadcrumb arch), Header Title, and Status Pill. */
@Composable
fun AppearanceModeScreen(
    repository: DeviceSettingsRepository,
    onGradientColor: () -> Unit,
) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val screen = appearance.screen
    val rows = ModeRow.entries
    val focus = rememberFocusListState("appearance-mode")
    val haptics = rememberPodiumHaptics()
    val activate: (Int) -> Unit = { index ->
        when (rows[index]) {
            ModeRow.MenuGradient -> {
                repository.setAppearance(appearance.copy(screen = screen.copy(menuGradientEnabled = !screen.menuGradientEnabled)))
                haptics.confirm()
            }
            ModeRow.GradientColor -> onGradientColor()
            ModeRow.SubMenuPreview -> {
                repository.setAppearance(appearance.copy(screen = screen.copy(subMenuBlurPreview = !screen.subMenuBlurPreview)))
                haptics.confirm()
            }
            ModeRow.BreadcrumbArch -> {
                repository.setAppearance(appearance.copy(screen = screen.copy(breadcrumbArchEnabled = !screen.breadcrumbArchEnabled)))
                haptics.confirm()
            }
            ModeRow.HeaderTitle -> {
                repository.setAppearance(appearance.copy(screen = screen.copy(showHeaderTitle = !screen.showHeaderTitle)))
                haptics.confirm()
            }
            ModeRow.StatusPill -> {
                repository.setAppearance(appearance.copy(screen = screen.copy(showStatusPill = !screen.showStatusPill)))
                haptics.confirm()
            }
        }
    }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = rows,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { row ->
            when (row) {
                ModeRow.MenuGradient, ModeRow.GradientColor -> MenuPreview.Swatch(Color(screen.menuGradientArgb))
                ModeRow.SubMenuPreview, ModeRow.BreadcrumbArch, ModeRow.HeaderTitle, ModeRow.StatusPill -> MenuPreview.Instrument
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            ModeRow.MenuGradient -> MenuRow(
                label = strings.menuGradient,
                focused = focused,
                value = if (screen.menuGradientEnabled) strings.on else strings.off,
                showChevron = false,
            )
            ModeRow.GradientColor -> MenuRow(
                label = strings.menuGradientColor,
                focused = focused,
                leadingContent = { Swatch(Color(screen.menuGradientArgb)) },
                showChevron = true,
            )
            ModeRow.SubMenuPreview -> MenuRow(
                label = strings.subMenuBlurPreview,
                focused = focused,
                value = if (screen.subMenuBlurPreview) strings.on else strings.off,
                showChevron = false,
            )
            ModeRow.BreadcrumbArch -> MenuRow(
                label = strings.breadcrumbArch,
                focused = focused,
                value = if (screen.breadcrumbArchEnabled) strings.on else strings.off,
                showChevron = false,
            )
            ModeRow.HeaderTitle -> MenuRow(
                label = strings.showHeaderTitle,
                focused = focused,
                value = if (screen.showHeaderTitle) strings.on else strings.off,
                showChevron = false,
            )
            ModeRow.StatusPill -> MenuRow(
                label = strings.showStatusPill,
                focused = focused,
                value = if (screen.showStatusPill) strings.on else strings.off,
                showChevron = false,
            )
        }
    }
}

private enum class BodyRow { Finish, CustomColor, Grain, Glitter, GlitterAmount, GlitterGlow, GlitterTilt, GlitterDensity, GlitterSize, GlitterOpacity }

/** Settings ▸ Appearance ▸ Device body: finish, grain and glitter. */
@Composable
fun DeviceBodyScreen(
    repository: DeviceSettingsRepository,
    onFinish: () -> Unit,
    onCustomColor: () -> Unit,
    onLevel: (AppearanceLevel) -> Unit,
) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val haptics = rememberPodiumHaptics()
    val rows = BodyRow.entries
    val focus = rememberFocusListState("device-body")
    val industrial = appearance.display.isIndustrial
    val solid = !appearance.isGlass
    val glitterOn = solid && appearance.glitter.enabled
    fun level(level: AppearanceLevel) = if (level.unavailable(appearance) != null) haptics.reject() else onLevel(level)
    val activate: (Int) -> Unit = { index ->
        when (rows[index]) {
            BodyRow.Finish -> if (industrial) haptics.reject() else onFinish()
            BodyRow.CustomColor -> if (industrial) haptics.reject() else onCustomColor()
            BodyRow.Grain -> level(AppearanceLevel.Grain)
            BodyRow.Glitter -> if (!solid) haptics.reject() else {
                haptics.confirm()
                repository.setAppearance(appearance.copy(glitter = appearance.glitter.copy(enabled = !appearance.glitter.enabled)))
            }
            BodyRow.GlitterAmount -> level(AppearanceLevel.GlitterAmount)
            BodyRow.GlitterGlow -> level(AppearanceLevel.GlitterGlow)
            BodyRow.GlitterDensity -> level(AppearanceLevel.GlitterDensity)
            BodyRow.GlitterSize -> level(AppearanceLevel.GlitterSize)
            BodyRow.GlitterOpacity -> level(AppearanceLevel.GlitterOpacity)
            BodyRow.GlitterTilt -> if (!glitterOn) haptics.reject() else {
                haptics.confirm()
                repository.setAppearance(appearance.copy(glitter = appearance.glitter.copy(tilt = !appearance.glitter.tilt)))
            }
        }
    }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = rows,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { row ->
            when (row) {
                BodyRow.Finish -> appearance.baseArgb?.let { MenuPreview.Swatch(Color(it)) } ?: MenuPreview.Instrument
                BodyRow.CustomColor -> MenuPreview.Swatch(Color(appearance.customArgb))
                else -> MenuPreview.Instrument
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            BodyRow.Finish -> MenuRow(
                strings.finish,
                focused,
                // Carbon comes in blackened steel (D-101); Bone and Custom in matte black hardware (D-29).
                value = when {
                    appearance.display == DisplayTheme.CARBON -> strings.finishBlackenedSteel
                    industrial -> strings.finishMatteBlack
                    else -> strings.finishPresetLabel(appearance.preset)
                },
                enabled = !industrial,
                showChevron = !industrial,
            )
            BodyRow.CustomColor -> MenuRow(
                strings.customColor,
                focused,
                leadingContent = { Swatch(Color(appearance.customArgb)) },
                enabled = !industrial,
                showChevron = !industrial,
            )
            BodyRow.Grain -> MenuRow(strings.grain, focused, value = if (solid) percent(appearance.grain) else strings.solidFinishesOnly, enabled = solid, showChevron = solid)
            BodyRow.Glitter -> MenuRow(
                strings.glitter,
                focused,
                value = if (!solid) strings.solidFinishesOnly else if (appearance.glitter.enabled) strings.on else strings.off,
                enabled = solid,
                showChevron = false,
            )
            BodyRow.GlitterAmount -> MenuRow(strings.glitterAmount, focused, value = percent(appearance.glitter.amount), enabled = glitterOn, showChevron = glitterOn)
            BodyRow.GlitterGlow -> MenuRow(strings.glitterGlow, focused, value = percent(appearance.glitter.glow), enabled = glitterOn, showChevron = glitterOn)
            // The light on the flakes follows the phone's tilt (motion sensors), or stays put.
            BodyRow.GlitterTilt -> MenuRow(strings.followTilt, focused, value = if (appearance.glitter.tilt) strings.on else strings.off, enabled = glitterOn, showChevron = false)
            BodyRow.GlitterDensity -> MenuRow(strings.glitterDensity, focused, value = percent(appearance.glitter.density), enabled = glitterOn, showChevron = glitterOn)
            BodyRow.GlitterSize -> MenuRow(strings.glitterSize, focused, value = percent(appearance.glitter.size), enabled = glitterOn, showChevron = glitterOn)
            BodyRow.GlitterOpacity -> MenuRow(strings.glitterOpacity, focused, value = percent(appearance.glitter.opacity), enabled = glitterOn, showChevron = glitterOn)
        }
    }
}

private enum class DisplayRow { Theme, Font, LyricsFont, LyricsTheme, LyricsBackground, Background, BackgroundColor, BackgroundOpacity, NoBackground, TextContrast }

/** Settings ▸ Appearance ▸ Virtual display: theme, font, background and contrast. */
@Composable
fun VirtualDisplayScreen(
    repository: DeviceSettingsRepository,
    imageStatus: BackgroundImageStatus,
    onTheme: () -> Unit,
    onFont: () -> Unit,
    onBackground: () -> Unit,
    onBackgroundColor: () -> Unit,
    onLevel: (AppearanceLevel) -> Unit,
    onLyricsFont: () -> Unit = {},
    onLyricsTheme: () -> Unit = {},
) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val haptics = rememberPodiumHaptics()
    val focus = rememberFocusListState("virtual-display")
    val screen = appearance.screen
    // Carbon keeps its own display (D-101): one row says so instead of three that can't be used.
    val ownDisplay = appearance.display == app.podium.core.designsystem.theme.DisplayTheme.CARBON
    val shown = screen.backgroundFor(appearance.display)
    val rows = DisplayRow.entries.filter { row ->
        when (row) {
            DisplayRow.Background, DisplayRow.BackgroundColor -> !ownDisplay
            DisplayRow.BackgroundOpacity -> !ownDisplay && shown == DisplayBackground.IMAGE
            DisplayRow.NoBackground -> ownDisplay
            // Classic's paper only (D-100): the line theme keeps its own black sheet.
            DisplayRow.LyricsBackground -> screen.lyricsTheme == app.podium.core.designsystem.theme.LyricsTheme.CLASSIC
            else -> true
        }
    }
    val overlay = app.podium.core.designsystem.component.LocalOverlayHost.current
    val canvas = PodiumTheme.colors.canvas
    val activate: (Int) -> Unit = { index ->
        when (rows[index]) {
            DisplayRow.Theme -> onTheme()
            DisplayRow.Font -> onFont()
            DisplayRow.LyricsFont -> onLyricsFont()
            DisplayRow.LyricsTheme -> onLyricsTheme()
            // Classic's paper (D-100).
            DisplayRow.LyricsBackground -> {
                overlay.show(
                    app.podium.core.designsystem.component.MenuSpec(
                        "${strings.lyricsBackground}: ${strings.lyricsBackgroundLabel(screen.lyricsBackground)}",
                        app.podium.core.designsystem.theme.LyricsBackground.entries.map { choice ->
                            app.podium.core.designsystem.component.MenuAction(strings.lyricsBackgroundChoice(choice)) {
                                val current = repository.appearance.value
                                repository.setAppearance(current.copy(screen = current.screen.copy(lyricsBackground = choice)))
                            }
                        },
                    ),
                )
            }
            DisplayRow.Background -> if (ownDisplay) haptics.reject() else onBackground()
            DisplayRow.BackgroundColor -> if (ownDisplay) haptics.reject() else onBackgroundColor()
            DisplayRow.BackgroundOpacity -> if (AppearanceLevel.BackgroundOpacity.unavailable(appearance) != null) haptics.reject() else onLevel(AppearanceLevel.BackgroundOpacity)
            DisplayRow.NoBackground -> haptics.reject()
            DisplayRow.TextContrast -> {
                haptics.confirm()
                val next = if (screen.contrast == TextContrast.STANDARD) TextContrast.HIGH else TextContrast.STANDARD
                repository.setAppearance(appearance.copy(screen = screen.copy(contrast = next)))
            }
        }
    }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = rows,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { row ->
            when (row) {
                DisplayRow.BackgroundColor -> MenuPreview.Swatch(Color(screen.solidArgb))
                else -> MenuPreview.Swatch(canvas)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            DisplayRow.Theme -> MenuRow(strings.theme, focused, value = strings.displayThemeLabel(appearance.display))
            DisplayRow.Font -> MenuRow(strings.font, focused, value = screen.font.label)
            DisplayRow.LyricsFont -> MenuRow(strings.lyricsFont, focused, value = screen.lyricsFont.label)
            DisplayRow.LyricsTheme -> MenuRow(strings.lyricsTheme, focused, value = strings.lyricsThemeLabel(screen.lyricsTheme))
            DisplayRow.LyricsBackground -> MenuRow(strings.lyricsBackground, focused, value = strings.lyricsBackgroundLabel(screen.lyricsBackground), showChevron = false)
            DisplayRow.Background -> MenuRow(
                strings.background,
                focused,
                value = if (shown == DisplayBackground.IMAGE && imageStatus == BackgroundImageStatus.UNAVAILABLE) strings.unavailable else strings.displayBackgroundLabel(shown),
            )
            DisplayRow.BackgroundColor -> MenuRow(strings.backgroundColor, focused, leadingContent = { Swatch(Color(screen.solidArgb)) })
            DisplayRow.BackgroundOpacity -> MenuRow(strings.backgroundOpacity, focused, value = percent(screen.imageOpacity))
            DisplayRow.NoBackground -> MenuRow(strings.backgroundsNotice, focused, enabled = false, showChevron = false)
            DisplayRow.TextContrast -> MenuRow(strings.contrast, focused, value = if (screen.contrast == TextContrast.HIGH) strings.highContrast else strings.standardContrast, showChevron = false)
        }
    }
}

/**
 * Font picker: each face set in itself. Turning the Wheel tries it on the whole display; Center
 * keeps it, Menu puts the old one back.
 */
@Composable
fun FontScreen(repository: DeviceSettingsRepository) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val fonts = DisplayFont.entries
    val focus = remember { FocusListState(initialIndex = fonts.indexOf(repository.appearance.value.screen.font)) }
    PreviewWhileFocused(repository, focus) { current, index ->
        fonts.getOrNull(index)?.let { font -> if (font == current.screen.font) null else current.copy(screen = current.screen.copy(font = font)) }
    }
    val activate: (Int) -> Unit = { index ->
        val current = repository.appearance.value
        repository.setAppearance(current.copy(screen = current.screen.copy(font = fonts[index])))
        repository.setPreview(null)
    }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = fonts,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { font, _, focused ->
        // The row in its own face, so the list is its own specimen.
        val preset = TypographyPreset.of(font)
        CompositionLocalProvider(LocalTypographyPreset provides preset, LocalPodiumType provides preset.type) {
            MenuRow(font.label, focused, selected = font == appearance.screen.font, showChevron = false)
        }
    }
}

/**
 * Lyrics font picker (D-50): each face set in itself, at a size where calligraphy and blackletter
 * read. Center keeps it.
 */
@Composable
fun LyricsFontScreen(repository: DeviceSettingsRepository) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val fonts = LyricsFont.entries
    val focus = remember { FocusListState(initialIndex = fonts.indexOf(repository.appearance.value.screen.lyricsFont)) }
    val activate: (Int) -> Unit = { index ->
        val current = repository.appearance.value
        repository.setAppearance(current.copy(screen = current.screen.copy(lyricsFont = fonts[index])))
    }
    ListInputEffect(focus, onActivate = activate)
    val display = LocalTypographyPreset.current
    FocusList(
        items = fonts,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { font, _, focused ->
        // The row in its own face, so the list is its own specimen.
        val specimen = remember(font, display) { LyricsTypeface.of(font, display).specimen }
        CompositionLocalProvider(LocalTypographyPreset provides specimen, LocalPodiumType provides specimen.type) {
            MenuRow(font.label, focused, selected = font == appearance.screen.lyricsFont, showChevron = false)
        }
    }
}

/**
 * Lyrics theme picker (D-81): Classic (one line at a time, inverting the display) or Continuous
 * line (one ivory line writing the words by hand). Center keeps it; the lyrics screen changes the
 * next time it draws — the music, its position and the queue are untouched.
 */
@Composable
fun LyricsThemeScreen(repository: DeviceSettingsRepository) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val haptics = rememberPodiumHaptics()
    val themes = LyricsTheme.entries
    val focus = remember { FocusListState(initialIndex = themes.indexOf(repository.appearance.value.screen.lyricsTheme)) }
    val activate: (Int) -> Unit = { index ->
        haptics.confirm()
        val current = repository.appearance.value
        repository.setAppearance(current.copy(screen = current.screen.copy(lyricsTheme = themes[index])))
    }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = themes,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        modifier = Modifier.fillMaxSize(),
    ) { theme, _, focused ->
        MenuRow(strings.lyricsThemeLabel(theme), focused, selected = theme == appearance.screen.lyricsTheme, showChevron = false)
    }
}

private enum class BackgroundRow { None, Solid, Image, ChooseImage }

/**
 * Background picker: None (the theme's own display), Solid (the background colour), or a picture
 * chosen with the system photo picker — no storage permission. A picture that can no longer be
 * read shows the solid colour instead and says so here.
 */
@Composable
fun BackgroundScreen(repository: DeviceSettingsRepository, imageStatus: BackgroundImageStatus, onChooseImage: () -> Unit) {
    val appearance by repository.appearance.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val haptics = rememberPodiumHaptics()
    val screen = appearance.screen
    val hasImage = screen.imageUri != null
    val rows = BackgroundRow.entries.filter { it != BackgroundRow.ChooseImage || hasImage }
    val focus = remember {
        FocusListState(
            initialIndex = when (repository.appearance.value.screen.background) {
                DisplayBackground.NONE -> 0
                DisplayBackground.SOLID -> 1
                DisplayBackground.IMAGE -> 2
            },
        )
    }
    fun choice(row: BackgroundRow): DisplayBackground? = when (row) {
        BackgroundRow.None -> DisplayBackground.NONE
        BackgroundRow.Solid -> DisplayBackground.SOLID
        BackgroundRow.Image -> if (hasImage) DisplayBackground.IMAGE else null
        BackgroundRow.ChooseImage -> null
    }
    PreviewWhileFocused(repository, focus) { current, index ->
        rows.getOrNull(index)?.let(::choice)?.let { bg -> if (bg == current.screen.background) null else current.copy(screen = current.screen.copy(background = bg)) }
    }
    val activate: (Int) -> Unit = { index ->
        val row = rows[index]
        val bg = choice(row)
        when {
            bg != null -> {
                haptics.confirm()
                val current = repository.appearance.value
                repository.setAppearance(current.copy(screen = current.screen.copy(background = bg)))
                repository.setPreview(null)
            }
            else -> onChooseImage()
        }
    }
    ListInputEffect(focus, onActivate = activate)
    FocusList(
        items = rows,
        state = focus,
        key = { it },
        contentPadding = LocalScreenInsets.current.listPadding(),
        onActivate = activate,
        preview = { row -> if (row == BackgroundRow.Solid) MenuPreview.Swatch(Color(screen.solidArgb)) else MenuPreview.None },
        modifier = Modifier.fillMaxSize(),
    ) { row, _, focused ->
        when (row) {
            BackgroundRow.None -> MenuRow(strings.none, focused, selected = screen.background == DisplayBackground.NONE, showChevron = false)
            BackgroundRow.Solid -> MenuRow(
                strings.solid,
                focused,
                leadingContent = { Swatch(Color(screen.solidArgb)) },
                selected = screen.background == DisplayBackground.SOLID,
                showChevron = false,
            )
            BackgroundRow.Image -> MenuRow(
                if (hasImage) strings.picture else strings.choosePicture,
                focused,
                value = if (hasImage && imageStatus == BackgroundImageStatus.UNAVAILABLE) strings.unavailable else null,
                selected = hasImage && screen.background == DisplayBackground.IMAGE,
                showChevron = !hasImage,
            )
            BackgroundRow.ChooseImage -> MenuRow(strings.chooseAnotherPicture, focused)
        }
    }
}

/**
 * Tries the focused choice on the device while a picker is open ([choose] returns the appearance to
 * preview, or null for "as it is"); the preview ends when the picker closes.
 */
@Composable
internal fun PreviewWhileFocused(
    repository: DeviceSettingsRepository,
    focus: FocusListState,
    choose: (current: DeviceAppearance, index: Int) -> DeviceAppearance?,
) {
    if (LocalMiniature.current) return
    LaunchedEffect(focus) {
        snapshotFlow { focus.focusedIndex }.collect { index -> repository.setPreview(choose(repository.appearance.value, index)) }
    }
    DisposableEffect(Unit) { onDispose { repository.setPreview(null) } }
}

/** A level (0–1) adjusted with the Wheel and previewed on the device as you turn; Center keeps it. */
@Composable
fun LevelScreen(repository: DeviceSettingsRepository, level: AppearanceLevel) {
    val strings = LocalAppStrings.current
    val colors = PodiumTheme.colors
    val type = PodiumTheme.type
    val insets = LocalScreenInsets.current
    val haptics = rememberPodiumHaptics()
    val appearance = remember { repository.appearance.value }
    var value by remember { mutableFloatStateOf(level.read(appearance)) }
    if (!LocalMiniature.current) DisposableEffect(Unit) { onDispose { repository.setPreview(null) } }
    val blocked = level.unavailable(appearance)
    if (blocked != null) {
        Box(Modifier.fillMaxSize().padding(top = insets.top, bottom = insets.bottom), contentAlignment = Alignment.Center) {
            MessageState(PodiumSymbol.Settings, blocked.first, blocked.second)
        }
        return
    }
    InputTargetEffect(WheelContext.VOLUME) { input ->
        when (input) {
            is PodiumInput.Rotate -> {
                val next = (value + input.detents * level.step).coerceIn(0f, 1f)
                if (next == value) haptics.boundary()
                value = next
                repository.setPreview(level.write(appearance, value))
                true
            }
            is PodiumInput.Press -> if (input.button == WheelButton.CENTER) {
                repository.setAppearance(level.write(appearance, value))
                repository.setPreview(null)
                haptics.confirm()
                true
            } else false
            else -> false
        }
    }
    Column(
        Modifier.fillMaxSize().padding(top = insets.top, bottom = insets.bottom).padding(horizontal = Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PodiumText(percent(value), type.largeTitle, colors.labelPrimary)
        Spacer(Modifier.height(Spacing.l))
        ProgressBar({ value }, running = false, emphasized = true)
        Spacer(Modifier.height(Spacing.l))
        PodiumText(
            strings.wheelAdjustNotice,
            type.footnote,
            colors.labelSecondary,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

internal fun percent(value: Float) = "${(value * 100).roundToInt()}%"

/** A choice in the lyrics background menu (D-100). */
private fun lyricsBackgroundChoice(choice: app.podium.core.designsystem.theme.LyricsBackground): String = when (choice) {
    app.podium.core.designsystem.theme.LyricsBackground.AUTOMATIC -> "Automatic: turns with each line"
    app.podium.core.designsystem.theme.LyricsBackground.BLACK -> "Always black"
    app.podium.core.designsystem.theme.LyricsBackground.WHITE -> "Always white"
}
