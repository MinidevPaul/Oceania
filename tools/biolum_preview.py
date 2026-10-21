import argparse
import math
import os
import pathlib
import random

from PIL import Image, ImageDraw, ImageFont

REPO = pathlib.Path(__file__).resolve().parent.parent

TUNING = {
    "SKY_DARKEN_MIN": 6,
    "SKY_DARKEN_FULL": 10,

    "DEPTH_FULL": 3.0,
    "DEPTH_FADE": 10.0,
    "ABOVE_SURFACE": 1.0,

    "MIN_SPEED": 0.035,
    "FULL_SPEED": 0.30,
    "RATE_MIN": 0.60,
    "RATE_MAX": 3.20,
    "SIZE_BONUS": 1.6,

    "LIFE_MIN": 40,
    "LIFE_MAX": 90,
    "SIZE_MIN": 0.040,
    "SIZE_MAX": 0.090,
    "DRIFT": 0.022,
    "SPREAD": 0.85,
    "BEHIND": 0.35,
    "FRICTION": 0.93,
    "ALPHA_PEAK": 0.95,
    "FADE_IN": 0.12,
}

COLOR_CORE = (225, 255, 235)
COLOR_BODY = (80, 255, 140)
COLOR_TAIL = (20, 140, 80)

SPRITE_SIZE = 16

ALPHA_DISCARD = 0.1

def make_sprite(size=SPRITE_SIZE):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    radius = size / 2.0
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c) / radius
            if d >= 1.0:
                a = 0.0
            else:
                core = math.exp(-(d * 2.4) ** 2)
                halo = math.exp(-(d * 1.15) ** 2) * 0.5
                a = (core + halo) / 1.5 * (1.0 - d ** 3)
            px[x, y] = (255, 255, 255, int(round(a * 255)))
    return img

class Particle:
    __slots__ = ("x", "y", "z", "vx", "vy", "vz", "age", "life", "size", "seed")

    def __init__(self, x, y, z, vx, vy, vz, life, size, seed):
        self.x, self.y, self.z = x, y, z
        self.vx, self.vy, self.vz = vx, vy, vz
        self.age = 0
        self.life = life
        self.size = size
        self.seed = seed

    def tick(self, friction):
        self.x += self.vx
        self.y += self.vy
        self.z += self.vz
        self.vx *= friction
        self.vy *= friction
        self.vz *= friction
        self.age += 1

    @property
    def alive(self):
        return self.age < self.life

    def alpha(self, t):
        f = self.age / self.life
        if f < t["FADE_IN"]:
            return (f / t["FADE_IN"]) * t["ALPHA_PEAK"]
        g = (f - t["FADE_IN"]) / (1.0 - t["FADE_IN"])
        return ALPHA_DISCARD + (t["ALPHA_PEAK"] - ALPHA_DISCARD) * (1.0 - g) ** 1.6

    def color(self):
        f = self.age / self.life
        if f < 0.25:
            k = f / 0.25
            return tuple(COLOR_CORE[i] + (COLOR_BODY[i] - COLOR_CORE[i]) * k for i in range(3))
        k = (f - 0.25) / 0.75
        return tuple(COLOR_BODY[i] + (COLOR_TAIL[i] - COLOR_BODY[i]) * k for i in range(3))

def depth_strength(depth, t):
    if depth <= -t["ABOVE_SURFACE"]:
        return 0.0
    if depth <= t["DEPTH_FULL"]:
        return 1.0
    if depth >= t["DEPTH_FADE"]:
        return 0.0
    return 1.0 - (depth - t["DEPTH_FULL"]) / (t["DEPTH_FADE"] - t["DEPTH_FULL"])

def sky_strength(sky_darken, t):
    if sky_darken <= t["SKY_DARKEN_MIN"]:
        return 0.0
    if sky_darken >= t["SKY_DARKEN_FULL"]:
        return 1.0
    return (sky_darken - t["SKY_DARKEN_MIN"]) / (t["SKY_DARKEN_FULL"] - t["SKY_DARKEN_MIN"])

def emit_rate(speed, width, t, strength=1.0):
    if speed < t["MIN_SPEED"] or strength <= 0.0:
        return 0.0
    k = min(1.0, (speed - t["MIN_SPEED"]) / max(1e-6, t["FULL_SPEED"] - t["MIN_SPEED"]))
    rate = t["RATE_MIN"] + (t["RATE_MAX"] - t["RATE_MIN"]) * k
    rate *= 1.0 + (t["SIZE_BONUS"] - 1.0) * min(1.0, (width - 1.0) / 3.0)
    return rate * strength

