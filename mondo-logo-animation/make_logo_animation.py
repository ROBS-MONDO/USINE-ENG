#!/usr/bin/env python3
"""Genera l'animazione del logo MONDO (GIF) sulla falsariga dell'animazione Redcorp:
icona grande che ruota in 3D -> riflesso di luce -> rimpicciolisce a sinistra -> il testo si rivela.
Uso: python3 make_logo_animation.py [sorgente.png] [uscita.gif]"""
import sys, math
import numpy as np
from PIL import Image, ImageChops

SRC = sys.argv[1] if len(sys.argv) > 1 else "signature-source.png"
OUT = sys.argv[2] if len(sys.argv) > 2 else "mondo-logo-animation.gif"
# area del logo nella firma (x0, y0, x1, y1) e x di separazione icona/testo
BOX = (8, 244, 256, 286)
SPLIT = 48
K = 3                      # fattore di upscale di lavoro
FPS = 25
W, H = 260 * K, 84 * K     # canvas
BG = (255, 255, 255)

def ease_out(t): return 1 - (1 - t) ** 3
def ease_io(t): return 3 * t * t - 2 * t ** 3
def ease_back(t, s=1.4): t -= 1; return 1 + t * t * ((s + 1) * t + s)
clamp = lambda t: max(0.0, min(1.0, t))

logo = Image.open(SRC).convert("RGB").crop(BOX)
# alpha = distanza dal bianco (niente aloni su sfondo bianco)
arr = np.asarray(logo).astype(float)
alpha = np.clip((255 - arr.min(2)) / 255 * 1.0, 0, 1)
alpha = np.clip(alpha * 1.15, 0, 1)
rgba = np.dstack([arr, alpha * 255]).astype(np.uint8)
logo = Image.fromarray(rgba, "RGBA")
logo = logo.resize((logo.width * K, logo.height * K), Image.LANCZOS)
# riporta i colori pieni dove alpha>0 (evita bordi slavati)
a = np.asarray(logo).astype(float)
al = a[..., 3:4] / 255
a[..., :3] = np.clip((a[..., :3] - 255 * (1 - al)) / np.maximum(al, 1e-3), 0, 255)
logo = Image.fromarray(a.astype(np.uint8), "RGBA")
bb = logo.getbbox(); logo = logo.crop(bb)
sp = SPLIT * K - (BOX[0] * 0) - bb[0] if False else (SPLIT - BOX[0]) * K - bb[0]
icon = logo.crop((0, 0, sp, logo.height))
text = logo.crop((sp, 0, logo.width, logo.height))
icon = icon.crop(icon.getbbox()); gap = (logo.width - text.width - icon.width)
LW, LH = logo.size
fx = (W - LW) // 2                 # posizione finale logo
fy = (H - LH) // 2

def persp_coeffs(dst, src):
    A = []
    for (x, y), (u, v) in zip(dst, src):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); A.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    return np.linalg.solve(np.array(A, float), np.array(src, float).reshape(8)) if False else \
        np.linalg.solve(np.array(A, float), np.array([c for p in src for c in p], float))

def rotated(img, theta, scale, cx, cy, d=700.0):
    """Ruota img attorno all'asse verticale con prospettiva; centro in (cx,cy)."""
    w, h = img.size; w *= scale; h *= scale
    c, s = math.cos(theta), math.sin(theta)
    quad = []
    for x, y in [(-w/2, -h/2), (w/2, -h/2), (w/2, h/2), (-w/2, h/2)]:
        z = x * s; f = d / (d - z)
        quad.append((cx + x * c * f, cy + y * f))
    big = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    src = [(0, 0), (img.width, 0), (img.width, img.height), (0, img.height)]
    co = persp_coeffs(quad, src)
    return img.transform((W, H), Image.PERSPECTIVE, co, Image.BICUBIC)

def shine(layer, t):
    """Banda di luce diagonale che attraversa l'icona (limitata alla sua alpha)."""
    yy, xx = np.mgrid[0:H, 0:W]
    bbx = layer.getbbox()
    if not bbx: return layer
    x0, y0, x1, y1 = bbx; ww = x1 - x0
    pos = x0 - ww * 0.4 + t * ww * 1.8
    d = (xx - pos) + (yy - y0) * 0.55
    band = np.exp(-(d / (ww * 0.12)) ** 2) * 0.85
    arr = np.asarray(layer).astype(float)
    m = (arr[..., 3] / 255)[..., None]
    arr[..., :3] = arr[..., :3] * (1 - band[..., None] * m) + 255 * band[..., None] * m
    return Image.fromarray(arr.astype(np.uint8), "RGBA")

big_scale = 1.5
ccx, ccy = W / 2, H / 2
fin_icon_cx = fx + icon.width / 2
fin_icon_cy = fy + LH / 2

frames = []
def emit(layers):
    base = Image.new("RGBA", (W, H), BG + (255,))
    for l in layers: base.alpha_composite(l)
    frames.append(base.convert("RGB"))

N1, N2, N3, N4, HOLD = 22, 16, 22, 28, 80
# 1) rotazione 3D in ingresso
for i in range(N1):
    t = ease_back(i / (N1 - 1))
    th = math.radians(-80 * (1 - t))
    emit([rotated(icon, th, big_scale, ccx, ccy)])
# 2) riflesso
for i in range(N2):
    l = rotated(icon, 0, big_scale, ccx, ccy)
    emit([shine(l, i / (N2 - 1))])
# 3) rimpicciolisce e va a sinistra (con piccolo giro)
for i in range(N3):
    t = ease_io(i / (N3 - 1))
    sc = big_scale + (1 - big_scale) * t
    cx = ccx + (fin_icon_cx - ccx) * t; cy = ccy + (fin_icon_cy - ccy) * t
    th = math.radians(360 * t) * 0.0 + math.radians(-14) * math.sin(math.pi * t)
    emit([rotated(icon, th, sc, cx, cy)])
# 4) rivelazione testo da sinistra a destra
icon_final = rotated(icon, 0, 1, fin_icon_cx, fin_icon_cy)
for i in range(N4):
    t = ease_out(i / (N4 - 1))
    cw = int(text.width * t)
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if cw > 0:
        part = text.crop((0, 0, cw, text.height))
        # fade morbido sul bordo di rivelazione
        pa = np.asarray(part).astype(float); edge = min(cw, 14 * K)
        ramp = np.ones(cw)
        if cw < text.width: ramp[-edge:] = np.linspace(1, 0, edge)
        pa[..., 3] *= ramp[None, :]
        part = Image.fromarray(pa.astype(np.uint8), "RGBA")
        slide = int((1 - t) * 10 * K)
        layer.alpha_composite(part, (fx + icon.width + gap - slide + 0, fy))
        # il testo non deve sovrapporsi all'icona
    emit([layer, icon_final])
# 5) pausa finale
final = frames[-1]
for _ in range(HOLD): frames.append(final)

# output: dimensione display 2x (520px) -> usare width=260 in HTML
out = [f.resize((W * 2 // K, H * 2 // K), Image.LANCZOS) for f in frames]
pal = [f.quantize(colors=96, method=Image.MEDIANCUT, dither=Image.NONE) for f in out]
dur = [int(1000 / FPS)] * len(pal)
dur[-1] = 3000
# collassa i frame finali identici in uno solo con durata lunga
pal = pal[: len(pal) - HOLD + 1]; dur = dur[: len(pal)]; dur[-1] = 3500
pal[0].save(OUT, save_all=True, append_images=pal[1:], duration=dur, loop=0, optimize=True, disposal=1)
print(OUT, len(pal), "frame", out[0].size)
