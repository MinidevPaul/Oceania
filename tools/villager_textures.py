import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import model_preview as mp

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(REPO, "src", "main", "resources", "assets", "minecraft",
                   "textures", "entity", "villager")

def box(i, j, kx, ly, mz):
    w, x, y, z = i, i + mz, i + mz + kx, i + mz + 2 * kx
    aa, ab = i + 2 * mz + kx, i + 2 * mz + 2 * kx
    ac, ad, ae = j, j + mz, j + mz + ly
    return {
        "down": (x, ac, y, ad),
        "up": (y, ac, z, ad),
        "west": (w, ad, x, ae),
        "north": (x, ad, y, ae),
        "east": (y, ad, aa, ae),
        "south": (aa, ad, ab, ae),
        "all": (i, j, ab, ae),
    }

P = {
    "head": box(0, 0, 8, 10, 8),
    "hat": box(32, 0, 8, 10, 8),
    "hat_rim": box(30, 47, 16, 16, 1),
    "nose": box(24, 0, 2, 4, 2),
    "body": box(16, 20, 8, 12, 6),
    "jacket": box(0, 38, 8, 20, 6),
    "arm": box(44, 22, 4, 8, 4),
    "shoulder": box(40, 38, 8, 4, 4),
    "leg": box(0, 22, 4, 12, 4),
}
SIDES = ("west", "north", "east", "south")

SKIN = (141, 94, 61, 255)
SKIN_SH = (114, 74, 47, 255)
SKIN_HI = (166, 116, 78, 255)
HAIR = (33, 24, 18, 255)
HAIR_HI = (56, 41, 29, 255)
TATTOO = (62, 43, 34, 255)
TAPA = (217, 197, 163, 255)
TAPA_SH = (186, 166, 132, 255)
OCHRE = (177, 123, 61, 255)
RUST = (143, 75, 47, 255)
WOVEN = (201, 170, 107, 255)
WOVEN_SH = (168, 138, 82, 255)
SHELL = (239, 231, 211, 255)
BONE = (223, 213, 189, 255)
TOOTH = (236, 228, 205, 255)
GREENSTONE = (62, 122, 90, 255)
PEARL = (226, 232, 238, 255)
LEAF = (87, 125, 59, 255)
SOOT = (59, 55, 51, 255)
FEATHER_R = (179, 59, 47, 255)
FEATHER_Y = (215, 163, 59, 255)
HIDE = (121, 83, 53, 255)
NET = (177, 161, 129, 255)
INK = (41, 45, 59, 255)
EYE_W = (238, 238, 238, 255)
EYE_D = (46, 36, 30, 255)
TEAL = (46, 104, 104, 255)
CREAM = (238, 228, 206, 255)
CHARCOAL = (46, 42, 40, 255)
CLEAR = (0, 0, 0, 0)

def sheet():
    return Image.new("RGBA", (64, 64), CLEAR)

def fill(im, rect, c):
    x0, y0, x1, y1 = rect
    ImageDraw.Draw(im).rectangle([x0, y0, x1 - 1, y1 - 1], fill=c)

def faces(im, part, c, which=SIDES):
    for name in which:
        fill(im, P[part][name], c)

def cap(im, part, c):
    faces(im, part, c, ("down", "up"))

def band(im, part, r0, r1, c, which=SIDES):
    for name in which:
        x0, y0, x1, _ = P[part][name]
        fill(im, (x0, y0 + r0, x1, y0 + r1), c)

def stripe(im, part, face, r0, r1, c0, c1, step=2):
    x0, y0, x1, _ = P[part][face]
    for x in range(x0, x1):
        fill(im, (x, y0 + r0, x + 1, y0 + r1), c0 if (x - x0) % step == 0 else c1)

def dots(im, part, r0, r1, c, step=3, which=SIDES):
    for name in which:
        x0, y0, x1, _ = P[part][name]
        for x in range(x0, x1):
            for y in range(y0 + r0, y0 + r1):
                if (x - x0 + y - y0) % step == 0:
                    im.putpixel((x, y), c)

def px(im, part, face, dx, dy, c):
    x0, y0, _, _ = P[part][face]
    im.putpixel((x0 + dx, y0 + dy), c)

