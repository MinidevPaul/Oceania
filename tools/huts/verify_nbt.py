import glob
import gzip
import os
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from nbt import read_nbt, write_nbt, to_bytes, from_bytes

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JAR_GLOB = (".gradle/loom-cache/minecraftMaven/net/minecraft/"
            "minecraft-common-*/*/minecraft-common-*-v2.jar")
PREFIX = "data/minecraft/structure/"
TEMPLATES = [
    "village/plains/houses/plains_small_house_1",
    "village/plains/houses/plains_fisher_cottage_1",
    "village/plains/streets/straight_01",
    "village/plains/town_centers/plains_fountain_01",
    "village/plains/villagers/baby",
    "village/common/animals/cat_black",
]
OUT = os.environ.get("HUTS_SCRATCH") or os.path.join(tempfile.gettempdir(),
                                                     "huts_nbt_verify")

def find_jar():
    hits = sorted(glob.glob(os.path.join(REPO, JAR_GLOB)))
    if not hits:
        sys.exit("no Minecraft jar matching %s under %s" % (JAR_GLOB, REPO))
    return hits[-1]

def extract(jar):
    paths = {}
    os.makedirs(OUT, exist_ok=True)
    with zipfile.ZipFile(jar) as zf:
        for name in TEMPLATES:
            entry = PREFIX + name + ".nbt"
            dest = os.path.join(OUT, name.replace("/", "_") + ".nbt")
            with open(dest, "wb") as fh:
                fh.write(zf.read(entry))
            paths[name] = dest
    return paths

def diff(a, b, ordered=True, path="$"):
    ta, tb = type(a).__name__, type(b).__name__
    if ta != tb:
        return "%s: tag type %s vs %s" % (path, ta, tb)
    if isinstance(a, dict):
        for key in a:
            if key not in b:
                return "%s.%s: missing on the right" % (path, key)
        for key in b:
            if key not in a:
                return "%s.%s: missing on the left" % (path, key)
        if ordered and list(a) != list(b):
            return "%s: key order %s vs %s" % (path, list(a), list(b))
        for key in a:
            found = diff(a[key], b[key], ordered, "%s.%s" % (path, key))
            if found:
                return found
        return None
    if isinstance(a, list):
        if len(a) != len(b):
            return "%s: length %d vs %d" % (path, len(a), len(b))
        for i in range(len(a)):
            found = diff(a[i], b[i], ordered, "%s[%d]" % (path, i))
            if found:
                return found
        return None
    if isinstance(a, float) and a != a and b != b:
        return None
    if a != b:
        return "%s: %r vs %r" % (path, a, b)
    return None

def check(name, path):
    raw_orig = gzip.decompress(open(path, "rb").read())
    tree = read_nbt(path)
    mine = to_bytes(tree)
    again = from_bytes(mine)

    found = diff(tree, again, ordered=True)
    if found:
        print("FAIL %-44s round trip differs at %s" % (name, found))
        return False, None

    rt = write_nbt(os.path.join(OUT, name.replace("/", "_") + ".roundtrip.nbt"), tree)
    found = diff(tree, read_nbt(rt), ordered=True)
    if found:
        print("FAIL %-44s file round trip differs at %s" % (name, found))
        return False, None

    if mine == raw_orig:
        note = "raw bytes identical (%d bytes)" % len(raw_orig)
        print("PASS %-44s %s" % (name, note))
        return True, note
    found = diff(from_bytes(raw_orig), from_bytes(mine), ordered=False)
    if found:
        print("FAIL %-44s bytes differ AND trees differ at %s" % (name, found))
        return False, None
    note = ("raw bytes differ (%d vs %d) but parsed trees are equal "
            "(key order only)" % (len(raw_orig), len(mine)))
    print("PASS %-44s %s" % (name, note))
    return True, note

def dump_house(tree):
    print("\n-- plains_small_house_1 --")
    size = tree["size"]
    print("size:        %s (%s of %s)" % (list(size), type(size).__name__,
                                          type(size[0]).__name__))
    dv = tree["DataVersion"]
    print("DataVersion: %d (%s)" % (dv, type(dv).__name__))
    palette, blocks = tree["palette"], tree["blocks"]
    print("palette:     %d entries" % len(palette))
    print("blocks:      %d entries" % len(blocks))
    print("jigsaw blocks (pos name target pool final_state orientation):")
    count = 0
    for block in blocks:
        state = palette[int(block["state"])]
        if str(state["Name"]) != "minecraft:jigsaw":
            continue
        count += 1
        tag = block.get("nbt", {})
        print("  %-12s %-28s %-28s %-44s %-28s %s" % (
            list(block["pos"]),
            str(tag.get("name", "-")),
            str(tag.get("target", "-")),
            str(tag.get("pool", "-")),
            str(tag.get("final_state", "-")),
            str(state.get("Properties", {}).get("orientation", "-"))))
    if not count:
        print("  (none)")

def dump_baby(tree):
    print("\n-- baby entities --")
    entities = tree.get("entities", [])
    if not entities:
        print("  (none)")
    for entity in entities:
        tag = entity.get("nbt", {})
        print("  pos=%s blockPos=%s(%s) id=%s keys=%s" % (
            list(entity["pos"]), list(entity["blockPos"]),
            type(entity["blockPos"]).__name__,
            str(tag.get("id", "-")), sorted(tag)))

def main():
    jar = find_jar()
    print("jar:       %s" % os.path.relpath(jar, REPO))
    print("scratch:   %s\n" % OUT)
    paths = extract(jar)
    failures = 0
    for name in TEMPLATES:
        ok, _ = check(name, paths[name])
        if not ok:
            failures += 1
    dump_house(read_nbt(paths[TEMPLATES[0]]))
    dump_baby(read_nbt(paths["village/plains/villagers/baby"]))
    print("\n%d/%d templates passed" % (len(TEMPLATES) - failures, len(TEMPLATES)))
    return 1 if failures else 0

if __name__ == "__main__":
    sys.exit(main())
