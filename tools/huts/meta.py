POOLS = {
    "18": ("centers", 2), "19": ("centers", 2), "20": ("centers", 2),
    "01": ("huts", 7), "02": ("huts", 5), "03": ("huts", 5), "04": ("huts", 4),
    "05": ("huts", 2), "07": ("huts", 5), "09": ("huts", 2), "10": ("huts", 5),
    "13": ("huts", 2),
    "21": ("huts", 2), "22": ("huts", 2), "23": ("huts", 2),
    "25": ("huts", 1), "26": ("huts", 1), "29": ("huts", 1), "30": ("huts", 1),
}

SKIP = {"27"}

WALL_ROWS = {"16": 1, "17": 1}

WALL_REPLACES = ("minecraft:jungle_planks",)

HUTS_EMPTY_WEIGHT = 4

TUFF_STAIR = "minecraft:tuff_stairs[facing=%s,half=bottom]"
INWARD = {"north": "south", "south": "north", "east": "west", "west": "east"}

CONNECTORS = {
    "18": [(0, 0, 7, "west", "paths", TUFF_STAIR % "east"),
           (10, 0, 7, "east", "paths", TUFF_STAIR % "west"),
           (5, 0, 0, "north", "paths", TUFF_STAIR % "south"),
           (5, 0, 14, "south", "paths", TUFF_STAIR % "north")],
    "19": [(0, 0, 5, "west", "paths", TUFF_STAIR % "east"),
           (10, 0, 5, "east", "paths", TUFF_STAIR % "west"),
           (5, 0, 0, "north", "paths", TUFF_STAIR % "south"),
           (5, 0, 10, "south", "paths", TUFF_STAIR % "north")],
    "20": [(0, 0, 6, "west", "paths", TUFF_STAIR % "east"),
           (8, 0, 6, "east", "paths", TUFF_STAIR % "west"),
           (4, 0, 0, "north", "paths", TUFF_STAIR % "south"),
           (4, 0, 12, "south", "paths", TUFF_STAIR % "north")],
    "01": [(0, 0, 5, "west", "empty", None)],
    "02": [(0, 0, 6, "west", "empty", None)],
    "03": [(3, 0, 10, "south", "empty", None)],
    "04": [(4, 0, 8, "south", "empty", None)],
    "05": [(4, 0, 8, "south", "empty", None)],
    "07": [(0, 0, 5, "west", "empty", None)],
    "09": [(0, 0, 2, "west", "empty", None)],
    "10": [(0, 0, 5, "west", "empty", None)],
    "13": [(0, 0, 3, "west", "empty", None)],
    "16": [(3, 0, 0, "north", "empty", None)],
    "17": [(3, 0, 12, "south", "empty", None)],
    "21": [(2, 0, 6, "south", "empty", None)],
    "22": [(2, 0, 4, "south", "empty", "minecraft:jungle_stairs[facing=north,half=bottom]")],
    "23": [(0, 0, 3, "west", "empty", None)],
    "25": [(3, 0, 0, "north", "empty", None)],
    "26": [(3, 0, 0, "north", "empty", None)],
    "29": [(3, 0, 0, "north", "empty", None)],
    "30": [(3, 0, 0, "north", "empty", None)],
    "31": [(0, 0, 4, "west", "empty", None)],
}

VILLAGERS = {
    "01": [(3, -1, 5)],
    "02": [(4, -1, 4)],
    "03": [(3, -1, 5)],
    "04": [(4, -1, 4)],
    "05": [(4, -1, 5)],
    "07": [(3, -1, 5)],
    "10": [(3, -1, 5)],
    "18": [(4, 0, 6), (6, 0, 8)],
    "19": [(4, 0, 5), (6, 0, 5)],
    "20": [(3, 0, 6), (5, 0, 6)],
}

CATS = {
    "18": [(6, 0, 10)],
    "19": [(4, 0, 3)],
    "20": [(4, 0, 3)],
}

LOOM = "minecraft:loom[facing=south]"
LECTERN = "minecraft:lectern[facing=south]"
SMOKER = "minecraft:smoker[facing=south]"
CARTOGRAPHY = "minecraft:cartography_table"
BARREL_BLOCK = "minecraft:barrel[facing=up]"
BREWING = "minecraft:brewing_stand"
CAULDRON = "minecraft:cauldron"
FLETCHING = "minecraft:fletching_table"

JOB_SITES = {
    "05": {(6, 0, 3): LOOM},
    "09": {(3, 0, 3): FLETCHING},
    "18": {(7, 1, 7): BREWING},
    "19": {(7, 1, 3): LECTERN, (3, 1, 7): CARTOGRAPHY, (7, 1, 7): BREWING},
    "20": {(6, 1, 6): BREWING},
    "22": {(2, 0, 2): BARREL_BLOCK},
    "23": {(1, 0, 2): CAULDRON},
    "04": {(2, 0, 7): BREWING},
    "29": {(2, 0, 5): CAULDRON},
}

HALL = "oceania:chests/hut_village_hall"
HOUSE = "oceania:chests/hut_village_house"
STORE = "oceania:chests/hut_village_store"
DOCK = "oceania:chests/hut_village_dock"
BARREL = "oceania:chests/hut_village_barrel"

CHESTS = {
    "01": {(4, 0, 6): HOUSE},
    "02": {(5, 0, 7): HOUSE},
    "03": {(4, 0, 6): HOUSE},
    "04": {(4, 0, 1): HOUSE},
    "05": {(4, 0, 2): HOUSE},
    "07": {(4, 0, 6): HOUSE},
    "10": {(4, 0, 6): HOUSE},
    "18": {(3, 1, 3): HALL, (7, 1, 3): HOUSE},
    "20": {(2, 1, 2): HALL},
    "21": {(2, 3, 3): STORE},
    "22": {(2, 2, 2): STORE},
}

PATH_NAME = "oceania:path"
DOOR_NAME = "oceania:hut_door"
BOTTOM = "minecraft:bottom"

POOL_IDS = {
    "centers": "oceania:hut_village/centers",
    "paths": "oceania:hut_village/paths",
    "huts": "oceania:hut_village/huts",
    "villagers": "oceania:hut_village/villagers",
    "cats": "oceania:hut_village/cats",
    "empty": "minecraft:empty",
}

WALLS = {
    "16": (1, 1, 5, 9, -1, -1, None),
    "17": (1, 1, 5, 11, -1, -1, None),
    "01": (1, 1, 5, 7, 1, 5, None),
    "02": (1, 1, 7, 9, 1, 6, None),
    "03": (1, 1, 5, 7, 3, 7, "south"),
    "07": (1, 1, 5, 7, 1, 5, None),
    "10": (1, 1, 5, 7, 1, 5, None),
}

APRONS = {}

TRAPDOOR = "minecraft:jungle_trapdoor[facing=%s,open=true,half=bottom]"
OUTWARD = {"north": "south", "south": "north", "west": "east", "east": "west"}
APRON_STAIR = "minecraft:tuff_stairs[facing=%s,half=bottom]"
