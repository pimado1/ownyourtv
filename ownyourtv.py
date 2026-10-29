#!/usr/bin/env python3
"""OwnYourTV from a computer: the same catalogue, the same safety rules and the same home screen as the TV app.

    python3 ownyourtv.py <tv ip>                 a menu: optimise, choose apps, home screen, undo
    python3 ownyourtv.py <tv ip> optimise        one click: verified ads, tracking, clutter off, the OwnYourTV home,
                                                 faster animations
    python3 ownyourtv.py <tv ip> list            what can be switched off, and what already is
    python3 ownyourtv.py <tv ip> off <app> ...   switch apps off (package names from "list")
    python3 ownyourtv.py <tv ip> on <app> ...    switch apps back on
    python3 ownyourtv.py <tv ip> home            install OwnYourTV and make its home screen the TV's home
    python3 ownyourtv.py <tv ip> undo            put back everything this script changed

Add --yes to skip the questions. Needs Python 3.8+ and adb (Android platform-tools). Apps are switched off for
the user, never uninstalled. What this script changes is recorded in ~/.ownyourtv/<tv>.json, so undo can put
it back.
"""
import json
import re
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
CATALOG = HERE / "data" / "catalog.json"
TVS = HERE / "app" / "src" / "main" / "assets" / "tvs.json"
STATE_DIR = Path.home() / ".ownyourtv"
APK_URL = "https://github.com/pimado1/ownyourtv/releases/latest/download/OwnYourTV.apk"
PKG = "app.ownyourtv"
HOME = f"{PKG}/.home.HomeActivity"
KEY_SERVICE = f"{PKG}/.home.HomeKeyService"  # versions before 1.2 only
KEYS_PKG = "app.ownyourtv.keys"
KEYS_SERVICE = "app.ownyourtv.keys/app.ownyourtv.keys.KeyService"
STOCK_HOMES = ["com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher"]
WIZARD = "com.google.android.tungsten.setupwraith"
ANIM_KEYS = ["window_animation_scale", "transition_animation_scale", "animator_duration_scale"]

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
# Kept identical to Catalog.NEVER and Catalog.NO_TICK: never offered even when a tested list switched it off (a TV
# stuck on its boot logo), and offered but never ticked (crash-loops some builds of the same model).
NEVER = re.compile(r"^com\.android\.sdksandbox$")
NO_TICK = re.compile(r".*\.partnercustomizer(\..*)?$")
GUESS = [
    ("tracking", re.compile(r"(^|\.)(analytics|telemetry|tracking|tracker|logkit|logcollect|feedback|crashreport|diagnos|"
                            r"usagestat|statistic|bigdata|dataservice|acr|samba)", re.IGNORECASE)),
    ("ads", re.compile(r"(^|\.)(ads?|adservice|advert|promo|promotion|sponsor|recommend(ation)?s?|partnercustomizer|demo|"
                       r"exhibit|retail|storemode|messagebox|pushservice|marketing|channelplus|fastchannel)", re.IGNORECASE)),
]
GROUPS = [("ads", "Ads"), ("tracking", "Tracking"), ("extras", "Clutter"), ("apps", "Preinstalled apps")]


class Tv:
    def __init__(self, target):
        self.serial = target if (":" in target or target.startswith("emulator-")) else f"{target}:5555"
        self.state_file = STATE_DIR / (self.serial.replace(":", "_") + ".json")
        self.state = json.loads(self.state_file.read_text()) if self.state_file.exists() else {}

    def adb(self, *args, timeout=120):
        r = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, text=True, timeout=timeout)
        return (r.stdout + r.stderr).strip()

    def sh(self, cmd):
        return self.adb("shell", cmd)

    def connect(self):
        if not self.serial.startswith("emulator-"):
            subprocess.run(["adb", "connect", self.serial], capture_output=True, text=True, timeout=30)
        for i in range(40):
            state = self.adb("get-state")
            if state == "device":
                return True
            if i == 0:
                print("Waiting for the TV: accept \"Allow USB debugging?\" on screen (tick Always allow).")
            time.sleep(1.5)
            if not self.serial.startswith("emulator-"):
                subprocess.run(["adb", "connect", self.serial], capture_output=True, text=True, timeout=30)
        print("The TV did not answer. Check its IP address, that USB debugging is on, then run this again.")
        return False

    def save(self):
        STATE_DIR.mkdir(exist_ok=True)
        self.state_file.write_text(json.dumps(self.state, indent=1, sort_keys=True))

    def packages(self, flag=""):
        return {l[len("package:"):].strip() for l in self.sh(f"pm list packages {flag}").splitlines() if l.startswith("package:")}

    def home_now(self):
        out = self.sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        return out.splitlines()[-1].strip() if out else ""


