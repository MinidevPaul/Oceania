import hashlib
import json
import os
import subprocess
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

def java_from_gradle_properties():
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("org.gradle.java.home="):
                return os.path.join(line.split("=", 1)[1].strip(), "bin", "java.exe")
    return "java"

def fetch_json(url):
    with urllib.request.urlopen(url) as r:
        return json.load(r)

def main():
    version = sys.argv[1] if len(sys.argv) > 1 else "26.3"
    java = sys.argv[2] if len(sys.argv) > 2 else java_from_gradle_properties()
    out = os.path.join(ROOT, "tools", "vanilla-data-" + version)
    work = os.path.join(ROOT, "tools", "vanilla-server-" + version)
    os.makedirs(work, exist_ok=True)

    manifest = fetch_json(MANIFEST)
    entry = next((v for v in manifest["versions"] if v["id"] == version), None)
    if entry is None:
        raise SystemExit("version %s is not in the manifest" % version)
    meta = fetch_json(entry["url"])
    server = meta["downloads"]["server"]
    jar = os.path.join(work, "server-%s.jar" % version)
    if not (os.path.isfile(jar) and hashlib.sha1(open(jar, "rb").read()).hexdigest() == server["sha1"]):
        print("downloading", server["url"])
        urllib.request.urlretrieve(server["url"], jar)
        if hashlib.sha1(open(jar, "rb").read()).hexdigest() != server["sha1"]:
            raise SystemExit("sha1 mismatch on the server jar")
    print("server jar ok:", jar, "(java %s)" % meta.get("javaVersion", {}).get("majorVersion"))

    cmd = [java, "-DbundlerMainClass=net.minecraft.data.Main", "-jar", jar, "--all", "--output", out]
    print(" ".join(cmd))
    subprocess.run(cmd, cwd=work, check=True)
    n = sum(len(files) for _, _, files in os.walk(os.path.join(out, "data")))
    print("done:", n, "data files under", out)

if __name__ == "__main__":
    main()
