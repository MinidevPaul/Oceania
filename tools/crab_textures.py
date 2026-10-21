import io
import math
import os

from PIL import Image, ImageDraw

from boxpack import Rnd, check_pack, filler, hexc, mix, pack, regions, shade, utilisation

GROUND_Y = 24.0

COCONUT = dict(
    carapace=(7, 4, 7),
    dome=(5, 2, 5),
    abdomen=(6, 4, 5),
    eye=(1, 2, 1),
    antenna=(1, 1, 3),
    claw_arm=(3, 2, 2),
    claw=(4, 3, 4),
    leg_up=(4, 2, 2),
    leg_lo=(6, 1, 1),
)

REEF = dict(
    carapace=(6, 3, 6),
    dome=(4, 1, 4),
    abdomen=(5, 2, 4),
    eye=(1, 2, 1),
    antenna=(1, 1, 2),
    claw_arm=(2, 2, 2),
    claw=(3, 2, 3),
    leg_up=(3, 2, 2),
    leg_lo=(4, 1, 1),
)

KING = dict(
    carapace=(9, 4, 10),
    rostrum=(2, 1, 4),
    spine=(1, 1, 1),
    eye=(1, 2, 1),
    claw_arm=(10, 2, 2),
    claw=(5, 3, 2),
    leg_up=(11, 2, 2),
    leg_lo=(14, 1, 1),
)

CC = dict(
    body_y=19.6,
    hip_x=3.0, hip_y=0.0,
    leg_z=(-1.5, 1.0, 3.5),
    leg_fan=(0.34, 0.06, -0.30),
    leg_up_rise=-0.22,
    leg_lo_drop=1.30,
    claw_x=3.0, claw_y=1.4, claw_z=-2.8,
    claw_arm_yaw=0.28, claw_arm_pitch=0.05,
    claw_yaw=-0.22, claw_pitch=0.0,
    claw_small_scale=0.62,
    eye_x=1.5, eye_y=-2.2, eye_z=-3.0,
    antenna_x=2.2, antenna_y=-1.6, antenna_z=-3.4,
    antenna_yaw=1.00, antenna_pitch=-0.08,
    abdomen_y=0.6, abdomen_z=4.6,
)

KG = dict(
    body_y=15.0,
    hip_x=4.0, hip_y=0.5,
    leg_z=(-2.5, 0.5, 3.5),
    leg_fan=(0.42, 0.0, -0.42),
    leg_up_rise=-0.30,
    leg_lo_drop=1.30,
    claw_x=3.2, claw_y=0.8, claw_z=-5.0,
    claw_arm_yaw=0.78, claw_arm_pitch=-0.30,
    claw_yaw=-0.25, claw_pitch=0.45,
    claw_small_scale=0.70,
    eye_x=1.5, eye_y=-2.2, eye_z=-4.4,
    rostrum_y=-1.6, rostrum_z=-4.8,
    spine_x=(3.0, 3.8, 2.6),
    spine_z=(-3.2, 0.5, 3.8),
)

RF = dict(
    body_y=21.05,
    hip_x=2.2, hip_y=0.2,
    leg_z=(-2.0, -0.6, 0.8, 2.2),
    leg_fan=(0.36, 0.12, -0.12, -0.36),
    leg_up_rise=-0.25,
    leg_lo_drop=1.30,
    claw_x=2.2, claw_y=0.8, claw_z=-2.6,
    claw_arm_yaw=0.30, claw_arm_pitch=0.08,
    claw_yaw=-0.26, claw_pitch=0.0,
    claw_small_scale=0.88,
    eye_x=1.2, eye_y=-1.8, eye_z=-2.6,
    antenna_x=1.6, antenna_y=-1.0, antenna_z=-2.8,
    antenna_yaw=0.90, antenna_pitch=-0.10,
    abdomen_y=0.4, abdomen_z=3.8,
)

GAIT_TICKS_COCONUT = 14
GAIT_TICKS_KING = 16

SWING_COCONUT, LIFT_COCONUT = 0.26, 0.10
SWING_REEF, LIFT_REEF = 0.34, 0.12
GAIT_TICKS_REEF = 8
SWING_KING, LIFT_KING = 0.22, 0.09