def base_skin():
    im = sheet()

    faces(im, "head", SKIN)
    cap(im, "head", HAIR)
    band(im, "head", 0, 2, HAIR)
    band(im, "head", 2, 3, HAIR_HI)
    fill(im, P["head"]["south"], SKIN_SH)
    band(im, "head", 0, 3, HAIR, ("south",))

    f = P["head"]["north"]
    fill(im, (f[0], f[1] + 3, f[2], f[1] + 4), HAIR)
    for dx in (1, 5):
        fill(im, (f[0] + dx, f[1] + 5, f[0] + dx + 2, f[1] + 7), EYE_W)
        fill(im, (f[0] + dx + 1, f[1] + 5, f[0] + dx + 2, f[1] + 7), EYE_D)
    fill(im, (f[0] + 3, f[1] + 8, f[0] + 5, f[1] + 9), SKIN_SH)

    faces(im, "nose", SKIN_HI)
    cap(im, "nose", SKIN)

    faces(im, "body", SKIN)
    cap(im, "body", SKIN)
    fill(im, P["body"]["south"], SKIN_SH)
    band(im, "body", 10, 12, SKIN_SH, ("north",))

    faces(im, "arm", SKIN)
    cap(im, "arm", SKIN)
    band(im, "arm", 1, 2, TATTOO)
    band(im, "arm", 4, 5, TATTOO)

    faces(im, "shoulder", SKIN)
    cap(im, "shoulder", SKIN)
    band(im, "shoulder", 1, 2, TATTOO)

    faces(im, "leg", SKIN)
    cap(im, "leg", SKIN)
    band(im, "leg", 2, 3, TATTOO)
    band(im, "leg", 4, 5, TATTOO)
    return im

def type_jungle():
    im = sheet()

    band(im, "body", 0, 2, SHELL)
    band(im, "body", 2, 3, TAPA_SH)

    for name in SIDES:
        x0, y0, x1, y1 = P["jacket"][name]
        fill(im, (x0, y0 + 10, x1, y1), TAPA)
        fill(im, (x0, y0 + 10, x1, y0 + 11), TAPA_SH)
    stripe(im, "jacket", "north", 13, 15, OCHRE, TAPA)
    stripe(im, "jacket", "south", 13, 15, OCHRE, TAPA)
    stripe(im, "jacket", "west", 13, 15, OCHRE, TAPA)
    stripe(im, "jacket", "east", 13, 15, OCHRE, TAPA)
    band(im, "jacket", 17, 18, RUST)
    return im

SKULL, BROW, EYES, CHIN = 0, 3, (5, 7), 10

def shade(c, k=0.8):
    return (int(c[0] * k), int(c[1] * k), int(c[2] * k), c[3])

BRIM_ROW = 4

def _hat(im, crown, brim=None, rows=None, crown_top=True):
    if rows is None:
        rows = (SKULL, BRIM_ROW if brim is not None else BROW)
    if rows[1] >= EYES[0]:
        raise ValueError("headwear reaching row %d covers the eyes at rows %d-%d" % (rows[1], *EYES))
    band(im, "hat", rows[0], rows[1], crown)
    if crown_top:
        cap(im, "hat", crown)
    if brim is not None:
        for face in ("north", "south"):
            fill(im, P["hat_rim"][face], brim)
        for face in ("down", "up", "west", "east"):
            fill(im, P["hat_rim"][face], shade(brim))

def _garment(im, c, r0=10, r1=18, hem=None):
    for name in SIDES:
        x0, y0, x1, _ = P["jacket"][name]
        fill(im, (x0, y0 + r0, x1, y0 + r1), c)
    if hem is not None:
        band(im, "jacket", r1 - 2, r1, hem)

def _sleeves(im, c, trim=None):
    faces(im, "shoulder", c)
    cap(im, "shoulder", c)
    if trim is not None:
        band(im, "shoulder", 0, 1, trim)
        band(im, "shoulder", 3, 4, trim)

def fisherman():
    im = sheet()
    _hat(im, WOVEN, brim=WOVEN)
    stripe(im, "hat", "north", SKULL, BRIM_ROW, WOVEN_SH, WOVEN)
    _sleeves(im, NET, TEAL)
    dots(im, "shoulder", 1, 3, TEAL, 2)
    _garment(im, TEAL, 10, 16, hem=WOVEN)
    return im

def fletcher():
    im = sheet()
    band(im, "hat", 2, BROW, HIDE)
    for dx in (3, 4):
        px(im, "hat", "north", dx, 1, FEATHER_R)
        px(im, "hat", "north", dx, 0, FEATHER_R)
    _sleeves(im, HIDE, FEATHER_R)
    _garment(im, CREAM, 10, 16, hem=HIDE)
    return im

