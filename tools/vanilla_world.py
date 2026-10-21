import hashlib
import json
import os
import pathlib
import subprocess
import sys
import threading
import time
import urllib.request

MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
VERSION = "26.3"
ROOT = pathlib.Path(__file__).resolve().parent
REPO = pathlib.Path(os.environ.get("OCEANIA_REPO", ROOT.parent))
JAVA = os.environ.get("OCEANIA_JAVA", "java")
WORLD = REPO / "build" / ("vanilla-world-" + VERSION)
FORCELOAD = ["forceload add 0 0 575 63", "forceload add 0 64 575 127"]

def fetch_json(url):
    with urllib.request.urlopen(url) as r:
        return json.load(r)

def server_jar():
    work = REPO / "tools" / ("vanilla-server-" + VERSION)
    work.mkdir(parents=True, exist_ok=True)
    jar = work / ("server-%s.jar" % VERSION)
    manifest = fetch_json(MANIFEST)
    entry = next((v for v in manifest["versions"] if v["id"] == VERSION), None)
    if entry is None:
        sys.exit("version %s is not in the manifest" % VERSION)
    want = fetch_json(entry["url"])["downloads"]["server"]
    if jar.is_file() and hashlib.sha1(jar.read_bytes()).hexdigest() == want["sha1"]:
        print("server jar already present:", jar)
        return jar
    print("downloading", want["url"])
    urllib.request.urlretrieve(want["url"], jar)
    if hashlib.sha1(jar.read_bytes()).hexdigest() != want["sha1"]:
        sys.exit("sha1 mismatch on the server jar")
    print("server jar ok:", jar)
    return jar

def main():
    jar = server_jar()
    WORLD.mkdir(parents=True, exist_ok=True)
    (WORLD / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (WORLD / "server.properties").write_text(
        "level-seed=42\nlevel-type=minecraft\\:normal\nonline-mode=false\nmax-tick-time=-1\n"
        "view-distance=10\nsimulation-distance=4\nspawn-protection=0\nsync-chunk-writes=true\n"
        "level-name=world\nmotd=oceania cave control\n", encoding="utf-8")

    proc = subprocess.Popen([JAVA, "-Xmx4G", "-jar", str(jar), "nogui"], cwd=str(WORLD),
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, bufsize=1)
    done = threading.Event()
    lines = []

    def pump():
        for line in proc.stdout:
            lines.append(line.rstrip())
            low = line.lower()
            if "done (" in low or "for help, type" in low:
                done.set()
            if len(lines) < 40 or "forceload" in low or "saving" in low or "error" in low:
                print("  |", line.rstrip()[:160])

    threading.Thread(target=pump, daemon=True).start()
    if not done.wait(600):
        proc.kill()
        sys.exit("server never reported Done; last lines:\n" + "\n".join(lines[-25:]))
    print("server up; force-loading")

    def send(cmd, wait):
        proc.stdin.write(cmd + "\n")
        proc.stdin.flush()
        time.sleep(wait)

    for cmd in FORCELOAD:
        send(cmd, 5)
    time.sleep(90)
    send("save-all flush", 25)
    send("stop", 0)
    try:
        proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        proc.kill()

    region = WORLD / "world" / "region"
    files = sorted(region.glob("*.mca")) if region.is_dir() else []
    print("region dir:", region)
    print("region files:", len(files), [f.name for f in files][:8])
    if not files:
        sys.exit("no region files were written; last lines:\n" + "\n".join(lines[-25:]))

if __name__ == "__main__":
    main()
