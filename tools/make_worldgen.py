import copy
import io
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def ocean_field_constant(name):
    src = io.open(os.path.join(ROOT, "src", "main", "java", "com", "oceania", "worldgen", "field",
                               "OceanFields.java"), encoding="utf-8").read()
    hit = re.search(r"static final double %s\s*=\s*([^;]+);" % re.escape(name), src)
    if not hit:
        raise SystemExit("make_worldgen: OceanFields has no 'static final double %s = ...;'" % name)
    text = hit.group(1).strip()
    if not re.fullmatch(r"-?\d+(\.\d+)?", text):
        raise SystemExit(
            "make_worldgen: OceanFields.%s is %r, not a plain number. The generator bakes this value into the\n"
            "density, so it cannot be a -D dial - a launch flag would move the water and not the wall. Make it a\n"
            "literal, or stop baking it here." % (name, text))
    return float(text)

RES = os.path.join(ROOT, "src", "main", "resources")
CANDIDATES = [
    os.environ.get("OCEANIA_VANILLA_DATA", ""),
    os.path.join(ROOT, "tools", "vanilla-data-26.3"),
]
VAN = next((c for c in CANDIDATES if c and os.path.isdir(c)), None)
if VAN is None or not os.path.isdir(f"{VAN}/data/minecraft/worldgen/material_rule"):
    raise SystemExit("26.3 vanilla data not found: run python tools/fetch_vanilla_data.py first (or set OCEANIA_VANILLA_DATA)")
van_ns = json.load(open(f"{VAN}/data/minecraft/worldgen/noise_settings/overworld.json"))
van_dim = json.load(open(f"{VAN}/data/minecraft/dimension_type/overworld.json"))
van_preset = json.load(open(f"{VAN}/data/minecraft/worldgen/world_preset/normal.json"))
van_ocean = json.load(open(f"{VAN}/data/minecraft/worldgen/biome/warm_ocean.json"))
va = van_ns["aquifers"]

MIN_Y, HEIGHT, SEA = -704, 1024, 63
MOD_MOBS = os.environ.get("OCEANIA_MOD_MOBS", "1") != "0"

def write(rel, obj):
    p = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", newline="\n", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")

def field(output):
    return {"type": "oceania:field", "output": output}

def once(a):
    return {"type": "minecraft:cache", "input": a}

def fc2d(f):
    return once({"type": "minecraft:slice", "axis": "y", "coordinate": 0, "input": f})

def interp(a):
    return {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8, "input": a}

def grad(fy, fv, ty, tv):
    return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": fy, "from_value": fv, "to_coordinate": ty, "to_value": tv}

def beardifier():
    return {"type": "minecraft:beardifier"}

def add(a, b):
    return {"type": "minecraft:add", "left": a, "right": b}

def mul(a, b):
    return {"type": "minecraft:mul", "left": a, "right": b}

def clamp(a, lo, hi):
    return {"type": "minecraft:clamp", "input": a, "min": lo, "max": hi}

def noise(n, xz, y):
    return {"type": "minecraft:noise", "noise": n, "xz_scale": xz, "y_scale": y}

def op(t, a):
    return {"type": "minecraft:" + t, "input": a}

def dmin(a, b):
    return {"type": "minecraft:min", "left": a, "right": b}

def dmax(a, b):
    return {"type": "minecraft:max", "left": a, "right": b}

def choice(inp, lo, hi, inside, outside):
    return {"type": "minecraft:range_choice", "input": inp, "min_inclusive": lo, "max_exclusive": hi,
            "when_in_range": inside, "when_out_of_range": outside}

def ramp(a, lo, hi):
    return clamp(mul(add(a, -float(lo)), 1.0 / (hi - lo)), 0.0, 1.0)

Y = "minecraft:y"
delta = add(fc2d(field("floor")), mul(-1.0, Y))
base_density = mul(delta, 1.0 / 48.0)
detail_gate = clamp(add(mul(delta, 1.0 / 8.0), -0.25), 0.0, 1.0)
detail = mul(detail_gate, mul(0.12, noise("minecraft:cave_cheese", 0.5, 0.3)))

DEEP_DARK_SELECT = 0.45
YOUNG_EROSION = -0.44

BASE, DEPTH, CAVES = "oceania:base", "oceania:depth", "oceania:caves"
ENTR, SPAG2D = "oceania:caves/entrances", "oceania:caves/spaghetti_2d"
ROUGH = "minecraft:overworld/caves/spaghetti_roughness_function"
WALL_XZ, WALL_Y = 0.67, 1.0
WALL_BAND_IN, WALL_BAND_OUT = 24, 48
CAVE_FULL = 1.5625
CAVE_DEEP_IN, CAVE_DEEP_OUT = 260, 300
SEA_CAVE_Y = (51, 53, 61, 63)
BLUE_HOLE_DEPTH = 110

CAVE_TOP, CAVE_MID_BOTTOM = 32, 128
DEPTH_SCALE = 128.0
BEARD_SCALE_IN, BEARD_SCALE_OUT, BEARD_SCALE = 48, 96, 0.25
CAVE_THIN_IN, CAVE_THIN_OUT, CAVE_THIN = 96, 200, 0.5
CAVE_GATE_RAMP = 3
CAVE_CHEESE_BIAS = float(os.environ.get("OCEANIA_CHEESE", "0.20"))

wall_noise = noise("oceania:wall", WALL_XZ, WALL_Y)
wall_term = mul(fc2d(field("wall")), mul(ramp(op("abs", delta), WALL_BAND_OUT, WALL_BAND_IN), wall_noise))

sea_y = mul(ramp(Y, SEA_CAVE_Y[0], SEA_CAVE_Y[1]), ramp(Y, SEA_CAVE_Y[3], SEA_CAVE_Y[2]))
sea_term = mul(fc2d(field("sea_cave")), mul(sea_y, add(wall_noise, -1.2)))

bh_term = mul(fc2d(field("blue_hole")), mul(ramp(delta, BLUE_HOLE_DEPTH + 8, BLUE_HOLE_DEPTH), -4.0))

write("data/oceania/worldgen/density_function/depth.json", once(mul(fc2d(field("cave_depth_scale")), delta)))

write("data/oceania/worldgen/density_function/base.json",
      once(clamp(add(add(base_density, detail), add(wall_term, add(sea_term, bh_term))), -1.0, 1.0)))

def mapped(n, lo, hi, xz=1.0, y=1.0):
    return add((lo + hi) * 0.5, mul((hi - lo) * 0.5, noise(n, xz, y)))

RARITY = {
    "type_1": ([-0.5, 0.0, 0.5], [0.75, 1.0, 1.5, 2.0]),
    "type_2": ([-0.75, -0.5, 0.5, 0.75], [0.5, 0.75, 1.0, 2.0, 3.0]),
}

def weird(inp, n, mapper):
    thresholds, rarities = RARITY[mapper]
    return op("abs", {"type": "minecraft:interval_select", "input": inp, "thresholds": thresholds,
                      "functions": [mul(noise(n, 1.0 / r, 1.0 / r), r) for r in rarities]})

rarity3d = once(noise("minecraft:spaghetti_3d_rarity", 2.0, 1.0))
spag3d = clamp(add(dmax(weird(rarity3d, "minecraft:spaghetti_3d_1", "type_1"),
                        weird(rarity3d, "minecraft:spaghetti_3d_2", "type_1")),
                   mapped("minecraft:spaghetti_3d_thickness", -0.065, -0.088)), -1.0, 1.0)
shaft = add(add(noise("minecraft:cave_entrance", 0.75, 0.5), 0.37), mul(0.3, ramp(delta, 34, 74)))
write("data/oceania/worldgen/density_function/caves/entrances.json", once(dmin(shaft, add(ROUGH, spag3d))))

s2_thick = once(mapped("minecraft:spaghetti_2d_thickness", -0.6, -1.3, 2.0, 1.0))
s2_band = op("abs", add(mapped("minecraft:spaghetti_2d_elevation", -8.0, 8.0, 1.0, 0.0),
                        clamp(mul(add(delta, -64.0), 0.125), -40.0, 8.0)))
s2 = dmax(add(weird(noise("minecraft:spaghetti_2d_modulator", 2.0, 1.0), "minecraft:spaghetti_2d", "type_2"),
              mul(0.083, s2_thick)),
          op("cube", add(s2_band, s2_thick)))
write("data/oceania/worldgen/density_function/caves/spaghetti_2d.json", clamp(s2, -1.0, 1.0))

NOODLE_TOP, NOODLE_BOTTOM = -8.0, 300.0

def noodle_band(inner, outside):
    return choice(delta, NOODLE_TOP, NOODLE_BOTTOM, inner, outside)

