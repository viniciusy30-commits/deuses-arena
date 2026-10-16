#!/usr/bin/env python3
"""Gera as texturas pixel art dos efeitos do Zeus em app/src/main/assets/fx.
Paleta fixa, bordas duras, sem anti-aliasing, dithering ordenado (Bayer 4x4).
Uso: python3 tools/gen_fx.py
"""
import math, os, random, sys
import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "fx")
os.makedirs(OUT, exist_ok=True)

BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


def hexc(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


# rampa elétrica (escura -> clara)
EL = [hexc(c) for c in (0x0B134D, 0x1B2F9E, 0x2E6BFF, 0x5CB0FF, 0x9BE8FF, 0xE6FBFF, 0xFFFFFF)]
# rampa fumaça / nuvem
SM = [hexc(c) for c in (0x0E1226, 0x1B2142, 0x2C3562, 0x434F86, 0x6877B3, 0x98A8D8)]
# rampa carvão (cicatrizes)
CH = [hexc(c) for c in (0x03040A, 0x090C16, 0x131828, 0x1F2640)]


def canvas(w, h):
    return np.zeros((h, w, 4), np.uint8)


def put(img, x, y, c, a=255):
    h, w = img.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        img[y, x] = (c[0], c[1], c[2], a)


def ramp_pick(f, x, y, ramp):
    """f em [0,1] -> cor da rampa com dithering ordenado."""
    f = min(1.0, max(0.0, f)) * (len(ramp) - 1)
    b = int(math.floor(f))
    if b >= len(ramp) - 1:
        return ramp[-1]
    frac = f - b
    return ramp[b + 1] if frac > BAYER[y % 4, x % 4] else ramp[b]


def stamp(img, x, y, r, c, a=255):
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy <= r * r + r:
                put(img, x + dx, y + dy, c, a)


def line(img, x0, y0, x1, y1, c, r=0, a=255):
    x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        if r == 0:
            put(img, x0, y0, c, a)
        else:
            stamp(img, x0, y0, r, c, a)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def zig(rng, x0, y0, x1, y1, amp, iters=4):
    pts = [(x0, y0), (x1, y1)]
    a = amp
    for _ in range(iters):
        out = []
        for i in range(len(pts) - 1):
            ax, ay = pts[i]
            bx, by = pts[i + 1]
            l = max(1e-6, math.hypot(bx - ax, by - ay))
            off = rng.uniform(-a, a)
            out.append((ax, ay))
            out.append(((ax + bx) / 2 - (by - ay) / l * off, (ay + by) / 2 + (bx - ax) / l * off))
        out.append(pts[-1])
        pts = out
        a *= 0.55
    return pts


def save(img, name):
    Image.fromarray(img, "RGBA").save(os.path.join(OUT, name))


def dist_field(w, h, polylines):
    """distância mínima de cada pixel até as polylines (lista de (pts, escala))."""
    ys, xs = np.mgrid[0:h, 0:w]
    best = np.full((h, w), 1e9)
    for pts, sc in polylines:
        for i in range(len(pts) - 1):
            ax, ay = pts[i]
            bx, by = pts[i + 1]
            abx, aby = bx - ax, by - ay
            l2 = abx * abx + aby * aby
            if l2 == 0:
                t = np.zeros((h, w))
            else:
                t = np.clip(((xs - ax) * abx + (ys - ay) * aby) / l2, 0, 1)
            cx = ax + abx * t - xs
            cy = ay + aby * t - ys
            d = np.sqrt(cx * cx + cy * cy) * sc
            best = np.minimum(best, d)
    return best


def bolt_from_field(d, ramp_edges=(1.4, 2.4, 3.6, 5.2, 7.2), outer=9.5):
    """Converte campo de distância em pixel art de raio (núcleo branco -> azul)."""
    h, w = d.shape
    img = canvas(w, h)
    for y in range(h):
        for x in range(w):
            v = d[y, x]
            if v > outer:
                continue
            if v < ramp_edges[0]:
                c = EL[6]
            elif v < ramp_edges[1]:
                c = EL[5]
            elif v < ramp_edges[2]:
                c = EL[4]
            elif v < ramp_edges[3]:
                c = EL[3]
            elif v < ramp_edges[4]:
                c = EL[2]
            else:
                # borda externa: dithering para transparente
                t = (v - ramp_edges[4]) / (outer - ramp_edges[4])
                if t > BAYER[y % 4, x % 4] * 1.0 + 0.0 and t > 0.35:
                    continue
                c = EL[1]
            put(img, x, y, c)
    return img


# ------------------------------------------------------------------ glow
def gen_glow():
    n = 32
    img = canvas(n, n)
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - 15.5, y - 15.5) / 16.0
            f = 1.0 - r
            if f < 0.08:
                continue
            c = ramp_pick(f * 0.95, x, y, EL[1:6])
            put(img, x, y, c)
    save(img, "glow.png")


