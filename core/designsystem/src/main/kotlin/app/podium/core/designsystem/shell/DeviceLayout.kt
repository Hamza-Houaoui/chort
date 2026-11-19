package app.podium.core.designsystem.shell

import android.content.Context
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.podium.core.designsystem.theme.Spacing

/**
 * Current physical rotation angle (0f, 90f, or -90f) when the user turns the phone sideways
 * while system auto-rotate is enabled. The iPod body, stickers, screen frame size, and wheel size
 * stay fixed; only the content inside [VirtualScreen] and the buttons on [PodWheel] rotate.
 */
val LocalPhysicalRotation = compositionLocalOf { 0f }

@Composable
fun rememberPhysicalRotation(): Float {
    val context = LocalContext.current
    var angle by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val autoRotateOn = runCatching {
                    Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                }.getOrDefault(false)
                if (!autoRotateOn) {
                    if (angle != 0f) angle = 0f
                    return
                }
                val next = when {
                    orientation in 240..300 -> 90f
                    orientation in 60..120 -> -90f
                    orientation <= 30 || orientation >= 330 -> 0f
                    else -> angle
                }
                if (next != angle) angle = next
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return angle
}

/**
 * The device's physical arrangement (D-26): the screen above the Wheel, with the power button next to
 * it. The screen keeps a device-like proportion (0.60–0.82 width:height) and is otherwise as large as
 * the Wheel allows; whatever is left over is body. Everything the display shows goes in [screen],
 * which is clipped to the display — nothing on the screen can reach the body.
 *
 * Portrait only (D-108): Podium is held upright, and the app is locked to it, so there is no
 * landscape arrangement to turn into.
 */
@Composable
fun DeviceLayout(
    modifier: Modifier = Modifier,
    screen: @Composable BoxScope.() -> Unit,
    wheel: @Composable (diameter: Dp) -> Unit,
    powerButton: @Composable () -> Unit = {},
    /** The indicator lights (D-33), at the bottom corner opposite the power button. */
    indicators: @Composable () -> Unit = {},
    /** The keyboard has opened out where the Wheel was (D-45): the power button and lights step aside. */
    keyboardOpen: Boolean = false,
    /**
     * Full screen (D-85): the phone's bars are hidden, so only the camera cutout is kept clear; bars
     * swiped back for a moment pass over the device instead of moving it.
     */
    immersive: Boolean = false,
    /** Whether to show the top accessory bar (power button and status LEDs). */
    showTopBar: Boolean = false,
) {
    val physicalRotation = rememberPhysicalRotation()
    val insets = if (immersive) WindowInsets.displayCutout else WindowInsets.systemBars.union(WindowInsets.displayCutout)
    CompositionLocalProvider(LocalPhysicalRotation provides physicalRotation) {
        BoxWithConstraints(modifier.windowInsetsPadding(insets)) {
            val diameter = (maxWidth * 0.56f).coerceIn(210.dp, 290.dp).coerceAtMost(maxHeight * 0.29f)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (showTopBar) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.m)) {
                        Accessory(!keyboardOpen, Modifier.align(Alignment.CenterStart).padding(start = Spacing.xs)) { indicators() }
                        Accessory(!keyboardOpen, Modifier.align(Alignment.CenterEnd)) { powerButton() }
                    }
                }
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = ScreenEdgePadding)
                        .padding(top = if (showTopBar) 0.dp else ScreenTopPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    val (w, h) = fitProportion(maxWidth, maxHeight, PORTRAIT_MIN, PORTRAIT_MAX)
                    VirtualScreen(Modifier.size(w, h), content = screen)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = WheelGap)) {
                    Box(Modifier.align(Alignment.Center)) { wheel(diameter) }
                }
            }
        }
    }
}

/** The power button and lights, hidden (and untouchable) while the keyboard spans the body. */
@Composable
private fun Accessory(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible,
        modifier,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 120)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
    ) { content() }
}

private val ScreenEdgePadding = 4.dp
private val ScreenTopPadding = 4.dp
private val WheelGap = 6.dp
private const val PORTRAIT_MIN = 0.45f
private const val PORTRAIT_MAX = 0.95f

/** The largest width × height inside [maxW] × [maxH] whose width:height lies in [min]..[max]. */
internal fun fitProportion(maxW: Dp, maxH: Dp, min: Float, max: Float): Pair<Dp, Dp> {
    val ratio = maxW / maxH
    return when {
        ratio > max -> (maxH * max) to maxH
        ratio < min -> maxW to (maxW / min)
        else -> maxW to maxH
    }
}
