import io
import math
import os

from PIL import Image, ImageDraw

from boxpack import Rnd, check_pack, filler, hexc, mix, pack, regions, shade, utilisation

GROUND_Y = 24.0

DIMS = {
    "moon": dict(
        rim=(10, 1, 10),
        bell=(8, 2, 8),
        dome=(5, 2, 5),
        oral=(2, 6, 2),
        tentacle=(1, 4, 1),
    ),
    "nettle": dict(
        rim=(9, 1, 9),
        bell=(7, 3, 7),
        dome=(4, 3, 4),
        oral=(2, 12, 2),
        tentacle=(1, 16, 1),
    ),
    "crown": dict(
        rim=(9, 1, 9),
        bell=(7, 2, 7),
        dome=(4, 1, 4),
        tentacle=(1, 4, 1),
        trailer=(1, 20, 1),
    ),
}

INFLATE = {
    "moon": {"oral": -0.40, "tentacle": -0.15},
    "nettle": {"oral": -0.35, "tentacle": -0.15},
    "crown": {"tentacle": -0.15, "trailer": -0.10},
}

POSE = {
    "moon": dict(
        bell_y=15.0,
        tentacles=8, tentacle_r=4.2, tentacle_splay=0.26,
        orals=4, oral_r=1.6, oral_splay=0.14,
    ),
    "nettle": dict(
        bell_y=12.0,
        tentacles=6, tentacle_r=3.8, tentacle_splay=0.14,
        orals=4, oral_r=2.0, oral_splay=0.11,
    ),
    "crown": dict(
        bell_y=14.0,
        tentacles=10, tentacle_r=4.2, tentacle_splay=0.24,
        orals=0, oral_r=0.0, oral_splay=0.0,
    ),
}

PULSE_TICKS = {"moon": 44, "nettle": 58, "crown": 70}

PULSE_SQUASH = {"moon": 0.17, "nettle": 0.22, "crown": 0.11}

TENTACLE_LAG = 0.55
TENTACLE_KICK = {"moon": 0.30, "nettle": 0.42, "crown": 0.20}

SHEET = {"moon": (64, 32), "nettle": (64, 32), "crown": (64, 32)}

def _measure(kind):
    from model_preview import bounds
    uv = {name: (0, 0) for name in DIMS[kind]}
    w = h = 0.0
    for n in range(24):
        lo, hi = bounds(model(kind, uv, pulse=n / 24.0))
        w = max(w, float(hi[0] - lo[0]), float(hi[2] - lo[2]))
        h = max(h, float(hi[1] - lo[1]))
    return {"width": w, "height": h}

def span(kind):
    w = _measure(kind)["width"]
    return w, w / 16.0

def height(kind):
    h = _measure(kind)["height"]
    return h, h / 16.0

def drop(kind):
    d, p = DIMS[kind], POSE[kind]
    longest = max(d[n][1] for n in ("tentacle", "trailer", "oral") if n in d)
    return longest * math.cos(p["tentacle_splay"])

ALPHA = dict(
    bell=180,
    rim=215,
    dome=170,
    crown=200,
    mark=235,
    tentacle=150,
    oral=165,
    trailer=140,
)

GREY = dict(
    bell="#E8EFF2",
    bell_lo="#B9C7CE",
    rim="#CFDCE2",
    mark="#FFFFFF",
    groove="#8FA2AB",
    tentacle="#DCE6EA",
    tentacle_tip="#F6FAFB",
)