# ------------------------------------------------------------------ orb (plasma)
def gen_orb():
    n = 32
    for fr in range(8):
        rng = random.Random(100 + fr)
        img = canvas(n, n)
        R = 11.5 + 1.2 * math.sin(fr * math.pi / 4)
        ph = fr * (2 * math.pi / 3) / 8  # 3 braços -> loop perfeito
        for y in range(n):
            for x in range(n):
                dx, dy = x - 15.5, y - 15.5
                r = math.hypot(dx, dy)
                th = math.atan2(dy, dx)
                wob = 1.0 + 0.10 * math.sin(3 * th + ph * 3) + 0.06 * math.sin(5 * th - ph * 6)
                f = 1.0 - r / (R * wob)
                if f <= 0.0:
                    continue
                f += 0.18 * math.sin(3 * th + ph * 3 + r * 0.5) * (1 - f)
                put(img, x, y, ramp_pick(f * 1.05 + 0.05, x, y, EL[1:]))
        # espinhos elétricos
        for k in range(7):
            a = rng.uniform(0, 6.2832)
            l = rng.uniform(4, 7)
            sx, sy = 15.5 + math.cos(a) * (R * 0.95), 15.5 + math.sin(a) * (R * 0.95)
            ex, ey = sx + math.cos(a + rng.uniform(-0.5, 0.5)) * l, sy + math.sin(a + rng.uniform(-0.5, 0.5)) * l
            line(img, sx, sy, ex, ey, EL[5] if k % 2 else EL[4])
            put(img, int(round(ex)), int(round(ey)), EL[6])
        save(img, "orb_%02d.png" % fr)


