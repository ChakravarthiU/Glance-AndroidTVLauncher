"""Makes the moon's surface for the sky (Requirements.md 5.10): assets/sky/moon.webp, 256 x 256 with transparency.

Generated, not a photo, so it carries no licence: the near side's maria in roughly their real places (Imbrium,
Serenitatis, Tranquillitatis, Crisium, Procellarum, Nubium, Fecunditatis), bright highlands, a few hundred craters
with lit and shaded rims, Tycho's rays, fine grain, and a slight darkening at the edge. The phase and the soft
terminator are drawn on the TV (SkyArt). Needs numpy and Pillow:  python3 make_moon.py out.webp
"""
import sys
import numpy as np
from PIL import Image

N = 256
rng = np.random.default_rng(1969)
y, x = np.mgrid[0:N, 0:N].astype(np.float64)
u = (x + 0.5) / N * 2 - 1          # -1..1, right is +
v = (y + 0.5) / N * 2 - 1          # -1..1, down is +
r = np.sqrt(u * u + v * v)
inside = r <= 1.0

def blob(cx, cy, rx, ry, soft=0.55):
    # Edges pushed in and out by noise, so the maria have ragged, irregular shores.
    d = np.sqrt(((u + WARP_U - cx) / rx) ** 2 + ((v + WARP_V - cy) / ry) ** 2)
    return np.clip((1 - d) / soft, 0, 1) ** 1.2

def noise(scale, octaves=4):
    out = np.zeros((N, N))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        s = max(2, int(N / scale * 2 ** o))
        g = rng.random((s + 1, s + 1))
        img = Image.fromarray((g * 255).astype(np.uint8)).resize((N, N), Image.BICUBIC)
        out += amp * (np.asarray(img, np.float64) / 255)
        total += amp
        amp *= 0.5
    return out / total

WARP_U = 0.16 * (noise(14, 3) - 0.5)
WARP_V = 0.16 * (noise(14, 3) - 0.5)

# Highlands, then the maria (dark, smoother), as seen from the northern hemisphere.
albedo = 0.74 + 0.12 * (noise(24) - 0.5)
maria = np.zeros((N, N))
for cx, cy, rx, ry in [(-0.30, -0.42, 0.36, 0.28),   # Imbrium
                       (0.16, -0.34, 0.25, 0.22),    # Serenitatis
                       (0.30, -0.08, 0.30, 0.24),    # Tranquillitatis
                       (0.66, -0.28, 0.13, 0.11),    # Crisium
                       (-0.62, -0.05, 0.28, 0.50),   # Procellarum
                       (-0.22, 0.26, 0.27, 0.19),    # Nubium
                       (0.55, 0.14, 0.17, 0.23),     # Fecunditatis
                       (0.05, 0.02, 0.12, 0.10),     # Vaporum/Sinus Medii
                       (0.38, 0.35, 0.10, 0.10)]:    # Nectaris
    maria = np.maximum(maria, blob(cx, cy, rx, ry) * (0.75 + 0.25 * noise(10, 3)))
albedo -= 0.34 * maria
albedo += 0.05 * (noise(6, 3) - 0.5) * maria

# Craters: lit rim towards the upper left, shaded floor and lower-right rim.
shade = np.zeros((N, N))
for _ in range(240):
    cr = rng.uniform(0.006, 0.035) if rng.random() < 0.95 else rng.uniform(0.035, 0.07)
    while True:
        cx, cy = rng.uniform(-1, 1, 2)
        if cx * cx + cy * cy < 0.92:
            break
    dx, dy = (u - cx) / cr, (v - cy) / cr
    d = np.sqrt(dx * dx + dy * dy)
    ring = np.exp(-((d - 1.0) / 0.35) ** 2)
    side = (-dx - dy) / np.maximum(d, 1e-6) / 1.414
    floor = np.clip(1 - d, 0, 1)
    depth = 0.05 if cr > 0.03 else 0.03
    shade += (ring * side * depth - floor * 0.025) * (1 - maria * 0.6)
albedo += shade

# Tycho and its rays (lower middle), and Copernicus (bright, in Procellarum).
for cx, cy, cr, gain in [(-0.10, 0.62, 0.035, 0.22), (-0.32, -0.12, 0.03, 0.15)]:
    d = np.sqrt((u - cx) ** 2 + (v - cy) ** 2)
    albedo += gain * np.exp(-(d / cr) ** 2)
ang = np.arctan2(v - 0.62, u + 0.10)
dist = np.sqrt((u + 0.10) ** 2 + (v - 0.62) ** 2)
rays = np.zeros((N, N))
for a in rng.uniform(-np.pi, np.pi, 16):
    diff = np.angle(np.exp(1j * (ang - a)))
    rays += np.exp(-(diff / 0.03) ** 2) * np.exp(-dist / rng.uniform(0.4, 0.9))
albedo += 0.06 * np.clip(rays, 0, 1) * (dist > 0.04)

albedo += 0.035 * (rng.random((N, N)) - 0.5)          # grain
limb = np.sqrt(np.clip(1 - r * r, 0, 1))
albedo *= 0.82 + 0.18 * limb ** 0.6                   # slight darkening at the edge
albedo = np.clip(albedo, 0, 1)

rgb = np.stack([albedo * 0.97, albedo * 0.96, albedo * 0.93], -1)
alpha = np.clip((1.0 - r) * N / 2 + 0.5, 0, 1)        # anti-aliased edge
img = np.dstack([rgb * 255, alpha * 255]).astype(np.uint8)
Image.fromarray(img, 'RGBA').save(sys.argv[1] if len(sys.argv) > 1 else 'moon.webp', 'WEBP', quality=88,
                                  method=6)
