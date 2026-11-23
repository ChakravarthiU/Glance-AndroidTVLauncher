#!/usr/bin/env python3
"""
Builds a festive banner as ONE Lottie JSON file for the TV launcher.

Layers, top to bottom:
  - sparkles      vector 4-point stars that twinkle at random moments
  - LINE_1/LINE_2 text layers the launcher replaces at run time (TextDelegate)
  - extras        the design's own moving parts:
                    diwali     diya flames that flicker (scale, sway) with soft glows that pulse
                    birthday   balloons that bob and confetti that spins
                    christmas  twinkling baubles and drifting snowflakes
                    simple     none
  - art           the still artwork (cloth, garland, tassels, diya bowls...) embedded as WebP

Banner template: 1200 x 340 canvas, text centred at x=600, line 1 baseline y=188, line 2 baseline y=228,
text safe zone x 300..900. Every banner uses this template, so the launcher never needs per-banner positions.

Usage: make_banner.py <design> <still-art.webp> <out.json> [line1] [line2]
       design: diwali | simple | birthday | christmas
"""
import base64, json, math, random, sys

FPS, FRAMES, W, H = 30, 90, 1200, 340          # 3-second seamless loop
TEXT_X, LINE1_Y, LINE2_Y = 600, 188, 228
DIYAS = [(105, 260), (1095, 260)]               # diya bowl centres in the template
SPARKLES = [(140, 100, 7), (1060, 110, 6), (260, 240, 5), (940, 270, 6), (70, 170, 4),
            (1130, 165, 5), (330, 58, 4), (870, 58, 4), (600, 262, 3)]


def static(v):
    return {"a": 0, "k": v}


def animated(keys, ease=(0.42, 0.58)):
    """keys: [(frame, value-list)]; the last key closes the loop."""
    out = []
    for n, (t, v) in enumerate(keys):
        k = {"t": t, "s": v}
        if n < len(keys) - 1:
            dims = len(v)
            k["i"] = {"x": [1 - ease[0]] * dims, "y": [1] * dims}
            k["o"] = {"x": [ease[1] - 0.16] * dims, "y": [0] * dims}
        out.append(k)
    return {"a": 1, "k": out}


def transform(p, a=(0, 0), s=None, r=None, o=None):
    return {"p": static([p[0], p[1], 0]), "a": static([a[0], a[1], 0]),
            "s": s or static([100, 100, 100]), "r": r or static(0), "o": o or static(100)}


def layer(ind, ty, nm, ks, **extra):
    d = {"ddd": 0, "ind": ind, "ty": ty, "nm": nm, "sr": 1, "ks": ks, "ao": 0,
         "ip": 0, "op": FRAMES, "st": 0, "bm": 0}
    d.update(extra)
    return d


def group(items):
    return {"ty": "gr", "it": items + [{"ty": "tr", "p": static([0, 0]), "a": static([0, 0]),
                                        "s": static([100, 100]), "r": static(0), "o": static(100),
                                        "sk": static(0), "sa": static(0)}]}


def path(verts, ins, outs, closed=True):
    return {"ty": "sh", "ks": static({"c": closed, "v": verts, "i": ins, "o": outs})}


def radial(center, edge, stops, opacity_stops=None):
    """stops: [(pos, r, g, b)]; opacity_stops: [(pos, alpha)]"""
    k = [x for s in stops for x in s]
    if opacity_stops:
        k += [x for s in opacity_stops for x in s]
    return {"ty": "gf", "o": static(100), "r": 1, "t": 2, "s": static(list(center)), "e": static(list(edge)),
            "g": {"p": len(stops), "k": static(k)}, "h": static(0), "a": static(0)}


def wobble(rng, base, spread, step=(3, 6)):
    """Random keyframes around base, first and last equal so the loop is seamless."""
    keys, t = [(0, base)], 0
    while True:
        t += rng.randint(*step)
        if t >= FRAMES - 2:
            break
        keys.append((t, [b + rng.uniform(-s, s) for b, s in zip(base, spread)]))
    keys.append((FRAMES, base))
    return keys


