MC = "minecraft:"

LEGEND = {
    ".": None, " ": None,
    "P": MC + "jungle_log[axis=y]",
    "Q": MC + "stripped_mangrove_log[axis=y]",
    "-": MC + "jungle_log[axis=x]",
    "|": MC + "jungle_log[axis=z]",
    "f": MC + "jungle_fence",
    "F": MC + "jungle_fence",
    "Y": MC + "jungle_trapdoor[facing=south,open=true,half=bottom]",
    "X": MC + "jungle_log[axis=x]",
    "Z": MC + "jungle_log[axis=z]",
    "#": MC + "mangrove_roots",
    "H": MC + "hay_block[axis=z]",
    "h": MC + "hay_block[axis=x]",
    "%": MC + "hay_block[axis=y]",
    "K": MC + "dried_kelp_block",
    "^": MC + "jungle_stairs[facing=north,half=bottom]",
    "v": MC + "jungle_stairs[facing=south,half=bottom]",
    "<": MC + "jungle_stairs[facing=west,half=bottom]",
    ">": MC + "jungle_stairs[facing=east,half=bottom]",
    "s": MC + "jungle_slab[type=bottom]",
    "S": MC + "jungle_slab[type=top]",
    "M": MC + "jungle_planks",
    "N": MC + "jungle_planks",
    "R": MC + "hanging_roots",
    "n": MC + "moss_carpet",
    "G": MC + "gravel",
    "m": MC + "gravel",
    "D": MC + "coarse_dirt",
    "d": MC + "jungle_planks",
    "_": MC + "jungle_slab[type=bottom]",
    "C": MC + "tuff",
    "c": MC + "tuff_slab[type=bottom]",
    "g": MC + "grass_block",
    "~": MC + "sand",
    "W": MC + "water",
    "U": MC + "tuff",
    "k": MC + "chest[facing=south]",
    "r": MC + "barrel[facing=up]",
    "p": MC + "decorated_pot",
    "o": MC + "campfire[lit=true]",
    "t": MC + "torch",
    "L": MC + "glow_lichen[up=true]",
    "*": MC + "cobweb",
    "=": MC + "jungle_fence",
    "e": MC + "sea_pickle[pickles=3,waterlogged=true]",
    "A": MC + "bell[attachment=ceiling]",
    "1": MC + "brown_carpet",
    "2": MC + "white_carpet",
    "3": MC + "light_gray_carpet",
    "4": MC + "yellow_carpet",
    "b": MC + "brown_bed[facing=north,part=foot]",
    "B": MC + "brown_bed[facing=north,part=head]",
    "a": MC + "composter",
    "O": MC + "loom[facing=south]",
    "u": MC + "smoker[facing=south]",
    "j": MC + "fletching_table",
    "q": MC + "lectern[facing=south]",
    "x": MC + "cartography_table",
    "+": MC + "crafting_table",
    "!": MC + "potted_fern",
    ";": MC + "sugar_cane",
    "'": MC + "fern",
    '"': MC + "short_grass",
    "i": MC + "bamboo[age=1,leaves=large,stage=0]",
    "I": MC + "bamboo[age=1,leaves=small,stage=0]",
    "@": MC + "ladder[facing=south]",
    "T": MC + "jungle_trapdoor[facing=north,open=true,half=bottom]",
    "w": MC + "cobweb",
}

def defaults_to_waterlogged(name):
    short = name.split(":")[-1]
    if short in ("sea_pickle", "conduit"):
        return True
    return "coral" in short and not short.endswith("_block")

def _check_waterlogged(name, props):
    if defaults_to_waterlogged(name) and "waterlogged" not in props:
        raise SystemExit(
            "%s defaults to waterlogged=true, so leaving it unsaid places WATER with it. "
            "Write waterlogged=false (or true, if that is what the drawing wants)." % name)

def parse_state(text):
    if "[" not in text:
        _check_waterlogged(text, {})
        return text, {}
    name, rest = text.split("[", 1)
    props = {}
    for kv in rest.rstrip("]").split(","):
        k, v = kv.split("=")
        props[k.strip()] = v.strip()
    _check_waterlogged(name, props)
    return name, props

def load_pieces():
    import importlib
    import os
    import sys

    here = os.path.dirname(os.path.abspath(__file__))
    if here not in sys.path:
        sys.path.insert(0, here)
    pieces = []
    for name in sorted(os.listdir(here)):
        if name.startswith("huts_") and name.endswith(".py"):
            pieces.extend(importlib.import_module(name[:-3]).HUTS)
    pieces.sort(key=lambda p: p["id"])
    return pieces
