import os
import struct
import sys
import zlib

from PIL import Image

COLOURS = {
    "air": 0xf4f4f4, "cave_air": 0xf4f4f4, "void_air": 0xf4f4f4,
    "water": 0x2b6cb0, "lava": 0xff7a18, "stone": 0x8a8a8a, "deepslate": 0x4a4a52, "basalt": 0x33333a,
    "sand": 0xe8d9a0, "sandstone": 0xd0bc82, "gravel": 0x9a8f80, "grass_block": 0x5a9e3a, "dirt": 0x7a5a3a,
    "bedrock": 0x111111, "granite": 0xa8796a, "diorite": 0xd8d8d2, "andesite": 0x9b9b9b, "tuff": 0x6c6c62,
    "coal_ore": 0x101010, "deepslate_coal_ore": 0x101010, "iron_ore": 0xe0a070, "deepslate_iron_ore": 0xe0a070,
    "copper_ore": 0xd97b3a, "deepslate_copper_ore": 0xd97b3a, "gold_ore": 0xf5d020, "deepslate_gold_ore": 0xf5d020,
    "redstone_ore": 0xe01010, "deepslate_redstone_ore": 0xe01010, "lapis_ore": 0x2040d0, "deepslate_lapis_ore": 0x2040d0,
    "diamond_ore": 0x20e0e0, "deepslate_diamond_ore": 0x20e0e0, "emerald_ore": 0x20d050, "deepslate_emerald_ore": 0x20d050,
    "oak_log": 0x4a3418, "birch_log": 0x4a3418, "spruce_log": 0x4a3418, "jungle_log": 0x4a3418, "dark_oak_log": 0x4a3418,
    "oak_leaves": 0x2f7a22, "birch_leaves": 0x2f7a22, "spruce_leaves": 0x2f7a22, "jungle_leaves": 0x2f7a22, "dark_oak_leaves": 0x2f7a22,
    "short_grass": 0x5fae3a, "tall_grass": 0x5fae3a, "fern": 0x5fae3a, "large_fern": 0x5fae3a, "dandelion": 0xe8e040, "poppy": 0xe8e040,
    "seagrass": 0x2f8f6a, "tall_seagrass": 0x2f8f6a, "kelp": 0x3fa050, "kelp_plant": 0x3fa050,
    "dripstone_block": 0x7d6a5a, "pointed_dripstone": 0xb08a6a, "amethyst_block": 0x9a5cf0, "budding_amethyst": 0x9a5cf0,
    "amethyst_cluster": 0xc89cff, "calcite": 0xe8e4dc, "smooth_basalt": 0x3c3c44, "sculk": 0x0b2a30, "sculk_vein": 0x0b2a30,
    "sculk_catalyst": 0x10606a, "sculk_sensor": 0x10606a, "sculk_shrieker": 0x20c0d0, "moss_block": 0x4f8a2a, "moss_carpet": 0x4f8a2a,
    "azalea": 0x5fa040, "flowering_azalea": 0x5fa040, "azalea_leaves": 0x5fa040, "flowering_azalea_leaves": 0x5fa040,
    "rooted_dirt": 0x8a6a4a, "hanging_roots": 0x8a6a4a, "cave_vines": 0x7fb040, "cave_vines_plant": 0x7fb040, "spore_blossom": 0xf070a0,
    "big_dripleaf": 0x3f9a40, "big_dripleaf_stem": 0x3f9a40, "small_dripleaf": 0x3f9a40, "clay": 0xa0a8b8,
    "raw_copper_block": 0xc06a40, "raw_iron_block": 0xc8a080, "glow_lichen": 0x90c0a0, "spawner": 0x6a7a6a,
    "mossy_cobblestone": 0x6a7a6a, "cobblestone": 0x6a7a6a, "magma_block": 0xc2410c, "obsidian": 0x201830,
    "snow": 0xffffff, "snow_block": 0xffffff, "ice": 0xa0d0f0, "packed_ice": 0xa0d0f0, "podzol": 0x6a4a2a, "coarse_dirt": 0x7a5a3a,
    "mud": 0x5a4a3a, "mycelium": 0x7a6a7a, "red_sand": 0xd08a40, "terracotta": 0xa06a50, "oak_planks": 0xb08a50, "oak_fence": 0xb08a50,
    "rail": 0x8a8a90, "cobweb": 0xe0e0e0, "chest": 0xb08a50, "torch": 0xffe080, "wall_torch": 0xffe080,
    "stone_bricks": 0x7a7a7a, "mossy_stone_bricks": 0x6a8a6a, "cracked_stone_bricks": 0x7a7a7a, "iron_bars": 0x9a9a9a,
    "seagrass_": 0, "bubble_column": 0x9fd8f0,
}
PLANTS = {"short_grass", "tall_grass", "fern", "large_fern", "dandelion", "poppy", "seagrass", "tall_seagrass", "kelp",
          "kelp_plant", "oak_leaves", "birch_leaves", "spruce_leaves", "jungle_leaves", "dark_oak_leaves", "oak_log",
          "birch_log", "spruce_log", "jungle_log", "dark_oak_log", "snow", "vine", "azalea", "flowering_azalea",
          "azalea_leaves", "flowering_azalea_leaves", "cave_vines", "cave_vines_plant", "glow_lichen", "sugar_cane",
          "lily_pad", "bamboo", "sweet_berry_bush", "dead_bush", "cactus", "pointed_dripstone", "spore_blossom",
          "hanging_roots", "moss_carpet", "small_dripleaf", "big_dripleaf", "big_dripleaf_stem", "cobweb"}
