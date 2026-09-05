package com.example.casinogames.games.destroyer

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.casinogames.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The Chain Reaction dice tray, ported from dice-tray.js. The physics, the
 * gestures and the settle are the same maths in Kotlin: pick a die up, rattle
 * it, carry it over the dashed line — or flick it — and a 2D simulation with
 * gravity toward the top of the field decides where the dice stop. What they
 * say is read off the tumble the throw gave them, the way the web tray reads
 * the face of its cube: nothing rigs it.
 *
 * The one thing not carried over is the CSS cube itself — Compose has no
 * preserve-3d — so the die is drawn flat and its face recomputed from the
 * same three rotation angles every frame, which keeps the numbers honest and
 * the tumble visible without the box.
 */

/* Pixels-per-dp and seconds, tuned off the web tray's CSS pixels — the
   bounce and drag run a little livelier here, asked for at the table. */
private const val GRAVITY = 2600f
private const val BOUNCE = 0.48f
private const val FLOOR_DRAG = 0.83f
private const val AIR = 0.995f
private const val FLICK = 0.55f
private const val LOB = 0.12f
private const val FAST = 2.2f
private const val THROW_PX = 42f

/** Face values and the direction each points before anything is turned. */
private val NORMALS = arrayOf(
    intArrayOf(1, 0, 0, 1), intArrayOf(6, 0, 0, -1), intArrayOf(3, 1, 0, 0),
    intArrayOf(4, -1, 0, 0), intArrayOf(5, 0, -1, 0), intArrayOf(2, 0, 1, 0),
)

/** Which number a die shows after turning z, then x, then y degrees. */
private fun faceToward(z: Float, x: Float, y: Float): Int {
    val rx = Math.toRadians(x.toDouble())
    val ry = Math.toRadians(y.toDouble())
    val cx = cos(rx); val sx = sin(rx)
    val cy = cos(ry); val sy = sin(ry)
    var best = 1
    var bestZ = -2.0
    for (n in NORMALS) {
        val vx = n[1].toDouble(); val vy = n[2].toDouble(); val vz = n[3].toDouble()
        val ay = vy
        val az = -sy * vx + cy * vz
        val bz = sx * ay + cx * az
        if (bz > bestZ) { bestZ = bz; best = n[0] }
    }
    return best
}

class TrayDie {
    var x by mutableStateOf(0f)          // px, centre
    var y by mutableStateOf(0f)
    var value by mutableIntStateOf(1)
    var rot by mutableStateOf(0f)        // flat spin, degrees
    var held by mutableStateOf(false)
    // The other two axes of the tumble; drawn every frame, so state too.
    var tumble by mutableStateOf(0f)
    var roll by mutableStateOf(0f)
    var vx = 0f; var vy = 0f
    var spin = 0f
    // Where the die stopped and where it is being stood up to, so the last
    // turn onto its face can be played rather than jumped.
    var fromRot = 0f; var fromTumble = 0f; var fromRoll = 0f
    var toRot = 0f; var toTumble = 0f; var toRoll = 0f

    /**
     * Stands [v] face-on and upright — the pose every die at rest is in.
     *
     * The drawn face comes from the angles alone, so setting [value] without
     * these is a die that says one thing and shows another. That is how the
     * tray opened: every die drawn as a 1 whatever it claimed to be.
     */
    fun faceUp(v: Int) {
        value = v
        val (fx, fy) = FACING.getValue(v)
        rot = 0f; tumble = fx; roll = fy
    }
}

/**
 * The rotateX / rotateY pair that stands each value face-on and the right way
 * up, like the web FACING map.
 *
 * 6 is the one to be careful with: turning the cube 180 about X also brings the
 * 6 to the front, and reads as a 6 either way, so the wrong pair passes every
 * test a pip die can fail. It stands the character on its head.
 */
private val FACING = mapOf(
    1 to (0f to 0f), 2 to (90f to 0f), 3 to (0f to -90f),
    4 to (0f to 90f), 5 to (-90f to 0f), 6 to (0f to 180f),
)

/**
 * The tray's whole state, owned outside the composable so a hand survives
 * recomposition. [onSettle] fires once per throw with the faces up.
 */