PROP = {"coconut": CC, "reef": RF, "king": KG}
DIMS = {"coconut": COCONUT, "reef": REEF, "king": KING}
SHEET = {"coconut": (64, 32), "reef": (64, 32), "king": (64, 48)}

def span(kind):
    p, d = PROP[kind], DIMS[kind]
    up = p["leg_up_rise"]
    lo = up + p["leg_lo_drop"]
    reach = p["hip_x"] + d["leg_up"][0] * math.cos(up) + d["leg_lo"][0] * math.cos(lo)
    return 2 * reach, 2 * reach / 16.0

def top_y(kind):
    p, d = PROP[kind], DIMS[kind]
    shell = p["body_y"] - d["carapace"][1] / 2.0
    if "dome" in d:
        shell -= d["dome"][1]
    elif "spine" in d:
        shell -= d["spine"][1]
    eye = p["body_y"] + p["eye_y"] - d["eye"][1]
    return min(shell, eye)

def height(kind):
    h = GROUND_Y - top_y(kind)
    return h, h / 16.0

def clearance(kind):
    p, d = PROP[kind], DIMS[kind]
    return GROUND_Y - (p["body_y"] + d["carapace"][1] / 2.0)

def foot_y(kind):
    p, d = PROP[kind], DIMS[kind]
    up = p["leg_up_rise"]
    lo = up + p["leg_lo_drop"]
    return (p["body_y"] + p["hip_y"] + d["leg_up"][0] * math.sin(up)
            + d["leg_lo"][0] * math.sin(lo))

BLUE = dict(
    shell="#43518C", shell_hi="#6072AC", shell_lo="#2B3660",
    mottle="#7E8EC2", belly="#C9C6D4", leg="#3C4A7E",
    joint="#232C4E", tip="#D9D4C6", eye="#14171A", stalk="#6E79A8",
)
ORANGE = dict(
    shell="#C65A24", shell_hi="#E27E3E", shell_lo="#8E3E18",
    mottle="#EAA866", belly="#E8DCC4", leg="#B5501F",
    joint="#78320F", tip="#F2E7CF", eye="#14171A", stalk="#D4794A",
)
GREEN = dict(
    shell="#4E7A3C", shell_hi="#6B9A52", shell_lo="#35562A",
    mottle="#93B86A", belly="#DCE3C8", leg="#466F36",
    joint="#2A4420", tip="#E8EBD4", eye="#14171A", stalk="#6E9455",
)
KING_PAL = dict(
    shell="#B03A22", shell_hi="#D4603A", shell_lo="#7A2715",
    mottle="#E08A5A", belly="#E6D8BE", leg="#9C3420",
    joint="#68220F", tip="#EFE0C4", eye="#14171A", stalk="#C05A3C",
)

def _sub(d, rect, fx0, fy0, fx1, fy1, col):
    u0, v0, u1, v1 = rect
    if v1 < v0:
        v0, v1 = v1, v0
    w, h = u1 - u0, v1 - v0
    x0, x1 = u0 + int(round(fx0 * w)), u0 + int(round(fx1 * w))
    y0, y1 = v0 + int(round(fy0 * h)), v0 + int(round(fy1 * h))
    if x1 > x0 and y1 > y0:
        d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=col)

