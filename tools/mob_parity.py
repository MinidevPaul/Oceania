import json
import os
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent
OURS = REPO / "src" / "main" / "resources" / "data" / "oceania" / "worldgen" / "biome"
VANILLA = REPO / "tools" / "vanilla-data-26.3" / "data" / "minecraft" / "worldgen" / "biome"
ATTR = "minecraft:gameplay/natural_mob_spawns"

ANALOGUE = {
    "island_jungle": "jungle",
    "island_sand": "beach",
    "island_cliff": "stony_shore",
    "island_barren": "desert",
    "dripstone_caves": "dripstone_caves",
    "lush_caves": "lush_caves",
    "deep_dark": "deep_dark",
    "sulfur_caves": "sulfur_caves",
}

DECLARED = {
    ("island_barren", "creature", "minecraft:rabbit"): "rabbits ruled OUT",
    ("island_barren", "creature", "minecraft:camel"): "no camels; desert analogue is for the GROUND only",
    ("island_barren", "monster", "minecraft:husk"): "desert-specific; island_barren is basalt, not sand",
    ("island_barren", "monster", "minecraft:parched"): "desert-specific; island_barren is basalt, not sand",
    ("island_barren", "monster", "minecraft:zombie", "weight"): "standard 95, not the desert's 19",
    ("island_barren", "monster", "minecraft:zombie_villager", "weight"): "standard 5, not the desert's 1",
    ("island_barren", "monster", "minecraft:skeleton", "weight"): "standard 100, not the desert's 50",

    ("island_jungle", "underground_water_creature", "minecraft:glow_squid"): "UNRULED: ocean and caves only",
    ("island_sand", "underground_water_creature", "minecraft:glow_squid"): "UNRULED: ocean and caves only",
    ("island_cliff", "underground_water_creature", "minecraft:glow_squid"): "UNRULED: ocean and caves only",
    ("island_barren", "underground_water_creature", "minecraft:glow_squid"): "UNRULED: ocean and caves only",
}

def count_text(count):
    if isinstance(count, dict):
        return "%d-%d" % (count["min_inclusive"], count["max_inclusive"])
    return str(count)

def table(path):
    doc = json.loads(path.read_text(encoding="utf-8"))
    attr = doc.get("attributes", {}).get(ATTR)
    if attr is None:
        return {}
    out = {}
    for category, entries in attr.get("argument", {}).get("spawns_by_category", {}).items():
        slot = out.setdefault(category, {})
        for e in entries:
            mob = e["type"]
            weight, count = e["weight"], count_text(e.get("count"))
            if mob in slot:
                slot[mob] = (slot[mob][0] + weight, count)
            else:
                slot[mob] = (weight, count)
    return out

def main():
    verbose = "--verbose" in sys.argv
    if not VANILLA.is_dir():
        sys.exit("no vanilla data at %s - run: python tools/fetch_vanilla_data.py" % VANILLA)

    problems, declared_hits = [], []
    for ours_name, van_name in ANALOGUE.items():
        mine = table(OURS / (ours_name + ".json"))
        theirs = table(VANILLA / (van_name + ".json"))
        print("=== oceania:%s vs minecraft:%s ===" % (ours_name, van_name))
        for category in sorted(set(mine) | set(theirs)):
            a, b = mine.get(category, {}), theirs.get(category, {})
            for mob in sorted(set(a) | set(b)):
                if mob in a and mob in b:
                    if a[mob] == b[mob]:
                        continue
                    kind = "weight" if a[mob][0] != b[mob][0] else "count"
                    line = "  %-27s %-24s ours %s%-9s vanilla %s" % (
                        category, mob.replace("minecraft:", ""),
                        "w%d " % a[mob][0], "c" + a[mob][1], "w%d c%s" % b[mob])
                    key = (ours_name, category, mob, kind)
                elif mob in b:
                    line = "  %-27s %-24s MISSING (vanilla w%d c%s)" % (
                        category, mob.replace("minecraft:", ""), b[mob][0], b[mob][1])
                    key = (ours_name, category, mob)
                else:
                    line = "  %-27s %-24s EXTRA (ours w%d c%s)" % (
                        category, mob.replace("minecraft:", ""), a[mob][0], a[mob][1])
                    key = (ours_name, category, mob)
                reason = DECLARED.get(key)
                if reason is None and len(key) == 4:
                    reason = DECLARED.get(key[:3])
                if reason is None:
                    problems.append(line)
                    print(line + "   <-- UNDECLARED")
                else:
                    declared_hits.append((key, reason))
                    if verbose:
                        print(line + "   (declared: %s)" % reason)
        if not verbose:
            print("  %d declared difference(s)" % sum(1 for k, _ in declared_hits if k[0] == ours_name))

    stale = [k for k in DECLARED if k not in [h[0] for h in declared_hits]]
    print()
    print("%d undeclared difference(s)" % len(problems))
    if stale:
        print("%d stale declaration(s) - the difference they excuse no longer exists:" % len(stale))
        for k in stale:
            print("  %s" % (k,))
    return 1 if problems or stale else 0

if __name__ == "__main__":
    sys.exit(main())