class DiceTrayState(
    val count: Int = 2,
    /**
     * A die that calls its face as a letter, battleship-style: A for 1 up to
     * F for 6. The physics neither knows nor cares — it is the same die read
     * out loud differently. -1 for an all-numbers tray.
     */
    val letterDie: Int = -1,
    private val random: Random = Random(System.nanoTime()),
) {
    val dice = List(count) { TrayDie().apply { faceUp(1 + random.nextInt(6)) } }
    var rolling by mutableStateOf(false)
    /** The last turn onto the face, after the physics has stopped. */
    var standing = false
    var standStart = 0L
    var standLast = 0L
    var reachPx = 0f
    var fieldW = 0f; var fieldH = 0f
    var density = 1f
    var epoch by mutableLongStateOf(0L)
    var onSettle: ((List<Int>) -> Unit)? = null
    var sound: TraySound? = null

    /** The waiting row along the bottom of the field, like the web tray's home(). */
    fun home() {
        if (fieldW <= 0f) return
        for ((i, d) in dice.withIndex()) {
            d.x = fieldW * (0.5f + (i - (count - 1) / 2f) * 0.26f)
            d.y = fieldH * 0.86f
            d.rot = 0f
        }
    }

    fun rattle() {
        for (d in dice) {
            var v: Int
            do { v = 1 + random.nextInt(6) } while (v == d.value)
            // The cube is really turned to the new number, then knocked a few
            // degrees off square so it looks rattled rather than presented.
            d.faceUp(v)
            d.tumble += (random.nextFloat() - 0.5f) * 26f
            d.roll += (random.nextFloat() - 0.5f) * 26f
            d.rot = (random.nextFloat() - 0.5f) * 26f
        }
    }

    /** Hands the dice to the physics loop; the composable runs it per frame. */
    fun launch(strength: Float) {
        if (rolling || fieldW <= 0f) return
        val s = strength.coerceIn(0f, 1f)
        val lift = (260f + 900f * s) * density
        for (d in dice) {
            d.held = false
            d.vx = (random.nextFloat() - 0.5f) * (260f + 420f * s) * density
            d.vy = -lift * (0.8f + random.nextFloat() * 0.4f)
            d.spin = (random.nextFloat() - 0.5f) * (700f + 900f * s)
            d.tumble = random.nextFloat() * 360f
            d.roll = random.nextFloat() * 360f
        }
        rolling = true
        sound?.roll()
        epoch++
    }
}

/**
 * The two slices of dice.mp3, played the way the web tray plays them: the
 * rattle is the head of the clip, looped by hand so it never runs on into the
 * landing that follows it at 1.13s.
 */
class TraySound(private val make: () -> MediaPlayer?) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var shakePlayer: MediaPlayer? = null
    private var rollPlayer: MediaPlayer? = null
    private var shaking = false
    private val relooper = object : Runnable {
        override fun run() {
            val p = shakePlayer ?: return
            if (!shaking) { runCatching { p.pause() }; return }
            runCatching { p.seekTo(0) }
            handler.postDelayed(this, 1100)
        }
    }

    fun shakeOn() {
        if (shaking) return
        shaking = true
        runCatching {
            val p = shakePlayer ?: make()?.also { shakePlayer = it } ?: return
            p.seekTo(0)
            p.start()
            handler.postDelayed(relooper, 1100)
        }
    }

    fun shakeOff() {
        if (!shaking) return
        shaking = false
        handler.removeCallbacks(relooper)
        runCatching { shakePlayer?.takeIf { it.isPlaying }?.pause() }
    }

    fun roll() {
        runCatching {
            val p = rollPlayer ?: make()?.also { rollPlayer = it } ?: return
            // The landing lives at 1.13s. The web tray holds it back 0.4s so
            // the dice have left the hand before they are heard to arrive.
            handler.postDelayed({
                runCatching { p.seekTo(1130); p.start() }
                handler.postDelayed({ runCatching { p.takeIf { m -> m.isPlaying }?.pause() } }, 700)
            }, 400)
        }
    }

    fun release() {
        shaking = false
        handler.removeCallbacksAndMessages(null)
        runCatching { shakePlayer?.release() }
        runCatching { rollPlayer?.release() }
        shakePlayer = null
        rollPlayer = null
    }
}

