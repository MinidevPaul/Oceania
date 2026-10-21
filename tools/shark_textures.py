import math
from PIL import Image, ImageDraw

R_HEAD, R_T1, R_T2, R_PED = 0.86, 1.00, 0.84, 0.48
L_T1, L_T2, L_PED = 9.0, 8.0, 7.0
OVERLAP = 1.0

VARIANTS = {
    "reef":        dict(girth=5.2, snout=4.5, head=8.0, cu=9.4, cl=5.1, cn=9.0,
                        dh=4.9, dl=6.1, pect=6.5, wr=1.20,
                        back="#6d7b87", belly="#eae6dd", split=0.66, hard=True,
                        tips="#1b2127", eye="#11151a", bars=None),
    "great_white": dict(girth=6.4, snout=4.5, head=9.0, cu=8.7, cl=6.4, cn=10.0,
                        dh=5.2, dl=6.5, pect=7.5, wr=1.38,
                        back="#626a71", belly="#f2efe8", split=0.58, hard=True,
                        tips=None, eye="#0d1013", bars=None),
    "tiger":       dict(girth=5.8, snout=4.0, head=8.0, cu=10.7, cl=4.2, cn=9.0,
                        dh=4.4, dl=5.5, pect=6.5, wr=1.38,
                        back="#79725f", belly="#e6dfc9", split=0.72, hard=False,
                        tips=None, eye="#15130e", bars="#59533f"),
    "bull":        dict(girth=6.6, snout=3.5, head=8.0, cu=8.5, cl=4.2, cn=8.0,
                        dh=4.6, dl=5.8, pect=5.5, wr=1.38,
                        back="#86816e", belly="#ded7c5", split=0.78, hard=False,
                        tips=None, eye="#161411", bars=None),
}

def ri(v):
    return max(1, int(round(v)))

def boxes_for(v):
    g, wr = v["girth"], v["wr"]
    h1, w1 = ri(g * 2 * R_T1), ri(g * wr * R_T1)
    h2, w2 = ri(g * 2 * R_T2), ri(g * wr * R_T2)
    h3, w3 = ri(g * 2 * R_PED), ri(g * wr * R_PED)
    hh, wh = ri(g * 2 * R_HEAD), ri(g * wr * R_HEAD)
    ws, hs = ri(wh * 0.60), ri(hh * 0.30)
    wt, ht = ri(wh * 0.36), ri(hh * 0.18)
    return [
        ("body1",    w1, h1, ri(L_T1),                     "body"),
        ("body2",    w2, h2, ri(L_T2 + OVERLAP),           "body"),
        ("peduncle", w3, h3, ri(L_PED + OVERLAP),          "body"),
        ("head",     wh, hh, ri(v["head"] + OVERLAP),      "body"),
        ("snout",    ws, hs, ri(v["snout"] * 0.62 + OVERLAP), "snout"),
        ("snout_tip", wt, ht, ri(v["snout"] * 0.38 + OVERLAP), "snout"),
        ("jaw",      ri(wh * 0.76), 2, ri(v["head"] + v["snout"] * 0.45), "jaw"),
        ("caudal",     1, ri(v["cu"] * 0.58), ri(v["cn"] + OVERLAP),       "fin_up"),
        ("caudal_tip", 1, ri(v["cu"] * 0.48), ri(v["cn"] * 0.70),          "fin_up"),
        ("caudal_lower", 1, ri(v["cl"]), ri(v["cn"] * 0.62 + OVERLAP),     "fin_dn"),
        ("dorsal",     1, ri(v["dh"] * 0.55), ri(v["dl"]),                 "fin_up"),
        ("dorsal_tip", 1, ri(v["dh"] * 0.52), ri(v["dl"] * 0.65),          "fin_up"),
        ("dorsal2",    1, ri(v["dh"] * 0.34), 3,                           "fin_up"),
        ("pelvic",     w2, 1, 3,                                          "fin_dn"),
        ("pectoral",     ri(v["pect"] * 0.55), 1, ri(max(3, v["pect"] * 0.55)), "fin_dn"),
        ("pectoral_tip", ri(v["pect"] * 0.50), 1, ri(max(2, v["pect"] * 0.34)), "fin_dn"),
    ]

def pack(boxes, atlas_w):
    items = sorted(boxes, key=lambda b: -(b[3] + b[2]))
    placed, shelves = {}, []
    for name, k, l, m, _role in items:
        fw, fh = 2 * m + 2 * k, m + l
        if fw > atlas_w:
            raise ValueError(f"{name} footprint {fw} wider than atlas {atlas_w}")
        for sh in shelves:
            if sh[1] >= fh and sh[2] + fw <= atlas_w:
                placed[name] = (sh[2], sh[0])
                sh[2] += fw
                break
        else:
            y = shelves[-1][0] + shelves[-1][1] if shelves else 0
            shelves.append([y, fh, fw])
            placed[name] = (0, y)
    used = shelves[-1][0] + shelves[-1][1] if shelves else 0
    return placed, used

