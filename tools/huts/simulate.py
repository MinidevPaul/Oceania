import glob
import json
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

from nbt import read_nbt

REPO = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(REPO, "src", "main", "resources")
NS, DIR = "oceania", "hut_village"

CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}

def load():
    pieces = {}
    for path in sorted(glob.glob(os.path.join(RES, "data", NS, "structure", DIR, "*.nbt"))):
        root = read_nbt(path)
        size = [int(v) for v in root["size"]]
        palette = root["palette"]
        conns = []
        for block in root["blocks"]:
            nbt = block.get("nbt")
            if not nbt or str(nbt.get("id", "")) != "minecraft:jigsaw":
                continue
            front = str(palette[int(block["state"])]["Properties"]["orientation"]).split("_")[0]
            if front in ("up", "down"):
                continue
            pos = [int(v) for v in block["pos"]]
            conns.append({"x": pos[0], "z": pos[2], "front": front,
                          "name": str(nbt["name"]), "target": str(nbt["target"]),
                          "pool": str(nbt["pool"])})
        pieces[os.path.basename(path)[:-4]] = {"w": size[0], "d": size[2], "conns": conns}
    pools = {}
    for path in sorted(glob.glob(os.path.join(RES, "data", NS, "worldgen", "template_pool", DIR, "*.json"))):
        pools["%s:%s/%s" % (NS, DIR, os.path.basename(path)[:-5])] = json.load(open(path, encoding="utf-8"))
    structure = json.load(open(os.path.join(RES, "data", NS, "worldgen", "structure", "hut_village.json"),
                               encoding="utf-8"))
    return pieces, pools, structure

def rotate_cell(x, z, w, d, turns):
    for _ in range(turns):
        x, z, w, d = d - 1 - z, x, d, w
    return x, z, w, d

def rotate_front(front, turns):
    for _ in range(turns):
        front = CW[front]
    return front

def candidates(pools, pool_id, at_limit):
    data = pools.get(pool_id)
    if data is None:
        return []
    def expand(d):
        out = []
        for e in d["elements"]:
            el = e["element"]
            loc = el.get("location")
            entry = loc.split("/")[-1] if loc else None
            out.extend([entry] * e["weight"])
        random.shuffle(out)
        return out
    fallback = pools.get(data["fallback"])
    first = [] if at_limit else expand(data)
    return first + (expand(fallback) if fallback else [])

def overlaps(box, placed):
    x1, z1, x2, z2 = box
    for (px1, pz1, px2, pz2) in placed:
        if x1 <= px2 and px1 <= x2 and z1 <= pz2 and pz1 <= z2:
            return True
    return False

def build_village(pieces, pools, structure, seed):
    random.seed(seed)
    radius = structure["max_distance_from_center"]
    depth_limit = structure["size"]

    start_pool = structure["start_pool"]
    start = random.choice([e["element"]["location"].split("/")[-1]
                           for e in pools[start_pool]["elements"] for _ in range(e["weight"])])
    turns = random.randrange(4)
    p = pieces[start]
    _, _, w, d = rotate_cell(0, 0, p["w"], p["d"], turns)
    ox, oz = -w // 2, -d // 2
    placed = [(ox, oz, ox + w - 1, oz + d - 1)]
    used = [start]
    queue = []
    for c in p["conns"]:
        cx, cz, _, _ = rotate_cell(c["x"], c["z"], p["w"], p["d"], turns)
        queue.append((ox + cx, oz + cz, rotate_front(c["front"], turns), c["target"], c["pool"], 1))

    while queue:
        jx, jz, front, target, pool_id, depth = queue.pop(0)
        if depth > depth_limit:
            continue
        dx, dz = STEP[front]
        tx, tz = jx + dx, jz + dz
        want = OPPOSITE[front]
        for entry in candidates(pools, pool_id, depth >= depth_limit):
            if entry is None:
                break
            cand = pieces[entry]
            for turns2 in random.sample(range(4), 4):
                matched = False
                for c in cand["conns"]:
                    if c["name"] != target:
                        continue
                    cx, cz, cw, cd = rotate_cell(c["x"], c["z"], cand["w"], cand["d"], turns2)
                    if rotate_front(c["front"], turns2) != want:
                        continue
                    ox2, oz2 = tx - cx, tz - cz
                    box = (ox2, oz2, ox2 + cw - 1, oz2 + cd - 1)
                    if min(box[0], box[1]) < -radius or max(box[2], box[3]) > radius:
                        continue
                    if overlaps(box, placed):
                        continue
                    placed.append(box)
                    used.append(entry)
                    for other in cand["conns"]:
                        if other is c:
                            continue
                        ocx, ocz, _, _ = rotate_cell(other["x"], other["z"], cand["w"], cand["d"], turns2)
                        queue.append((ox2 + ocx, oz2 + ocz, rotate_front(other["front"], turns2),
                                      other["target"], other["pool"], depth + 1))
                    matched = True
                    break
                if matched:
                    break
            else:
                continue
            break
    return used, placed

def main():
    runs = int(sys.argv[1]) if len(sys.argv) > 1 else 40
    pieces, pools, structure = load()
    totals, counts, spans = {}, [], []
    for seed in range(runs):
        used, placed = build_village(pieces, pools, structure, seed)
        counts.append(len(used))
        xs = [b[0] for b in placed] + [b[2] for b in placed]
        zs = [b[1] for b in placed] + [b[3] for b in placed]
        spans.append((max(xs) - min(xs) + 1, max(zs) - min(zs) + 1))
        for name in used:
            totals[name] = totals.get(name, 0) + 1

    counts.sort()
    print("%d villages, size %d, radius %d" % (runs, structure["size"], structure["max_distance_from_center"]))
    print("  pieces per village: min %d  median %d  max %d"
          % (counts[0], counts[len(counts) // 2], counts[-1]))
    print("  footprint: median %d x %d blocks"
          % (sorted(s[0] for s in spans)[len(spans) // 2], sorted(s[1] for s in spans)[len(spans) // 2]))
    buildings = sum(v for k, v in totals.items() if not k.startswith("path"))
    print("  buildings per village: %.1f   path pieces: %.1f"
          % (buildings / runs, sum(v for k, v in totals.items() if k.startswith("path")) / runs))
    print("\n  piece                      per village")
    for name in sorted(pieces):
        if not any(e["element"].get("location", "").endswith("/" + name)
                   for p in pools.values() for e in p["elements"]):
            continue
        if not pieces[name]["conns"]:
            continue
        n = totals.get(name, 0)
        print("  %-26s %5.2f %s" % (name, n / runs, "NEVER PLACED" if n == 0 else ""))

if __name__ == "__main__":
    main()