@Composable
fun DiceTray(
    state: DiceTrayState,
    modifier: Modifier = Modifier,
    dieSize: androidx.compose.ui.unit.Dp = 52.dp,
    faceColor: Color = Color(0xFFF7F3FA),
    pipColor: Color = Color(0xFF140610),
    lineColor: Color = Color(0xB3FF40A0),
) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { dieSize.toPx() }
    state.density = density

    DisposableEffect(Unit) {
        val snd = TraySound { runCatching { MediaPlayer.create(context, R.raw.dice) }.getOrNull() }
        state.sound = snd
        onDispose { snd.release(); state.sound = null }
    }

    Box(
        modifier
            .onSizeChanged {
                val fresh = state.fieldW == 0f
                state.fieldW = it.width.toFloat()
                state.fieldH = it.height.toFloat()
                if (fresh) state.home()
            }
            // The hand listens on the field, not the die: the web tray reads
            // clientX/Y, and a die that moves under the finger would otherwise
            // swallow most of the measured speed.
            .pointerInput(state) { trayGesture(state, sizePx) }
            // The throwing area's near edge: quiet enough to ignore, there
            // enough to find. Everything above it is the table.
            .drawBehind {
                drawLine(
                    color = lineColor.copy(alpha = 0.35f),
                    start = Offset(size.width * 0.07f, size.height * 0.5f),
                    end = Offset(size.width * 0.93f, size.height * 0.5f),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                )
            },
    ) {
        if (!state.rolling) {
            Text(
                "EASY THROW BELOW HERE",
                color = lineColor.copy(alpha = 0.45f),
                fontSize = 9.sp,
                letterSpacing = 0.2.em,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 12.dp),
            )
        }
        state.dice.forEachIndexed { i, die ->
            DieView(state, i, die, sizePx, dieSize, faceColor, pipColor)
        }
    }

    // Read where they landed, then walk them back down to the band they are
    // thrown from — the same dice, moved, so the next throw starts in reach.
    LaunchedEffect(state.epoch, state.rolling) {
        if (state.rolling || state.epoch == 0L) return@LaunchedEffect
        delay(1000)
        if (state.rolling || state.dice.any { it.held }) return@LaunchedEffect
        val from = state.dice.map { it.x to it.y }
        val to = state.dice.indices.map { i ->
            state.fieldW * (0.5f + (i - (state.count - 1) / 2f) * 0.26f) to state.fieldH * 0.86f
        }
        val start = System.nanoTime()
        while (true) {
            val t = ((System.nanoTime() - start) / 4.2e8f).coerceAtMost(1f)
            val e = 1f - (1f - t) * (1f - t)   // ease-out, like the web tray's walk
            withFrameNanos { }
            if (state.rolling || state.dice.any { it.held }) return@LaunchedEffect
            state.dice.forEachIndexed { i, d ->
                d.x = from[i].first + (to[i].first - from[i].first) * e
                d.y = from[i].second + (to[i].second - from[i].second) * e
                d.rot *= (1f - e)
            }
            if (t >= 1f) break
        }
    }

    // The physics loop, frame-locked. One throw runs until the dice have been
    // still for a run of frames; the cap only stops a pathological bounce.
    LaunchedEffect(state.epoch) {
        if (!state.rolling) return@LaunchedEffect
        var last = 0L
        var still = 0
        var began = 0L
        val reachW = sizePx * 0.708f
        state.reachPx = reachW
        try {
        while (state.rolling) {
            withFrameNanos { now ->
                // The dice have stopped travelling and are turning onto their
                // faces; the simulation is over and this is the last of it.
                if (state.standing) { standStep(state, now); return@withFrameNanos }
                val dt = if (last == 0L) 0.016f else min(0.032f, (now - last) / 1e9f)
                last = now
                if (began == 0L) began = now
                var moving = 0
                val w = state.fieldW
                val h = state.fieldH
                val bodies = state.dice
                for (b in bodies) {
                    b.vy -= GRAVITY * state.density * dt   // toward the top: the table
                    b.vx *= AIR; b.vy *= AIR
                    b.x += b.vx * dt; b.y += b.vy * dt
                    b.rot += b.spin * dt; b.spin *= 0.988f
                    if (b.x < reachW) { b.x = reachW; b.vx = abs(b.vx) * BOUNCE; b.spin = -b.spin * 0.7f }
                    if (b.x > w - reachW) { b.x = w - reachW; b.vx = -abs(b.vx) * BOUNCE; b.spin = -b.spin * 0.7f }
                    if (b.y < reachW) {
                        b.y = reachW
                        b.vy = abs(b.vy) * BOUNCE
                        b.vx *= FLOOR_DRAG; b.spin *= 0.8f
                        if (abs(b.vy) < 60f * state.density) b.vy = 0f
                    }
                    if (b.y > h - reachW) { b.y = h - reachW; b.vy = -abs(b.vy) * BOUNCE }
                }
                // Dice against dice, as discs: push apart, swap momentum.
                for (i in bodies.indices) {
                    for (j in i + 1 until bodies.size) {
                        val a = bodies[i]; val c = bodies[j]
                        var dx = c.x - a.x; var dy = c.y - a.y
                        var dist = sqrt(dx * dx + dy * dy)
                        val minD = sizePx
                        if (dist >= minD) continue
                        if (dist < 0.01f) { dx = 1f; dy = 0f; dist = 1f }
                        val nx = dx / dist; val ny = dy / dist
                        val push = (minD - dist) / 2f
                        a.x -= nx * push; a.y -= ny * push
                        c.x += nx * push; c.y += ny * push
                        val rel = (c.vx - a.vx) * nx + (c.vy - a.vy) * ny
                        if (rel > 0) continue
                        val hit = -rel * 0.6f
                        a.vx -= nx * hit; a.vy -= ny * hit
                        c.vx += nx * hit; c.vy += ny * hit
                        a.spin -= hit * 2f / state.density; c.spin += hit * 2f / state.density
                    }
                }
                for (b in bodies) {
                    b.x = b.x.coerceIn(reachW, w - reachW)
                    b.y = b.y.coerceIn(reachW, h - reachW)
                    val speed = abs(b.vx) + abs(b.vy)
                    // "Still" starts while there is visibly a little life left:
                    // the stand-up carries the last of the glide, so coming to
                    // rest flat is part of the roll, not a correction after it.
                    if (speed > 80f * state.density) moving++
                    // Over and over about two axes at once, at a rate set by
                    // how fast it travels — no throw looks like the last one.
                    // The flat spin feeds the tumble a little too, so a die
                    // still spinning keeps turning faces while it slows.
                    b.tumble += (speed * 1.15f / state.density + abs(b.spin) * 0.12f) * dt
                    b.roll += (speed * 0.8f / state.density + abs(b.spin) * 0.08f) * dt
                    // The face is read live off the same angles the settle
                    // will read, so the numbers flicker honestly as it goes.
                    b.value = faceToward(b.rot, b.tumble, b.roll)
                }
                // The stand-up begins while there is still a breath of life
                // in the dice, so lying sideways never reads as a pause.
                still = if (moving > 0) 0 else still + 1
                if (still >= 4 || now - began > 6_000_000_000L) {
                    settle(state)
                }
            }
        }
        } finally {
            // Left mid-throw — the screen went away while the dice were still
            // in the air, so the frame loop stops and nothing will ever call
            // the settle. A tray left rolling is a tray nobody can pick up
            // again: launch() refuses while rolling, so the dice would be
            // dead for the rest of the session.
            if (state.rolling) {
                state.standing = false
                state.rolling = false
            }
        }
    }
}

