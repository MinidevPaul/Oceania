import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

import meta
import mobs as mobmod
import paths as pathmod
import shore as shoremod
from nbt import Byte, Double, Int, String, write_nbt
from palette import LEGEND, load_pieces, parse_state

REPO = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(REPO, "src", "main", "resources")
NS = "oceania"
DIR = "hut_village"
DATA_VERSION = 3955

VILLAGE_SIZE = 12
VILLAGE_RADIUS = 52
BUILDING_MARGIN = 1
PATHS_EMPTY_WEIGHT = 2
SHORE_SEARCH = 80
MAX_HEIGHT_SPREAD = 24
SHORE_BUILDINGS = [["16", "17"], ["13"]]

BED_BUILDINGS = ["01", "03", "10", "07"]
MIN_BEDS = 2

FARM_BUILDINGS = ["31"]
MIN_FARMS = 2

CODE_ONLY = ["31"]
SPACING = 10
SEPARATION = 5
SALT = 7215104
PATH_COARSE_CHANCE = 0.34

written = []

def write_json(rel, obj):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")
    written.append(rel)

def slug(text):
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")

def jigsaw_nbt(name, target, pool, final_state, joint):
    return {"id": "minecraft:jigsaw", "name": name, "target": target, "pool": pool,
            "final_state": final_state, "joint": joint}

def jigsaw_state(front):
    return "minecraft:jigsaw[orientation=%s]" % front

