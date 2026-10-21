import collections
import importlib.util
import os
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("vs", str(ROOT / "vanilla_slice.py"))
vs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vs)

DEFAULT = ROOT.parent / "build" / "vanilla-world-26.3" / "world" / "dimensions" / "minecraft" / "overworld" / "region"
REGION = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT

WATCH = [
    ("coal", ("coal_ore", "deepslate_coal_ore")),
    ("iron", ("iron_ore", "deepslate_iron_ore")),
    ("copper", ("copper_ore", "deepslate_copper_ore")),
    ("gold", ("gold_ore", "deepslate_gold_ore")),
    ("redstone", ("redstone_ore", "deepslate_redstone_ore")),
    ("lapis", ("lapis_ore", "deepslate_lapis_ore")),
    ("diamond", ("diamond_ore", "deepslate_diamond_ore")),
    ("emerald", ("emerald_ore", "deepslate_emerald_ore")),
    ("granite", ("granite",)),
    ("diorite", ("diorite",)),
    ("andesite", ("andesite",)),
    ("tuff", ("tuff",)),
    ("dirt", ("dirt",)),
    ("gravel", ("gravel",)),
    ("clay", ("clay",)),
    ("magma", ("magma_block",)),
    ("dripstone", ("dripstone_block", "pointed_dripstone")),
    ("amethyst", ("amethyst_block", "budding_amethyst", "amethyst_cluster")),
    ("calcite", ("calcite",)),
    ("lava", ("lava",)),
]
LOOKUP = {}
for row, blocks in WATCH:
    for b in blocks:
        LOOKUP.setdefault(b, []).append(row)

NOT_ROCK = {"air", "cave_air", "void_air", "water", "lava", "bubble_column"}
BAND = 32
Y_LO, Y_HI = -64, 320

def main():
    if not REGION.is_dir():
        raise SystemExit("no region dir at %s - run tools/vanilla_world.py first" % REGION)

    counts = collections.defaultdict(collections.Counter)
    rock = collections.Counter()
    chunks = 0
    for path in sorted(REGION.glob("*.mca")):
        region = vs.Region(str(path))
        rx, rz = (int(v) for v in path.name.split(".")[1:3])
        for cz in range(32):
            for cx in range(32):
                chunk = region.chunk(rx * 32 + cx, rz * 32 + cz)
                if chunk is None or chunk.status != "minecraft:full":
                    continue
                chunks += 1
                for sy, (pal, data) in chunk.sections.items():
                    if not (Y_LO >> 4) <= sy <= (Y_HI >> 4):
                        continue
                    band = (sy * 16) // BAND
                    if data is None or len(pal) == 1:
                        name = pal[0]
                        bulk = 4096
                        if name not in NOT_ROCK:
                            rock[band] += bulk
                        for row in LOOKUP.get(name, ()):
                            counts[row][band] += bulk
                        continue
                    bits = max(4, (len(pal) - 1).bit_length())
                    per = 64 // bits
                    mask = (1 << bits) - 1
                    tally = collections.Counter()
                    for idx in range(4096):
                        word = data[idx // per] & 0xffffffffffffffff
                        tally[(word >> ((idx % per) * bits)) & mask] += 1
                    for v, n in tally.items():
                        if v >= len(pal):
                            continue
                        name = pal[v]
                        if name not in NOT_ROCK:
                            rock[band] += n
                        for row in LOOKUP.get(name, ()):
                            counts[row][band] += n

    if not chunks:
        raise SystemExit("no fully generated chunks found in %s" % REGION)

    bands = sorted(rock)
    total_rock = sum(rock.values())
    print("vanilla 26.3 ore census: %d fully generated chunks, %s" % (chunks, REGION))
    print("rock = not air and no fluid, as OreScan counts it; %d blocks of rock, %.0f per chunk"
          % (total_rock, total_rock / chunks))
    print()
    print("%-11s %10s %12s   %s" % ("", "blocks/ch", "per million", "per million of rock, by y band"))
    print("%-11s %10s %12s   %s" % ("", "", "of rock", " ".join("%7d" % (b * BAND) for b in bands)))
    print("%-11s %10s %12s   %s" % ("rock/ch", "", "",
                                    " ".join("%7.0f" % (rock[b] / chunks) for b in bands)))
    for row, _ in WATCH:
        per_chunk = sum(counts[row].values()) / chunks
        if per_chunk == 0:
            continue
        ppm = 1e6 * sum(counts[row].values()) / total_rock
        cells = []
        for b in bands:
            cells.append("%7.0f" % (1e6 * counts[row][b] / rock[b]) if rock[b] else "      .")
        print("%-11s %10.2f %12.0f   %s" % (row, per_chunk, ppm, " ".join(cells)))
    print()
    print("Compare the 'per million of rock' column against ours. The bands are NOT comparable between the two")
    print("worlds - vanilla's rock sits under a y 64 surface, ours under a sea floor at -200 to -460.")

if __name__ == "__main__":
    main()
