import argparse
import os
import pathlib
import random

from PIL import Image, ImageDraw, ImageFont

REPO = pathlib.Path(__file__).resolve().parent.parent

WIDTH, HEIGHT = 64, 256

VANILLA_STREAKS = 110
LENGTH_WEIGHTS = {1: 45, 2: 11, 3: 13, 4: 8, 5: 13, 6: 2, 7: 3, 8: 3, 9: 1, 10: 3, 11: 4, 15: 3, 16: 1}
COLOR = (70, 103, 195)
ALPHA_HEAD = 255
ALPHA_TAIL = 14

DENSITIES = [1.0, 1.75, 2.0, 2.5]

def draw_rain(multiplier, seed=1):
    rng = random.Random(seed)
    img = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    px = img.load()

    lengths = []
    for length, weight in LENGTH_WEIGHTS.items():
        lengths += [length] * weight

    count = int(round(VANILLA_STREAKS * multiplier))
    placed = 0
    attempts = 0
    while placed < count and attempts < count * 40:
        attempts += 1
        x = rng.randrange(WIDTH)
        length = rng.choice(lengths)
        y = rng.randrange(HEIGHT)
        clash = False
        for dy in range(-2, length + 2):
            yy = (y + dy) % HEIGHT
            if px[x, yy][3] > 0:
                clash = True
                break
        if clash:
            continue

        peak = rng.randint(140, 255) if length > 1 else rng.randint(60, 255)
        for i in range(length):
            yy = (y + i) % HEIGHT
            t = i / max(1, length - 1) if length > 1 else 0.0
            a = int(round(peak + (ALPHA_TAIL - peak) * t))
            jitter = rng.randint(-6, 6)
            px[x, yy] = COLOR + (max(ALPHA_TAIL, min(255, a + jitter)),)
        placed += 1

    return img, placed

def coverage(img):
    px = img.load()
    n = sum(1 for y in range(img.height) for x in range(img.width) if px[x, y][3] > 0)
    return n, n / (img.width * img.height)

def fat_segment_rate(img):
    px = img.load()
    one = wide = 0
    for y in range(img.height):
        run = 0
        for x in range(img.width):
            if px[x, y][3] > 0:
                run += 1
            else:
                if run == 1:
                    one += 1
                elif run > 1:
                    wide += 1
                run = 0
        if run == 1:
            one += 1
        elif run > 1:
            wide += 1
    return wide / max(1, one + wide)

VANILLA_FAT_RATE = 0.063

def _recorded_density():
    import re
    path = REPO / "tools" / "heavy_rain.py"
    try:
        m = re.search(r"^TEXTURE_DENSITY\s*=\s*([0-9.]+)", path.read_text(encoding="utf-8"), re.M)
    except OSError:
        return None
    return float(m.group(1)) if m else None

def panel(img, tiles_x, tiles_y, scale, bg):
    tile = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    w, h = tile.width * tiles_x, tile.height * tiles_y
    out = Image.new("RGB", (w, h), bg)
    for ty in range(tiles_y):
        for tx in range(tiles_x):
            out.paste(tile, (tx * tile.width, ty * tile.height), tile)
    return out

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    default_out = os.environ.get("OCEANIA_PREVIEW_OUT") or str(REPO / "build" / "rain-texture")
    ap.add_argument("--out", default=default_out)
    ap.add_argument("--install", type=float, metavar="MULTIPLIER",
                    help="write this density as the heavy-rain texture into src/")
    args = ap.parse_args()

    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    panels = []
    labels = []
    for m in DENSITIES:
        img, placed = draw_rain(m)
        n, frac = coverage(img)
        img.save(out / f"rain-x{m:.2f}.png")
        panels.append(panel(img, 3, 1, 2, (12, 14, 20)))
        labels.append(f"x{m:.2f}  -  {placed} streaks, {frac * 100:.1f}% of pixels"
                      + ("   (vanilla's density)" if m == 1.0 else ""))
        fat = fat_segment_rate(img)
        verdict = "ok" if fat <= VANILLA_FAT_RATE else "TOO FAT - drops are merging, this density is out"
        print(f"x{m:.2f}: {placed} streaks, {n} lit pixels, {frac * 100:.1f}% coverage, "
              f"fat segments {fat * 100:.1f}% vs vanilla {VANILLA_FAT_RATE * 100:.1f}%  [{verdict}]")

    gap = 26
    pw, ph = panels[0].size
    sheet = Image.new("RGB", (pw * len(panels) + gap * (len(panels) + 1), ph + 86), (8, 10, 14))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("arial.ttf", 14)
        small = ImageFont.truetype("arial.ttf", 12)
    except OSError:
        font = small = ImageFont.load_default()
    d.text((gap, 10), "Heavy-rain texture density - same 1px drops, same lengths, same colour, only the COUNT changes",
           fill=(230, 240, 255), font=font)
    d.text((gap, 30), "Each panel is the 64x256 texture tiled 3 times, 2x magnified, over a dark background. "
                      "Density only - not a frame of the game.", fill=(140, 160, 180), font=small)
    for i, (p, label) in enumerate(zip(panels, labels)):
        x = gap + i * (pw + gap)
        sheet.paste(p, (x, 56))
        d.text((x, 56 + ph + 6), label, fill=(200, 220, 240), font=small)
    sheet.save(out / "rain-density-comparison.png")
    print(f"\nwrote {out / 'rain-density-comparison.png'}")
    print("\nSCOPE: density only. No perspective, no depth, no overlapping columns, no fog. In game you look")
    print("  through hundreds of these at once, so the real thing reads denser than any single tile here.")

    if args.install:
        img, placed = draw_rain(args.install)
        dest = REPO / "src/main/resources/assets/oceania/textures/environment/rain_heavy.png"
        recorded = _recorded_density()
        if recorded is not None and abs(recorded - args.install) > 1e-9:
            print(f"REFUSING to install x{args.install:.2f}: tools/heavy_rain.py records TEXTURE_DENSITY = "
                  f"{recorded}, so the Java and the art would disagree about what shipped.")
            print("  Change TEXTURE_DENSITY there, rerun heavy_rain.py --install, then rerun this.")
            raise SystemExit(1)

        dest.parent.mkdir(parents=True, exist_ok=True)
        fat = fat_segment_rate(img)
        if fat > VANILLA_FAT_RATE:
            print(f"REFUSING to install x{args.install:.2f}: fat-segment rate {fat * 100:.1f}% exceeds "
                  f"vanilla's {VANILLA_FAT_RATE * 100:.1f}% - drops are merging into wider ones.")
            raise SystemExit(1)
        img.save(dest)
        n, frac = coverage(img)
        print(f"\ninstalled x{args.install:.2f}: {placed} streaks, {frac * 100:.1f}% coverage, "
              f"fat segments {fat * 100:.1f}% (vanilla {VANILLA_FAT_RATE * 100:.1f}%)")
        print(f"  {dest.relative_to(REPO)}")

if __name__ == "__main__":
    main()
