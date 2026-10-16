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
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    companion object {
        const val AW = 2000f
        const val AH = 1300f
        const val SC = 2.0f              // escala das sprites
        const val BODY = 40f             // altura do centro do corpo acima dos pés
        val DIRS = arrayOf("south", "east", "north", "west")
        val CD_MAX = floatArrayOf(0.5f, 4f, 5f, 30f)       // 0 ataque, 1 passo, 2 raio, 3 ultimate
        val CAST_DUR = floatArrayOf(0.45f, 0f, 0.7f, 1.5f)
        const val ULT_R = 300f
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

    private class BoltFx(val pts: FloatArray, var life: Float, val maxLife: Float, val width: Float, val color: Int)
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
    private val strikes = ArrayList<Strike>()
    private val storms = ArrayList<Storm>()

    private var camX = 0f
    private var camY = 0f
    private var shake = 0f
    private var screenFlash = 0f
    private var hintT = 8f
    private var timeAll = 0f

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
        vs = h / 560f
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

    private fun burst(x: Float, y: Float, n: Int, color: Int, speed: Float, life: Float) {
        if (parts.size > 450) return
        for (i in 0 until n) {
            val a = rnd(0f, 6.2832f)
            val s = rnd(speed * 0.3f, speed)
            parts.add(Part(x, y, cos(a) * s, sin(a) * s, rnd(life * 0.5f, life), life, color, rnd(2f, 5f)))
        }
    }

    private fun makeBolt(x1: Float, y1: Float, x2: Float, y2: Float, life: Float, width: Float, color: Int, jitter: Float) {
        val n = 14
        val pts = FloatArray((n + 1) * 2)
        val dx = x2 - x1
        val dy = y2 - y1
        val len = max(1f, hypot(dx, dy))
        val nx = -dy / len
        val ny = dx / len
        for (i in 0..n) {
            val t = i / n.toFloat()
            val taper = if (i == 0 || i == n) 0f else 1f
            val off = rnd(-jitter, jitter) * taper
            pts[i * 2] = x1 + dx * t + nx * off
            pts[i * 2 + 1] = y1 + dy * t + ny * off
        }
        bolts.add(BoltFx(pts, life, life, width, color))
    }

    private fun damage(t: Fighter, amount: Float, kx: Float, ky: Float) {
        if (t.dead || t.dashT > 0f) return
        t.hp -= amount
        t.flash = 0.15f
        texts.add(FText(t.x + rnd(-20f, 20f), t.y - 120f, amount.toInt().toString(), 0.8f,
            if (t.isPlayer) 0xFFFF8A80.toInt() else 0xFFFFF59D.toInt()))
        burst(t.x, t.y - BODY, 8, 0xFF9BEFFF.toInt(), 260f, 0.4f)
        if (t.hp <= 0f) {
            t.hp = 0f
            t.dead = true
            t.respawnT = 2.2f
            t.castKind = -1
            texts.add(FText(t.x, t.y - 150f, if (t.isPlayer) "Derrotado!" else "Inimigo derrotado!", 1.6f, 0xFFFFFFFF.toInt()))
            burst(t.x, t.y - BODY, 30, 0xFFFFD54F.toInt(), 420f, 0.8f)
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
        rings.add(Ring(f.x, f.y - BODY, 90f, 0.3f, 0.3f, 0xFF9BEFFF.toInt()))
        burst(f.x, f.y - BODY, 14, 0xFFBDF3FF.toInt(), 300f, 0.4f)
    }

    private fun fire(f: Fighter, kind: Int) {
        val ox = f.x + f.aimX * 34f
        val oy = f.y - BODY - 6f + f.aimY * 10f
        when (kind) {
            0 -> {
                val sp = if (f.isPlayer) 780f else 420f
                val dmg = if (f.isPlayer) 45f else 35f
                projs.add(Proj(f, ox, oy, f.aimX * sp, f.aimY * sp, dmg, 0.75f, !f.isPlayer))
                burst(ox, oy, 6, if (f.isPlayer) 0xFF9BEFFF.toInt() else 0xFFFF8A80.toInt(), 180f, 0.3f)
            }
            2 -> {
                val len = 560f
                val ex = ox + f.aimX * len
                val ey = oy + f.aimY * len
                makeBolt(ox, oy, ex, ey, 0.3f, 10f, 0xFF7FE8FF.toInt(), 22f)
                makeBolt(ox, oy, ex, ey, 0.22f, 5f, 0xFFFFFFFF.toInt(), 14f)
                val o = opp(f)
                if (!o.dead && segDist(o.x, o.y - BODY, ox, oy, ex, ey) <= 56f) {
                    damage(o, 160f, f.x, f.y)
                    o.stun = 0.6f
                }
                burst(ex, ey, 14, 0xFF9BEFFF.toInt(), 320f, 0.5f)
                rings.add(Ring(ex, ey, 70f, 0.3f, 0.3f, 0xFF9BEFFF.toInt()))
                shake = max(shake, 0.12f)
            }
            3 -> {
                storms.add(Storm(f, f.x, f.y, 0f, 2.6f, 0f))
                rings.add(Ring(f.x, f.y, ULT_R, 0.6f, 0.6f, 0xFFFFD54F.toInt()))
                rings.add(Ring(f.x, f.y, ULT_R * 0.6f, 0.45f, 0.45f, 0xFFFFFFFF.toInt()))
                burst(f.x, f.y - BODY, 40, 0xFFFFE082.toInt(), 520f, 0.9f)
                screenFlash = 0.35f
                shake = 0.5f
            }
        }
    }

    // ---------- update ----------
    private fun update(dt: Float) {
        timeAll += dt
        if (hintT > 0f) hintT -= dt

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
            if (Random.nextFloat() < 0.6f) {
                parts.add(Part(pr.x, pr.y, rnd(-30f, 30f), rnd(-30f, 30f), 0.25f, 0.25f,
                    if (pr.enemy) 0xFFFF8A80.toInt() else 0xFF9BEFFF.toInt(), rnd(2f, 4f)))
            }
            val o = opp(pr.owner)
            if (!o.dead && o.dashT <= 0f && hypot(o.x - pr.x, o.y - BODY - pr.y) < 14f + 34f) {
                damage(o, pr.dmg, pr.x, pr.y)
                rings.add(Ring(pr.x, pr.y, 40f, 0.2f, 0.2f, 0xFFFFFFFF.toInt()))
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
                var sx: Float
                var sy: Float
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
            if (s.t >= s.dur) si.remove()
        }
        val ki = strikes.iterator()
        while (ki.hasNext()) {
            val k = ki.next()
            k.delay -= dt
            if (k.delay <= 0f) {
                makeBolt(k.x + rnd(-30f, 30f), k.y - 700f, k.x, k.y, 0.28f, 14f, 0xFF7FE8FF.toInt(), 30f)
                makeBolt(k.x + rnd(-30f, 30f), k.y - 700f, k.x, k.y, 0.2f, 6f, 0xFFFFFFFF.toInt(), 18f)
                rings.add(Ring(k.x, k.y, 80f, 0.3f, 0.3f, 0xFFFFE082.toInt()))
                burst(k.x, k.y, 10, 0xFFFFF59D.toInt(), 300f, 0.5f)
                val o = opp(k.owner)
                if (!o.dead && hypot(o.x - k.x, o.y - BODY - k.y) < 85f) {
                    damage(o, 90f, k.x, k.y)
                }
                shake = max(shake, 0.1f)
                ki.remove()
            }
        }

        // efeitos
        val bi = bolts.iterator(); while (bi.hasNext()) { val b = bi.next(); b.life -= dt; if (b.life <= 0f) bi.remove() }
        val ri = rings.iterator(); while (ri.hasNext()) { val r = ri.next(); r.life -= dt; if (r.life <= 0f) ri.remove() }
        val gi = ghosts.iterator(); while (gi.hasNext()) { val g = gi.next(); g.life -= dt; if (g.life <= 0f) gi.remove() }
        val ti = texts.iterator(); while (ti.hasNext()) { val t = ti.next(); t.life -= dt; t.y -= 40f * dt; if (t.life <= 0f) ti.remove() }
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
        if (shake > 0f) shake -= dt
        if (screenFlash > 0f) screenFlash -= dt

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
                for (i in 0 until 4) f.cd[i] = 0f
            }
            return
        }

        f.animT += dt
        if (f.stun > 0f) { f.stun -= dt; f.moving = false }

        // dash
        if (f.dashT > 0f) {
            f.dashT -= dt
            f.x += f.dashDx * 1800f * dt
            f.y += f.dashDy * 1800f * dt
            ghosts.add(Ghost(f.x, f.y, dirOf(f.faceX, f.faceY), 0.3f))
            parts.add(Part(f.x, f.y - BODY, rnd(-40f, 40f), rnd(-40f, 40f), 0.3f, 0.3f, 0xFF9BEFFF.toInt(), 4f))
            val o = opp(f)
            if (!f.dashHit && !o.dead && hypot(o.x - f.x, o.y - f.y) < 70f) {
                f.dashHit = true
                damage(o, 50f, f.x, f.y)
            }
            if (f.dashT <= 0f) rings.add(Ring(f.x, f.y - BODY, 70f, 0.25f, 0.25f, 0xFF9BEFFF.toInt()))
            clampPos(f)
            return
        }

        // conjuração
        if (f.castKind >= 0) {
            f.castT += dt
            if (!f.castFired && f.castT >= f.castDur * 0.47f) {
                f.castFired = true
                fire(f, f.castKind)
            }
            if (f.castKind == 3 && f.castT < f.castDur && Random.nextFloat() < 0.5f) {
                val a = rnd(0f, 6.2832f)
                parts.add(Part(f.x + cos(a) * 60f, f.y - BODY + sin(a) * 60f, -cos(a) * 120f, -sin(a) * 120f, 0.4f, 0.4f, 0xFFFFE082.toInt(), 4f))
            }
            if (f.castT >= f.castDur) f.castKind = -1
        }

        // movimento
        if (f.stun <= 0f) {
            val l = hypot(f.moveX, f.moveY)
            val mult = if (f.castKind == 3) 0f else if (f.castKind >= 0) 0.4f else 1f
            val speed = if (f.isPlayer) 240f else 110f
            f.moving = l > 0.15f && mult > 0f
            if (l > 0.15f && mult > 0f) {
                val nx = f.moveX / max(1f, l)
                val ny = f.moveY / max(1f, l)
                val mag = min(1f, l)
                f.x += nx * mag * speed * mult * dt
                f.y += ny * mag * speed * mult * dt
                if (f.castKind < 0) {
                    f.faceX = f.moveX / l
                    f.faceY = f.moveY / l
                }
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

    private fun drawFighter(c: Canvas, f: Fighter) {
        if (f.dead) return
        // sombra
        p.style = Paint.Style.FILL
        p.color = 0x55000000
        c.drawOval(f.x - 36f, f.y - 9f, f.x + 36f, f.y + 9f, p)
        val flt: ColorFilter? = if (f.flash > 0f) flashF else if (!f.isPlayer) redF else null
        drawSprite(c, frameOf(f), f.x, f.y, flt, 255)
        if (f.stun > 0f) {
            tp.textSize = 26f
            tp.color = 0xFFFFF59D.toInt()
            c.drawText("⚡", f.x + sin(timeAll * 12f) * 18f, f.y - 135f, tp)
        }
    }

    private fun boltPath(pts: FloatArray): Path {
        path.reset()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        return path
    }

    private fun render(c: Canvas) {
        c.drawColor(0xFF05070D.toInt())
        val w = width.toFloat()
        val h = height.toFloat()

        c.save()
        c.scale(vs, vs)
        var sx = 0f
        var sy = 0f
        if (shake > 0f) { sx = rnd(-5f, 5f) * min(1f, shake * 3f); sy = rnd(-5f, 5f) * min(1f, shake * 3f) }
        c.translate(-camX + sx, -camY + sy)

        drawArena(c)

        // marcações do ultimate
        for (s in storms) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 4f
            p.color = 0x55FFD54F
            val pulse = 1f + 0.03f * sin(timeAll * 8f)
            c.drawCircle(s.x, s.y, ULT_R * pulse, p)
            p.color = 0x22FFD54F
            c.drawCircle(s.x, s.y, ULT_R * 0.6f * pulse, p)
        }
        for (k in strikes) {
            val prog = 1f - k.delay / k.maxDelay
            p.style = Paint.Style.FILL
            p.color = ((40 + 80 * prog).toInt() shl 24) or 0xFFE082
            c.drawCircle(k.x, k.y, 85f * prog, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            p.color = 0xCCFFE082.toInt()
            c.drawCircle(k.x, k.y, 85f, p)
        }

        // rastros do passo
        for (g in ghosts) {
            val a = (g.life / 0.3f * 150f).toInt().coerceIn(0, 255)
            drawSprite(c, idle[g.dir][0], g.x, g.y, ghostF, a)
        }

        // lutadores ordenados por Y
        val order = if (player.y <= enemy.y) listOf(player, enemy) else listOf(enemy, player)
        for (f in order) drawFighter(c, f)

        // projéteis
        for (pr in projs) {
            val col = if (pr.enemy) 0xFFFF5252.toInt() else 0xFF7FE8FF.toInt()
            p.style = Paint.Style.FILL
            p.color = (col and 0x00FFFFFF) or 0x44000000
            c.drawCircle(pr.x, pr.y, 26f, p)
            p.color = (col and 0x00FFFFFF) or 0xAA000000.toInt()
            c.drawCircle(pr.x, pr.y, 16f, p)
            p.color = Color.WHITE
            c.drawCircle(pr.x, pr.y, 8f, p)
        }

        // raios
        for (b in bolts) {
            val a = (b.life / b.maxLife)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeJoin = Paint.Join.ROUND
            val pt = boltPath(b.pts)
            p.strokeWidth = b.width * 2.4f
            p.color = ((a * 90).toInt() shl 24) or (b.color and 0x00FFFFFF)
            c.drawPath(pt, p)
            p.strokeWidth = b.width
            p.color = ((a * 255).toInt() shl 24) or (b.color and 0x00FFFFFF)
            c.drawPath(pt, p)
        }
        p.strokeCap = Paint.Cap.BUTT

        // anéis
        for (r in rings) {
            val t = 1f - r.life / r.maxLife
            p.style = Paint.Style.STROKE
            p.strokeWidth = 5f * (1f - t) + 1f
            p.color = (((1f - t) * 255).toInt() shl 24) or (r.color and 0x00FFFFFF)
            c.drawCircle(r.x, r.y, r.maxR * t, p)
        }

        // partículas
        p.style = Paint.Style.FILL
        for (q in parts) {
            val a = (q.life / q.maxLife).coerceIn(0f, 1f)
            p.color = ((a * 255).toInt() shl 24) or (q.color and 0x00FFFFFF)
            c.drawRect(q.x - q.size / 2f, q.y - q.size / 2f, q.x + q.size / 2f, q.y + q.size / 2f, p)
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
            p.color = ((screenFlash / 0.35f * 120).toInt() shl 24) or 0xFFFFFF
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
