import argparse
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from weather_sim import OCEANIA, TICKS_PER_DAY, TICKS_PER_MINUTE, simulate

REPO = pathlib.Path(__file__).resolve().parent.parent

PERIOD_1 = 9643.0
PERIOD_2 = 14879.0
PERIOD_3 = 24533.0
WEIGHT_2 = 0.62
WEIGHT_3 = 0.38
PHASE_2 = 1.7
PHASE_3 = 4.1

TARGET_HEAVY = 1.0 / 3.0

SOFTNESS = 0.22

STREAK_BOOST = 1.80
SPLASH_BOOST = 1.45

TEXTURE_DENSITY = 2.0
FALL_SPEED_ADD = 1.4
SOUND_VOLUME_BOOST = 2.1
SOUND_PITCH_DROP = 0.18

def raw(tick):
    t = float(tick)
    return (math.sin(t / PERIOD_1 * math.tau)
            + WEIGHT_2 * math.sin(t / PERIOD_2 * math.tau + PHASE_2)
            + WEIGHT_3 * math.sin(t / PERIOD_3 * math.tau + PHASE_3))

def smoothstep(edge0, edge1, x):
    if edge1 <= edge0:
        return 0.0 if x < edge0 else 1.0
    t = min(1.0, max(0.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)

def strength(tick, threshold):
    return smoothstep(threshold - SOFTNESS, threshold + SOFTNESS, raw(tick))

def solve_threshold(samples=4_000_000):
    values = sorted(raw(i) for i in range(samples))
    idx = int((1.0 - TARGET_HEAVY) * len(values))
    return values[min(idx, len(values) - 1)]

def measure(threshold, days=400):
    ticks = days * TICKS_PER_DAY
    rain_mask = _raining_mask(ticks)

    heavy_all = 0
    heavy_while_raining = 0
    raining = 0
    phases = []
    run = 0
    spells_with_heavy = 0
    in_spell = False
    spell_had_heavy = False
    spells = 0

    for t in range(ticks):
        s = strength(t, threshold)
        is_heavy = s >= 0.5
        if is_heavy:
            heavy_all += 1
        if rain_mask[t]:
            raining += 1
            if not in_spell:
                in_spell, spell_had_heavy = True, False
                spells += 1
            if is_heavy:
                heavy_while_raining += 1
                run += 1
                spell_had_heavy = True
            elif run:
                phases.append(run)
                run = 0
        else:
            if in_spell:
                in_spell = False
                if spell_had_heavy:
                    spells_with_heavy += 1
                if run:
                    phases.append(run)
                    run = 0
    if run:
        phases.append(run)

    return {
        "heavy_of_all": heavy_all / ticks,
        "heavy_of_raining": heavy_while_raining / max(1, raining),
        "phases": len(phases),
        "phase_mean_min": (sum(phases) / len(phases) / TICKS_PER_MINUTE) if phases else 0.0,
        "phase_max_min": (max(phases) / TICKS_PER_MINUTE) if phases else 0.0,
        "spells": spells,
        "spells_with_heavy": spells_with_heavy,
        "phases_per_spell": len(phases) / max(1, spells),
    }

def _raining_mask(ticks):
    import random
    rng = random.Random(42)

    def sample(key):
        lo, hi = OCEANIA[key]
        return rng.randint(lo, hi)

    i = j = k = 0
    thundering = raining = False
    rain_level = thunder_level = 0.0
    mask = bytearray(ticks)
    for t in range(ticks):
        if i > 0:
            i -= 1
            j = 0 if thundering else 1
            k = 0 if raining else 1
            thundering = raining = False
        else:
            if j > 0:
                j -= 1
                if j == 0:
                    thundering = not thundering
            elif thundering:
                j = sample("thunder_duration")
            else:
                j = sample("thunder_delay")
            if k > 0:
                k -= 1
                if k == 0:
                    raining = not raining
            elif raining:
                k = sample("rain_duration")
            else:
                k = sample("rain_delay")
        thunder_level = min(1.0, max(0.0, thunder_level + (0.01 if thundering else -0.01)))
        rain_level = min(1.0, max(0.0, rain_level + (0.01 if raining else -0.01)))
        mask[t] = 1 if rain_level > 0.2 else 0
    return mask

def render_java(threshold):
    lines = [
        "package com.oceania.client.weather;",
        "",
        "public final class HeavyRainTuning {",
        "",
        "\tprivate HeavyRainTuning() {",
        "\t}",
        "",
        f"\tpublic static final double PERIOD_1 = {PERIOD_1};",
        f"\tpublic static final double PERIOD_2 = {PERIOD_2};",
        f"\tpublic static final double PERIOD_3 = {PERIOD_3};",
        f"\tpublic static final double WEIGHT_2 = {WEIGHT_2};",
        f"\tpublic static final double WEIGHT_3 = {WEIGHT_3};",
        f"\tpublic static final double PHASE_2 = {PHASE_2};",
        f"\tpublic static final double PHASE_3 = {PHASE_3};",
        "",
        f"\tpublic static final double THRESHOLD = {threshold:.6f};",
        f"\tpublic static final double SOFTNESS = {SOFTNESS};",
        "",
        f"\tpublic static final double STREAK_BOOST = {STREAK_BOOST};",
        f"\tpublic static final double SPLASH_BOOST = {SPLASH_BOOST};",
        "",
        f"\tpublic static final double TEXTURE_DENSITY = {TEXTURE_DENSITY};",
        f"\tpublic static final double FALL_SPEED_ADD = {FALL_SPEED_ADD};",
        f"\tpublic static final double SOUND_VOLUME_BOOST = {SOUND_VOLUME_BOOST};",
        f"\tpublic static final double SOUND_PITCH_DROP = {SOUND_PITCH_DROP};",
        "}",
        "",
    ]
    return "\n".join(lines)

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--days", type=int, default=400)
    ap.add_argument("--install", action="store_true")
    args = ap.parse_args()

    print("WHAT THIS MEASURES: how often rain reads as heavy, against the REAL rain schedule from the shipped")
    print("  ranges. It changes nothing server-side, so the rain and thunderstorm numbers stand.")
    print("WHAT IT DOES NOT MEASURE: what heavy rain looks like. The two boost multipliers are judged by eye.")
    print()

    threshold = solve_threshold()
    print(f"threshold solved at raw >= {threshold:.4f} for a {TARGET_HEAVY * 100:.0f}% target")
    m = measure(threshold, args.days)
    print()
    print(f"over {args.days} game days:")
    print(f"  heavy share of ALL time        {m['heavy_of_all'] * 100:5.1f}%")
    print(f"  heavy share of RAINING time    {m['heavy_of_raining'] * 100:5.1f}%   <- target 33%")
    print(f"  heavy phases during rain       {m['phases']} over {m['spells']} rain spells"
          f"  ({m['phases_per_spell']:.1f} per spell)")
    print(f"  heavy phase length             mean {m['phase_mean_min']:.1f} min, longest {m['phase_max_min']:.1f} min")
    print(f"  rain spells containing heavy   {m['spells_with_heavy']}/{m['spells']}"
          f"  ({m['spells_with_heavy'] / max(1, m['spells']) * 100:.0f}%)")
    print()
    print(f"  at full heavy: streak alpha x{STREAK_BOOST}, splashes x{SPLASH_BOOST ** 2:.1f} "
          f"({int(100 * SPLASH_BOOST ** 2)} a tick against vanilla's 100),")
    print(f"                 texture x{TEXTURE_DENSITY} streak count, fall speed "
          f"{3.0 + FALL_SPEED_ADD:.1f}-{4.0 + FALL_SPEED_ADD:.1f} against vanilla's 3.0-4.0,")
    print(f"                 rain audio x{SOUND_VOLUME_BOOST} volume, pitch -{SOUND_PITCH_DROP}")

    if args.install:
        out = REPO / "src/client/java/com/oceania/client/weather/HeavyRainTuning.java"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(render_java(threshold), encoding="utf-8")
        print()
        print("installed: src/client/java/com/oceania/client/weather/HeavyRainTuning.java  (GENERATED)")

if __name__ == "__main__":
    main()
