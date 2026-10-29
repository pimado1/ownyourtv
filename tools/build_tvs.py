"""Builds app/src/main/assets/tvs.json, OwnYourTV's TV database, from CleanYourTV's examples (MIT, see NOTICE.md).

    python3 tools/build_tvs.py <cleanyourtv checkout>

For every TV a list was tested on: the names it reports (to recognise it), its stock home, what the list switched
off and what it kept on purpose, and the home-screen quirks its notes describe. Per brand: what lists of that brand
switch off, for a TV of that brand that is not in the database itself.
"""
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

SRC = Path(sys.argv[1])
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/tvs.json"
PKG = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
TOKEN = re.compile(r"\b[a-z][a-z0-9_]*(?:\.[A-Za-z0-9_]+){1,}\b")
KEEPWORDS = re.compile(r"left enabled|kept|keep |keeps|not touched|do not disable|don't disable|never disable|"
                       r"never launch|on purpose|tv needs|needs them", re.I)
CHOICE = re.compile(r"choice|owner|not used|personal|unused here|household|by preference", re.I)
# The home step handles these itself; a list never switches them off through OwnYourTV.
HOME_PKGS = {"com.google.android.tungsten.setupwraith", "com.google.android.apps.tv.launcherx",
             "com.google.android.tvlauncher", "com.google.android.leanbacklauncher", "com.amazon.tv.launcher"}
HARD_CORE = re.compile(r"^(android|android\.ext\..*|com\.android\.(systemui|shell|phone|settings|tv\.settings|"
                       r"permissioncontroller|packageinstaller|ext\.services|ext\.shared|providers\.(?!calendar|contacts|"
                       r"userdictionary|blockednumber).*)|com\.google\.android\.(gms|gsf|modulemetadata|permissioncontroller|"
                       r"packageinstaller|ext\..*|overlay\.modules.*)|com\.android\.vending|com\.cltv\.mal|"
                       r"com\.mediatek\.(tis|tv\.service|tv\.agent)|com\.android\.sdksandbox)$")
TRACK = r"telemetr|analytic|diagnos|\blog|feedback|privacy sandbox|adservices|personali[sz]|federated|crash|report|statist|track|usage|data collect|acr\b|samba"
ADS = r"promo|\bads?\b|advert|sponsor|push|demo|fast|channel|recommend|banner|message|popup|content row|store|shopping|waterfall|deal|patchwall"
APPS = r"youtube|netflix|prime|disney|music|alexa|assistant|voice|cast|airplay|miracast|mirror|browser|game|player|video|photo|gallery|weather|keyboard|karaoke|fitness|sport|news|radio|spotify|twitch|plex|kodi"
NO_TICK = re.compile(r"update|upgrad|\\bota\\b|\\.ota|fota|firmware", re.I)
# Crash-loops some builds of the same model (tvlauncher.widget lives there): offered, never ticked.
RISKY = re.compile(r"\.partnercustomizer(\.|$)")
GENERIC = {"android", "tv", "smart", "google", "the", "and", "with", "for", "pro", "max", "plus", "mini", "inch", "series",
           "box", "stick", "uhd", "hdr", "led", "oled", "qled", "mini-led", "europe", "global", "india", "china", "us", "uk",
           "eu", "gb", "ram", "rom", "first", "second", "third", "gen", "new", "old", "edition"}


def kind(text, desc, section):
    d = desc.lower()
    t = f"{desc} {section}".lower()
    if CHOICE.search(section) or CHOICE.search(desc):
        return "apps"
    for k, rx in (("tracking", TRACK), ("apps", APPS), ("ads", ADS)):
        if re.search(rx, d):
            return k
    for k, rx in (("tracking", TRACK), ("ads", ADS), ("apps", APPS)):
        if re.search(rx, t):
            return k
    return "extras"


def tokens(text):
    """Model codes, product names and codenames: what getprop on the TV is likely to contain."""
    out = set()
    for raw in re.findall(r"[A-Za-z0-9][A-Za-z0-9_\-]{1,}", text):
        t = raw.lower().strip("-_")
        if t in GENERIC or len(t) < 3 or t.isdigit() or re.fullmatch(r"\d{4}", t) or re.fullmatch(r"\d+(gb|k|mb|hz)", t):
            continue
        if re.fullmatch(r"\d{4}-\d\d-\d\d", t) or "google-tv" in t or t.count("-") > 2:
            continue
        if re.search(r"\d", t):
            out.add(t)
    # Codenames are plain words, so they are only taken when the text names them as such.
    for name in re.findall(r"(?:codename|device|board)\s+[`\"']?([a-z][a-z0-9_]{2,})", text, re.I):
        out.add(name.lower())
    for name in re.findall(r"[(,]\s*([a-z][a-z0-9_]{3,})\s*[,)]", text):
        if name not in GENERIC:
            out.add(name)
    return sorted(out)


