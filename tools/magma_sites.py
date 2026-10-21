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

NOT_FULL = {
    "air", "cave_air", "void_air", "water", "lava", "bubble_column",
    "glow_lichen", "pointed_dripstone", "vine", "cave_vines", "cave_vines_plant", "sculk_vein",
    "kelp", "kelp_plant", "seagrass", "tall_seagrass", "moss_carpet", "small_dripleaf", "big_dripleaf",
    "azalea", "flowering_azalea", "hanging_roots", "spore_blossom", "torch", "wall_torch", "soul_fire",
    "sulfur_spike", "speleothem", "short_grass", "tall_grass", "dead_bush", "amethyst_cluster",
    "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud", "chain", "lantern", "soul_lantern",
    "rail", "web", "cobweb", "snow", "light",
}

def short(name):
    return name.split(":")[-1].split("[")[0]

def chunk_blocks(buf, ci, cx, cz):
    off = (buf[ci * 4] << 16 | buf[ci * 4 + 1] << 8 | buf[ci * 4 + 2]) * 4096
    if off == 0:
        return None
    ln = struct.unpack(">I", buf[off:off + 4])[0]
    raw = buf[off + 5:off + 4 + ln]
    try:
        tag = vs.root(zlib.decompress(raw) if buf[off + 4] == 2 else raw)
    except Exception:
        return None
    out = {}
    for sec in tag.get("sections", []):
        bs = sec.get("block_states")
        if not bs:
            continue
        sy = sec["Y"] * 16
        pal = [short(vs.block_name(e)) for e in bs["palette"]]
        if len(pal) == 1:
            if pal[0] == "air":
                continue
            for idx in range(4096):
                out[(idx % 16, sy + idx // 256, (idx % 256) // 16)] = pal[0]
            continue
        data = bs.get("data")
        if data is None:
            continue
        bits = max(4, (len(pal) - 1).bit_length())
        per = 64 // bits
        mask = (1 << bits) - 1
        for idx in range(4096):
            word = data[idx // per] & 0xffffffffffffffff
            v = (word >> ((idx % per) * bits)) & mask
            name = pal[v] if v < len(pal) else "?"
            if name == "air":
                continue
            out[(idx % 16, sy + idx // 256, (idx % 256) // 16)] = name
    return out

def main():
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    centre_x, centre_z, chunks = (int(a) for a in sys.argv[1:4])
    region = sys.argv[4] if len(sys.argv) > 4 else str(
        REPO / "run-headless" / "world" / "dimensions" / "minecraft" / "overworld" / "region")
    ccx, ccz = centre_x >> 4, centre_z >> 4
    lo_cx, hi_cx, lo_cz, hi_cz = ccx - chunks, ccx + chunks, ccz - chunks, ccz + chunks

    water = floored = placeable = 0
    sites = 0
    reasons = collections.Counter()
    depths = collections.Counter()
    seen_chunks = 0
    first = None

    for path in sorted(glob.glob(os.path.join(region, "*.mca"))):
        rx, rz = (int(v) for v in os.path.basename(path).split(".")[1:3])
        if not (rx * 32 <= hi_cx and (rx + 1) * 32 > lo_cx and rz * 32 <= hi_cz and (rz + 1) * 32 > lo_cz):
            continue
        buf = open(path, "rb").read()
        for ci in range(1024):
            cx, cz = rx * 32 + ci % 32, rz * 32 + ci // 32
            if not (lo_cx <= cx <= hi_cx and lo_cz <= cz <= hi_cz):
                continue
            blocks = chunk_blocks(buf, ci, cx, cz)
            if not blocks:
                continue
            seen_chunks += 1

            def name(lx, y, lz):
                return blocks.get((lx, y, lz), "air")

            def full(lx, y, lz):
                return name(lx, y, lz) not in NOT_FULL

            top = {}
            for (lx, y, lz), nm in blocks.items():
                if nm in NOT_FULL:
                    continue
                k = (lx, lz)
                if y > top.get(k, -10000):
                    top[k] = y

            for (lx, y, lz), nm in blocks.items():
                if nm != "water":
                    continue
                if not (1 <= lx <= 14 and 1 <= lz <= 14):
                    continue
                floor_top = top.get((lx, lz))
                if floor_top is None or y > floor_top - 32:
                    continue
                water += 1
                fy = None
                for d in range(1, 5):
                    if name(lx, y - d, lz) != "water":
                        fy = y - d
                        break
                if fy is None:
                    reasons["no floor within 4 blocks below"] += 1
                    continue
                if not full(lx, fy, lz):
                    reasons["floor block is not solid (air cave under the puddle)"] += 1
                    continue
                floored += 1
                ok = 0
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        for dz in (-1, 0, 1):
                            px, py, pz = lx + dx, fy + dy, lz + dz
                            if not (0 <= px <= 15 and 0 <= pz <= 15):
                                continue
                            if not full(px, py, pz):
                                continue
                            if not full(px, py - 1, pz):
                                continue
                            if not (full(px + 1, py, pz) and full(px - 1, py, pz)
                                    and full(px, py, pz + 1) and full(px, py, pz - 1)):
                                continue
                            ok += 1
                if ok == 0:
                    reasons["floor found but nothing is enclosed on five sides"] += 1
                    continue
                placeable += 1
                sites += ok
                depths[(floor_top - y) // 32 * 32] += 1
                if first is None:
                    first = (cx * 16 + lx, y, cz * 16 + lz, ok)

    print("magma site census over %d chunks (%d by %d chunks at %d,%d)"
          % (seen_chunks, 2 * chunks + 1, 2 * chunks + 1, centre_x, centre_z))
    print("  water blocks more than 32 below their own floor: %d  (%.2f a chunk)"
          % (water, water / max(1, seen_chunks)))
    print("  of those, with a solid floor within 4 below:     %d" % floored)
    print("  of those, with at least one legal magma block:   %d" % placeable)
    print("  total legal magma positions (0.5 each are taken): %d" % sites)
    for reason, n in reasons.most_common():
        print("  rejected: %-52s %d" % (reason, n))
    if first:
        print("  first usable water at %d,%d,%d with %d legal positions" % first)
    if depths:
        print("  usable water by depth under the floor: %s"
              % ", ".join("%d-%d: %d" % (d, d + 32, n) for d, n in sorted(depths.items())))

if __name__ == "__main__":
    main()
