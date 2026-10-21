import os
import shutil
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(REPO, "src", "main", "resources")

DEFAULT_DEST = os.environ.get("OCEANIA_DATAPACKS", "")
NAME = "oceania_hut_village"

INCLUDE = [
    "data/oceania/structure/hut_village",
    "data/oceania/worldgen/template_pool/hut_village",
    "data/oceania/worldgen/processor_list/hut_path.json",
    "data/oceania/loot_table/chests",
]

def main():
    dest = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DEST
    if not os.path.isdir(dest):
        raise SystemExit("no such folder: %s" % dest)
    staging = os.path.join(HERE, "_testpack")
    shutil.rmtree(staging, ignore_errors=True)
    os.makedirs(staging)
    with open(os.path.join(staging, "pack.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        f.write('{"pack": {"pack_format": 48, "description": '
                '"Oceania hut village: /place jigsaw oceania:hut_village/centers oceania:path 7"}}\n')

    count = 0
    for rel in INCLUDE:
        src = os.path.join(RES, rel.replace("/", os.sep))
        dst = os.path.join(staging, rel.replace("/", os.sep))
        if os.path.isdir(src):
            shutil.copytree(src, dst)
            count += len(os.listdir(src))
        elif os.path.isfile(src):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
            count += 1
        else:
            raise SystemExit("missing %s, run build.py first" % rel)

    out = os.path.join(dest, NAME + ".zip")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(staging):
            for name in files:
                full = os.path.join(root, name)
                z.write(full, os.path.relpath(full, staging).replace(os.sep, "/"))
    shutil.rmtree(staging, ignore_errors=True)
    print("%d files -> %s" % (count, out))

if __name__ == "__main__":
    main()