def simulate(speed, width, ticks, t, seed=7, depth=0.0, heading=0.0, bob=0.0, sky_darken=11):
    rng = random.Random(seed)
    live = []
    x = z = 0.0
    y = -depth
    carry = 0.0
    for tick in range(ticks):
        ang = heading * tick / max(1, ticks)
        dx = math.cos(ang) * speed
        dz = math.sin(ang) * speed
        x += dx
        z += dz
        if bob:
            y = -depth + math.sin(tick * 0.09) * bob

        for p in live:
            p.tick(t["FRICTION"])
        live = [p for p in live if p.alive]

        strength = depth_strength(depth, t) * sky_strength(sky_darken, t)
        carry += emit_rate(speed, width, t, strength)
        n = int(carry)
        carry -= n
        for _ in range(n):
            back = t["BEHIND"]
            across = (rng.random() - 0.5) * width * t["SPREAD"]
            nx, nz = -math.sin(ang), math.cos(ang)
            px = x - dx / max(1e-6, speed) * back + nx * across
            pz = z - dz / max(1e-6, speed) * back + nz * across
            out = t["DRIFT"] * (1.0 if across >= 0 else -1.0) * (0.5 + rng.random())
            live.append(Particle(
                px, y + (rng.random() - 0.5) * 0.12, pz,
                nx * out - dx * 0.05 + (rng.random() - 0.5) * t["DRIFT"],
                (rng.random() - 0.5) * t["DRIFT"] * 0.4,
                nz * out - dz * 0.05 + (rng.random() - 0.5) * t["DRIFT"],
                rng.randint(t["LIFE_MIN"], t["LIFE_MAX"]),
                t["SIZE_MIN"] + rng.random() * (t["SIZE_MAX"] - t["SIZE_MIN"]),
                rng.random()))
    return live

def render(live, sprite, t, plane, px_per_block, size, bg=(5, 9, 16)):
    w, h = size
    img = Image.new("RGB", (w, h), bg)

    if plane == "xz":
        coords = [(p.x, p.z) for p in live]
    else:
        coords = [(p.x, -p.y) for p in live]
    if not coords:
        return img
    minu = min(c[0] for c in coords)
    maxv = max(c[1] for c in coords)
    minv = min(c[1] for c in coords)
    centre_v = (maxv + minv) / 2.0

    order = sorted(range(len(live)), key=lambda i: -live[i].age)

    for i in order:
        p = live[i]
        u, v = coords[i]
        cx = (u - minu) * px_per_block + 24
        cy = h / 2 + (v - centre_v) * px_per_block
        a = p.alpha(t)
        if a <= 0.004:
            continue
        r = max(1, int(p.size * 2 * px_per_block * 3.2))
        stamp = sprite.resize((r * 2, r * 2), Image.LANCZOS)
        col = p.color()
        tint = Image.new("RGBA", stamp.size, (int(col[0]), int(col[1]), int(col[2]), 0))
        tint.putalpha(stamp.getchannel("A").point(
            lambda q: int(q * a) if (q / 255.0) * a >= ALPHA_DISCARD else 0))
        img.paste(tint, (int(cx - r), int(cy - r)), tint)
    return img

def label(img, lines, colour=(150, 170, 190)):
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("arial.ttf", 13)
        small = ImageFont.truetype("arial.ttf", 11)
    except OSError:
        font = small = ImageFont.load_default()
    y = 6
    for i, line in enumerate(lines):
        d.text((8, y), line, fill=(225, 240, 255) if i == 0 else colour, font=font if i == 0 else small)
        y += 18 if i == 0 else 14
    return img

