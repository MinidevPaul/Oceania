import math

from PIL import Image, ImageDraw

from boxpack import check_pack, filler, hexc, pack, regions, ri, shade, utilisation

SPAN = 26.0

PROP = dict(
    body_w=4, body_h=4, body_len=6,
    head_w=3, head_h=3, head_len=3,
    bill_w=1, bill_h=1, bill_len=2,
    tail_w=5, tail_len=4,
    wing_base_k=7, wing_base_m=4,
    wing_tip_k=8, wing_tip_m=3,
)

Y_AXIS = 20.0

COL = dict(
    white="#F2F3F0",
    mantle="#95A2AB",
    mantle_lo="#7E8C96",
    tip="#2B3036",
    mirror="#EDEFEA",
    bill="#E3B23C",
    bill_spot="#BF3B2E",
    eye="#15181B",
    foot="#E0A43A",
)

def boxes():
    p = PROP
    return [
        ("body", p["body_w"], p["body_h"], p["body_len"], "body"),
        ("head", p["head_w"], p["head_h"], p["head_len"], "head"),
        ("bill", p["bill_w"], p["bill_h"], p["bill_len"], "bill"),
        ("tail", p["tail_w"], 1, p["tail_len"], "tail"),
        ("wing", p["wing_base_k"], 1, p["wing_base_m"], "wing"),
        ("wing_tip", p["wing_tip_k"], 1, p["wing_tip_m"], "wing_tip"),
    ]

def span_check():
    p = PROP
    s = p["body_w"] + 2 * (p["wing_base_k"] + p["wing_tip_k"])
    return s, s / 16.0

def aspect_ratio():
    p = PROP
    half = p["wing_base_k"] + p["wing_tip_k"]
    area = p["wing_base_k"] * p["wing_base_m"] + p["wing_tip_k"] * p["wing_tip_m"]
    return (2 * half) ** 2 / (2.0 * area)

def paint_box(d, i, j, k, l, m, role):
    fill = filler(d)
    r = regions(i, j, k, l, m)
    white, mantle = hexc(COL["white"]), hexc(COL["mantle"])
    mantle_lo, tip = hexc(COL["mantle_lo"]), hexc(COL["tip"])

    if role == "body":
        for key in ("belly", "west", "north", "east", "south"):
            fill(r[key], white)
        fill(r["back"], mantle)
        for key in ("west", "east"):
            u0, v0, u1, v1 = r[key]
            d.rectangle([u0, v0, u1 - 1, v0], fill=mantle)
        return

    if role == "head":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], white)
        for key in ("west", "east"):
            u0, v0, u1, v1 = r[key]
            ue = u0 + max(1, (u1 - u0) // 4)
            ve = v0 + max(1, (v1 - v0) // 3)
            d.rectangle([ue, ve, ue, ve], fill=hexc(COL["eye"]))
        return

    if role == "bill":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], hexc(COL["bill"]))
        for key in ("west", "east"):
            u0, v0, u1, v1 = r[key]
            d.rectangle([u0, v1 - 1, u0, v1 - 1], fill=hexc(COL["bill_spot"]))
        return

    if role == "tail":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], white)
        u0, v0, u1, v1 = r["back"]
        d.rectangle([u0, v0, u1 - 1, v0], fill=mantle_lo)
        return

    if role == "wing":
        fill(r["belly"], white)
        fill(r["back"], mantle)
        for key in ("west", "north", "east", "south"):
            fill(r[key], mantle_lo)
        return

    if role == "wing_tip":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], tip)
        for key in ("back", "belly"):
            u0, v0, u1, v1 = r[key]
            if v1 < v0:
                v0, v1 = v1, v0
            mu = u0 + max(1, (u1 - u0) * 2 // 3)
            mv = v0 + max(0, (v1 - v0) // 2)
            d.rectangle([mu, mv, mu, mv], fill=hexc(COL["mirror"]))
        for key in ("back",):
            u0, v0, u1, v1 = r[key]
            d.rectangle([u0, v0, u0, v1 - 1], fill=mantle)
        return

    for key in ("back", "belly", "west", "north", "east", "south"):
        fill(r[key], white)

def build_sheet(atlas_w=32):
    bx = boxes()
    placed, used = pack(bx, atlas_w)
    atlas_h = 1 << max(4, math.ceil(math.log2(max(used, 1))))
    check_pack(placed, bx, atlas_w, atlas_h)

    img = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for name, k, l, m, role in bx:
        i, j = placed[name]
        paint_box(d, i, j, k, l, m, role)
    return img, placed, atlas_w, atlas_h, bx, used

JAVA_HEADER = '''package com.oceania.client.render;

public final class SeagullGeometry {
'''

def emit_java(path):
    bx = boxes()
    placed, used = pack(bx, 32)
    out = [JAVA_HEADER]
    a = out.append
    span_u, span_b = span_check()
    a("\tpublic static final float SPAN = %dF;" % span_u)
    a("\tpublic static final int TEX_W = 32, TEX_H = 32;")
    a("\tpublic static final float Y_AXIS = %.1fF;" % Y_AXIS)
    a("")
    a("\tpublic static final float DIHEDRAL = 0.20F;")
    a("\tpublic static final float CARPAL = -0.34F;")
    a("\tpublic static final float SWEEP = 0.18F;")
    a("\tpublic static final float FLAP_BASE = 0.62F;")
    a("\tpublic static final float FLAP_TIP = 0.46F;")
    a("\tpublic static final float FLAP_LAG = 0.95F;")
    a("\tpublic static final int BEAT_TICKS = 7;")
    a("")
    for name, k, l, m, _role in bx:
        a("\tpublic static final float %s_W = %dF, %s_H = %dF, %s_D = %dF;"
          % (name.upper(), k, name.upper(), l, name.upper(), m))
    a("")
    for name, _k, _l, _m, _role in bx:
        i, j = placed[name]
        a("\tpublic static final int %s_U = %d, %s_V = %d;" % (name.upper(), i, name.upper(), j))
    a("")
    a("\tprivate SeagullGeometry() {")
    a("\t}")
    a("}")
    import io as _io
    with _io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(out) + "\n")

def main():
    import os
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    tex = os.path.join(repo, "src", "main", "resources", "assets", "oceania",
                       "textures", "entity", "seagull")
    java = os.path.join(repo, "src", "client", "java", "com", "oceania", "client", "render")
    os.makedirs(tex, exist_ok=True)
    img, placed, aw, ah, bx, used = build_sheet()
    img.save(os.path.join(tex, "seagull.png"))
    emit_java(os.path.join(java, "SeagullGeometry.java"))
    span_u, span_b = span_check()
    print("sheet %dx%d, shelves used %d, %d boxes" % (aw, ah, used, len(bx)))
    print("  wingspan %d units = %.2f blocks, wing aspect %.1f"
          % (span_u, span_b, aspect_ratio()))
    print("  wrote %s" % os.path.join(tex, "seagull.png"))
    print("  wrote %s" % os.path.join(java, "SeagullGeometry.java"))

if __name__ == "__main__":
    main()
