import io
import re
import sys

VANILLA = {
    "coal": 2381, "iron": 2571, "copper": 2426, "gold": 873, "redstone": 1237, "lapis": 801,
    "diamond": 829, "granite": 29994, "diorite": 31614, "andesite": 32802, "tuff": 36674,
    "dirt": 18936, "gravel": 18836, "clay": 14089, "magma": 43, "amethyst": 557, "calcite": 668,
    "lava": 1040,
}
ORDER = ["coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald",
         "granite", "diorite", "andesite", "tuff", "dirt", "gravel", "clay", "magma",
         "amethyst", "calcite", "lava"]

def parse(path):
    text = io.open(path, encoding="utf-8", errors="replace").read()
    rock = None
    rows = {}
    for line in text.splitlines():
        s = line.strip()
        m = re.match(r"^rock/ch\s+(.*)$", s)
        if m and rock is None:
            rock = sum(float(v) for v in m.group(1).split())
            continue
        m = re.match(r"^(.+?)\s+.*total\s+([0-9.]+)/ch\s*$", s)
        if m:
            rows[m.group(1).strip()] = float(m.group(2))
    if rock is None:
        sys.exit("%s: no rock/ch row" % path)
    return rock, rows

places = [("trench 1988,-3008", sys.argv[1]), ("basalt cone 436,-429", sys.argv[2]),
          ("large island -1228,1071", sys.argv[3])]
data = {}
for label, path in places:
    rock, rows = parse(path)
    data[label] = (rock, rows)
    print("%-26s rock %8.0f /chunk" % (label, rock))
print()

head = "%-10s %9s | %s | %s" % ("ore", "vanilla", " ".join("%9s" % l.split()[0] for l, _ in places), "ours/vanilla")
print(head)
print("-" * len(head))
for ore in ORDER:
    van = VANILLA.get(ore)
    cells, ratios = [], []
    for label, _ in places:
        rock, rows = data[label]
        v = rows.get(ore)
        if v is None:
            cells.append("        .")
            continue
        ppm = 1e6 * v / rock
        cells.append("%9.0f" % ppm)
        if van:
            ratios.append(ppm / van)
    if van is None:
        tail = "no vanilla sample"
    elif ratios:
        lo, hi = min(ratios), max(ratios)
        tail = "%.2fx" % lo if abs(hi - lo) < 0.01 else "%.2f-%.2fx" % (lo, hi)
    else:
        tail = "-"
    print("%-10s %9s | %s | %s" % (ore, van if van else ".", " ".join(cells), tail))
