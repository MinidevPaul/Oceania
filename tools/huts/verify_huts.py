import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

from nbt import read_nbt

REPO = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(REPO, "src", "main", "resources")
NS, DIR = "oceania", "hut_village"

FRONT = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0),
         "up": (0, 1, 0), "down": (0, -1, 0)}

CODE_PLACED = {"dock", "path_link", "torch_post"}

MC_JAR = None

def jar_json(path):
    global MC_JAR
    import zipfile
    if MC_JAR is None:
        jars = [j for j in glob.glob(os.path.join(
            REPO, ".gradle", "loom-cache", "minecraftMaven", "net", "minecraft",
            "minecraft-common-*", "*", "minecraft-common-*.jar")) if not j.endswith("-sources.jar")]
        MC_JAR = zipfile.ZipFile(jars[0]) if jars else False
    if MC_JAR is False:
        return None
    try:
        return json.loads(MC_JAR.read(path).decode("utf-8"))
    except KeyError:
        return None

def buildable_biomes(structure_id):
    ns, path = structure_id.split(":")
    local = os.path.join(RES, "data", ns, "worldgen", "structure", path + ".json")
    data = json.load(open(local, encoding="utf-8")) if os.path.exists(local) else         jar_json("data/%s/worldgen/structure/%s.json" % (ns, path))
    if data is None:
        return None
    biomes = data.get("biomes")
    values = []
    if isinstance(biomes, str) and biomes.startswith("#"):
        tns, tpath = biomes[1:].split(":")
        tag = os.path.join(RES, "data", tns, "tags", "worldgen", "biome", tpath + ".json")
        if os.path.exists(tag):
            values = json.load(open(tag, encoding="utf-8")).get("values", [])
    elif isinstance(biomes, str):
        values = [biomes]
    elif isinstance(biomes, list):
        values = biomes
    return [v for v in values if isinstance(v, str) and v.startswith(NS + ":")]

problems = []

def bad(msg):
    problems.append(msg)

def load_json(rel):
    path = os.path.join(RES, rel)
    if not os.path.exists(path):
        bad("missing file %s" % rel)
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)

def read_templates():
    out = {}
    for path in sorted(glob.glob(os.path.join(RES, "data", NS, "structure", DIR, "*.nbt"))):
        name = os.path.basename(path)[:-4]
        root = read_nbt(path)
        size = [int(v) for v in root["size"]]
        palette = root["palette"]
        jigsaws, chests, voids, containers = [], [], [], []
        for entry in palette:
            if str(entry["Name"]) == "minecraft:structure_void":
                voids.append(str(entry["Name"]))
        for block in root["blocks"]:
            pos = [int(v) for v in block["pos"]]
            state = palette[int(block["state"])]
            if str(state["Name"]).split(":")[-1] in ("chest", "trapped_chest", "barrel"):
                containers.append(pos)
            nbt = block.get("nbt")
            if not nbt:
                continue
            ident = str(nbt.get("id", ""))
            if ident == "minecraft:jigsaw":
                front = str(state["Properties"]["orientation"]).split("_")[0]
                jigsaws.append({"pos": pos, "front": front,
                                "name": str(nbt["name"]), "target": str(nbt["target"]),
                                "pool": str(nbt["pool"]), "final": str(nbt["final_state"]),
                                "joint": str(nbt["joint"])})
            elif ident in ("minecraft:chest", "minecraft:barrel"):
                chests.append({"pos": pos, "table": str(nbt.get("LootTable", ""))})
        out[name] = {"size": size, "palette": palette, "jigsaws": jigsaws,
                     "chests": chests, "containers": containers, "voids": voids, "entities": root["entities"],
                     "blocks": len(root["blocks"]), "data_version": int(root["DataVersion"])}
    return out

