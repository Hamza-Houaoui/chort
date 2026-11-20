package app.podium.core.designsystem.shell

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.interaction.InputTargetEffect
import app.podium.core.interaction.PodiumInput
import app.podium.core.interaction.WheelContext
import app.podium.core.interaction.rememberPodiumHaptics
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where the boot is (D-102), shared with the screen it reveals: the boot writes [time]; the screen
 * reads [reveal] while drawing (its layer's alpha, scale and focus) and never recomposes for it.
 */
@Stable
class BootReveal {
    /** Milliseconds into [BootTimeline]. */
    var time by mutableFloatStateOf(0f)
        internal set

    /** With reduced motion the mascot crossfades to the menu: 0–1, or −1 while it doesn't. */
    internal var fade by mutableFloatStateOf(-1f)

    /** How far the menu has come into view: 0 hidden behind boot, 1 all there is. */
    val reveal: Float get() = if (fade >= 0f) fade else BootTimeline.revealAt(time)

    /** Starts over (the device switched off and on again). */
    fun reset() {
        time = 0f
        fade = -1f
    }
}

/**
 * CHORT Mascot Boot Choreography (total 2.8 s — under 3 s):
 * 1. 0–140 ms: Themed screen wakes + chime.
 * 2. 140–720 ms: Glossy 3D robot head + headphones peeks up from behind the real Click Wheel.
 * 3. 720–1220 ms: Robot rises into the virtual screen with glowing orbit ring & floating notes.
 * 4. 1220–2250 ms: Friendly head tilt, cute eye blink, and two smooth right-hand "hello" waves.
 * 5. 2250–2800 ms: Smooth fade & reveal into the live Home menu.
 */
internal object BootTimeline {
    const val CHIME = 120f
    const val PEEK_START = 140f
    const val PEEK_END = 700f
    const val RISE_START = 740f
    const val RISE_END = 1220f
    const val WAVE_START = 1180f
    const val WAVE_END = 2280f
    const val EXIT_START = 2280f
    const val END = 2800f

    fun phase(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

    fun exit(t: Float): Float {
        val v = phase(t, EXIT_START, END)
        val a = v.pow(2.0f)
        val b = (1f - v).pow(2.0f)
        return if (a + b == 0f) 0f else a / (a + b)
    }

    fun revealAt(t: Float): Float = if (t < EXIT_START) 0f else exit(t)
}

private const val SKIP_EXIT_MS = 220
private const val REDUCED_HOLD_MS = 700L

@Composable
fun BootScreen(
    reveal: BootReveal,
    onChime: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = PodiumTheme.motion.reduced
    val colors = PodiumTheme.colors
    val haptics = rememberPodiumHaptics()
    val chime by rememberUpdatedState(onChime)
    val finished by rememberUpdatedState(onFinished)
    var skip by remember { mutableStateOf(false) }

    InputTargetEffect(WheelContext.LIST_FOCUS) { input ->
        if (input is PodiumInput.Press || input is PodiumInput.Rotate) skip = true
        true
    }

    LaunchedEffect(skip) {
        reveal.reset()
        if (skip) {
            if (reduced) reveal.fade = 1f
            else animate(BootTimeline.EXIT_START, BootTimeline.END, animationSpec = tween(SKIP_EXIT_MS, easing = LinearEasing)) { v, _ -> reveal.time = v }
            finished()
            return@LaunchedEffect
        }
        if (reduced) {
            chime()
            reveal.time = BootTimeline.RISE_END
            delay(REDUCED_HOLD_MS)
            animate(0f, 1f, animationSpec = tween(220)) { v, _ -> reveal.fade = v }
            finished()
            return@LaunchedEffect
        }
        animate(0f, BootTimeline.END, animationSpec = tween(BootTimeline.END.roundToInt(), easing = LinearEasing)) { v, _ -> reveal.time = v }
        finished()
    }

    // Subtle haptics synchronized with the mascot's arrival & wave
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        var last = -1f
        snapshotFlow { reveal.time }.collect { t ->
            fun crossed(at: Float) = last < at && t >= at
            if (!skip) {
                if (crossed(BootTimeline.CHIME)) {
                    chime()
                    haptics.tick()
                }
                if (crossed(BootTimeline.PEEK_END)) haptics.tick()
                if (crossed(BootTimeline.RISE_END)) haptics.press()
            }
            last = t
        }
    }

    val isDark = colors.isDark
    val bgColor = colors.canvas
    val noteColor = if (isDark) Color(0xFFE6EAF2) else Color(0xFF3A4150)
    val glowColor = if (isDark) colors.highlight.copy(alpha = 0.24f) else Color(0xFF8AA8FF).copy(alpha = 0.20f)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "CHORT" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    skip = true
                }
            },
    ) {
        val t = reveal.time
        val exitProg = if (reveal.fade >= 0f) reveal.fade else BootTimeline.exit(t)
        val sceneAlpha = (1f - exitProg).coerceIn(0f, 1f)
        if (sceneAlpha <= 0.005f) return@Canvas

        // Theme-matched background that fades smoothly to reveal the live menu
        drawRect(color = bgColor.copy(alpha = sceneAlpha))

        // Soft studio halo behind the mascot
        val w = size.width
        val h = size.height
        val unit = min(w, h) * 0.28f
        val peekP = easeOutBack(BootTimeline.phase(t, BootTimeline.PEEK_START, BootTimeline.PEEK_END))
        val riseP = easeOutBack(BootTimeline.phase(t, BootTimeline.RISE_START, BootTimeline.RISE_END), overshoot = 1.15f)
        val waveP = BootTimeline.phase(t, BootTimeline.WAVE_START, BootTimeline.WAVE_END)

        // Vertical position:
        // Starts hidden below bottom edge (behind the real Click Wheel),
        // peeks up at bottom, then rises gracefully to center of virtual screen!
        val startY = h + unit * 1.45f
        val peekY = h - unit * 0.22f
        val centerY = h * 0.56f
        val baseY = lerpF(lerpF(startY, peekY, peekP), centerY, riseP)
        val bobY = if (waveP > 0f) sin(waveP * PI.toFloat() * 4f) * unit * 0.035f else 0f
        val exitLift = -exitProg * unit * 0.25f
        val robotCenter = Offset(w / 2f, baseY + bobY + exitLift)

        // Subtle ambient studio glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = glowColor.alpha * sceneAlpha * (0.4f + 0.6f * riseP)), Color.Transparent),
                center = Offset(robotCenter.x, robotCenter.y - unit * 0.35f),
                radius = unit * 2.2f,
            ),
            radius = unit * 2.2f,
            center = Offset(robotCenter.x, robotCenter.y - unit * 0.35f),
        )

        // Orbit ring (back half drawn BEHIND robot body)
        val ringAlpha = easeOutCubic(BootTimeline.phase(t, BootTimeline.RISE_START + 80f, BootTimeline.RISE_END + 100f)) * sceneAlpha
        if (ringAlpha > 0.01f) {
            drawOrbitRingHalf(
                center = Offset(robotCenter.x, robotCenter.y + unit * 0.32f),
                rx = unit * 1.55f * (0.72f + 0.28f * riseP),
                ry = unit * 0.38f * (0.72f + 0.28f * riseP),
                tiltDeg = -13f + sin(waveP * PI.toFloat() * 2f) * 2.5f,
                alpha = ringAlpha,
                isDark = isDark,
                frontHalf = false,
            )
        }

        // Floating music notes (pop in as robot rises and bob playfully)
        val notesProg = easeOutCubic(BootTimeline.phase(t, BootTimeline.RISE_START + 160f, BootTimeline.RISE_END + 180f))
        if (notesProg > 0.01f) {
            val nAlpha = notesProg * sceneAlpha
            val float1 = sin(waveP * PI.toFloat() * 2.5f) * unit * 0.09f
            val float2 = sin(waveP * PI.toFloat() * 2.5f + 1.6f) * unit * 0.08f
            val float3 = sin(waveP * PI.toFloat() * 2.5f + 3.1f) * unit * 0.07f

            // Left beamed double note ♫
            drawBeamedNote(
                center = Offset(
                    robotCenter.x - unit * (1.28f + 0.12f * (1f - notesProg)),
                    robotCenter.y - unit * 0.38f + float1,
                ),
                scale = unit * 0.36f * (0.6f + 0.4f * notesProg),
                tiltDeg = -12f + sin(waveP * PI.toFloat() * 3f) * 5f,
                color = noteColor.copy(alpha = nAlpha * 0.90f),
            )
            // Top-right eighth note ♪
            drawSingleNote(
                center = Offset(
                    robotCenter.x + unit * (1.28f + 0.10f * (1f - notesProg)),
                    robotCenter.y - unit * 0.72f + float2,
                ),
                scale = unit * 0.30f * (0.6f + 0.4f * notesProg),
                tiltDeg = 10f - sin(waveP * PI.toFloat() * 3f) * 5f,
                color = noteColor.copy(alpha = nAlpha * 0.88f),
            )
            // Lower-right eighth note ♪
            drawSingleNote(
                center = Offset(
                    robotCenter.x + unit * (1.36f + 0.10f * (1f - notesProg)),
                    robotCenter.y + unit * 0.42f + float3,
                ),
                scale = unit * 0.28f * (0.6f + 0.4f * notesProg),
                tiltDeg = 14f + sin(waveP * PI.toFloat() * 2.5f) * 4f,
                color = noteColor.copy(alpha = nAlpha * 0.82f),
            )
        }

        // Cute head tilt & right-hand "hello" wave angles
        val headTilt = when {
            waveP <= 0f -> -4f * peekP
            else -> -8f * sin(waveP * PI.toFloat() * 1.5f) - 4f * (1f - waveP)
        }
        val armWaveAngle = if (waveP <= 0f) {
            28f * (1f - riseP)
        } else {
            // Two crisp friendly waves back & forth!
            val envelope = sin(waveP * PI.toFloat()).coerceIn(0f, 1f)
            sin(waveP * PI.toFloat() * 4.5f) * 24f * (0.35f + 0.65f * envelope)
        }
        // Single cute blink around the middle of the wave
        val blinkPhase = BootTimeline.phase(t, 1620f, 1800f)
        val eyeOpen = 1f - sin(blinkPhase * PI.toFloat()).coerceIn(0f, 1f) * 0.85f

        // Draw the 3D glossy robot character!
        drawMascotRobot(
            center = robotCenter,
            unit = unit,
            headTiltDeg = headTilt,
            armWaveDeg = armWaveAngle,
            eyeOpen = eyeOpen,
            alpha = sceneAlpha,
            isDark = isDark,
            glowColor = glowColor,
        )

        // Orbit ring (front half drawn IN FRONT of lower torso for true 3D wrap!)
        if (ringAlpha > 0.01f) {
            drawOrbitRingHalf(
                center = Offset(robotCenter.x, robotCenter.y + unit * 0.32f),
                rx = unit * 1.55f * (0.72f + 0.28f * riseP),
                ry = unit * 0.38f * (0.72f + 0.28f * riseP),
                tiltDeg = -13f + sin(waveP * PI.toFloat() * 2f) * 2.5f,
                alpha = ringAlpha,
                isDark = isDark,
                frontHalf = true,
            )
        }
    }
}