def shepherd():
    im = sheet()
    _hat(im, CREAM)
    stripe(im, "hat", "north", SKULL, BROW, RUST, CREAM)
    _sleeves(im, CREAM, RUST)
    stripe(im, "shoulder", "north", 1, 3, RUST, OCHRE)
    _garment(im, CREAM, 10, 18, hem=RUST)
    stripe(im, "jacket", "north", 13, 15, RUST, CREAM)
    band(im, "arm", 6, 8, RUST)
    return im

def butcher():
    im = sheet()
    _hat(im, LEAF)
    _sleeves(im, CHARCOAL, SOOT)
    _garment(im, TAPA, 10, 18, hem=CHARCOAL)
    stripe(im, "jacket", "north", 12, 15, SOOT, TAPA, 3)
    return im

def farmer():
    im = sheet()
    _hat(im, WOVEN, brim=WOVEN)
    stripe(im, "hat_rim", "north", 0, 16, WOVEN_SH, WOVEN, 3)
    _sleeves(im, LEAF, WOVEN)
    _garment(im, WOVEN, 10, 17, hem=LEAF)
    fill(im, (P["jacket"]["north"][0] + 1, P["jacket"]["north"][1] + 11,
              P["jacket"]["north"][0] + 4, P["jacket"]["north"][1] + 14), HIDE)
    return im

def cartographer():
    im = sheet()
    _hat(im, WOVEN, rows=(1, BROW), crown_top=False)
    band(im, "hat", 1, 2, WOVEN_SH)
    _sleeves(im, INK, SHELL)
    dots(im, "shoulder", 1, 3, SHELL, 3)
    band(im, "body", 0, 2, SHELL)
    _garment(im, INK, 10, 15, hem=SHELL)
    return im

def librarian():
    im = sheet()
    _hat(im, CREAM)
    band(im, "hat", 2, 3, RUST)
    _sleeves(im, CREAM, BONE)
    band(im, "body", 0, 2, BONE)
    _garment(im, CREAM, 10, 18, hem=RUST)
    fill(im, (P["jacket"]["north"][0] + 3, P["jacket"]["north"][1] + 10,
              P["jacket"]["north"][0] + 5, P["jacket"]["north"][1] + 18), RUST)
    return im

def cleric():
    im = sheet()
    _hat(im, RUST)
    for dx, c in ((1, FEATHER_R), (3, FEATHER_Y), (5, FEATHER_R), (7, FEATHER_Y)):
        for dy in (0, 1):
            px(im, "hat", "north", dx, dy, c)
            px(im, "hat", "south", dx, dy, c)
    _sleeves(im, OCHRE, FEATHER_R)
    band(im, "body", 0, 2, BONE)
    _garment(im, OCHRE, 10, 16, hem=FEATHER_R)
    return im

def leatherworker():
    im = sheet()
    _sleeves(im, HIDE, SHELL)
    band(im, "body", 0, 3, SHELL)
    _garment(im, HIDE, 10, 17, hem=SHELL)
    px(im, "jacket", "north", 1, 12, BONE)
    px(im, "jacket", "north", 6, 12, BONE)
    return im

def nitwit():
    im = sheet()
    _hat(im, LEAF)
    _sleeves(im, LEAF)
    _garment(im, LEAF, 10, 17, hem=TAPA_SH)
    return im

PROFESSIONS = {
    "fisherman": ("Fisher", fisherman),
    "fletcher": ("Boatwright", fletcher),
    "shepherd": ("Weaver", shepherd),
    "butcher": ("Pitkeeper", butcher),
    "farmer": ("Seedkeeper", farmer),
    "cartographer": ("Wayfinder", cartographer),
    "librarian": ("Chant-keeper", librarian),
    "cleric": ("Shaman", cleric),
    "leatherworker": ("Shellworker", leatherworker),
    "nitwit": ("Nitwit", nitwit),
}

BADGE = (10, 54, 14, 58)
TIERS = {
    "stone": ("shell", SHELL),
    "iron": ("bone", BONE),
    "gold": ("tooth", TOOTH),
    "emerald": ("greenstone", GREENSTONE),
    "diamond": ("pearl", PEARL),
}

def badge(colour):
    im = sheet()
    x0, y0, x1, y1 = BADGE
    fill(im, (x0 + 1, y0, x1 - 1, y1), colour)
    fill(im, (x0, y0 + 1, x1, y1 - 1), colour)
    im.putpixel((x0 + 1, y0 + 1), SKIN_SH)
    return im