class Template:

    def __init__(self):
        self.cells = {}
        self.entities = []

    def set(self, x, y, z, state, nbt=None):
        self.cells[(x, y, z)] = (state, nbt)

    def get(self, x, y, z):
        return self.cells.get((x, y, z), (None, None))[0]

    def fill_air(self, w, h, d, from_y=1):
        for y in range(from_y, h):
            for z in range(d):
                for x in range(w):
                    if (x, y, z) not in self.cells:
                        self.cells[(x, y, z)] = ("minecraft:air", None)

    def emit(self, size):
        palette, index = [], {}
        blocks = []
        for (x, y, z), (state, nbt) in sorted(self.cells.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            if state not in index:
                index[state] = len(palette)
                name, props = parse_state(state)
                entry = {"Name": String(name)}
                if props:
                    entry["Properties"] = {k: String(v) for k, v in props.items()}
                palette.append(entry)
            block = {"pos": [Int(x), Int(y), Int(z)], "state": Int(index[state])}
            if nbt:
                block["nbt"] = nbt
            blocks.append(block)
        return {
            "size": [Int(size[0]), Int(size[1]), Int(size[2])],
            "palette": palette,
            "blocks": blocks,
            "entities": self.entities,
            "DataVersion": Int(DATA_VERSION),
        }

BOAT_TYPE = re.compile(r'Type:"([a-z_]+)"')
FRAME_FACING = re.compile(r"Facing:(-?\d+)b")
FRAME_FIXED = re.compile(r"Fixed:(\d+)b")
FRAME_ROTATION = re.compile(r"ItemRotation:(\d+)b")
FRAME_ITEM = re.compile(r'id:"([a-z_:]+)"')
FRAME_COUNT = re.compile(r"count:(\d+)")

ENTITY_KEYS = {
    "minecraft:boat": {"Type"},
    "minecraft:item_frame": {"Facing", "Fixed", "ItemRotation", "Item"},
}

def top_level_keys(snbt):
    keys, depth, quote, token = [], 0, None, ""
    for ch in snbt:
        if quote is not None:
            if ch == quote:
                quote = None
            continue
        if ch in "\"'":
            quote, token = ch, ""
        elif ch in "{[":
            depth, token = depth + 1, ""
        elif ch in "}]":
            depth, token = depth - 1, ""
        elif ch == ",":
            token = ""
        elif ch == ":":
            if depth == 1 and token.strip():
                keys.append(token.strip())
            token = ""
        else:
            token += ch
    return keys

def add_entity(tpl, x, y, z, etype, snbt):
    ty = y + 1
    handled = ENTITY_KEYS.get(etype)
    if handled is None:
        raise SystemExit("no rule for entity %s" % etype)
    unhandled = [k for k in top_level_keys(snbt) if k not in handled]
    if unhandled:
        raise SystemExit("%s: nothing carries %s into the template (snbt %s)"
                         % (etype, ", ".join(unhandled), snbt))
    if etype == "minecraft:boat":
        nbt = {"id": "minecraft:boat", "Type": BOAT_TYPE.search(snbt).group(1)}
    else:
        fixed = FRAME_FIXED.search(snbt)
        rotation = FRAME_ROTATION.search(snbt)
        count = FRAME_COUNT.search(snbt)
        nbt = {"id": "minecraft:item_frame",
               "Facing": Byte(int(FRAME_FACING.search(snbt).group(1))),
               "Fixed": Byte(int(fixed.group(1)) if fixed else 0),
               "ItemRotation": Byte(int(rotation.group(1)) if rotation else 0),
               "Item": {"id": FRAME_ITEM.search(snbt).group(1),
                        "count": Int(int(count.group(1)) if count else 1)}}
    tpl.entities.append({
        "pos": [Double(x + 0.5), Double(ty), Double(z + 0.5)],
        "blockPos": [Int(x), Int(int(ty)), Int(z)],
        "nbt": nbt,
    })

def flip_wall_second_row(tpl, base):
    flipped = 0
    for (x, y, z), (state, nbt) in list(tpl.cells.items()):
        if y != base + 1 or "trapdoor" not in state:
            continue
        below = tpl.cells.get((x, base, z))
        if below is None or "trapdoor" not in below[0]:
            continue
        if "half=bottom" not in state:
            continue
        tpl.set(x, y, z, state.replace("half=bottom", "half=top"), nbt)
        flipped += 1
    return flipped

def build_piece(piece, margin=0):
    pid = piece["id"]
    legend = dict(LEGEND)
    legend.update(piece.get("legend", {}))
    tpl = Template()

    floored = any(y == -1 for y, _ in piece["layers"])
    base = 2 if floored else 1

    for y, rows in piece["layers"]:
        for z, row in enumerate(rows):
            for x, ch in enumerate(row):
                state = legend[ch]
                if state:
                    tpl.set(x + margin, y + base, z + margin, state)
    for (x1, y1, z1, x2, y2, z2, block) in piece.get("fills", []):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    tpl.set(x + margin, y + base, z + margin, block)

    w = max(len(r) for _, rows in piece["layers"] for r in rows) + 2 * margin
    d = max(len(rows) for _, rows in piece["layers"]) + 2 * margin

    fill_walls(tpl, pid, margin, base)
    fill_apron(tpl, pid, margin, base)
    shaped = shape_stairs(tpl)
    if shaped:
        print("  %s: %d stair(s) given a corner shape" % (pid, shaped))
    flipped = flip_wall_second_row(tpl, base)
    if flipped:
        print("  %s: %d second-row trapdoor(s) flipped to half=top" % (pid, flipped))

    if floored:
        for (x, y, z) in [c for c in tpl.cells if c[1] == base - 1]:
            tpl.set(x, base - 2, z, "minecraft:dirt")

    h = max(y for (_, y, _) in tpl.cells) + 1
    tpl.fill_air(w, h, d, from_y=base)

    for (x, y, z, facing, pool, final) in meta.CONNECTORS.get(pid, []):
        dx, dz = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}[facing]
        cx, cz = x + margin, z + margin
        drawn = tpl.get(cx, y + base, cz)
        inward = tpl.get(cx - dx, y + base, cz - dz)
        if drawn is not None and drawn != "minecraft:air":
            final = "minecraft:structure_void"
        elif final and inward is not None and inward != "minecraft:air":
            tpl.set(cx, y + base, cz, final)
            final = "minecraft:structure_void"
        while 0 < cx < w - 1 and 0 < cz < d - 1:
            ground(tpl, cx, cz, base)
            cx, cz = cx + dx, cz + dz
        if not (cx in (0, w - 1) or cz in (0, d - 1)):
            raise SystemExit("piece %s: connector %s is not on a boundary face" % (pid, (x, y, z)))
        name = meta.PATH_NAME if pool == "paths" else meta.DOOR_NAME
        tpl.set(cx, y + base, cz, jigsaw_state("%s_up" % facing),
                jigsaw_nbt(name, name, meta.POOL_IDS[pool], final or "minecraft:structure_void", "aligned"))

    for kind, pool in (("VILLAGERS", "villagers"), ("CATS", "cats")):
        for (x, y, z) in getattr(meta, kind).get(pid, []):
            floor = tpl.get(x + margin, y + base, z + margin)
            if floor is None or floor == "minecraft:air":
                raise SystemExit("piece %s: %s spawner at %s has no floor" % (pid, kind, (x, y, z)))
            tpl.set(x + margin, y + base, z + margin, jigsaw_state("up_north"),
                    jigsaw_nbt(meta.BOTTOM, meta.BOTTOM, meta.POOL_IDS[pool], floor, "rollable"))

    for (x, y, z), state in meta.JOB_SITES.get(pid, {}).items():
        cell = (x + margin, y + base, z + margin)
        held = tpl.cells.get(cell)
        if held is not None and held[0] not in (None, "minecraft:air"):
            raise SystemExit("piece %s: job site at %s is occupied by %s" % (pid, (x, y, z), held[0]))
        tpl.set(cell[0], cell[1], cell[2], state)

    for (x, y, z), table in meta.CHESTS.get(pid, {}).items():
        state = tpl.get(x + margin, y + base, z + margin)
        if state is None or not state.startswith("minecraft:chest"):
            raise SystemExit("piece %s: no chest at %s, found %s" % (pid, (x, y, z), state))
        tpl.set(x + margin, y + base, z + margin, state, {"id": "minecraft:chest", "LootTable": table})

    fill_containers(tpl)

    for (x, y, z, etype, snbt) in piece.get("entities", []):
        add_entity(tpl, x + margin, y + base - 1, z + margin, etype, snbt)

    return tpl.emit((w, h, d)), (w, h, d), base