CASES = [
    ("raft under full thrust",     0.35,  3.0,   90,    0.0,   0.0,     0.0),
    ("raft turning",               0.30,  3.0,   90,    0.0,   1.1,     0.0),
    ("vanilla boat",               0.28,  1.4,   90,    0.0,   0.0,     0.0),
    ("player swimming at surface", 0.11,  0.8,   90,    0.3,   0.0,     0.05),
    ("player sprint-swimming",     0.21,  0.8,   90,    0.4,   0.4,     0.05),
    ("whale cruising just under",  0.12,  4.0,   120,   2.5,   0.3,     0.35),
    ("shark charging",             0.42,  1.6,   70,    1.2,   0.0,     0.1),
    ("swimmer 6 blocks down",      0.10,  0.8,   100,   6.0,   0.2,     0.05),
    ("shark at 11.9 (flight case)",0.30,  1.6,   90,    11.9,  0.3,     0.1),
    ("whale at 18 blocks (gone)",  0.12,  4.0,   120,   18.0,  0.0,     0.3),
]

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    default_out = os.environ.get("OCEANIA_PREVIEW_OUT") or str(REPO / "build" / "biolum-preview")
    ap.add_argument("--out", default=default_out, help="directory for the PNGs")
    ap.add_argument("--install", action="store_true",
                    help="also write the sprite, its particle json and the generated tuning Java into src/")
    args = ap.parse_args()

    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    sprite = make_sprite()

    mag = 14
    sheet = Image.new("RGB", (SPRITE_SIZE * mag + 40 + 260, SPRITE_SIZE * mag + 70), (5, 9, 16))
    big = Image.new("RGBA", (SPRITE_SIZE * mag, SPRITE_SIZE * mag), (0, 0, 0, 0))
    src = sprite.resize((SPRITE_SIZE * mag, SPRITE_SIZE * mag), Image.NEAREST)
    tint = Image.new("RGBA", src.size, COLOR_BODY + (0,))
    tint.putalpha(src.getchannel("A"))
    big.alpha_composite(tint)
    sheet.paste(big, (20, 50), big)
    dx = SPRITE_SIZE * mag + 40
    for j, (name, col) in enumerate((("core", COLOR_CORE), ("body", COLOR_BODY), ("tail", COLOR_TAIL))):
        for k, s in enumerate((10, 18, 30, 48)):
            st = sprite.resize((s, s), Image.LANCZOS)
            ti = Image.new("RGBA", st.size, col + (0,))
            ti.putalpha(st.getchannel("A"))
            sheet.paste(ti, (dx + k * 56 + (48 - s) // 2, 60 + j * 60 + (48 - s) // 2), ti)
    label(sheet, [f"oceania:biolum sprite, {SPRITE_SIZE}x{SPRITE_SIZE}, alpha only (tinted in code)",
                  "left: magnified 14x.  right: the three colour stops at the four sizes the game uses",
                  "HONEST: the sprite. NOT a prediction of how it looks in the game."])
    sheet.save(out / "biolum-00-sprite.png")

    written = [out / "biolum-00-sprite.png"]
    for i, (name, speed, width, ticks, depth, heading, bob) in enumerate(CASES, start=1):
        live = simulate(speed, width, ticks, TUNING, seed=11 + i, depth=depth, heading=heading, bob=bob)
        strength = depth_strength(depth, TUNING) * sky_strength(11, TUNING)
        rate = emit_rate(speed, width, TUNING, strength)
        plan = render(live, sprite, TUNING, "xz", 34, (1180, 420))
        label(plan, [f"{name} - PLAN VIEW (looking straight down at the water)",
                     f"{speed:.2f} blocks/tick ({speed * 20:.1f} b/s), body {width:.1f} wide, "
                     f"{depth:.1f} blocks below the surface, {ticks} ticks of travel",
                     f"depth strength {strength:.2f}  ->  {rate:.2f} particles/tick  ->  {len(live)} alive in the trail. "
                     f"Orthographic, 34 px per block. No perspective, no water, no fog."])
        plan.save(out / f"biolum-{i:02d}-{name.replace(' ', '-')}-plan.png")
        side = render(live, sprite, TUNING, "xy", 34, (1180, 300))
        label(side, [f"{name} - SIDE VIEW (the waterline is the horizontal centre)",
                     "shows how tightly the trail hugs the surface and how much it sinks"])
        side.save(out / f"biolum-{i:02d}-{name.replace(' ', '-')}-side.png")
        written += [out / f"biolum-{i:02d}-{name.replace(' ', '-')}-plan.png",
                    out / f"biolum-{i:02d}-{name.replace(' ', '-')}-side.png"]

    plans = [p for p in written if p.name.endswith("-plan.png")]
    thumbs = [Image.open(p).resize((590, 210), Image.LANCZOS) for p in plans]
    cols, rows = 2, (len(thumbs) + 1) // 2
    contact = Image.new("RGB", (cols * 590, rows * 210), (5, 9, 16))
    for j, th in enumerate(thumbs):
        contact.paste(th, ((j % cols) * 590, (j // cols) * 210))
    contact.save(out / "biolum-contact-sheet.png")
    written.append(out / "biolum-contact-sheet.png")

    print(f"wrote {len(written)} PNGs to {out}")
    for p in written:
        print("  " + p.name)

    print()
    print("SCOPE: these show the trail's GEOMETRY and density from the real emitter arithmetic, orthographically.")
    print("  They do not show perspective, the water surface, the mod's depth fog, or the vanilla particle")
    print("  shader, so they cannot tell you whether it looks good - only whether it is a trail.")

    if args.install:
        install(sprite)

def install(sprite):
    tex = REPO / "src/main/resources/assets/oceania/textures/particle"
    tex.mkdir(parents=True, exist_ok=True)
    sprite.save(tex / "biolum.png")

    pj = REPO / "src/main/resources/assets/oceania/particles"
    pj.mkdir(parents=True, exist_ok=True)
    (pj / "biolum.json").write_text('{\n  "textures": [\n    "oceania:biolum"\n  ]\n}\n', encoding="utf-8")

    java = REPO / "src/client/java/com/oceania/client/biolum/SurfaceGlowTuning.java"
    java.parent.mkdir(parents=True, exist_ok=True)
    java.write_text(render_java(), encoding="utf-8")

    print()
    print("installed:")
    print("  src/main/resources/assets/oceania/textures/particle/biolum.png")
    print("  src/main/resources/assets/oceania/particles/biolum.json")
    print("  src/client/java/com/oceania/client/biolum/SurfaceGlowTuning.java  (GENERATED - do not hand-edit)")

def render_java():
    t = TUNING
    lines = [
        "package com.oceania.client.biolum;",
        "",
        "public final class SurfaceGlowTuning {",
        "",
        "\tprivate SurfaceGlowTuning() {",
        "\t}",
        "",
        f"\tpublic static final int SKY_DARKEN_MIN = {t['SKY_DARKEN_MIN']};",
        f"\tpublic static final int SKY_DARKEN_FULL = {t['SKY_DARKEN_FULL']};",
        "",
        f"\tpublic static final double DEPTH_FULL = {t['DEPTH_FULL']};",
        f"\tpublic static final double DEPTH_FADE = {t['DEPTH_FADE']};",
        f"\tpublic static final double ABOVE_SURFACE = {t['ABOVE_SURFACE']};",
        "",
        f"\tpublic static final double MIN_SPEED = {t['MIN_SPEED']};",
        f"\tpublic static final double FULL_SPEED = {t['FULL_SPEED']};",
        f"\tpublic static final double RATE_MIN = {t['RATE_MIN']};",
        f"\tpublic static final double RATE_MAX = {t['RATE_MAX']};",
        f"\tpublic static final double SIZE_BONUS = {t['SIZE_BONUS']};",
        "",
        f"\tpublic static final int LIFE_MIN = {t['LIFE_MIN']};",
        f"\tpublic static final int LIFE_MAX = {t['LIFE_MAX']};",
        f"\tpublic static final double SIZE_MIN = {t['SIZE_MIN']};",
        f"\tpublic static final double SIZE_MAX = {t['SIZE_MAX']};",
        f"\tpublic static final double DRIFT = {t['DRIFT']};",
        f"\tpublic static final double SPREAD = {t['SPREAD']};",
        f"\tpublic static final double BEHIND = {t['BEHIND']};",
        f"\tpublic static final double FRICTION = {t['FRICTION']};",
        f"\tpublic static final double ALPHA_PEAK = {t['ALPHA_PEAK']};",
        f"\tpublic static final double FADE_IN = {t['FADE_IN']};",
        f"\tpublic static final double ALPHA_DISCARD = {ALPHA_DISCARD};",
        "",
        f"\tpublic static final int COLOR_CORE = 0x{COLOR_CORE[0]:02X}{COLOR_CORE[1]:02X}{COLOR_CORE[2]:02X};",
        f"\tpublic static final int COLOR_BODY = 0x{COLOR_BODY[0]:02X}{COLOR_BODY[1]:02X}{COLOR_BODY[2]:02X};",
        f"\tpublic static final int COLOR_TAIL = 0x{COLOR_TAIL[0]:02X}{COLOR_TAIL[1]:02X}{COLOR_TAIL[2]:02X};",
        "}",
        "",
    ]
    return "\n".join(lines)

if __name__ == "__main__":
    main()
