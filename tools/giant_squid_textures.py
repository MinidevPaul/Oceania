import io
import math
import os

from PIL import Image, ImageDraw

from boxpack import check_pack, filler, hexc, mix, pack, regions, shade, utilisation

DIMS = {
    "architeuthis": dict(
        mantle=(11, 11, 38),
        tip=(6, 6, 14),
        fin=(15, 2, 18),
        head=(10, 9, 9),
        eye=(7, 7, 4),
        arm_up=(2, 22, 2),
        arm_lo=(2, 20, 2),
        tentacle=(2, 42, 2),
        club=(3, 11, 3),
    ),
    "colossal": dict(
        mantle=(17, 17, 42),
        tip=(9, 9, 12),
        fin=(16, 3, 16),
        head=(14, 13, 10),
        eye=(9, 9, 4),
        arm_up=(4, 14, 4),
        arm_mid=(3, 12, 3),
        arm_lo=(2, 10, 2),
        tentacle=(3, 20, 3),
        club=(5, 13, 5),
    ),
}

TENT_SEGMENTS = {"architeuthis": 4, "colossal": 3}

ARM_SEGMENTS = {"architeuthis": ("arm_up", "arm_lo"),
                "colossal": ("arm_up", "arm_mid", "arm_lo")}

ARM_WAVE = (0.06, 0.11, 0.19)
TENT_WAVE = (0.05, 0.10, 0.17, 0.26)
WAVE_LAG = 0.17

POSE = {
    "architeuthis": dict(
        axis_y=12.0,
        arms=8, arm_r=3.4,
        arm_sweep=0.30, arm_bend=0.34,
        tent_r=2.2, tent_sweep=0.10,
        eye_x=5.2, eye_y=-0.5, eye_z=-3.0,
        fin_z=13.0, fin_x=6.0,
    ),
    "colossal": dict(
        axis_y=16.0,
        arms=8, arm_r=5.0,
        arm_sweep=0.42, arm_bend=0.46,
        tent_r=3.0, tent_sweep=0.16,
        eye_x=7.2, eye_y=-1.0, eye_z=-3.5,
        fin_z=15.0, fin_x=8.0,
    ),
}

SCALE = {"architeuthis": 1.0, "colossal": 1.0, "leviathan": 1.4}
BASE = {"architeuthis": "architeuthis", "colossal": "colossal", "leviathan": "architeuthis"}

SHEET = {"architeuthis": (128, 128), "colossal": (128, 128)}

def total_length(kind):
    d, p = DIMS[kind], POSE[kind]
    back = d["tip"][2] + d["mantle"][2] * 0.5
    tent = (d["tentacle"][1] * TENT_SEGMENTS[kind] + d["club"][1]) * math.cos(p["tent_sweep"])
    front = d["mantle"][2] * 0.5 + d["head"][2] + tent
    return back + front, (back + front) / 16.0

def girth(kind):
    d = DIMS[kind]
    return float(max(d["mantle"][0], d["fin"][0] * 2)), max(d["mantle"][0], d["fin"][0] * 2) / 16.0

PAL = {
    "architeuthis": dict(
        back="#7E3A2E", back_hi="#9C4C3A", back_lo="#5A2820",
        belly="#D9C3AE", mottle="#B2583F", sucker="#E6D2BC",
        eye="#F2E4B8", pupil="#101418", beak="#2A2420",
    ),
    "colossal": dict(
        back="#6E2F3E", back_hi="#8E4254", back_lo="#4C1F2B",
        belly="#CBB6B4", mottle="#A24358", sucker="#E0CBC6",
        eye="#EFEAD2", pupil="#0C1014", beak="#241E1E",
    ),
}

def _sub(d, rect, fx0, fy0, fx1, fy1, col):
    u0, v0, u1, v1 = rect
    if v1 < v0:
        v0, v1 = v1, v0
    w, h = u1 - u0, v1 - v0
    x0, x1 = u0 + int(round(fx0 * w)), u0 + int(round(fx1 * w))
    y0, y1 = v0 + int(round(fy0 * h)), v0 + int(round(fy1 * h))
    if x1 > x0 and y1 > y0:
        d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=col)