# ------------------------------------------------------------------ spark (impacto elétrico)
def gen_spark():
    n = 48
    c0 = 23.5
    for fr in range(10):
        k = fr / 9.0
        ease = 1 - (1 - k) ** 2
        rng = random.Random(200 + fr)
        img = canvas(n, n)
        # anel tracejado, só no início
        if k < 0.65:
            rr = 3 + 10 * ease
            for y in range(n):
                for x in range(n):
                    r = math.hypot(x - c0, y - c0)
                    if abs(r - rr) <= 0.7 and ((x + y) % 3 != 0):
                        put(img, x, y, ramp_pick(1.0 - k, x, y, EL[2:]))
        # raios principais (longos, em zigue-zague, com ramificação)
        nr = 9
        for i in range(nr):
            a = i * 6.2832 / nr + rng.uniform(-0.3, 0.3)
            L = (8 + 15 * ease) * rng.uniform(0.65, 1.2)
            s0 = 1 + 4 * k
            x0, y0 = c0 + math.cos(a) * s0, c0 + math.sin(a) * s0
            x1, y1 = c0 + math.cos(a) * (s0 + L), c0 + math.sin(a) * (s0 + L)
            pts = zig(rng, x0, y0, x1, y1, 3.2, 3)
            col = EL[max(2, 6 - int(k * 4.5))]
            for j in range(len(pts) - 1):
                line(img, pts[j][0], pts[j][1], pts[j + 1][0], pts[j + 1][1], col, 0)
                if k < 0.5:
                    line(img, pts[j][0] + 1, pts[j][1], pts[j + 1][0] + 1, pts[j + 1][1], EL[3], 0)
            if i % 3 == 0 and len(pts) > 6:
                bx, by = pts[len(pts) // 2]
                ba = a + rng.choice([-1, 1]) * rng.uniform(0.6, 1.0)
                bp = zig(rng, bx, by, bx + math.cos(ba) * L * 0.4, by + math.sin(ba) * L * 0.4, 2, 2)
                for j in range(len(bp) - 1):
                    line(img, *bp[j], *bp[j + 1], EL[4])
            if k < 0.7:
                put(img, int(round(pts[-1][0])), int(round(pts[-1][1])), EL[6])
        # núcleo
        cr = 9.0 * (1 - k) ** 0.8
        for y in range(n):
            for x in range(n):
                r = math.hypot(x - c0, y - c0)
                if r <= cr:
                    put(img, x, y, ramp_pick(1.15 - r / max(cr, 1) * 0.6, x, y, EL[2:]))
        for _ in range(int(16 * (1 - k))):
            a = rng.uniform(0, 6.2832)
            r = rng.uniform(8, 22)
            put(img, int(c0 + math.cos(a) * r), int(c0 + math.sin(a) * r), EL[rng.choice([4, 5, 6])])
        save(img, "spark_%02d.png" % fr)


# ------------------------------------------------------------------ blast (onda de choque no chão)
def gen_blast():
    n = 64
    c0 = 31.5
    for fr in range(12):
        k = fr / 11.0
        rng = random.Random(300 + fr)
        img = canvas(n, n)
        rr = 5 + 25 * (1 - (1 - k) ** 2.2)
        th = 5.5 * (1 - k) + 1.2
        for y in range(n):
            for x in range(n):
                r = math.hypot(x - c0, y - c0)
                d = abs(r - rr)
                if d <= th:
                    t = d / th
                    lvl = (1 - t) * (1 - 0.55 * k)
                    put(img, x, y, ramp_pick(lvl * 1.15, x, y, EL[1:]))
                elif r < rr - th:
                    f = (1 - r / rr) * 0.55 * (1 - k) ** 1.3
                    if f > BAYER[y % 4, x % 4] * 0.55:
                        put(img, x, y, EL[2] if f > 0.22 else EL[1])
        # tentáculos de raio ao longo do anel
        for i in range(8):
            a = i * 6.2832 / 8 + rng.uniform(-0.2, 0.2)
            L = 8 + 8 * (1 - k)
            pts = zig(rng, c0 + math.cos(a) * (rr - L * 0.4), c0 + math.sin(a) * (rr - L * 0.4),
                      c0 + math.cos(a) * (rr + L * 0.8), c0 + math.sin(a) * (rr + L * 0.8), 3, 2)
            for j in range(len(pts) - 1):
                line(img, *pts[j], *pts[j + 1], EL[6] if k < 0.5 else EL[4])
        # detritos
        for _ in range(int(16 * (1 - k))):
            a = rng.uniform(0, 6.2832)
            r = rr + rng.uniform(2, 9)
            put(img, int(c0 + math.cos(a) * r), int(c0 + math.sin(a) * r), EL[rng.choice([3, 4, 5])])
        save(img, "blast_%02d.png" % fr)


# ------------------------------------------------------------------ pilar de raio (coluna do céu)
def pillar(name, w, h, scale, seed_base, nbr):
    for fr in range(4):
        rng = random.Random(seed_base + fr)
        cx = w / 2.0
        top = zig(rng, cx + rng.uniform(-4, 4), 0, cx + rng.uniform(-4, 4), h - 8, w * 0.34, 6)
        top = [(min(w - 10, max(10, x)), y) for x, y in top]
        lines = [(top, 1.0)]
        for _ in range(nbr):
            idx = rng.randint(len(top) // 6, len(top) * 5 // 6)
            bx, by = top[idx]
            side = rng.choice([-1, 1])
            L = rng.uniform(h * 0.07, h * 0.18)
            ang = math.pi / 2 + side * rng.uniform(0.6, 1.1)
            br = zig(rng, bx, by, bx + math.cos(ang) * L, by + math.sin(ang) * L, 5, 3)
            lines.append((br, 2.4))
        d = dist_field(w, h, lines)
        ys = np.arange(h).reshape(-1, 1)
        widen = 1.0 + 0.9 * np.clip((ys - h * 0.6) / (h * 0.4), 0, 1) ** 2
        d = d / widen / scale
        img = bolt_from_field(d, tuple(e * scale * 0.8 for e in (1.4, 2.4, 3.6, 5.2, 7.2)), 8.0 * scale)
        # clarão achatado na base (elipse)
        for y in range(h - 16, h):
            for x in range(w):
                dx = (x - cx) / (w * 0.5)
                dy = (y - (h - 7)) / 8.0
                r = math.hypot(dx, dy)
                if r < 1.0:
                    put(img, x, y, ramp_pick(1.0 - r * 0.9, x, y, EL[1:]))
        for y in range(0, 24):
            for x in range(w):
                if img[y, x, 3] and (y / 24.0) < BAYER[y % 4, x % 4]:
                    img[y, x, 3] = 0
        save(img, "%s_%02d.png" % (name, fr))


# ------------------------------------------------------------------ nuvem de tempestade
def gen_cloud():
    W, H = 96, 56
    rng0 = random.Random(401)
    circles = []
    for i in range(11):
        cx = 12 + i * 7.2 + rng0.uniform(-2, 2)
        base = 34 - 12 * math.sin(i / 10 * math.pi)
        circles.append((cx, base + rng0.uniform(-6, 4), rng0.uniform(8, 15)))
    for i in range(4):
        circles.append((24 + i * 16 + rng0.uniform(-4, 4), 36 + rng0.uniform(-2, 4), rng0.uniform(9, 13)))
    for fr in range(4):
        img = canvas(W, H)
        flick = fr in (1, 3)
        bolt = []
        if flick:
            fr_rng = random.Random(500 + fr)
            sx = fr_rng.randint(30, 66)
            bolt = zig(fr_rng, sx, 16, sx + fr_rng.randint(-14, 14), 44, 5, 3)
        bd = dist_field(W, H, [(bolt, 1.0)]) if flick else None
        mask = np.zeros((H, W), bool)
        for y in range(H):
            for x in range(W):
                best = None
                for cx, cy, r in circles:
                    dd = math.hypot(x - cx, (y - cy) * 1.15)
                    if dd < r:
                        sc = r - dd
                        if best is None or sc > best[0]:
                            best = (sc, cx, cy, r)
                if best is None:
                    continue
                mask[y, x] = True
                _, cx, cy, r = best
                lit = 0.62 - ((y - cy) / r) * 0.5 - ((x - cx) / r) * 0.12 + 0.12 * (best[0] / r)
                lit -= 0.18 * max(0.0, (y - 36) / 20.0)
                if flick:
                    lit += max(0.0, 0.75 - bd[y, x] / 15.0)
                put(img, x, y, ramp_pick(lit, x, y, SM))
        if flick:
            for y in range(H):
                for x in range(W):
                    if mask[y, x] and bd[y, x] < 1.2:
                        put(img, x, y, EL[6])
                    elif mask[y, x] and bd[y, x] < 2.2:
                        put(img, x, y, EL[4])
        for y in range(H - 1):
            for x in range(W):
                if mask[y, x] and (y == H - 1 or not mask[y + 1, x]):
                    put(img, x, y, SM[0])
        save(img, "cloud_%02d.png" % fr)


# ------------------------------------------------------------------ fumaça
def gen_smoke():
    n = 48
    for fr in range(8):
        k = fr / 7.0
        rng = random.Random(600)
        blobs = []
        for i in range(7):
            a = rng.uniform(0, 6.2832)
            d = rng.uniform(0, 9) * (0.6 + k)
            blobs.append((23.5 + math.cos(a) * d, 23.5 + math.sin(a) * d * 0.8, rng.uniform(7, 11) * (0.7 + 0.5 * k)))
        img = canvas(n, n)
        for y in range(n):
            for x in range(n):
                inside = [b for b in blobs if (x - b[0]) ** 2 + (y - b[1]) ** 2 < b[2] ** 2]
                if not inside:
                    continue
                vis = 1.0 - k * 0.95
                if vis < BAYER[(y + 1) % 4, (x + 2) % 4] * 1.05:
                    continue
                b = inside[0]
                lit = 0.5 - ((x - b[0]) * 0.5 + (y - b[1]) * 0.8) / (2 * b[2])
                put(img, x, y, ramp_pick(lit * 0.9, x, y, SM[:5]))
        save(img, "smoke_%02d.png" % fr)


# ------------------------------------------------------------------ cicatrizes de chão
def gen_scorch():
    n = 48
    for v in range(4):
        rng = random.Random(700 + v)
        R = 19
        phs = [rng.uniform(0, 6.2832) for _ in range(3)]
        cracks = []
        nc = rng.randint(6, 9)
        for i in range(nc):
            a = i * 6.2832 / nc + rng.uniform(-0.3, 0.3)
            L = rng.uniform(13, 22)
            cracks.append(zig(rng, 23.5, 23.5, 23.5 + math.cos(a) * L, 23.5 + math.sin(a) * L, 3.5, 3))
        d = canvas(n, n)
        g = canvas(n, n)
        for y in range(n):
            for x in range(n):
                dx, dy = x - 23.5, y - 23.5
                r = math.hypot(dx, dy)
                th = math.atan2(dy, dx)
                rad = R * (0.72 + 0.16 * math.sin(2 * th + phs[0]) + 0.1 * math.sin(5 * th + phs[1]) + 0.06 * math.sin(9 * th + phs[2]))
                if r > rad:
                    continue
                f = 1.0 - r / rad
                if f < 0.25 and f / 0.25 < BAYER[y % 4, x % 4]:
                    continue
                put(d, x, y, ramp_pick(f * 0.7, x, y, CH[::-1]))
        for ck in cracks:
            for j in range(len(ck) - 1):
                line(d, *ck[j], *ck[j + 1], CH[0])
                t = j / max(1, len(ck) - 2)
                col = EL[6] if t < 0.3 else (EL[4] if t < 0.6 else EL[2])
                line(g, *ck[j], *ck[j + 1], col)
        save(d, "scorchd_%02d.png" % v)
        save(g, "scorchg_%02d.png" % v)


# ------------------------------------------------------------------ aura de carga
def gen_aura():
    n = 64
    for fr in range(8):
        img = canvas(n, n)
        base = fr * (2 * math.pi / 3) / 8  # 3 braços -> loop perfeito
        for arm in range(3):
            a0 = base + arm * 2 * math.pi / 3
            for s in range(34):
                a = a0 + s * 0.055
                r = 29 - s * 0.16
                t = 1.0 - s / 34.0
                x = int(round(31.5 + math.cos(a) * r))
                y = int(round(31.5 + math.sin(a) * r))
                w = 2 if t < 0.5 else 3
                col = EL[6] if t > 0.9 else (EL[5] if t > 0.7 else (EL[4] if t > 0.45 else (EL[3] if t > 0.25 else EL[2])))
                stamp(img, x, y, w - 1, col)
        # faíscas orbitando
        rr = random.Random(800 + fr)
        for _ in range(9):
            a = rr.uniform(0, 6.2832)
            r = rr.uniform(14, 30)
            put(img, int(31.5 + math.cos(a) * r), int(31.5 + math.sin(a) * r), EL[rr.choice([4, 5, 6])])
        save(img, "aura_%02d.png" % fr)


# ------------------------------------------------------------------ runas
GLYPHS = [
    ["..#..", ".###.", "#.#.#", "..#..", "..#..", ".#.#.", "#...#"],
    ["#...#", "##.##", "#.#.#", "#...#", "#...#", ".#.#.", "..#.."],
    ["..#..", "..#..", "####.", "..#.#", "..#.#", "..###", ".#..."],
    ["#####", "#...#", "..#..", ".###.", "..#..", "#...#", "#####"],
    [".#.#.", "#####", ".#.#.", "..#..", ".#.#.", "#####", ".#.#."],
    ["#..#.", "#.##.", "####.", ".##.#", ".#.##", ".#..#", "....#"],
    ["..#..", ".##..", "#.#.#", "..###", "#.#..", "..##.", "..#.."],
    ["###..", "#..#.", "#...#", ".###.", "#...#", "#..#.", "###.."],
]


def glyph(img, gx, gy, gi, col, shadow=None, sc=1):
    g = GLYPHS[gi % len(GLYPHS)]
    for layer in (0, 1):
        for j, row in enumerate(g):
            for i, ch in enumerate(row):
                if ch != "#":
                    continue
                if layer == 0 and not shadow:
                    continue
                c = shadow if layer == 0 else col
                o = sc if layer == 0 else 0
                for a in range(sc):
                    for b in range(sc):
                        put(img, gx + (i - 2) * sc + a + o, gy + (j - 3) * sc + b + o, c)


def circle(img, cx, cy, r, col, th=1.0, arcs=None):
    n = img.shape[0]
    for y in range(n):
        for x in range(img.shape[1]):
            d = abs(math.hypot(x - cx, y - cy) - r)
            if d <= th * 0.5 + 0.35:
                if arcs:
                    a = math.atan2(y - cy, x - cx) % 6.2832
                    if not arcs(a):
                        continue
                put(img, x, y, col)


def poly_star(img, cx, cy, r, n, step, rot, col):
    pts = [(cx + math.cos(rot + i * 6.2832 / n) * r, cy + math.sin(rot + i * 6.2832 / n) * r) for i in range(n)]
    for i in range(n):
        j = (i + step) % n
        line(img, *pts[i], *pts[j], col)


def rune_sheet(name, n, frames, r_out, nglyph, star_n, star_step, glyph_ring, inner_r, gl_loop_deg, star_loop_deg, ticks, gsc=1):
    c0 = n / 2.0 - 0.5
    for fr in range(frames):
        img = canvas(n, n)
        k = fr / frames
        # névoa interna (dither)
        for y in range(n):
            for x in range(n):
                r = math.hypot(x - c0, y - c0)
                if r < r_out - 2:
                    f = 0.30 * (1 - r / r_out) + 0.08
                    if f > BAYER[y % 4, x % 4] * 0.6:
                        put(img, x, y, EL[1])
        circle(img, c0, c0, r_out, EL[4], 2.0)
        circle(img, c0, c0, r_out - 3, EL[2], 1.0)
        circle(img, c0, c0, glyph_ring - 4 - 2 * gsc, EL[3], 1.0)
        circle(img, c0, c0, glyph_ring + 4 + 2 * gsc, EL[3], 1.0)
        # marcas externas fixas
        for i in range(ticks):
            a = i * 6.2832 / ticks
            line(img, c0 + math.cos(a) * (r_out + 1), c0 + math.sin(a) * (r_out + 1), c0 + math.cos(a) * (r_out + 4), c0 + math.sin(a) * (r_out + 4), EL[5])
        # runas giratórias
        rot = math.radians(gl_loop_deg) * k
        for i in range(nglyph):
            a = rot + i * 6.2832 / nglyph
            gx = int(round(c0 + math.cos(a) * glyph_ring))
            gy = int(round(c0 + math.sin(a) * glyph_ring))
            glyph(img, gx, gy, i, EL[6] if i % 2 == 0 else EL[5], EL[1], gsc)
        # estrela interna
        sr = math.radians(star_loop_deg) * k
        poly_star(img, c0, c0, inner_r, star_n, star_step, -sr + 0.3, EL[5])
        poly_star(img, c0, c0, inner_r * 0.62, star_n, star_step, sr + 0.3, EL[3])
        circle(img, c0, c0, inner_r, EL[4], 1.0)
        # símbolo central: raio
        bolt_pts = [(c0 + 2, c0 - 5), (c0 - 2, c0 - 0.5), (c0 + 1, c0 - 0.5), (c0 - 2, c0 + 5)]
        for j in range(3):
            line(img, *bolt_pts[j], *bolt_pts[j + 1], EL[6])
        save(img, "%s_%02d.png" % (name, fr))


def gen_runes():
    # runa gigante do Ultimate (azul)
    rune_sheet("rune_ult", 160, 8, 74, 12, 6, 2, 56, 40, 30, 60, 24, 2)
    # runa pequena (raios do Ultimate)
    rune_sheet("rune_s", 48, 6, 22, 6, 3, 1, 15, 9, 60, 120, 8)


def main():
    gen_glow()
    gen_orb()
    gen_spark()
    gen_blast()
    pillar("pillar", 48, 208, 1.0, 900, 3)
    pillar("thor", 96, 208, 1.9, 950, 6)
    gen_cloud()
    gen_smoke()
    gen_scorch()
    gen_aura()
    gen_runes()
    print("ok")


if __name__ == "__main__":
    main()