def fill_containers(tpl):
    for (x, y, z), (state, nbt) in list(tpl.cells.items()):
        if nbt is not None or not state:
            continue
        kind = state.split("[")[0].split(":")[-1]
        if kind in ("chest", "barrel"):
            tpl.set(x, y, z, state, {"id": "minecraft:" + kind, "LootTable": meta.BARREL})

def ground(tpl, x, z, base):
    if (x, base - 1, z) not in tpl.cells:
        tpl.set(x, base - 1, z, "minecraft:gravel")
    if base > 1 and (x, base - 2, z) not in tpl.cells:
        tpl.set(x, base - 2, z, "minecraft:dirt")

def fill_walls(tpl, pid, margin, base):
    spec = meta.WALLS.get(pid)
    if not spec:
        return
    x0, z0, x1, z1, doorx, doorz, open_face = spec
    for y in range(meta.WALL_ROWS.get(pid, 2)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)) or (x, z) == (doorx, doorz):
                    continue
                side = "north" if z == z0 else "south" if z == z1 else "west" if x == x0 else "east"
                if side == open_face:
                    continue
                cell = (x + margin, y + base, z + margin)
                held = tpl.cells.get(cell)
                if held is not None and "trapdoor" not in held[0] and held[0] not in meta.WALL_REPLACES:
                    continue
                tpl.set(cell[0], cell[1], cell[2], meta.TRAPDOOR % meta.OUTWARD[side])

SIDES = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
CLOCKWISE = {"north": "east", "east": "south", "south": "west", "west": "north"}
COUNTER = {v: k for k, v in CLOCKWISE.items()}
OPPOSITE = {"north": "south", "south": "north", "west": "east", "east": "west"}
AXIS_OF = {"north": "z", "south": "z", "west": "x", "east": "x"}

def _facing(state):
    if not state or "_stairs" not in state:
        return None
    name, props = parse_state(state)
    return props.get("facing")

def _half(state):
    name, props = parse_state(state)
    return props.get("half", "bottom")

def shape_stairs(tpl):
    def at(cell):
        return (tpl.cells.get(cell) or (None, None))[0]

    def can_take_shape(state, cell, direction):
        dx, dz = SIDES[direction]
        other = at((cell[0] + dx, cell[1], cell[2] + dz))
        if _facing(other) is None:
            return True
        return _facing(other) != _facing(state) or _half(other) != _half(state)

    shaped = 0
    for cell, (state, nbt) in list(tpl.cells.items()):
        facing = _facing(state)
        if facing is None:
            continue
        name, props = parse_state(state)
        shape = "straight"
        dx, dz = SIDES[facing]
        ahead = at((cell[0] + dx, cell[1], cell[2] + dz))
        if _facing(ahead) and _half(ahead) == _half(state):
            other = _facing(ahead)
            if AXIS_OF[other] != AXIS_OF[facing] and can_take_shape(state, cell, OPPOSITE[other]):
                shape = "outer_left" if other == COUNTER[facing] else "outer_right"
        if shape == "straight":
            bx, bz = SIDES[OPPOSITE[facing]]
            behind = at((cell[0] + bx, cell[1], cell[2] + bz))
            if _facing(behind) and _half(behind) == _half(state):
                other = _facing(behind)
                if AXIS_OF[other] != AXIS_OF[facing] and can_take_shape(state, cell, other):
                    shape = "inner_left" if other == COUNTER[facing] else "inner_right"
        if shape == "straight":
            continue
        props["shape"] = shape
        rebuilt = "%s[%s]" % (name, ",".join("%s=%s" % kv for kv in sorted(props.items())))
        tpl.set(cell[0], cell[1], cell[2], rebuilt, nbt)
        shaped += 1
    return shaped

