import argparse
import hashlib
import io
import os
import struct
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SOURCE_ROOTS = (
    os.path.join("src", "main", "java"),
    os.path.join("src", "client", "java"),
)
CLIENT_JARS = tuple(
    p for p in os.environ.get("OCEANIA_CLIENT_JARS", "").split(os.pathsep) if p
)

RESOURCE_ROOTS = (
    os.path.join("src", "main", "resources"),
    os.path.join("src", "client", "resources"),
)

CONSTANT_CHECKS = {
    "com/oceania/client/biolum/BiolumDebug.class": (["biolum"], ["oceania"]),
    "com/oceania/client/render/JellyfishModel.class": (
        ["com/oceania/client/render/OceaniaRenderTypes"], []),
    "com/oceania/client/render/OceaniaRenderTypes.class": (
        ["net/minecraft/client/renderer/rendertype/RenderTypes", "entityTranslucentEmissive"], ["class_"]),
}

RESOURCE_CHECKS = {
    "oceania.client.mixins.json": (["WeatherEffectRendererMixin", "ClientLevelRainMixin", "FogRendererMixin",
                                    "WorldTypeLockMixin", "WorldTypeRowMixin"], []),
    "oceania.modmenu.mixins.json": (["DescriptionListWidgetMixin", "ModMenuMixinPlugin"], []),
    "fabric.mod.json": (["oceania.modmenu.mixins.json", "oceania.client.mixins.json"], []),
    "oceania.mixins.json": (["weather.ServerLevelWeatherAccessor",
                             "spawn.MinecraftServerSeagullSpawnerMixin",
                             "spawn.NaturalSpawnerVillageMixin",
                             "worldgen.NoiseBasedAquiferMixin",
                             "worldgen.CarvingMaskSeaMixin",
                             "worldgen.BeardifierCaptureMixin",
                             "village.VillagerBabyTradeMixin",
                             "raft.CampfireRaftTickMixin",
                             "raft.BrewingStandRaftTickMixin",
                             "debug.NoiseBasedChunkGeneratorMixin",
                             "perf."],
                            ["worldgen.FluidStatusAccessor", "worldgen.NoiseChunkOreVeinMixin"]),
    "com/oceania/mixin/worldgen/NoiseBasedAquiferMixin.class": (
        ["net/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer"], ["class_6350", "intermediary"]),
    "oceania.accesswidener": (["accessWidener v2 official"], ["intermediary", "v2 named"]),
    "data/minecraft/loot_table/chests/shipwreck_map.json": (["minecraft:diamond"], ["exploration_map"]),
    "data/minecraft/loot_table/chests/underwater_ruin_big.json": (["minecraft:diamond"], ["exploration_map"]),
    "data/minecraft/loot_table/chests/underwater_ruin_small.json": (["minecraft:diamond"], ["exploration_map"]),
}

_SKIP_DIRS = {"package-info"}

