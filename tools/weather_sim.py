import argparse
import random

TICKS_PER_DAY = 24000
TICKS_PER_MINUTE = 1200

VANILLA = {
    "rain_delay": (12000, 180000),
    "rain_duration": (12000, 24000),
    "thunder_delay": (12000, 180000),
    "thunder_duration": (3600, 15600),
}

OCEANIA = {
    "rain_delay": (7000, 60000),
    "rain_duration": (16000, 36000),
    "thunder_delay": (9000, 72000),
    "thunder_duration": (4800, 18000),
}

def simulate(ranges, ticks, seed=42):
    rng = random.Random(seed)

    def sample(key):
        lo, hi = ranges[key]
        return rng.randint(lo, hi)

    i = 0
    j = 0
    k = 0
    thundering = False
    raining = False
    rain_level = 0.0
    thunder_level = 0.0

    raining_ticks = 0
    storm_ticks = 0
    flag_rain_ticks = 0
    flag_thunder_ticks = 0
    rain_spells = []
    storm_spells = []
    in_rain = 0
    in_storm = 0

    for _ in range(ticks):
        if i > 0:
            i -= 1
            j = 0 if thundering else 1
            k = 0 if raining else 1
            thundering = False
            raining = False
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

        is_raining = rain_level > 0.2
        is_storm = (thunder_level * rain_level) > 0.9

        if is_raining:
            raining_ticks += 1
            in_rain += 1
        elif in_rain:
            rain_spells.append(in_rain)
            in_rain = 0

        if is_storm:
            storm_ticks += 1
            in_storm += 1
            thunder_window_saw_storm = True
        elif in_storm:
            storm_spells.append(in_storm)
            in_storm = 0

        if raining:
            flag_rain_ticks += 1
        if thundering:
            flag_thunder_ticks += 1

    if in_rain:
        rain_spells.append(in_rain)
    if in_storm:
        storm_spells.append(in_storm)

    thunder_windows, wasted_thunder_windows = _count_thunder_windows(ranges, ticks, seed)

    def mins(t):
        return t / TICKS_PER_MINUTE

    return {
        "ticks": ticks,
        "days": ticks / TICKS_PER_DAY,
        "rain_fraction": raining_ticks / ticks,
        "storm_fraction": storm_ticks / ticks,
        "flag_rain_fraction": flag_rain_ticks / ticks,
        "flag_thunder_fraction": flag_thunder_ticks / ticks,
        "rain_spells": len(rain_spells),
        "rain_spell_mean_min": mins(sum(rain_spells) / len(rain_spells)) if rain_spells else 0.0,
        "rain_spell_max_min": mins(max(rain_spells)) if rain_spells else 0.0,
        "rain_spell_min_min": mins(min(rain_spells)) if rain_spells else 0.0,
        "storm_spells": len(storm_spells),
        "storm_spell_mean_min": mins(sum(storm_spells) / len(storm_spells)) if storm_spells else 0.0,
        "storm_spell_max_min": mins(max(storm_spells)) if storm_spells else 0.0,
        "rain_per_day": len(rain_spells) / (ticks / TICKS_PER_DAY),
        "storm_per_day": len(storm_spells) / (ticks / TICKS_PER_DAY),
        "thunder_windows": thunder_windows,
        "wasted_thunder_windows": wasted_thunder_windows,
    }

def _count_thunder_windows(ranges, ticks, seed):
    rng = random.Random(seed)

    def sample(key):
        lo, hi = ranges[key]
        return rng.randint(lo, hi)

    i = j = k = 0
    thundering = raining = False
    rain_level = thunder_level = 0.0
    windows = 0
    wasted = 0
    saw_storm = False
    was_thundering = False

    for _ in range(ticks):
        if i > 0:
            i -= 1
            j = 0 if thundering else 1
            k = 0 if raining else 1
            thundering = False
            raining = False
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

        if thundering and not was_thundering:
            windows += 1
            saw_storm = False
        if thundering and (thunder_level * rain_level) > 0.9:
            saw_storm = True
        if was_thundering and not thundering:
            if not saw_storm:
                wasted += 1
        was_thundering = thundering

    return windows, wasted

def expected_duty(ranges, key_delay, key_duration):
    d = sum(ranges[key_delay]) / 2
    r = sum(ranges[key_duration]) / 2
    return r / (d + r)