def check_template(name, t):
    w, h, d = t["size"]
    if t["data_version"] != 3955:
        bad("%s: DataVersion %s, expected 3955" % (name, t["data_version"]))
    if t["voids"]:
        bad("%s: structure_void in the palette, it would be placed as a real block" % name)
    if not t["jigsaws"] and name not in CODE_PLACED:
        bad("%s: no jigsaw block at all, nothing can attach to it" % name)
    for j in t["jigsaws"]:
        x, y, z = j["pos"]
        if not (0 <= x < w and 0 <= y < h and 0 <= z < d):
            bad("%s: jigsaw at %s is outside the %s box" % (name, j["pos"], t["size"]))
        if j["front"] in ("north", "south", "east", "west"):
            dx, _, dz = FRONT[j["front"]]
            nx, nz = x + dx, z + dz
            if 0 <= nx < w and 0 <= nz < d:
                bad("%s: connector at %s faces %s but is not on that face of the %dx%d footprint"
                    % (name, j["pos"], j["front"], w, d))
        if j["front"] == "up":
            if x in (0, w - 1) or z in (0, d - 1):
                bad("%s: spawn marker at %s is on a boundary face, where an attaching piece overwrites it"
                    % (name, j["pos"]))
        if j["final"] != "minecraft:structure_void" and not j["final"].startswith("minecraft:"):
            bad("%s: final_state %r is not a block" % (name, j["final"]))
    for c in t["chests"]:
        if not c["table"]:
            bad("%s: chest at %s has no loot table" % (name, c["pos"]))