def paint_box(d, i, j, k, l, m, role, pal, seed=7):
    fill = filler(d)
    r = regions(i, j, k, l, m)
    shell, hi, lo = hexc(pal["shell"]), hexc(pal["shell_hi"]), hexc(pal["shell_lo"])
    mot, belly = hexc(pal["mottle"]), hexc(pal["belly"])
    leg, joint, tip = hexc(pal["leg"]), hexc(pal["joint"]), hexc(pal["tip"])
    eye, stalk = hexc(pal["eye"]), hexc(pal["stalk"])

    if role in ("carapace", "dome", "abdomen"):
        for key in ("west", "north", "east", "south"):
            fill(r[key], shell)
        fill(r["back"], hi if role == "dome" else shell)
        fill(r["belly"], belly if role != "dome" else shell)
        for key in ("west", "north", "east", "south"):
            _sub(d, r[key], 0.0, 0.62, 1.0, 1.0, lo)
        rnd = Rnd(seed + len(role))
        for _ in range(7 if role == "carapace" else 4):
            cx, cy = rnd.frac(), rnd.frac()
            w, h = 0.09 + 0.15 * rnd.frac(), 0.09 + 0.15 * rnd.frac()
            _sub(d, r["back"], max(0.0, cx - w), max(0.0, cy - h),
                 min(1.0, cx + w), min(1.0, cy + h), mot)
        if role == "carapace":
            _sub(d, r["back"], 0.42, 0.0, 0.58, 1.0, mix(shell, hi, 0.55))

    elif role == "rostrum":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], shell)
        fill(r["north"], tip)
        for key in ("west", "east", "back"):
            _sub(d, r[key], 0.7, 0.0, 1.0, 1.0, tip)
        _sub(d, r["belly"], 0.0, 0.0, 1.0, 1.0, lo)

    elif role == "spine":
        for key in ("west", "north", "east", "south"):
            fill(r[key], shell)
            _sub(d, r[key], 0.0, 0.0, 1.0, 0.45, tip)
        fill(r["back"], tip)
        fill(r["belly"], lo)

    elif role == "eye":
        for key in ("west", "north", "east", "south"):
            fill(r[key], stalk)
            _sub(d, r[key], 0.0, 0.0, 1.0, 0.5, eye)
        fill(r["back"], eye)
        fill(r["belly"], stalk)

    elif role == "antenna":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], joint)
        for key in ("back", "belly", "west", "east"):
            _sub(d, r[key], 0.45, 0.0, 1.0, 1.0, stalk)

    elif role == "claw_arm":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], leg)
        for key in ("west", "east", "back", "belly"):
            _sub(d, r[key], 0.0, 0.0, 0.22, 1.0, joint)

    elif role == "claw":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], shell)
        for key in ("west", "east", "back", "belly", "north"):
            _sub(d, r[key], 0.68, 0.0, 1.0, 1.0, tip)
        for key in ("west", "east"):
            _sub(d, r[key], 0.50, 0.42, 1.0, 0.58, joint)
        _sub(d, r["north"], 0.0, 0.42, 1.0, 0.58, joint)

    elif role in ("leg_up", "leg_lo"):
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], leg)
        for key in ("west", "east", "back", "belly"):
            _sub(d, r[key], 0.0, 0.0, 0.18, 1.0, joint)
            _sub(d, r[key], 0.86, 0.0, 1.0, 1.0, joint if role == "leg_up" else tip)
        _sub(d, r["belly"], 0.18, 0.0, 0.86, 1.0, shade(leg, 0.82))

    else:
        raise ValueError("unpainted role %s" % role)

def boxes(kind):
    return [(n, d[0], d[1], d[2], n) for n, d in DIMS[kind].items()]

def build_sheet(kind, pal):
    bx = boxes(kind)
    atlas_w, atlas_h = SHEET[kind]
    placed, used = pack(bx, atlas_w)
    check_pack(placed, bx, atlas_w, atlas_h)
    img = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for name, k, l, m, role in bx:
        i, j = placed[name]
        paint_box(d, i, j, k, l, m, role, pal)
    return img, placed, atlas_w, atlas_h, bx, used

def _side(side):
    return "r" if side > 0 else "l"

def _leg(uv, kind, side, idx, swing, lift):
    from model_preview import Cube, Part
    p, d = PROP[kind], DIMS[kind]
    uk, ul, um = d["leg_up"]
    lk, ll, lm = d["leg_lo"]
    mirror = side < 0
    up = Part("leg_up_%s%d" % (_side(side), idx),
              pivot=(side * p["hip_x"], p["hip_y"], p["leg_z"][idx]),
              rot=(0.0, side * (p["leg_fan"][idx] + swing),
                   side * (p["leg_up_rise"] - lift)),
              cubes=[Cube(uv["leg_up"], (0.0 if side > 0 else -uk, -ul / 2.0, -um / 2.0),
                          (uk, ul, um), mirror=mirror)])
    up.add(Part("leg_lo_%s%d" % (_side(side), idx),
                pivot=(side * uk, 0.0, 0.0),
                rot=(0.0, 0.0, side * p["leg_lo_drop"]),
                cubes=[Cube(uv["leg_lo"], (0.0 if side > 0 else -lk, -ll / 2.0, -lm / 2.0),
                            (lk, ll, lm), mirror=mirror)]))
    return up