RIDGE = 2.6666666666666665
noodle = choice(noodle_band(noise("minecraft:noodle", 1.0, 1.0), -1.0), -1000000.0, 0.0, 64.0,
                add(noodle_band(mapped("minecraft:noodle_thickness", -0.05, -0.1), 0.0),
                    mul(1.5, dmax(op("abs", noodle_band(noise("minecraft:noodle_ridge_a", RIDGE, RIDGE), 0.0)),
                                  op("abs", noodle_band(noise("minecraft:noodle_ridge_b", RIDGE, RIDGE), 0.0))))))

cheese = add(clamp(add(CAVE_CHEESE_BIAS, noise("minecraft:cave_cheese", 1.0, 2.0 / 3.0)), -1.0, 1.0),
             clamp(add(1.5, mul(-0.64, DEPTH)), 0.0, 0.5))
caverns = add(mul(4.0, op("square", noise("minecraft:cave_layer", 1.0, 8.0))), cheese)
tunnels = dmin(dmin(caverns, ENTR), add(SPAG2D, ROUGH))
write("data/oceania/worldgen/density_function/caves.json",
      choice(DEPTH, -1000000.0, CAVE_FULL, dmin(DEPTH, mul(5.0, ENTR)),
             dmin(tunnels, noodle)))

seal = fc2d(field("seal"))
deep_thin = add(1.0, mul(-(1.0 - CAVE_THIN), ramp(delta, CAVE_THIN_IN, CAVE_THIN_OUT)))
cave_gate = mul(mul(mul(ramp(add(delta, mul(-1.0, seal)), 0.0, float(CAVE_GATE_RAMP)), ramp(delta, CAVE_DEEP_OUT, CAVE_DEEP_IN)),
                    deep_thin),
                fc2d(field("cave")))

WET_SHELL = ocean_field_constant("WET_SHELL")
below_air = add(delta, mul(-1.0, fc2d(field("cave_air"))))
wet_shell = mul(fc2d(field("wet")),
                mul(ramp(below_air, -1.0, 0.0), ramp(below_air, WET_SHELL + 2.0, float(WET_SHELL))))

WET_DEPTH = ocean_field_constant("WET_DEPTH")
WET_WALL_PAD = 3
WET_WALL = WET_DEPTH + WET_WALL_PAD
wet_wall = mul(fc2d(field("wet_edge")), ramp(delta, WET_WALL + 2.0, float(WET_WALL)))
cave_gate = mul(cave_gate, add(1.0, mul(-1.0, dmax(wet_shell, wet_wall))))
carved = add(BASE, mul(-1.0, mul(cave_gate, add(BASE, mul(-1.0, dmin(BASE, CAVES))))))
beard_scale = add(1.0, mul(-(1.0 - BEARD_SCALE), ramp(delta, BEARD_SCALE_IN, BEARD_SCALE_OUT)))
BEARD_FILL_DEPTH, BEARD_FILL = 32, 2.0
surface_beard = choice(delta, float(BEARD_FILL_DEPTH), 10000.0, beardifier(), mul(BEARD_FILL, dmax(beardifier(), 0.0)))
final_density = add(interp(mul(beard_scale, carved)), surface_beard)
initial_density = clamp(base_density, -1.0, 1.0)

def block(name):
    return {"type": "minecraft:block", "result_state": name}

def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}

def cond(c, then):
    return {"type": "minecraft:condition", "if_true": c, "then_run": then}

def biome_is(*names):
    return {"type": "minecraft:biome", "biome_is": [f"oceania:{n}" for n in names]}

def vgrad(name, lo, hi):
    return {"type": "minecraft:vertical_gradient", "random_name": name, "true_at_and_below": lo, "false_at_and_above": hi}

def stone_depth(offset):
    return {"type": "minecraft:stone_depth", "offset": offset, "surface_type": "floor", "add_surface_depth": False, "secondary_depth_range": 0}

floor_cond = stone_depth(0)
under_cond = {"type": "minecraft:stone_depth", "offset": 0, "surface_type": "floor", "add_surface_depth": True, "secondary_depth_range": 0}
above_water = {"type": "minecraft:water", "offset": -1, "surface_depth_multiplier": 0, "add_stone_depth": False}
surface_noise_high = {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.0, "max_threshold": 10.0}
SAND_BIOMES = ("island_sand", "sandy_shelf", "reef", "lagoon")
GRAVEL_BIOMES = ("kelp_forest", "abyssal_plain")

above_surface = {"type": "minecraft:above_preliminary_surface"}

VEIN_TOP, VEIN_BOTTOM = 16, 200

def vein_band(n, outside=0.0):
    return interp(choice(delta, float(VEIN_TOP), float(VEIN_BOTTOM), n, outside))

COPPER_TOP, COPPER_BOTTOM, IRON_TOP, IRON_BOTTOM = 24, 80, 96, 190
vein_toggle = once(vein_band(noise("minecraft:ore_veininess", 1.5, 1.5)))
vein_mask = once(choice(vein_toggle, -0.4, 0.4, -1.0,
                        add(0.08, mul(-1.0, dmax(op("abs", vein_band(noise("minecraft:ore_vein_a", 4.0, 4.0), 1.0)),
                                                 op("abs", vein_band(noise("minecraft:ore_vein_b", 4.0, 4.0), 1.0)))))))
vein_gap = add(-0.3, mul(-1.0, noise("minecraft:ore_gap", 1.0, 1.0)))
vein_richness = add(clamp(op("abs", vein_toggle), 0.4, 0.6), -0.3)

def vein_density(sign, top, bottom):
    strength = add(mul(sign, vein_toggle), -0.4)
    taper = add(mul(clamp(dmin(add(float(bottom), mul(-1.0, delta)), add(delta, -float(top))), 0.0, 20.0), 0.01), -0.2)
    core = choice(add(strength, taper), 0.0, 1000000.0, 0.7, -1.0)
    return mul(fc2d(field("veins")), choice(delta, float(top), float(bottom), choice(vein_mask, 0.0, 1000000.0, core, -1.0), -1.0))

def ore_vein(ore, raw, filler, density):
    return {"type": "minecraft:ore_vein", "ore_block": ore, "raw_ore_block": raw, "filler_block": filler, "raw_ore_chance": 0.02,
            "density": density, "richness": vein_richness, "filler_gap": vein_gap}

copper_vein = ore_vein("minecraft:copper_ore", "minecraft:raw_copper_block", "minecraft:granite", vein_density(1.0, COPPER_TOP, COPPER_BOTTOM))
iron_vein = ore_vein("minecraft:deepslate_iron_ore", "minecraft:raw_iron_block", "minecraft:tuff", vein_density(-1.0, IRON_TOP, IRON_BOTTOM))