def fill_apron(tpl, pid, margin, base):
    spec = meta.APRONS.get(pid)
    if not spec:
        return
    x0, z0, x1, z1 = spec
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if x0 <= x <= x1 and z0 <= z <= z1:
                continue
            if (x < x0 or x > x1) and (z < z0 or z > z1):
                tpl.set(x + margin, base, z + margin, "minecraft:tuff")
                continue
            face = "south" if z < z0 else "north" if z > z1 else "east" if x < x0 else "west"
            tpl.set(x + margin, base, z + margin, meta.APRON_STAIR % face)

def build_flat(spec, legend):
    tpl = Template()
    layers = spec.get("layers") or [(0, spec["rows"])]
    w = max(len(r) for _, rows in layers for r in rows)
    d = max(len(rows) for _, rows in layers)
    h = 0
    for y, rows in layers:
        h = max(h, y + 1)
        for z, row in enumerate(rows):
            for x, ch in enumerate(row):
                state = legend[ch]
                if state:
                    tpl.set(x, y, z, state)
    return tpl, w, d, h

def build_dock():
    tpl, w, d, h = build_flat(shoremod.DOCK, shoremod.LEGEND)
    spot = None
    for y, rows in shoremod.DOCK["layers"]:
        for z, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "r" and spot is None:
                    spot = (x, y, z)
    if spot is None:
        raise SystemExit("dock: no barrel in the drawing to put the fishing rod in")
    tpl.set(spot[0], spot[1], spot[2], shoremod.LEGEND["r"],
            {"id": "minecraft:barrel", "LootTable": meta.DOCK})
    fill_containers(tpl)
    return tpl.emit((w, h, d)), (w, h, d)

def build_link():
    tpl, w, d, h = build_flat(pathmod.LINK, pathmod.LEGEND)
    return tpl.emit((w, h, d)), (w, h, d)

def build_torch_post():
    tpl, w, d, h = build_flat(pathmod.TORCH_POST, pathmod.LEGEND)
    return tpl.emit((w, h, d)), (w, h, d)

def build_path(spec):
    tpl, w, d, _ = build_flat(spec, pathmod.LEGEND)
    for (x, z, facing, kind) in spec["conns"]:
        if not (x in (0, w - 1) or z in (0, d - 1)):
            raise SystemExit("path %s: connector %s is not on a boundary face" % (spec["id"], (x, z)))
        if kind == "hut":
            name, pool = meta.DOOR_NAME, "huts"
        elif kind == "end":
            name, pool = meta.PATH_NAME, "empty"
        else:
            name, pool = meta.PATH_NAME, "paths"
        tpl.set(x, 1, z, jigsaw_state("%s_up" % facing),
                jigsaw_nbt(name, name, meta.POOL_IDS[pool], "minecraft:structure_void", "aligned"))
    return tpl.emit((w, 2, d)), (w, 2, d)

def build_mob(height, nbt):
    tpl = Template()
    tpl.set(0, 0, 0, jigsaw_state("down_south"),
            jigsaw_nbt(meta.BOTTOM, meta.BOTTOM, "minecraft:empty", "minecraft:structure_void", "rollable"))
    tpl.entities.append({
        "pos": [Double(0.5), Double(1.0), Double(0.5)],
        "blockPos": [Int(0), Int(1), Int(0)],
        "nbt": nbt,
    })
    return tpl.emit((1, height, 1)), (1, height, 1)

def single(location, projection="rigid", processors="minecraft:empty"):
    return {"element_type": "minecraft:single_pool_element", "location": location,
            "processors": processors, "projection": projection}

def pool(name, elements, fallback="minecraft:empty"):
    write_json("data/%s/worldgen/template_pool/%s/%s.json" % (NS, DIR, name),
               {"fallback": fallback, "elements": elements})

def element(weight, el):
    return {"weight": weight, "element": el}