/** Draws the glossy 3D white CHORT robot with metallic headphones, smiling eyes, and waving hand. */
private fun DrawScope.drawMascotRobot(
    center: Offset,
    unit: Float,
    headTiltDeg: Float,
    armWaveDeg: Float,
    eyeOpen: Float,
    alpha: Float,
    isDark: Boolean,
    glowColor: Color,
) {
    val whiteTop = Color(0xFFFFFFFF).copy(alpha = alpha)
    val whiteMid = Color(0xFFF0F3F8).copy(alpha = alpha)
    val whiteShadow = Color(0xFFC5CCD8).copy(alpha = alpha)
    val shadowDrop = Color.Black.copy(alpha = (if (isDark) 0.36f else 0.14f) * alpha)

    // 1. Torso (rounded pear-like glossy body below the head)
    val bodyTop = center.y - unit * 0.02f
    val bodyW = unit * 0.84f
    val bodyH = unit * 0.92f
    val bodyRect = Rect(center.x - bodyW / 2f, bodyTop, center.x + bodyW / 2f, bodyTop + bodyH)

    // Soft torso drop shadow
    drawRoundRect(
        color = shadowDrop,
        topLeft = Offset(bodyRect.left + unit * 0.05f, bodyRect.top + unit * 0.08f),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(bodyW * 0.44f, bodyW * 0.44f),
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(whiteTop, whiteMid, whiteShadow),
            center = Offset(center.x - bodyW * 0.18f, bodyTop + bodyH * 0.24f),
            radius = bodyH * 0.95f,
        ),
        topLeft = bodyRect.topLeft,
        size = bodyRect.size,
        cornerRadius = CornerRadius(bodyW * 0.44f, bodyW * 0.44f),
    )

    // 2. Left tucked arm (cute rounded mitten on the left)
    val leftArmCenter = Offset(center.x - unit * 0.56f, bodyTop + unit * 0.26f)
    rotate(-26f, pivot = leftArmCenter) {
        val aw = unit * 0.36f
        val ah = unit * 0.26f
        drawRoundRect(
            color = shadowDrop,
            topLeft = Offset(leftArmCenter.x - aw / 2f, leftArmCenter.y - ah / 2f + unit * 0.05f),
            size = Size(aw, ah),
            cornerRadius = CornerRadius(ah / 2f, ah / 2f),
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(whiteTop, whiteMid, whiteShadow),
                center = Offset(leftArmCenter.x - aw * 0.15f, leftArmCenter.y - ah * 0.2f),
                radius = aw * 0.9f,
            ),
            topLeft = Offset(leftArmCenter.x - aw / 2f, leftArmCenter.y - ah / 2f),
            size = Size(aw, ah),
            cornerRadius = CornerRadius(ah / 2f, ah / 2f),
        )
    }

    // 3. Right waving arm ("Hello!" — pivoted at right shoulder)
    val rightShoulder = Offset(center.x + unit * 0.34f, bodyTop + unit * 0.20f)
    rotate(-34f + armWaveDeg, pivot = rightShoulder) {
        val armLen = unit * 0.68f
        val armThick = unit * 0.25f
        val armTopLeft = Offset(rightShoulder.x - unit * 0.06f, rightShoulder.y - armThick / 2f)
        drawRoundRect(
            color = shadowDrop,
            topLeft = Offset(armTopLeft.x + unit * 0.02f, armTopLeft.y + unit * 0.06f),
            size = Size(armLen, armThick),
            cornerRadius = CornerRadius(armThick / 2f, armThick / 2f),
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(whiteTop, whiteMid, whiteShadow),
                start = Offset(armTopLeft.x, armTopLeft.y),
                end = Offset(armTopLeft.x + armLen, armTopLeft.y + armThick),
            ),
            topLeft = armTopLeft,
            size = Size(armLen, armThick),
            cornerRadius = CornerRadius(armThick / 2f, armThick / 2f),
        )
    }

    // 4. Head + Headphones + Smiling Eyes (tilted together playfully)
    val headPivot = Offset(center.x, center.y - unit * 0.06f)
    val headCenter = Offset(center.x, center.y - unit * 0.50f)
    val headW = unit * 1.12f
    val headH = unit * 0.90f

    rotate(headTiltDeg, pivot = headPivot) {
        // Soft headphone ambient aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = glowColor.alpha * alpha), Color.Transparent),
                center = headCenter,
                radius = unit * 1.15f,
            ),
            radius = unit * 1.15f,
            center = headCenter,
        )

        // Headphone headband (metallic silver outer + dark charcoal inner cushion)
        val bandR = headW * 0.62f
        val bandRect = Rect(
            headCenter.x - bandR,
            headCenter.y - bandR * 1.04f,
            headCenter.x + bandR,
            headCenter.y + bandR * 0.96f,
        )
        drawArc(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF4F6FA).copy(alpha = alpha), Color(0xFF8E97A6).copy(alpha = alpha)),
                startY = bandRect.top,
                endY = headCenter.y,
            ),
            startAngle = 192f,
            sweepAngle = 156f,
            useCenter = false,
            topLeft = bandRect.topLeft,
            size = bandRect.size,
            style = Stroke(width = unit * 0.16f, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color(0xFF232730).copy(alpha = alpha),
            startAngle = 198f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(bandRect.left + unit * 0.045f, bandRect.top + unit * 0.045f),
            size = Size(bandRect.width - unit * 0.09f, bandRect.height - unit * 0.09f),
            style = Stroke(width = unit * 0.095f, cap = StrokeCap.Round),
        )

        // Left & Right 3D Headphone Ear Cups
        drawEarCup(
            cupCenter = Offset(headCenter.x - headW * 0.56f, headCenter.y + headH * 0.08f),
            unit = unit,
            isLeft = true,
            alpha = alpha,
        )
        drawEarCup(
            cupCenter = Offset(headCenter.x + headW * 0.56f, headCenter.y + headH * 0.08f),
            unit = unit,
            isLeft = false,
            alpha = alpha,
        )

        // Head drop shadow
        val headRect = Rect(
            headCenter.x - headW / 2f,
            headCenter.y - headH / 2f,
            headCenter.x + headW / 2f,
            headCenter.y + headH / 2f,
        )
        drawRoundRect(
            color = shadowDrop,
            topLeft = Offset(headRect.left + unit * 0.04f, headRect.top + unit * 0.08f),
            size = headRect.size,
            cornerRadius = CornerRadius(headH * 0.42f, headH * 0.42f),
        )

        // Glossy 3D White Squircle Head
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(whiteTop, whiteMid, whiteShadow),
                center = Offset(headCenter.x - headW * 0.16f, headCenter.y - headH * 0.22f),
                radius = headW * 0.85f,
            ),
            topLeft = headRect.topLeft,
            size = headRect.size,
            cornerRadius = CornerRadius(headH * 0.42f, headH * 0.42f),
        )
        // Subtle glossy rim highlight along top of head
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.85f * alpha), Color.Transparent),
                startY = headRect.top,
                endY = headRect.top + headH * 0.35f,
            ),
            topLeft = Offset(headRect.left + unit * 0.03f, headRect.top + unit * 0.02f),
            size = Size(headW - unit * 0.06f, headH - unit * 0.04f),
            cornerRadius = CornerRadius(headH * 0.40f, headH * 0.40f),
            style = Stroke(width = 1.6.dp.toPx()),
        )

        // Expressive Smiling Curved Eyes (^ ^) with cute blink
        val eyeColor = Color(0xFF1F242D).copy(alpha = alpha)
        val eyeY = headCenter.y + headH * 0.02f
        val eyeSpacing = headW * 0.21f
        val eyeW = headW * 0.17f
        val eyeArchH = headH * 0.09f * eyeOpen
        val eyeStroke = Stroke(width = (unit * 0.082f).coerceAtLeast(3.dp.toPx()), cap = StrokeCap.Round)

        for (dir in listOf(-1f, 1f)) {
            val ex = headCenter.x + dir * eyeSpacing
            val path = Path().apply {
                moveTo(ex - eyeW / 2f, eyeY + eyeArchH * 0.35f)
                quadraticTo(ex, eyeY - eyeArchH, ex + eyeW / 2f, eyeY + eyeArchH * 0.35f)
            }
            drawPath(path, color = eyeColor, style = eyeStroke)
        }
    }
}