def paint_box(d, i, j, k, l, m, role, kind):
    fill = filler(d)
    r = regions(i, j, k, l, m)
    p = PAL[kind]
    back, hi, lo = hexc(p["back"]), hexc(p["back_hi"]), hexc(p["back_lo"])
    belly, mottle = hexc(p["belly"]), hexc(p["mottle"])
    sucker, eye, pupil = hexc(p["sucker"]), hexc(p["eye"]), hexc(p["pupil"])

    if role in ("mantle", "tip", "head"):
        for key in ("west", "north", "east", "south"):
            fill(r[key], back)
            _sub(d, r[key], 0.0, 0.62, 1.0, 1.0, belly)
            _sub(d, r[key], 0.0, 0.58, 1.0, 0.64, mix(back, belly, 0.5))
        fill(r["back"], back)
        fill(r["belly"], belly)
        if role == "mantle":
            for n in range(5):
                f = 0.10 + n * 0.18
                _sub(d, r["back"], f, 0.14, f + 0.08, 0.40, mottle)
                _sub(d, r["back"], f + 0.04, 0.58, f + 0.12, 0.84, hi)

    elif role == "fin":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], back)
        _sub(d, r["back"], 0.0, 0.0, 1.0, 0.22, lo)
        _sub(d, r["belly"], 0.0, 0.0, 1.0, 1.0, mix(back, belly, 0.55))

    elif role == "eye":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], eye)
        for key in ("west", "east"):
            _sub(d, r[key], 0.22, 0.22, 0.78, 0.78, pupil)
        _sub(d, r["north"], 0.3, 0.3, 0.7, 0.7, pupil)

    elif role in ("arm_up", "arm_mid", "arm_lo", "tentacle", "club"):
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], back if role != "club" else hi)
        rows = 7 if role != "club" else 4
        for key in ("north", "south"):
            for n in range(rows):
                f = 0.08 + n * (0.84 / rows)
                _sub(d, r[key], 0.30, f, 0.70, f + 0.045, sucker)
        if role in ("arm_up", "arm_mid", "tentacle"):
            _sub(d, r["back"], 0.0, 0.0, 1.0, 0.08, lo)
        if role == "club":
            for key in ("west", "east"):
                _sub(d, r[key], 0.18, 0.12, 0.82, 0.88, mix(hi, sucker, 0.45))

    else:
        raise ValueError("unpainted role %s" % role)

def boxes(kind):
    return [(n, d[0], d[1], d[2], n) for n, d in DIMS[kind].items()]

def build_sheet(kind):
    bx = boxes(kind)
    atlas_w, atlas_h = SHEET[kind]
    placed, used = pack(bx, atlas_w)
    check_pack(placed, bx, atlas_w, atlas_h)
    img = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for name, k, l, m, role in bx:
        i, j = placed[name]
        paint_box(d, i, j, k, l, m, role, kind)
    return img, placed, atlas_w, atlas_h, bx, used

