#!/usr/bin/env python3
"""OwnYourTV from a computer: the same catalogue and the same safety rules as the TV app, over adb.

    python3 ownyourtv.py <tv ip>            show what can be switched off, then ask before changing anything
    python3 ownyourtv.py <tv ip> --apply    switch off what is recommended (ads and tracking checked on a TV)
    python3 ownyourtv.py <tv ip> --undo     switch back on everything this script switched off

Needs adb (Android platform-tools). Apps are disabled for the user, never uninstalled. What was switched off is
recorded in ~/.ownyourtv/<ip>.json so --undo can bring it back.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CATALOG = HERE / "data" / "catalog.json"
STATE = Path.home() / ".ownyourtv"

# Kept identical to Catalog.PROTECTED in the app: never offered, whatever the catalogue or the name says.
PROTECTED = re.compile(
    r"^(android|com\.android\.(systemui|shell|phone|settings|tv\.settings|vending|bluetooth|nfc|networkstack.*|wifi.*|"
    r"permissioncontroller|packageinstaller|ext\.services|ext\.shared|providers\..*|inputmethod.*|launcher.*|"
    r"externalstorage|documentsui|media.*|webview|server\..*|se|location.*|keychain|certinstaller)|"
    r"android\.ext\..*|com\.google\.android\.(gms|gsf|modulemetadata|permissioncontroller|packageinstaller|"
    r"ext\..*|overlay\..*|webview|tvlauncher|leanbacklauncher|apps\.tv\.launcherx|tungsten\.setupwraith|"
    r"tv\.remote\..*|inputmethod.*|tts|marvin\..*|configupdater|networkstack.*|tv)|"
    r".*(factory|\.fac\b|\.fac\.|keyboard|inputmethod|ime\b|bluetooth|remote|tvinput|tv\.input|tuner|livetv|tvservice|"
    r"tv\.service|hdmi|cec|middleware|systemserver|systemui|provider|setup|wizard|ota|update(r)?\b|recovery|"
    r"mediatek\.(tis|tv\..*)|cltv|downloader|filelinked|sendfiles|ownyourtv).*)$",
    re.IGNORECASE,
)
GUESS = [
    ("tracking", re.compile(r"(^|\.)(analytics|telemetry|tracking|tracker|logkit|logcollect|feedback|crashreport|diagnos|"
                            r"usagestat|statistic|bigdata|dataservice|acr|samba)", re.IGNORECASE)),
    ("ads", re.compile(r"(^|\.)(ads?|adservice|advert|promo|promotion|sponsor|recommend(ation)?s?|partnercustomizer|demo|"
                       r"exhibit|retail|storemode|messagebox|pushservice|marketing|channelplus|fastchannel)", re.IGNORECASE)),
]
KINDS = {"ads": "Ads", "tracking": "Tracking", "extras": "Clutter", "apps": "Preinstalled apps"}


def adb(host, *args):
    r = subprocess.run(["adb", "-s", f"{host}:5555", *args], capture_output=True, text=True)
    return r.stdout.strip() + r.stderr.strip()


def shell(host, cmd):
    return adb(host, "shell", cmd)


def packages(host, flag=""):
    return {l.removeprefix("package:").strip() for l in shell(host, f"pm list packages {flag}").splitlines() if l.startswith("package:")}


def items(host):
    cat = json.loads(CATALOG.read_text())["packages"]
    installed, off = packages(host), packages(host, "-d")
    out = []
    for p in sorted(installed):
        if p in cat:
            e = cat[p]
            out.append({"pkg": p, "kind": e["k"], "rec": e["r"], "verified": bool(e.get("v")), "off": p in off})
        elif not PROTECTED.match(p):
            for kind, rx in GUESS:
                if rx.search(p):
                    out.append({"pkg": p, "kind": kind, "rec": False, "verified": False, "off": p in off, "guessed": True})
                    break
    return out


def record(host):
    STATE.mkdir(exist_ok=True)
    return STATE / f"{host}.json"


def main():
    if len(sys.argv) < 2 or sys.argv[1].startswith("-"):
        print(__doc__)
        return 1
    host, mode = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "")
    subprocess.run(["adb", "connect", f"{host}:5555"], capture_output=True, text=True)
    if adb(host, "get-state") != "device":
        print("Accept \"Allow USB debugging?\" on the TV (tick Always allow), then run this again.")
        return 1
    rec_file = record(host)
    ours = set(json.loads(rec_file.read_text())) if rec_file.exists() else set()

    if mode == "--undo":
        for p in sorted(ours):
            print(("on   " if "enabled" in shell(host, f"pm enable {p}") else "FAIL ") + p)
        rec_file.write_text("[]")
        return 0

    found = items(host)
    for kind, title in KINDS.items():
        group = [i for i in found if i["kind"] == kind]
        if not group:
            continue
        print(f"\n{title}")
        for i in group:
            mark = "off " if i["off"] else ("[x] " if i["rec"] else "[ ] ")
            note = "checked on a test TV" if i["verified"] else ("spotted by its name" if i.get("guessed") else "")
            print(f"  {mark}{i['pkg']}  {note}")
    todo = [i["pkg"] for i in found if i["rec"] and not i["off"]]
    print(f"\n{len(todo)} recommended app(s) to switch off.")
    if not todo:
        return 0
    if mode != "--apply":
        answer = input("Switch them off now? [y/N] ").strip().lower()
        if answer not in ("y", "yes", "o", "oui"):
            print("Nothing changed.")
            return 0
    for p in todo:
        ok = "disabled" in shell(host, f"pm disable-user --user 0 {p}")
        print(("off  " if ok else "FAIL ") + p)
        if ok:
            ours.add(p)
    rec_file.write_text(json.dumps(sorted(ours)))
    print(f"\nDone. Undo any time: python3 ownyourtv.py {host} --undo")
    print("Then switch USB debugging off on the TV (Developer options).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
