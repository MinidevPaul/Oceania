import collections
import importlib.util
import os
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("vslice", REPO / "tools" / "vanilla_slice.py")
vslice = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vslice)

def decode(pal, data, idx):
    if data is None or len(pal) == 1:
        return pal[0]
    bits = max(4, (len(pal) - 1).bit_length())
    per = 64 // bits
    word = data[idx // per] & 0xFFFFFFFFFFFFFFFF
    v = (word >> ((idx % per) * bits)) & ((1 << bits) - 1)
    return pal[v] if v < len(pal) else "air"

def main():
    dir_a, dir_b = sys.argv[1], sys.argv[2]
    files = sorted(set(os.listdir(dir_a)) & set(os.listdir(dir_b)))
    print("comparing %d region files" % len(files))

    chunks = sections = diff_sections = diff_blocks = palette_only = 0
    status_skipped = 0
    status_pairs = collections.Counter()
    pairs = collections.Counter()
    yband = collections.Counter()
    chunkhist = collections.Counter()
    examples = []
    for name in files:
        parts = name.split(".")
        rx, rz = int(parts[1]), int(parts[2])
        ra = vslice.Region(os.path.join(dir_a, name))
        rb = vslice.Region(os.path.join(dir_b, name))
        for lcz in range(32):
            for lcx in range(32):
                cx, cz = rx * 32 + lcx, rz * 32 + lcz
                ca, cb = ra.chunk(cx, cz), rb.chunk(cx, cz)
                if ca is None or cb is None:
                    if (ca is None) != (cb is None):
                        print("chunk %d,%d present in only one world" % (cx, cz))
                    continue
                if ca.status != cb.status or ca.status != "minecraft:full":
                    status_skipped += 1
                    status_pairs[(ca.status, cb.status)] += 1
                    continue
                chunks += 1
                for sy in sorted(set(ca.sections) | set(cb.sections)):
                    sa, sb = ca.sections.get(sy), cb.sections.get(sy)
                    if sa is None or sb is None:
                        print("chunk %d,%d section %d in only one world" % (cx, cz, sy))
                        continue
                    sections += 1
                    if sa[0] == sb[0] and sa[1] == sb[1]:
                        continue
                    diff_sections += 1
                    found = 0
                    for idx in range(4096):
                        ba = decode(sa[0], sa[1], idx)
                        bb = decode(sb[0], sb[1], idx)
                        if ba != bb:
                            diff_blocks += 1
                            found += 1
                            pairs[(ba, bb)] += 1
                            y = (sy << 4) + (idx // 256)
                            yband[(y // 32) * 32] += 1
                            chunkhist[(cx, cz)] += 1
                            if len(examples) < 12:
                                x = (cx << 4) + (idx % 16)
                                z = (cz << 4) + ((idx // 16) % 16)
                                examples.append((x, y, z, ba, bb))
                    if found == 0:
                        palette_only += 1

    print("")
    if status_skipped:
        print("chunks SKIPPED for status (not full in both): %d" % status_skipped)
        for (a, b), n in status_pairs.most_common(6):
            print("   %-22s vs %-22s %d" % (a, b, n))
    print("chunks compared      %d" % chunks)
    print("sections compared    %d" % sections)
    print("sections differing   %d" % diff_sections)
    print("  identical blocks, different palette or packing: %d" % palette_only)
    print("BLOCKS DIFFERING     %d" % diff_blocks)

    print("")
    print("state pairs (A -> B):")
    for (a, b), n in pairs.most_common(40):
        print("   %7d  %s -> %s" % (n, a, b))

    print("")
    print("by y band:")
    for y, n in sorted(yband.items()):
        print("   y %6d..%6d  %7d" % (y, y + 31, n))

    print("")
    print("chunks with differences: %d" % len(chunkhist))
    for (cx, cz), n in chunkhist.most_common(10):
        print("   chunk %5d,%6d  %6d blocks" % (cx, cz, n))

    print("")
    print("examples (x, y, z, A, B):")
    for e in examples:
        print("   %s" % (e,))

if __name__ == "__main__":
    main()