def model(kind, uv, scale=1.0, strike=0.0, t=0.0):
    from model_preview import Cube, Part
    d, p = DIMS[kind], POSE[kind]

    root = Part("root")
    body = Part("body", pivot=(0.0, POSE[kind]["axis_y"], 0.0), scale=(scale, scale, scale))
    root.add(body)

    mk, ml, mm = d["mantle"]
    body.add(Part("mantle",
                  cubes=[Cube(uv["mantle"], (-mk / 2.0, -ml / 2.0, -mm / 2.0), (mk, ml, mm))]))
    tk, tl, tm = d["tip"]
    body.add(Part("tip", pivot=(0.0, 0.0, mm / 2.0),
                  cubes=[Cube(uv["tip"], (-tk / 2.0, -tl / 2.0, 0.0), (tk, tl, tm))]))

    fk, fl, fm = d["fin"]
    for side in (1, -1):
        body.add(Part("fin_%s" % ("r" if side > 0 else "l"),
                      pivot=(side * p["fin_x"], 0.0, p["fin_z"]),
                      rot=(0.0, 0.0, side * 0.18),
                      cubes=[Cube(uv["fin"], (0.0 if side > 0 else -fk, -fl / 2.0, -fm / 2.0),
                                  (fk, fl, fm), mirror=side < 0)]))

    hk, hl, hm = d["head"]
    head = Part("head", pivot=(0.0, 0.0, -mm / 2.0),
                cubes=[Cube(uv["head"], (-hk / 2.0, -hl / 2.0, -hm), (hk, hl, hm))])
    body.add(head)

    ek, el, em = d["eye"]
    for side in (1, -1):
        head.add(Part("eye_%s" % ("r" if side > 0 else "l"),
                      pivot=(side * p["eye_x"], p["eye_y"], p["eye_z"]),
                      cubes=[Cube(uv["eye"], (-ek / 2.0, -el / 2.0, -em / 2.0), (ek, el, em))]))

    opening = strike * 0.9
    for idx in range(p["arms"]):
        a = 2.0 * math.pi * idx / p["arms"]
        sweep = p["arm_sweep"] + opening * 0.7
        seg_names = ARM_SEGMENTS[kind]
        node = body
        for si, seg in enumerate(seg_names):
            ak, al, am = d[seg]
            wave = ARM_WAVE[si] * math.sin(2.0 * math.pi * (t - si * WAVE_LAG + a / (2.0 * math.pi)))
            if si == 0:
                part = Part("arm_%s%d" % (seg, idx),
                            pivot=(-math.sin(a) * p["arm_r"], math.cos(a) * p["arm_r"], -hm),
                            rot=(-math.pi / 2.0 + sweep + wave, 0.0, a),
                            cubes=[Cube(uv[seg], (-ak / 2.0, 0.0, -am / 2.0), (ak, al, am))])
                body.add(part)
            else:
                bend = (p["arm_bend"] - opening * 0.5) / (len(seg_names) - 1)
                part = Part("arm_%s%d" % (seg, idx), pivot=(0.0, prev_len, 0.0),
                            rot=(bend + wave, 0.0, 0.0),
                            cubes=[Cube(uv[seg], (-ak / 2.0, 0.0, -am / 2.0), (ak, al, am))])
                node.add(part)
            node = part
            prev_len = al

    sk, sl, sm = d["tentacle"]
    ck, cl, cm = d["club"]
    for side in (1, -1):
        a = side * math.pi * 0.5
        chain = Part("tent_%s" % ("r" if side > 0 else "l"),
                     pivot=(-math.sin(a) * p["tent_r"], math.cos(a) * p["tent_r"] + 2.0, -hm),
                     rot=(-math.pi / 2.0 + p["tent_sweep"]
                          + TENT_WAVE[0] * math.sin(2.0 * math.pi * t), 0.0, a),
                     cubes=[Cube(uv["tentacle"], (-sk / 2.0, 0.0, -sm / 2.0), (sk, sl, sm))])
        body.add(chain)
        node = chain
        for seg in range(1, TENT_SEGMENTS[kind]):
            nxt = Part("tent_%s%d" % ("r" if side > 0 else "l", seg), pivot=(0.0, sl, 0.0),
                       rot=(0.06 + TENT_WAVE[seg]
                            * math.sin(2.0 * math.pi * (t - seg * WAVE_LAG)), 0.0, 0.0),
                       cubes=[Cube(uv["tentacle"], (-sk / 2.0, 0.0, -sm / 2.0), (sk, sl, sm))])
            node.add(nxt)
            node = nxt
        node.add(Part("club_%s" % ("r" if side > 0 else "l"), pivot=(0.0, sl, 0.0),
                      rot=(TENT_WAVE[-1] * math.sin(2.0 * math.pi
                           * (t - TENT_SEGMENTS[kind] * WAVE_LAG)), 0.0, 0.0),
                      cubes=[Cube(uv["club"], (-ck / 2.0, 0.0, -cm / 2.0), (ck, cl, cm))]))
    return root

HEADER = """package com.oceania.client.render;

public final class GiantSquidGeometry {
"""