def main():
    pieces = {p["id"]: p for p in load_pieces()}
    by_pool = {"centers": [], "huts": []}
    report = []
    located = {}
    bases = {}

    for pid, piece in sorted(pieces.items()):
        if pid in meta.SKIP:
            continue
        group, weight = meta.POOLS.get(pid, (None, 0))
        name = "%s_%s" % (pid, slug(piece["name"]))
        location = "%s:%s/%s" % (NS, DIR, name)
        root, size, base = build_piece(piece, margin=BUILDING_MARGIN)
        bases[pid] = base
        write_nbt(os.path.join(RES, "data", NS, "structure", DIR, name + ".nbt"), root)
        report.append((pid, name, size, len(root["blocks"]), len(root["entities"]), group, weight))
        located[pid] = location
        if group:
            by_pool[group].append(element(weight, single(location)))

    root, size = build_dock()
    write_nbt(os.path.join(RES, "data", NS, "structure", DIR, "dock.nbt"), root)
    report.append(("dock", "dock", size, len(root["blocks"]), 0, "shore", 0))

    root, size = build_link()
    write_nbt(os.path.join(RES, "data", NS, "structure", DIR, "path_link.nbt"), root)
    report.append(("path_link", "path_link", size, len(root["blocks"]), 0, "shore", 0))

    root, size = build_torch_post()
    write_nbt(os.path.join(RES, "data", NS, "structure", DIR, "torch_post.nbt"), root)
    report.append(("torch_post", "torch_post", size, len(root["blocks"]), 0, "shore", 0))

    path_elements, end_elements = [], []
    for spec in pathmod.PATHS:
        root, size = build_path(spec)
        write_nbt(os.path.join(RES, "data", NS, "structure", DIR, spec["id"] + ".nbt"), root)
        report.append((spec["id"], spec["id"], size, len(root["blocks"]), 0, spec["pool"], spec["weight"]))
        el = element(spec["weight"], single("%s:%s/%s" % (NS, DIR, spec["id"]),
                                            projection="terrain_matching",
                                            processors="%s:hut_path" % NS))
        (path_elements if spec["pool"] == "paths" else end_elements).append(el)

    mob_elements = {"villagers": [], "cats": []}
    for (mid, group, weight, height, nbt) in mobmod.MOBS:
        root, size = build_mob(height, nbt)
        write_nbt(os.path.join(RES, "data", NS, "structure", DIR, mid + ".nbt"), root)
        report.append((mid, mid, size, len(root["blocks"]), 1, group, weight))
        mob_elements[group].append(element(weight, single("%s:%s/%s" % (NS, DIR, mid))))
    if mobmod.CATS_EMPTY_WEIGHT:
        mob_elements["cats"].append(element(mobmod.CATS_EMPTY_WEIGHT,
                                            {"element_type": "minecraft:empty_pool_element"}))

    by_pool["huts"].append(element(meta.HUTS_EMPTY_WEIGHT,
                                   {"element_type": "minecraft:empty_pool_element"}))

    pool("centers", by_pool["centers"])
    pool("huts", by_pool["huts"])
    path_elements.append(element(PATHS_EMPTY_WEIGHT, {"element_type": "minecraft:empty_pool_element"}))
    pool("paths", path_elements, fallback="%s:%s/path_ends" % (NS, DIR))
    pool("path_ends", end_elements)
    pool("villagers", mob_elements["villagers"])
    pool("cats", mob_elements["cats"])

    write_json("data/%s/worldgen/structure/hut_village.json" % NS, {
        "type": "%s:hut_village" % NS,
        "biomes": "#%s:has_structure/hut_village" % NS,
        "step": "surface_structures",
        "terrain_adaptation": "beard_thin",
        "spawn_overrides": {
            "monster": {"bounding_box": "full", "spawns": []},
            "creature": {"bounding_box": "piece", "spawns": []},
        },
        "start_pool": "%s:%s/centers" % (NS, DIR),
        "size": VILLAGE_SIZE,
        "max_distance_from_center": VILLAGE_RADIUS,
        "shore_search": SHORE_SEARCH,
        "max_height_spread": MAX_HEIGHT_SPREAD,
        "dock": "%s:%s/dock" % (NS, DIR),
        "dock_landward": shoremod.LANDWARD,
        "path_link": "%s:%s/path_link" % (NS, DIR),
        "required_buildings": [
            {"buildings": [located[pid] for pid in BED_BUILDINGS], "minimum": MIN_BEDS},
            {"buildings": [located[pid] for pid in FARM_BUILDINGS], "minimum": MIN_FARMS},
        ],
        "shore_buildings": [[located[pid] for pid in group] for group in SHORE_BUILDINGS],
        "shore_ground_delta": max(bases[pid] for group in SHORE_BUILDINGS for pid in group),
        "spacing": SPACING,
        "separation": SEPARATION,
        "salt": SALT,
    })
    write_json("data/%s/worldgen/structure_set/hut_villages.json" % NS, {
        "structures": [{"structure": "%s:hut_village" % NS, "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": SPACING,
                      "separation": SEPARATION, "salt": SALT},
    })
    write_json("data/%s/tags/worldgen/biome/has_structure/hut_village.json" % NS,
               {"values": ["%s:island_jungle" % NS, "%s:island_sand" % NS]})

    write_json("data/%s/worldgen/processor_list/hut_path.json" % NS, {
        "processors": [{
            "processor_type": "minecraft:rule",
            "rules": [{
                "input_predicate": {"predicate_type": "minecraft:random_block_match",
                                    "block": "minecraft:gravel", "probability": PATH_COARSE_CHANCE},
                "location_predicate": {"predicate_type": "minecraft:always_true"},
                "output_state": "minecraft:coarse_dirt",
            }],
        }],
    })

    write_loot()

    print("%d templates, %d json files" % (
        sum(1 for r in report if True), len(written)))
    for (pid, name, size, blocks, ents, group, weight) in report:
        print("  %-22s %-9s %4d blocks %2d ent  %-10s w%d" % (
            name[:22], "%dx%dx%d" % size, blocks, ents, group or "-", weight))

def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}