def main():
    templates = read_templates()
    print("%d templates" % len(templates))
    for name, t in templates.items():
        check_template(name, t)

    pools = {}
    for path in sorted(glob.glob(os.path.join(RES, "data", NS, "worldgen", "template_pool", DIR, "*.json"))):
        pools["%s:%s/%s" % (NS, DIR, os.path.basename(path)[:-5])] = json.load(open(path, encoding="utf-8"))

    def members(pool_id):
        out = []
        data = pools.get(pool_id)
        if data is None:
            return out
        for e in data["elements"]:
            loc = e["element"].get("location")
            if loc:
                out.append(loc.split("/")[-1])
        return out

    for pool_id, data in pools.items():
        fb = data["fallback"]
        if fb != "minecraft:empty" and fb not in pools:
            bad("pool %s: fallback %s does not exist" % (pool_id, fb))
        for e in data["elements"]:
            loc = e["element"].get("location")
            if loc and loc.split("/")[-1] not in templates:
                bad("pool %s: element %s has no template" % (pool_id, loc))
            if not 1 <= e["weight"] <= 150:
                bad("pool %s: weight %s outside 1..150" % (pool_id, e["weight"]))

    for name, t in templates.items():
        for j in t["jigsaws"]:
            if j["pool"] == "minecraft:empty":
                continue
            if j["pool"] not in pools:
                bad("%s: connector pool %s does not exist" % (name, j["pool"]))
                continue
            candidates = members(j["pool"]) + members(pools[j["pool"]]["fallback"])
            if not any(any(k["name"] == j["target"] for k in templates[c]["jigsaws"])
                       for c in candidates if c in templates):
                bad("%s: connector targets %r but nothing in %s answers to it"
                    % (name, j["target"], j["pool"]))

    st = load_json("data/%s/worldgen/structure/hut_village.json" % NS)
    if st:
        for key in ("dock",):
            if st[key].split("/")[-1] not in templates:
                bad("structure: %s names %s, which has no template" % (key, st[key]))
        for group in st["shore_buildings"]:
            if not group:
                bad("structure: an empty shore-building group places nothing")
            for loc in group:
                if loc.split("/")[-1] not in templates:
                    bad("structure: shore building %s has no template" % loc)
        if st["start_pool"] not in pools:
            bad("structure: start_pool %s does not exist" % st["start_pool"])
        if not 0 <= st["size"] <= 20:
            bad("structure: size %s outside 0..20" % st["size"])
        if st["max_distance_from_center"] + 12 > 128:
            bad("structure: max_distance_from_center %s is too large with a beard"
                % st["max_distance_from_center"])
        tag = st["biomes"].lstrip("#").split(":")
        tagfile = "data/%s/tags/worldgen/biome/%s.json" % (tag[0], tag[1])
        gate = load_json(tagfile)
        if gate:
            for biome in gate["values"]:
                ns, path = biome.split(":")
                if not os.path.exists(os.path.join(RES, "data", ns, "worldgen", "biome", path + ".json")):
                    bad("biome tag names %s, which has no biome file" % biome)
    ss = load_json("data/%s/worldgen/structure_set/hut_villages.json" % NS)
    if ss and ss["placement"]["separation"] >= ss["placement"]["spacing"]:
        bad("structure_set: separation must be smaller than spacing")

    known = {"%s:%s" % (NS, os.path.basename(p)[:-5])
             for p in glob.glob(os.path.join(RES, "data", NS, "worldgen", "biome", "*.json"))}
    for path in glob.glob(os.path.join(RES, "data", "*", "tags", "worldgen", "biome", "**", "*.json"),
                          recursive=True):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for value in data.get("values", []):
            if isinstance(value, str) and value.startswith(NS + ":") and value not in known:
                bad("%s names %s, which is not a biome this mod defines"
                    % (os.path.relpath(path, RES).replace(os.sep, "/"), value))

    for path in glob.glob(os.path.join(RES, "data", "*", "worldgen", "structure_set", "*.json")):
        rel = os.path.relpath(path, RES).replace(os.sep, "/")
        with open(path, encoding="utf-8") as f:
            entries = json.load(f).get("structures", [])
        for entry in entries:
            ident = entry["structure"]
            usable = buildable_biomes(ident)
            if usable is None:
                bad("%s draws %s, which has no structure file" % (rel, ident))
            elif not usable:
                bad("%s draws %s, which has no biome in this world and will silently build nothing"
                    % (rel, ident))

    for path in glob.glob(os.path.join(RES, "data", "*", "tags", "worldgen", "biome", "has_structure", "*.json")):
        with open(path, encoding="utf-8") as f:
            values = json.load(f).get("values", [])
        if not [v for v in values if isinstance(v, str) and v.startswith(NS + ":")]:
            bad("%s names none of this world's biomes, so it does nothing; delete it or fill it in"
                % os.path.relpath(path, RES).replace(os.sep, "/"))

    referenced = set()
    for path in (glob.glob(os.path.join(RES, "data", NS, "worldgen", "template_pool", DIR, "*.json"))
                 + glob.glob(os.path.join(RES, "data", NS, "worldgen", "structure", "*.json"))
                 + glob.glob(os.path.join(REPO, "src", "main", "java", "com", "oceania", "village", "*.java"))):
        with open(path, encoding="utf-8") as f:
            blob = f.read()
        for name in templates:
            if name in blob:
                referenced.add(name)
    for name in sorted(set(templates) - referenced):
        bad("%s.nbt is referenced by no pool and no structure; it is a leftover and should be deleted" % name)

    for name, t in templates.items():
        filled = [c for c in t["chests"] if c["table"]]
        if len(filled) != len(t["containers"]):
            bad("%s: %d container(s) drawn but %d with a loot table; the rest generate empty every time"
                % (name, len(t["containers"]), len(filled)))

    for name, t in templates.items():
        for c in t["chests"]:
            ns, path = c["table"].split(":")
            if not os.path.exists(os.path.join(RES, "data", ns, "loot_table", path + ".json")):
                bad("%s: loot table %s does not exist" % (name, c["table"]))

    for pool_id, data in pools.items():
        for e in data["elements"]:
            proc = e["element"].get("processors")
            if isinstance(proc, str) and proc != "minecraft:empty":
                ns, path = proc.split(":")
                if ns != "minecraft" and not os.path.exists(
                        os.path.join(RES, "data", ns, "worldgen", "processor_list", path + ".json")):
                    bad("pool %s: processor list %s does not exist" % (pool_id, proc))

    print("\npools:")
    for pool_id, data in sorted(pools.items()):
        total = sum(e["weight"] for e in data["elements"])
        empties = sum(e["weight"] for e in data["elements"]
                      if e["element"]["element_type"] == "minecraft:empty_pool_element")
        print("  %-34s %2d elements, weight %3d (empty %d), fallback %s"
              % (pool_id, len(data["elements"]), total, empties, data["fallback"]))
    print("\nconnectors:")
    for name in sorted(templates):
        js = templates[name]["jigsaws"]
        kinds = {}
        for j in js:
            kinds[j["name"]] = kinds.get(j["name"], 0) + 1
        print("  %-24s %s" % (name, ", ".join("%s x%d" % (k, v) for k, v in sorted(kinds.items()))))

    print("\nchests:")
    for name in sorted(templates):
        for c in templates[name]["chests"]:
            print("  %-24s %s %s" % (name, c["pos"], c["table"]))

    if problems:
        print("\n%d PROBLEMS" % len(problems))
        for p in problems:
            print("  " + p)
        return 1
    print("\nall checks pass")
    return 0

if __name__ == "__main__":
    sys.exit(main())