def emit_java(path, kind):
    d, p = DIMS[kind], POSE[kind]
    bx = boxes(kind)
    atlas_w, atlas_h = SHEET[kind]
    placed, _used = pack(bx, atlas_w)

    out = [HEADER]
    a = out.append
    a("	public static final int TEX_W = %d, TEX_H = %d;" % (atlas_w, atlas_h))
    tl_u, tl_b = total_length(kind)
    g_u, g_b = girth(kind)
    a("	public static final float LENGTH = %.2FF, GIRTH = %.2FF;" % (tl_u, g_u))
    a("	public static final int ARMS = %d;" % p["arms"])
    a("	public static final int TENT_SEGMENTS = %d;" % TENT_SEGMENTS[kind])
    a("	public static final String[] ARM_SEGMENTS = {%s};"
      % ", ".join('"%s"' % n for n in ARM_SEGMENTS[kind]))
    a("")
    for name, k, l, m, _role in bx:
        u = name.upper()
        a("	public static final float %s_W = %dF, %s_H = %dF, %s_D = %dF;" % (u, k, u, l, u, m))
    a("")
    for name, _k, _l, _m, _role in bx:
        i2, j2 = placed[name]
        u = name.upper()
        a("	public static final int %s_U = %d, %s_V = %d;" % (u, i2, u, j2))
    a("")
    for key in sorted(p):
        if key == "arms":
            continue
        v = p[key]
        if isinstance(v, int) and not isinstance(v, bool):
            a("	public static final int %s = %d;" % (key.upper(), v))
        else:
            a("	public static final float %s = %.4FF;" % (key.upper(), float(v)))
    a("")
    a("	public static final float[] ARM_WAVE = {%s};"
      % ", ".join("%.4FF" % x for x in ARM_WAVE))
    a("	public static final float[] TENT_WAVE = {%s};"
      % ", ".join("%.4FF" % x for x in TENT_WAVE))
    a("	public static final float WAVE_LAG = %.4FF;" % WAVE_LAG)
    a("")
    a("	private GiantSquidGeometry() {")
    a("	}")
    a("}")
    with io.open(path, "w", encoding="utf-8", newline=chr(10)) as fh:
        fh.write(chr(10).join(out) + chr(10))

DEEP = (8, 18, 30)