def detect(tv):
    """The database TV this set is, matched on what getprop reports, as the app does; and its brand's lists."""
    db = json.loads(TVS.read_text())
    props = " ".join(tv.sh(f"getprop {k}") for k in ("ro.product.model", "ro.product.device", "ro.product.name",
                                                      "ro.product.board", "ro.hardware", "ro.build.fingerprint")).lower()
    brand = (tv.sh("getprop ro.product.brand") or tv.sh("getprop ro.product.manufacturer")).strip().lower()
    best, score = None, 0
    for t in db["tvs"]:
        sc = sum(len(k) for k in t["m"] if re.search(r"(^|[^a-z0-9])" + re.escape(k) + r"($|[^a-z0-9])", props))
        if sc and brand and brand in t["b"]:
            sc += 2
        if sc > score:
            best, score = t, sc
    names = db["pkgs"]
    known = None
    if best and score >= 5:
        known = {"name": best["n"], "flags": set(best["f"]), "home": best["home"],
                 "off": {names[i] for i in best["off"]}, "rec": {names[i] for i in best["rec"]}, "keep": {names[i] for i in best["keep"]}}
    b = next((v for k, v in db["brands"].items() if brand and (k == brand or k.startswith(brand + " "))), None)
    group = None
    if b:
        group = {"seen": {names[i] for i in b["seen"]}, "rec": {names[i] for i in b["rec"]}, "keep": {names[i] for i in b["keep"]}}
    kinds = dict(zip(names, db["kinds"]))
    return known, group, kinds


def items(tv):
    cat = json.loads(CATALOG.read_text())["packages"]
    known, group, kinds = detect(tv)
    installed, off = tv.packages(), tv.packages("-d")
    keep = (known or {}).get("keep", set()) | (set() if known else (group or {}).get("keep", set()))
    out = []
    for p in sorted(installed):
        if p == PKG or p in keep or p in STOCK_HOMES or p == WIZARD or NEVER.match(p):
            continue
        if known and p in known["off"]:
            k = cat.get(p, {}).get("k") or kinds.get(p, "extras")
            out.append({"pkg": p, "kind": k, "rec": p in known["rec"] and k != "apps", "verified": True, "off": p in off})
        elif p in cat:
            e = cat[p]
            out.append({"pkg": p, "kind": e["k"], "rec": e["r"], "verified": bool(e.get("v")), "off": p in off})
        elif group and p in group["seen"] and not PROTECTED.match(p):
            k = kinds.get(p, "extras")
            out.append({"pkg": p, "kind": k, "rec": not known and p in group["rec"] and k != "apps", "verified": False, "off": p in off})
        elif not PROTECTED.match(p):
            for kind, rx in GUESS:
                if rx.search(p):
                    out.append({"pkg": p, "kind": kind, "rec": False, "verified": False, "off": p in off, "guessed": True})
                    break
    for i in out:
        if NO_TICK.match(i["pkg"]):
            i["rec"] = False
    return out


def ask(question, yes):
    if yes:
        return True
    return input(f"{question} [y/N] ").strip().lower() in ("y", "yes", "o", "oui")


def show(found):
    n = 0
    for kind, title in GROUPS:
        group = [i for i in found if i["kind"] == kind]
        if not group:
            continue
        print(f"\n{title}")
        for i in group:
            n += 1
            i["n"] = n
            mark = "off" if i["off"] else ("[x]" if i["rec"] else "[ ]")
            note = "checked on a test TV" if i["verified"] else ("spotted by its name" if i.get("guessed") else "")
            print(f"  {n:>3}  {mark}  {i['pkg']:<52} {note}")
    print("\n[x] recommended   off already switched off")