TINTS = {
    "moon": [("cyan", 0x47F2FF), ("violet", 0x9C6BFF), ("seafoam", 0x5BFFC4)],
    "nettle": [("magenta", 0xFF46C8), ("amber", 0xFFA33D), ("orchid", 0xC65BFF)],
    "crown": [("ember", 0xFF3B52), ("electric", 0x3D7BFF), ("acid", 0x8CFF3D)],
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

def _rgba(col, role):
    return (col[0], col[1], col[2], ALPHA.get(role, 200))

def paint_box(d, i, j, k, l, m, role, kind):
    fill = filler(d)
    r = regions(i, j, k, l, m)
    a = lambda c: _rgba(c, role)
    bell, bell_lo = a(hexc(GREY["bell"])), a(hexc(GREY["bell_lo"]))
    rim = a(hexc(GREY["rim"]))
    mark = _rgba(hexc(GREY["mark"]), "mark")
    groove = _rgba(hexc(GREY["groove"]), "mark")
    tent, tent_tip = a(hexc(GREY["tentacle"])), a(hexc(GREY["tentacle_tip"]))

    if role == "bell":
        for key in ("west", "north", "east", "south"):
            fill(r[key], bell)
            _sub(d, r[key], 0.0, 0.70, 1.0, 1.0, bell_lo)
        fill(r["back"], bell)
        fill(r["belly"], bell_lo)

        if kind == "moon":
            for fx, fy in ((0.24, 0.24), (0.58, 0.24), (0.24, 0.58), (0.58, 0.58)):
                _sub(d, r["back"], fx, fy, fx + 0.18, fy + 0.18, mark)
                _sub(d, r["back"], fx + 0.04, fy + 0.04, fx + 0.14, fy + 0.14, bell)
            for key in ("west", "north", "east", "south"):
                for n in range(2):
                    f = 0.22 + n * 0.34
                    _sub(d, r[key], f, 0.18, f + 0.22, 0.62, mark)
        elif kind == "nettle":
            for n in range(4):
                f = 0.12 + n * 0.22
                _sub(d, r["back"], f, 0.06, f + 0.07, 0.94, mark)
            for key in ("west", "north", "east", "south"):
                for n in range(3):
                    f = 0.18 + n * 0.28
                    _sub(d, r[key], f, 0.0, f + 0.08, 0.70, mark)
        else:
            _sub(d, r["back"], 0.16, 0.16, 0.84, 0.84, a(mix(bell, groove, 0.45)[:3]))
            _sub(d, r["back"], 0.26, 0.26, 0.74, 0.74, bell)

    elif role == "dome":
        for key in ("west", "north", "east", "south"):
            fill(r[key], bell)
        fill(r["back"], bell)
        fill(r["belly"], bell_lo)
        if kind == "crown":
            _sub(d, r["back"], 0.18, 0.18, 0.82, 0.82, groove)
            _sub(d, r["back"], 0.30, 0.30, 0.70, 0.70, mark)
        else:
            _sub(d, r["back"], 0.30, 0.30, 0.70, 0.70, mark)

    elif role == "crown":
        for key in ("west", "north", "east", "south"):
            fill(r[key], bell)
            _sub(d, r[key], 0.0, 0.0, 1.0, 0.35, mark)
        fill(r["back"], mark)
        fill(r["belly"], groove)

    elif role == "rim":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], rim)
        for key in ("west", "north", "east", "south"):
            _sub(d, r[key], 0.0, 0.0, 1.0, 0.5, mark)
        _sub(d, r["belly"], 0.18, 0.18, 0.82, 0.82, groove)

    elif role in ("tentacle", "trailer", "oral"):
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], tent)
        rnd = Rnd(7 + len(role))
        for key in ("west", "east", "north", "south"):
            for n in range(5):
                f = 0.08 + n * 0.18 + 0.03 * rnd.frac()
                _sub(d, r[key], 0.0, f, 1.0, f + 0.06, mark)
        for key in ("west", "east", "north", "south"):
            _sub(d, r[key], 0.0, 0.0, 1.0, 0.10, groove)
        fill(r["belly"], tent_tip)

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