def main():
    import argparse
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from model_preview import render

    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--out", default=None)
    ap.add_argument("--install", action="store_true",
                    help="write the COLOSSAL sheet into resources and emit its geometry")
    args = ap.parse_args()

    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = args.out or os.path.join(repo, "build", "giant-squid-preview")
    os.makedirs(out, exist_ok=True)

    sheets = {}
    for kind in DIMS:
        img, placed, aw, ah, bx, used = build_sheet(kind)
        uv = {b[0]: placed[b[0]] for b in bx}
        sheets[kind] = (img, uv)
        tl_u, tl_b = total_length(kind)
        g_u, g_b = girth(kind)
        print("%-13s sheet %dx%d, %d boxes, %2d shelf rows, %2.0f%% used | "
              "length %5.1f u (%5.2f blocks), girth %4.1f (%.2f)"
              % (kind, aw, ah, len(bx), used, 100.0 * utilisation(bx, aw, ah),
                 tl_u, tl_b, g_u, g_b))

    PROPOSALS = [
        ("A", "architeuthis", 1.0, "Architeuthis - the giant squid"),
        ("B", "colossal", 1.0, "Mesonychoteuthis - the colossal squid"),
        ("C", "architeuthis", 1.4, "Architeuthis at 1.4x - top of the recorded range"),
    ]

    for letter, base, sc, title in PROPOSALS:
        sheet, uv = sheets[base]
        cell = (760, 420)
        views = ("side", "top", "three_quarter")
        tiles = [render(model(base, uv, scale=sc), texture=sheet, view=v, size=cell,
                        scale=None, bg=DEEP, ambient=0.78, grid_blocks=16,
                        grid_rgb=(24, 40, 56)) for v in views]
        card = Image.new("RGB", (cell[0], cell[1] * len(tiles) + 28), DEEP)
        d = ImageDraw.Draw(card)
        tl_u, tl_b = total_length(base)
        d.text((8, 8), "%s   %s   -   %.1f blocks long, grid is 1 block"
               % (letter, title, tl_b * sc), fill=(210, 226, 238))
        for n, t in enumerate(tiles):
            card.paste(t.convert("RGB"), (0, 28 + n * cell[1]))
            d.text((8, 34 + n * cell[1]), views[n], fill=(118, 148, 170))
        card.save(os.path.join(out, "proposal_%s.png" % letter))

    px = 3.9
    cell = (1500, 290)
    card = Image.new("RGB", (cell[0], cell[1] * len(PROPOSALS) + 70), DEEP)
    d = ImageDraw.Draw(card)
    d.text((8, 8), "all three at the same scale. bars: blue whale 24 blocks, player 1.8",
           fill=(210, 226, 238))
    d.rectangle([8, 28, 8 + int(24 * 16 * px), 36], fill=(70, 104, 140))
    d.text((12, 40), "blue whale, 24 blocks", fill=(120, 160, 196))
    d.rectangle([8, 56, 8 + int(1.8 * 16 * px), 62], fill=(190, 170, 110))
    for n, (letter, base, sc, title) in enumerate(PROPOSALS):
        sheet, uv = sheets[base]
        tile = render(model(base, uv, scale=sc), texture=sheet, view="side", size=cell,
                      scale=px, bg=DEEP, ambient=0.80)
        card.paste(tile.convert("RGB"), (0, 70 + n * cell[1]))
        tl_u, tl_b = total_length(base)
        d.text((8, 76 + n * cell[1]), "%s  %s  -  %.1f blocks" % (letter, title, tl_b * sc),
               fill=(150, 184, 208))
    card.save(os.path.join(out, "scale.png"))

    for view, fname in (("three_quarter", "motion_3q"), ("top", "motion_top")):
        sheet, uv = sheets["colossal"]
        frames = 6
        cell = (430, 330)
        strip = Image.new("RGB", (cell[0] * frames, cell[1] + 26), DEEP)
        d = ImageDraw.Draw(strip)
        d.text((8, 7), "colossal squid - one wave cycle, %s view, cruise pose" % view,
               fill=(210, 226, 238))
        for n in range(frames):
            tile = render(model("colossal", uv, t=n / float(frames)), texture=sheet,
                          view=view, size=cell, scale=3.0, bg=DEEP, ambient=0.80)
            strip.paste(tile.convert("RGB"), (n * cell[0], 26))
            d.text((n * cell[0] + 8, 32), "t=%.2f" % (n / float(frames)), fill=(118, 148, 170))
        strip.save(os.path.join(out, "%s.png" % fname))

    sheet, uv = sheets["colossal"]
    cell = (640, 420)
    pair = Image.new("RGB", (cell[0] * 2, cell[1] + 26), DEEP)
    d = ImageDraw.Draw(pair)
    d.text((8, 7), "colossal squid - arms closed at cruise, open to strike", fill=(210, 226, 238))
    for n, (lbl, st) in enumerate((("cruise", 0.0), ("strike", 1.0))):
        tile = render(model("colossal", uv, strike=st, t=0.25), texture=sheet,
                      view="three_quarter", size=cell, scale=3.4, bg=DEEP, ambient=0.80)
        pair.paste(tile.convert("RGB"), (n * cell[0], 26))
        d.text((n * cell[0] + 8, 32), lbl, fill=(118, 148, 170))
    pair.save(os.path.join(out, "strike.png"))

    if args.install:
        tex = os.path.join(repo, "src", "main", "resources", "assets", "oceania",
                           "textures", "entity", "squid")
        java = os.path.join(repo, "src", "client", "java", "com", "oceania", "client", "render")
        os.makedirs(tex, exist_ok=True)
        sheets["colossal"][0].save(os.path.join(tex, "giant_squid.png"))
        emit_java(os.path.join(java, "GiantSquidGeometry.java"), "colossal")
        print("installed the colossal sheet and emitted GiantSquidGeometry")

    print("images in %s" % out)

if __name__ == "__main__":
    main()
