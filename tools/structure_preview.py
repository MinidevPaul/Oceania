import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "huts"))

from huts import nbt as nbtlib
from model_preview import Cube, Part, render, bounds

BLOCK = 16.0

PALETTE = [
    ("jigsaw", (226, 72, 212)),
    ("structure_block", (226, 72, 212)),
    ("hay_block", (210, 176, 54)),
    ("stripped_jungle", (186, 136, 94)),
    ("stripped_oak", (198, 166, 112)),
    ("stripped_bamboo", (198, 176, 104)),
    ("jungle_log", (94, 74, 44)),
    ("jungle_wood", (94, 74, 44)),
    ("jungle_planks", (168, 118, 84)),
    ("jungle_leaves", (56, 128, 46)),
    ("jungle", (150, 110, 78)),
    ("bamboo_mosaic", (196, 174, 100)),
    ("bamboo_planks", (200, 180, 108)),
    ("bamboo", (154, 182, 86)),
    ("oak_log", (118, 94, 56)),
    ("oak_planks", (186, 152, 98)),
    ("oak_leaves", (62, 136, 52)),
    ("acacia", (180, 112, 70)),
    ("cobblestone", (136, 136, 136)),
    ("stone_brick", (126, 126, 126)),
    ("stone", (134, 134, 134)),
    ("andesite", (140, 140, 140)),
    ("deepslate", (78, 78, 82)),
    ("basalt", (72, 70, 76)),
    ("gravel", (142, 136, 132)),
    ("coarse_dirt", (122, 86, 58)),
    ("dirt_path", (154, 126, 74)),
    ("dirt", (134, 96, 62)),
    ("grass_block", (108, 162, 76)),
    ("sand", (222, 208, 158)),
    ("sandstone", (214, 198, 148)),
    ("clay", (162, 166, 178)),
    ("terracotta", (164, 104, 76)),
    ("prismarine", (102, 160, 150)),
    ("sea_lantern", (226, 236, 226)),
    ("glowstone", (236, 212, 148)),
    ("lantern", (226, 196, 124)),
    ("torch", (232, 206, 130)),
    ("campfire", (196, 110, 64)),
    ("bell", (222, 186, 86)),
    ("cartography_table", (160, 142, 112)),
    ("fletching_table", (196, 170, 118)),
    ("smoker", (122, 118, 112)),
    ("composter", (150, 118, 72)),
    ("lectern", (168, 134, 84)),
    ("loom", (176, 150, 104)),
    ("cobweb", (220, 220, 224)),
    ("cocoa", (150, 88, 50)),
    ("decorated_pot", (178, 116, 88)),
    ("potted_fern", (86, 146, 66)),
    ("fern", (86, 146, 66)),
    ("short_grass", (96, 156, 70)),
    ("glow_lichen", (140, 170, 120)),
    ("hanging_roots", (150, 120, 86)),
    ("melon", (104, 150, 56)),
    ("banner", (170, 60, 60)),
    ("sugar_cane", (126, 186, 98)),
    ("tuff", (114, 114, 108)),
    ("water", (84, 128, 200)),
    ("lava", (222, 134, 54)),
    ("kelp", (52, 118, 60)),
    ("seagrass", (72, 138, 70)),
    ("coral", (218, 112, 150)),
    ("wool", (212, 212, 208)),
    ("carpet", (206, 200, 192)),
    ("chest", (158, 118, 60)),
    ("barrel", (146, 112, 66)),
    ("crafting_table", (158, 120, 74)),
    ("furnace", (118, 118, 118)),
    ("bed", (178, 70, 70)),
    ("ladder", (166, 134, 86)),
    ("scaffolding", (196, 168, 102)),
    ("door", (150, 112, 72)),
    ("trapdoor", (156, 120, 78)),
    ("fence", (164, 128, 82)),
    ("wall", (130, 130, 130)),
    ("glass", (198, 222, 228)),
    ("flower", (206, 92, 122)),
    ("bush", (70, 136, 60)),
    ("vine", (64, 128, 56)),
    ("leaves", (60, 132, 50)),
    ("log", (120, 94, 58)),
    ("planks", (184, 150, 98)),
    ("slab", (178, 146, 96)),
    ("stairs", (178, 146, 96)),
]

