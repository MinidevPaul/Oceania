import math

from PIL import Image, ImageDraw

from boxpack import Rnd, check_pack, hexc, mix, pack, regions as _regions, ri, shade

TL = 384.0

Z_NOSE = -TL / 2.0

Y_AXIS = -8.0

LM = dict(
    rostrum_mid=0.072,
    rostrum_end=0.175,
    head_end=0.260,
    body1_end=0.440,
    body2_end=0.600,
    body3_end=0.750,
    ped1_end=0.855,
    ped_end=0.950,
    jaw_hinge_off=0.200,
    throat_start=0.100,
    throat_mid=0.270,
    throat_end=0.580,
)

PROP = dict(
    rostrum_h=0.052, rostrum_w=0.092,
    rtip_h=0.040, rtip_w=0.070,
    head_h=0.095, head_w=0.120,
    body1_h=0.130, body1_w=0.150,
    body2_h=0.120, body2_w=0.135,
    body3_h=0.100, body3_w=0.105,
    ped1_h=0.072, ped1_w=0.058,
    ped2_h=0.048, ped2_w=0.038,
    fluke_span=0.215,
    fluke_chord=0.051,
    flipper_len=0.132,
    flipper_chord=0.037,
    flipper_at=0.310,
    dorsal_h=0.012,
    dorsal_chord=0.030,
    dorsal_at=0.750,
    jaw_h=0.030,
    jaw_w=0.095,
    jaw_hinge=0.200,
    blow_at=0.165,
    ridge_chord=0.170,
    throat_w=0.110, throat_h=0.045,
)

POUCH_STAGES = (
    (0.00, 1.00, 1.00),
    (0.18, 1.22, 1.75),
    (0.52, 1.42, 2.55),
    (0.84, 1.60, 3.20),
)

JAW_FULL = 1.36

COL = dict(
    back="#52687C",
    back_hi="#6E8192",
    back_lo="#45596C",
    flank="#7A8A98",
    belly="#C3CBC6",
    pleat="#B9BFB4",
    pleat_line="#A6AEA6",
    fin_dark="#46596B",
    fin_pale="#B4BDBA",
    eye="#14181C",
    ridge="#5E748A",
)

SPLIT = 0.72
SPLIT_SOFT = 3

def u(frac):
    return ri(frac * TL)

def offsets():
    o = {"nose": 0}
    for key, frac in LM.items():
        o[key] = ri(frac * TL)
    o["tail"] = ri(TL)
    return o

def stations():
    return {k: Z_NOSE + v for k, v in offsets().items()}

def seg_len(a, b):
    o = offsets()
    return o[b] - o[a]

def st_off(name):
    return offsets()[name]

GULLET_SLICES = 22
GULLET_SLICE_LEN = 2

OVERLAP = 1.0
SEAM = 0.02