def flame_layers(ind, rng, cx, cy):
    base_y = cy - 16
    # teardrop: tip, right, bottom, left (origin at the flame's base so it sways from the wick)
    tear = path([[0, -54], [13, -16], [0, 0], [-13, -16]],
                [[-3, 6], [3, -14], [7, 0], [0, 9]],
                [[3, 6], [0, 9], [-7, 0], [-3, -14]])
    flame_fill = radial((0, -12), (0, -56), [(0, 1, .98, .86), (.45, 1, .82, .4), (1, 1, .48, 0)])
    core = {"ty": "el", "p": static([0, -13]), "s": static([9, 18])}
    core_fill = {"ty": "fl", "c": static([1, 1, .95, 1]), "o": static(80), "r": 1}
    scale_keys = wobble(rng, [100, 100, 100], [8, 12, 0])
    rot_keys = wobble(rng, [0], [5])
    flame = layer(ind, 4, f"flame {ind}",
                  transform((cx, base_y), s=animated(scale_keys), r=animated(rot_keys)),
                  shapes=[group([core, core_fill]), group([tear, flame_fill])])
    # glow follows the flame's height so the light pulses with the flicker
    glow_scale = [(t, [v[1], v[1], 100]) for t, v in scale_keys]
    glow_op = [(t, [70 + (v[1] - 88) * 1.2]) for t, v in scale_keys]
    halo = {"ty": "el", "p": static([0, 0]), "s": static([150, 150])}
    halo_fill = radial((0, 0), (75, 0), [(0, 1, .8, .35), (1, 1, .63, .24)], [(0, .7), (1, 0)])
    glow = layer(ind + 1, 4, f"glow {ind}",
                 transform((cx, cy - 30), s=animated(glow_scale), o=animated(glow_op)),
                 shapes=[group([halo, halo_fill])])
    return flame, glow