def pool_entries(rolls, entries):
    return {"rolls": rolls, "bonus_rolls": 0.0, "entries": entries}

def item(name, count=None, weight=None, chance=None):
    entry = {"type": "minecraft:item", "name": name}
    if count:
        entry["functions"] = [{"function": "minecraft:set_count", "add": False,
                               "count": uniform(count[0], count[1])}]
    if weight:
        entry["weight"] = weight
    if chance:
        entry["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return entry

GOODS = [
    item("minecraft:cod", (1, 3), weight=9),
    item("minecraft:salmon", (1, 2), weight=7),
    item("minecraft:cooked_cod", (1, 3), weight=7),
    item("minecraft:cooked_salmon", (1, 2), weight=6),
    item("minecraft:bread", (2, 4), weight=7),
    item("minecraft:carrot", (2, 5), weight=6),
    item("minecraft:potato", (2, 5), weight=6),
    item("minecraft:beetroot", (1, 4), weight=4),
    item("minecraft:dried_kelp", (3, 8), weight=5),
    item("minecraft:emerald", (1, 4), weight=9),
    item("minecraft:iron_ingot", (1, 3), weight=4),
    item("minecraft:bamboo", (2, 6), weight=6),
    item("minecraft:string", (1, 4), weight=6),
    item("minecraft:jungle_log", (1, 4), weight=5),
    item("minecraft:coal", (1, 3), weight=5),
    item("minecraft:kelp", (2, 6), weight=3),
    item("minecraft:sea_pickle", (1, 3), weight=3),
    item("minecraft:wheat", (1, 4), weight=2),
    item("minecraft:nautilus_shell", (1, 1), weight=2),
    item("minecraft:turtle_scute", (1, 1), weight=2),
]

def write_loot():
    base = "data/%s/loot_table/chests/" % NS
    write_json(base + "hut_village_hall.json", {
        "type": "minecraft:chest",
        "pools": [
            pool_entries(1.0, [item("minecraft:heart_of_the_sea")]),
            pool_entries(uniform(4, 6), GOODS),
        ],
    })
    write_json(base + "hut_village_house.json", {
        "type": "minecraft:chest",
        "pools": [
            pool_entries(uniform(3, 5), GOODS),
        ],
    })
    write_json(base + "hut_village_store.json", {
        "type": "minecraft:chest",
        "pools": [
            pool_entries(uniform(4, 7), GOODS),
        ],
    })
    write_json(base + "hut_village_barrel.json", {
        "type": "minecraft:chest",
        "pools": [
            pool_entries(uniform(3, 5), GOODS),
        ],
    })
    write_json(base + "hut_village_dock.json", {
        "type": "minecraft:chest",
        "pools": [
            pool_entries(1.0, [item("minecraft:fishing_rod")]),
            pool_entries(uniform(3, 5), GOODS),
        ],
    })

if __name__ == "__main__":
    main()
