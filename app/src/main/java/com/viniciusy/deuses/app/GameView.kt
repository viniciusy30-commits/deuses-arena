package com.viniciusy.deuses.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LightingColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
        val CD_MAX = floatArrayOf(0.5f, 1f, 1f, 1f)       // 0 ataque, 1 passo, 2 raio, 3 ultimate
        val CAST_DUR = floatArrayOf(0.45f, 0f, 0.7f, 1.5f)
        const val ULT_R = 300f
        const val G = 2f                  // grade do pixel art dos raios
        const val DASH_T = 0.75f           // tempo total da Queda Relâmpago (sobe + desce)
        const val DASH_DIST = 560f        // alcance máximo da Queda (mirando)
        const val DASH_DEF = 420f         // alcance sem mirar
        const val DASH_MIN = 160f
        const val THOR_RANGE = 720f       // alcance máximo do Raio Fulminante (mirando)
        const val RISE_R = 150f           // área de dano na subida
        const val LAND_R = 250f           // área de dano na descida
        const val THOR_R = 148f           // área do Raio Fulminante (El Thor)
        const val THOR_DELAY = 0.8f
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
        var manual = false
        var lift = 0f
        var mAx = 0f
        var mAy = 1f
        var mDist = 0f
        var mTx = 0f
        var mTy = 0f
        var dashEx = 0f
        var dashEy = 0f
        var dashSx = 0f
        var dashSy = 0f
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
        var interval = 0.07f
        var grow = 0f
        val subIdx = ArrayList<Int>()
    }
    private class Scorch(val x: Float, val y: Float, val r: Float, var life: Float, val maxLife: Float) {
        val v = Random.nextInt(4)
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
    private class Storm(val owner: Fighter, var x: Float, var y: Float, var t: Float, val dur: Float, var acc: Float)
    private class Anim(val sheet: Array<Bitmap>, val x: Float, val y: Float, val size: Float, val squash: Float,
                       val dur: Float, val add: Boolean, val flt: ColorFilter?, val rot: Float, val ground: Boolean) {
        var t = 0f
    }
    private class Pillar(val x: Float, val y: Float, val h: Float, val w: Float, var life: Float, val maxLife: Float,
                         val big: Boolean)
    private class Thor(val owner: Fighter, val x: Float, val y: Float, var delay: Float, val maxDelay: Float)
    private class Chunk(var x: Float, var y: Float, var vx: Float, var vy: Float, val gy: Float, var life: Float,
                        val maxLife: Float, val size: Float, val col: Int)
    private class Puff(var x: Float, var y: Float, val vx: Float, val vy: Float, var r: Float, var life: Float, val maxLife: Float)
    private class Crater(val owner: Fighter, val x: Float, val y: Float, val r: Float, var life: Float, val maxLife: Float,
                         val electric: Boolean, val dmg: Boolean) {
        val cracks = ArrayList<FloatArray>()
        val rocks = ArrayList<FloatArray>()
        var tick = 0f
        var dmgT = 0.3f
    }
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
    private val craters = ArrayList<Crater>()
    private val chunks = ArrayList<Chunk>()
    private val puffs = ArrayList<Puff>()
    private val strikes = ArrayList<Strike>()
    private val storms = ArrayList<Storm>()
    private val anims = ArrayList<Anim>()
    private val pillars = ArrayList<Pillar>()
    private val thors = ArrayList<Thor>()

    private var camX = 0f
    private var camY = 0f
    private var shake = 0f
    private var screenFlash = 0f
    private var hintT = 8f
    private var timeAll = 0f
    private var hitStop = 0f
    private var camInit = false
    private var darken = 0f

    // mira das skills (arrastar o botão)
    private var aimBtn = -1
    private var aimId = -1
    private var aimVX = 0f
    private var aimVY = 0f

    // entrada
    private var joyId = -1
    private var joyBX = 0f
    private var joyBY = 0f
    private var joyX = 0f
    private var joyY = 0f
    private var atkId = -1
    private val btns = arrayOf(Btn(0, "ATQ"), Btn(1, "QUEDA"), Btn(2, "RAIO"), Btn(3, "FÚRIA"))
    private var u = 1f
    private var vs = 1f

    // sprites
    private fun load(p: String): Bitmap = context.assets.open(p).use { BitmapFactory.decodeStream(it) }
    private val idle = Array(4) { d -> Array(9) { i -> load("zeus/idle/${DIRS[d]}/frame_%03d.png".format(i)) } }
    private val walk = Array(4) { d -> Array(8) { i -> load("zeus/walk/${DIRS[d]}/frame_%03d.png".format(i)) } }
    private val cast = Array(4) { d -> Array(9) { i -> load("zeus/cast/${DIRS[d]}/frame_%03d.png".format(i)) } }
    private val ult = Array(4) { d -> Array(9) { i -> load("zeus/ult/${DIRS[d]}/frame_%03d.png".format(i)) } }

    // efeitos (pixel art com textura)
    private fun sheet(name: String, n: Int): Array<Bitmap> = Array(n) { i -> load("fx/${name}_%02d.png".format(i)) }
    private val fxImpact = sheet("spark", 10)
    private val fxBlast = sheet("blast", 12)
    private val fxOrb = sheet("orb", 8)
    private val fxAura = sheet("aura", 8)
    private val fxPillar = sheet("pillar", 4)
    private val fxThor = sheet("thor", 4)
    private val fxCloud = sheet("cloud", 4)
    private val fxSmoke = sheet("smoke", 8)
    private val fxScD = sheet("scorchd", 4)
    private val fxScG = sheet("scorchg", 4)
    private val fxRuneUlt = sheet("rune_ult", 8)
    private val fxRuneS = sheet("rune_s", 6)
    private val fxGlow = load("fx/glow.png")

    private val fxRedF: ColorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        0f, 0f, 1f, 0f, 0f,
        0f, 0.45f, 0f, 0f, 0f,
        0.25f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)))
    private val fxGoldF: ColorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
        0f, 0f, 1f, 0f, 0f,
        0.1f, 0.25f, 0.6f, 0f, 0f,
        0.45f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f)))
    private val cloudF: ColorFilter = LightingColorFilter(0xFF8E96C4.toInt(), 0)
    private val palElec = intArrayOf(0xFF2E6BFF.toInt(), 0xFF9BE8FF.toInt(), 0xFFFFFFFF.toInt())
    private val palRed = intArrayOf(0xFFC62828.toInt(), 0xFFFF8A80.toInt(), 0xFFFFFFFF.toInt())
    private val palGold = intArrayOf(0xFFB36B00.toInt(), 0xFFFFD54F.toInt(), 0xFFFFFFFF.toInt())
    private val addMode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val addPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; xfermode = addMode }
    private val normPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

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
        return pts
    }

    private fun regen(b: BoltFx) {
        val len = hypot(b.x2 - b.x1, b.y2 - b.y1)
        val iters = if (len < 120f) 3 else if (len < 400f) 4 else 5
        b.main = genPoly(b.x1, b.y1, b.x2, b.y2, b.jitter, iters)
        b.subs.clear()
        b.subIdx.clear()
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
                b.subIdx.add(idx)
            }
        }
    }

    private fun makeBolt(x1: Float, y1: Float, x2: Float, y2: Float, life: Float, width: Float,
                         col: Int, outer: Int, jitter: Float, branches: Int, grow: Float = 0f) {
        if (bolts.size > 170) return
        val len = hypot(x2 - x1, y2 - y1)
        val j = if (len > 300f) max(jitter, len * 0.055f) else jitter
        val b = BoltFx(x1, y1, x2, y2, j, life, life, max(2f, width * 0.5f), col, outer, if (branches > 0) min(8, branches + 2) else 0)
        b.interval = if (width >= 8f) 0.11f else 0.07f
        b.flick = b.interval
        b.grow = grow
        regen(b)
        bolts.add(b)
    }

    private fun arc(x: Float, y: Float, len: Float, col: Int = C_CYAN, outer: Int = C_BLUE) {
        val a = rnd(0f, 6.2832f)
        makeBolt(x, y, x + cos(a) * len, y + sin(a) * len, 0.1f, 2f, col, outer, len * 0.25f, 0)
    }

    private fun addCrater(owner: Fighter, x: Float, y: Float, r: Float, life: Float, electric: Boolean, dmg: Boolean = electric) {
        if (craters.size > 20) {
            val i = craters.indexOfFirst { it.r < 100f }
            craters.removeAt(if (i >= 0) i else 0)
        }
        val cr = Crater(owner, x, y, r, life, life, electric, dmg)
        val n = 6 + (r / 22f).toInt()
        for (i in 0 until n) {
            val a = i * 6.2832f / n + rnd(-0.25f, 0.25f)
            val l = r * rnd(0.6f, 1.05f)
            val pts = genPoly(x, y, x + cos(a) * l, y + sin(a) * l * 0.6f, l * 0.14f, 3)
            cr.cracks.add(pts)
            if (r > 90f) {
                val k = Random.nextInt(2, pts.size / 2 - 2)
                val bx = pts[k * 2]
                val by = pts[k * 2 + 1]
                val ba = a + (if (Random.nextBoolean()) 1f else -1f) * rnd(0.5f, 1.0f)
                val bl = l * rnd(0.25f, 0.45f)
                cr.cracks.add(genPoly(bx, by, bx + cos(ba) * bl, by + sin(ba) * bl * 0.6f, bl * 0.18f, 2))
            }
        }
        val nr = 4 + (r / 40f).toInt()
        for (i in 0 until nr) {
            val a = rnd(0f, 6.2832f)
            val d = r * rnd(0.55f, 1.0f)
            cr.rocks.add(floatArrayOf(x + cos(a) * d, y + sin(a) * d * 0.6f, rnd(3f, 7f)))
        }
        craters.add(cr)
    }

    // pedaços de terra que voam e caem de verdade
    private fun debris(x: Float, y: Float, n: Int) {
        if (chunks.size > 160) return
        for (i in 0 until n) {
            val a = rnd(0f, 6.2832f)
            val sp = rnd(120f, 420f)
            chunks.add(Chunk(x + cos(a) * rnd(8f, 50f), y + sin(a) * rnd(4f, 25f), cos(a) * sp, -rnd(260f, 620f),
                y + rnd(-10f, 30f) + sin(a) * 20f, rnd(2.2f, 3.8f), 3.8f, rnd(3f, 7f),
                if (i % 3 == 0) 0xFF6B5E50.toInt() else 0xFF3A3340.toInt()))
        }
    }

    // nuvem de poeira subindo do impacto
    private fun dust(x: Float, y: Float, n: Int, sc: Float) {
        if (puffs.size > 60) return
        for (i in 0 until n) {
            val a = rnd(0f, 6.2832f)
            val sp = rnd(30f, 110f) * sc
            puffs.add(Puff(x + cos(a) * 20f * sc, y + sin(a) * 10f * sc, cos(a) * sp, sin(a) * sp * 0.4f - rnd(10f, 45f),
                rnd(22f, 40f) * sc, rnd(0.9f, 1.5f), 1.5f))
        }
    }

    // pouso suave do Zeus depois da ultimate
    private fun landSoft(f: Fighter) {
        dust(f.x, f.y, 8, 1.0f)
        burst(f.x, f.y - 10f, 10, C_CYAN, 220f, 0.5f)
        for (i in 0 until 4) arc(f.x + rnd(-40f, 40f), f.y + rnd(-10f, 10f), rnd(20f, 45f))
        shake = max(shake, 0.1f)
    }

    private fun addScorch(x: Float, y: Float, r: Float, life: Float) {
        if (scorches.size > 60) scorches.removeAt(0)
        scorches.add(Scorch(x, y, r, life, life))
    }

    private fun tintOf(col: Int, outer: Int): ColorFilter? = when {
        col == C_RED || outer == C_REDD -> fxRedF
        col == C_GOLD || outer == C_GOLD -> fxGoldF
        else -> null
    }

    private fun drawFx(c: Canvas, b: Bitmap, x: Float, y: Float, w: Float, h: Float, a: Float,
                       add: Boolean, flt: ColorFilter?, rot: Float) {
        val al = (a.coerceIn(0f, 1f) * 255f).toInt()
        if (al <= 2) return
        val pt = if (add) addPaint else normPaint
        pt.alpha = al
        pt.colorFilter = flt
        if (rot != 0f) { c.save(); c.rotate(rot, x, y) }
        dst.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
        c.drawBitmap(b, null, dst, pt)
        if (rot != 0f) c.restore()
        pt.colorFilter = null
        pt.alpha = 255
    }

    private fun addAnim(sheet: Array<Bitmap>, x: Float, y: Float, size: Float, squash: Float, dur: Float,
                        add: Boolean, flt: ColorFilter?, rot: Float, ground: Boolean) {
        if (anims.size > 90) return
        anims.add(Anim(sheet, x, y, size, squash, dur, add, flt, rot, ground))
    }

    private fun addPillar(x: Float, y: Float, h: Float, w: Float, life: Float, big: Boolean = false) {
        if (pillars.size > 24) return
        pillars.add(Pillar(x, y, h, w, life, life, big))
    }

    // raios que saem do ponto (x, y) para os lados, no chão
    private fun radialBolts(x: Float, y: Float, n: Int, minL: Float, maxL: Float, w: Float, life: Float) {
        for (i in 0 until n) {
            val a = i * 6.2832f / n + rnd(-0.25f, 0.25f)
            val l = rnd(minL, maxL)
            makeBolt(x, y, x + cos(a) * l, y + sin(a) * l * 0.62f, life, w, C_CYAN, C_BLUE, l * 0.16f, 1)
        }
    }

    private fun groundBlast(x: Float, y: Float, sc: Float, col: Int, outer: Int) {
        val n = (6 + 3 * sc).toInt()
        for (i in 0 until n) {
            val a = i * 6.2832f / n + rnd(-0.25f, 0.25f)
            val l = rnd(50f, 120f) * sc
            makeBolt(x, y, x + cos(a) * l, y + sin(a) * l * 0.6f, 0.3f, 2f, col, outer, l * 0.2f, 1)
        }
    }

    private fun impact(x: Float, y: Float, sc: Float, col: Int, outer: Int) {
        val n = (5 + 3 * sc).toInt()
        for (i in 0 until n) {
            val a = i * 6.2832f / n + rnd(-0.3f, 0.3f)
            val l = rnd(35f, 80f) * sc
            makeBolt(x, y, x + cos(a) * l, y + sin(a) * l * 0.85f, 0.2f, 2f, if (i % 3 == 0) C_WHITE else col, outer, l * 0.22f, 1)
        }
        burst(x, y, (6 * sc).toInt(), col, 300f * sc, 0.4f)
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
    // mão que aponta para frente (relativa aos pés), por direção do sprite: sul, leste, norte, oeste
    private val HAND_DX = floatArrayOf(-32f, 56f, -42f, -46f)
    private val HAND_DY = floatArrayOf(-58f, -88f, -106f, -74f)
    private fun handX(f: Fighter): Float = f.x + HAND_DX[dirOf(f.faceX, f.faceY)]
    private fun handY(f: Fighter): Float = f.y + HAND_DY[dirOf(f.faceX, f.faceY)]

    private fun aim(f: Fighter) {
        if (f.manual) {
            f.aimX = f.mAx; f.aimY = f.mAy; f.faceX = f.mAx; f.faceY = f.mAy
            return
        }
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
        var dist = DASH_DEF
        if (f.manual) {
            dx = f.mAx; dy = f.mAy; l = 1f; dist = f.mDist
        } else {
            if (l < 0.2f) { dx = f.faceX; dy = f.faceY; l = hypot(dx, dy) }
            if (l < 0.01f) { dx = 0f; dy = 1f; l = 1f }
        }
        f.dashDx = dx / l
        f.dashDy = dy / l
        f.faceX = f.dashDx
        f.faceY = f.dashDy
        f.dashSx = f.x
        f.dashSy = f.y
        f.dashEx = (f.x + f.dashDx * dist).coerceIn(70f, AW - 70f)
        f.dashEy = (f.y + f.dashDy * dist).coerceIn(150f, AH - 50f)
        f.dashT = DASH_T
        f.dashHit = false
        f.cd[1] = CD_MAX[1]
        riseFx(f)
    }

    // SOBE como um raio: o Zeus vira uma coluna elétrica rumo ao céu e destroi o chão ao redor
    private fun riseFx(f: Fighter) {
        val x = f.x
        val y = f.y
        makeBolt(x, y, x + rnd(-14f, 14f), y - 900f, 0.55f, 14f, C_CYAN, C_BLUE, 34f, 4, 0.2f)
        makeBolt(x, y, x + rnd(-8f, 8f), y - 900f, 0.45f, 5f, C_WHITE, C_CYAN, 20f, 2, 0.18f)
        addPillar(x, y, 900f, 90f, 0.4f)
        impact(x, y - BODY, 1.6f, C_CYAN, C_BLUE)
        groundBlast(x, y, 1.5f, C_CYAN, C_BLUE)
        radialBolts(x, y, 12, 80f, RISE_R * 1.1f, 4f, 0.3f)
        addCrater(f, x, y, RISE_R, 8f, false)
        dust(x, y, 6, 1.0f)
        addScorch(x, y, 60f, 5f)
        for (i in 0 until 5) {
            val a = i * 1.2566f + rnd(-0.3f, 0.3f)
            addScorch(x + cos(a) * RISE_R * 0.7f, y + sin(a) * RISE_R * 0.45f, 24f, 4f)
        }
        debris(x, y, 20)
        burst(x, y - BODY, 18, C_CYAN, 420f, 0.6f)
        val o = opp(f)
        if (!o.dead && hypot(o.x - x, o.y - y) < RISE_R + 20f) {
            damage(o, 35f, 0.5f)
            o.stun = max(o.stun, 0.35f)
            impact(o.x, o.y - BODY, 1.3f, C_WHITE, C_CYAN)
        }
        screenFlash = max(screenFlash, 0.1f)
        shake = max(shake, 0.25f)
        hitStop = max(hitStop, 0.04f)
    }

    // DESCE como outro raio, mais forte: destroi uma área maior e deixa o chão eletrificado
    private fun landFromSky(f: Fighter) {
        f.x = f.dashEx
        f.y = f.dashEy
        val x = f.x
        val y = f.y
        val skyX = x + rnd(-30f, 30f)
        addPillar(x, y, 900f, 230f, 0.5f, true)
        impact(x, y - 10f, 2.2f, C_CYAN, C_BLUE)
        groundBlast(x, y, 2.6f, C_CYAN, C_BLUE)
        radialBolts(x, y, 18, 110f, LAND_R * 1.25f, 4f, 0.35f)
        addCrater(f, x, y, LAND_R, 10f, true)
        dust(x, y, 10, 1.6f)
        addScorch(x, y, 110f, 8f)
        for (i in 0 until 8) {
            val a = i * 0.7854f + rnd(-0.2f, 0.2f)
            addScorch(x + cos(a) * LAND_R * 0.75f, y + sin(a) * LAND_R * 0.47f, 32f, 6f)
        }
        debris(x, y, 36)
        burst(x, y - BODY, 30, C_CYAN, 560f, 0.8f)
        screenFlash = max(screenFlash, 0.25f)
        shake = max(shake, 0.5f)
        hitStop = max(hitStop, 0.09f)
        val o = opp(f)
        if (!o.dead && hypot(o.x - x, o.y - y) < LAND_R) {
            damage(o, 130f, 0.9f)
            o.stun = max(o.stun, 0.8f)
            impact(o.x, o.y - BODY, 1.9f, C_WHITE, C_CYAN)
            hitStop = max(hitStop, 0.1f)
        }
        clampPos(f)
    }

    // alvo do Raio Fulminante: o inimigo (se estiver perto) ou um ponto à frente
    private fun thorAtOpp(f: Fighter): Boolean {
        if (f.manual) return false
        val o = opp(f)
        return !o.dead && hypot(o.x - f.x, o.y - f.y) < 760f
    }

    private fun thorTx(f: Fighter): Float =
        if (f.manual) f.mTx else if (thorAtOpp(f)) opp(f).x else (f.x + f.aimX * 380f).coerceIn(80f, AW - 80f)

    private fun thorTy(f: Fighter): Float =
        if (f.manual) f.mTy else if (thorAtOpp(f)) opp(f).y else (f.y + f.aimY * 380f).coerceIn(160f, AH - 60f)

    // o raio gigante despenca da nuvem (estilo El Thor)
    private fun thorStrike(k: Thor) {
        val x = k.x
        val y = k.y
        val skyX = x + rnd(-20f, 20f)
        makeBolt(skyX, y - 950f, x, y, 0.7f, 30f, C_CYAN, C_BLUE, 70f, 6, 0.14f)
        makeBolt(skyX, y - 950f, x, y, 0.6f, 14f, C_WHITE, C_CYAN, 46f, 4, 0.12f)
        makeBolt(skyX, y - 950f, x, y, 0.5f, 6f, C_WHITE, C_CYAN, 30f, 3, 0.1f)
        addPillar(x, y, 950f, 270f, 0.65f, true)
        impact(x, y - 10f, 2.5f, C_CYAN, C_BLUE)
        groundBlast(x, y, 2.8f, C_CYAN, C_BLUE)
        rings.add(Ring(x, y, THOR_R * 1.3f, 0.55f, 0.55f, C_WHITE))
        rings.add(Ring(x, y, THOR_R * 0.8f, 0.4f, 0.4f, C_CYAN))
        rings.add(Ring(x, y, THOR_R * 1.7f, 0.7f, 0.7f, C_BLUE))
        radialBolts(x, y, 14, 110f, THOR_R * 1.5f, 5f, 0.3f)
        addScorch(x, y, 120f, 9f)
        addCrater(k.owner, x, y, THOR_R, 10f, true)
        dust(x, y, 12, 1.8f)
        debris(x, y, 36)
        for (i in 0 until 6) {
            val a = i * 1.0472f + rnd(-0.2f, 0.2f)
            addScorch(x + cos(a) * THOR_R, y + sin(a) * THOR_R * 0.6f, 30f, 6f)
        }
        burst(x, y - BODY, 48, C_CYAN, 660f, 1.0f)
        burst(x, y - BODY, 20, C_WHITE, 500f, 0.6f)
        screenFlash = max(screenFlash, 0.45f)
        shake = max(shake, 0.7f)
        hitStop = max(hitStop, 0.09f)
        val o = opp(k.owner)
        if (!o.dead && hypot(o.x - x, o.y - y) < THOR_R) {
            damage(o, 170f, 1.0f)
            o.stun = max(o.stun, 0.8f)
            impact(o.x, o.y - BODY, 2.0f, C_WHITE, C_CYAN)
            hitStop = max(hitStop, 0.1f)
        }
    }

    private fun fire(f: Fighter, kind: Int) {
        val ox = handX(f)
        val oy = handY(f)
        val col = if (f.isPlayer) C_CYAN else C_RED
        val outer = if (f.isPlayer) C_BLUE else C_REDD
        when (kind) {
            0 -> {
                if (f.isPlayer) {
                    // descarga elétrica: raio em zigue-zague com ramificações
                    val o = opp(f)
                    val dist = hypot(o.x - f.x, o.y - BODY - oy)
                    val len = 520f
                    var ex = ox + f.aimX * len
                    var ey = oy + f.aimY * len
                    var hit = false
                    val lockOn = if (f.manual) (!o.dead && segDist(o.x, o.y - BODY, ox, oy, ex, ey) < 60f)
                                 else (!o.dead && hypot(o.x - ox, o.y - BODY - oy) <= len + 20f && dist < 680f)
                    if (lockOn) {
                        ex = o.x
                        ey = o.y - BODY
                        hit = true
                    }
                    makeBolt(ox, oy, ex, ey, 0.26f, 8f, C_CYAN, C_BLUE, 26f, 4)
                    makeBolt(ox, oy, ex, ey, 0.2f, 4f, C_WHITE, C_CYAN, 18f, 2)
                    makeBolt(ox, oy, ex, ey, 0.14f, 3f, C_WHITE, C_CYAN, 34f, 1)
                    for (i in 0 until 3) arc(ox, oy, rnd(18f, 34f), col, outer)
                    burst(ox, oy, 8, col, 220f, 0.3f)
                    impact(ex, ey, 1.0f, col, outer)
                    for (i in 0 until 4) arc(ex, ey, rnd(30f, 60f), col, outer)
                    if (hit) {
                        damage(o, 45f, 0.3f)
                        addScorch(o.x, o.y, 24f, 2.5f)
                        hitStop = max(hitStop, 0.03f)
                        shake = max(shake, 0.08f)
                    } else {
                        addScorch(ex, ey + BODY, 20f, 2f)
                    }
                } else {
                    projs.add(Proj(f, ox, oy, f.aimX * 420f, f.aimY * 420f, 35f, 0.75f, true))
                    burst(ox, oy, 8, col, 220f, 0.3f)
                    rings.add(Ring(ox, oy, 40f, 0.18f, 0.18f, col))
                    for (i in 0 until 3) arc(ox, oy, rnd(25f, 45f), col, outer)
                }
            }
            2 -> {
                // Raio Fulminante: invoca a nuvem e o raio despenca do céu no alvo
                val tx = thorTx(f)
                val ty = thorTy(f)
                thors.add(Thor(f, tx, ty, THOR_DELAY, THOR_DELAY))
                makeBolt(ox, oy, tx, ty - 430f, 0.3f, 6f, C_CYAN, C_BLUE, 24f, 2)
                for (i in 0 until 4) arc(ox, oy, rnd(24f, 46f), C_CYAN, C_BLUE)
                rings.add(Ring(ox, oy, 60f, 0.25f, 0.25f, C_WHITE))
                burst(ox, oy, 12, C_CYAN, 260f, 0.4f)
                screenFlash = max(screenFlash, 0.06f)
            }
            3 -> {
                // Fúria do Olimpo: o Zeus voa e raios caem aleatoriamente ao redor dele
                storms.add(Storm(f, f.x, f.y, 0f, 3.6f, 0f))
                for (i in 0 until 8) {
                    val a = i * 0.7854f + rnd(-0.2f, 0.2f)
                    val l = rnd(140f, 230f)
                    makeBolt(f.x, f.y - BODY - f.lift, f.x + cos(a) * l, f.y - BODY - f.lift + sin(a) * l * 0.7f, 0.3f, 6f, C_WHITE, C_CYAN, 24f, 2)
                }
                for (i in 0 until 6) {
                    val a = rnd(0f, 6.2832f)
                    val r = rnd(220f, 480f)
                    val d = 0.3f + i * 0.08f
                    strikes.add(Strike(f, (f.x + cos(a) * r).coerceIn(80f, AW - 80f), (f.y + sin(a) * r * 0.75f).coerceIn(160f, AH - 60f), d, d))
                }
                burst(f.x, f.y - BODY - f.lift, 30, C_CYAN, 480f, 0.9f)
                screenFlash = 0.3f
                shake = 0.5f
                hitStop = max(hitStop, 0.06f)
            }
        }
    }

    private fun chargeFx(f: Fighter) {
        val hx = handX(f)
        val hy = handY(f)
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
                    parts.add(Part(f.x + cos(a) * r, f.y - BODY - f.lift + sin(a) * r, -cos(a) * 260f, -sin(a) * 260f, 0.4f, 0.4f,
                        if (i == 0) C_WHITE else C_CYAN, 4f))
                }
                if (Random.nextFloat() < 0.6f) arc(f.x + rnd(-50f, 50f), f.y - BODY - f.lift + rnd(-60f, 60f), rnd(50f, 110f), C_WHITE, C_CYAN)
                if (Random.nextFloat() < 0.3f) {
                    val tx = f.x + rnd(-150f, 150f)
                    makeBolt(tx, f.y - 700f, tx + rnd(-60f, 60f), f.y - 380f, 0.25f, 4f, C_CYAN, C_BLUE, 30f, 1)
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
        if (atkId != -1 && player.castKind < 0) {
            if (aimBtn == 0) applyAimTo(player, 0) else player.manual = false
            startCast(player, 0)
        }
        if (aimBtn >= 0 && aimDragged() && player.castKind < 0 && player.dashT <= 0f && hypot(joyX, joyY) < 0.15f) {
            val l = hypot(aimVX, aimVY)
            player.faceX = aimVX / l
            player.faceY = aimVY / l
        }

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

        // tempestade (ultimate): o Zeus voa e raios caem aleatoriamente ao redor dele
        val si = storms.iterator()
        while (si.hasNext()) {
            val s = si.next()
            s.t += dt
            s.acc += dt
            if (!s.owner.dead) { s.x = s.owner.x; s.y = s.owner.y }
            while (s.acc >= 0.12f) {
                s.acc -= 0.12f
                val o = opp(s.owner)
                val sx: Float
                val sy: Float
                if (!o.dead && hypot(o.x - s.x, o.y - s.y) < 640f && Random.nextFloat() < 0.35f) {
                    sx = o.x + rnd(-70f, 70f)
                    sy = o.y + rnd(-50f, 50f)
                } else {
                    val a = rnd(0f, 6.2832f)
                    val r = rnd(170f, 560f)
                    sx = (s.x + cos(a) * r).coerceIn(80f, AW - 80f)
                    sy = (s.y + sin(a) * r * 0.75f).coerceIn(160f, AH - 60f)
                }
                strikes.add(Strike(s.owner, sx, sy, 0.45f, 0.45f))
            }
            if (Random.nextFloat() < 0.5f) {
                arc(s.x + rnd(-45f, 45f), s.y - BODY - s.owner.lift + rnd(-55f, 55f), rnd(40f, 100f), C_WHITE, C_CYAN)
            }
            if (s.t >= s.dur || s.owner.dead) si.remove()
        }
        val ki = strikes.iterator()
        while (ki.hasNext()) {
            val k = ki.next()
            k.delay -= dt
            if (k.delay > 0f) {
                if (Random.nextFloat() < dt * 16f) arc(k.x + rnd(-28f, 28f), k.y + rnd(-14f, 14f), rnd(16f, 38f))
            } else {
                val skyX = k.x + rnd(-40f, 40f)
                makeBolt(skyX, k.y - 800f, k.x, k.y, 0.32f, 12f, C_CYAN, C_BLUE, 38f, 4, 0.1f)
                makeBolt(skyX, k.y - 800f, k.x, k.y, 0.24f, 4f, C_WHITE, C_CYAN, 22f, 2, 0.08f)
                if (Random.nextFloat() < 0.4f) {
                    makeBolt(k.owner.x, k.owner.y - BODY - k.owner.lift, k.x, k.y, 0.14f, 3f, C_WHITE, C_CYAN, 30f, 1)
                }
                impact(k.x, k.y, 1.2f, C_CYAN, C_BLUE)
                addPillar(k.x, k.y, 800f, 120f, 0.34f)
                groundBlast(k.x, k.y, 0.9f, C_CYAN, C_BLUE)
                addScorch(k.x, k.y, 36f, 5f)
                addCrater(k.owner, k.x, k.y, 62f, 3.5f, true, false)
                dust(k.x, k.y, 3, 0.6f)
                debris(k.x, k.y, 6)
                val o = opp(k.owner)
                if (!o.dead && hypot(o.x - k.x, o.y - BODY - k.y) < 85f) {
                    damage(o, 90f, 0.6f)
                    impact(o.x, o.y - BODY, 1.2f, C_WHITE, C_CYAN)
                    hitStop = max(hitStop, 0.03f)
                }
                shake = max(shake, 0.12f)
                ki.remove()
            }
        }

        // Raio Fulminante: nuvem se forma, runa no chão, e o raio gigante despenca
        val thi = thors.iterator()
        while (thi.hasNext()) {
            val k = thi.next()
            k.delay -= dt
            if (Random.nextFloat() < 0.35f) arc(k.x + rnd(-120f, 120f), k.y - 420f + rnd(-40f, 30f), rnd(40f, 90f))
            if (Random.nextFloat() < 0.3f) {
                val a = rnd(0f, 6.2832f)
                parts.add(Part(k.x + cos(a) * THOR_R, k.y + sin(a) * THOR_R * 0.9f, 0f, -120f, 0.5f, 0.5f, C_CYAN, 4f))
            }
            shake = max(shake, 0.03f)
            if (k.delay <= 0f) {
                thorStrike(k)
                thi.remove()
            }
        }

        // escurecer o chão durante o ultimate
        val want = if (storms.isNotEmpty()) 0.4f else if (thors.isNotEmpty()) 0.3f else if (player.castKind == 3 || enemy.castKind == 3) 0.22f else 0f
        darken += (want - darken) * min(1f, dt0 * 4f)

        // efeitos
        val bi = bolts.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b.life -= dt
            b.flick -= dt
            if (b.life <= 0f) { bi.remove(); continue }
            if (b.flick <= 0f) { b.flick = b.interval; regen(b) }
        }
        val ri = rings.iterator(); while (ri.hasNext()) { val r = ri.next(); r.life -= dt; if (r.life <= 0f) ri.remove() }
        val ani = anims.iterator(); while (ani.hasNext()) { val a = ani.next(); a.t += dt; if (a.t >= a.dur) ani.remove() }
        val pli = pillars.iterator(); while (pli.hasNext()) { val a = pli.next(); a.life -= dt; if (a.life <= 0f) pli.remove() }
        val gi = ghosts.iterator(); while (gi.hasNext()) { val g = gi.next(); g.life -= dt; if (g.life <= 0f) gi.remove() }
        val cri = craters.iterator()
        while (cri.hasNext()) {
            val cr = cri.next()
            cr.life -= dt0
            if (cr.life <= 0f) { cri.remove(); continue }
            if (cr.electric && cr.life > 0.6f) {
                cr.tick -= dt
                if (cr.tick <= 0f) {
                    cr.tick = 0.07f
                    val ck = cr.cracks[Random.nextInt(cr.cracks.size)]
                    val i = Random.nextInt(ck.size / 2)
                    arc(ck[i * 2], ck[i * 2 + 1], rnd(16f, 44f))
                }
                // faíscas subindo das rachaduras
                if (Random.nextFloat() < dt * 22f) {
                    val ck = cr.cracks[Random.nextInt(cr.cracks.size)]
                    val i = Random.nextInt(ck.size / 2)
                    parts.add(Part(ck[i * 2], ck[i * 2 + 1], rnd(-25f, 25f), -rnd(50f, 130f), 0.7f, 0.7f, C_CYAN, 4f))
                }
                // arcos pulando de uma rachadura para outra
                if (Random.nextFloat() < dt * 6f) {
                    val c1 = cr.cracks[Random.nextInt(cr.cracks.size)]
                    val c2 = cr.cracks[Random.nextInt(cr.cracks.size)]
                    val i1 = Random.nextInt(c1.size / 2)
                    val i2 = Random.nextInt(c2.size / 2)
                    if (hypot(c1[i1 * 2] - c2[i2 * 2], c1[i1 * 2 + 1] - c2[i2 * 2 + 1]) < cr.r * 0.8f) {
                        makeBolt(c1[i1 * 2], c1[i1 * 2 + 1], c2[i2 * 2], c2[i2 * 2 + 1], 0.14f, 3f, C_WHITE, C_CYAN, 10f, 0)
                    }
                }
                if (cr.dmg) {
                    cr.dmgT -= dt
                    if (cr.dmgT <= 0f) {
                        cr.dmgT = 0.5f
                        val o = opp(cr.owner)
                        if (!o.dead && o.dashT <= 0f && hypot(o.x - cr.x, (o.y - cr.y) / 0.6f) < cr.r * 0.9f) {
                            damage(o, 12f, 0.25f)
                            arc(o.x + rnd(-20f, 20f), o.y - BODY + rnd(-30f, 30f), rnd(30f, 60f))
                        }
                    }
                }
            }
        }
        val chi = chunks.iterator()
        while (chi.hasNext()) {
            val ch = chi.next()
            ch.life -= dt0
            if (ch.life <= 0f) { chi.remove(); continue }
            if (ch.vy != 0f || ch.y < ch.gy) {
                ch.vy += 1300f * dt
                ch.x += ch.vx * dt
                ch.y += ch.vy * dt
                if (ch.y >= ch.gy) {
                    ch.y = ch.gy
                    ch.vy = -ch.vy * 0.28f
                    ch.vx *= 0.45f
                    if (abs(ch.vy) < 40f) { ch.vy = 0f; ch.vx = 0f }
                }
            }
        }
        val pui = puffs.iterator()
        while (pui.hasNext()) {
            val pf = pui.next()
            pf.life -= dt0
            if (pf.life <= 0f) { pui.remove(); continue }
            pf.x += pf.vx * dt
            pf.y += pf.vy * dt
            pf.r += 26f * dt
        }
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

        // câmera (suave, para acompanhar o salto)
        val visW = width / vs
        val visH = height / vs
        var fx0 = player.x
        var fy0 = player.y
        if (player.dashT > 0f) {
            val pr = (1f - player.dashT / DASH_T).coerceIn(0f, 1f)
            fx0 = player.dashSx + (player.dashEx - player.dashSx) * pr
            fy0 = player.dashSy + (player.dashEy - player.dashSy) * pr
        }
        val tx = if (visW >= AW) (AW - visW) / 2f else (fx0 - visW / 2f).coerceIn(0f, AW - visW)
        val ty = if (visH >= AH) (AH - visH) / 2f else (fy0 - 30f - visH / 2f).coerceIn(0f, AH - visH)
        if (!camInit) { camX = tx; camY = ty; camInit = true }
        val k = min(1f, dt0 * 9f)
        camX += (tx - camX) * k
        camY += (ty - camY) * k
    }

    private fun updateFighter(f: Fighter, dt: Float) {
        for (i in 0 until 4) if (f.cd[i] > 0f) f.cd[i] -= dt
        if (f.flash > 0f) f.flash -= dt
        val flyTarget = if (!f.dead && (f.castKind == 3 || storms.any { it.owner === f })) 80f else 0f
        if (flyTarget > 0f) {
            f.lift += (flyTarget - f.lift) * min(1f, dt * 4f)
        } else if (f.lift > 0f) {
            f.lift = max(0f, f.lift - (14f + f.lift * 1.1f) * dt)
            if (f.lift <= 0f) landSoft(f)
        }
        if (f.lift > 20f && Random.nextFloat() < 0.5f) arc(f.x + rnd(-40f, 40f), f.y - BODY - f.lift + rnd(-45f, 45f), rnd(25f, 60f))

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

        // Queda Relâmpago: sobe como um raio, cruza o céu e desce como um raio mais forte
        if (f.dashT > 0f) {
            f.moving = false
            f.dashT -= dt
            if (f.dashT < 0.26f && !f.dashHit) {
                f.dashHit = true
                val skyX = f.dashEx + rnd(-30f, 30f)
                makeBolt(skyX, f.dashEy - 900f, f.dashEx, f.dashEy, 0.6f, 26f, C_CYAN, C_BLUE, 60f, 5, 0.24f)
                makeBolt(skyX, f.dashEy - 900f, f.dashEx, f.dashEy, 0.55f, 12f, C_WHITE, C_CYAN, 40f, 3, 0.22f)
                makeBolt(skyX, f.dashEy - 900f, f.dashEx, f.dashEy, 0.5f, 5f, C_WHITE, C_CYAN, 26f, 2, 0.2f)
            }
            if (Random.nextFloat() < 0.5f) arc(f.dashEx + rnd(-70f, 70f), f.dashEy + rnd(-30f, 10f), rnd(25f, 55f))
            if (f.dashT <= 0f) {
                f.dashT = 0f
                landFromSky(f)
            }
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
            if (f.castT >= f.castDur) { f.castKind = -1; f.manual = false }
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
            if (f.castKind == 3) ult[d][((timeAll * 10f).toInt()) % 9]
            else cast[d][min(8, (f.castT / f.castDur * 9f).toInt())]
        } else if (f.lift > 3f) {
            ult[d][((timeAll * 10f).toInt()) % 9]
        } else if (f.moving) {
            walk[d][((f.animT * 10f).toInt()) % 8]
        } else {
            idle[d][((f.animT * 8f).toInt()) % 9]
        }
    }

    private fun fxAlpha(a: Float, col: Int): Int = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (col and 0x00FFFFFF)

    private fun drawFighter(c: Canvas, f: Fighter) {
        if (f.dead || f.dashT > 0f) return
        val lift = if (f.lift > 2f) f.lift + sin(timeAll * 4f) * 4f else 0f
        // sombra (diminui quando ele voa)
        val shR = 36f * (1f - min(0.45f, f.lift / 200f))
        p.style = Paint.Style.FILL
        p.color = (0x55 * (1f - min(0.5f, f.lift / 160f))).toInt() shl 24
        c.drawOval(f.x - shR, f.y - shR * 0.25f, f.x + shR, f.y + shR * 0.25f, p)

        val shockOn = f.shock > 0f && ((timeAll * 16f).toInt() % 2 == 0)
        val flt: ColorFilter? = if (f.flash > 0f || shockOn) flashF else if (!f.isPlayer) redF else null
        val jx = if (f.shock > 0f) rnd(-2f, 2f) else 0f
        drawSprite(c, frameOf(f), f.x + jx, f.y - lift, flt, 255)

        if (f.stun > 0f) {
            tp.textSize = 26f
            tp.color = 0xFFFFF59D.toInt()
            c.drawText("⚡", f.x + sin(timeAll * 12f) * 18f, f.y - 135f - lift, tp)
        }
    }

    // ---------- efeitos suaves: raios, brilhos, nuvem e pilar de luz ----------
    private val boltPath = Path()
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rf = RectF()
    private val glowCache = HashMap<Int, Paint>()
    private val glowDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = addMode }
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xC0000000.toInt(), 0x80000000.toInt(), 0x00000000),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xF0182044.toInt(), 0xB00E1530.toInt(), 0x00080C22),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    private val colPaint = Paint().apply {
        shader = LinearGradient(-0.5f, 0f, 0.5f, 0f,
            intArrayOf(0x003A7BFF, 0x555FB8FF, 0xDDFFFFFF.toInt(), 0x555FB8FF, 0x003A7BFF),
            floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
        xfermode = addMode
    }
    private val chunkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val puffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x90707A94.toInt(), 0x50606880, 0x00606880),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x99BFEFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }

    private fun colsOf(col: Int, outer: Int): IntArray = when {
        col == C_RED || outer == C_REDD -> intArrayOf(0xFFD32F2F.toInt(), 0xFFFF7B6B.toInt(), 0xFFFFE6E1.toInt())
        col == C_GOLD || outer == C_GOLD -> intArrayOf(0xFFB36B00.toInt(), 0xFFFFD54F.toInt(), 0xFFFFF8E1.toInt())
        else -> intArrayOf(0xFF2E6BFF.toInt(), 0xFF8FE3FF.toInt(), 0xFFFFFFFF.toInt())
    }

    private fun glowPaint(col: Int): Paint = glowCache.getOrPut(col or 0xFF000000.toInt()) {
        val rgb = col and 0x00FFFFFF
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFF000000.toInt() or rgb, 0x66000000 or rgb, rgb),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            xfermode = addMode
        }
    }

    private fun drawGlowE(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, col: Int, a: Float) {
        if (a <= 0.01f) return
        val gp = glowPaint(col)
        gp.alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        c.save()
        c.translate(x, y)
        c.scale(rx, ry)
        c.drawCircle(0f, 0f, 1f, gp)
        c.restore()
    }

    private fun drawGlow(c: Canvas, x: Float, y: Float, r: Float, col: Int, a: Float) = drawGlowE(c, x, y, r, r, col, a)

    private fun drawDark(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, a: Float) {
        darkPaint.alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        c.save()
        c.translate(x, y)
        c.scale(rx, ry)
        c.drawCircle(0f, 0f, 1f, darkPaint)
        c.restore()
    }

    private fun polyPath(pts: FloatArray, reveal: Float) {
        boltPath.reset()
        val n = pts.size / 2
        boltPath.moveTo(pts[0], pts[1])
        if (reveal >= 0.999f) {
            for (i in 1 until n) boltPath.lineTo(pts[i * 2], pts[i * 2 + 1])
        } else {
            val f = reveal * (n - 1)
            val k = f.toInt()
            for (i in 1..k) boltPath.lineTo(pts[i * 2], pts[i * 2 + 1])
            if (k < n - 1) {
                val t = f - k
                boltPath.lineTo(pts[k * 2] + (pts[k * 2 + 2] - pts[k * 2]) * t, pts[k * 2 + 1] + (pts[k * 2 + 3] - pts[k * 2 + 1]) * t)
            }
        }
    }

    // raio suave: brilho largo e translúcido, corpo colorido e núcleo branco fino
    private fun strokePath(c: Canvas, w: Float, a: Float, pal: IntArray, big: Boolean) {
        bp.xfermode = addMode
        if (big) {
            bp.strokeWidth = w * 4.4f
            bp.color = fxAlpha(a * 0.07f, pal[0])
            c.drawPath(boltPath, bp)
            bp.strokeWidth = w * 2.3f
            bp.color = fxAlpha(a * 0.16f, pal[0])
            c.drawPath(boltPath, bp)
        }
        bp.strokeWidth = w * 1.1f + 1.4f
        bp.color = fxAlpha(a * 0.6f, pal[1])
        c.drawPath(boltPath, bp)
        bp.xfermode = null
        bp.strokeWidth = max(1.2f, w * 0.36f)
        bp.color = fxAlpha(min(1f, a * 1.4f), pal[2])
        c.drawPath(boltPath, bp)
    }

    private fun drawBoltFx(c: Canvas, b: BoltFx) {
        if (b.main.size < 4) return
        val a0 = (b.life / b.maxLife).coerceIn(0f, 1f)
        val reveal = if (b.grow > 0f) ((b.maxLife - b.life) / b.grow).coerceIn(0.02f, 1f) else 1f
        val a = min(1f, a0 * 2.2f) * (if (((timeAll * 45f).toInt() + b.x1.toInt()) % 4 == 0) 0.72f else 1f)
        val pal = colsOf(b.col, b.outer)
        val n = b.main.size / 2
        polyPath(b.main, reveal)
        strokePath(c, b.width, a, pal, b.width >= 4f)
        val shown = reveal * (n - 1)
        for ((i, sp) in b.subs.withIndex()) {
            if (sp.size < 4 || b.subIdx[i] > shown) continue
            polyPath(sp, 1f)
            strokePath(c, b.width * 0.5f, a * 0.9f, pal, false)
        }
    }

    // mira de chão: anel elíptico fino com arcos girando
    private fun drawReticle(c: Canvas, x: Float, y: Float, r: Float, a: Float) {
        if (a <= 0.02f) return
        val ry = r * 0.6f
        rf.set(x - r, y - ry, x + r, y + ry)
        bp.xfermode = addMode
        bp.strokeWidth = 2f
        bp.color = fxAlpha(a * 0.45f, 0xFF8FE3FF.toInt())
        c.drawOval(rf, bp)
        bp.strokeWidth = 4f
        bp.color = fxAlpha(a, 0xFFE8FBFF.toInt())
        for (k in 0 until 3) c.drawArc(rf, timeAll * 130f + k * 120f, 34f, false, bp)
        bp.strokeWidth = 2f
        bp.color = fxAlpha(a * 0.8f, 0xFF8FE3FF.toInt())
        c.drawLine(x - 10f, y, x + 10f, y, bp)
        c.drawLine(x, y - 6f, x, y + 6f, bp)
        bp.xfermode = null
    }

    private fun drawCloud(c: Canvas, x: Float, y: Float, s: Float, a: Float) {
        cloudPaint.alpha = (a.coerceIn(0f, 1f) * 255f).toInt()
        for (i in 0 until 8) {
            val k = i - 3.5f
            val bx = x + k * 62f * s + sin(timeAll * 0.9f + i * 1.7f) * 10f * s
            val by = y + sin(i * 2.3f) * 20f * s + cos(timeAll * 0.7f + i) * 6f * s
            val r = (95f + (i % 3) * 28f) * s
            c.save()
            c.translate(bx, by)
            c.scale(r, r * 0.62f)
            c.drawCircle(0f, 0f, 1f, cloudPaint)
            c.restore()
        }
        val ph = (timeAll * 11f).toInt()
        if (ph % 3 == 0) drawGlow(c, x + sin(ph * 2.1f) * 150f * s, y + cos(ph * 1.3f) * 15f * s, 90f * s, 0xFF5FB8FF.toInt(), 0.35f * a)
    }

    private fun drawPillar(c: Canvas, pl: Pillar) {
        val k = (pl.life / pl.maxLife).coerceIn(0f, 1f)
        val wob = 0.9f + 0.1f * sin(timeAll * 50f)
        val w = pl.w * (0.45f + 0.55f * k) * wob
        colPaint.alpha = (min(1f, k * 2.2f) * 0.85f * 255f).toInt()
        c.save()
        c.translate(pl.x, pl.y)
        c.scale(w, pl.h)
        c.drawRect(-0.5f, -1f, 0.5f, 0f, colPaint)
        c.restore()
    }

    private fun drawOrb(c: Canvas, pr: Proj) {
        val col = if (pr.enemy) C_RED else C_CYAN
        val outer = if (pr.enemy) C_REDD else C_BLUE
        val pal = colsOf(col, outer)
        drawGlow(c, pr.x, pr.y, 24f, pal[0], 0.5f)
        drawGlow(c, pr.x, pr.y, 9f, pal[2], 0.9f)
        for (k in 0 until 4) {
            val a = timeAll * 20f + k * 1.5708f
            val a2 = a + 0.9f
            boltPath.reset()
            boltPath.moveTo(pr.x + cos(a) * 5f, pr.y + sin(a) * 5f)
            boltPath.lineTo(pr.x + cos(a + 0.45f) * 18f + rnd(-3f, 3f), pr.y + sin(a + 0.45f) * 18f + rnd(-3f, 3f))
            boltPath.lineTo(pr.x + cos(a2) * 30f, pr.y + sin(a2) * 30f)
            strokePath(c, 2f, 0.9f, pal, false)
        }
    }

    private fun drawAimWorld(c: Canvas) {
        val l = hypot(aimVX, aimVY)
        val dx = aimVX / l
        val dy = aimVY / l
        val mag = min(1f, l)
        val x0 = player.x
        val y0 = player.y - BODY
        when (aimBtn) {
            0 -> c.drawLine(x0, y0, x0 + dx * 520f, y0 + dy * 520f, aimPaint)
            1 -> {
                val d = DASH_MIN + (DASH_DIST - DASH_MIN) * mag
                val ex = (player.x + dx * d).coerceIn(70f, AW - 70f)
                val ey = (player.y + dy * d).coerceIn(150f, AH - 50f)
                c.drawLine(player.x, player.y, ex, ey, aimPaint)
                drawReticle(c, ex, ey, LAND_R, 0.7f)
            }
            2 -> {
                val d = 80f + (THOR_RANGE - 80f) * mag
                val tx = (player.x + dx * d).coerceIn(80f, AW - 80f)
                val ty = (player.y + dy * d).coerceIn(160f, AH - 60f)
                c.drawLine(player.x, player.y, tx, ty, aimPaint)
                drawReticle(c, tx, ty, THOR_R, 0.7f)
            }
        }
    }

    private fun drawAnims(c: Canvas, ground: Boolean) {
        for (a in anims) {
            if (a.ground != ground) continue
            val n = a.sheet.size
            val k = (a.t / a.dur).coerceIn(0f, 1f)
            val fr = (k * n).toInt().coerceIn(0, n - 1)
            val al = if (a.add) 0.7f * (1f - k * 0.5f) else 1f - k * 0.15f
            drawFx(c, a.sheet[fr], a.x, a.y, a.size, a.size * a.squash, al, a.add, a.flt, a.rot)
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
        if (shake > 0f) { sx = rnd(-4f, 4f) * min(1f, shake * 3f); sy = rnd(-4f, 4f) * min(1f, shake * 3f) }
        c.translate(-camX + sx, -camY + sy)

        drawArena(c)

        // cicatrizes no chão (manchas suaves)
        for (sc in scorches) {
            val a = (sc.life / sc.maxLife).coerceIn(0f, 1f)
            val glow = ((sc.life - (sc.maxLife - 0.9f)) / 0.9f).coerceIn(0f, 1f)
            drawDark(c, sc.x, sc.y, sc.r * 1.7f, sc.r * 1.0f, min(1f, a * 1.6f))
            if (glow > 0f) drawGlowE(c, sc.x, sc.y, sc.r * 1.2f, sc.r * 0.7f, 0xFF5FB8FF.toInt(), glow * 0.16f)
        }

        // crateras e rachaduras no chão (as eletrificadas brilham e soltam faíscas)
        for (cr in craters) {
            val fade = min(1f, cr.life / 1.5f)
            val glow = if (cr.electric) fade else ((cr.life - (cr.maxLife - 1.2f)) / 1.2f).coerceIn(0f, 1f)
            val pulse = 0.65f + 0.35f * sin(timeAll * 5f + cr.x * 0.01f)
            drawDark(c, cr.x, cr.y, cr.r * 1.1f, cr.r * 0.68f, fade * 0.85f)
            rf.set(cr.x - cr.r * 0.92f, cr.y - cr.r * 0.56f, cr.x + cr.r * 0.92f, cr.y + cr.r * 0.56f)
            bp.xfermode = null
            bp.strokeWidth = 7f
            bp.color = fxAlpha(fade * 0.35f, 0xFF1A1D2B.toInt())
            c.drawOval(rf, bp)
            bp.strokeWidth = 2.5f
            bp.color = fxAlpha(fade * 0.5f, 0xFF6A7390.toInt())
            c.drawArc(rf, 200f, 110f, false, bp)
            if (glow > 0.02f) drawGlowE(c, cr.x, cr.y, cr.r * 0.85f, cr.r * 0.5f, 0xFF3A7BFF.toInt(), glow * pulse * 0.2f)
            for (rk in cr.rocks) {
                chunkPaint.color = fxAlpha(fade * 0.9f, 0xFF2B2A36.toInt())
                c.drawCircle(rk[0], rk[1], rk[2], chunkPaint)
                chunkPaint.color = fxAlpha(fade * 0.5f, 0xFF6A7390.toInt())
                c.drawCircle(rk[0] - rk[2] * 0.3f, rk[1] - rk[2] * 0.3f, rk[2] * 0.45f, chunkPaint)
            }
            for ((ci, ck) in cr.cracks.withIndex()) {
                polyPath(ck, 1f)
                bp.xfermode = null
                bp.strokeWidth = 6f
                bp.color = fxAlpha(fade * 0.9f, 0xFF04060C.toInt())
                c.drawPath(boltPath, bp)
                if (glow > 0.02f) {
                    val fl = 0.55f + 0.45f * sin(timeAll * 18f + ci * 1.7f)
                    bp.xfermode = addMode
                    bp.strokeWidth = 10f
                    bp.color = fxAlpha(glow * fl * 0.12f, 0xFF3A7BFF.toInt())
                    c.drawPath(boltPath, bp)
                    bp.strokeWidth = 4f
                    bp.color = fxAlpha(glow * fl * 0.4f, 0xFF5FB8FF.toInt())
                    c.drawPath(boltPath, bp)
                    bp.strokeWidth = 1.6f
                    bp.color = fxAlpha(glow * fl, 0xFFE8FBFF.toInt())
                    c.drawPath(boltPath, bp)
                    bp.xfermode = null
                }
            }
        }

        // escurece o chão (ultimate / raio fulminante)
        if (darken > 0.01f) {
            fx.style = Paint.Style.FILL
            fx.xfermode = null
            fx.color = fxAlpha(darken, 0xFF050A24.toInt())
            c.drawRect(camX - 60f, camY - 60f, camX + visW + 60f, camY + visH + 60f, fx)
        }

        // Raio Fulminante: mira no chão que se fecha no alvo
        for (f in listOf(player, enemy)) {
            if (!f.dead && f.castKind == 2 && !f.castFired) {
                val prog = (f.castT / (f.castDur * 0.47f)).coerceIn(0f, 1f)
                drawReticle(c, thorTx(f), thorTy(f), THOR_R * 1.5f, 0.25f + 0.4f * prog)
            }
        }
        for (k in thors) {
            val prog = (1f - k.delay / k.maxDelay).coerceIn(0f, 1f)
            drawReticle(c, k.x, k.y, THOR_R * (1f + 0.7f * (1f - prog)), 0.45f + 0.5f * prog)
        }

        // marcador de pouso da Queda Relâmpago
        for (f in listOf(player, enemy)) {
            if (!f.dead && f.dashT > 0f) {
                val prog = (1f - f.dashT / DASH_T).coerceIn(0f, 1f)
                drawReticle(c, f.dashEx, f.dashEy, LAND_R * (1f + 0.5f * (1f - prog)), 0.45f + 0.5f * prog)
            }
        }

        // mira das skills
        if (aimBtn >= 0 && !player.dead && aimDragged()) drawAimWorld(c)

        // rastros do passo
        for (g in ghosts) {
            val a = (g.life / 0.3f * 150f).toInt().coerceIn(0, 255)
            drawSprite(c, idle[g.dir][0], g.x, g.y, ghostF, a)
        }

        // lutadores ordenados por Y
        val order = if (player.y <= enemy.y) listOf(player, enemy) else listOf(enemy, player)
        for (f in order) drawFighter(c, f)

        // projéteis
        for (pr in projs) drawOrb(c, pr)

        // nuvem de tempestade do Raio Fulminante
        for (k in thors) {
            val prog = (1f - k.delay / k.maxDelay).coerceIn(0f, 1f)
            drawCloud(c, k.x, k.y - 430f, 0.6f + 0.4f * min(1f, prog * 2f), min(1f, prog * 3f))
        }

        // pedaços de terra voando e poeira
        for (ch in chunks) {
            val a = min(1f, ch.life / 0.5f)
            chunkPaint.color = fxAlpha(a, ch.col)
            c.drawRect(ch.x - ch.size, ch.y - ch.size * 0.8f, ch.x + ch.size, ch.y + ch.size * 0.8f, chunkPaint)
            chunkPaint.color = fxAlpha(a * 0.5f, 0xFF8A8FA8.toInt())
            c.drawRect(ch.x - ch.size, ch.y - ch.size * 0.8f, ch.x, ch.y - ch.size * 0.2f, chunkPaint)
        }
        for (pf in puffs) {
            val a = (pf.life / pf.maxLife).coerceIn(0f, 1f)
            puffPaint.alpha = (min(1f, a * 1.6f) * 0.8f * 255f).toInt()
            c.save()
            c.translate(pf.x, pf.y)
            c.scale(pf.r, pf.r * 0.7f)
            c.drawCircle(0f, 0f, 1f, puffPaint)
            c.restore()
        }

        // pilares de luz (raios vindos do céu)
        for (pl in pillars) drawPillar(c, pl)

        // raios
        for (b in bolts) drawBoltFx(c, b)

        // partículas (brilhantes, mistura aditiva)
        for (q in parts) {
            val a = (q.life / q.maxLife).coerceIn(0f, 1f)
            val rad = q.size * 0.4f * (0.5f + 0.5f * a)
            glowDot.color = fxAlpha(a * 0.2f, q.color)
            c.drawCircle(q.x, q.y, rad * 2.6f, glowDot)
            glowDot.color = fxAlpha(a, q.color)
            c.drawCircle(q.x, q.y, rad, glowDot)
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
            p.color = ((screenFlash / 0.4f * 55).toInt().coerceIn(0, 255) shl 24) or 0xDDF6FF
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
        if (aimBtn >= 0) {
            val b = btns[aimBtn]
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * u
            p.color = 0x449BEFFF
            c.drawCircle(b.cx, b.cy, 100f * u, p)
            p.style = Paint.Style.FILL
            p.color = 0xCC9BEFFF.toInt()
            c.drawCircle(b.cx + aimVX * 100f * u, b.cy + aimVY * 100f * u, 12f * u, p)
        }

        // dica
        if (hintT > 0f) {
            tp.textSize = 15f * u
            tp.color = ((min(1f, hintT) * 220).toInt() shl 24) or 0xFFFFFF
            c.drawText("Arraste à esquerda para andar  •  Segure e arraste um botão para mirar", w / 2f, h - 24f * u, tp)
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
                    0 -> if (aimId == -1) { atkId = id; aimBtn = 0; aimId = id; aimVX = 0f; aimVY = 0f }
                    1, 2 -> if (aimId == -1 && player.cd[b.idx] <= 0f && !player.dead) { aimBtn = b.idx; aimId = id; aimVX = 0f; aimVY = 0f }
                    3 -> { player.manual = false; startCast(player, 3) }
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

    private fun aimDragged(): Boolean = hypot(aimVX, aimVY) > 0.22f

    private fun updateAim(x: Float, y: Float) {
        if (aimBtn < 0) return
        val b = btns[aimBtn]
        var dx = (x - b.cx) / (100f * u)
        var dy = (y - b.cy) / (100f * u)
        val l = hypot(dx, dy)
        if (l > 1f) { dx /= l; dy /= l }
        aimVX = dx
        aimVY = dy
    }

    // converte o arrasto do botão em direção / alcance; sem arrastar, a mira é automática
    private fun applyAimTo(f: Fighter, idx: Int) {
        if (!aimDragged()) { f.manual = false; return }
        val l = hypot(aimVX, aimVY)
        val dx = aimVX / l
        val dy = aimVY / l
        val mag = min(1f, l)
        f.manual = true
        f.mAx = dx
        f.mAy = dy
        when (idx) {
            1 -> f.mDist = DASH_MIN + (DASH_DIST - DASH_MIN) * mag
            2 -> {
                val d = 80f + (THOR_RANGE - 80f) * mag
                f.mTx = (f.x + dx * d).coerceIn(80f, AW - 80f)
                f.mTy = (f.y + dy * d).coerceIn(160f, AH - 60f)
            }
        }
    }

    private fun fireAim() {
        val idx = aimBtn
        if (idx == 1) {
            applyAimTo(player, 1)
            startDash(player)
            player.manual = false
        } else if (idx == 2) {
            applyAimTo(player, 2)
            startCast(player, 2)
            if (player.castKind != 2) player.manual = false
        }
        aimBtn = -1
        aimId = -1
        aimVX = 0f
        aimVY = 0f
    }

    private fun release(id: Int) {
        if (id == joyId) { joyId = -1; joyX = 0f; joyY = 0f }
        if (id == atkId) atkId = -1
        if (id == aimId) fireAim()
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
                        if (e.getPointerId(i) == aimId) updateAim(e.getX(i), e.getY(i))
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex))
                MotionEvent.ACTION_UP -> {
                    release(e.getPointerId(e.actionIndex))
                    joyId = -1; joyX = 0f; joyY = 0f; atkId = -1
                }
                MotionEvent.ACTION_CANCEL -> {
                    joyId = -1; joyX = 0f; joyY = 0f; atkId = -1
                    aimBtn = -1; aimId = -1; aimVX = 0f; aimVY = 0f
                }
            }
        }
        return true
    }
}
