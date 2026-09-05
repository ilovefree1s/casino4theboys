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

/* Pixels-per-dp and seconds, matching the web tray's CSS-pixel tuning. */
private const val GRAVITY = 2600f
private const val BOUNCE = 0.42f
private const val FLOOR_DRAG = 0.82f
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
    var vx = 0f; var vy = 0f
    var spin = 0f; var tumble = 0f; var roll = 0f
}

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
    val dice = List(count) { TrayDie().apply { value = 1 + random.nextInt(6) } }
    var rolling by mutableStateOf(false)
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
            d.value = v
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
        while (state.rolling) {
            withFrameNanos { now ->
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
                    if (speed > 26f * state.density) moving++
                    // Over and over about two axes at once, at a rate set by
                    // how fast it travels — no throw looks like the last one.
                    b.tumble += speed * 0.8f * dt / state.density
                    b.roll += speed * 0.55f * dt / state.density
                    // The face is read live off the same angles the settle
                    // will read, so the numbers flicker honestly as it goes.
                    b.value = faceToward(b.rot, b.tumble, b.roll)
                }
                still = if (moving > 0) 0 else still + 1
                if (still >= 8 || now - began > 6_000_000_000L) {
                    settle(state)
                }
            }
        }
    }
}

/** The die rocks onto its nearest flat side, and that side is read off it. */
private fun settle(state: DiceTrayState) {
    val values = state.dice.map { b ->
        val sz = (b.rot / 90f).roundToInt() * 90f
        val sx = (b.tumble / 90f).roundToInt() * 90f
        val sy = (b.roll / 90f).roundToInt() * 90f
        b.rot = sz
        b.value = faceToward(sz, sx, sy)
        b.vx = 0f; b.vy = 0f; b.spin = 0f
        b.value
    }
    state.rolling = false
    state.onSettle?.invoke(values)
}

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
    // number die keeps the bone face and dark pips.
    val letter = index == state.letterDie
    Box(
        Modifier
            .offset { IntOffset((die.x - sizePx / 2).roundToInt(), (die.y - sizePx / 2).roundToInt()) }
            .size(dieSize)
            .graphicsLayer {
                rotationZ = die.rot
                val s = if (die.held) 1.1f else 1f
                scaleX = s; scaleY = s
            }
            .background(if (letter) Color(0xFFC81428) else faceColor, RoundedCornerShape(10.dp))
            .then(
                if (die.held) Modifier.border(2.dp, Color(0x80FF40A0), RoundedCornerShape(10.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Both dice call their face outright — the letter for the row, the
        // numeral for the column — battleship coordinates, not pips.
        Text(
            if (letter) ('A' + die.value - 1).toString() else die.value.toString(),
            color = if (letter) Color.White else pipColor,
            fontSize = with(LocalDensity.current) { (sizePx * 0.44f).toSp() },
            fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
        )
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