private fun DrawScope.drawEarCup(
    cupCenter: Offset,
    unit: Float,
    isLeft: Boolean,
    alpha: Float,
) {
    val tilt = if (isLeft) -10f else 10f
    val outerW = unit * 0.24f
    val outerH = unit * 0.56f
    val padW = unit * 0.20f
    val padH = unit * 0.62f
    val padOffsetX = if (isLeft) unit * 0.08f else -unit * 0.08f
    val shellOffsetX = if (isLeft) -unit * 0.04f else unit * 0.04f

    rotate(tilt, pivot = cupCenter) {
        // Metallic silver outer shell
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF7F9FC).copy(alpha = alpha),
                    Color(0xFFBAC2CF).copy(alpha = alpha),
                    Color(0xFF7B8391).copy(alpha = alpha),
                ),
                start = Offset(cupCenter.x - outerW, cupCenter.y - outerH / 2f),
                end = Offset(cupCenter.x + outerW, cupCenter.y + outerH / 2f),
            ),
            topLeft = Offset(cupCenter.x + shellOffsetX - outerW / 2f, cupCenter.y - outerH / 2f),
            size = Size(outerW, outerH),
            cornerRadius = CornerRadius(outerW * 0.55f, outerW * 0.55f),
        )
        // Dark matte leather cushion against the head
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF3A3F4B).copy(alpha = alpha),
                    Color(0xFF171A21).copy(alpha = alpha),
                ),
                start = Offset(cupCenter.x, cupCenter.y - padH / 2f),
                end = Offset(cupCenter.x, cupCenter.y + padH / 2f),
            ),
            topLeft = Offset(cupCenter.x + padOffsetX - padW / 2f, cupCenter.y - padH / 2f),
            size = Size(padW, padH),
            cornerRadius = CornerRadius(padW * 0.5f, padW * 0.5f),
        )
    }
}

