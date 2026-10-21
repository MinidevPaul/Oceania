import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))

def main():
    path = os.path.join(REPO, "src", "main", "resources", "data", "oceania",
                        "structure", "hut_village", sys.argv[1])
    root = nbt.read_nbt(path)
    pal = [str(p["Name"]) for p in root["palette"]]
    size = [int(v) for v in root["size"]]
    grid = {}
    for b in root["blocks"]:
        x, y, z = [int(v) for v in b["pos"]]
        grid[(x, y, z)] = pal[int(b["state"])]
    print("size", size)
    ys = sorted(set(k[1] for k in grid))
    want = [int(a) for a in sys.argv[2:]] or ys
    for y in want:
        print("--- template y=%d (drawing y=%d) ---" % (y, y - 1))
        for z in range(size[2]):
            row = []
            for x in range(size[0]):
                name = grid.get((x, y, z))
                if name is None:
                    row.append("??")
                elif name == "minecraft:air":
                    row.append("..")
                else:
                    row.append(name.split(":")[1].split("[")[0][:2])
            print("z%-2d %s" % (z, " ".join(row)))

main()
