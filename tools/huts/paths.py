LEGEND = {"X": "minecraft:gravel", "F": "minecraft:jungle_fence", "t": "minecraft:torch", ".": None}

TORCH_POST = dict(id="torch_post", layers=[(0, ["F"]), (1, ["F"]), (2, ["F"]), (3, ["t"])])

LINK = dict(id="path_link", layers=[(0, ["X"])])

PATHS = [
    dict(id="path_straight", pool="paths", weight=5,
         rows=["X", "X", "X", "X"],
         conns=[(0, 0, "north", "path"), (0, 3, "south", "path"),
                (0, 1, "west", "hut"), (0, 2, "east", "hut")]),

    dict(id="path_jog_east", pool="paths", weight=5,
         rows=["X.",
               "X.",
               "XX",
               ".X",
               ".X"],
         conns=[(0, 0, "north", "path"), (1, 4, "south", "path"),
                (0, 1, "west", "hut"), (1, 3, "east", "hut")]),

    dict(id="path_jog_west", pool="paths", weight=5,
         rows=[".X",
               ".X",
               "XX",
               "X.",
               "X."],
         conns=[(1, 0, "north", "path"), (0, 4, "south", "path"),
                (1, 1, "east", "hut"), (0, 3, "west", "hut")]),

    dict(id="path_wander", pool="paths", weight=4,
         rows=["X..",
               "X..",
               "XX.",
               ".X.",
               ".XX",
               "..X",
               "..X"],
         conns=[(0, 0, "north", "path"), (2, 6, "south", "path"),
                (0, 1, "west", "hut"), (2, 5, "east", "hut")]),

    dict(id="path_corner_east", pool="paths", weight=3,
         rows=["X...",
               "XXXX"],
         conns=[(0, 0, "north", "path"), (3, 1, "east", "path"), (2, 1, "south", "hut")]),

    dict(id="path_corner_west", pool="paths", weight=3,
         rows=["...X",
               "XXXX"],
         conns=[(3, 0, "north", "path"), (0, 1, "west", "path"), (1, 1, "south", "hut")]),

    dict(id="path_fork", pool="paths", weight=2,
         rows=["..X..",
               "XXXXX"],
         conns=[(2, 0, "north", "path"), (0, 1, "west", "path"), (4, 1, "east", "path")]),

    dict(id="path_end", pool="path_ends", weight=2,
         rows=["X", "X"],
         conns=[(0, 0, "north", "end")]),

    dict(id="path_end_hut", pool="path_ends", weight=3,
         rows=["X", "X"],
         conns=[(0, 0, "north", "end"), (0, 1, "south", "hut")]),
]