/** Draws either the back half (180..360°) or front half (0..180°) of the tilted glowing orbit ring. */
private fun DrawScope.drawOrbitRingHalf(
    center: Offset,
    rx: Float,
    ry: Float,
    tiltDeg: Float,
    alpha: Float,
    isDark: Boolean,
    frontHalf: Boolean,
) {
    val startAngle = if (frontHalf) 0f else 180f
    val sweepAngle = 180f
    val ringColor = if (isDark) Color(0xFFEAF0FF) else Color(0xFFFFFFFF)
    val rimShadow = if (isDark) Color(0xFF8AA8FF).copy(alpha = 0.25f * alpha) else Color(0xFF9EA7B8).copy(alpha = 0.30f * alpha)

    rotate(tiltDeg, pivot = center) {
        val rect = Rect(center.x - rx, center.y - ry, center.x + rx, center.y + ry)
        // Soft shadow/glow underneath the ribbon
        drawArc(
            color = rimShadow,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(rect.left, rect.top + 3.dp.toPx()),
            size = rect.size,
            style = Stroke(width = (if (frontHalf) 6.5.dp else 4.dp).toPx(), cap = StrokeCap.Round),
        )
        // Crisp glowing white orbit ribbon
        drawArc(
            color = ringColor.copy(alpha = (if (frontHalf) 0.92f else 0.55f) * alpha),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = (if (frontHalf) 5.dp else 3.dp).toPx(), cap = StrokeCap.Round),
        )
    }
}

