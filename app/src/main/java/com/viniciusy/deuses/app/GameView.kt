package com.viniciusy.deuses.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    companion object {
        const val AW = 2600f
        const val AH = 1700f
        const val SC = 2.0f              // escala das sprites
        const val BODY = 40f             // altura do centro do corpo acima dos pés
        val DIRS = arrayOf("south", "east", "north", "west")
        val CD_MAX = floatArrayOf(0.5f, 4f, 5f, 30f)       // 0 ataque, 1 passo, 2 raio, 3 ultimate
        val CAST_DUR = floatArrayOf(0.45f, 0f, 0.7f, 1.5f)
        const val ULT_R = 300f
        const val PX = 2f
        const val C_CYAN = 0xFF7FE8FF.toInt()
        const val C_BLUE = 0xFF3A7BFF.toInt()
        const val C_GOLD = 0xFFFFE082.toInt()
        const val C_RED = 0xFFFF8A80.toInt()
        const val C_REDD = 0xFFE53935.toInt()
        const val C_WHITE = 0xFFFFFFFF.toInt()
    }

    // ---------- tipos ----------
    private class Fighter(val name: String, val isPlayer: Boolean, var x: Float, var y: Float, val maxHp: Float) {
        var hp = maxHp
        var faceX = 0f
        var faceY = 1f
        var moveX = 0f
        var moveY = 0f
        var moving = false
        var animT = 0f
        var castKind = -1
        var castT = 0f
        var castDur = 0f
        var castFired = false
        var aimX = 0f
        var aimY = 1f
        var stun = 0f
        var dashT = 0f
        var dashDx = 0f
        var dashDy = 0f
        var dashHit = false
        var flash = 0f
        var shock = 0f
        var dead = false
        var respawnT = 0f
        val cd = FloatArray(4)
        var aiT = 1f
        var aiCast = 2.5f
        var tx = x
        var ty = y
    }

    private class Proj(val owner: Fighter, var x: Float, var y: Float, val vx: Float, val vy: Float,
                       val dmg: Float, var life: Float, val enemy: Boolean)

    private class BoltFx(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val jitter: Float,
                      var life: Float, val maxLife: Float, val width: Float, val col: Int, val outer: Int, val branches: Int) {
        var main = FloatArray(0)
        val subs = ArrayList<FloatArray>()
        var flick = 0f
    }
    private class Scorch(val x: Float, val y: Float, val r: Float, var life: Float, val maxLife: Float) {
        val ox = FloatArray(14)
        val oy = FloatArray(14)
        val os = FloatArray(14)
        init {
            for (i in 0 until 14) {
                val a = Random.nextFloat() * 6.2832f
                val d = sqrt(Random.nextFloat()) * r
                ox[i] = cos(a) * d
                oy[i] = sin(a) * d * 0.55f
                os[i] = 4f + Random.nextFloat() * 8f
            }
        }
    }
    private class Ring(val x: Float, val y: Float, val maxR: Float, var life: Float, val maxLife: Float, val color: Int)
    private class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float,
                       val color: Int, val size: Float)
    private class FText(var x: Float, var y: Float, val text: String, var life: Float, val color: Int)
    private class Ghost(val x: Float, val y: Float, val dir: Int, var life: Float)
    private class Strike(val owner: Fighter, val x: Float, val y: Float, var delay: Float, val maxDelay: Float)
    private class Storm(val owner: Fighter, val x: Float, val y: Float, var t: Float, val dur: Float, var acc: Float)
    private class Btn(val idx: Int, val label: String) {
        var cx = 0f
        var cy = 0f
        var r = 0f
    }

    // ---------- estado ----------
    private val lock = Any()
    @Volatile private var running = false
    private var thread: Thread? = null

    private val player = Fighter("Zeus", true, AW / 2f, AH / 2f + 150f, 1000f)
    private val enemy = Fighter("Inimigo de Teste", false, AW / 2f, AH / 2f - 250f, 1000f)
    private val projs = ArrayList<Proj>()
    private val bolts = ArrayList<BoltFx>()
    private val rings = ArrayList<Ring>()
    private val parts = ArrayList<Part>()
    private val texts = ArrayList<FText>()
    private val ghosts = ArrayList<Ghost>()
    private val scorches = ArrayList<Scorch>()
    private val strikes = ArrayList<Strike>()
    private val storms = ArrayList<Storm>()

    private var camX = 0f
    private var camY = 0f
    private var shake = 0f
    private var screenFlash = 0f
    private var hintT = 8f
    private var timeAll = 0f
    private var hitStop = 0f
    private var darken = 0f

    // entrada
    private var joyId = -1
    private var joyBX = 0f
    private var joyBY = 0f
    private var joyX = 0f
    private var joyY = 0f
    private var atkId = -1
    private val btns = arrayOf(Btn(0, "ATQ"), Btn(1, "PASSO"), Btn(2, "RAIO"), Btn(3, "FÚRIA"))
    private var u = 1f
    private var vs = 1f

    // sprites
    private fun load(p: String): Bitmap = context.assets.open(p).use { BitmapFactory.decodeStream(it) }
    private val idle = Array(4) { d -> Array(4) { i -> load("zeus/idle/${DIRS[d]}/frame_%03d.png".format(i)) } }
    private val walk = Array(4) { d -> Array(6) { i -> load("zeus/walk/${DIRS[d]}/frame_%03d.png".format(i)) } }
    private val cast = Array(4) { d -> Array(17) { i -> load("zeus/cast/${DIRS[d]}/frame_%03d.png".format(i)) } }

    private val redF: ColorFilter = LightingColorFilter(0xFFFF7070.toInt(), 0)
    private val flashF: ColorFilter = LightingColorFilter(0xFFFFFFFF.toInt(), 0x00707070)
    private val ghostF: ColorFilter = PorterDuffColorFilter(0xFF8FE9FF.toInt(), PorterDuff.Mode.SRC_ATOP)

    private val spritePaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val fx = Paint().apply { isAntiAlias = false; strokeCap = Paint.Cap.SQUARE; strokeJoin = Paint.Join.MITER }
    private val dst = RectF()
    private val path = Path()

    init {
        holder.addCallback(this)
        isFocusable = true
        enemy.faceY = 1f
    }

    // ---------- ciclo ----------
    override fun surfaceCreated(h: SurfaceHolder) {
        running = true
        thread = Thread(this).also { it.start() }
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        running = false
        try { thread?.join(500) } catch (e: InterruptedException) {}
        thread = null
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        u = h / 540f
        vs = h / 860f
        btns[0].apply { cx = w - 150 * u; cy = h - 130 * u; r = 78 * u }
        btns[1].apply { cx = w - 300 * u; cy = h - 90 * u; r = 54 * u }
        btns[2].apply { cx = w - 265 * u; cy = h - 235 * u; r = 54 * u }
        btns[3].apply { cx = w - 120 * u; cy = h - 295 * u; r = 58 * u }
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1e9f
            last = now
            if (dt > 0.033f) dt = 0.033f
            val c = try { holder.lockCanvas() } catch (e: Exception) { null }
            if (c == null) {
                try { Thread.sleep(10) } catch (e: InterruptedException) {}
                continue
            }
            try {
                synchronized(lock) {
                    update(dt)
                    render(c)
                }
            } finally {
                try { holder.unlockCanvasAndPost(c) } catch (e: Exception) {}
            }
        }
    }

    // ---------- util ----------
    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    private fun dirOf(fx: Float, fy: Float): Int =
        if (abs(fx) > abs(fy)) (if (fx > 0) 1 else 3) else (if (fy > 0) 0 else 2)

    private fun opp(f: Fighter) = if (f === player) enemy else player

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abx = bx - ax
        val aby = by - ay
        val l2 = abx * abx + aby * aby
        var t = if (l2 == 0f) 0f else ((px - ax) * abx + (py - ay) * aby) / l2
        t = t.coerceIn(0f, 1f)
        val cx = ax + abx * t - px
        val cy = ay + aby * t - py
        return sqrt(cx * cx + cy * cy)
    }

    private fun snap(v: Float): Float = (v / PX).roundToInt() * PX

    private fun burst(x: Float, y: Float, n: Int, color: Int, speed: Float, life: Float) {
        if (parts.size > 450) return
        for (i in 0 until n) {
            val a = rnd(0f, 6.2832f)
            val sp = rnd(speed * 0.3f, speed)
            parts.add(Part(x, y, cos(a) * sp, sin(a) * sp, rnd(life * 0.5f, life), life, color, 4f))
        }
    }

    private fun genPoly(x1: Float, y1: Float, x2: Float, y2: Float, jitter: Float, iters: Int): FloatArray {
        var pts = floatArrayOf(x1, y1, x2, y2)
        var j = jitter
        for (it in 0 until iters) {
            val n = pts.size / 2
            val out = FloatArray((n * 2 - 1) * 2)
            var k = 0
            for (i in 0 until n - 1) {
                val ax = pts[i * 2]
                val ay = pts[i * 2 + 1]
                val bx = pts[i * 2 + 2]
                val by = pts[i * 2 + 3]
                out[k++] = ax
                out[k++] = ay
                val dx = bx - ax
                val dy = by - ay
                val l = max(1f, hypot(dx, dy))
                val off = rnd(-j, j)
                out[k++] = (ax + bx) / 2f - dy / l * off
                out[k++] = (ay + by) / 2f + dx / l * off
            }
            out[k++] = pts[(n - 1) * 2]
            out[k++] = pts[(n - 1) * 2 + 1]
            pts = out
            j *= 0.55f
        }
        for (i in pts.indices) pts[i] = snap(pts[i])
        return pts
    }

    private fun regen(b: BoltFx) {
        val len = hypot(b.x2 - b.x1, b.y2 - b.y1)
        val iters = if (len < 120f) 3 else 4
        b.main = genPoly(b.x1, b.y1, b.x2, b.y2, b.jitter, iters)
        b.subs.clear()
        val n = b.main.size / 2
        if (b.branches > 0 && n > 6) {
            val base = atan2(b.y2 - b.y1, b.x2 - b.x1)
            for (i in 0 until b.branches) {
                val idx = Random.nextInt(2, n - 2)
                val sx = b.main[idx * 2]
                val sy = b.main[idx * 2 + 1]
                val ang = base + (if (Random.nextBoolean()) 1f else -1f) * rnd(0.4f, 1.0f)
                val l = len * rnd(0.1f, 0.22f)
                b.subs.add(genPoly(sx, sy, sx + cos(ang) * l, sy + sin(ang) * l, b.jitter * 0.6f, 3))
            }
        }
    }

    private fun makeBolt(x1: Float, y1: Float, x2: Float, y2: Float, life: Float, width: Float,
                         col: Int, outer: Int, jitter: Float, branches: Int) {
        if (bolts.size > 110) return
        val b = BoltFx(x1, y1, x2, y2, jitter, life, life, width, col, outer, branches)
        b.flick = 0.05f
        regen(b)
        bolts.add(b)
    }

    private fun arc(x: Float, y: Float, len: Float, col: Int = C_CYAN, outer: Int = C_BLUE) {
        val a = rnd(0f, 6.2832f)
        makeBolt(x, y, x + cos(a) * len, y + sin(a) * len, 0.1f, 2f, col, outer, len * 0.25f, 0)
    }

    private fun addScorch(x: Float, y: Float, r: Float, life: Float) {
        if (scorches.size > 60) scorches.removeAt(0)
        scorches.add(Scorch(x, y, r, life, life))
    }

    private fun impact(x: Float, y: Float, sc: Float, col: Int, outer: Int) {
        rings.add(Ring(x, y, 70f * sc, 0.28f, 0.28f, col))
        rings.add(Ring(x, y, 115f * sc, 0.4f, 0.4f, outer))
        val n = (5 + 3 * sc).toInt()
        for (i in 0 until n) {
            val a = i * 6.2832f / n + rnd(-0.2f, 0.2f)
            makeBolt(x, y, x + cos(a) * 55f * sc, y + sin(a) * 45f * sc, 0.16f, 2.5f, col, outer, 6f * sc, 0)
        }
        burst(x, y, (10 * sc).toInt(), col, 360f * sc, 0.5f)
        burst(x, y, (6 * sc).toInt(), C_WHITE, 260f * sc, 0.35f)
    }

    private fun damage(t: Fighter, amount: Float, shock: Float) {
        if (t.dead || t.dashT > 0f) return
        t.hp -= amount
        t.flash = 0.15f
        t.shock = max(t.shock, shock)
        texts.add(FText(t.x + rnd(-20f, 20f), t.y - 120f, amount.toInt().toString(), 0.8f,
            if (t.isPlayer) 0xFFFF8A80.toInt() else 0xFFFFF59D.toInt()))
        if (t.hp <= 0f) {
            t.hp = 0f
            t.dead = true
            t.respawnT = 2.2f
            t.castKind = -1
            texts.add(FText(t.x, t.y - 150f, if (t.isPlayer) "Derrotado!" else "Inimigo derrotado!", 1.6f, 0xFFFFFFFF.toInt()))
            burst(t.x, t.y - BODY, 30, C_GOLD, 420f, 0.8f)
            impact(t.x, t.y - BODY, 1.6f, C_GOLD, C_CYAN)
            hitStop = max(hitStop, 0.12f)
            shake = max(shake, 0.3f)
        }
    }

    // ---------- habilidades ----------
    private fun aim(f: Fighter) {
        val o = opp(f)
        var ax = o.x - f.x
        var ay = o.y - f.y
        val d = hypot(ax, ay)
        if (!o.dead && d < 650f && d > 1f) {
            ax /= d; ay /= d
        } else {
            ax = f.faceX; ay = f.faceY
            val l = hypot(ax, ay)
            if (l < 0.01f) { ax = 0f; ay = 1f } else { ax /= l; ay /= l }
        }
        f.aimX = ax
        f.aimY = ay
        f.faceX = ax
        f.faceY = ay
    }

    private fun startCast(f: Fighter, kind: Int) {
        if (f.dead || f.castKind >= 0 || f.stun > 0f || f.dashT > 0f || f.cd[kind] > 0f) return
        f.cd[kind] = CD_MAX[kind]
        f.castKind = kind
        f.castT = 0f
        f.castDur = CAST_DUR[kind]
        f.castFired = false
        f.moving = false
        aim(f)
    }

    private fun startDash(f: Fighter) {
        if (f.dead || f.castKind >= 0 || f.stun > 0f || f.dashT > 0f || f.cd[1] > 0f) return
        var dx = f.moveX
        var dy = f.moveY
        var l = hypot(dx, dy)
        if (l < 0.2f) { dx = f.faceX; dy = f.faceY; l = hypot(dx, dy) }
        if (l < 0.01f) { dx = 0f; dy = 1f; l = 1f }
        f.dashDx = dx / l
        f.dashDy = dy / l
        f.faceX = f.dashDx
        f.faceY = f.dashDy
        f.dashT = 0.16f
        f.dashHit = false
        f.cd[1] = CD_MAX[1]
        val ex = (f.x + f.dashDx * 288f).coerceIn(70f, AW - 70f)
        val ey = (f.y + f.dashDy * 288f).coerceIn(150f, AH - 50f)
        makeBolt(f.x, f.y - BODY, ex, ey - BODY, 0.32f, 6f, C_CYAN, C_BLUE, 22f, 3)
        makeBolt(f.x, f.y - BODY, ex, ey - BODY, 0.22f, 3f, C_WHITE, C_CYAN, 12f, 1)
        impact(f.x, f.y - BODY, 0.9f, C_CYAN, C_BLUE)
        addScorch(f.x, f.y, 40f, 3f)
    }

    private fun fire(f: Fighter, kind: Int) {
        val ox = f.x + f.aimX * 34f
        val oy = f.y - BODY - 6f + f.aimY * 10f
        val col = if (f.isPlayer) C_CYAN else C_RED
        val outer = if (f.isPlayer) C_BLUE else C_REDD
        when (kind) {
            0 -> {
                val sp = if (f.isPlayer) 780f else 420f
                val dmg = if (f.isPlayer) 45f else 35f
                projs.add(Proj(f, ox, oy, f.aimX * sp, f.aimY * sp, dmg, 0.75f, !f.isPlayer))
                burst(ox, oy, 8, col, 220f, 0.3f)
                rings.add(Ring(ox, oy, 40f, 0.18f, 0.18f, col))
                for (i in 0 until 3) arc(ox, oy, rnd(25f, 45f), col, outer)
            }
            2 -> {
                val len = 560f
                val ex = ox + f.aimX * len
                val ey = oy + f.aimY * len
                makeBolt(ox, oy, ex, ey, 0.34f, 8f, C_CYAN, C_BLUE, 26f, 6)
                makeBolt(ox, oy, ex, ey, 0.26f, 4f, C_WHITE, C_CYAN, 16f, 3)
                var d = 60f
                while (d < len) {
                    val px = ox + f.aimX * d
                    val py = oy + f.aimY * d
                    addScorch(px, py + BODY, 26f, 3.5f)
                    burst(px, py, 3, C_CYAN, 200f, 0.5f)
                    d += 90f
                }
                impact(ex, ey, 1.2f, C_CYAN, C_BLUE)
                addScorch(ex, ey + BODY, 60f, 4.5f)
                rings.add(Ring(ox, oy, 60f, 0.25f, 0.25f, C_WHITE))
                val o = opp(f)
                if (!o.dead && segDist(o.x, o.y - BODY, ox, oy, ex, ey) <= 56f) {
                    damage(o, 160f, 0.9f)
                    o.stun = 0.6f
                    impact(o.x, o.y - BODY, 1.5f, C_WHITE, C_CYAN)
                    hitStop = max(hitStop, 0.07f)
                }
                screenFlash = max(screenFlash, 0.12f)
                shake = max(shake, 0.25f)
            }
            3 -> {
                storms.add(Storm(f, f.x, f.y, 0f, 2.6f, 0f))
                rings.add(Ring(f.x, f.y, ULT_R, 0.6f, 0.6f, C_GOLD))
                rings.add(Ring(f.x, f.y, ULT_R * 0.6f, 0.45f, 0.45f, C_WHITE))
                rings.add(Ring(f.x, f.y - BODY, ULT_R * 1.2f, 0.5f, 0.5f, C_CYAN))
                for (i in 0 until 10) {
                    val a = i * 0.6283f
                    makeBolt(f.x, f.y - BODY, f.x + cos(a) * 190f, f.y - BODY + sin(a) * 150f, 0.3f, 4f, C_WHITE, C_GOLD, 14f, 1)
                }
                burst(f.x, f.y - BODY, 40, C_GOLD, 520f, 0.9f)
                addScorch(f.x, f.y, 90f, 6f)
                screenFlash = 0.4f
                shake = 0.6f
                hitStop = max(hitStop, 0.06f)
            }
        }
    }

    private fun chargeFx(f: Fighter) {
        val hx = f.x + f.aimX * 34f
        val hy = f.y - BODY - 6f
        val prog = (f.castT / (f.castDur * 0.47f)).coerceIn(0f, 1f)
        val col = if (f.isPlayer) C_CYAN else C_RED
        val outer = if (f.isPlayer) C_BLUE else C_REDD
        when (f.castKind) {
            0 -> {
                if (Random.nextFloat() < 0.5f) {
                    val a = rnd(0f, 6.2832f)
                    parts.add(Part(hx + cos(a) * 40f, hy + sin(a) * 40f, -cos(a) * 150f, -sin(a) * 150f, 0.25f, 0.25f, col, 4f))
                }
                if (Random.nextFloat() < 0.2f) arc(hx, hy, rnd(20f, 38f), col, outer)
            }
            2 -> {
                for (i in 0 until 2) {
                    val a = rnd(0f, 6.2832f)
                    val r = rnd(40f, 80f)
                    parts.add(Part(hx + cos(a) * r, hy + sin(a) * r, -cos(a) * 220f, -sin(a) * 220f, 0.3f, 0.3f, C_CYAN, 4f))
                }
                if (Random.nextFloat() < 0.5f) arc(hx, hy, rnd(30f, 60f))
                if (Random.nextFloat() < 0.35f) arc(f.x + rnd(-25f, 25f), f.y - BODY + rnd(-40f, 40f), rnd(35f, 70f))
            }
            3 -> {
                for (i in 0 until 3) {
                    val a = rnd(0f, 6.2832f)
                    val r = rnd(80f, 150f)
                    parts.add(Part(f.x + cos(a) * r, f.y - BODY + sin(a) * r, -cos(a) * 260f, -sin(a) * 260f, 0.4f, 0.4f,
                        if (i == 0) C_GOLD else C_CYAN, 4f))
                }
                if (Random.nextFloat() < 0.85f) arc(f.x + rnd(-30f, 30f), f.y - BODY + rnd(-45f, 45f), rnd(50f, 110f), C_WHITE, C_GOLD)
                if (Random.nextFloat() < 0.5f) {
                    val a = rnd(0f, 6.2832f)
                    val r = ULT_R * prog
                    arc(f.x + cos(a) * r, f.y + sin(a) * r * 0.6f, rnd(30f, 60f), C_GOLD, C_BLUE)
                }
                shake = max(shake, 0.04f + 0.06f * prog)
            }
        }
    }

    // ---------- update ----------
    private fun update(dt0: Float) {
        timeAll += dt0
        var dt = dt0
        if (hitStop > 0f) { hitStop -= dt0; dt = dt0 * 0.08f }
        if (hintT > 0f) hintT -= dt0

        // entrada do jogador
        player.moveX = joyX
        player.moveY = joyY
        if (atkId != -1) startCast(player, 0)

        updateFighter(player, dt)
        updateEnemyAi(dt)
        updateFighter(enemy, dt)

        // projéteis
        val pi = projs.iterator()
        while (pi.hasNext()) {
            val pr = pi.next()
            pr.x += pr.vx * dt
            pr.y += pr.vy * dt
            pr.life -= dt
            if (pr.life <= 0f) { pi.remove(); continue }
            val col = if (pr.enemy) C_RED else C_CYAN
            if (Random.nextFloat() < 0.7f) {
                parts.add(Part(pr.x, pr.y, rnd(-40f, 40f), rnd(-40f, 40f), 0.3f, 0.3f, if (Random.nextBoolean()) col else C_WHITE, 4f))
            }
            if (Random.nextFloat() < 0.2f) {
                makeBolt(pr.x, pr.y, pr.x - pr.vx * 0.06f + rnd(-12f, 12f), pr.y - pr.vy * 0.06f + rnd(-12f, 12f),
                    0.1f, 2f, col, if (pr.enemy) C_REDD else C_BLUE, 8f, 0)
            }
            val o = opp(pr.owner)
            if (!o.dead && o.dashT <= 0f && hypot(o.x - pr.x, o.y - BODY - pr.y) < 14f + 34f) {
                damage(o, pr.dmg, 0.3f)
                impact(pr.x, pr.y, 0.9f, col, if (pr.enemy) C_REDD else C_BLUE)
                addScorch(o.x, o.y, 22f, 2.5f)
                hitStop = max(hitStop, 0.03f)
                shake = max(shake, 0.08f)
                pi.remove()
            }
        }

        // tempestade (ultimate)
        val si = storms.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.t += dt
            s.acc += dt
            while (s.acc >= 0.11f) {
                s.acc -= 0.11f
                val o = opp(s.owner)
                val sx: Float
                val sy: Float
                if (!o.dead && hypot(o.x - s.x, o.y - s.y) < ULT_R + 60f && Random.nextFloat() < 0.45f) {
                    sx = o.x + rnd(-50f, 50f)
                    sy = o.y + rnd(-40f, 40f)
                } else {
                    val a = rnd(0f, 6.2832f)
                    val r = sqrt(Random.nextFloat()) * ULT_R
                    sx = s.x + cos(a) * r
                    sy = s.y + sin(a) * r
                }
                strikes.add(Strike(s.owner, sx, sy, 0.6f, 0.6f))
            }
            if (Random.nextFloat() < 0.4f) {
                val a = rnd(0f, 6.2832f)
                arc(s.x + cos(a) * ULT_R, s.y + sin(a) * ULT_R * 0.6f, rnd(40f, 90f), C_GOLD, C_BLUE)
            }
            if (s.t >= s.dur) si.remove()
        }
        val ki = strikes.iterator()
        while (ki.hasNext()) {
            val k = ki.next()
            k.delay -= dt
            if (k.delay <= 0f) {
                val skyX = k.x + rnd(-40f, 40f)
                makeBolt(skyX, k.y - 800f, k.x, k.y, 0.3f, 9f, C_CYAN, C_BLUE, 38f, 4)
                makeBolt(skyX, k.y - 800f, k.x, k.y, 0.22f, 4f, C_WHITE, C_GOLD, 22f, 2)
                if (Random.nextFloat() < 0.4f) {
                    makeBolt(k.owner.x, k.owner.y - BODY, k.x, k.y, 0.12f, 3f, C_WHITE, C_CYAN, 30f, 1)
                }
                impact(k.x, k.y, 1.3f, C_GOLD, C_CYAN)
                addScorch(k.x, k.y, 55f, 7f)
                val o = opp(k.owner)
                if (!o.dead && hypot(o.x - k.x, o.y - BODY - k.y) < 85f) {
                    damage(o, 90f, 0.6f)
                    impact(o.x, o.y - BODY, 1.2f, C_WHITE, C_CYAN)
                    hitStop = max(hitStop, 0.03f)
                }
                shake = max(shake, 0.14f)
                ki.remove()
            }
        }

        // escurecer o chão durante o ultimate
        val want = if (storms.isNotEmpty()) 0.4f else if (player.castKind == 3 || enemy.castKind == 3) 0.22f else 0f
        darken += (want - darken) * min(1f, dt0 * 4f)

        // efeitos
        val bi = bolts.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b.life -= dt
            b.flick -= dt
            if (b.life <= 0f) { bi.remove(); continue }
            if (b.flick <= 0f) { b.flick = 0.05f; regen(b) }
        }
        val ri = rings.iterator(); while (ri.hasNext()) { val r = ri.next(); r.life -= dt; if (r.life <= 0f) ri.remove() }
        val gi = ghosts.iterator(); while (gi.hasNext()) { val g = gi.next(); g.life -= dt; if (g.life <= 0f) gi.remove() }
        val sci = scorches.iterator(); while (sci.hasNext()) { val g = sci.next(); g.life -= dt0; if (g.life <= 0f) sci.remove() }
        val ti = texts.iterator(); while (ti.hasNext()) { val t = ti.next(); t.life -= dt0; t.y -= 40f * dt0; if (t.life <= 0f) ti.remove() }
        val pa = parts.iterator()
        while (pa.hasNext()) {
            val q = pa.next()
            q.life -= dt
            q.x += q.vx * dt
            q.y += q.vy * dt
            q.vx *= 0.96f
            q.vy *= 0.96f
            if (q.life <= 0f) pa.remove()
        }
        if (shake > 0f) shake -= dt0
        if (screenFlash > 0f) screenFlash -= dt0

        // câmera
        val visW = width / vs
        val visH = height / vs
        camX = if (visW >= AW) (AW - visW) / 2f else (player.x - visW / 2f).coerceIn(0f, AW - visW)
        camY = if (visH >= AH) (AH - visH) / 2f else (player.y - 30f - visH / 2f).coerceIn(0f, AH - visH)
    }

    private fun updateFighter(f: Fighter, dt: Float) {
        for (i in 0 until 4) if (f.cd[i] > 0f) f.cd[i] -= dt
        if (f.flash > 0f) f.flash -= dt

        if (f.dead) {
            f.respawnT -= dt
            if (f.respawnT <= 0f) {
                f.dead = false
                f.hp = f.maxHp
                if (f.isPlayer) { f.x = AW / 2f; f.y = AH / 2f + 150f }
                else { f.x = rnd(300f, AW - 300f); f.y = rnd(300f, AH / 2f); f.tx = f.x; f.ty = f.y }
                f.stun = 0f
                f.shock = 0f
                for (i in 0 until 4) f.cd[i] = 0f
            }
            return
        }

        f.animT += dt
        if (f.stun > 0f) { f.stun -= dt; f.moving = false }
        if (f.shock > 0f) {
            f.shock -= dt
            if (Random.nextFloat() < 0.4f) arc(f.x + rnd(-30f, 30f), f.y - BODY + rnd(-45f, 45f), rnd(30f, 70f))
        }

        // dash
        if (f.dashT > 0f) {
            f.dashT -= dt
            f.x += f.dashDx * 1800f * dt
            f.y += f.dashDy * 1800f * dt
            ghosts.add(Ghost(f.x, f.y, dirOf(f.faceX, f.faceY), 0.3f))
            parts.add(Part(f.x, f.y - BODY, rnd(-60f, 60f), rnd(-60f, 60f), 0.3f, 0.3f, if (Random.nextBoolean()) C_CYAN else C_WHITE, 4f))
            if (Random.nextFloat() < 0.6f) arc(f.x + rnd(-20f, 20f), f.y - BODY + rnd(-40f, 40f), rnd(25f, 55f))
            val o = opp(f)
            if (!f.dashHit && !o.dead && hypot(o.x - f.x, o.y - f.y) < 70f) {
                f.dashHit = true
                damage(o, 50f, 0.4f)
                impact(o.x, o.y - BODY, 1.1f, C_CYAN, C_BLUE)
                hitStop = max(hitStop, 0.04f)
                shake = max(shake, 0.12f)
            }
            if (f.dashT <= 0f) {
                impact(f.x, f.y - BODY, 0.8f, C_CYAN, C_BLUE)
                addScorch(f.x, f.y, 36f, 3f)
            }
            clampPos(f)
            return
        }

        // conjuração: o personagem fica parado
        if (f.castKind >= 0) {
            f.moving = false
            f.castT += dt
            if (!f.castFired) chargeFx(f)
            if (!f.castFired && f.castT >= f.castDur * 0.47f) {
                f.castFired = true
                fire(f, f.castKind)
            }
            if (f.castKind == 3 && f.castFired && Random.nextFloat() < 0.3f) {
                arc(f.x + rnd(-30f, 30f), f.y - BODY + rnd(-45f, 45f), rnd(40f, 80f), C_WHITE, C_GOLD)
            }
            if (f.castT >= f.castDur) f.castKind = -1
            clampPos(f)
            return
        }

        // movimento
        if (f.stun <= 0f) {
            val l = hypot(f.moveX, f.moveY)
            val speed = if (f.isPlayer) 240f else 110f
            f.moving = l > 0.15f
            if (l > 0.15f) {
                val nx = f.moveX / max(1f, l)
                val ny = f.moveY / max(1f, l)
                val mag = min(1f, l)
                f.x += nx * mag * speed * dt
                f.y += ny * mag * speed * dt
                f.faceX = f.moveX / l
                f.faceY = f.moveY / l
            }
        }
        clampPos(f)
    }

    private fun clampPos(f: Fighter) {
        f.x = f.x.coerceIn(70f, AW - 70f)
        f.y = f.y.coerceIn(150f, AH - 50f)
    }

    private fun updateEnemyAi(dt: Float) {
        val e = enemy
        if (e.dead) return
        e.moveX = 0f
        e.moveY = 0f
        if (e.stun > 0f || e.castKind >= 0) return
        e.aiT -= dt
        val d = hypot(e.tx - e.x, e.ty - e.y)
        if (d < 20f || e.aiT <= 0f) {
            e.tx = rnd(250f, AW - 250f)
            e.ty = rnd(250f, AH - 250f)
            e.aiT = rnd(2f, 4f)
        } else {
            e.moveX = (e.tx - e.x) / d
            e.moveY = (e.ty - e.y) / d
        }
        e.aiCast -= dt
        if (e.aiCast <= 0f && !player.dead) {
            e.aiCast = rnd(3f, 5f)
            startCast(e, 0)
        }
    }

    // ---------- desenho ----------
    private fun drawSprite(c: Canvas, b: Bitmap, x: Float, y: Float, filter: ColorFilter?, alpha: Int) {
        val w = b.width * SC
        val h = b.height * SC
        val l = x - w / 2f
        val t = y - 30f * SC - h / 2f
        dst.set(l, t, l + w, t + h)
        spritePaint.colorFilter = filter
        spritePaint.alpha = alpha
        c.drawBitmap(b, null, dst, spritePaint)
        spritePaint.colorFilter = null
        spritePaint.alpha = 255
    }

    private fun frameOf(f: Fighter): Bitmap {
        val d = dirOf(f.faceX, f.faceY)
        return if (f.castKind >= 0) {
            val fr = min(16, (f.castT / f.castDur * 17f).toInt())
            cast[d][fr]
        } else if (f.moving) {
            walk[d][((f.animT * 10f).toInt()) % 6]
        } else {
            idle[d][((f.animT * 5f).toInt()) % 4]
        }
    }

    private fun fxAlpha(a: Float, col: Int): Int = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (col and 0x00FFFFFF)

    private fun drawFighter(c: Canvas, f: Fighter) {
        if (f.dead) return
        // sombra
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawOval(f.x - 36f, f.y - 9f, f.x + 36f, f.y + 9f, p)

        // aura de carga (raio / ultimate)
        if (f.castKind == 2 || f.castKind == 3) {
            val prog = (f.castT / (f.castDur * 0.47f)).coerceIn(0f, 1f)
            val big = f.castKind == 3
            val pulse = 1f + 0.08f * sin(timeAll * 25f)
            fx.style = Paint.Style.FILL
            fx.color = fxAlpha(0.14f + 0.12f * prog, if (big) C_GOLD else C_CYAN)
            c.drawCircle(f.x, f.y - BODY, (if (big) 100f else 70f) * (0.5f + 0.5f * prog) * pulse, fx)
            fx.color = fxAlpha(0.22f, C_WHITE)
            c.drawCircle(f.x, f.y - BODY, (if (big) 60f else 40f) * (0.5f + 0.5f * prog) * pulse, fx)
        }

        val shockOn = f.shock > 0f && ((timeAll * 16f).toInt() % 2 == 0)
        val flt: ColorFilter? = if (f.flash > 0f || shockOn) flashF else if (!f.isPlayer) redF else null
        val jx = if (f.shock > 0f) rnd(-2f, 2f) else 0f
        drawSprite(c, frameOf(f), f.x + jx, f.y, flt, 255)
        if (f.stun > 0f) {
            tp.textSize = 26f
            tp.color = 0xFFFFF59D.toInt()
            c.drawText("⚡", f.x + sin(timeAll * 12f) * 18f, f.y - 135f, tp)
        }
    }

    private fun drawPoly(c: Canvas, pts: FloatArray, w: Float, a: Float, col: Int, outer: Int) {
        if (pts.size < 4) return
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        fx.style = Paint.Style.STROKE
        fx.strokeWidth = w * 3.4f
        fx.color = fxAlpha(a * 0.28f, outer)
        c.drawPath(path, fx)
        fx.strokeWidth = w * 1.8f
        fx.color = fxAlpha(a * 0.85f, col)
        c.drawPath(path, fx)
        fx.strokeWidth = w
        fx.color = fxAlpha(a, C_WHITE)
        c.drawPath(path, fx)
    }

    private fun drawBoltFx(c: Canvas, b: BoltFx) {
        val a = (b.life / b.maxLife).coerceIn(0f, 1f)
        drawPoly(c, b.main, b.width, a, b.col, b.outer)
        for (sp in b.subs) drawPoly(c, sp, b.width * 0.55f, a, b.col, b.outer)
    }

    private fun drawMiniArc(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, col: Int, outer: Int) {
        val mx = (x1 + x2) / 2f + rnd(-6f, 6f)
        val my = (y1 + y2) / 2f + rnd(-6f, 6f)
        drawPoly(c, floatArrayOf(snap(x1), snap(y1), snap(mx), snap(my), snap(x2), snap(y2)), w, 1f, col, outer)
    }

    private fun drawOrb(c: Canvas, pr: Proj) {
        val col = if (pr.enemy) C_RED else C_CYAN
        val outer = if (pr.enemy) C_REDD else C_BLUE
        val pulse = 1f + 0.15f * sin(timeAll * 30f)
        val x = snap(pr.x)
        val y = snap(pr.y)
        fx.style = Paint.Style.FILL
        fx.color = fxAlpha(0.28f, outer)
        c.drawCircle(x, y, 26f * pulse, fx)
        fx.color = fxAlpha(0.75f, col)
        c.drawCircle(x, y, 16f * pulse, fx)
        fx.color = fxAlpha(1f, C_WHITE)
        c.drawCircle(x, y, 8f, fx)
        for (k in 0 until 3) {
            val a = timeAll * 18f + k * 2.0944f
            drawMiniArc(c, x + cos(a) * 10f, y + sin(a) * 10f, x + cos(a + 0.9f) * 32f, y + sin(a + 0.9f) * 32f, 2f, col, outer)
        }
    }

    private fun render(c: Canvas) {
        c.drawColor(0xFF05070D.toInt())
        val w = width.toFloat()
        val h = height.toFloat()
        val visW = w / vs
        val visH = h / vs

        c.save()
        c.scale(vs, vs)
        var sx = 0f
        var sy = 0f
        if (shake > 0f) { sx = rnd(-6f, 6f) * min(1f, shake * 3f); sy = rnd(-6f, 6f) * min(1f, shake * 3f) }
        c.translate(-camX + sx, -camY + sy)

        drawArena(c)

        // cicatrizes no chão
        fx.style = Paint.Style.FILL
        for (sc in scorches) {
            val a = (sc.life / sc.maxLife).coerceIn(0f, 1f)
            val glow = ((sc.life - (sc.maxLife - 0.6f)) / 0.6f).coerceIn(0f, 1f)
            for (i in 0 until 14) {
                val bx = snap(sc.x + sc.ox[i])
                val by = snap(sc.y + sc.oy[i])
                val sz = snap(sc.os[i])
                fx.color = fxAlpha(min(1f, a * 1.4f) * 0.75f, 0xFF04060B.toInt())
                c.drawRect(bx, by, bx + sz, by + sz, fx)
                if (glow > 0f && i % 3 == 0) {
                    fx.color = fxAlpha(glow * 0.9f, C_CYAN)
                    c.drawRect(bx, by, bx + PX * 2, by + PX * 2, fx)
                }
            }
        }

        // anel de carga do ultimate
        for (f in listOf(player, enemy)) {
            if (!f.dead && f.castKind == 3 && !f.castFired) {
                val prog = (f.castT / (f.castDur * 0.47f)).coerceIn(0f, 1f)
                fx.style = Paint.Style.STROKE
                fx.strokeWidth = 6f
                fx.color = fxAlpha(0.35f + 0.4f * prog, C_GOLD)
                c.drawCircle(f.x, f.y, ULT_R * prog, fx)
                fx.strokeWidth = 4f
                fx.color = fxAlpha(0.3f * prog, C_CYAN)
                c.drawCircle(f.x, f.y, ULT_R * 0.6f * prog, fx)
            }
        }

        // escurece o chão (ultimate)
        if (darken > 0.01f) {
            fx.style = Paint.Style.FILL
            fx.color = fxAlpha(darken, 0xFF050A24.toInt())
            c.drawRect(camX - 60f, camY - 60f, camX + visW + 60f, camY + visH + 60f, fx)
        }

        // marcações do ultimate
        for (s in storms) {
            fx.style = Paint.Style.STROKE
            fx.strokeWidth = 5f
            fx.color = fxAlpha(0.5f, C_GOLD)
            val pulse = 1f + 0.03f * sin(timeAll * 8f)
            c.drawCircle(s.x, s.y, ULT_R * pulse, fx)
            fx.color = fxAlpha(0.22f, C_GOLD)
            c.drawCircle(s.x, s.y, ULT_R * 0.6f * pulse, fx)
        }
        for (k in strikes) {
            val prog = 1f - k.delay / k.maxDelay
            fx.style = Paint.Style.FILL
            fx.color = fxAlpha(0.1f + 0.25f * prog, C_GOLD)
            c.drawCircle(k.x, k.y, 85f * prog, fx)
            fx.style = Paint.Style.STROKE
            fx.strokeWidth = 4f
            fx.color = fxAlpha(0.8f, C_GOLD)
            c.drawCircle(k.x, k.y, 85f, fx)
            fx.color = fxAlpha(0.95f, C_WHITE)
            c.drawCircle(k.x, k.y, 85f * (1f - prog) + 8f, fx)
            if (prog > 0.5f) {
                val a = timeAll * 14f
                drawMiniArc(c, k.x + cos(a) * 85f, k.y + sin(a) * 85f * 0.8f, k.x + cos(a + 2.2f) * 60f, k.y + sin(a + 2.2f) * 50f, 2f, C_GOLD, C_BLUE)
            }
        }

        // rastros do passo
        for (g in ghosts) {
            val a = (g.life / 0.3f * 150f).toInt().coerceIn(0, 255)
            drawSprite(c, idle[g.dir][0], g.x, g.y, ghostF, a)
        }

        // lutadores ordenados por Y
        val order = if (player.y <= enemy.y) listOf(player, enemy) else listOf(enemy, player)
        for (f in order) drawFighter(c, f)

        // linha de mira do Raio Fulminante
        for (f in order) {
            if (!f.dead && f.castKind == 2 && !f.castFired) {
                val prog = (f.castT / (f.castDur * 0.47f)).coerceIn(0f, 1f)
                val hx = f.x + f.aimX * 34f
                val hy = f.y - BODY - 6f
                fx.style = Paint.Style.FILL
                fx.color = fxAlpha(0.25f + 0.55f * prog, C_CYAN)
                var d = 30f
                while (d < 560f) {
                    val px = snap(hx + f.aimX * d)
                    val py = snap(hy + f.aimY * d)
                    c.drawRect(px - 3f, py - 3f, px + 3f, py + 3f, fx)
                    d += 22f
                }
            }
        }

        // projéteis
        for (pr in projs) drawOrb(c, pr)

        // raios
        for (b in bolts) drawBoltFx(c, b)

        // anéis
        fx.style = Paint.Style.STROKE
        for (r in rings) {
            val t = 1f - r.life / r.maxLife
            fx.strokeWidth = 6f * (1f - t) + 2f
            fx.color = fxAlpha(1f - t, r.color)
            c.drawCircle(r.x, r.y, r.maxR * t, fx)
        }

        // partículas (quadradinhos pixelados)
        fx.style = Paint.Style.FILL
        for (q in parts) {
            val a = (q.life / q.maxLife).coerceIn(0f, 1f)
            fx.color = fxAlpha(a, q.color)
            val px = snap(q.x)
            val py = snap(q.y)
            val sz = if (a > 0.5f) q.size else PX * 1.5f
            c.drawRect(px, py, px + sz, py + sz, fx)
        }

        // textos de dano
        for (t in texts) {
            val a = (t.life / 0.8f).coerceIn(0f, 1f)
            tp.textSize = 30f
            tp.color = t.color
            tp.alpha = (a * 255).toInt()
            c.drawText(t.text, t.x, t.y, tp)
            tp.alpha = 255
        }
        c.restore()

        if (screenFlash > 0f) {
            p.style = Paint.Style.FILL
            p.color = ((screenFlash / 0.4f * 130).toInt().coerceIn(0, 255) shl 24) or 0xDDF6FF
            c.drawRect(0f, 0f, w, h, p)
        }

        drawHud(c, w, h)
    }

    private fun drawArena(c: Canvas) {
        p.style = Paint.Style.FILL
        val t0x = max(0, floor(camX / 100f).toInt())
        val t1x = min((AW / 100f).toInt(), ceil((camX + width / vs) / 100f).toInt())
        val t0y = max(0, floor(camY / 100f).toInt())
        val t1y = min((AH / 100f).toInt(), ceil((camY + height / vs) / 100f).toInt())
        for (ty in t0y until t1y) {
            for (tx in t0x until t1x) {
                p.color = if ((tx + ty) % 2 == 0) 0xFF1E2538.toInt() else 0xFF1A2032.toInt()
                c.drawRect(tx * 100f, ty * 100f, tx * 100f + 100f, ty * 100f + 100f, p)
            }
        }
        // runa central
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = 0x334FC3F7
        c.drawCircle(AW / 2f, AH / 2f, 130f, p)
        c.drawCircle(AW / 2f, AH / 2f, 210f, p)
        for (i in 0 until 12) {
            val a = i * 0.5236f
            c.drawLine(AW / 2f + cos(a) * 130f, AH / 2f + sin(a) * 130f, AW / 2f + cos(a) * 210f, AH / 2f + sin(a) * 210f, p)
        }
        // borda
        p.strokeWidth = 10f
        p.color = 0xFF4FC3F7.toInt()
        c.drawRect(0f, 0f, AW, AH, p)
    }

    // ---------- HUD ----------
    private fun bar(c: Canvas, x: Float, y: Float, w: Float, h: Float, frac: Float, col: Int) {
        p.style = Paint.Style.FILL
        p.color = 0xAA000000.toInt()
        c.drawRoundRect(x - 3f, y - 3f, x + w + 3f, y + h + 3f, h, h, p)
        p.color = col
        c.drawRoundRect(x, y, x + w * frac.coerceIn(0f, 1f), y + h, h / 2f, h / 2f, p)
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        // vida do Zeus
        tp.textAlign = Paint.Align.LEFT
        tp.textSize = 16f * u
        tp.color = Color.WHITE
        c.drawText("ZEUS", 20f * u, 28f * u, tp)
        bar(c, 20f * u, 36f * u, 260f * u, 14f * u, player.hp / player.maxHp, 0xFF4FC3F7.toInt())
        // vida do inimigo
        tp.textAlign = Paint.Align.RIGHT
        c.drawText(enemy.name.uppercase(), w - 20f * u, 28f * u, tp)
        bar(c, w - 280f * u, 36f * u, 260f * u, 14f * u, enemy.hp / enemy.maxHp, 0xFFEF5350.toInt())
        tp.textAlign = Paint.Align.CENTER

        // joystick
        if (joyId != -1) {
            p.style = Paint.Style.FILL
            p.color = 0x33FFFFFF
            c.drawCircle(joyBX, joyBY, 70f * u, p)
            p.color = 0x88FFFFFF.toInt()
            c.drawCircle(joyBX + joyX * 50f * u, joyBY + joyY * 50f * u, 30f * u, p)
        }

        // botões
        for (b in btns) drawButton(c, b)

        // dica
        if (hintT > 0f) {
            tp.textSize = 15f * u
            tp.color = ((min(1f, hintT) * 220).toInt() shl 24) or 0xFFFFFF
            c.drawText("Arraste à esquerda para andar  •  Botões à direita para atacar", w / 2f, h - 24f * u, tp)
        }
    }

    private fun drawButton(c: Canvas, b: Btn) {
        val cdv = player.cd[b.idx]
        val cdMax = CD_MAX[b.idx]
        val ready = cdv <= 0f
        val pressed = (b.idx == 0 && atkId != -1)
        p.style = Paint.Style.FILL
        p.color = if (b.idx == 3 && ready) {
            val a = (110 + 60 * sin(timeAll * 6f)).toInt()
            (a shl 24) or 0xFFB300
        } else if (pressed) 0xAA4FC3F7.toInt() else 0x88101830.toInt()
        c.drawCircle(b.cx, b.cy, b.r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * u
        p.color = if (ready) 0xFF9BEFFF.toInt() else 0xFF55607A.toInt()
        c.drawCircle(b.cx, b.cy, b.r, p)

        // ícone
        p.style = Paint.Style.FILL
        p.color = if (ready) 0xFFFFFFFF.toInt() else 0xFF8892A8.toInt()
        val s = b.r * 0.55f
        when (b.idx) {
            0 -> {
                c.drawCircle(b.cx, b.cy - b.r * 0.1f, s * 0.55f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f * u
                c.drawCircle(b.cx, b.cy - b.r * 0.1f, s * 0.9f, p)
            }
            1 -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 5f * u
                for (k in 0..1) {
                    val ox = b.cx - s * 0.5f + k * s * 0.7f
                    path.reset()
                    path.moveTo(ox, b.cy - b.r * 0.1f - s * 0.5f)
                    path.lineTo(ox + s * 0.5f, b.cy - b.r * 0.1f)
                    path.lineTo(ox, b.cy - b.r * 0.1f + s * 0.5f)
                    c.drawPath(path, p)
                }
            }
            else -> {
                val cy = b.cy - b.r * 0.1f
                path.reset()
                path.moveTo(b.cx + s * 0.25f, cy - s)
                path.lineTo(b.cx - s * 0.45f, cy + s * 0.1f)
                path.lineTo(b.cx - s * 0.02f, cy + s * 0.1f)
                path.lineTo(b.cx - s * 0.25f, cy + s)
                path.lineTo(b.cx + s * 0.5f, cy - s * 0.2f)
                path.lineTo(b.cx + s * 0.05f, cy - s * 0.2f)
                path.close()
                c.drawPath(path, p)
            }
        }
        tp.textSize = 11f * u
        tp.color = if (ready) 0xFFBDF3FF.toInt() else 0xFF8892A8.toInt()
        c.drawText(b.label, b.cx, b.cy + b.r * 0.78f, tp)

        // recarga
        if (!ready) {
            val frac = (cdv / cdMax).coerceIn(0f, 1f)
            p.style = Paint.Style.FILL
            p.color = 0xAA000000.toInt()
            dst.set(b.cx - b.r, b.cy - b.r, b.cx + b.r, b.cy + b.r)
            c.drawArc(dst, -90f, 360f * frac, true, p)
            if (cdv > 0.95f) {
                tp.textSize = 20f * u
                tp.color = Color.WHITE
                c.drawText(ceil(cdv).toInt().toString(), b.cx, b.cy + 7f * u, tp)
            }
        }
    }

    // ---------- toque ----------
    private fun pressAt(id: Int, x: Float, y: Float) {
        for (b in btns) {
            if (hypot(x - b.cx, y - b.cy) <= b.r * 1.15f) {
                when (b.idx) {
                    0 -> { atkId = id }
                    1 -> startDash(player)
                    2 -> startCast(player, 2)
                    3 -> startCast(player, 3)
                }
                return
            }
        }
        if (x < width * 0.5f && joyId == -1) {
            joyId = id
            joyBX = x
            joyBY = y
            joyX = 0f
            joyY = 0f
        }
    }

    private fun updateJoy(x: Float, y: Float) {
        var dx = (x - joyBX) / (60f * u)
        var dy = (y - joyBY) / (60f * u)
        val l = hypot(dx, dy)
        if (l > 1f) { dx /= l; dy /= l }
        joyX = dx
        joyY = dy
    }

    private fun release(id: Int) {
        if (id == joyId) { joyId = -1; joyX = 0f; joyY = 0f }
        if (id == atkId) atkId = -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        synchronized(lock) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = e.actionIndex
                    pressAt(e.getPointerId(i), e.getX(i), e.getY(i))
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until e.pointerCount) {
                        if (e.getPointerId(i) == joyId) updateJoy(e.getX(i), e.getY(i))
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    joyId = -1; joyX = 0f; joyY = 0f; atkId = -1
                }
            }
        }
        return true
    }
}