def md5(path):
    h = hashlib.md5()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def utf8_constants(data):
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    count = struct.unpack_from(">H", data, 8)[0]
    out, off, i = [], 10, 1
    fixed = {3: 4, 4: 4, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
             15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while i < count:
        tag = data[off]
        off += 1
        if tag == 1:
            n = struct.unpack_from(">H", data, off)[0]
            out.append(data[off + 2:off + 2 + n].decode("utf-8", "replace"))
            off += 2 + n
        elif tag in (5, 6):
            off += 8
            i += 1
        elif tag in fixed:
            off += fixed[tag]
        else:
            raise ValueError("unknown constant pool tag %d at %d" % (tag, off - 1))
        i += 1
    return out

def expected_entries():
    classes, resources = {}, {}
    for root in SOURCE_ROOTS:
        base = os.path.join(REPO, root)
        if not os.path.isdir(base):
            continue
        for dirpath, _dirs, files in os.walk(base):
            for name in files:
                if not name.endswith(".java"):
                    continue
                stem = name[:-5]
                if stem in _SKIP_DIRS:
                    continue
                rel = os.path.relpath(os.path.join(dirpath, stem), base)
                classes[rel.replace(os.sep, "/") + ".class"] = os.path.join(dirpath, name)
    for root in RESOURCE_ROOTS:
        base = os.path.join(REPO, root)
        if not os.path.isdir(base):
            continue
        for dirpath, _dirs, files in os.walk(base):
            for name in files:
                rel = os.path.relpath(os.path.join(dirpath, name), base)
                resources[rel.replace(os.sep, "/")] = os.path.join(dirpath, name)
    return classes, resources

def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--jar", default=os.path.join("build", "libs", "oceania-1.0.0.jar"))
    ap.add_argument("--quiet", action="store_true", help="only print failures and the md5")
    ap.add_argument("--clients", action="store_true",
                    help="also report whether each client already holds this exact jar")
    args = ap.parse_args()

    jar = args.jar if os.path.isabs(args.jar) else os.path.join(REPO, args.jar)
    if not os.path.exists(jar):
        print("MISSING JAR %s" % jar)
        return 2

    digest = md5(jar)
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        classes, resources = expected_entries()

        missing_classes = sorted(n for n in classes if n not in names)
        missing_resources = sorted(n for n in resources if n not in names)

        failures = []
        import json as _json
        for entry in sorted(names):
            if not entry.endswith(".json"):
                continue
            try:
                _json.loads(zf.read(entry).decode("utf-8"))
            except Exception as exc:
                failures.append("%s: not strict JSON (%s)" % (entry, exc))
        for entry, (must, must_not) in RESOURCE_CHECKS.items():
            if entry not in names:
                failures.append("%s: not in the jar at all" % entry)
                continue
            text = zf.read(entry).decode("utf-8", "replace")
            for want in must:
                if want not in text:
                    failures.append("%s: does not mention %r" % (entry, want))
            for avoid in must_not:
                if avoid in text:
                    failures.append("%s: still mentions %r, which it must not" % (entry, avoid))

        for entry, (must, must_not) in CONSTANT_CHECKS.items():
            if entry not in names:
                failures.append("%s: not in the jar at all" % entry)
                continue
            try:
                pool = set(utf8_constants(zf.read(entry)))
            except ValueError as exc:
                failures.append("%s: %s" % (entry, exc))
                continue
            for want in must:
                if want not in pool:
                    failures.append("%s: missing the constant %r" % (entry, want))
            for avoid in must_not:
                if avoid in pool:
                    failures.append("%s: holds the constant %r, which it must not" % (entry, avoid))

    if not args.quiet:
        print("jar      %s" % os.path.relpath(jar, REPO))
        print("md5      %s" % digest)
        print("entries  %d, of which %d expected classes and %d expected resources"
              % (len(names), len(classes), len(resources)))

    ok = True
    if missing_classes:
        ok = False
        print("\nNOT COMPILED INTO THE JAR (%d):" % len(missing_classes))
        for n in missing_classes:
            print("  %s" % n)
    if missing_resources:
        ok = False
        print("\nNOT PACKED INTO THE JAR (%d):" % len(missing_resources))
        for n in missing_resources:
            print("  %s" % n)
    if failures:
        ok = False
        print("\nCONTENT CHECKS FAILED (%d):" % len(failures))
        for f in failures:
            print("  %s" % f)

    if args.clients:
        print("")
        for path in CLIENT_JARS:
            if not os.path.exists(path):
                print("  MISSING   %s" % path)
                ok = False
                continue
            other = md5(path)
            same = other == digest
            print("  %-9s %s  %s" % ("in sync" if same else "DIFFERS", other, path))
            if not same:
                print("            ^ this client is NOT running the jar just checked")

    if ok:
        n = len(CONSTANT_CHECKS) + len(RESOURCE_CHECKS)
        print("")
        print("OK - every source compiled, every resource packed, %d content checks passed." % n)
        print("md5 %s" % digest)
        return 0
    print("\nDO NOT DISTRIBUTE THIS JAR.")
    return 1

if __name__ == "__main__":
    sys.exit(main())