/** Draws a crisp vector eighth note (♪). */
private fun DrawScope.drawSingleNote(
    center: Offset,
    scale: Float,
    tiltDeg: Float,
    color: Color,
) {
    rotate(tiltDeg, pivot = center) {
        val headR = scale * 0.24f
        val stemH = scale * 0.88f
        val headCenter = Offset(center.x - headR * 0.4f, center.y + stemH * 0.35f)
        drawOval(
            color = color,
            topLeft = Offset(headCenter.x - headR * 1.15f, headCenter.y - headR * 0.85f),
            size = Size(headR * 2.3f, headR * 1.7f),
        )
        val stemX = headCenter.x + headR * 0.9f
        val stemTop = headCenter.y - stemH
        drawLine(
            color = color,
            start = Offset(stemX, headCenter.y - headR * 0.1f),
            end = Offset(stemX, stemTop),
            strokeWidth = scale * 0.13f,
            cap = StrokeCap.Round,
        )
        val flag = Path().apply {
            moveTo(stemX, stemTop)
            quadraticTo(stemX + scale * 0.50f, stemTop + scale * 0.16f, stemX + scale * 0.36f, stemTop + scale * 0.50f)
        }
        drawPath(flag, color = color, style = Stroke(width = scale * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Draws a crisp vector beamed double eighth note (♫). */
private fun DrawScope.drawBeamedNote(
    center: Offset,
    scale: Float,
    tiltDeg: Float,
    color: Color,
) {
    rotate(tiltDeg, pivot = center) {
        val headR = scale * 0.20f
        val stemH = scale * 0.78f
        val dx = scale * 0.56f
        val leftHead = Offset(center.x - dx / 2f, center.y + stemH * 0.36f)
        val rightHead = Offset(center.x + dx / 2f, center.y + stemH * 0.22f)
        val strokeW = scale * 0.12f

        for (h in listOf(leftHead, rightHead)) {
            drawOval(
                color = color,
                topLeft = Offset(h.x - headR * 1.15f, h.y - headR * 0.85f),
                size = Size(headR * 2.3f, headR * 1.7f),
            )
        }
        val lx = leftHead.x + headR * 0.85f
        val rx = rightHead.x + headR * 0.85f
        val lTop = leftHead.y - stemH
        val rTop = rightHead.y - stemH
        drawLine(color, Offset(lx, leftHead.y), Offset(lx, lTop), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(rx, rightHead.y), Offset(rx, rTop), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(lx, lTop), Offset(rx, rTop), strokeWidth = scale * 0.20f, cap = StrokeCap.Round)
    }
}

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun easeOutCubic(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

private fun easeOutBack(t: Float, overshoot: Float = 1.35f): Float {
    val c1 = overshoot
    val c3 = c1 + 1f
    val x = t.coerceIn(0f, 1f) - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}