SKIP = ("minecraft:air", "minecraft:cave_air", "minecraft:void_air",
        "minecraft:structure_void")

FALLBACK = (150, 150, 155)

JIGSAW_CONNECTOR = (226, 72, 212)
JIGSAW_SPAWN = (64, 204, 226)

def colour_for(name, unknown):
    short = name.split("[")[0]
    bare = short.split(":")[-1]
    if bare == "jigsaw":
        if "orientation=up_" in name:
            return JIGSAW_SPAWN, "jigsaw_spawn"
        return JIGSAW_CONNECTOR, "jigsaw_connector"
    for key, rgb in PALETTE:
        if key in bare:
            return rgb, key
    unknown.add(short)
    return FALLBACK, "unknown"

def load(path):
    root = nbtlib.read_nbt(path)
    size = [int(v) for v in root["size"]]
    palette = []
    for p in root["palette"]:
        entry = str(p["Name"])
        if entry.endswith(":jigsaw"):
            props = p.get("Properties") or {}
            orient = str(props.get("orientation", ""))
            if orient:
                entry = "%s[orientation=%s]" % (entry, orient)
        palette.append(entry)
    out = []
    for b in root["blocks"]:
        name = palette[int(b["state"])]
        if name in SKIP:
            continue
        x, y, z = (int(v) for v in b["pos"])
        out.append((x, y, z, name))
    return size, out

def cull(blocks):
    filled = {(x, y, z) for x, y, z, _n in blocks}
    kept = []
    for x, y, z, n in blocks:
        if all((x + dx, y + dy, z + dz) in filled for dx, dy, dz in
               ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            continue
        kept.append((x, y, z, n))
    return kept

def build(size, blocks, unknown):
    root = Part("structure")
    groups, colours = {}, {}
    sx, sy, sz = size
    for x, y, z, name in blocks:
        rgb, key = colour_for(name, unknown)
        colours[key] = rgb
        groups.setdefault(key, []).append((x, y, z))
    for key, cells in groups.items():
        cubes = []
        for x, y, z in cells:
            cubes.append(Cube((0, 0),
                              ((x - sx / 2.0) * BLOCK,
                               -(y + 1) * BLOCK,
                               -(z - sz / 2.0) * BLOCK - BLOCK),
                              (BLOCK, BLOCK, BLOCK)))
        root.add(Part(key, cubes=cubes))
    return root, colours

def render_one(path, out_dir, views, size_px=(1100, 850)):
    unknown = set()
    size, blocks = load(path)
    total = len(blocks)
    blocks = cull(blocks)
    root, colours = build(size, blocks, unknown)

    name = os.path.splitext(os.path.basename(path))[0]
    os.makedirs(out_dir, exist_ok=True)
    for view in views:
        img = render(root, view=view, size=size_px, mode="parts",
                     part_colours=colours, ambient=0.46)
        img.save(os.path.join(out_dir, "%s_%s.png" % (name, view)))
    lo, hi = bounds(root)
    print("  %-40s %2dx%2dx%2d  %4d blocks (%d after cull)  -> %d views"
          % (name, size[0], size[1], size[2], total, len(blocks), len(views)))
    return unknown

def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    flags = {a.split("=")[0]: a.split("=", 1)[-1] for a in sys.argv[1:] if a.startswith("--")}
    if not args:
        print(__doc__)
        return 1
    views = flags.get("--views", "three_quarter,side,top").split(",")
    target = args[0]
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = flags.get("--out", os.path.join(repo, "build", "structure-preview"))

    paths = []
    if os.path.isdir(target):
        paths = sorted(os.path.join(target, f) for f in os.listdir(target)
                       if f.endswith(".nbt"))
    else:
        paths = [target]

    unknown = set()
    print("rendering %d template(s) to %s" % (len(paths), out_dir))
    for p in paths:
        unknown |= render_one(p, out_dir, views)
    if unknown:
        print("\n  %d block id(s) had no palette entry and were drawn mid grey:" % len(unknown))
        for u in sorted(unknown):
            print("    %s" % u)
    return 0

if __name__ == "__main__":
    sys.exit(main())
