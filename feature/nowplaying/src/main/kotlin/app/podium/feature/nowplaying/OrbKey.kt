package app.podium.feature.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.podium.core.designsystem.component.pressFeedback
import app.podium.core.designsystem.orb.LiveOrb
import app.podium.core.designsystem.orb.OrbFace
import app.podium.core.designsystem.orb.OrbFrame
import app.podium.core.designsystem.theme.PodiumTheme
import app.podium.core.interaction.rememberPodiumHaptics
import app.podium.core.model.QueueUid
import app.podium.player.api.AudioPulse
import app.podium.player.api.PlayIntent
import app.podium.player.api.PlaybackController
import app.podium.player.api.PlaybackSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Now Playing's play key: the live orb (D-107). It does what the play key did — Center on it, or a
 * tap, starts and stops the music, with the same firm click and soft fall (D-105) and the same
 * "Play" and "Pause" for anyone reading the screen — and it is alive while it does it.
 *
 * Everything the orb knows comes together here: the music as [OrbDrive] smooths it from
 * [AudioPulse][app.podium.player.api.AudioPulse], the Wheel's focus lens as a place to look
 * ([lensGaze]), the song changing as something to watch go by, and the finger on it. [OrbMind] turns
 * all of it into one [OrbFace] per frame and [LiveOrb] draws it.
 *
 * Frames are asked for only while the orb is moving. While the music plays it always is (so does the
 * cover's light behind it); stopped, it is still between one blink or fidget and the next, and the
 * loop sleeps until the next one is due — looking in every [StillPollMs] at the longest, so a finger
 * landing on it is felt at once. The loop starts again by itself whenever the music starts or stops,
 * the lens moves, the song changes or the orb is pressed, so none of those ever waits for a poll.
 */
@Composable
internal fun OrbKey(p: NowPlayingParts, diameter: Dp, modifier: Modifier = Modifier) {
    val haptics = rememberPodiumHaptics()
    val reduced = PodiumTheme.motion.reduced
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val face = remember { OrbFace() }
    val mind = remember { OrbMind() }
    val drive = remember { OrbDrive() }
    val frame = remember { OrbFrame() }
    val levels = remember { FloatArray(3) }
    val playing = p.playing
    val controller = p.controller
    val pulse = p.pulse
    val duration = (p.item.durationMs ?: 0L).coerceAtLeast(1L)
    val isScrubbing = p.mode == WheelMode.Scrub || p.scrubTarget != null
    val scrubFrac = if (isScrubbing) {
        ((p.scrubTarget ?: controller.positionMs()).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0.5f

    // Track Favorite (♥) transitions to trigger a 2-second heart-eyes + joyful hop celebration!
    val lastFav = remember { booleanArrayOf(p.isFavorite) }
    val heartTimerMs = remember { floatArrayOf(0f) }
    val wearState = remember { floatArrayOf(if (playing) 1f else 0f) }
    LaunchedEffect(p.isFavorite) {
        if (p.isFavorite && !lastFav[0]) {
            heartTimerMs[0] = 2_000f
        }
        lastFav[0] = p.isFavorite
    }

    // Where the orb looks:
    // - Scrub mode: eyes dart left & right following the seek playhead.
    // - Volume bar showing: eyes follow the volume level across the bar.
    // - Actions tray mode: eyes follow the focused action slot.
    val look = when {
        isScrubbing -> Offset((scrubFrac - 0.5f) * 2f, 0.38f)
        p.showVolume -> Offset((p.volumeFraction - 0.5f) * 1.8f, 0.42f)
        p.mode == WheelMode.Actions -> Offset(lensGaze(p.actionFocus, p.actions.size), LensGaze)
        else -> null
    }

    // A new song: the orb watches the covers travel, and the music's references start afresh.
    val seen = remember { arrayOfNulls<QueueUid>(1) }
    LaunchedEffect(p.item.uid) {
        val was = seen[0]
        seen[0] = p.item.uid
        if (was != null && was != p.item.uid) {
            drive.reset()
            if (!reduced) mind.watch(forward = !songChangeIsBack(was, p.item.indexInQueue, p.queueOrder))
        }
    }

    LaunchedEffect(playing, reduced, pulse, look, pressed, p.item.uid, p.isFavorite, p.volumeFraction, p.showVolume, isScrubbing) {
        var last = -1L
        while (isActive) {
            val nanos = withFrameNanos { it }
            val dt = if (last < 0L) 0f else (nanos - last) / 1e6f
            last = nanos
            // Read a little ahead of the playhead, as the lyrics' swell does, so the beat lands with it.
            val measured = playing && pulse.levelsAt(controller.positionMs() + PulseFollower.LEAD_MS, levels)
            drive.update(if (measured) levels else null, dt, playing, frame)
            mind.update(dt, playing, frame.energy, frame.low, frame.high, frame.glint, look, pressed, reduced, face)

            // 1. Headphones wear state (snug on Play, relaxed on Pause) + Bass thump
            val targetWear = if (playing) 1f else 0f
            val wearStep = (dt / 220f).coerceIn(0f, 1f)
            wearState[0] += (targetWear - wearState[0]) * wearStep
            face.headphones = wearState[0]
            val volBoost = if (p.volumeFraction > 0.85f) 1.35f else 1f
            face.bassBounce = (frame.low * volBoost).coerceIn(0f, 1f)

            // 2. Heart-eyes (♥ ♥) + joyful hop for 2 seconds when Favorited
            if (heartTimerMs[0] > 0f) {
                heartTimerMs[0] = (heartTimerMs[0] - dt).coerceAtLeast(0f)
                val rem = heartTimerMs[0]
                val env = when {
                    rem > 1_800f -> (2_000f - rem) / 200f
                    rem < 350f -> rem / 350f
                    else -> 1f
                }.coerceIn(0f, 1f)
                face.heartEyes = env
                val hopPhase = (2_000f - rem) / 2_000f
                val hop = kotlin.math.abs(kotlin.math.sin(hopPhase * Math.PI.toFloat() * 3f)) * (1f - hopPhase * 0.5f)
                face.bob = (face.bob + hop * 0.85f).coerceIn(-1f, 1.5f)
                face.tilt += kotlin.math.sin(hopPhase * Math.PI.toFloat() * 6f) * 8f * env
            } else {
                face.heartEyes = 0f
            }

            // 3. Volume extremes reaction (> 85% hyped & bigger vs < 5% mute puzzled & smaller eyes)
            if (p.volumeFraction > 0.85f && playing) {
                val hype = ((p.volumeFraction - 0.85f) / 0.15f).coerceIn(0f, 1f)
                face.scaleX *= (1f + 0.08f * hype)
                face.scaleY *= (1f + 0.08f * hype)
                face.bob += 0.18f * hype * kotlin.math.sin((nanos / 1e8).toFloat())
            } else if (p.volumeFraction < 0.05f) {
                face.eyeWidth *= 0.62f
                face.eyeHeight *= 0.58f
                face.tilt += 7f
            }
            face.moved()

            val animatingExtras = heartTimerMs[0] > 0f || kotlin.math.abs(wearState[0] - targetWear) > 0.01f || isScrubbing || p.showVolume
            if (mind.atRest && !animatingExtras) {
                delay(mind.millisUntilMove().coerceIn(MinWaitMs, StillPollMs))
            } else if (!animatingExtras) {
                // Still moving, but perhaps slowly enough that not every frame is worth drawing.
                val pace = mind.pacingMs()
                if (pace > 0L) delay(pace)
            }
        }
    }

    val hidingForReturn = OrbTransitionState.isReturningToNowPlaying
    LiveOrb(
        diameter = diameter,
        settled = !playing,
        face = face,
        pressed = { pressed },
        modifier = modifier
            .graphicsLayer {
                alpha = if (hidingForReturn) 0f else 1f
            }
            .pressFeedback(0.96f)
            .clickable(interactionSource = interaction, indication = null) {
                // Touching the orb keeps the tray's own clock running (D-108).
                p.touched()
                // Starting is a firm click, stopping a soft fall (D-105).
                haptics.playPause(playing = !playing)
                controller.togglePlayPause()
            }
            .semantics {
                role = Role.Button
                contentDescription = if (playing) "Pause" else "Play"
            },
    )
}

/**
 * Where the focus lens sits across the action tray, in the orb's own −1 to 1 reach: the middle of the
 * [focus]th of [count] slots. The tray is as wide as the orb's column and the orb is centred in it, so
 * the slot's place in the tray is the place to look, whatever the screen's shape.
 */
internal fun lensGaze(focus: Int, count: Int): Float =
    if (count <= 0) 0f else (((focus + 0.5f) / count) - 0.5f) * 2f

/** The tray is below the orb, so looking at the lens is looking down. */
internal const val LensGaze = 0.62f

/** Resting, the orb looks in this often at the longest, so a finger is felt at once. */
private const val StillPollMs = 400L
private const val MinWaitMs = 16L

/** Shared transition flag so Now Playing's [OrbKey] stays hidden while the floating robot flies back into place. */
internal object OrbTransitionState {
    var isReturningToNowPlaying: Boolean by mutableStateOf(false)
}

/**
 * Floating live robot companion outside Now Playing:
 * - Alternates between two exit choreographies whenever leaving Now Playing while music is active:
 *   - Mode 1 (Dive): 0–900 ms stays at Now Playing spot looking left, right, up -> 900–1300 ms dives
 *     behind bottom bezel -> 1300–2000 ms pops out from behind top bezel over top-left dynamic icon with bounce.
 *   - Mode 2 (Direct Arc + Spin): launches immediately from Now Playing spot along a smooth curved arc
 *     to top-left while shrinking (72dp -> 26dp) and doing a playful 360° spin, landing with a soft bounce.
 * - When returning to Now Playing, plays the reverse animation from top-left back to Now Playing's orb spot!
 * - Keeps [AudioPulse.listen] active so it dances to real-time bass/energy/volume just like Now Playing,
 *   with expressive music motion tuned for 26dp and eyes that react to the wheel cursor.
 * - Single tap toggles Play/Pause; long-press or double-tap opens Now Playing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingNowPlayingOrb(
    inNowPlaying: Boolean,
    hidden: Boolean,
    snapshot: PlaybackSnapshot,
    controller: PlaybackController,
    pulse: AudioPulse,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    volumeFraction: Float = 0.5f,
    cursorYFraction: () -> Float? = { null },
    cursorKey: String? = null,
) {
    val active = snapshot.isActive
    val playing = active && snapshot.intent == PlayIntent.PLAY
    val reduced = PodiumTheme.motion.reduced
    val haptics = rememberPodiumHaptics()

    // Alternate between Dive (false) and Direct Arc + Spin (true) on every exit from Now Playing,
    // and reverse the active mode when returning into Now Playing.
    var wasInNowPlaying by remember { mutableStateOf(false) }
    var wasOutsideActive by remember { mutableStateOf(false) }
    var nextExitDirectArc by remember { mutableStateOf(false) }
    var activeExitIsArc by remember { mutableStateOf(false) }
    var isReturningToNowPlaying by remember { mutableStateOf(false) }

    val travelDurationMs = when {
        isReturningToNowPlaying && activeExitIsArc -> 1_100f
        isReturningToNowPlaying -> 1_150f
        activeExitIsArc -> 1_200f
        else -> 2_000f
    }
    var travelElapsedMs by remember { mutableFloatStateOf(2_000f) }

    LaunchedEffect(inNowPlaying, active, hidden) {
        if (inNowPlaying) {
            wasInNowPlaying = true
            if (wasOutsideActive && active && !hidden && !reduced) {
                wasOutsideActive = false
                isReturningToNowPlaying = true
                OrbTransitionState.isReturningToNowPlaying = true
                travelElapsedMs = 0f
            } else {
                wasOutsideActive = false
                isReturningToNowPlaying = false
                OrbTransitionState.isReturningToNowPlaying = false
                travelElapsedMs = travelDurationMs
            }
        } else {
            isReturningToNowPlaying = false
            OrbTransitionState.isReturningToNowPlaying = false
            if (active && !hidden) {
                wasOutsideActive = true
            } else if (!active) {
                wasOutsideActive = false
            }
            if (wasInNowPlaying && active) {
                wasInNowPlaying = false
                activeExitIsArc = nextExitDirectArc
                nextExitDirectArc = !nextExitDirectArc
                val dur = if (activeExitIsArc) 1_200f else 2_000f
                travelElapsedMs = if (reduced) dur else 0f
            }
        }
    }

    // Keep the audio meter alive outside Now Playing (and during return flight) so it dances to real live beats!
    DisposableEffect(pulse, playing, inNowPlaying, isReturningToNowPlaying, hidden) {
        val listening = if (playing && (!inNowPlaying || isReturningToNowPlaying) && !hidden) pulse.listen() else null
        onDispose { listening?.close() }
    }

    if (!active || hidden) return
    if (inNowPlaying && !isReturningToNowPlaying) return

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val face = remember { OrbFace() }
    val mind = remember { OrbMind() }
    val drive = remember { OrbDrive() }
    val frame = remember { OrbFrame() }
    val levels = remember { FloatArray(3) }
    val wearState = remember { floatArrayOf(if (playing) 1f else 0f) }

    // Track cursor movement so the robot's eyes react immediately when scrolling lists.
    val cursorAlertMs = remember { floatArrayOf(0f) }
    val lastCursorKey = remember { arrayOfNulls<String>(1) }
    LaunchedEffect(cursorKey) {
        if (cursorKey != null && lastCursorKey[0] != null && lastCursorKey[0] != cursorKey) {
            cursorAlertMs[0] = 1_800f
        }
        lastCursorKey[0] = cursorKey
    }

    // Track song changes just like Now Playing's OrbKey.
    val seenUid = remember { arrayOfNulls<QueueUid>(1) }
    LaunchedEffect(snapshot.item?.uid) {
        val uid = snapshot.item?.uid
        val was = seenUid[0]
        seenUid[0] = uid
        if (was != null && uid != null && was != uid) {
            drive.reset()
            if (!reduced) mind.watch(forward = true)
        }
    }

    LaunchedEffect(playing, reduced, pulse, pressed, snapshot.item?.uid, inNowPlaying, isReturningToNowPlaying, volumeFraction, activeExitIsArc) {
        var last = -1L
        while (isActive) {
            val nanos = withFrameNanos { it }
            val dt = if (last < 0L) 0f else (nanos - last) / 1e6f
            last = nanos

            val dur = when {
                isReturningToNowPlaying && activeExitIsArc -> 1_100f
                isReturningToNowPlaying -> 1_150f
                activeExitIsArc -> 1_200f
                else -> 2_000f
            }
            if (travelElapsedMs < dur) {
                travelElapsedMs = (travelElapsedMs + dt).coerceAtMost(dur)
                if (travelElapsedMs >= dur && isReturningToNowPlaying) {
                    isReturningToNowPlaying = false
                    OrbTransitionState.isReturningToNowPlaying = false
                }
            }
            if (cursorAlertMs[0] > 0f) {
                cursorAlertMs[0] = (cursorAlertMs[0] - dt).coerceAtLeast(0f)
            }

            // Compute cursor gaze target when settled at top-left.
            val cY = cursorYFraction()
            val cursorLook: Offset? = if (travelElapsedMs >= dur && cY != null) {
                val lookY = (cY * 1.45f - 0.15f).coerceIn(-0.25f, 0.95f)
                val alert = (cursorAlertMs[0] / 1_800f).coerceIn(0f, 1f)
                val lookX = 0.28f + 0.30f * alert
                Offset(lookX, lookY)
            } else null

            val measured = playing && pulse.levelsAt(controller.positionMs() + PulseFollower.LEAD_MS, levels)
            drive.update(if (measured) levels else null, dt, playing, frame)
            mind.update(dt, playing, frame.energy, frame.low, frame.high, frame.glint, cursorLook, pressed, reduced, face)

            // 1. Headphones wear state + amplified Bass thump so music effects pop clearly at 26dp
            val targetWear = if (playing) 1f else 0f
            val wearStep = (dt / 220f).coerceIn(0f, 1f)
            wearState[0] += (targetWear - wearState[0]) * wearStep
            face.headphones = wearState[0]
            val volBoost = if (volumeFraction > 0.85f) 1.35f else 1f
            val beatLow = frame.low.coerceIn(0f, 1f)
            val beatEnergy = frame.energy.coerceIn(0f, 1f)
            face.bassBounce = (beatLow * 1.35f * volBoost).coerceIn(0f, 1f)

            // 2. Expressive music groove & volume extremes when settled at top-left (26dp)
            val t = travelElapsedMs
            if (t >= dur) {
                if (playing && !reduced) {
                    face.scaleX *= (1f + 0.10f * beatLow)
                    face.scaleY *= (1f - 0.05f * beatLow + 0.08f * beatEnergy)
                    face.bob = (face.bob * 1.45f + 0.24f * beatLow).coerceIn(-1.2f, 1.4f)
                    face.tilt = (face.tilt * 1.35f).coerceIn(-22f, 22f)
                }
                // Subtle curious tilt toward the cursor when actively scrolling
                if (cursorAlertMs[0] > 0f && cY != null) {
                    val alert = (cursorAlertMs[0] / 1_800f).coerceIn(0f, 1f)
                    face.tilt = (face.tilt + (cY - 0.4f) * 10f * alert).coerceIn(-24f, 24f)
                    face.eyeWidth *= (1f + 0.08f * alert)
                    face.eyeHeight *= (1f + 0.08f * alert)
                }
                // Volume extremes reaction (> 85% hyped vs < 5% mute puzzled)
                if (volumeFraction > 0.85f && playing) {
                    val hype = ((volumeFraction - 0.85f) / 0.15f).coerceIn(0f, 1f)
                    face.scaleX *= (1f + 0.09f * hype)
                    face.scaleY *= (1f + 0.09f * hype)
                    face.bob += 0.22f * hype * kotlin.math.sin((nanos / 1e8).toFloat())
                } else if (volumeFraction < 0.05f) {
                    face.eyeWidth *= 0.62f
                    face.eyeHeight *= 0.58f
                    face.tilt += 7f
                }
            } else if (isReturningToNowPlaying && activeExitIsArc) {
                // Reverse Mode 2: Direct Arc + Spin from top-left down to Now Playing spot
                when {
                    t < 780f -> {
                        val p = (t / 780f).coerceIn(0f, 1f)
                        face.gazeX = 0.45f * (1f - p * 0.4f)
                        face.gazeY = 0.55f * (1f - p * 0.4f)
                        face.eyeBend = 0.65f
                        face.scaleX = 1f + 0.06f * kotlin.math.sin(p * Math.PI.toFloat())
                        face.scaleY = 1f - 0.06f * kotlin.math.sin(p * Math.PI.toFloat())
                    }
                    else -> {
                        val p = ((t - 780f) / 320f).coerceIn(0f, 1f)
                        val wobble = kotlin.math.sin(p * Math.PI.toFloat() * 2.5f) * (1f - p)
                        face.scaleX = 1f + 0.14f * wobble
                        face.scaleY = 1f - 0.14f * wobble
                        face.eyeBend = 0.70f * (1f - p * 0.5f)
                    }
                }
            } else if (isReturningToNowPlaying) {
                // Reverse Mode 1: Dive up behind top bezel, emerge from bottom bezel up to Now Playing spot
                when {
                    t < 420f -> {
                        face.gazeX = 0f
                        face.gazeY = -0.6f
                        face.scaleX = 0.92f
                        face.scaleY = 1.08f
                    }
                    else -> {
                        val p = ((t - 420f) / 730f).coerceIn(0f, 1f)
                        val wobble = kotlin.math.sin(p * Math.PI.toFloat() * 3f) * (1f - p)
                        face.scaleX = 1f - 0.10f * wobble
                        face.scaleY = 1f + 0.10f * wobble
                        face.eyeBend = 0.75f * (1f - p * 0.4f)
                    }
                }
            } else if (activeExitIsArc) {
                // Mode 2: Direct Arc + Spin face expression
                when {
                    // 0..820 ms: Flying in an arc with spin — joyful excited eyes
                    t < 820f -> {
                        val p = (t / 820f).coerceIn(0f, 1f)
                        face.gazeX = -0.45f * (1f - p * 0.4f)
                        face.gazeY = -0.55f * (1f - p * 0.4f)
                        face.eyeBend = 0.65f
                        face.scaleX = 1f + 0.06f * kotlin.math.sin(p * Math.PI.toFloat())
                        face.scaleY = 1f - 0.06f * kotlin.math.sin(p * Math.PI.toFloat())
                    }
                    // 820..1200 ms: Soft bounce settle at top-left
                    else -> {
                        val p = ((t - 820f) / 380f).coerceIn(0f, 1f)
                        val wobble = kotlin.math.sin(p * Math.PI.toFloat() * 2.5f) * (1f - p)
                        face.scaleX = 1f + 0.16f * wobble
                        face.scaleY = 1f - 0.16f * wobble
                        face.eyeBend = 0.70f * (1f - p * 0.5f)
                    }
                }
            } else {
                // Mode 1: Dive behind bottom bezel & pop out top-left
                when {
                    // 0..300 ms: Look left curiously (keep exact head scale)
                    t < 300f -> {
                        val p = (t / 300f).coerceIn(0f, 1f)
                        face.gazeX = -0.92f * p
                        face.gazeY = -0.15f * p
                        face.tilt = -12f * p
                        face.eyeWidth = 1.12f
                        face.eyeHeight = 1.15f
                        face.scaleX = 1f
                        face.scaleY = 1f
                    }
                    // 300..600 ms: Look right searching for a path (keep exact head scale)
                    t < 600f -> {
                        val p = ((t - 300f) / 300f).coerceIn(0f, 1f)
                        face.gazeX = -0.92f + 1.84f * p
                        face.gazeY = -0.25f
                        face.tilt = -12f + 24f * p
                        face.eyeWidth = 1.15f
                        face.eyeHeight = 1.18f
                        face.scaleX = 1f
                        face.scaleY = 1f
                    }
                    // 600..900 ms: Spot the top frame! Look straight up (keep exact head scale)
                    t < 900f -> {
                        val p = ((t - 600f) / 300f).coerceIn(0f, 1f)
                        face.gazeX = 0.92f * (1f - p)
                        face.gazeY = -0.25f - 0.75f * p
                        face.tilt = 12f * (1f - p)
                        face.scaleX = 1f
                        face.scaleY = 1f
                    }
                    // 900..1300 ms: Diving behind the bottom frame
                    t < 1300f -> {
                        face.gazeX = 0f
                        face.gazeY = 0.6f
                        face.scaleX = 0.92f
                        face.scaleY = 1.08f
                    }
                    // 1300..2000 ms: Bouncing out over top-left dynamic icon!
                    else -> {
                        val p = ((t - 1300f) / 700f).coerceIn(0f, 1f)
                        val wobble = kotlin.math.sin(p * Math.PI.toFloat() * 3f) * (1f - p)
                        face.scaleX = 1f - 0.12f * wobble
                        face.scaleY = 1f + 0.12f * wobble
                        face.eyeBend = 0.75f * (1f - p * 0.4f)
                    }
                }
            }
            face.moved()

            val inTransition = travelElapsedMs < dur || cursorAlertMs[0] > 0f || kotlin.math.abs(wearState[0] - targetWear) > 0.01f
            if (mind.atRest && !inTransition) {
                delay(mind.millisUntilMove().coerceIn(MinWaitMs, StillPollMs))
            } else if (!inTransition && !playing) {
                val pace = mind.pacingMs()
                if (pace > 0L) delay(pace)
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight
        val t = travelElapsedMs

        // Bottom spot: In Now Playing, the orb is horizontally centered and sits right above the tray
        val bottomDiameter = 72.dp
        val bottomSpotX = (screenW - bottomDiameter) / 2
        val bottomSpotY = (screenH - 148.dp).coerceAtLeast(0.dp)

        // Top spot: Over the dynamic icon on the top-left!
        val topDiameter = 26.dp
        val topSpotX = 10.dp
        val topSpotY = 6.dp

        val currentX: Dp
        val currentY: Dp
        val currentSize: Dp
        var spinDeg = 0f

        if (isReturningToNowPlaying && activeExitIsArc) {
            when {
                // Reverse Mode 2 Phase 1 (0..780 ms): Fly back in a curved arc from top-left to Now Playing with -360° spin & grow
                t < 780f -> {
                    val p = (t / 780f).coerceIn(0f, 1f)
                    val e = easeInOutCubic(p)
                    val inv = 1f - e
                    val ctrlX = bottomSpotX + 48.dp
                    val ctrlY = bottomSpotY * 0.22f
                    currentX = topSpotX * (inv * inv) + ctrlX * (2f * inv * e) + bottomSpotX * (e * e)
                    currentY = topSpotY * (inv * inv) + ctrlY * (2f * inv * e) + bottomSpotY * (e * e)
                    currentSize = topDiameter + (bottomDiameter - topDiameter) * e
                    spinDeg = -360f * e
                }
                // Reverse Mode 2 Phase 2 (780..1100 ms): Soft bounce settle in Now Playing
                t < 1_100f -> {
                    val p = ((t - 780f) / 320f).coerceIn(0f, 1f)
                    val bounceDp = (kotlin.math.abs(kotlin.math.sin(p * Math.PI.toFloat() * 2f)) * (1f - p) * 6f).dp
                    currentX = bottomSpotX
                    currentY = bottomSpotY + bounceDp
                    currentSize = bottomDiameter
                    spinDeg = 0f
                }
                else -> {
                    currentX = bottomSpotX
                    currentY = bottomSpotY
                    currentSize = bottomDiameter
                }
            }
        } else if (isReturningToNowPlaying) {
            when {
                // Reverse Mode 1 Phase 1 (0..420 ms): Slip up behind the top bezel from top-left
                t < 420f -> {
                    val p = (t / 420f).coerceIn(0f, 1f)
                    val easeIn = p * p
                    val endTop = -36.dp
                    currentX = topSpotX
                    currentY = topSpotY + (endTop - topSpotY) * easeIn
                    currentSize = topDiameter
                }
                // Reverse Mode 1 Phase 2 (420..1150 ms): Emerge up from behind the bottom bezel into Now Playing with bounce!
                t < 1_150f -> {
                    val p = ((t - 420f) / 730f).coerceIn(0f, 1f)
                    val bounce = easeOutBounceCubic(p)
                    val startBottom = screenH + 56.dp
                    currentX = bottomSpotX
                    currentY = startBottom + (bottomSpotY - startBottom) * bounce
                    currentSize = topDiameter + (bottomDiameter - topDiameter) * p.coerceIn(0f, 1f)
                }
                else -> {
                    currentX = bottomSpotX
                    currentY = bottomSpotY
                    currentSize = bottomDiameter
                }
            }
        } else if (activeExitIsArc) {
            when {
                // Mode 2 Phase 1 (0..820 ms): Launch immediately in a smooth curved arc with 360° spin & shrink
                t < 820f -> {
                    val p = (t / 820f).coerceIn(0f, 1f)
                    val e = easeInOutCubic(p)
                    val inv = 1f - e
                    // Quadratic Bezier control point curving gracefully outward-upward
                    val ctrlX = bottomSpotX + 48.dp
                    val ctrlY = bottomSpotY * 0.22f
                    currentX = bottomSpotX * (inv * inv) + ctrlX * (2f * inv * e) + topSpotX * (e * e)
                    currentY = bottomSpotY * (inv * inv) + ctrlY * (2f * inv * e) + topSpotY * (e * e)
                    currentSize = bottomDiameter + (topDiameter - bottomDiameter) * e
                    spinDeg = 360f * e
                }
                // Mode 2 Phase 2 (820..1200 ms): Soft bounce settle at top-left
                t < 1_200f -> {
                    val p = ((t - 820f) / 380f).coerceIn(0f, 1f)
                    val bounceDp = (-kotlin.math.abs(kotlin.math.sin(p * Math.PI.toFloat() * 2f)) * (1f - p) * 6f).dp
                    currentX = topSpotX
                    currentY = topSpotY + bounceDp
                    currentSize = topDiameter
                    spinDeg = 0f
                }
                else -> {
                    currentX = topSpotX
                    currentY = topSpotY
                    currentSize = topDiameter
                }
            }
        } else {
            when {
                // Mode 1 Phase 1 (0..900 ms): Stay exactly at Now Playing spot looking left, right, and up
                t < 900f -> {
                    currentX = bottomSpotX
                    currentY = bottomSpotY
                    currentSize = bottomDiameter
                }
                // Mode 1 Phase 2 (900..1300 ms): Dive down behind the bottom bezel
                t < 1300f -> {
                    val p = ((t - 900f) / 400f).coerceIn(0f, 1f)
                    val easeIn = p * p
                    currentX = bottomSpotX
                    currentY = bottomSpotY + (screenH - bottomSpotY + 56.dp) * easeIn
                    currentSize = bottomDiameter - (bottomDiameter - topDiameter) * p
                }
                // Mode 1 Phase 3 (1300..2000 ms): Emerge from behind the top bezel over the top-left dynamic icon with bounce!
                t < 2_000f -> {
                    val p = ((t - 1300f) / 700f).coerceIn(0f, 1f)
                    val bounce = easeOutBounceCubic(p)
                    val startTop = -36.dp
                    currentX = topSpotX
                    currentY = startTop + (topSpotY - startTop) * bounce
                    currentSize = topDiameter
                }
                // Settled at top-left over the dynamic icon
                else -> {
                    currentX = topSpotX
                    currentY = topSpotY
                    currentSize = topDiameter
                }
            }
        }

        Box(
            modifier = Modifier
                .offset(x = currentX, y = currentY)
                .graphicsLayer { rotationZ = spinDeg },
        ) {
            LiveOrb(
                diameter = currentSize,
                settled = !playing,
                face = face,
                pressed = { pressed },
                modifier = Modifier
                    .pressFeedback(0.94f)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            haptics.playPause(playing = !playing)
                            controller.togglePlayPause()
                        },
                        onLongClick = {
                            haptics.navigate(forward = true)
                            onOpenNowPlaying()
                        },
                        onDoubleClick = {
                            haptics.navigate(forward = true)
                            onOpenNowPlaying()
                        },
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = if (playing) "Pause" else "Play"
                    },
            )
        }
    }
}

/** Smooth cubic ease-in-out for the direct arc + spin flight. */
private fun easeInOutCubic(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f) * (-2f * x + 2f) * (-2f * x + 2f) / 2f
}

/** Elastic spring-bounce landing curve for popping out from behind the top bezel. */
private fun easeOutBounceCubic(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    val c1 = 2.4f
    val c3 = c1 + 1f
    val u = x - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}