def noise3(name, lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": name, "min_threshold": lo, "max_threshold": hi, "is_3d": True}

sulfur_bands = seq(
    cond(noise3("minecraft:sulfur_cave_gradient", -0.4, -0.1), block("minecraft:cinnabar")),
    cond(noise3("minecraft:sulfur_cave_gradient", 0.0, 0.4), block("minecraft:sulfur")),
    cond(noise3("minecraft:sulfur_cave_gradient", 0.4, 10.0), block("minecraft:cinnabar")),
)

surface_rule = seq(
    cond(vgrad("minecraft:bedrock_floor", {"above_bottom": 0}, {"above_bottom": 5}), block("minecraft:bedrock")),
    copper_vein,
    iron_vein,
    cond(stone_depth(127), cond(biome_is("island_barren", "barren_flank"), block("minecraft:basalt"))),
    cond(stone_depth(2), cond(biome_is("sulfur_caves"), sulfur_bands)),
    cond(above_surface, seq(
        cond(floor_cond, seq(
            cond(biome_is("island_jungle"), seq(cond(above_water, block("minecraft:grass_block")), block("minecraft:sand"))),
            cond(biome_is("ridge"), block("minecraft:basalt")),
            cond(biome_is("island_cliff"), block("minecraft:stone")),
            cond(biome_is(*SAND_BIOMES), block("minecraft:sand")),
            cond(biome_is(*GRAVEL_BIOMES), block("minecraft:gravel")),
        )),
        cond(stone_depth(1), cond(biome_is(*GRAVEL_BIOMES), block("minecraft:gravel"))),
        cond(stone_depth(2), cond(biome_is(*GRAVEL_BIOMES), cond(surface_noise_high, block("minecraft:gravel")))),
        cond(under_cond, seq(
            cond(biome_is("island_jungle"), block("minecraft:dirt")),
            cond(biome_is(*SAND_BIOMES), block("minecraft:sandstone")),
        )),
    )),
    cond({"type": "oceania:floor_depth", "depth": CAVE_MID_BOTTOM - 8, "blend": 16}, block("minecraft:deepslate")),
    cond(vgrad("minecraft:deepslate", {"absolute": -160}, {"absolute": -140}), block("minecraft:deepslate")),
)

ROUTER = {
    "continents": fc2d(field("continents")),
    "depth": mul(fc2d(field("cave_biomes")), clamp(mul(delta, 1.0 / DEPTH_SCALE), -2.0, 2.0)),
    "erosion": fc2d(field("erosion")),
    "ridges": fc2d(field("weirdness")),
    "temperature": fc2d(field("cliff")),
    "vegetation": fc2d(noise("oceania:cave_select", 1.0, 0.0)),
}
for _name, _fn in ROUTER.items():
    write(f"data/oceania/worldgen/density_function/router/{_name}.json", _fn)

def router(name):
    return f"oceania:router/{name}"

floor_level = fc2d(field("floor"))

aquifer_exclusion = dmin(add(router("depth"), -(CAVE_MID_BOTTOM / DEPTH_SCALE)),
                         dmin(add(router("vegetation"), -DEEP_DARK_SELECT), add(router("erosion"), -YOUNG_EROSION)))

noise_settings = {
    "aquifers": {
        "barrier": va["barrier"],
        "exclusion": aquifer_exclusion,
        "fluid_level_floodedness": va["fluid_level_floodedness"],
        "fluid_level_spread": va["fluid_level_spread"],
        "lava": va["lava"],
        "surface_level": floor_level,
    },
    "default_block": "minecraft:stone",
    "default_fluid": "minecraft:water",
    "disable_mob_generation": False,
    "legacy_random_source": False,
    "material_rule": surface_rule,
    "noise": {"height": HEIGHT, "min_y": MIN_Y},
    "noise_router": {
        "chunk_surface_level": floor_level,
        "continents": router("continents"),
        "depth": router("depth"),
        "erosion": router("erosion"),
        "final_density": final_density,
        "ridges": router("ridges"),
        "temperature": router("temperature"),
        "vegetation": router("vegetation"),
    },
    "sea_level": SEA,
    "spawn_target": [{router("continents"): [0.05, 0.45], router("erosion"): [0.3, 1.0]}],
}
write("data/minecraft/worldgen/noise_settings/overworld.json", noise_settings)

dim = copy.deepcopy(van_dim)
dim["min_y"] = MIN_Y
dim["height"] = HEIGHT
dim["logical_height"] = HEIGHT
write("data/minecraft/dimension_type/overworld.json", dim)

FULL = [-1.0, 1.0]

def box(biome, c, e=FULL, w=FULL, t=FULL, h=FULL, d=FULL):
    return {"biome": f"oceania:{biome}", "parameters": {"temperature": t, "humidity": h, "continentalness": c,
                                                         "erosion": e, "weirdness": w, "depth": d, "offset": 0.0}}

NOT_YOUNG = [YOUNG_EROSION, 1.0]
GENTLE = [-1.0, 0.5]
STEEP = [0.5, 1.0]
boxes = [
    box("trench", [-1.0, -0.85]),
    box("abyssal_plain", [-0.85, -0.55], w=[-1.0, 0.3]),
    box("ridge", [-0.85, -0.55], w=[0.3, 1.0]),
    box("barren_flank", [-0.55, 0.6], e=[-1.0, -0.44]),
    box("ridge", [-0.55, -0.15], e=NOT_YOUNG, w=[0.3, 1.0]),
    box("seamount_flank", [-0.55, -0.15], e=NOT_YOUNG, w=[-1.0, 0.3]),
    box("rocky_floor", [-0.15, 0.05], e=[-0.44, 0.2]),
    box("kelp_forest", [-0.15, 0.05], e=[0.2, 1.0]),
    box("reef", [0.05, 0.3], e=NOT_YOUNG, w=[-0.6, 1.0]),
    box("lagoon", [0.05, 0.3], e=NOT_YOUNG, w=[-1.0, -0.6]),
    box("island_cliff", [0.3, 0.6], e=NOT_YOUNG, t=STEEP),
    box("sandy_shelf", [0.3, 0.45], e=NOT_YOUNG, t=GENTLE),
    box("island_sand", [0.45, 0.6], e=NOT_YOUNG, t=GENTLE),
    box("island_barren", [0.6, 1.0], e=[-1.0, -0.44]),
    box("island_jungle", [0.6, 1.0], e=[-0.44, 0.24], t=GENTLE),
    box("island_sand", [0.6, 1.0], e=[0.24, 1.0], t=GENTLE),
    box("island_cliff", [0.6, 1.0], e=NOT_YOUNG, t=STEEP),
]
SURFACE_D = [-2.0, CAVE_TOP / DEPTH_SCALE]
MID_D = [CAVE_TOP / DEPTH_SCALE, CAVE_MID_BOTTOM / DEPTH_SCALE]
DEEP_D = [CAVE_MID_BOTTOM / DEPTH_SCALE, 2.0]
SULFUR_H = [-1.0, -0.55]
DRIPSTONE_H = [SULFUR_H[1], -0.33]
LUSH_H = [0.14, 1.0]
DEEP_DARK_H = [DEEP_DARK_SELECT, 1.0]
LUSH_MIN_C = -0.15
CAVE_BIOME_NAMES = ("dripstone_caves", "lush_caves", "sulfur_caves", "deep_dark")

def is_young(b):
    return b["parameters"]["erosion"][1] <= -0.44

def lush_possible(b):
    return b["parameters"]["continentalness"][1] > LUSH_MIN_C

def with_layer(b, d, h):
    nb = copy.deepcopy(b)
    nb["parameters"]["depth"] = d
    nb["parameters"]["humidity"] = h
    return nb

surface_boxes = [with_layer(b, SURFACE_D, FULL) for b in boxes]
layer_boxes = []
for b in boxes:
    if is_young(b):
        layer_boxes.append(with_layer(b, [MID_D[0], DEEP_D[1]], FULL))
        continue
    layer_boxes.append(with_layer(b, MID_D, [DRIPSTONE_H[1], LUSH_H[0] if lush_possible(b) else 1.0]))
    layer_boxes.append(with_layer(b, DEEP_D, [-1.0, DEEP_DARK_H[0]]))
cave_boxes = [
    box("dripstone_caves", [-1.0, 1.0], e=NOT_YOUNG, h=DRIPSTONE_H, d=MID_D),
    box("lush_caves", [LUSH_MIN_C, 1.0], e=NOT_YOUNG, h=LUSH_H, d=MID_D),
    box("sulfur_caves", [-1.0, 1.0], e=NOT_YOUNG, h=SULFUR_H, d=MID_D),
    box("deep_dark", [-1.0, 1.0], e=NOT_YOUNG, h=DEEP_DARK_H, d=DEEP_D),
]
all_boxes = surface_boxes + layer_boxes + cave_boxes

def _overlapping(a, b):
    for k in ("temperature", "humidity", "continentalness", "erosion", "weirdness", "depth"):
        lo = max(a["parameters"][k][0], b["parameters"][k][0])
        hi = min(a["parameters"][k][1], b["parameters"][k][1])
        if hi - lo <= 1e-9:
            return False
    return True

for i, a in enumerate(all_boxes):
    for b in all_boxes[i + 1:]:
        if _overlapping(a, b):
            raise SystemExit("biome boxes overlap: %s %s / %s %s" % (a["biome"], a["parameters"], b["biome"], b["parameters"]))
preset = copy.deepcopy(van_preset)
preset["dimensions"]["minecraft:overworld"]["generator"]["biome_source"] = {"type": "minecraft:multi_noise", "biomes": all_boxes}
write("data/minecraft/worldgen/world_preset/normal.json", preset)

DEEPSLATE = {"deepslate_ore_replaceables"}

def ore_target(state, tag):
    return {"state": "minecraft:" + state,
            "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:" + tag}}

def ore_feature(name, stone_state, deep_state, size, discard=0.0):
    targets = [ore_target(stone_state, "stone_ore_replaceables"), ore_target(deep_state, "deepslate_ore_replaceables"),
               {"state": "minecraft:" + deep_state,
                "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:basalt"}}]
    write("data/oceania/worldgen/feature/%s.json" % name,
          {"type": "minecraft:ore", "discard_chance_on_air_exposure": discard, "size": size, "targets": targets})

def rock_feature(name, state, size):
    write("data/oceania/worldgen/feature/%s.json" % name,
          {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": size,
           "targets": [ore_target(state, "base_stone_overworld")]})