def switch_off(tv, pkgs):
    done = set(tv.state.get("off", []))
    for p in pkgs:
        if NEVER.match(p) or PROTECTED.match(p) and p not in json.loads(CATALOG.read_text())["packages"]:
            print(f"skip {p}: protected, the TV needs it")
            continue
        ok = "disabled" in tv.sh(f"pm disable-user --user 0 {p}")
        print(("off  " if ok else "FAIL ") + p)
        if ok:
            done.add(p)
    tv.state["off"] = sorted(done)
    tv.save()


def switch_on(tv, pkgs):
    done = set(tv.state.get("off", []))
    for p in pkgs:
        ok = "enabled" in tv.sh(f"pm enable {p}")
        print(("on   " if ok else "FAIL ") + p)
        if ok:
            done.discard(p)
    tv.state["off"] = sorted(done)
    tv.save()


def install_app(tv, apk=None):
    if PKG in tv.packages():
        print("OwnYourTV is installed.")
        return True
    if apk is None:
        print("Downloading OwnYourTV…")
        apk = Path(tempfile.gettempdir()) / "OwnYourTV.apk"
        urllib.request.urlretrieve(APK_URL, apk)
    out = tv.adb("install", "-r", str(apk), timeout=300)
    if "UPDATE_INCOMPATIBLE" in out:
        # 1.2 is signed with OwnYourTV's own key; an earlier test build can only be replaced, not updated.
        print("An earlier OwnYourTV, signed with another key, is on the TV: it has to be uninstalled first.")
        if not ask("Uninstall it and install the new one?", False):
            return False
        # Bring a stock home back first: with the old OwnYourTV as the only home, uninstalling it would leave the
        # TV on a black screen until the new one is set up.
        off = tv.packages("-d")
        for p in STOCK_HOMES:
            if p in off:
                tv.sh(f"pm enable {p}")
        tv.adb("uninstall", PKG)
        out = tv.adb("install", str(apk), timeout=300)
    print(out.splitlines()[-1] if out else "")
    return PKG in tv.packages()


def make_home(tv, apk=None):
    if not install_app(tv, apk):
        print("OwnYourTV could not be installed.")
        return False
    # Android lets only the app itself switch its home entry on: ask it, then give it the Home role.
    tv.sh(f"am start -f 0x20000000 -n {PKG}/.MainActivity --ez computer_home true --ez on true")
    time.sleep(2)
    tv.sh(f"pm grant {PKG} android.permission.BLUETOOTH_CONNECT; pm grant {PKG} android.permission.READ_TV_LISTINGS; "
          f"cmd appops set {PKG} ACCESS_RESTRICTED_SETTINGS allow")
    # Versions before 1.2 had a remote-key service; it is gone, so its entry in the TV's settings goes too.
    old = tv.sh("settings get secure enabled_accessibility_services").strip()
    kept = [s for s in old.split(":") if s and s != "null" and s != KEY_SERVICE]
    if ":".join(kept) != old and old not in ("", "null"):
        tv.sh("settings put secure enabled_accessibility_services " + ":".join(kept) if kept else "settings delete secure enabled_accessibility_services")
    tv.sh(f"cmd package set-home-activity --user 0 {HOME}")
    if not home_opens(tv):
        known, _, _ = detect(tv)
        flags = known["flags"] if known else set()
        homes = tv.sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
        if "home_allowlist" in flags or "wizard_returns" in flags or ("FallbackHome" not in homes and ".settings" not in homes):
            tv.sh(f"am start -f 0x20000000 -n {PKG}/.MainActivity --ez computer_home true --ez on false")
            print("This TV cannot safely take another home: it keeps its own. Everything else still works.")
            return False
        enabled = tv.packages("-e")
        if WIZARD in enabled and "setupwraith" in tv.sh("dumpsys wifi | grep -o 'cname=[^ ]*'"):
            tv.sh(f"am start -f 0x20000000 -n {PKG}/.MainActivity --ez computer_home true --ez on false")
            print("The TV's Wi-Fi was saved by its setup wizard, which the home step has to switch off (Android would")
            print("delete the network with it). On the TV: Settings > Network > your Wi-Fi > Forget, join it again,")
            print("then run this again. Or use Ethernet.")
            return False
        # The wizard goes before the stock home: with the launcher off and the wizard on, the TV lands on the
        # wizard's recovery screen.
        order = ([WIZARD] if WIZARD in enabled else []) + [p for p in dict.fromkeys(STOCK_HOMES + ([known["home"]] if known else [])) if p in enabled]
        done = [p for p in order if "disabled" in tv.sh(f"pm disable-user --user 0 {p}")]
        tv.sh(f"cmd package set-home-activity --user 0 {HOME}")
        if not home_opens(tv):
            for p in sorted(done, key=lambda x: x == WIZARD):
                tv.sh(f"pm enable {p}")
            tv.sh(f"am start -f 0x20000000 -n {PKG}/.MainActivity --ez computer_home true --ez on false")
            tv.sh("input keyevent HOME")
            print("This TV does not let another app be its home: its own home is back, nothing else changed.")
            return False
        tv.state["home_off"] = sorted(set(tv.state.get("home_off", [])) | set(done))
        install_keys(tv)
    tv.state["home"] = True
    tv.save()
    print("The OwnYourTV home is the TV's home.")
    return True