def sparkle_layer(ind, rng, x, y, r, slot, slots):
    r *= 1.7
    q = 0.32 * r
    star = path([[0, -2 * r], [q, -q], [2 * r, 0], [q, q], [0, 2 * r], [-q, q], [-2 * r, 0], [-q, -q]],
                [[0, 0]] * 8, [[0, 0]] * 8)
    fill = {"ty": "fl", "c": static([1, .97, .85, 1]), "o": static(100), "r": 1}
    halo = {"ty": "el", "p": static([0, 0]), "s": static([3.2 * r, 3.2 * r])}
    halo_fill = radial((0, 0), (1.6 * r, 0), [(0, 1, .9, .55), (1, 1, .8, .4)], [(0, .8), (1, 0)])
    # twinkles spread evenly over the loop (each sparkle gets its own slot, plus jitter), sometimes a second one
    keys, rot = [(0, [0, 0, 100])], [(0, [0])]
    span = FRAMES - 22
    first = int(4 + slot * span / slots + rng.uniform(-2, 2))
    starts = [max(2, min(span, first))]
    second = (starts[0] + span // 2) % span + 2
    if rng.random() < 0.45 and abs(second - starts[0]) >= 22:
        starts = sorted(starts + [second])
    for s in starts:
        keys += [(s, [0, 0, 100]), (s + 7, [100, 100, 100]), (s + 16, [0, 0, 100])]
        rot += [(s, [0]), (s + 16, [90])]
    keys.append((FRAMES, [0, 0, 100]))
    rot.append((FRAMES, [90 if starts else 0]))
    return layer(ind, 4, f"sparkle {ind}", transform((x, y), s=animated(keys), r=animated(rot)),
                 shapes=[group([star, fill]), group([halo, halo_fill])])


def text_layer(ind, name, text, y, size, font, tracking, color, stroke=(.3, .03, .05)):
    doc = {"s": size, "f": font, "t": text, "j": 2, "tr": tracking, "lh": size * 1.2, "ls": 0,
           "fc": color, "sc": list(stroke), "sw": 2 if size > 40 else 0, "of": False}
    return layer(ind, 5, name, transform((TEXT_X, y)),
                 t={"d": {"k": [{"s": doc, "t": 0}]}, "p": {}, "m": {"g": 1, "a": static([0, 0])}, "a": []})


def fill(rgb, opacity=100):
    return {"ty": "fl", "c": static(list(rgb) + [1]), "o": static(opacity), "r": 1}


def stroke(rgb, width, opacity=100):
    return {"ty": "st", "c": static(list(rgb) + [1]), "o": static(opacity), "w": static(width), "lc": 2, "lj": 2}


def balloon_layers(ind, rng, balloons):
    """Balloons with strings, each bobbing and swaying at its own pace (first and last keys equal: seamless)."""
    out = []
    for (x, y, rgb) in balloons:
        body = {"ty": "el", "p": static([0, 0]), "s": static([52, 64])}
        shine = {"ty": "el", "p": static([-11, -14]), "s": static([12, 18])}
        knot = path([[-5, 34], [5, 34], [0, 28]], [[0, 0]] * 3, [[0, 0]] * 3)
        string = path([[0, 34], [0, 100]], [[0, 0], [-10, -20]], [[8, 20], [0, 0]], closed=False)
        half = FRAMES // 2 + rng.randint(-8, 8)
        bob = [(0, [x, y, 0]), (half, [x + rng.uniform(-4, 4), y - rng.uniform(8, 14), 0]), (FRAMES, [x, y, 0])]
        tilt = rng.uniform(3, 6) * rng.choice([-1, 1])
        rot = [(0, [0]), (half, [tilt]), (FRAMES, [0])]
        ks = transform((x, y), r=animated(rot))
        ks["p"] = animated(bob)
        out.append(layer(ind, 4, f"balloon {ind}", ks,
                         shapes=[group([shine, fill((1, 1, 1), 45)]), group([knot, fill(rgb)]),
                                 group([body, fill(rgb)]), group([string, stroke((1, .95, .98), 1.6)])]))
        ind += 1
    return out


def confetti_layers(ind, rng, pieces):
    """Small pieces that spin a full turn per loop and drift a little."""
    out = []
    cols = [(.96, .45, .71), (.98, .8, .08), (.22, .74, .97), (.29, .87, .5), (.98, .57, .24)]
    for n, (x, y) in enumerate(pieces):
        rect = {"ty": "rc", "p": static([0, 0]), "s": static([7, 13]), "r": static(1.5)}
        turn = rng.choice([-360, 360])
        ks = transform((x, y), r=animated([(0, [0]), (FRAMES, [turn])], ease=(0, 1)))
        ks["p"] = animated(wobble(rng, [x, y, 0], [5, 7, 0], step=(18, 30)))
        out.append(layer(ind, 4, f"confetti {ind}", ks, shapes=[group([rect, fill(cols[n % len(cols)])])]))
        ind += 1
    return out


def snow_layers(ind, rng, flakes):
    """Six-armed snowflakes that turn 60 degrees per loop (looks the same: seamless) and drift."""
    out = []
    for (x, y, r) in flakes:
        arms = []
        for k in range(3):
            a = math.pi * k / 3
            dx, dy = math.cos(a) * r, math.sin(a) * r
            arms.append(path([[-dx, -dy], [dx, dy]], [[0, 0]] * 2, [[0, 0]] * 2, closed=False))
        ks = transform((x, y), r=animated([(0, [0]), (FRAMES, [60])], ease=(0, 1)),
                       o=static(85))
        ks["p"] = animated(wobble(rng, [x, y, 0], [4, 5, 0], step=(20, 30)))
        out.append(layer(ind, 4, f"snow {ind}", ks, shapes=[group(arms + [stroke((1, 1, 1), 2.2)])]))
        ind += 1
    return out


def twinkle_layers(ind, rng, lights):
    """Small coloured lights whose glow pulses out of step."""
    out = []
    for (x, y, rgb) in lights:
        dot = {"ty": "el", "p": static([0, 0]), "s": static([9, 9])}
        halo = {"ty": "el", "p": static([0, 0]), "s": static([26, 26])}
        halo_fill = radial((0, 0), (13, 0), [(0,) + tuple(rgb), (1,) + tuple(rgb)], [(0, .8), (1, 0)])
        op = [(t, [max(20, min(100, v[0]))]) for t, v in wobble(rng, [70], [30], step=(8, 16))]
        out.append(layer(ind, 4, f"twinkle {ind}", transform((x, y), o=animated(op)),
                         shapes=[group([dot, fill(rgb)]), group([halo, halo_fill])]))
        ind += 1
    return out


# Each design: text colours (line 1, line 2, line 1 outline) and its moving parts. Positions are in the template.
DESIGNS = {
    "diwali": {"name": "Diwali banner", "line1": [1, .82, .4], "line2": [1, .91, .76], "outline": (.3, .03, .05)},
    "simple": {"name": "Simple banner", "line1": [1, .88, .55], "line2": [.93, .91, 1], "outline": (.1, .08, .3)},
    "birthday": {"name": "Birthday banner", "line1": [1, 1, 1], "line2": [1, .9, .97], "outline": (.45, .05, .4)},
    "christmas": {"name": "Christmas banner", "line1": [1, .85, .35], "line2": [1, 1, 1], "outline": (.02, .2, .08)},
}
BALLOONS = [(70, 120, (.96, .3, .55)), (130, 85, (.98, .78, .1)), (1072, 92, (.25, .7, .97)),
            (1132, 128, (.6, .45, .98)), (100, 200, (.29, .85, .5)), (1105, 205, (.98, .55, .2))]
CONFETTI = [(260, 300), (360, 312), (470, 298), (735, 310), (840, 300), (945, 314), (175, 280), (1025, 282),
            (300, 30), (900, 34), (560, 300), (650, 315)]
SNOW = [(70, 120, 11), (130, 210, 8), (1080, 110, 10), (1135, 215, 9), (60, 285, 7), (1145, 290, 7),
        (300, 302, 6), (900, 305, 6)]


def christmas_lights():
    # On the garland's baubles (see banner-art.html): the curve from (170,66) via (600,98) to (1030,66).
    cols = [(1, .3, .3), (1, .85, .3), (.6, 1, .6), (.6, .8, 1)]
    out = []
    for i in range(1, 24):
        t = i / 24
        x = (1 - t) ** 2 * 170 + 2 * (1 - t) * t * 600 + t * t * 1030
        y = (1 - t) ** 2 * 66 + 2 * (1 - t) * t * 98 + t * t * 66
        out.append((x, y + 4, cols[i % len(cols)]))
    return out


def build(design, art_path, line1, line2, seed=7):
    spec = DESIGNS[design]
    rng = random.Random(seed)
    art = base64.b64encode(open(art_path, "rb").read()).decode()
    layers, ind = [], 1
    order = list(range(len(SPARKLES)))
    rng.shuffle(order)                         # neighbouring sparkles don't twinkle one after another
    for n, (x, y, r) in enumerate(SPARKLES):
        layers.append(sparkle_layer(ind, rng, x, y, r, order[n], len(SPARKLES))); ind += 1
    layers.append(text_layer(ind, "LINE_1", line1, LINE1_Y, 64, "Inter-Bold", 20, spec["line1"], spec["outline"]))
    ind += 1
    layers.append(text_layer(ind, "LINE_2", line2, LINE2_Y, 26, "Inter-Regular", 160, spec["line2"])); ind += 1
    if design == "diwali":
        flames, glows = [], []
        for (cx, cy) in DIYAS:
            f, g = flame_layers(ind, rng, cx, cy); ind += 2
            flames.append(f); glows.append(g)
        layers += flames + glows
    elif design == "birthday":
        extra = balloon_layers(ind, rng, BALLOONS) + confetti_layers(ind + len(BALLOONS), rng, CONFETTI)
        layers += extra; ind += len(extra)
    elif design == "christmas":
        lights = christmas_lights()
        extra = twinkle_layers(ind, rng, lights) + snow_layers(ind + len(lights), rng, SNOW)
        layers += extra; ind += len(extra)
    layers.append(layer(ind, 2, "art", transform((0, 0)), refId="art"))
    return {
        "v": "5.7.4", "fr": FPS, "ip": 0, "op": FRAMES, "w": W, "h": H, "nm": spec["name"], "ddd": 0,
        "meta": {"template": "tvhome-banner-1200x340", "text_slots": ["LINE_1", "LINE_2"],
                 "text_safe_zone": [300, 110, 900, 240]},
        "assets": [{"id": "art", "w": W, "h": H, "u": "", "p": "data:image/webp;base64," + art, "e": 1}],
        "fonts": {"list": [{"fName": "Inter-Bold", "fFamily": "Inter", "fStyle": "Bold", "fWeight": "700", "ascent": 72.7},
                           {"fName": "Inter-Regular", "fFamily": "Inter", "fStyle": "Regular", "fWeight": "400", "ascent": 72.7}]},
        "layers": layers,
    }


DEFAULT_TEXT = {"diwali": ("Happy Diwali", "WISHING YOU LIGHT AND JOY"),
                "simple": ("Welcome home", "HAVE A LOVELY DAY"),
                "birthday": ("Happy Birthday!", "HAVE A WONDERFUL DAY"),
                "christmas": ("Merry Christmas", "PEACE AND JOY TO ALL")}

if __name__ == "__main__":
    design, art, out = sys.argv[1], sys.argv[2], sys.argv[3]
    line1 = sys.argv[4] if len(sys.argv) > 4 else DEFAULT_TEXT[design][0]
    line2 = sys.argv[5] if len(sys.argv) > 5 else DEFAULT_TEXT[design][1]
    json.dump(build(design, art, line1, line2), open(out, "w"), separators=(",", ":"))
    print("wrote", out)