def boxes():
    p = PROP
    ov = ri(OVERLAP)
    span = u(p["fluke_span"])
    flen = u(p["flipper_len"])
    return [
        ("rtip", u(p["rtip_w"]), u(p["rtip_h"]),
         seg_len("nose", "rostrum_mid") + ov, "head"),
        ("rostrum", u(p["rostrum_w"]), u(p["rostrum_h"]),
         seg_len("rostrum_mid", "rostrum_end") + ov, "head"),
        ("head", u(p["head_w"]), u(p["head_h"]),
         seg_len("rostrum_end", "head_end") + ov, "head"),
        ("ridge", 3, 3, u(p["ridge_chord"]), "ridge"),
        ("blow", ri(u(p["rostrum_w"]) * 0.52), 6, ri(u(p["rostrum_w"]) * 0.42), "blow"),
        ("jaw", u(p["jaw_w"]), u(p["jaw_h"]), u(p["jaw_hinge"]) + ov, "jaw"),
        ("gullet_slice", u(p["throat_w"]), u(p["throat_h"]), GULLET_SLICE_LEN + ov, "throat"),
        ("gullet_step", u(p["throat_w"]), u(p["throat_h"]),
         ri((st_off("body1_end") - st_off("jaw_hinge_off")) * 0.5) + ov, "throat"),
        ("throat2", ri(u(p["throat_w"]) * 0.95), ri(u(p["throat_h"]) * 0.94),
         (st_off("body1_end") - st_off("jaw_hinge_off")) + ov, "throat"),
        ("throat3", ri(u(p["throat_w"]) * 0.74), ri(u(p["throat_h"]) * 0.68),
         (st_off("throat_end") - st_off("body1_end")) + ov, "throat"),
        ("body1", u(p["body1_w"]), u(p["body1_h"]),
         seg_len("head_end", "body1_end") + ov, "body"),
        ("body2", u(p["body2_w"]), u(p["body2_h"]),
         seg_len("body1_end", "body2_end") + ov, "body"),
        ("body3", u(p["body3_w"]), u(p["body3_h"]),
         seg_len("body2_end", "body3_end") + ov, "body"),
        ("ped1", u(p["ped1_w"]), u(p["ped1_h"]),
         seg_len("body3_end", "ped1_end") + ov, "body"),
        ("ped2", u(p["ped2_w"]), u(p["ped2_h"]),
         seg_len("ped1_end", "ped_end") + ov, "body"),
        ("fluke", ri(span * 0.38), 3, u(p["fluke_chord"]) + ov, "fin_flat"),
        ("fluke_tip", ri(span * 0.31), 2, ri(u(p["fluke_chord"]) * 0.68), "fin_flat"),
        ("dorsal", 2, u(p["dorsal_h"]), u(p["dorsal_chord"]), "fin_up"),
        ("flipper", ri(flen * 0.55), 2, u(p["flipper_chord"]), "fin_flat"),
        ("flipper_tip", ri(flen * 0.45), 2, ri(u(p["flipper_chord"]) * 0.72), "fin_flat"),
    ]