OPEN = {"air", "cave_air", "void_air", "water", "lava"}

def read_nbt(buf, pos, tag):
    if tag == 1:
        return struct.unpack_from(">b", buf, pos)[0], pos + 1
    if tag == 2:
        return struct.unpack_from(">h", buf, pos)[0], pos + 2
    if tag == 3:
        return struct.unpack_from(">i", buf, pos)[0], pos + 4
    if tag == 4:
        return struct.unpack_from(">q", buf, pos)[0], pos + 8
    if tag == 5:
        return struct.unpack_from(">f", buf, pos)[0], pos + 4
    if tag == 6:
        return struct.unpack_from(">d", buf, pos)[0], pos + 8
    if tag == 7:
        n = struct.unpack_from(">i", buf, pos)[0]
        return buf[pos + 4:pos + 4 + n], pos + 4 + n
    if tag == 8:
        n = struct.unpack_from(">H", buf, pos)[0]
        return buf[pos + 2:pos + 2 + n].decode("utf-8", "replace"), pos + 2 + n
    if tag == 9:
        t = buf[pos]
        n = struct.unpack_from(">i", buf, pos + 1)[0]
        pos += 5
        out = []
        for _ in range(n):
            v, pos = read_nbt(buf, pos, t)
            out.append(v)
        return out, pos
    if tag == 10:
        out = {}
        while True:
            t = buf[pos]
            pos += 1
            if t == 0:
                return out, pos
            n = struct.unpack_from(">H", buf, pos)[0]
            name = buf[pos + 2:pos + 2 + n].decode("utf-8", "replace")
            pos += 2 + n
            v, pos = read_nbt(buf, pos, t)
            out[name] = v
    if tag == 11:
        n = struct.unpack_from(">i", buf, pos)[0]
        return list(struct.unpack_from(">%di" % n, buf, pos + 4)), pos + 4 + 4 * n
    if tag == 12:
        n = struct.unpack_from(">i", buf, pos)[0]
        return list(struct.unpack_from(">%dq" % n, buf, pos + 4)), pos + 4 + 8 * n
    raise ValueError("tag %d" % tag)

def root(buf):
    assert buf[0] == 10
    n = struct.unpack_from(">H", buf, 1)[0]
    v, _ = read_nbt(buf, 3 + n, 10)
    return v

class Region:
    def __init__(self, path):
        self.data = open(path, "rb").read()
        self.chunks = {}

    def chunk(self, cx, cz):
        key = (cx & 31, cz & 31)
        if key in self.chunks:
            return self.chunks[key]
        i = 4 * (key[0] + key[1] * 32)
        off = struct.unpack_from(">I", self.data, i)[0]
        sectors = off & 0xff
        off >>= 8
        if off == 0:
            self.chunks[key] = None
            return None
        p = off * 4096
        n = struct.unpack_from(">i", self.data, p)[0]
        comp = self.data[p + 4]
        raw = self.data[p + 5:p + 4 + n]
        if comp == 2:
            raw = zlib.decompress(raw)
        elif comp == 1:
            import gzip
            raw = gzip.decompress(raw)
        elif comp == 3:
            pass
        else:
            raise ValueError("compression %d" % comp)
        tag = root(raw)
        self.chunks[key] = Chunk(tag)
        return self.chunks[key]