def _claw(uv, kind, side, small):
    from model_preview import Cube, Part
    p, d = PROP[kind], DIMS[kind]
    ak, al, am = d["claw_arm"]
    ck, cl, cm = d["claw"]
    mirror = side < 0
    s = p["claw_small_scale"] if small else 1.0
    arm = Part("claw_arm_%s" % _side(side),
               pivot=(side * p["claw_x"], p["claw_y"], p["claw_z"]),
               rot=(0.0, side * p["claw_arm_yaw"], side * p["claw_arm_pitch"]),
               cubes=[Cube(uv["claw_arm"], (0.0 if side > 0 else -ak, -al / 2.0, -am / 2.0),
                           (ak, al, am), mirror=mirror)],
               scale=(s, s, s))
    arm.add(Part("claw_%s" % _side(side),
                 pivot=(side * ak, 0.0, 0.0),
                 rot=(0.0, side * p["claw_yaw"], side * p["claw_pitch"]),
                 cubes=[Cube(uv["claw"], (0.0 if side > 0 else -ck, -cl / 2.0, -cm),
                             (ck, cl, cm), mirror=mirror)]))
    return arm

def _gait(kind, idx, side, t):
    if t is None:
        return 0.0, 0.0
    swing_a, lift_a = {"coconut": (SWING_COCONUT, LIFT_COCONUT),
                       "reef": (SWING_REEF, LIFT_REEF),
                       "king": (SWING_KING, LIFT_KING)}[kind]
    ph = t + (0.5 if ((idx % 2 == 0) == (side < 0)) else 0.0)
    return (swing_a * math.sin(2 * math.pi * ph),
            lift_a * max(0.0, math.sin(2 * math.pi * ph + math.pi / 2)))

def model(kind, uv, t=None):
    from model_preview import Cube, Part
    p, d = PROP[kind], DIMS[kind]
    k, l, m = d["carapace"]
    body = Part("body", pivot=(0.0, p["body_y"], 0.0),
                cubes=[Cube(uv["carapace"], (-k / 2.0, -l / 2.0, -m / 2.0), (k, l, m))])

    if "dome" in d:
        dk, dl, dm = d["dome"]
        body.add(Part("dome", pivot=(0.0, -l / 2.0, -0.5),
                      cubes=[Cube(uv["dome"], (-dk / 2.0, -dl, -dm / 2.0), (dk, dl, dm))]))

    if "abdomen" in d:
        ak, al, am = d["abdomen"]
        body.add(Part("abdomen", pivot=(0.0, p["abdomen_y"], p["abdomen_z"]),
                      cubes=[Cube(uv["abdomen"], (-ak / 2.0, -al / 2.0, -am / 2.0),
                                  (ak, al, am))]))
    if "rostrum" in d:
        rk, rl, rm = d["rostrum"]
        body.add(Part("rostrum", pivot=(0.0, p["rostrum_y"], p["rostrum_z"]),
                      cubes=[Cube(uv["rostrum"], (-rk / 2.0, -rl / 2.0, -rm), (rk, rl, rm))]))
    if "spine" in d:
        sk, sl, sm = d["spine"]
        for side in (1, -1):
            for n, (sx, sz) in enumerate(zip(p["spine_x"], p["spine_z"])):
                body.add(Part("spine_%s%d" % (_side(side), n),
                              pivot=(side * sx, -l / 2.0, sz),
                              cubes=[Cube(uv["spine"], (-sk / 2.0, -sl, -sm / 2.0),
                                          (sk, sl, sm))]))

    for side in (1, -1):
        for idx in range(len(p["leg_z"])):
            swing, lift = _gait(kind, idx, side, t)
            body.add(_leg(uv, kind, side, idx, swing, lift))
        small = (side > 0) if kind == "coconut" else (side < 0)
        body.add(_claw(uv, kind, side, small))

    ek, el, em = d["eye"]
    for side in (1, -1):
        body.add(Part("eye_%s" % _side(side),
                      pivot=(side * p["eye_x"], p["eye_y"], p["eye_z"]),
                      cubes=[Cube(uv["eye"], (-ek / 2.0, -el, -em / 2.0), (ek, el, em))]))

    if "antenna" in d:
        nk, nl, nm = d["antenna"]
        for side in (1, -1):
            body.add(Part("antenna_%s" % _side(side),
                          pivot=(side * p["antenna_x"], p["antenna_y"], p["antenna_z"]),
                          rot=(p["antenna_pitch"], side * p["antenna_yaw"], 0.0),
                          cubes=[Cube(uv["antenna"], (-nk / 2.0, -nl / 2.0, -nm),
                                      (nk, nl, nm))]))
    return Part("root", children=[body])