/** The same angle, moved to whichever revolution sits nearest [to]. */
private fun nearest(from: Float, to: Float): Float {
    var f = from
    while (f - to > 180f) f -= 360f
    while (to - f > 180f) f += 360f
    return f
}

/**
 * The die rocks onto its nearest flat side, that side is read off it, and then
 * it is stood up the right way round.
 *
 * The number is decided on the first two lines, off the angles the throw
 * actually left behind, and nothing after that can change it. Standing the die
 * up only turns the cube about the face already pointing at you — the same face
 * either way, so the throw stays honest.
 *
 * Which is a rule pips never needed. A 6 lying on its side is still a 6, so the
 * web tray could stop wherever it liked and nobody could tell. A letter cannot:
 * snapping each axis to its own nearest 90 left only a quarter of throws
 * standing upright, and on a die whose whole job is to call out a square, three
 * reads in four came up sideways or on their head.
 */
private fun settle(state: DiceTrayState) {
    for (b in state.dice) {
        val sz = (b.rot / 90f).roundToInt() * 90f
        val sx = (b.tumble / 90f).roundToInt() * 90f
        val sy = (b.roll / 90f).roundToInt() * 90f
        b.value = faceToward(sz, sx, sy)
        // The glide is kept: the stand-up damps it out while the die rocks
        // flat, so it does not freeze mid-slide and then turn.
        b.spin = 0f
        val (fx, fy) = FACING.getValue(b.value)
        b.toRot = 0f; b.toTumble = fx; b.toRoll = fy
        // From the revolution nearest the target, or a die that has gone over
        // six times unwinds all six on its way to standing up.
        b.fromRot = nearest(b.rot, b.toRot)
        b.fromTumble = nearest(b.tumble, b.toTumble)
        b.fromRoll = nearest(b.roll, b.toRoll)
        b.rot = b.fromRot; b.tumble = b.fromTumble; b.roll = b.fromRoll
    }
    state.standing = true
    state.standStart = 0L
}

