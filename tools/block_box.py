import collections
import glob
import importlib.util
import os
import pathlib
import struct
import sys
import zlib

REPO = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("vs", str(REPO / "tools" / "vanilla_slice.py"))
vs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vs)

x0, x1, y0, y1, z0, z1 = (int(a) for a in sys.argv[1:7])
REGION = sys.argv[7] if len(sys.argv) > 7 else str(
    REPO / "run-headless" / "world" / "dimensions" / "minecraft" / "overworld" / "region")

blocks = {}
for path in sorted(glob.glob(os.path.join(REGION, "*.mca"))):
    rx, rz = (int(v) for v in os.path.basename(path).split(".")[1:3])
    if not (rx * 512 <= x1 and (rx + 1) * 512 > x0 and rz * 512 <= z1 and (rz + 1) * 512 > z0):
        continue
    buf = open(path, "rb").read()
    for ci in range(1024):
        cx, cz = rx * 32 + ci % 32, rz * 32 + ci // 32
        if not (cx * 16 <= x1 and cx * 16 + 15 >= x0 and cz * 16 <= z1 and cz * 16 + 15 >= z0):
            continue
        off = (buf[ci * 4] << 16 | buf[ci * 4 + 1] << 8 | buf[ci * 4 + 2]) * 4096
        if off == 0:
            continue
        ln = struct.unpack(">I", buf[off:off + 4])[0]
        raw = buf[off + 5:off + 4 + ln]
        try:
            tag = vs.root(zlib.decompress(raw) if buf[off + 4] == 2 else raw)
        except Exception as exc:
            print("chunk %d,%d unreadable: %s" % (cx, cz, exc))
            continue
        for sec in tag.get("sections", []):
            bs = sec.get("block_states")
            if not bs:
                continue
            sy = sec["Y"] * 16
            if sy > y1 or sy + 15 < y0:
                continue
            pal = [vs.block_name(e) for e in bs["palette"]]
            data = bs.get("data")
            for idx in range(4096):
                y = sy + idx // 256
                if y < y0 or y > y1:
                    continue
                z = cz * 16 + (idx % 256) // 16
                x = cx * 16 + idx % 16
                if not (x0 <= x <= x1 and z0 <= z <= z1):
                    continue
                if data is None or len(pal) == 1:
                    name = pal[0]
                else:
                    bits = max(4, (len(pal) - 1).bit_length())
                    per = 64 // bits
                    word = data[idx // per] & 0xffffffffffffffff
                    v = (word >> ((idx % per) * bits)) & ((1 << bits) - 1)
                    name = pal[v] if v < len(pal) else "?"
                blocks[(x, y, z)] = name

if not blocks:
    sys.exit("no blocks found - is the box inside generated chunks?")
print("census of %d blocks in x %d..%d y %d..%d z %d..%d" % (len(blocks), x0, x1, y0, y1, z0, z1))
for name, n in collections.Counter(blocks.values()).most_common():
    print("  %6d  %s" % (n, name))

GLYPH = {"air": ".", "water": "~", "stone": "#", "deepslate": "D", "gravel": "g", "sand": "s",
         "sandstone": "S", "dirt": "d", "clay": "c", "tuff": "t", "andesite": "a", "diorite": "i",
         "granite": "G", "calcite": "C", "dripstone_block": "p", "magma_block": "M", "lava": "L",
         "kelp_plant": "k", "seagrass": "v", "tall_seagrass": "v", "sea_pickle": "P", "cave_air": ","}
for z in range(z0, z1 + 1):
    print("\nz = %d   (x %d..%d left to right)" % (z, x0, x1))
    for y in range(y1, y0 - 1, -1):
        row = "".join(GLYPH.get(blocks.get((x, y, z), "?"), "?") for x in range(x0, x1 + 1))
        print("  y %4d  %s" % (y, row))
print("\nunmapped names appear as ?; the census above names them")