HEADER = '''package com.oceania.client.render;

public final class %s {
'''

def emit_java(path, kind):
    p = PROP[kind]
    cls = {"coconut": "CoconutCrabGeometry", "reef": "ReefCrabGeometry",
           "king": "KingCrabGeometry"}[kind]
    bx = boxes(kind)
    atlas_w, atlas_h = SHEET[kind]
    placed, _used = pack(bx, atlas_w)

    out = [HEADER % cls]
    a = out.append
    a("\tpublic static final int TEX_W = %d, TEX_H = %d;" % (atlas_w, atlas_h))
    a("\tpublic static final float GROUND_Y = %.1FF;" % GROUND_Y)
    sp_u, sp_b = span(kind)
    ht_u, ht_b = height(kind)
    a("\tpublic static final float SPAN = %.2FF, HEIGHT = %.2FF;" % (sp_u, ht_u))
    a("\tpublic static final int LEGS_PER_SIDE = %d;" % len(p["leg_z"]))
    a("")
    for name, k, l, m, _role in bx:
        u = name.upper()
        a("\tpublic static final float %s_W = %dF, %s_H = %dF, %s_D = %dF;" % (u, k, u, l, u, m))
    a("")
    for name, _k, _l, _m, _role in bx:
        i, j = placed[name]
        u = name.upper()
        a("\tpublic static final int %s_U = %d, %s_V = %d;" % (u, i, u, j))
    a("")
    for key in sorted(p):
        v = p[key]
        if isinstance(v, tuple):
            a("\tpublic static final float[] %s = {%s};"
              % (key.upper(), ", ".join("%.4FF" % x for x in v)))
        else:
            a("\tpublic static final float %s = %.4FF;" % (key.upper(), v))
    a("")
    ticks, sw, lf = {"coconut": (GAIT_TICKS_COCONUT, SWING_COCONUT, LIFT_COCONUT),
                     "reef": (GAIT_TICKS_REEF, SWING_REEF, LIFT_REEF),
                     "king": (GAIT_TICKS_KING, SWING_KING, LIFT_KING)}[kind]
    a("	public static final int GAIT_TICKS = %d;" % ticks)
    a("	public static final float SWING = %.4FF, LIFT = %.4FF;" % (sw, lf))
    a("")
    a("\tprivate %s() {" % cls)
    a("\t}")
    a("}")
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(out) + "\n")

