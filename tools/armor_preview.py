import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import model_preview as mp

OUTER = 1.0
INNER = 0.5
L1 = (64, 0)
L2 = (64, 32)

def cube(tex, origin, size, inflate=0.0, mirror=False, off=(0, 0)):
    return mp.Cube((tex[0] + off[0], tex[1] + off[1]), origin, size, inflate=inflate, mirror=mirror)

def player():
    root = mp.Part("player")
    root.add(mp.Part("head", pivot=(0, 0, 0), cubes=[cube((0, 0), (-4, -8, -4), (8, 8, 8))]))
    root.add(mp.Part("hat", pivot=(0, 0, 0), cubes=[cube((32, 0), (-4, -8, -4), (8, 8, 8), inflate=0.5)]))
    root.add(mp.Part("body", pivot=(0, 0, 0), cubes=[cube((16, 16), (-4, 0, -2), (8, 12, 4))]))
    root.add(mp.Part("right_arm", pivot=(-5, 2, 0), cubes=[cube((40, 16), (-3, -2, -2), (4, 12, 4))]))
    root.add(mp.Part("left_arm", pivot=(5, 2, 0), cubes=[cube((32, 48), (-1, -2, -2), (4, 12, 4))]))
    root.add(mp.Part("right_leg", pivot=(-1.9, 12, 0), cubes=[cube((0, 16), (-2, 0, -2), (4, 12, 4))]))
    root.add(mp.Part("left_leg", pivot=(1.9, 12, 0), cubes=[cube((16, 48), (-2, 0, -2), (4, 12, 4))]))
    return root

def armour(root, slots):
    if "head" in slots:
        root.add(mp.Part("helmet", cubes=[cube((0, 0), (-4, -8, -4), (8, 8, 8), inflate=OUTER, off=L1)]))
        root.add(mp.Part("helmet_hat", cubes=[cube((32, 0), (-4, -8, -4), (8, 8, 8), inflate=OUTER + 0.5, off=L1)]))
    if "chest" in slots:
        root.add(mp.Part("chest_body", cubes=[cube((16, 16), (-4, 0, -2), (8, 12, 4), inflate=OUTER, off=L1)]))
        root.add(mp.Part("chest_right_arm", pivot=(-5, 2, 0), cubes=[cube((40, 16), (-3, -2, -2), (4, 12, 4), inflate=OUTER, off=L1)]))
        root.add(mp.Part("chest_left_arm", pivot=(5, 2, 0), cubes=[cube((40, 16), (-1, -2, -2), (4, 12, 4), inflate=OUTER, mirror=True, off=L1)]))
    if "legs" in slots:
        root.add(mp.Part("legs_body", cubes=[cube((16, 16), (-4, 0, -2), (8, 12, 4), inflate=INNER, off=L2)]))
        root.add(mp.Part("legs_right", pivot=(-1.9, 12, 0), cubes=[cube((0, 16), (-2, 0, -2), (4, 12, 4), inflate=INNER, off=L2)]))
        root.add(mp.Part("legs_left", pivot=(1.9, 12, 0), cubes=[cube((0, 16), (-2, 0, -2), (4, 12, 4), inflate=INNER, mirror=True, off=L2)]))
    if "feet" in slots:
        root.add(mp.Part("boots_right", pivot=(-1.9, 12, 0), cubes=[cube((0, 16), (-2, 0, -2), (4, 12, 4), inflate=OUTER, off=L1)]))
        root.add(mp.Part("boots_left", pivot=(1.9, 12, 0), cubes=[cube((0, 16), (-2, 0, -2), (4, 12, 4), inflate=OUTER, mirror=True, off=L1)]))
    return root

def atlas(skin, layer1, layer2, helmet_layer1=None):
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    img.paste(skin.convert("RGBA"), (0, 0))
    l1 = layer1.convert("RGBA").copy()
    if helmet_layer1 is not None:
        head = helmet_layer1.convert("RGBA").crop((0, 0, 64, 16))
        l1.paste((0, 0, 0, 0), (0, 0, 64, 16))
        l1.paste(head, (0, 0), head)
    img.paste(l1, (64, 0))
    img.paste(layer2.convert("RGBA"), (64, 32))
    return img

def main():
    skin_p, l1_p, l2_p, out_p = sys.argv[1:5]
    helmet_p = sys.argv[5] if len(sys.argv) > 5 else None
    skin = Image.open(skin_p)
    tex = atlas(skin, Image.open(l1_p), Image.open(l2_p), Image.open(helmet_p) if helmet_p else None)
    tex.save(str(Path(out_p).with_name(Path(out_p).stem + "_atlas.png")))

    panels = []
    for label, slots in (("full set", ("head", "chest", "legs", "feet")), ("no helmet", ("chest", "legs", "feet"))):
        for view in ("front", "three_quarter", "side", "back"):
            root = armour(player(), slots)
            im = mp.render(root, texture=tex, view=view, size=(300, 440), scale=11, mode="textured", bg=(236, 238, 240))
            panels.append(im)
    bare = mp.render(player(), texture=tex, view="front", size=(300, 440), scale=11, mode="textured", bg=(236, 238, 240))
    panels.append(bare)

    cols = 4
    rows = (len(panels) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 300 + (cols + 1) * 6, rows * 440 + (rows + 1) * 6), (120, 124, 130))
    for i, im in enumerate(panels):
        sheet.paste(im, (6 + (i % cols) * 306, 6 + (i // cols) * 446))
    sheet.save(out_p)
    print("wrote", out_p, sheet.size)

if __name__ == "__main__":
    main()