def install_keys(tv):
    """The remote's app and gear buttons went to the stock home, now off: OwnYourTV's buttons module answers them.
    It ships inside the app (assets/keys.apk), so it is taken from the app on the TV, the same version."""
    import zipfile
    base = tv.sh(f"pm path {PKG}").strip().splitlines()[0].split("package:", 1)[-1]
    tmp = Path(tempfile.gettempdir())
    tv.adb("pull", base, str(tmp / "OwnYourTV-installed.apk"), timeout=120)
    try:
        with zipfile.ZipFile(tmp / "OwnYourTV-installed.apk") as z:
            (tmp / "OwnYourTV-keys.apk").write_bytes(z.read("assets/keys.apk"))
    except (OSError, KeyError, zipfile.BadZipFile):
        print("This OwnYourTV has no buttons module (1.8.7 and later do): the remote's app buttons may not work.")
        return False
    tv.adb("install", "-r", str(tmp / "OwnYourTV-keys.apk"), timeout=120)
    old = [x for x in tv.sh("settings get secure enabled_accessibility_services").strip().split(":")
           if x and x != "null" and not x.startswith(KEYS_PKG + "/")]
    tv.sh(f"cmd appops set {KEYS_PKG} AUTO_START allow; settings put secure enabled_accessibility_services "
          + ":".join(old + [KEYS_SERVICE]) + "; settings put secure accessibility_enabled 1")
    print("The remote's app and gear buttons work with the OwnYourTV home.")
    return True


def home_opens(tv):
    """The TV both picks the OwnYourTV home and really shows it when asked for the home."""
    if tv.home_now() != HOME:
        return False
    shown = tv.sh("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME")
    time.sleep(0.8)
    top = tv.sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")
    return "HomeActivity" in shown and f"{PKG}/.home.HomeActivity" in top


def undo_home(tv):
    for p in sorted(tv.state.get("home_off", []), key=lambda x: x == WIZARD):
        tv.sh(f"pm enable {p}")
    off = tv.packages("-d")
    for p in STOCK_HOMES:
        if p in off:
            tv.sh(f"pm enable {p}")
    if not any(p in tv.packages("-e") for p in STOCK_HOMES):
        print("No other home on this TV: the OwnYourTV home stays, so the TV is never left without one.")
        return
    tv.sh(f"am start -f 0x20000000 -n {PKG}/.MainActivity --ez computer_home true --ez on false")
    time.sleep(2)
    services = [s for s in tv.sh("settings get secure enabled_accessibility_services").strip().split(":")
                if s and s != "null" and s != KEY_SERVICE and not s.startswith(KEYS_PKG + "/")]
    tv.sh("settings put secure enabled_accessibility_services " + ":".join(services) if services else "settings delete secure enabled_accessibility_services")
    # The stock home answers the remote's buttons again.
    tv.sh(f"pm uninstall {KEYS_PKG}")
    tv.state.pop("home_off", None)
    tv.state.pop("home", None)
    tv.save()
    print("The original home is back.")