def tinted(sheet, rgb):
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    out = sheet.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            pr, pg, pb, pa = px[x, y]
            if pa:
                px[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return out

def model(kind, uv, pulse=0.0):
    from model_preview import Cube, Part
    d, p = DIMS[kind], POSE[kind]

    squeeze = PULSE_SQUASH[kind] * 0.5 * (1.0 - math.cos(2.0 * math.pi * pulse))
    bell_x = 1.0 - squeeze
    bell_y = 1.0 - squeeze * 0.7

    root = Part("root")
    body = Part("body", pivot=(0.0, p["bell_y"], 0.0), scale=(bell_x, bell_y, bell_x))
    root.add(body)

    y = 0.0
    for part in ("rim", "bell", "dome"):
        pk, pl, pm = d[part]
        body.add(Part(part, pivot=(0.0, y, 0.0),
                      cubes=[Cube(uv[part], (-pk / 2.0, -pl, -pm / 2.0), (pk, pl, pm))]))
        y -= pl

    kick = TENTACLE_KICK[kind] * math.sin(2.0 * math.pi * (pulse - TENTACLE_LAG / (2 * math.pi)))
    n = p["tentacles"]
    name = "trailer" if "trailer" in d else "tentacle"
    tk, tl, tm = d[name] if "trailer" not in d else d["tentacle"]
    for idx in range(n):
        a = 2.0 * math.pi * idx / n
        splay = p["tentacle_splay"] + kick
        body.add(Part("tentacle%d" % idx,
                      pivot=(math.cos(a) * p["tentacle_r"], 0.0, math.sin(a) * p["tentacle_r"]),
                      rot=(math.sin(a) * splay, 0.0, -math.cos(a) * splay),
                      cubes=[Cube(uv["tentacle"], (-tk / 2.0, 0.0, -tm / 2.0), (tk, tl, tm),
                                  inflate=INFLATE[kind].get("tentacle", 0.0))]))

    if "trailer" in d:
        sk, sl, sm = d["trailer"]
        body.add(Part("trailer",
                      rot=(0.06 * math.sin(2 * math.pi * pulse), 0.0, 0.0),
                      cubes=[Cube(uv["trailer"], (-sk / 2.0, 0.0, -sm / 2.0), (sk, sl, sm),
                                  inflate=INFLATE[kind].get("trailer", 0.0))]))

    if p["orals"]:
        ok, ol, om = d["oral"]
        for idx in range(p["orals"]):
            a = 2.0 * math.pi * (idx + 0.5) / p["orals"]
            sp = p["oral_splay"] + kick * 0.4
            body.add(Part("oral%d" % idx,
                          pivot=(math.cos(a) * p["oral_r"], 0.0, math.sin(a) * p["oral_r"]),
                          rot=(math.sin(a) * sp, 0.0, -math.cos(a) * sp),
                          cubes=[Cube(uv["oral"], (-ok / 2.0, 0.0, -om / 2.0), (ok, ol, om),
                                      inflate=INFLATE[kind].get("oral", 0.0))]))
    return root

HEADER = '''package com.oceania.client.render;

public final class %s {
'''

def emit_java(path, kind):
    d, p = DIMS[kind], POSE[kind]
    cls = {"moon": "MoonJellyGeometry", "nettle": "NettleJellyGeometry",
           "crown": "CrownJellyGeometry"}[kind]
    bx = boxes(kind)
    atlas_w, atlas_h = SHEET[kind]
    placed, _used = pack(bx, atlas_w)

    out = [HEADER % cls]
    a = out.append
    a("\tpublic static final int TEX_W = %d, TEX_H = %d;" % (atlas_w, atlas_h))
    sp_u, sp_b = span(kind)
    ht_u, ht_b = height(kind)
    a("\tpublic static final float SPAN = %.2FF, HEIGHT = %.2FF;" % (sp_u, ht_u))
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
        if isinstance(v, int) and not isinstance(v, bool):
            a("\tpublic static final int %s = %d;" % (key.upper(), v))
        else:
            a("\tpublic static final float %s = %.4FF;" % (key.upper(), float(v)))
    a("")
    for role in sorted(INFLATE[kind]):
        a("	public static final float %s_INFLATE = %.2FF;" % (role.upper(), INFLATE[kind][role]))
    a("")
    a("\tpublic static final int PULSE_TICKS = %d;" % PULSE_TICKS[kind])
    a("\tpublic static final float PULSE_SQUASH = %.4FF;" % PULSE_SQUASH[kind])
    a("\tpublic static final float TENTACLE_LAG = %.4FF;" % TENTACLE_LAG)
    a("\tpublic static final float TENTACLE_KICK = %.4FF;" % TENTACLE_KICK[kind])
    a("")
    a("\tpublic static final int[] TINTS = {%s};"
      % ", ".join("0x%06X" % c for _n, c in TINTS[kind]))
    a("\tpublic static final String[] TINT_NAMES = {%s};"
      % ", ".join('"%s"' % n for n, _c in TINTS[kind]))
    a("")
    a("\tprivate %s() {" % cls)
    a("\t}")
    a("}")
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(out) + "\n")

DEEP = (10, 24, 38)