def contact_sheet(root, tex, title, views=("three_quarter", "front", "side", "top"),
                  cell=(540, 400), scale=None, mode="textured"):
    from model_preview import render
    tiles = [render(root, texture=tex, view=v, size=cell, scale=scale, mode=mode,
                    grid_blocks=8) for v in views]
    cols = 2
    rows = (len(tiles) + cols - 1) // cols
    img = Image.new("RGB", (cell[0] * cols, cell[1] * rows + 26), (248, 248, 246))
    d = ImageDraw.Draw(img)
    d.text((8, 7), title, fill=(40, 44, 48))
    for n, t in enumerate(tiles):
        img.paste(t.convert("RGB"), ((n % cols) * cell[0], 26 + (n // cols) * cell[1]))
        d.text(((n % cols) * cell[0] + 8, 26 + (n // cols) * cell[1] + 6), views[n],
               fill=(120, 126, 132))
    return img

def scale_card(made, path, view="three_quarter", px=11.0, cell=(560, 400)):
    from model_preview import render
    tiles = []
    for kind, name, img, placed in made:
        uv = {b[0]: placed[b[0]] for b in boxes(kind)}
        tiles.append((name, render(model(kind, uv), texture=img, view=view, size=cell,
                                   scale=px, mode="textured", grid_blocks=8)))
    out = Image.new("RGB", (cell[0] * len(tiles), cell[1] + 26), (248, 248, 246))
    d = ImageDraw.Draw(out)
    d.text((8, 7), "same scale, %.0f px per unit, grid 8 units = half a block" % px,
           fill=(40, 44, 48))
    for n, (name, t) in enumerate(tiles):
        out.paste(t.convert("RGB"), (n * cell[0], 26))
        sp_u, sp_b = span(name.split("_")[0])
        d.text((n * cell[0] + 8, 32), "%s - %.2f blocks across" % (name, sp_b),
               fill=(120, 126, 132))
    out.save(path)

def main():
    import argparse
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", default=None, help="where the preview images go")
    ap.add_argument("--install", action="store_true",
                    help="also write the sheets into resources and emit the Java geometry")
    args = ap.parse_args()

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = args.out or os.path.join(repo, "build", "crab-preview")
    os.makedirs(out, exist_ok=True)

    builds = [("coconut", "coconut_blue", BLUE), ("coconut", "coconut_orange", ORANGE),
              ("reef", "reef_crab", GREEN), ("king", "king_crab", KING_PAL)]
    made = []
    for kind, name, pal in builds:
        img, placed, aw, ah, bx, used = build_sheet(kind, pal)
        made.append((kind, name, img, placed))
        img.resize((aw * 9, ah * 9), Image.NEAREST).save(
            os.path.join(out, "sheet_%s.png" % name))
        print("%-7s %-14s sheet %dx%d, %2d boxes, %2d shelf rows, %2.0f%% used"
              % (kind, name, aw, ah, len(bx), used, 100.0 * utilisation(bx, aw, ah)))

    for kind, name, img, placed in made:
        uv = {b[0]: placed[b[0]] for b in boxes(kind)}
        sc = {"coconut": 20.0, "reef": 26.0, "king": 13.0}[kind]
        contact_sheet(model(kind, uv), img, "%s - rest pose" % name, scale=sc).save(
            os.path.join(out, "%s.png" % name))
        contact_sheet(model(kind, uv), img, "%s - box layout" % name, scale=sc,
                      mode="parts").save(os.path.join(out, "%s_parts.png" % name))
        contact_sheet(model(kind, uv, t=0.25), img, "%s - mid-stride" % name, scale=sc,
                      views=("three_quarter", "front")).save(
            os.path.join(out, "%s_walk.png" % name))

    for kind in ("coconut", "reef", "king"):
        sp_u, sp_b = span(kind)
        ht_u, ht_b = height(kind)
        print("%-7s span %5.1f units = %.2f blocks, height %4.1f = %.2f blocks, "
              "belly %.1f clear, feet at y %.2f (ground %.1f)"
              % (kind, sp_u, sp_b, ht_u, ht_b, clearance(kind), foot_y(kind), GROUND_Y))

    scale_card(made, os.path.join(out, "scale.png"))

    if args.install:
        tex = os.path.join(repo, "src", "main", "resources", "assets", "oceania",
                           "textures", "entity", "crab")
        java = os.path.join(repo, "src", "client", "java", "com", "oceania", "client", "render")
        os.makedirs(tex, exist_ok=True)
        for _kind, name, img, _placed in made:
            img.save(os.path.join(tex, "%s.png" % name))
        emit_java(os.path.join(java, "CoconutCrabGeometry.java"), "coconut")
        emit_java(os.path.join(java, "ReefCrabGeometry.java"), "reef")
        emit_java(os.path.join(java, "KingCrabGeometry.java"), "king")
        print("installed sheets into %s and emitted both geometry classes" % tex)
    else:
        print("preview only. Rerun with --install to write resources and Java.")
    print("images in %s" % out)

if __name__ == "__main__":
    main()