def villager_model():
    root = mp.Part("root")
    head = mp.Part("head", cubes=[mp.Cube((0, 0), (-4, -10, -4), (8, 10, 8))])
    hat = mp.Part("hat", cubes=[mp.Cube((32, 0), (-4, -10, -4), (8, 10, 8), inflate=0.51)])
    hat.add(mp.Part("hat_rim", rot=(-math.pi / 2, 0, 0),
                    cubes=[mp.Cube((30, 47), (-8, -8, -6), (16, 16, 1))]))
    head.add(hat)
    head.add(mp.Part("nose", pivot=(0, -2, 0), cubes=[mp.Cube((24, 0), (-1, -1, -6), (2, 4, 2))]))
    root.add(head)

    body = mp.Part("body", cubes=[mp.Cube((16, 20), (-4, 0, -3), (8, 12, 6))])
    body.add(mp.Part("jacket", cubes=[mp.Cube((0, 38), (-4, 0, -3), (8, 20, 6), inflate=0.5)]))
    root.add(body)

    root.add(mp.Part("arms", pivot=(0, 3, -1), rot=(-0.75, 0, 0), cubes=[
        mp.Cube((44, 22), (-8, -2, -2), (4, 8, 4)),
        mp.Cube((44, 22), (4, -2, -2), (4, 8, 4), mirror=True),
        mp.Cube((40, 38), (-4, 2, -2), (8, 4, 4)),
    ]))
    root.add(mp.Part("right_leg", pivot=(-2, 12, 0),
                     cubes=[mp.Cube((0, 22), (-2, 0, -2), (4, 12, 4))]))
    root.add(mp.Part("left_leg", pivot=(2, 12, 0),
                     cubes=[mp.Cube((0, 22), (-2, 0, -2), (4, 12, 4), mirror=True)]))
    return root

def stack(*layers):
    out = layers[0].copy()
    for layer in layers[1:]:
        out = Image.alpha_composite(out, layer)
    return out

def scratch():
    d = os.environ.get("VILLAGER_SCRATCH")
    if not d:
        import tempfile
        d = os.path.join(tempfile.gettempdir(), "oceania_villagers")
    os.makedirs(d, exist_ok=True)
    return d

def contact_sheet(base, wrap, tier, out):
    model = villager_model()
    cell_w, cell_h, pad = 300, 150, 6
    rows = len(PROFESSIONS)
    img = Image.new("RGB", (64 * 2 + pad * 3 + cell_w * 2, (cell_h + pad) * rows + pad),
                    (248, 248, 246))
    draw = ImageDraw.Draw(img)
    for n, (key, (label, paint)) in enumerate(sorted(PROFESSIONS.items())):
        y = pad + n * (cell_h + pad)
        overlay = paint()
        tex = stack(base, wrap, overlay) if key == "nitwit" else stack(base, wrap, overlay, tier)
        img.paste(tex.resize((128, 128), Image.NEAREST).convert("RGB"), (pad, y + 10))
        x = pad * 2 + 128
        for view in ("front", "three_quarter"):
            shot = mp.render(model, texture=tex, view=view, size=(cell_w, cell_h),
                             bg=(248, 248, 246))
            img.paste(shot.convert("RGB"), (x, y))
            x += cell_w
        draw.text((pad + 2, y), "%s  (%s)" % (label, key), fill=(40, 40, 40))
    img.save(out)
    return out

def main():
    install = "--install" in sys.argv
    base, wrap = base_skin(), type_jungle()
    tier = badge(GREENSTONE)

    d = scratch()
    sheet_path = contact_sheet(base, wrap, tier, os.path.join(d, "villagers.png"))
    base.resize((256, 256), Image.NEAREST).save(os.path.join(d, "sheet_villager.png"))
    wrap.resize((256, 256), Image.NEAREST).save(os.path.join(d, "sheet_type_jungle.png"))
    print("previews ->", sheet_path)
    print("          ", os.path.join(d, "sheet_villager.png"))
    print("          ", os.path.join(d, "sheet_type_jungle.png"))

    if not install:
        print("\nnot installed. re-run with --install to install.")
        return

    os.makedirs(os.path.join(TEX, "profession"), exist_ok=True)
    os.makedirs(os.path.join(TEX, "profession_level"), exist_ok=True)
    os.makedirs(os.path.join(TEX, "type"), exist_ok=True)
    base.save(os.path.join(TEX, "villager.png"))
    wrap.save(os.path.join(TEX, "type", "jungle.png"))
    for key, (_, paint) in PROFESSIONS.items():
        paint().save(os.path.join(TEX, "profession", key + ".png"))
    for name, (_, colour) in TIERS.items():
        badge(colour).save(os.path.join(TEX, "profession_level", name + ".png"))
    print("\ninstalled %d files into %s" % (2 + len(PROFESSIONS) + len(TIERS), TEX))

if __name__ == "__main__":
    main()