/**
 * The last turn onto the face, played out over about four tenths of a second
 * with a small overshoot: the die tips past flat by a few degrees and rocks
 * back, which is what a real one does when it stops rolling — never a snap.
 * Only when it has finished is the throw called, so the number arrives with
 * the picture rather than ahead of it.
 */
private fun standStep(state: DiceTrayState, now: Long) {
    if (state.standStart == 0L) { state.standStart = now; state.standLast = now }
    val dt = min(0.032f, (now - state.standLast) / 1e9f)
    state.standLast = now
    val t = ((now - state.standStart) / 4.2e8f).coerceIn(0f, 1f)
    // Ease-out with a rock past the flat: back-out, softened.
    val u = t - 1f
    val e = 1f + 2.3f * u * u * u + 1.3f * u * u
    for (b in state.dice) {
        // The last of the slide plays out under the turn and damps away.
        b.x = (b.x + b.vx * dt).coerceIn(state.reachPx, state.fieldW - state.reachPx)
        b.y = (b.y + b.vy * dt).coerceIn(state.reachPx, state.fieldH - state.reachPx)
        b.vx *= 0.88f; b.vy *= 0.88f
        b.rot = b.fromRot + (b.toRot - b.fromRot) * e
        b.tumble = b.fromTumble + (b.toTumble - b.fromTumble) * e
        b.roll = b.fromRoll + (b.toRoll - b.fromRoll) * e
    }
    if (t < 1f) return
    for (b in state.dice) { b.vx = 0f; b.vy = 0f }
    state.standing = false
    state.rolling = false
    state.onSettle?.invoke(state.dice.map { it.value })
}

/*
 * The cube itself, projected by hand: Compose has no preserve-3d, so the
 * six faces are turned through the same three angles the physics tracks,
 * backfaces culled, the rest shaded by how squarely they face the light
 * and filled as perspective quads — a die that really goes over and over,
 * not a card with the number repainted. Each row is a face: its value,
 * outward normal, the axis that runs right across it and the one that
 * runs down it.
 */
private val FACE_DEFS = arrayOf(
    intArrayOf(1, 0, 0, 1, 1, 0, 0, 0, 1, 0),
    intArrayOf(6, 0, 0, -1, -1, 0, 0, 0, 1, 0),
    intArrayOf(3, 1, 0, 0, 0, 0, -1, 0, 1, 0),
    intArrayOf(4, -1, 0, 0, 0, 0, 1, 0, 1, 0),
    intArrayOf(5, 0, -1, 0, 1, 0, 0, 0, 0, 1),
    intArrayOf(2, 0, 1, 0, 1, 0, 0, 0, 0, -1),
)