def placed_ore(name, feature, amount, lo, hi, shape="uniform"):
    how = {"type": "minecraft:rarity_filter", "chance": -amount} if amount < 0 else {"type": "minecraft:count", "count": amount}
    write("data/oceania/worldgen/placed_feature/%s.json" % name, {"feature": "oceania:" + feature, "placement": [
        how,
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:" + shape,
                                                      "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
        {"type": "oceania:column_biome"},
    ]})

ore_feature("ore_coal", "coal_ore", "deepslate_coal_ore", 22)
ore_feature("ore_copper", "copper_ore", "deepslate_copper_ore", 14)
ore_feature("ore_iron", "iron_ore", "deepslate_iron_ore", 13)
ore_feature("ore_iron_small", "iron_ore", "deepslate_iron_ore", 6)
ore_feature("ore_gold", "gold_ore", "deepslate_gold_ore", 12)
ore_feature("ore_redstone", "redstone_ore", "deepslate_redstone_ore", 11)
ore_feature("ore_lapis", "lapis_ore", "deepslate_lapis_ore", 9)
ore_feature("ore_diamond", "diamond_ore", "deepslate_diamond_ore", 6, 0.5)
ore_feature("ore_diamond_buried", "diamond_ore", "deepslate_diamond_ore", 11, 1.0)
ore_feature("ore_emerald", "emerald_ore", "deepslate_emerald_ore", 4)
rock_feature("ore_granite", "granite", 64)
rock_feature("ore_diorite", "diorite", 64)
rock_feature("ore_andesite", "andesite", 64)
rock_feature("ore_tuff", "tuff", 64)
rock_feature("ore_dirt", "dirt", 33)
rock_feature("ore_gravel", "gravel", 33)

ORES = [
    ("ore_iron_island",    "ore_iron",            16,  -50,  150, "trapezoid", "island"),
    ("ore_redstone_rock",  "ore_redstone",        10,  -80,  100, "trapezoid", "flank"),
    ("ore_diamond",        "ore_diamond",         21, -600,   16, "uniform",   "all"),
    ("ore_diamond_buried", "ore_diamond_buried",   6, -600,   16, "uniform",   "all"),
]
for name, feature, amount, lo, hi, shape, where in ORES:
    placed_ore(name, feature, amount, lo, hi, shape)

def depth_ore(name, feature, amount, lo, hi):
    how = {"type": "minecraft:rarity_filter", "chance": -amount} if amount < 0 else {"type": "minecraft:count", "count": amount}
    write("data/oceania/worldgen/placed_feature/%s.json" % name, {"feature": "oceania:" + feature, "placement": [
        how,
        {"type": "minecraft:in_square"},
        {"type": "oceania:floor_depth", "depth": {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}},
        {"type": "oceania:column_biome"},
    ]})

DEPTH_ORES = [
    ("ore_coal",           "ore_coal",           12,  0,               150,           "all"),
    ("ore_copper",         "ore_copper",         50,  0,               188,           "all"),
    ("ore_iron_mid",       "ore_iron",           16,  19,              206,           "all"),
    ("ore_iron_small",     "ore_iron_small",     15,  0,               CAVE_DEEP_OUT, "all"),
    ("ore_gold",           "ore_gold",            7,  75,              CAVE_DEEP_OUT, "all"),
    ("ore_redstone",       "ore_redstone",        7,  115,             CAVE_DEEP_OUT, "all"),
    ("ore_redstone_deep",  "ore_redstone",        7,  225,             CAVE_DEEP_OUT, "all"),
    ("ore_lapis",          "ore_lapis",           4,  75,              225,           "all"),
    ("ore_lapis_buried",   "ore_lapis",           7,  0,               CAVE_DEEP_OUT, "all"),
    ("ore_granite",        "ore_granite",         5,  9,               150,           "all"),
    ("ore_diorite",        "ore_diorite",         5,  9,               150,           "all"),
    ("ore_andesite",       "ore_andesite",        5,  9,               150,           "all"),
    ("ore_tuff",           "ore_tuff",            5,  150,             CAVE_DEEP_OUT, "all"),
    ("ore_gravel",         "ore_gravel",         11,  0,               CAVE_DEEP_OUT, "all"),
    ("ore_emerald",        "ore_emerald",        28,  0,               188,           "island"),
    ("ore_diamond_deep",   "ore_diamond",         24, 64,              CAVE_DEEP_OUT, "deep"),
]
for name, feature, amount, lo, hi, where in DEPTH_ORES:
    depth_ore(name, feature, amount, lo, hi)

ISLANDS = ("island_jungle", "island_sand", "island_cliff", "island_barren")
FLANKS = ("island_barren", "barren_flank", "seamount_flank", "island_cliff", "rocky_floor")
DEEPS = ("trench", "abyssal_plain", "ridge")

def heightmap_placed(name, feature, amount, extra=()):
    how = {"type": "minecraft:rarity_filter", "chance": -amount} if amount < 0 else {"type": "minecraft:count", "count": amount}
    write("data/oceania/worldgen/placed_feature/%s.json" % name, {"feature": feature, "placement": [
        how,
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
        {"type": "minecraft:block_predicate_filter",
         "predicate": {"type": "minecraft:matching_fluids", "fluids": "minecraft:water"}},
        *extra,
        {"type": "minecraft:biome"},
    ]})

write("data/oceania/worldgen/feature/vent.json", {"type": "oceania:vent"})
heightmap_placed("vent", "oceania:vent", -2)

NODULES = [("iron", "raw_iron_block", 7), ("copper", "raw_copper_block", 6),
           ("gold", "raw_gold_block", 420), ("netherite", "ancient_debris", 250)]
for metal, block, rarity in NODULES:
    write("data/oceania/worldgen/feature/nodule_%s.json" % metal,
          {"type": "minecraft:simple_block", "to_place": {"id": "minecraft:" + block}})
    heightmap_placed("nodule_%s" % metal, "oceania:nodule_%s" % metal, -rarity)

FLOOR_BLOCKS = ["minecraft:gravel", "minecraft:stone", "minecraft:deepslate", "minecraft:sand", "minecraft:tuff"]
for name, block, radius, half, count in [("sand", "sand", (2, 6), 1, 3), ("clay", "clay", (2, 4), 1, 2),
                                         ("gravel", "gravel", (3, 7), 2, 3)]:
    write("data/oceania/worldgen/feature/disk_%s.json" % name, {
        "type": "minecraft:disk",
        "half_height": half,
        "radius": {"type": "minecraft:uniform", "min_inclusive": radius[0], "max_inclusive": radius[1]},
        "state_provider": {"type": "minecraft:rule_based", "fallback": {"id": "minecraft:" + block}, "rules": []},
        "target": {"type": "minecraft:matching_blocks", "blocks": FLOOR_BLOCKS},
    })
    heightmap_placed("disk_%s" % name, "oceania:disk_%s" % name, count)

write("data/oceania/worldgen/feature/tall_kelp.json", {"type": "oceania:tall_kelp"})
heightmap_placed("kelp_forest", "oceania:tall_kelp", 28)
heightmap_placed("kelp_sparse", "oceania:tall_kelp", 6)
heightmap_placed("kelp_deep", "oceania:tall_kelp", 2)

