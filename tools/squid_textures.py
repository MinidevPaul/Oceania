from PIL import Image, ImageDraw

W, H = 64, 32
BODY = dict(u=0, v=0, k=12, l=16, m=12)
TENT = dict(u=48, v=0, k=2, l=18, m=2)

VARIANTS = {
    "squid":       ("#3f5a73", "#7792a8", "#4a6b85", "#2d4357"),
    "squid_red":   ("#8c3330", "#c9736a", "#9c4440", "#66211f"),
    "squid_white": ("#d9d4cc", "#f4f1ea", "#e2ddd4", "#b6afa4"),
}

def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)

def paint_cube(d, box, back, belly, mid, speck, mantle):
    u, v, k, l, m = box["u"], box["v"], box["k"], box["l"], box["m"]
    x, y, z = u + m, u + m + k, u + m + 2 * k
    aa, ab = u + 2 * m + k, u + 2 * m + 2 * k
    ac, ad, ae = v, v + m, v + m + l

    def fill(u0, v0, u1, v1, col):
        if u1 > u0 and v1 > v0:
            d.rectangle([u0, v0, u1 - 1, v1 - 1], fill=col)

    fill(x, ac, y, ad, back)
    fill(y, ac, z, ad, belly)

    for u0, u1 in ((u, x), (x, y), (y, aa), (aa, ab)):
        if mantle:
            for row in range(ad, ae):
                t = (row - ad) / max(1, ae - ad - 1)
                fill(u0, row, u1, row + 1, shade(back, 1.0 + 0.55 * t))
            fill(u0, ad, u1, ad + 1, shade(back, 0.82))
        else:
            fill(u0, ad, u1, ae, mid)

    if mantle:
        for i, (u0, u1) in enumerate(((u, x), (x, y), (y, aa), (aa, ab))):
            for n in range((u1 - u0) * (ae - ad) // 14):
                su = u0 + (n * 7 + i * 3) % max(1, u1 - u0)
                sv = ad + 2 + (n * 5 + i * 2) % max(1, ae - ad - 3)
                d.point((su, sv), fill=speck)

def build(name, palette):
    back, belly, mid, speck = (hexc(c) for c in palette)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    paint_cube(d, BODY, back, belly, mid, speck, mantle=True)
    paint_cube(d, TENT, shade(mid, 0.9), shade(mid, 1.1), mid, speck, mantle=False)
    img.save(f"{name}.png")
    return img

def main():
    for name, palette in VARIANTS.items():
        img = build(name, palette)
        print("wrote", f"{name}.png", img.size)

    scale = 8
    tiles = [(n, Image.open(f"{n}.png").convert("RGBA")) for n in VARIANTS]
    pad = 18
    sheet = Image.new("RGB", (W * scale + pad * 2, (H * scale + 34) * 3 + pad), (14, 22, 32))
    for i, (n, im) in enumerate(tiles):
        big = im.resize((W * scale, H * scale), Image.NEAREST)
        bg = Image.new("RGB", big.size, (30, 40, 52))
        dd = ImageDraw.Draw(bg)
        for yy in range(0, big.height, 16):
            for xx in range(0, big.width, 16):
                if (xx // 16 + yy // 16) % 2:
                    dd.rectangle([xx, yy, xx + 15, yy + 15], fill=(38, 50, 64))
        bg.paste(big, (0, 0), big)
        oy = pad + i * (H * scale + 34)
        sheet.paste(bg, (pad, oy))
        ImageDraw.Draw(sheet).text((pad, oy + H * scale + 8), n, fill=(226, 234, 242))
    sheet.save("squid_variants.png")
    print("wrote squid_variants.png", sheet.size)

if __name__ == "__main__":
    main()