pkg_kind = defaultdict(Counter)
brand_seen = defaultdict(Counter)
brand_keep = defaultdict(Counter)
tvs = []
for d in sorted(p for p in (SRC / "examples").iterdir() if (p / "debloat.txt").exists()):
    meta = json.loads((d / "tv.json").read_text()) if (d / "tv.json").exists() else {}
    text = (d / "debloat.txt").read_text(errors="ignore")
    head = text.splitlines()[0] if text.startswith("#") else ""
    brand = (meta.get("brand") or d.name.split("-")[0]).lower()
    off, keep = {}, set()
    section, in_keep = "", False
    stock = None
    live = None
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#"):
            t = line.lstrip("#").strip()
            if not t:
                in_keep = False
                continue
            m = re.search(r"stock (?:launcher|home)[^:]*:\s*`?([a-z][\w.]+)", t, re.I)
            if m and stock is None and PKG.match(m.group(1)):
                stock = m.group(1)
            m = re.search(r"live tv[^:]*:\s*`?([a-z][\w.]+)", t, re.I)
            if m and live is None and PKG.match(m.group(1).rstrip(".")):
                live = m.group(1).rstrip(".")
            if KEEPWORDS.search(t):
                in_keep = True
            if in_keep:
                keep.update(x for x in TOKEN.findall(t) if PKG.match(x))
            if len(t) < 100:
                section = t
            continue
        name, _, comment = line.partition("#")
        name = name.strip().split()[0] if name.strip() else ""
        if not PKG.match(name) or name in HOME_PKGS or HARD_CORE.match(name):
            continue
        k = kind(text, comment.strip(), section)
        off[name] = k
        pkg_kind[name][k] += 1
        brand_seen[brand][name] += 1
    keep -= set(off)
    for p in keep:
        brand_keep[brand][p] += 1
    if stock is None:
        stock = "com.google.android.apps.tv.launcherx" if "google tv" in meta.get("os", "").lower() else "com.google.android.tvlauncher"
    flags = []
    low = text.lower()
    if "com.google.android.tungsten.setupwraith" in [l.split("#")[0].strip() for l in text.splitlines()]:
        flags.append("wizard_off_tested")
    if re.search(r"re-?enables setupwraith|setupwraith (?:comes|came) back", low):
        flags.append("wizard_returns")
    if "allowlist" in low or "isthirdpartyhomeallowed" in low:
        flags.append("home_allowlist")
    if "castinfoprovider" in low or "untrusted device" in low:
        flags.append("cast_needs_wizard")
    if re.search(r"no fallbackhome|without a fallbackhome|boot never completes", low):
        flags.append("no_fallback_home")
    if re.search(r"keeps? (?:the )?stock launcher|stock launcher (?:stays|kept)|home (?:stays|was left)", low):
        flags.append("stock_home_kept")
    tvs.append({"id": d.name, "name": meta.get("tv", d.name), "brand": brand, "os": meta.get("os", ""),
                "match": tokens(meta.get("tv", "") + "\n" + head), "home": stock, "live": live, "flags": flags,
                "off": off, "keep": sorted(keep)})

names = sorted({p for t in tvs for p in t["off"]} | {p for t in tvs for p in t["keep"]})
idx = {p: i for i, p in enumerate(names)}
kinds = [pkg_kind[p].most_common(1)[0][0] if pkg_kind[p] else "extras" for p in names]
out_tvs = []
for t in tvs:
    # Updaters are offered but never ticked: stopping updates stays the owner's choice.
    rec = [idx[p] for p, k in t["off"].items() if k in ("ads", "tracking", "extras") and not NO_TICK.search(p)
           and not RISKY.search(p)]
    out_tvs.append({"id": t["id"], "n": t["name"], "b": t["brand"], "os": t["os"], "m": t["match"], "home": t["home"],
                    "live": t["live"], "f": t["flags"], "off": sorted(idx[p] for p in t["off"]), "rec": sorted(rec),
                    "keep": sorted(idx[p] for p in t["keep"])})
brands = {}
for b, seen in brand_seen.items():
    lists = sum(1 for t in tvs if t["brand"] == b)
    rec = [idx[p] for p, n in seen.items() if kinds[idx[p]] in ("ads", "tracking") and n >= 2 and not NO_TICK.search(p)
           and not RISKY.search(p) and brand_keep[b][p] * 4 < n]
    brands[b] = {"lists": lists, "seen": sorted(idx[p] for p in seen), "rec": sorted(rec),
                 "keep": sorted(idx[p] for p, n in brand_keep[b].items() if n * 2 >= seen.get(p, 0) and n)}
OUT.write_text(json.dumps({"source": "CleanYourTV examples (MIT)", "pkgs": names, "kinds": kinds, "tvs": out_tvs,
                           "brands": brands}, separators=(",", ":")))
print(len(out_tvs), "TVs,", len(names), "packages,", len(brands), "brands,", OUT.stat().st_size // 1024, "KB")
print(Counter(f for t in tvs for f in t["flags"]))