def paint_box(d, i, j, k, l, m, role, rnd):
    back, hi, lo = hexc(COL["back"]), hexc(COL["back_hi"]), hexc(COL["back_lo"])
    belly, flank = hexc(COL["belly"]), hexc(COL["flank"])
    r = _regions(i, j, k, l, m)

    def fill(rect, col):
        u0, v0, u1, v1 = rect
        if v1 < v0:
            v0, v1 = v1, v0
        if u1 > u0 and v1 > v0:
            d.rectangle([u0, v0, u1 - 1, v1 - 1], fill=col)

    def mottle(rect, density=0.30):
        u0, v0, u1, v1 = rect
        if v1 < v0:
            v0, v1 = v1, v0
        w, h = u1 - u0, v1 - v0
        if w < 4 or h < 4:
            return
        for _ in range(max(1, int(w * h * density / 150))):
            col = hi if rnd.frac() > 0.45 else lo
            halo = mix(back, col, 0.45)
            cu = rnd.irange(u0 + 1, max(u0 + 2, u1 - 2))
            cv = rnd.irange(v0 + 1, max(v0 + 2, v1 - 2))
            for lobe in range(rnd.irange(2, 4)):
                bw = rnd.irange(max(3, w // 16), max(6, w // 8))
                bh = rnd.irange(max(2, h // 7), max(5, h // 3))
                bu = min(max(cu + rnd.irange(0, max(1, bw)) - bw // 2, u0), u1 - 1)
                bv = min(max(cv + rnd.irange(0, max(1, bh)) - bh // 2, v0), v1 - 1)
                x1 = min(bu + bw - 1, u1 - 1)
                y1 = min(bv + bh - 1, v1 - 1)
                if x1 <= bu or y1 <= bv:
                    continue
                d.rectangle([bu, bv, x1, y1], fill=halo)
                if x1 - bu > 2 and y1 - bv > 1:
                    d.rectangle([bu + 1, bv + 1, x1 - 1, y1 - 1], fill=col)

    if role == "fin_flat":
        fill(r["back"], shade(back, 0.90))
        mottle(r["back"], 0.16)
        fill(r["belly"], hexc(COL["fin_pale"]))
        for side in ("west", "north", "east", "south"):
            fill(r[side], shade(back, 0.82))
        fill(r["south"], shade(back, 0.66))
        return

    if role == "fin_up":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], hexc(COL["fin_dark"]))
        return

    if role == "ridge":
        for key in ("back", "west", "north", "east", "south"):
            fill(r[key], hexc(COL["ridge"]))
        fill(r["back"], shade(hexc(COL["ridge"]), 1.10))
        fill(r["belly"], back)
        return

    if role == "blow":
        for key in ("back", "belly", "west", "north", "east", "south"):
            fill(r[key], shade(back, 1.06))
        u0, v0, u1, v1 = r["back"]
        cu, cv = (u0 + u1) // 2, (v0 + v1) // 2
        d.rectangle([cu - 3, cv - 1, cu - 2, cv + 1], fill=lo)
        d.rectangle([cu + 1, cv - 1, cu + 2, cv + 1], fill=lo)
        return

    if role == "throat":
        pleat, line = hexc(COL["pleat"]), hexc(COL["pleat_line"])
        for key in ("back", "west", "north", "east", "south"):
            fill(r[key], mix(back, belly, 0.62))
        fill(r["belly"], pleat)
        u0, v0, u1, v1 = r["belly"]
        if v1 < v0:
            v0, v1 = v1, v0
        for uu in range(u0 + 1, u1 - 1, 3):
            d.rectangle([uu, v0, uu, v1 - 1], fill=line)
        for key in ("west", "east"):
            u0, v0, u1, v1 = r[key]
            for vv in range(v0 + 1, v1 - 1, 3):
                d.rectangle([u0, vv, u1 - 1, vv], fill=line)
        return

    if role == "jaw":
        pleat, line = hexc(COL["pleat"]), hexc(COL["pleat_line"])
        fill(r["back"], shade(belly, 0.88))
        fill(r["belly"], pleat)
        for key in ("west", "north", "east", "south"):
            fill(r[key], mix(back, belly, 0.5))
        u0, v0, u1, v1 = r["belly"]
        if v1 < v0:
            v0, v1 = v1, v0
        for uu in range(u0 + 1, u1 - 1, 3):
            d.rectangle([uu, v0, uu, v1 - 1], fill=line)
        for key in ("west", "east"):
            su0, sv0, su1, _ = r[key]
            d.rectangle([su0, sv0, su1 - 1, sv0], fill=shade(back, 0.62))
        return

    split_l = max(1, int(round(l * SPLIT)))
    soft = min(SPLIT_SOFT, max(1, split_l - 1))

    fill(r["back"], back)
    fill(r["belly"], belly)
    if role == "body":
        mottle(r["back"], 0.34)

    for key in ("west", "north", "east", "south"):
        u0, v0, u1, v1 = r[key]
        d.rectangle([u0, v0, u1 - 1, v0 + split_l - 1], fill=back)
        d.rectangle([u0, v0 + split_l, u1 - 1, v1 - 1], fill=belly)
        for s in range(soft):
            t = (s + 1) / float(soft + 1)
            d.rectangle([u0, v0 + split_l - soft + s, u1 - 1, v0 + split_l - soft + s],
                        fill=mix(back, flank, t))
        d.rectangle([u0, v0 + split_l, u1 - 1, v0 + split_l],
                    fill=mix(flank, belly, 0.45))
        if role == "body":
            mottle((u0, v0, u1, v0 + split_l - soft), 0.30)

def paint_head_details(d, placed, bx):
    _, k, l, m, _ = next(b for b in bx if b[0] == "head")
    i, j = placed["head"]
    r = _regions(i, j, k, l, m)
    split_l = max(1, int(round(l * SPLIT)))
    for key in ("west", "east"):
        u0, v0, u1, v1 = r[key]
        vm = v0 + split_l - 1
        d.rectangle([u0, vm, u1 - 1, vm], fill=shade(hexc(COL["back"]), 0.66))
        ue = u0 + max(1, (u1 - u0) // 5)
        ve = vm - max(2, split_l // 6)
        d.rectangle([ue, ve, ue + 2, ve + 1], fill=hexc(COL["eye"]))

def build_sheet(atlas_w=512):
    bx = boxes()
    placed, used = pack(bx, atlas_w)
    atlas_h = 1 << max(6, math.ceil(math.log2(max(used, 1))))
    check_pack(placed, bx, atlas_w, atlas_h)

    img = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rnd = Rnd(0x8EA17)
    for name, k, l, m, role in bx:
        i, j = placed[name]
        paint_box(d, i, j, k, l, m, role, rnd)
    paint_head_details(d, placed, bx)
    return img, placed, atlas_w, atlas_h, bx, used

JAVA_HEADER = '''package com.oceania.client.render;

import com.oceania.mob.WhaleEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class WhaleGeometry {
'''

def emit_java(path):
    bx = boxes()
    placed, used = pack(bx, 512)
    st = stations()
    dim = {b[0]: (b[1], b[2], b[3]) for b in bx}

    out = [JAVA_HEADER]
    a = out.append
    a("\tpublic static final float TL = %.1fF;" % TL)
    a("\tpublic static final int TEX_W = 512, TEX_H = 512;")
    a("")
    for key in ("nose", "throat_start", "rostrum_mid", "rostrum_end", "head_end",
                "throat_mid", "body1_end", "throat_end", "body2_end", "body3_end",
                "ped1_end", "ped_end", "tail"):
        if key in st:
            a("\tpublic static final float Z_%s = %.1fF;" % (key.upper(), st[key]))
    a("\tpublic static final float Z_JAW_HINGE = %.1fF;" % st["jaw_hinge_off"])
    a("\tpublic static final float Y_AXIS = %.1fF;" % Y_AXIS)
    a("")
    a("\tpublic static final float JAW_FULL = %.4fF;" % JAW_FULL)
    a("\tpublic static final int GULLET_SLICES = %d;" % GULLET_SLICES)
    a("\tpublic static final float GULLET_SLICE_LEN = %.1fF;" % GULLET_SLICE_LEN)
    a("\tpublic static final float[][] POUCH_STAGES = {")
    for th, sw, sh in POUCH_STAGES:
        a("\t\t{%.2fF, %.2fF, %.2fF}," % (th, sw, sh))
    a("\t};")
    a("")
    for name, k, l, m, _role in bx:
        a("\tpublic static final float %s_W = %dF, %s_H = %dF, %s_D = %dF;"
          % (name.upper(), k, name.upper(), l, name.upper(), m))
    a("")
    for name, _k, _l, _m, _role in bx:
        i, j = placed[name]
        a("\tpublic static final int %s_U = %d, %s_V = %d;" % (name.upper(), i, name.upper(), j))
    a("")
    a("\tprivate WhaleGeometry() {")
    a("\t}")
    a("}")

    import io as _io
    with _io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(out) + "\n")
    return placed, bx, used

def main():
    import os
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    tex = os.path.join(repo, "src", "main", "resources", "assets", "oceania",
                       "textures", "entity", "whale")
    java = os.path.join(repo, "src", "client", "java", "com", "oceania", "client", "render")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(java, exist_ok=True)

    img, placed, aw, ah, bx, used = build_sheet()
    img.save(os.path.join(tex, "whale.png"))
    emit_java(os.path.join(java, "WhaleGeometry.java"))

    print("sheet %dx%d, shelves used %d, %d boxes" % (aw, ah, used, len(bx)))
    print("  wrote %s" % os.path.join(tex, "whale.png"))
    print("  wrote %s" % os.path.join(java, "WhaleGeometry.java"))

if __name__ == "__main__":
    main()