def contact_sheet(kind, sheet, title, views=("three_quarter", "side", "top"),
                  cell=(430, 460), scale=22.0, pulse=0.0, uv=None, mode="textured"):
    from model_preview import render
    root = model(kind, uv, pulse=pulse)
    tiles = [render(root, texture=sheet, view=v, size=cell, scale=scale, mode=mode,
                    bg=DEEP, ambient=0.80, grid_blocks=8, grid_rgb=(26, 44, 62))
             for v in views]
    img = Image.new("RGB", (cell[0] * len(tiles), cell[1] + 26), DEEP)
    d = ImageDraw.Draw(img)
    d.text((8, 7), title, fill=(200, 220, 232))
    for n, t in enumerate(tiles):
        img.paste(t.convert("RGB"), (n * cell[0], 26))
        d.text((n * cell[0] + 8, 32), views[n], fill=(110, 140, 162))
    return img

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
    out = args.out or os.path.join(repo, "build", "jellyfish-preview")
    os.makedirs(out, exist_ok=True)

    made = {}
    for kind in DIMS:
        img, placed, aw, ah, bx, used = build_sheet(kind)
        uv = {b[0]: placed[b[0]] for b in bx}
        made[kind] = (img, uv)
        sp_u, sp_b = span(kind)
        ht_u, ht_b = height(kind)
        print("%-7s sheet %dx%d, %d boxes, %2d shelf rows, %2.0f%% used | "
              "span %4.1f u (%.2f blocks), height %4.1f (%.2f)"
              % (kind, aw, ah, len(bx), used, 100.0 * utilisation(bx, aw, ah),
                 sp_u, sp_b, ht_u, ht_b))

        name, rgb = TINTS[kind][0]
        tint = tinted(img, rgb)
        contact_sheet(kind, tint, "%s jelly - %s - relaxed" % (kind, name),
                      uv=uv).save(os.path.join(out, "%s_relaxed.png" % kind))
        contact_sheet(kind, tint, "%s jelly - %s - mid-pulse" % (kind, name),
                      views=("three_quarter", "side"), pulse=0.5,
                      uv=uv).save(os.path.join(out, "%s_pulse.png" % kind))
        contact_sheet(kind, tint, "%s jelly - box layout" % kind,
                      views=("three_quarter", "side"), mode="parts",
                      uv=uv).save(os.path.join(out, "%s_parts.png" % kind))

    from model_preview import render
    cell = (320, 360)
    rows, cols = len(DIMS), max(len(v) for v in TINTS.values())
    card = Image.new("RGB", (cell[0] * cols, cell[1] * rows + 26), DEEP)
    d = ImageDraw.Draw(card)
    d.text((8, 7), "every species in every tint - deep water, no other light source",
           fill=(200, 220, 232))
    for ri, kind in enumerate(DIMS):
        sheet, uv = made[kind]
        root = model(kind, uv, pulse=0.18)
        for ci, (tname, rgb) in enumerate(TINTS[kind]):
            tile = render(root, texture=tinted(sheet, rgb), view="three_quarter", size=cell,
                          scale=19.0, bg=DEEP, ambient=0.84)
            card.paste(tile.convert("RGB"), (ci * cell[0], 26 + ri * cell[1]))
            d.text((ci * cell[0] + 8, 26 + ri * cell[1] + 6), "%s / %s" % (kind, tname),
                   fill=(150, 180, 202))
    card.save(os.path.join(out, "colours.png"))

    if args.install:
        tex = os.path.join(repo, "src", "main", "resources", "assets", "oceania",
                           "textures", "entity", "jellyfish")
        java = os.path.join(repo, "src", "client", "java", "com", "oceania", "client", "render")
        os.makedirs(tex, exist_ok=True)
        for kind, (img, _uv) in made.items():
            img.save(os.path.join(tex, "%s.png" % kind))
        for kind, cls in (("moon", "MoonJellyGeometry"), ("nettle", "NettleJellyGeometry"),
                          ("crown", "CrownJellyGeometry")):
            emit_java(os.path.join(java, cls + ".java"), kind)
        print("installed 3 sheets into %s and emitted three geometry classes" % tex)
    else:
        print("preview only. Rerun with --install to write resources and Java.")
    print("images in %s" % out)

if __name__ == "__main__":
    main()
