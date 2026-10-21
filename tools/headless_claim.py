import pathlib
import sys
import time

CLAIM = pathlib.Path(__file__).resolve().parent.parent / "run-headless" / ".headless-claim"
STALE_SECONDS = 45 * 60

def read():
    if not CLAIM.exists():
        return None, 0
    try:
        who, when = CLAIM.read_text(encoding="utf-8").strip().split("\n", 1)
        return who, float(when)
    except (ValueError, OSError):
        return None, 0

def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    action, who = sys.argv[1], sys.argv[2]
    CLAIM.parent.mkdir(parents=True, exist_ok=True)

    holder, when = read()
    age = time.time() - when

    if action == "take":
        if holder and holder != who and age < STALE_SECONDS:
            print("HEADLESS BUSY: held by %s for %d min. Do not start." % (holder, age // 60))
            return 1
        if holder and holder != who:
            print("taking over a stale claim from %s (%d min old)" % (holder, age // 60))
        CLAIM.write_text("%s\n%f\n" % (who, time.time()), encoding="utf-8")
        print("headless claimed by %s" % who)
        return 0

    if action == "release":
        if holder and holder != who:
            print("not releasing: claim belongs to %s" % holder)
            return 0
        CLAIM.unlink(missing_ok=True)
        print("headless released by %s" % who)
        return 0

    print(__doc__)
    return 2

if __name__ == "__main__":
    sys.exit(main())