write("data/oceania/worldgen/feature/atoll_cactus.json", {"type": "oceania:atoll_cactus"})
write("data/oceania/worldgen/placed_feature/atoll_cactus.json", {"feature": "oceania:atoll_cactus", "placement": [
    {"type": "minecraft:rarity_filter", "chance": 10},
    {"type": "minecraft:in_square"},
    {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
    {"type": "minecraft:biome"},
]})

FLOOR_ORDER = ["oceania:vent", "oceania:disk_sand", "oceania:disk_clay", "oceania:disk_gravel",
               "oceania:nodule_iron", "oceania:nodule_copper", "oceania:nodule_gold", "oceania:nodule_netherite"]
SEDIMENT = ["oceania:disk_sand", "oceania:disk_clay", "oceania:disk_gravel"]
COMMON_NODULES = ["oceania:nodule_iron", "oceania:nodule_copper"]
RARE_NODULES = ["oceania:nodule_gold", "oceania:nodule_netherite"]
FLOOR = {
    "abyssal_plain": SEDIMENT + COMMON_NODULES + RARE_NODULES,
    "trench": ["oceania:vent"] + SEDIMENT + COMMON_NODULES + RARE_NODULES,
    "ridge": ["oceania:vent"] + SEDIMENT + COMMON_NODULES,
    "seamount_flank": SEDIMENT + COMMON_NODULES,
    "rocky_floor": SEDIMENT,
    "kelp_forest": SEDIMENT,
    "sandy_shelf": ["oceania:disk_clay", "oceania:disk_gravel"],
    "barren_flank": ["oceania:disk_gravel"],
}

def floor_for(biome):
    want = set(FLOOR.get(biome, ()))
    return [f for f in FLOOR_ORDER if f in want]

def floor_placed(name, feature, how, depth, extra=()):
    if isinstance(how, dict):
        first = how
    elif how < 0:
        first = {"type": "minecraft:rarity_filter", "chance": -how}
    else:
        first = {"type": "minecraft:count", "count": how}
    write("data/oceania/worldgen/placed_feature/%s.json" % name, {"feature": feature, "placement": [
        first,
        {"type": "minecraft:in_square"},
        {"type": "oceania:floor_depth", "depth": {"type": "minecraft:uniform", "min_inclusive": depth[0], "max_inclusive": depth[1]}},
        *extra,
        {"type": "minecraft:biome"},
    ]})

def ucount(lo, hi):
    return {"type": "minecraft:count", "count": {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}}

MID_BAND = (CAVE_TOP, CAVE_MID_BOTTOM)
DEEP_BAND = (CAVE_MID_BOTTOM, CAVE_DEEP_OUT)
WHOLE_BAND = (CAVE_TOP, CAVE_DEEP_OUT)
scan_down = {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 12,
             "target_condition": {"type": "minecraft:solid"},
             "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}}
scan_up = dict(scan_down, direction_of_search="up")
scan_up_sturdy = dict(scan_up, target_condition={"type": "minecraft:has_sturdy_face", "direction": "down"})
up_one = {"type": "minecraft:offset", "x": 0, "y": 1, "z": 0}
down_one = {"type": "minecraft:offset", "x": 0, "y": -1, "z": 0}
lake_scan = {"type": "minecraft:environment_scan", "direction_of_search": "down", "max_steps": 32,
             "target_condition": {"type": "minecraft:all_of", "predicates": [
                 {"type": "minecraft:not", "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}},
                 {"type": "minecraft:inside_world_bounds", "offset": [0, -5, 0]}]}}

floor_placed("amethyst_geode", "minecraft:amethyst_geode", -24, (CAVE_TOP, 140))
floor_placed("monster_room", "minecraft:monster_room", 12, WHOLE_BAND)
floor_placed("lake_lava_underground", "minecraft:lake_lava", -9, (72, CAVE_DEEP_OUT), extra=[lake_scan])
floor_placed("fossil_upper", "minecraft:fossil_coal", -64, MID_BAND)
floor_placed("fossil_lower", "minecraft:fossil_diamonds", -64, DEEP_BAND)
floor_placed("glow_lichen", "minecraft:glow_lichen", ucount(60, 90), (CAVE_TOP // 2, CAVE_DEEP_OUT))
floor_placed("ore_dirt", "oceania:ore_dirt", 6, (0, CAVE_MID_BOTTOM))
floor_placed("ore_clay", "minecraft:ore_clay", 14, MID_BAND)
write("data/oceania/worldgen/feature/underwater_magma.json",
      {"type": "oceania:underwater_magma", "floor_search_range": 5,
       "placement_radius_around_floor": 1, "placement_probability_per_valid_position": 0.5})
floor_placed("underwater_magma", "oceania:underwater_magma", 40, WHOLE_BAND)
floor_placed("large_dripstone", "minecraft:large_dripstone", ucount(10, 40), MID_BAND)
floor_placed("dripstone_cluster", "minecraft:dripstone_cluster", ucount(40, 80), MID_BAND)
floor_placed("pointed_dripstone", "minecraft:pointed_dripstone", ucount(150, 220), MID_BAND, extra=[
    ucount(1, 5),
    {"type": "minecraft:offset",
     "x": {"type": "minecraft:clamped_normal", "mean": 0.0, "deviation": 3.0, "min_inclusive": -10, "max_inclusive": 10},
     "y": {"type": "minecraft:clamped_normal", "mean": 0.0, "deviation": 0.6, "min_inclusive": -2, "max_inclusive": 2},
     "z": {"type": "minecraft:clamped_normal", "mean": 0.0, "deviation": 3.0, "min_inclusive": -10, "max_inclusive": 10}}])
floor_placed("sculk_vein", "minecraft:sculk_vein", ucount(120, 160), DEEP_BAND)
floor_placed("sculk_patch_deep_dark", "minecraft:sculk_patch_deep_dark", 140, DEEP_BAND)
floor_placed("lush_caves_ceiling_vegetation", "minecraft:moss_patch_ceiling", 40, MID_BAND, extra=[scan_up, down_one])
floor_placed("lush_caves_vegetation", "minecraft:moss_patch", 40, MID_BAND, extra=[scan_down, up_one])
floor_placed("lush_caves_clay", "minecraft:lush_caves_clay", 20, MID_BAND, extra=[scan_down, up_one])
floor_placed("cave_vines", "minecraft:cave_vine", 60, MID_BAND, extra=[scan_up_sturdy, down_one])
floor_placed("spore_blossom", "minecraft:spore_blossom", 8, MID_BAND, extra=[scan_up, down_one])
floor_placed("rooted_azalea_tree", "minecraft:rooted_azalea_tree", -2, MID_BAND, extra=[scan_up, down_one])
floor_placed("classic_vines_cave_feature", "minecraft:vines", 80, MID_BAND)
floor_placed("sulfur_spike_cluster", "minecraft:sulfur_spike_cluster", ucount(40, 80), MID_BAND)
floor_placed("sulfur_spike", "minecraft:sulfur_spike", ucount(150, 220), MID_BAND, extra=[
    ucount(1, 5),
    {"type": "minecraft:offset",
     "x": {"type": "minecraft:clamped_normal", "mean": 0.0, "deviation": 3.0, "min_inclusive": -10, "max_inclusive": 10},
     "y": {"type": "minecraft:clamped_normal", "mean": 0.0, "deviation": 0.6, "min_inclusive": -2, "max_inclusive": 2},
     "z": {"type": "minecraft:clamped_normal", "mean": 0.0, "deviation": 3.0, "min_inclusive": -10, "max_inclusive": 10}}])
floor_placed("sulfur_pool", "minecraft:sulfur_pool", 140, MID_BAND, extra=[
    {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:solid"}},
    {"type": "minecraft:environment_scan", "direction_of_search": "up", "max_steps": 32,
     "target_condition": {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"}},
    down_one,
    {"type": "minecraft:block_predicate_filter",
     "predicate": {"type": "minecraft:matching_blocks", "blocks": "minecraft:sulfur"}}])
floor_placed("rooted_sulfur_spring", "minecraft:rooted_sulfur_spring", ucount(1, 2), MID_BAND, extra=[scan_up, down_one])
floor_placed("monster_room_deep", "minecraft:monster_room", 5, DEEP_BAND)
write("data/oceania/worldgen/feature/cave_pool.json", {"type": "oceania:cave_pool"})
floor_placed("cave_pool", "oceania:cave_pool", 12, MID_BAND)

CAVE_COMMON = {
    1: ["oceania:lake_lava_underground"],
    2: ["oceania:amethyst_geode", "oceania:fossil_upper", "oceania:fossil_lower"],
    3: ["oceania:monster_room", "oceania:monster_room_deep"],
    6: ["oceania:ore_dirt", "oceania:underwater_magma"],
    9: ["oceania:glow_lichen"],
}
CAVE_EXTRA = {
    "dripstone_caves": {2: ["oceania:large_dripstone"], 7: ["oceania:dripstone_cluster", "oceania:pointed_dripstone"]},
    "lush_caves": {1: ["oceania:cave_pool"],
                   6: ["oceania:ore_clay"],
                   9: ["oceania:lush_caves_ceiling_vegetation", "oceania:cave_vines", "oceania:lush_caves_clay",
                       "oceania:lush_caves_vegetation", "oceania:rooted_azalea_tree", "oceania:spore_blossom",
                       "oceania:classic_vines_cave_feature"]},
    "sulfur_caves": {1: ["oceania:sulfur_pool", "oceania:rooted_sulfur_spring"],
                     7: ["oceania:sulfur_spike_cluster", "oceania:sulfur_spike"]},
    "deep_dark": {7: ["oceania:sculk_vein", "oceania:sculk_patch_deep_dark"]},
}
LUSH_VEG = ["oceania:glow_lichen", "oceania:lush_caves_ceiling_vegetation", "oceania:cave_vines", "oceania:lush_caves_clay",
            "oceania:lush_caves_vegetation", "oceania:rooted_azalea_tree", "oceania:spore_blossom",
            "oceania:classic_vines_cave_feature"]

SHARKS = {
    "reef":             0.00375,
    "lagoon":           0.0075,
    "sandy_shelf":      0.0075,
    "kelp_forest":      0.0054,
    "rocky_floor":      0.0054,
    "barren_flank":     0.0054,
    "seamount_flank":   0.010,
    "abyssal_plain":    0.010,
    "ridge":            0.010,
    "trench":           0.010,
}
DOLPHINS = ("reef", "lagoon", "sandy_shelf", "kelp_forest", "rocky_floor",
            "seamount_flank", "abyssal_plain", "ridge")

SQUID = ("reef", "lagoon", "sandy_shelf", "kelp_forest", "rocky_floor", "barren_flank",
         "seamount_flank", "abyssal_plain", "ridge")
GLOW_SQUID = ("seamount_flank", "abyssal_plain", "ridge", "trench")
TROPICAL_FISH = ("reef", "lagoon", "sandy_shelf")
PUFFERFISH = ("reef", "lagoon")
COD = ("sandy_shelf", "kelp_forest", "rocky_floor", "barren_flank", "seamount_flank",
       "abyssal_plain", "ridge")
SALMON = ("kelp_forest", "rocky_floor")
TURTLES = ("island_sand",)

FARM_ANIMALS = ("island_jungle",)
FARM_ANIMAL_TABLE = (
    ("minecraft:sheep", 12, 4, 4),
    ("minecraft:pig", 10, 4, 4),
    ("minecraft:chicken", 20, 4, 4),
    ("minecraft:cow", 8, 4, 4),
)

JUNGLE_CREATURES = ("island_jungle",)
JUNGLE_CREATURE_TABLE = (
    ("minecraft:parrot", 40, 1, 2),
    ("minecraft:panda", 1, 1, 2),
)

DROWNED = ("abyssal_plain",)
DROWNED_BUDGET = 0.02

CAVE_DROWNED = ("dripstone_caves",)

LAND_MONSTERS = ("island_jungle", "island_sand", "island_cliff", "island_barren")
LAND_MONSTER_TABLE = (
    ("minecraft:spider", 100, 4, 4),
    ("minecraft:zombie", 95, 4, 4),
    ("minecraft:zombie_villager", 5, 1, 1),
    ("minecraft:skeleton", 100, 4, 4),
    ("minecraft:creeper", 100, 4, 4),
    ("minecraft:slime", 100, 4, 4),
    ("minecraft:enderman", 10, 1, 4),
    ("minecraft:witch", 5, 1, 1),
)

JUNGLE_MONSTER_TABLE = (
    ("minecraft:ocelot", 2, 1, 3),
)

SULFUR_MONSTER_TABLE = (
    ("minecraft:sulfur_cube", 100, 2, 4),
    ("minecraft:creeper", 50, 2, 2),
    ("minecraft:skeleton", 50, 2, 2),
    ("minecraft:slime", 25, 1, 1),
    ("minecraft:cave_spider", 20, 1, 1),
    ("minecraft:zombie", 50, 2, 2),
    ("minecraft:enderman", 10, 1, 1),
    ("minecraft:witch", 1, 1, 1),
    ("minecraft:zombie_villager", 5, 1, 1),
)

SHARK_WEIGHT, SHARK_COUNT = 1, 1
SHARK_CHARGE = 1.0

CAVE_MONSTERS = ("dripstone_caves", "lush_caves")
BATS = ("dripstone_caves", "lush_caves", "sulfur_caves",
        "island_jungle", "island_sand", "island_cliff", "island_barren")
SULFUR_CUBES = ("sulfur_caves",)
AXOLOTLS = ("lush_caves",)
CAVE_GLOW_SQUID = ("dripstone_caves", "lush_caves")
CAVE_TROPICAL_FISH = ("lush_caves",)

def _spawn(kind, weight, lo, hi):
    return {"type": kind, "weight": weight, "minCount": lo, "maxCount": hi}

def spawners_for(biome):
    out = {}
    if MOD_MOBS and biome in SHARKS:
        out["monster"] = [{"type": "oceania:shark", "weight": SHARK_WEIGHT,
                           "minCount": SHARK_COUNT, "maxCount": SHARK_COUNT}]
    if biome in DROWNED:
        out.setdefault("monster", []).append(
            {"type": "minecraft:drowned", "weight": 1, "minCount": 1, "maxCount": 2})
    if biome in CAVE_DROWNED:
        out.setdefault("monster", []).append(
            {"type": "minecraft:drowned", "weight": 95, "minCount": 4, "maxCount": 4})
    if biome in LAND_MONSTERS or biome in CAVE_MONSTERS:
        for kind, weight, lo, hi in LAND_MONSTER_TABLE:
            out.setdefault("monster", []).append(
                {"type": kind, "weight": weight, "minCount": lo, "maxCount": hi})
    if biome in JUNGLE_CREATURES:
        for kind, weight, lo, hi in JUNGLE_MONSTER_TABLE:
            out.setdefault("monster", []).append(
                {"type": kind, "weight": weight, "minCount": lo, "maxCount": hi})
    if biome in SULFUR_CUBES:
        for kind, weight, lo, hi in SULFUR_MONSTER_TABLE:
            out.setdefault("monster", []).append(
                {"type": kind, "weight": weight, "minCount": lo, "maxCount": hi})

    water_creature = []
    if biome in DOLPHINS:
        water_creature.append(_spawn("minecraft:dolphin", 2, 1, 2))
    if biome in SQUID:
        water_creature.append(_spawn("minecraft:squid", 1, 1, 4))
    if water_creature:
        out["water_creature"] = water_creature

    if biome in GLOW_SQUID or biome in CAVE_GLOW_SQUID:
        out["underground_water_creature"] = [_spawn("minecraft:glow_squid", 10, 4, 6)]
    if biome in BATS:
        out["ambient"] = [_spawn("minecraft:bat", 10, 8, 8)]
    if biome in AXOLOTLS:
        out["axolotls"] = [_spawn("minecraft:axolotl", 10, 4, 6)]

    water_ambient = []
    if biome in COD:
        water_ambient.append(_spawn("minecraft:cod", 10, 3, 6))
    if biome in SALMON:
        water_ambient.append(_spawn("minecraft:salmon", 5, 1, 5))
    if biome in TROPICAL_FISH or biome in CAVE_TROPICAL_FISH:
        water_ambient.append(_spawn("minecraft:tropical_fish", 25, 8, 8))
    if biome in PUFFERFISH:
        water_ambient.append(_spawn("minecraft:pufferfish", 15, 1, 3))
    if water_ambient:
        out["water_ambient"] = water_ambient

    creature = []
    if biome in TURTLES:
        creature.append(_spawn("minecraft:turtle", 5, 2, 5))
    if biome in FARM_ANIMALS:
        for kind, weight, lo, hi in FARM_ANIMAL_TABLE:
            creature.append(_spawn(kind, weight, lo, hi))
    if biome in JUNGLE_CREATURES:
        for kind, weight, lo, hi in JUNGLE_CREATURE_TABLE:
            creature.append(_spawn(kind, weight, lo, hi))
    if creature:
        out["creature"] = creature

    return out

SHARK_CHARGE_ONLY = {
    "island_jungle":  0.0054,
    "island_sand":    0.0054,
    "island_cliff":   0.0054,
    "island_barren":  0.0054,
}

def spawn_costs_for(biome):
    out = {}
    if MOD_MOBS and biome in SHARKS:
        out["oceania:shark"] = {"charge": SHARK_CHARGE, "energy_budget": SHARKS[biome]}
    elif MOD_MOBS and biome in SHARK_CHARGE_ONLY:
        out["oceania:shark"] = {"charge": SHARK_CHARGE,
                                "energy_budget": SHARK_CHARGE_ONLY[biome]}
    if biome in DROWNED or biome in CAVE_DROWNED:
        out["minecraft:drowned"] = {"charge": 1.0, "energy_budget": DROWNED_BUDGET}
    return out

JUNGLE_BUSH = json.load(open(f"{VAN}/data/minecraft/worldgen/feature/jungle_bush.json"))
assert JUNGLE_BUSH["foliage_provider"]["id"] == "minecraft:oak_leaves", JUNGLE_BUSH["foliage_provider"]
JUNGLE_BUSH["foliage_provider"]["id"] = "minecraft:jungle_leaves"
write("data/oceania/worldgen/feature/jungle_bush.json", JUNGLE_BUSH)
write("data/oceania/worldgen/placed_feature/jungle_bush.json", {"feature": "oceania:jungle_bush", "placement": [
    {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": "minecraft:jungle_sapling"}}]})

def _tree_placement(lo, hi):
    return [
        {"type": "minecraft:count", "count": {"type": "minecraft:weighted_list", "distribution": [
            {"data": lo, "weight": 9}, {"data": hi, "weight": 1}]}},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"},
    ]

write("data/oceania/worldgen/feature/trees_jungle.json", {
    "type": "minecraft:random_selector",
    "default": "minecraft:jungle_tree",
    "features": [
        {"chance": 0.5, "feature": "oceania:jungle_bush"},
        {"chance": 0.33333334, "feature": "minecraft:mega_jungle_tree_checked"},
    ],
})
write("data/oceania/worldgen/placed_feature/trees_jungle.json",
      {"feature": "oceania:trees_jungle", "placement": _tree_placement(50, 51)})

write("data/oceania/worldgen/feature/trees_sparse_jungle.json", {
    "type": "minecraft:random_selector",
    "default": "minecraft:jungle_tree",
    "features": [
        {"chance": 0.5, "feature": "oceania:jungle_bush"},
    ],
})
write("data/oceania/worldgen/placed_feature/trees_sparse_jungle.json",
      {"feature": "oceania:trees_sparse_jungle", "placement": _tree_placement(2, 3)})

VEG_ORDER = [
    "oceania:trees_jungle", "oceania:trees_sparse_jungle", "minecraft:bamboo_light",
    "minecraft:flower_warm", "minecraft:patch_grass_jungle", "minecraft:patch_grass_normal",
    "minecraft:patch_dead_bush", "minecraft:patch_sugar_cane", "oceania:atoll_cactus",
    "minecraft:warm_ocean_vegetation",
    "minecraft:seagrass_warm", "minecraft:seagrass_normal", "minecraft:seagrass_deep",
    "oceania:kelp_forest", "oceania:kelp_sparse", "oceania:kelp_deep", "minecraft:sea_pickle",
] + LUSH_VEG

STEP_ORDER = {
    1: ["oceania:lake_lava_underground", "oceania:cave_pool", "oceania:sulfur_pool", "oceania:rooted_sulfur_spring"],
    2: ["oceania:amethyst_geode", "oceania:large_dripstone", "oceania:fossil_upper", "oceania:fossil_lower"],
    3: ["oceania:monster_room", "oceania:monster_room_deep"],
    6: ["oceania:ore_dirt", "oceania:ore_clay", "oceania:underwater_magma"],
    7: FLOOR_ORDER + ["oceania:dripstone_cluster", "oceania:pointed_dripstone",
                      "oceania:sulfur_spike_cluster", "oceania:sulfur_spike",
                      "oceania:sculk_vein", "oceania:sculk_patch_deep_dark"],
    9: VEG_ORDER,
}

def step_features(biome, step):
    want = set(CAVE_COMMON.get(step, ())) | set(CAVE_EXTRA.get(biome, {}).get(step, ()))
    if step == 7:
        want |= set(FLOOR.get(biome, ()))
    if step == 9:
        want |= set(VEGETATION.get(biome, ()))
    unknown = want - set(STEP_ORDER[step])
    if unknown:
        raise SystemExit("step %d feature not in STEP_ORDER: %s" % (step, sorted(unknown)))
    return [f for f in STEP_ORDER[step] if f in want]

def features_for(biome):
    return [[], step_features(biome, 1), step_features(biome, 2), step_features(biome, 3), [], [],
            ores_for(biome) + step_features(biome, 6), step_features(biome, 7), [],
            step_features(biome, 9), []]

VEGETATION = {
    "island_jungle": ["oceania:trees_jungle", "minecraft:patch_grass_jungle", "minecraft:flower_warm",
                      "minecraft:bamboo_light", "minecraft:patch_sugar_cane"],
    "island_sand": ["oceania:trees_sparse_jungle", "minecraft:patch_grass_normal", "minecraft:patch_sugar_cane",
                    "minecraft:patch_dead_bush", "oceania:atoll_cactus"],
    "island_cliff": ["minecraft:patch_grass_normal", "minecraft:patch_dead_bush"],
    "island_barren": [],
    "reef": ["minecraft:warm_ocean_vegetation", "minecraft:seagrass_warm", "minecraft:sea_pickle"],
    "lagoon": ["minecraft:seagrass_warm", "minecraft:sea_pickle"],
    "sandy_shelf": ["minecraft:seagrass_normal", "oceania:kelp_sparse"],
    "kelp_forest": ["minecraft:seagrass_normal", "oceania:kelp_forest"],
    "rocky_floor": ["minecraft:seagrass_normal", "oceania:kelp_sparse"],
    "seamount_flank": ["minecraft:seagrass_deep", "oceania:kelp_deep"],
    "barren_flank": [],
    "abyssal_plain": ["minecraft:seagrass_deep", "oceania:kelp_deep"],
    "ridge": [],
    "trench": [],
}

def vegetation_for(biome):
    want = set(VEGETATION.get(biome, ()))
    unknown = want - set(VEG_ORDER)
    if unknown:
        raise SystemExit("vegetation not in VEG_ORDER: %s" % sorted(unknown))
    return [f for f in VEG_ORDER if f in want]

def ore_scope(where, biome):
    return where == "all" or (where == "island" and biome in ISLANDS) \
        or (where == "flank" and biome in FLANKS) or (where == "deep" and biome in DEEPS)

def ores_for(biome):
    picked = ["oceania:" + e[0] for e in ORES if ore_scope(e[6], biome)]
    picked += ["oceania:" + e[0] for e in DEPTH_ORES if ore_scope(e[5], biome)]
    return picked

SHALLOW = 0x43d5ee
WATER = {
    "trench": (SHALLOW, 0x02060c), "abyssal_plain": (SHALLOW, 0x050a14), "ridge": (SHALLOW, 0x0a0a0a),
    "seamount_flank": (SHALLOW, 0x0b1a2e), "rocky_floor": (SHALLOW, 0x1b3a5c), "kelp_forest": (0x48c8c0, 0x1c4634),
    "reef": (SHALLOW, 0x041f33), "lagoon": (0x5de0f0, 0x0a2a3a), "sandy_shelf": (SHALLOW, 0x041f33),
    "island_sand": (SHALLOW, 0x041f33), "island_jungle": (SHALLOW, 0x041f33), "island_barren": (SHALLOW, 0x0b1a2e),
    "island_cliff": (SHALLOW, 0x041f33), "barren_flank": (SHALLOW, 0x0b1a2e),
}
ISLAND_BIOMES = ("island_jungle", "island_sand", "island_cliff", "island_barren")
ISLAND_BIOMES_CARVERS = ["oceania:island_cave", "oceania:island_cave_extra", "oceania:island_canyon"]
DEEP_CARVERS = ["oceania:deep_cave", "oceania:deep_cave_extra"]

def hex_colour(c):
    return "#%06x" % c

def spawns_attribute(biome):
    by_category = {}
    for category, entries in spawners_for(biome).items():
        by_category[category] = [
            {"type": e["type"], "weight": e["weight"],
             "count": e["minCount"] if e["minCount"] == e["maxCount"]
             else {"type": "minecraft:uniform", "min_inclusive": e["minCount"], "max_inclusive": e["maxCount"]}}
            for e in entries]
    return {"argument": {"spawn_costs": spawn_costs_for(biome), "spawns_by_category": by_category}, "modifier": "overlay"}

OCEAN_MUSIC = van_ocean["attributes"]["minecraft:audio/background_music"]
for name, (wc, wfc) in WATER.items():
    write(f"data/oceania/worldgen/biome/{name}.json", {
        "has_precipitation": True, "temperature": 0.95, "downfall": 0.9,
        "effects": {"water_color": hex_colour(wc)},
        "attributes": {
            "minecraft:visual/sky_color": hex_colour(7907327),
            "minecraft:visual/water_fog_color": hex_colour(wfc),
            "minecraft:audio/background_music": OCEAN_MUSIC,
            "minecraft:gameplay/natural_mob_spawns": spawns_attribute(name),
        },
        "carvers": ISLAND_BIOMES_CARVERS if name in ISLAND_BIOMES else DEEP_CARVERS,
        "features": features_for(name),
    })

CAVE_BIOMES = {
    "dripstone_caves": (0.8, 0.4, 7907327, 12638463, 4159204, 329011, "minecraft:music.overworld.dripstone_caves"),
    "lush_caves": (0.5, 0.5, 8103167, 12638463, 4159204, 329011, "minecraft:music.overworld.lush_caves"),
    "sulfur_caves": (0.8, 0.4, int("78a7ff", 16), int("8cb831", 16), int("34bf89", 16), int("17543c", 16),
                     "minecraft:music.overworld.sulfur_caves"),
    "deep_dark": (0.8, 0.4, 7907327, 12638463, 4159204, 329011, "minecraft:music.overworld.deep_dark"),
}
assert tuple(CAVE_BIOMES) == CAVE_BIOME_NAMES
for name, (temp, down, sky, fog, wc, wfc, music) in CAVE_BIOMES.items():
    write(f"data/oceania/worldgen/biome/{name}.json", {
        "has_precipitation": True, "temperature": temp, "downfall": down,
        "effects": {"water_color": hex_colour(wc)},
        "attributes": {
            "minecraft:visual/sky_color": hex_colour(sky),
            "minecraft:visual/fog_color": hex_colour(fog),
            "minecraft:visual/water_fog_color": hex_colour(wfc),
            "minecraft:audio/background_music": {"default": {"sound": music, "min_delay": 12000, "max_delay": 24000}},
            "minecraft:gameplay/natural_mob_spawns": spawns_attribute(name),
        },
        "carvers": ISLAND_BIOMES_CARVERS + DEEP_CARVERS,
        "features": features_for(name),
    })
write("data/minecraft/tags/worldgen/biome/allows_tropical_fish_spawns_at_any_height.json",
      {"replace": False, "values": ["oceania:lush_caves"]})

def floor_structure(name, floor_depth, min_y, biomes):
    d = json.load(open(f"{VAN}/data/minecraft/worldgen/structure/{name}.json"))
    d["type"] = "oceania:floor_jigsaw"
    d.pop("start_height", None)
    d.pop("project_start_to_heightmap", None)
    d["floor_depth"] = {"type": "minecraft:uniform", "min_inclusive": floor_depth[0], "max_inclusive": floor_depth[1]}
    d["min_y"] = min_y
    d["biomes"] = f"#minecraft:has_structure/{name}"
    write(f"data/minecraft/worldgen/structure/{name}.json", d)
    write(f"data/minecraft/tags/worldgen/biome/has_structure/{name}.json",
          {"replace": False, "values": [f"oceania:{b}" for b in biomes]})

NOT_YOUNG_BIOMES = [b for b in WATER if b not in ("island_barren", "barren_flank")]
ALL_BIOMES = list(WATER) + list(CAVE_BIOME_NAMES)
floor_structure("ancient_city", (CAVE_MID_BOTTOM + 4, CAVE_MID_BOTTOM + 24), -616, ["deep_dark"])
floor_structure("trial_chambers", (56, 110), -600, NOT_YOUNG_BIOMES + ["dripstone_caves", "lush_caves", "sulfur_caves"])

def structure_file(name, type_=None, **fields):
    d = json.load(open(f"{VAN}/data/minecraft/worldgen/structure/{name}.json"))
    if type_:
        d["type"] = type_
    d.update(fields)
    write(f"data/minecraft/worldgen/structure/{name}.json", d)

def biome_tag(name, biomes):
    write(f"data/minecraft/tags/worldgen/biome/{name}.json", {"replace": False, "values": [f"oceania:{b}" for b in biomes]})

def structure_set(name, structures=None, **placement):
    d = json.load(open(f"{VAN}/data/minecraft/worldgen/structure_set/{name}.json"))
    d["placement"].update(placement)
    if structures is not None:
        d["structures"] = [{"structure": f"minecraft:{s}", "weight": w} for s, w in structures]
    write(f"data/minecraft/worldgen/structure_set/{name}.json", d)

def uniform_int(lo, hi):
    return {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}

structure_file("stronghold", "oceania:floor_stronghold", floor_depth=uniform_int(16, 72), min_y=-620)
biome_tag("has_structure/stronghold", ALL_BIOMES)
biome_tag("stronghold_biased_to", ["island_jungle", "island_sand", "island_cliff", "dripstone_caves", "lush_caves"])
structure_file("mineshaft", "oceania:floor_mineshaft", floor_depth=uniform_int(24, 100), min_y=-640, max_floor_spread=120)
biome_tag("has_structure/mineshaft", ALL_BIOMES)
structure_set("mineshafts", structures=[("mineshaft", 1)])
structure_file("pillager_outpost", "oceania:island_jigsaw", exclude_island_types=["large"])
biome_tag("has_structure/pillager_outpost", ["island_jungle", "island_sand"])
d = json.load(open(f"{VAN}/data/minecraft/worldgen/structure_set/pillager_outposts.json"))
d["placement"].pop("exclusion_zone", None)
write("data/minecraft/worldgen/structure_set/pillager_outposts.json", d)
biome_tag("has_structure/trail_ruins", ["island_jungle"])
structure_set("ancient_cities", spacing=32, separation=10,
              exclusion_zone={"other_set": "minecraft:trial_chambers", "chunk_count": 8})
structure_set("trial_chambers", exclusion_zone={"other_set": "minecraft:ocean_monuments", "chunk_count": 5})

biome_tag("has_structure/shipwreck", ["reef", "lagoon", "sandy_shelf", "kelp_forest", "rocky_floor"])
biome_tag("has_structure/ocean_monument", ["abyssal_plain"])
write("data/minecraft/worldgen/structure_set/strongholds.json", {
    "placement": {"type": "minecraft:random_spread", "spread_type": "triangular", "spacing": 96, "separation": 48,
                  "salt": 165745296},
    "structures": [{"structure": "minecraft:stronghold", "weight": 1}]})
write("data/minecraft/worldgen/structure_set/ocean_monuments.json", {
    "structures": [{"structure": "minecraft:monument", "weight": 1}],
    "placement": {"type": "minecraft:random_spread", "spread_type": "triangular", "spacing": 14, "separation": 12,
                  "salt": 10387313}})
write("data/minecraft/worldgen/structure/jungle_pyramid.json", {
    "type": "oceania:island_temple", "biomes": "#minecraft:has_structure/jungle_temple", "spawn_overrides": {},
    "step": "surface_structures", "island_types": ["large"], "min_reach": 100.0, "search_radius": 64,
    "max_shift": 112, "island_chance": 1.0})
def uniform(lo, hi):
    return {"type": "minecraft:uniform", "min_inclusive": lo, "max_exclusive": hi}

def cave_carver(prob, y_lo, y_hi):
    return {"type": "minecraft:cave",
            "probability": prob,
            "y": {"type": "minecraft:uniform", "min_inclusive": {"absolute": y_lo}, "max_inclusive": {"absolute": y_hi}},
            "count": {"type": "minecraft:very_biased_to_bottom", "min_inclusive": 0, "max_inclusive": 14},
            "thickness": {"type": "minecraft:trapezoid", "min": 0.0, "max": 3.0, "plateau": 1.0},
            "weird_thickness_bias": True,
            "room_vertical_radius_multiplier": uniform(0.1, 0.9),
            "horizontal_radius_multiplier": uniform(0.7, 1.4),
            "vertical_radius_multiplier": uniform(0.8, 1.3),
            "floor_level": uniform(-1.0, -0.4)}

write("data/oceania/worldgen/carver/island_cave.json", cave_carver(0.28, -60, 170))
write("data/oceania/worldgen/carver/deep_cave.json", cave_carver(0.5, -560, 20))
write("data/oceania/worldgen/carver/island_cave_extra.json", cave_carver(0.28, -60, 170))
write("data/oceania/worldgen/carver/deep_cave_extra.json", cave_carver(0.5, -560, 20))
write("data/oceania/worldgen/carver/island_canyon.json", {"type": "minecraft:canyon",
    "probability": 0.02,
    "y": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 70}, "max_inclusive": {"absolute": 150}},
    "vertical_rotation": uniform(-0.125, 0.125),
    "shape": {
        "distance_factor": uniform(0.75, 1.0),
        "thickness": {"type": "minecraft:trapezoid", "min": 0.0, "max": 6.0, "plateau": 2.0},
        "width_smoothness": 3,
        "horizontal_radius_factor": uniform(0.75, 1.0),
        "vertical_radius_default_factor": 1.0,
        "vertical_radius_center_factor": 0.0,
        "y_scale": 3.0,
    },
})

def noise_params(first_octave, amplitudes):
    return {"base_octave": first_octave, "octave_count": len(amplitudes), "amplitude_modifiers": amplitudes, "normalize": "legacy"}

write("data/oceania/worldgen/noise/wall.json", noise_params(-5, [1.0, 0.4, 0.15]))
write("data/oceania/worldgen/noise/cave_select.json", noise_params(-9, [1.0, 0.6, 0.3]))
print("wrote noise_settings, dimension_type, world_preset, %d biomes (%d cave), 2 noises, %d density functions, %d biome boxes"
      % (len(WATER) + len(CAVE_BIOMES), len(CAVE_BIOMES), 5 + len(ROUTER), len(all_boxes)))