def block_name(entry):
    if isinstance(entry, str):
        return entry.split(":", 1)[-1]
    for key in ("Name", ""):
        if key in entry and isinstance(entry[key], str):
            return entry[key].split(":", 1)[-1]
    for v in entry.values():
        if isinstance(v, str) and ":" in v:
            return v.split(":", 1)[-1]
    return "?"

class Chunk:
    def __init__(self, tag):
        self.status = tag.get("Status")
        self.sections = {}
        for s in tag.get("sections", []):
            bs = s.get("block_states")
            if not bs:
                continue
            pal = [block_name(p) for p in bs["palette"]]
            data = bs.get("data")
            self.sections[s["Y"]] = (pal, data)

    def block(self, x, y, z):
        sy = y >> 4
        sec = self.sections.get(sy)
        if sec is None:
            return "air"
        pal, data = sec
        if data is None or len(pal) == 1:
            return pal[0]
        bits = max(4, (len(pal) - 1).bit_length())
        per = 64 // bits
        idx = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
        word = data[idx // per] & 0xffffffffffffffff
        v = (word >> ((idx % per) * bits)) & ((1 << bits) - 1)
        return pal[v] if v < len(pal) else "air"

class World:
    def __init__(self, region_dir):
        self.dir = region_dir
        self.regions = {}

    def block(self, x, y, z):
        rx, rz = x >> 9, z >> 9
        r = self.regions.get((rx, rz))
        if r is None:
            p = os.path.join(self.dir, "r.%d.%d.mca" % (rx, rz))
            r = Region(p) if os.path.exists(p) else False
            self.regions[(rx, rz)] = r
        if not r:
            return None
        c = r.chunk(x >> 4, z >> 4)
        if c is None or c.status != "minecraft:full":
            return None
        return c.block(x, y, z)

def main():
    region_dir, out, z, x0, x1 = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5])
    ylo = int(sys.argv[6]) if len(sys.argv) > 6 else -64
    yhi = int(sys.argv[7]) if len(sys.argv) > 7 else 140
    w = World(region_dir)
    length, height = x1 - x0 + 1, yhi - ylo + 1
    img = Image.new("RGB", (length, height), (255, 0, 255))
    px = img.load()
    BAND, BANDS = 32, 10
    band_open, band_all = [0] * BANDS, [0] * BANDS
    open_total = under = water_under = lava_under = 0
    missing = 0
    for i in range(length):
        x = x0 + i
        floor = None
        for y in range(yhi, ylo - 1, -1):
            b = w.block(x, y, z)
            if b is None:
                missing += 1
                px[i, yhi - y] = (255, 0, 255)
                continue
            c = COLOURS.get(b)
            if c is None:
                c = 0xff00ff
            px[i, yhi - y] = ((c >> 16) & 255, (c >> 8) & 255, c & 255)
            if floor is None and b not in OPEN and b not in PLANTS:
                floor = y
            if floor is not None and y < floor:
                band = min(BANDS - 1, (floor - y) // BAND)
                band_all[band] += 1
                under += 1
                if b in OPEN:
                    band_open[band] += 1
                    open_total += 1
                    if b == "water":
                        water_under += 1
                    elif b == "lava":
                        lava_under += 1
    img.save(out)
    print("slice z=%d x %d..%d y %d..%d -> %s (missing blocks %d)" % (z, x0, x1, ylo, yhi, out, missing))
    print("under the floor: %.2f%% open (%.2f%% water, %.2f%% lava)" % (
        100.0 * open_total / max(1, under), 100.0 * water_under / max(1, under), 100.0 * lava_under / max(1, under)))
    print("open share per 32-block band below the floor: " + " ".join(
        "%d..%d %.1f%%" % (k * BAND, (k + 1) * BAND, 100.0 * band_open[k] / band_all[k]) for k in range(BANDS) if band_all[k]))

if __name__ == "__main__":
    main()