def faster(tv):
    if "anim" not in tv.state:
        tv.state["anim"] = [tv.sh(f"settings get global {k}").strip() for k in ANIM_KEYS]
        tv.save()
    tv.sh("; ".join(f"settings put global {k} 0.5" for k in ANIM_KEYS))
    print("Faster animations.")


def optimise(tv, yes):
    found = items(tv)
    todo = [i["pkg"] for i in found if i["rec"] and not i["off"] and i["kind"] != "apps"]
    print(f"Optimise: switch off {len(todo)} verified app(s), make the OwnYourTV home the home, faster animations.")
    if not ask("Go ahead?", yes):
        print("Nothing changed.")
        return
    switch_off(tv, todo)
    make_home(tv)
    faster(tv)


def undo(tv, yes):
    off = tv.state.get("off", [])
    print(f"Undo: switch {len(off)} app(s) back on" + (", bring the original home back" if tv.state.get("home") else "")
          + (", put the animations back" if "anim" in tv.state else "") + ".")
    if not ask("Go ahead?", yes):
        print("Nothing changed.")
        return
    switch_on(tv, off)
    if tv.state.get("home"):
        undo_home(tv)
    if "anim" in tv.state:
        tv.sh("; ".join(f"settings delete global {k}" if v in ("", "null") else f"settings put global {k} {v}"
                        for k, v in zip(ANIM_KEYS, tv.state.pop("anim"))))
        tv.save()
        print("Animations put back.")


def choose(tv):
    known, _, _ = detect(tv)
    print(f"TV recognised: {known['name']}" if known else "TV not in the database yet: only checked and spotted apps are offered.")
    found = items(tv)
    show(found)
    print("\nType the numbers to switch, separated by spaces (an app that is off comes back on), or press Enter.")
    picked = input("> ").split()
    by_n = {str(i["n"]): i for i in found}
    to_off = [by_n[n]["pkg"] for n in picked if n in by_n and not by_n[n]["off"]]
    to_on = [by_n[n]["pkg"] for n in picked if n in by_n and by_n[n]["off"]]
    if to_off:
        switch_off(tv, to_off)
    if to_on:
        switch_on(tv, to_on)
    if not (to_off or to_on):
        print("Nothing changed.")


def menu(tv):
    while True:
        print("\nOwnYourTV\n  1  Optimise in one click\n  2  Choose apps, one by one\n  3  Install the OwnYourTV home"
              "\n  4  Put everything back\n  5  Done (switch USB debugging off)\n  q  Quit")
        c = input("> ").strip().lower()
        if c == "1":
            optimise(tv, False)
        elif c == "2":
            choose(tv)
        elif c == "3":
            make_home(tv)
        elif c == "4":
            undo(tv, False)
        elif c == "5":
            tv.sh("settings put global adb_enabled 0")
            print("USB debugging is off. See you.")
            return
        elif c in ("q", ""):
            return


def main(argv):
    args = [a for a in argv if not a.startswith("--")]
    yes = "--yes" in argv
    if not args:
        print(__doc__)
        return 1
    tv = Tv(args[0])
    if not tv.connect():
        return 1
    cmd, rest = (args[1] if len(args) > 1 else "menu"), args[2:]
    if cmd == "menu":
        menu(tv)
    elif cmd == "optimise":
        optimise(tv, yes)
    elif cmd == "list":
        known, _, _ = detect(tv)
        print(f"TV recognised: {known['name']}" if known else "TV not in the database yet.")
        show(items(tv))
    elif cmd == "off":
        switch_off(tv, rest)
    elif cmd == "on":
        switch_on(tv, rest)
    elif cmd == "home":
        make_home(tv, Path(rest[0]) if rest else None)
    elif cmd == "undo":
        undo(tv, yes)
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