def report(name, ranges, ticks):
    s = simulate(ranges, ticks)
    print(f"--- {name} ---")
    for key in ("rain_delay", "rain_duration", "thunder_delay", "thunder_duration"):
        lo, hi = ranges[key]
        print(f"  {key:16s} {lo:7d}..{hi:7d} ticks   ({lo / TICKS_PER_MINUTE:6.1f}..{hi / TICKS_PER_MINUTE:6.1f} min)")
    pred_rain = expected_duty(ranges, "rain_delay", "rain_duration")
    pred_thunder = expected_duty(ranges, "thunder_delay", "thunder_duration")
    print(f"  over {s['days']:.0f} game days ({ticks:,} ticks):")
    print(f"    raining            {s['rain_fraction'] * 100:5.1f}% of the time   "
          f"(predicted from means {pred_rain * 100:.1f}%)")
    print(f"    THUNDERSTORM       {s['storm_fraction'] * 100:5.1f}% of the time   "
          f"(rain x thunder overlap; predicted {pred_rain * pred_thunder * 100:.1f}%)")
    print(f"    rain spells        {s['rain_per_day']:.2f} per day, "
          f"mean {s['rain_spell_mean_min']:.0f} min, range {s['rain_spell_min_min']:.0f}..{s['rain_spell_max_min']:.0f} min")
    print(f"    storm spells       {s['storm_per_day']:.2f} per day, "
          f"mean {s['storm_spell_mean_min']:.0f} min, longest {s['storm_spell_max_min']:.0f} min")
    print(f"    thunder windows    {s['thunder_windows']} opened, {s['wasted_thunder_windows']} produced "
          f"NO visible storm ({s['wasted_thunder_windows'] / max(1, s['thunder_windows']) * 100:.0f}% wasted - "
          f"they landed in a clear spell)")
    return s

SWEEP = {
    "vanilla": VANILLA,
    "oceania (proposed)": OCEANIA,
    "wetter": {"rain_delay": (5000, 42000), "rain_duration": (18000, 42000),
               "thunder_delay": (7000, 54000), "thunder_duration": (4800, 18000)},
    "gentler": {"rain_delay": (10000, 84000), "rain_duration": (14000, 30000),
                "thunder_delay": (12000, 96000), "thunder_duration": (3600, 15600)},
}

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--days", type=int, default=2000, help="game days to simulate (default 2000)")
    ap.add_argument("--sweep", action="store_true", help="report several candidate range sets")
    args = ap.parse_args()
    ticks = args.days * TICKS_PER_DAY

    print("WHAT THIS MEASURES: the weather COUNTERS only, transcribed from ServerLevel.advanceWeatherCycle")
    print("  (ServerLevel.java:640-700) and Level.getRainLevel/getThunderLevel/isRaining/isThundering")
    print("  (Level.java:830-856).")
    print("WHAT IT DOES NOT MEASURE: whether rain is visible in any biome (that is has_precipitation and")
    print("  temperature), whether a storm over open ocean shows anything, or what any of it looks like.")
    print()

    sets = SWEEP if args.sweep else {"vanilla 1.21.1": VANILLA, "oceania (proposed)": OCEANIA}
    out = {}
    for name, ranges in sets.items():
        out[name] = report(name, ranges, ticks)
        print()

    if "vanilla 1.21.1" in out and "oceania (proposed)" in out:
        v, o = out["vanilla 1.21.1"], out["oceania (proposed)"]
        print("--- the change, stated as multiples ---")
        print(f"  time spent raining       {v['rain_fraction'] * 100:.1f}% -> {o['rain_fraction'] * 100:.1f}%"
              f"   ({o['rain_fraction'] / v['rain_fraction']:.1f}x)")
        print(f"  mean rain spell          {v['rain_spell_mean_min']:.0f} min -> {o['rain_spell_mean_min']:.0f} min"
              f"   ({o['rain_spell_mean_min'] / v['rain_spell_mean_min']:.1f}x)")
        print(f"  time in a thunderstorm   {v['storm_fraction'] * 100:.2f}% -> {o['storm_fraction'] * 100:.2f}%"
              f"   ({o['storm_fraction'] / max(1e-9, v['storm_fraction']):.1f}x)")
        print(f"  thunderstorms per day    {v['storm_per_day']:.2f} -> {o['storm_per_day']:.2f}"
              f"   ({o['storm_per_day'] / max(1e-9, v['storm_per_day']):.1f}x)")

if __name__ == "__main__":
    main()
