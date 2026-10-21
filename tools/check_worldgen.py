import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, "src", "main", "resources", "data", "oceania", "worldgen")
VANILLA = os.path.join(ROOT, "tools", "vanilla-data-26.3", "data", "minecraft", "worldgen")

def names(directory, kind):
    return {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(directory, kind, "*.json"))}

def main():
    if not os.path.isdir(GEN):
        sys.exit("no generated data at %s - run tools/make_worldgen.py first" % GEN)
    have_vanilla = os.path.isdir(VANILLA)
    ours = {k: names(GEN, k) for k in ("feature", "placed_feature", "biome")}
    vanilla = {k: names(VANILLA, k) for k in ("feature", "placed_feature")} if have_vanilla else None

    def known(ref, kind):
        ns, _, name = ref.partition(":")
        if ns == "oceania":
            return name in ours[kind]
        return True if vanilla is None else name in vanilla[kind]

    problems = []

    for p in sorted(glob.glob(os.path.join(GEN, "placed_feature", "*.json"))):
        ref = json.load(open(p, encoding="utf-8"))["feature"]
        if not known(ref, "feature"):
            problems.append("placed_feature/%s names a configured feature that does not exist: %s"
                            % (os.path.basename(p), ref))

    biomes = {}
    for p in sorted(glob.glob(os.path.join(GEN, "biome", "*.json"))):
        name = os.path.basename(p)[:-5]
        data = json.load(open(p, encoding="utf-8"))
        biomes[name] = data["features"]
        for step in data["features"]:
            for ref in step:
                if not known(ref, "placed_feature"):
                    problems.append("biome/%s names a placed feature that does not exist: %s" % (name, ref))

    for step in range(11):
        edges, nodes = {}, set()
        for lst in (fs[step] for fs in biomes.values()):
            nodes.update(lst)
            for i, a in enumerate(lst):
                for b in lst[i + 1:]:
                    edges.setdefault(a, set()).add(b)
        indeg = dict.fromkeys(nodes, 0)
        for a in edges:
            for b in edges[a]:
                indeg[b] += 1
        queue = [n for n in nodes if indeg[n] == 0]
        sorted_count = 0
        while queue:
            n = queue.pop()
            sorted_count += 1
            for b in edges.get(n, ()):
                indeg[b] -= 1
                if indeg[b] == 0:
                    queue.append(b)
        if sorted_count != len(nodes):
            stuck = sorted(n for n in nodes if indeg[n] > 0)
            problems.append("step %d has a feature order CYCLE; in the loop: %s" % (step, ", ".join(stuck)))

    counts = " ".join("%d:%d" % (s, len({f for fs in biomes.values() for f in fs[s]})) for s in range(11)
                      if any(fs[s] for fs in biomes.values()))
    print("%d biomes, features per step  %s" % (len(biomes), counts))
    if not have_vanilla:
        print("note: no tools/vanilla-data-26.3, so minecraft: ids were not checked")
    if problems:
        for text in problems:
            print("FAIL " + text)
        sys.exit("%d problem(s)" % len(problems))
    print("ok: every reference resolves and every decoration step is acyclic")

if __name__ == "__main__":
    main()