def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)

def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def paint_box(img, d, i, j, k, l, m, role, v):
    back, belly = hexc(v["back"]), hexc(v["belly"])
    tips = hexc(v["tips"]) if v["tips"] else None

    x, y, z = i + m, i + m + k, i + m + 2 * k
    aa, ab = i + 2 * m + k, i + 2 * m + 2 * k
    ac, ad, ae = j, j + m, j + m + l

    def fill(u0, v0, u1, v1, col):
        if u1 > u0 and v1 > v0:
            d.rectangle([u0, v0, u1 - 1, v1 - 1], fill=col)

    if role.startswith("fin"):
        fill(i, ac, ab, ae, shade(back, 0.84))
        fill(x, ad, y, ad + max(1, l // 6), shade(back, 0.70))
        if tips:
            if role == "fin_up":
                for u0, u1 in ((i, x), (x, y), (y, aa), (aa, ab)):
                    fill(u0, ad, u1, ad + max(2, l // 5), tips)
            else:
                for u0, u1 in ((i, x), (x, y), (y, aa), (aa, ab)):
                    fill(u0, ae - max(2, l // 5), u1, ae, tips)
        return

    if role == "jaw":
        fill(i, ac, ab, ae, belly)
        fill(x, ac, y, ad, shade(belly, 0.93))
        for u0, u1 in ((i, x), (y, aa)):
            fill(u0, ad, u1, ad + 1, (246, 244, 236))
            for u in range(u0, u1, 2):
                fill(u, ad + 1, u + 1, ad + 2, (246, 244, 236))
        return

    split_l = max(1, int(round(l * v["split"])))

    fill(x, ac, y, ad, back)
    fill(x, ac, y, ac + max(1, m // 8), shade(back, 1.14))
    fill(y, ac, z, ad, belly)

    for u0, u1 in ((i, x), (x, y), (y, aa), (aa, ab)):
        fill(u0, ad, u1, ad + split_l, back)
        fill(u0, ad + split_l, u1, ae, belly)
        if v["hard"]:
            fill(u0, ad + split_l - 1, u1, ad + split_l, shade(back, 0.68))
        else:
            fill(u0, ad + split_l - 1, u1, ad + split_l + 1, mix(back, belly, 0.5))

    if v["bars"]:
        bar = hexc(v["bars"])
        for u0, u1 in ((i, x), (y, aa)):
            for u in range(u0 + 1, u1, 3):
                fill(u, ad, u + 1, ad + int(split_l * 0.8), bar)
        for u in range(x + 1, y, 3):
            fill(u, ac, u + 1, ad, bar)

def paint_head_details(img, d, placed, bx, v):
    name, k, l, m, _ = next(b for b in bx if b[0] == "head")
    i, j = placed["head"]
    x, y, aa = i + m, i + m + k, i + 2 * m + k
    ad = j + m
    back = hexc(v["back"])
    eye = hexc(v["eye"])
    split_l = max(1, int(round(l * v["split"])))

    for u0, u1 in ((i, x), (y, aa)):
        for s in range(5):
            u = u1 - 2 - s * 2
            if u <= u0:
                break
            d.rectangle([u, ad + 2, u, ad + max(3, split_l - 1)], fill=shade(back, 0.74))
        ue = u0 + 1
        ve = ad + max(1, split_l // 3)
        d.rectangle([ue, ve, ue + 1, ve], fill=eye)

def build(name, v, atlas_w=128):
    bx = boxes_for(v)
    placed, used = pack(bx, atlas_w)
    atlas_h = 1 << max(6, math.ceil(math.log2(max(used, 1))))

    img = Image.new("RGBA", (atlas_w, atlas_h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for bname, k, l, m, role in bx:
        i, j = placed[bname]
        paint_box(img, d, i, j, k, l, m, role, v)
    paint_head_details(img, d, placed, bx, v)

    img.save(f"shark_tex_{name}.png")
    return placed, bx, atlas_w, atlas_h, used

def main():
    print("generated by shark_textures.py - paste into SharkModel")
    sheets = {}
    for name, v in VARIANTS.items():
        placed, bx, aw, ah, used = build(name, v)
        sheets[name] = (aw, ah)
        print(f"\n--- {name}: atlas {aw}x{ah} (shelves used {used}) ---")
        for bname, k, l, m, _role in bx:
            i, j = placed[bname]
            print(f"  {bname:<14} texOffs({i:>3}, {j:>3})   box {k}x{l}x{m}")
    print()
    for n, (w, h) in sheets.items():
        print(f"LayerDefinition.create(mesh, {w}, {h})  for {n}")

if __name__ == "__main__":
    main()