@Composable
private fun DieView(
    state: DiceTrayState,
    index: Int,
    die: TrayDie,
    sizePx: Float,
    dieSize: androidx.compose.ui.unit.Dp,
    faceColor: Color,
    pipColor: Color,
) {
    // The letter die wears battleship red with white call letters; the
    // number die keeps the bone face and dark ink.
    val letter = index == state.letterDie
    val face = if (letter) Color(0xFFC81428) else faceColor
    val ink = if (letter) Color.White else pipColor
    val glyphPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD
            )
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    Box(
        Modifier
            .offset { IntOffset((die.x - sizePx / 2).roundToInt(), (die.y - sizePx / 2).roundToInt()) }
            .size(dieSize)
            .graphicsLayer {
                val s = if (die.held) 1.12f else 1f
                scaleX = s; scaleY = s
            }
            .drawBehind {
                if (die.held) {
                    drawCircle(Color(0x59FF40A0), radius = size.width * 0.72f, center = center)
                }
                drawDieCube(die.rot, die.tumble, die.roll, face, ink, letter, glyphPaint)
            },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDieCube(
    zDeg: Float,
    xDeg: Float,
    yDeg: Float,
    face: Color,
    ink: Color,
    letterDie: Boolean,
    glyphPaint: android.graphics.Paint,
) {
    val half = size.width * 0.46f
    val persp = size.width * 3.6f
    val rz = Math.toRadians(zDeg.toDouble()); val rx = Math.toRadians(xDeg.toDouble())
    val ry = Math.toRadians(yDeg.toDouble())
    val cz = cos(rz); val sz = sin(rz)
    val cxr = cos(rx); val sxr = sin(rx)
    val cyr = cos(ry); val syr = sin(ry)
    // rotateY, then rotateX, then rotateZ — the same order faceToward reads.
    fun turn(vx: Double, vy: Double, vz: Double): DoubleArray {
        val x1 = cyr * vx + syr * vz; val z1 = -syr * vx + cyr * vz
        val y2 = cxr * vy - sxr * z1; val z2 = sxr * vy + cxr * z1
        return doubleArrayOf(cz * x1 - sz * y2, sz * x1 + cz * y2, z2)
    }

    fun proj(v: DoubleArray): Offset {
        val s = persp / (persp - v[2] * half)
        return Offset(
            (center.x + v[0] * half * s).toFloat(),
            (center.y + v[1] * half * s).toFloat(),
        )
    }

    // A soft shadow pinned under the cube, so it reads as a thing, not a tile.
    drawCircle(Color(0x33000000), radius = half * 1.02f, center = center + Offset(0f, half * 0.16f))

    val srcPts = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
    val dstPts = FloatArray(8)
    val warp = android.graphics.Matrix()
    for (def in FACE_DEFS) {
        val n = turn(def[1].toDouble(), def[2].toDouble(), def[3].toDouble())
        if (n[2] <= 0.02) continue   // facing away
        val value = def[0]
        val t1x = def[4].toDouble(); val t1y = def[5].toDouble(); val t1z = def[6].toDouble()
        val t2x = def[7].toDouble(); val t2y = def[8].toDouble(); val t2z = def[9].toDouble()
        val tl = proj(turn(def[1] - t1x - t2x, def[2] - t1y - t2y, def[3] - t1z - t2z))
        val tr = proj(turn(def[1] + t1x - t2x, def[2] + t1y - t2y, def[3] + t1z - t2z))
        val br = proj(turn(def[1] + t1x + t2x, def[2] + t1y + t2y, def[3] + t1z + t2z))
        val bl = proj(turn(def[1] - t1x + t2x, def[2] - t1y + t2y, def[3] - t1z + t2z))
        val light = (0.5 + 0.5 * n[2]).toFloat()
        val lit = Color(face.red * light, face.green * light, face.blue * light, 1f)
        val quad = androidx.compose.ui.graphics.Path().apply {
            moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
        }
        drawPath(quad, lit)
        drawPath(
            quad, Color(0f, 0f, 0f, 0.35f),
            style = Stroke(width = size.width * 0.03f),
        )
        // The face's own character, warped onto it with the quad.
        dstPts[0] = tl.x; dstPts[1] = tl.y; dstPts[2] = tr.x; dstPts[3] = tr.y
        dstPts[4] = br.x; dstPts[5] = br.y; dstPts[6] = bl.x; dstPts[7] = bl.y
        if (warp.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
            drawContext.canvas.nativeCanvas.let { nc ->
                nc.save()
                nc.concat(warp)
                glyphPaint.color = android.graphics.Color.argb(
                    (255 * (0.45f + 0.55f * light)).roundToInt(),
                    (ink.red * 255).roundToInt(), (ink.green * 255).roundToInt(),
                    (ink.blue * 255).roundToInt(),
                )
                glyphPaint.textSize = 58f
                val ch = if (letterDie) ('A' + value - 1).toString() else value.toString()
                nc.drawText(ch, 50f, 50f - (glyphPaint.ascent() + glyphPaint.descent()) / 2f, glyphPaint)
                nc.restore()
            }
        }
    }
}

/**
 * The web tray's whole hand, in one gesture: carry a die (and whatever is
 * piled on it), rattle it with quick reversals, and throw it by carrying it
 * up over the line — or flicking it upward hard enough for where it is.
 *
 * It listens on the field itself, the way the web tray reads clientX/Y off
 * the document: every position is in the tray's own frame, so a die moving
 * under the finger cannot eat the speed the finger actually has.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.trayGesture(
    state: DiceTrayState,
    sizePx: Float,
) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown()
            if (state.rolling) continue
            // The die under the finger; a touch on open felt is not a hand.
            val die = state.dice.minByOrNull { d ->
                val dx = d.x - down.position.x; val dy = d.y - down.position.y
                dx * dx + dy * dy
            } ?: continue
            val grabDx = die.x - down.position.x
            val grabDy = die.y - down.position.y
            if (sqrt(grabDx * grabDx + grabDy * grabDy) > sizePx * 0.9f) continue
            // The pile: everything close enough to come up with this die.
            val reach = state.density * 52f * 0.62f
            val pile = state.dice.filter { other ->
                val dx = other.x - die.x; val dy = other.y - die.y
                other === die || sqrt(dx * dx + dy * dy) <= reach
            }
            pile.forEach { it.held = true }
            var lastPos = down.position
            var lastT = down.uptimeMillis
            var endT = lastT
            var speed = 0f                 // upward px/ms
            var moved = false
            var launched = false
            val y0 = down.position.y
            var wasBelow = die.y >= state.fieldH * 0.5f
            var shakeX = down.position.x
            var shakeDir = 0
            var shakeY = down.position.y
            var turns = mutableListOf<Long>()
            var shaking = false
            var rattled = 0L
            val startY = down.position.y
            val startX = down.position.x
            var totalDy = 0f

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                endT = change.uptimeMillis
                if (!change.pressed) break
                val pos = change.position
                val now = change.uptimeMillis
                val dt = now - lastT
                if (dt > 0) speed = (lastPos.y - pos.y) / dt
                val dx = pos.x - lastPos.x
                val dy = pos.y - lastPos.y
                lastPos = pos; lastT = now
                totalDy = pos.y - startY
                if (abs(pos.x - startX) > 6 * state.density || abs(totalDy) > 6 * state.density) moved = true

                // Carry the pile, held inside the walls as it goes.
                val edge = state.density * 52f * 0.55f
                for (m in pile) {
                    m.x = (m.x + dx).coerceIn(edge, state.fieldW - edge)
                    m.y = (m.y + dy).coerceIn(edge, state.fieldH - edge)
                }
                change.consume()

                // A rattle is quick reversals on one level.
                if (shaking && shakeY - pos.y > 44 * state.density) {
                    shaking = false; turns.clear(); shakeDir = 0
                    state.sound?.shakeOff()
                }
                if (abs(pos.x - shakeX) >= 9 * state.density) {
                    val dir = if (pos.x > shakeX) 1 else -1
                    shakeX = pos.x
                    if (shakeDir != 0 && dir != shakeDir) {
                        if (abs(pos.y - shakeY) > 30 * state.density) turns.clear()
                        turns = turns.filter { now - it < 900 }.toMutableList()
                        turns.add(now)
                        shakeY = pos.y
                        if (turns.size >= 3) shaking = true
                    }
                    shakeDir = dir
                }
                if (shaking) {
                    state.sound?.shakeOn()
                    if (now - rattled >= 80) {
                        rattled = now
                        state.rattle()
                    }
                }

                // Carrying it up over the line is the throw, finger or not.
                val below = die.y >= state.fieldH * 0.5f
                if (wasBelow && !below && !shaking && speed > 0) {
                    val strength = ((speed / state.density - LOB) / (FAST - LOB)).coerceIn(0.15f, 1f)
                    pile.forEach { it.held = false }
                    state.sound?.shakeOff()
                    state.launch(strength)
                    launched = true
                    break
                }
                wasBelow = below
            }

            pile.forEach { it.held = false }
            state.sound?.shakeOff()
            if (launched || state.rolling) continue
            if (shaking || !moved) continue
            // A flick: upward and still moving when let go. Down in the
            // throwing band a gentle lift is enough; out on the felt it has
            // to be a real one — and mostly upward, so carrying a die across
            // the table and setting it down does not throw the lot.
            val staleness = endT - lastT
            val v = if (staleness > 90) 0f else speed / state.density
            val near = y0 >= state.fieldH * 0.42f || lastPos.y >= state.fieldH * 0.42f
            val needs = if (near) LOB else FLICK
            val throwDy = if (near) -16f else -THROW_PX
            val sideways = abs(lastPos.x - startX)
            if (totalDy / state.density <= throwDy && v >= needs && sideways < abs(totalDy) * 1.6f) {
                state.launch(((v - needs) / (FAST - needs)).coerceIn(0f, 1f))
            }
        }
    }
}
