"""Writes one page per TV brand, in English (docs/tv/) and French (docs/fr/tv/).

Each page answers what people search for ("remove ads TCL TV"): which ad and tracking apps that maker puts on its
TVs, what to know before starting on that brand, how to install OwnYourTV, and the TVs of that brand owners shared.
The app names come from what OwnYourTV switches off; nothing here is a count copied from another list.
Run by tools/site_version.py.
"""
import html
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent / "docs"
SITE = "https://ownyour.tv/"
CODE = "8225348"

# App: (package, English description, French description)
BRANDS = {
    "tcl": {
        "name": "TCL",
        "apps": [
            ("com.tcl.esticker", "On-screen stickers and banners", "Bandeaux et autocollants publicitaires à l'écran"),
            ("com.tcl.messagebox", "Promotional messages", "Messages promotionnels"),
            ("com.tcl.tv.tclhome_passive", "TCL home recommendations", "Recommandations de l'accueil TCL"),
            ("com.tcl.channelplus", "TCL Channel, sponsored channels", "TCL Channel, chaînes sponsorisées"),
            ("com.tcl.dashboard", "Promoted dashboard", "Tableau de bord promotionnel"),
            ("com.tcl.usercenter", "TCL account and usage tracking", "Compte TCL et suivi d'utilisation"),
            ("com.tcl.bi", "Usage statistics sent to TCL", "Statistiques d'utilisation envoyées à TCL"),
            ("com.tcl.logkit", "Logs sent to TCL", "Journaux envoyés à TCL"),
            ("com.google.android.tvrecommendations", "Google's sponsored rows", "Rangées sponsorisées de Google"),
        ],
        "notes_en": [
            "Most TCL TVs sold since 2021 run Google TV: OwnYourTV can switch the stock home off and put its own in its place.",
            "On some Google TV models, the Wi-Fi network was saved by the setup wizard. The app then asks you once to forget it and join it again, so the TV keeps its internet.",
            "Live TV, HDMI inputs and the TCL remote keep working: only ad and tracking apps are switched off.",
        ],
        "notes_fr": [
            "La plupart des TV TCL vendues depuis 2021 sont des Google TV : OwnYourTV peut couper l'accueil d'origine et mettre le sien à la place.",
            "Sur certains modèles Google TV, le Wi-Fi a été enregistré par l'assistant d'installation. L'appli te demande alors une fois de l'oublier et de t'y reconnecter, pour que la TV garde internet.",
            "La TV en direct, les entrées HDMI et la télécommande TCL continuent de marcher : seules les applis de pub et de pistage sont coupées.",
        ],
    },
    "xiaomi": {
        "name": "Xiaomi",
        "apps": [
            ("com.xm.webcontent", "Web content and ads", "Contenus web et publicités"),
            ("com.mitv.tvhome.atv", "PatchWall recommendations", "Recommandations PatchWall"),
            ("com.mitv.tvhome.michannel", "Mi Channel, sponsored channels", "Mi Channel, chaînes sponsorisées"),
            ("com.mitv.tvhome.mitvplus", "Mi TV+ promoted content", "Contenus mis en avant par Mi TV+"),
            ("com.miui.tv.analytics", "Usage statistics sent to Xiaomi", "Statistiques d'utilisation envoyées à Xiaomi"),
            ("com.xiaomi.statistic", "Xiaomi analytics", "Mesure d'audience Xiaomi"),
            ("com.xiaomi.floatingframe", "Floating promotional frame", "Cadre promotionnel flottant"),
            ("com.google.android.tvrecommendations", "Google's sponsored rows", "Rangées sponsorisées de Google"),
        ],
        "notes_en": [
            "Xiaomi TVs come with Google TV, or with PatchWall, Xiaomi's own home screen with promoted content.",
            "Some Xiaomi models only accept their own home screen. On those, OwnYourTV says so, keeps the stock home and still switches the ads and tracking off.",
            "Tested by an owner on a Xiaomi TV A Pro (MiTV-MZTU0) with Google TV 14: everything worked.",
        ],
        "notes_fr": [
            "Les TV Xiaomi ont Google TV, ou PatchWall, l'accueil maison de Xiaomi avec ses contenus mis en avant.",
            "Certains modèles Xiaomi n'acceptent que leur propre accueil. Sur ceux-là, OwnYourTV le dit, garde l'accueil d'origine et coupe quand même les pubs et le pistage.",
            "Testé par un utilisateur sur une Xiaomi TV A Pro (MiTV-MZTU0) en Google TV 14 : tout a marché.",
        ],
    },
    "sony": {
        "name": "Sony",
        "apps": [
            ("com.sony.dtv.sonyselect", "Sony Select, promoted apps", "Sony Select, applis mises en avant"),
            ("com.sony.dtv.promos", "Sony promotions", "Promotions Sony"),
            ("com.sony.dtv.multiscreendemo", "Store demo", "Démo de magasin"),
            ("com.sony.dtv.demosupport", "Store demo support", "Support de la démo de magasin"),
            ("com.sony.dtv.da.service", "Usage data sent to Sony", "Données d'utilisation envoyées à Sony"),
            ("tv.samba.ssm", "Samba TV, viewing measurement", "Samba TV, mesure de ce que tu regardes"),
            ("com.sonyericsson.idd.agent", "Usage reports", "Rapports d'utilisation"),
            ("com.google.android.tvrecommendations", "Google's sponsored rows", "Rangées sponsorisées de Google"),
        ],
        "notes_en": [
            "Sony Bravia TVs run Android TV or Google TV, often with Samba TV, which identifies what is on screen to measure viewing.",
            "Store demo apps are still installed on many sets sold for home use: they are switched off too.",
            "On a few Bravia models, the stock home has to stay: OwnYourTV then switches the ads off and leaves the home as it is.",
        ],
        "notes_fr": [
            "Les TV Sony Bravia sont sous Android TV ou Google TV, souvent avec Samba TV, qui reconnaît ce qui passe à l'écran pour mesurer l'audience.",
            "Les applis de démo de magasin sont encore installées sur beaucoup de TV vendues aux particuliers : elles sont coupées aussi.",
            "Sur quelques modèles Bravia, l'accueil d'origine doit rester : OwnYourTV coupe alors les pubs et laisse l'accueil tel quel.",
        ],
    },
    "philips": {
        "name": "Philips",
        "apps": [
            ("org.droidtv.nettvrecommender", "Net TV recommendations", "Recommandations Net TV"),
            ("org.droidtv.nettv.market", "Net TV app gallery", "Galerie d'applis Net TV"),
            ("org.droidtv.recommendation", "Philips recommendations", "Recommandations Philips"),
            ("org.droidtv.demome", "Store demo", "Démo de magasin"),
            ("org.droidtv.usagelogger", "Usage logging", "Journal d'utilisation"),
            ("org.droidtv.gel", "Event logging", "Journal des événements"),
            ("org.droidtv.candeebug", "Diagnostics sent to the maker", "Diagnostics envoyés au fabricant"),
            ("com.google.android.tvrecommendations", "Google's sponsored rows", "Rangées sponsorisées de Google"),
        ],
        "notes_en": [
            "Philips Android TVs and Google TVs (made by TP Vision) add Net TV recommendations and their own logging on top of Google's.",
            "On some models, Chromecast needs the setup wizard: OwnYourTV keeps what is needed so casting still works.",
            "Ambilight, live TV and HDMI inputs are not touched.",
        ],
        "notes_fr": [
            "Les TV Philips sous Android TV et Google TV (fabriquées par TP Vision) ajoutent les recommandations Net TV et leurs propres journaux, en plus de ceux de Google.",
            "Sur certains modèles, Chromecast a besoin de l'assistant d'installation : OwnYourTV garde ce qu'il faut pour que le cast marche toujours.",
            "L'Ambilight, la TV en direct et les entrées HDMI ne sont pas touchés.",
        ],
    },
    "hisense": {
        "name": "Hisense",
        "apps": [
            ("com.hisense.service.message", "Promotional messages", "Messages promotionnels"),
            ("com.hisense.business.ad", "Hisense ads", "Publicités Hisense"),
            ("com.hisense.storemode", "Store mode", "Mode magasin"),
            ("com.hisense.demonstrate", "Store demo", "Démo de magasin"),
            ("com.hisense.logupload", "Logs sent to Hisense", "Journaux envoyés à Hisense"),
            ("com.hisense.evservice", "Usage events", "Événements d'utilisation"),
            ("com.google.android.tvrecommendations", "Google's sponsored rows", "Rangées sponsorisées de Google"),
        ],
        "notes_en": [
            "Many Hisense TVs run VIDAA, Hisense's own system, not Android: OwnYourTV cannot be installed on those.",
            "On Hisense TVs with Android TV or Google TV, it switches off Hisense's messages, store demos and logging, and Google's sponsored rows.",
            "To check: Settings › Device preferences › About. If you see Android TV OS or Google TV, it works.",
        ],
        "notes_fr": [
            "Beaucoup de TV Hisense sont sous VIDAA, le système maison de Hisense, et pas sous Android : OwnYourTV ne s'y installe pas.",
            "Sur les TV Hisense sous Android TV ou Google TV, elle coupe les messages Hisense, les démos de magasin, les journaux, et les rangées sponsorisées de Google.",
            "Pour vérifier : Paramètres › Préférences de l'appareil › À propos. Si tu vois Android TV OS ou Google TV, ça marche.",
        ],
    },
}

T = {
    "en": {
        "title": "Remove the ads on a {b} TV · OwnYourTV",
        "desc": "Switch off the ads and tracking on your {b} Android TV or Google TV, from the remote, no computer. What OwnYourTV switches off on {b} TVs, and how.",
        "eye": "{b} TVs", "h1": "Remove the ads on a {b} TV",
        "lead": "OwnYourTV switches off the ads and tracking your {b} TV came with, and gives it a calm home screen. From the remote, no computer. Nothing is deleted: one button puts everything back.",
        "h_apps": "What it switches off on {b} TVs", "apps_note": "The app names Android shows, with what they do. OwnYourTV only ticks the ones checked on real TVs; you can untick any of them.",
        "h_notes": "Before you start on a {b}", "h_install": "Install it from your {b} TV",
        "steps": ["Open the Downloader app (free, from your TV's Play Store).", "Type the code <b>{c}</b>, install, open OwnYourTV.", "Follow the steps: about ten minutes, all with the remote."],
        "h_owners": "{b} TVs shared by owners", "owners": "See the {b} TVs owners shared from the app, and add yours with the Share button at the end.",
        "owners_link": "The list on GitHub →", "back": "← Back to the home page", "other": "Other brands",
        "faq": "More questions", "faq_link": "Read the questions on the home page →",
        "pkg": "Package",
        "nav": ["Install", "From a computer", "My TV", "Questions", "Contact"], "cta": "Install",
        "ft": ["Samsung, LG, Roku", "Privacy", "Credits", "Download", "Free and open source, MIT licence"],
    },
    "fr": {
        "title": "Enlever les pubs d'une TV {b} · OwnYourTV",
        "desc": "Coupe les pubs et le pistage de ta TV {b} Android TV ou Google TV, à la télécommande, sans ordinateur. Ce qu'OwnYourTV coupe sur les TV {b}, et comment.",
        "eye": "TV {b}", "h1": "Enlever les pubs d'une TV {b}",
        "lead": "OwnYourTV coupe les pubs et le pistage installés d'usine sur ta TV {b}, et lui donne un accueil calme. À la télécommande, sans ordinateur. Rien n'est supprimé : un bouton remet tout comme avant.",
        "h_apps": "Ce qu'elle coupe sur les TV {b}", "apps_note": "Les noms d'applis qu'affiche Android, avec ce qu'elles font. OwnYourTV ne coche que celles vérifiées sur de vraies TV, et tu peux décocher n'importe laquelle.",
        "h_notes": "Avant de commencer sur une {b}", "h_install": "L'installer depuis ta TV {b}",
        "steps": ["Ouvre l'appli Downloader (gratuite, dans le Play Store de ta TV).", "Tape le code <b>{c}</b>, installe, ouvre OwnYourTV.", "Suis les étapes : une dizaine de minutes, tout à la télécommande."],
        "h_owners": "Les TV {b} partagées par leurs propriétaires", "owners": "Vois les TV {b} que les utilisateurs ont partagées depuis l'appli, et ajoute la tienne avec le bouton Partager à la fin.",
        "owners_link": "La liste sur GitHub →", "back": "← Retour à l'accueil", "other": "Autres marques",
        "faq": "D'autres questions", "faq_link": "Lire les questions de l'accueil →",
        "pkg": "Paquet",
        "nav": ["Installer", "Depuis un ordinateur", "Ma TV", "Questions", "Contact"], "cta": "Installer",
        "ft": ["Samsung, LG, Roku", "Confidentialité", "Crédits", "Télécharger", "Gratuit et open source, licence MIT"],
    },
}


def page(lang, key, stamp):
    b = BRANDS[key]
    t = {k: (v.format(b=b["name"], c=CODE) if isinstance(v, str) else [s.format(c=CODE) for s in v]) for k, v in T[lang].items()}
    nav, ft = T[lang]["nav"], T[lang]["ft"]
    up = "../" if lang == "en" else "../../"
    home = up if lang == "en" else "../"
    other_lang = ("fr", "Français", "FR", "../fr/tv/%s.html" % key) if lang == "en" else ("en", "English", "EN", "../../tv/%s.html" % key)
    apps = "\n".join(
        '        <li><b>%s</b><code>%s</code></li>' % (html.escape(en if lang == "en" else fr), pkg) for pkg, en, fr in b["apps"])
    notes = "\n".join("        <li>%s</li>" % html.escape(n) for n in b["notes_" + lang])
    steps = "\n".join("        <li>%s</li>" % s for s in t["steps"])
    others = " · ".join('<a href="%s.html">%s</a>' % (k, v["name"]) for k, v in BRANDS.items() if k != key)
    path = "tv/%s.html" % key
    canon = SITE + ("" if lang == "en" else "fr/") + path
    issues = "https://github.com/pimado1/ownyourtv/issues?q=label%%3Atv-report+%s" % b["name"]
    return """<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<link rel="canonical" href="{canon}">
<link rel="alternate" hreflang="en" href="{site}{path}">
<link rel="alternate" hreflang="fr" href="{site}fr/{path}">
<link rel="alternate" hreflang="x-default" href="{site}{path}">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<meta name="description" content="{desc}">
<meta property="og:title" content="{title}">
<meta property="og:description" content="{desc}">
<meta property="og:image" content="{site}site/img/social.png">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#0b0d12">
<link rel="icon" href="{up}site/img/apple-touch-icon.png">
<link rel="stylesheet" href="{up}site/style.css?v={stamp}">
</head>
<body>

<header class="bar">
  <div class="wrap">
    <a class="brand" href="{home}" aria-label="OwnYourTV"><svg viewBox="0 0 32 32" aria-hidden="true"><rect x="3" y="12" width="26" height="14" rx="4" fill="#c6f432"/><path d="M11.5 12V8a4.5 4.5 0 0 1 9 0v1" fill="none" stroke="#f4f4f0" stroke-width="3" stroke-linecap="round"/><circle cx="16" cy="17.5" r="2" fill="#0b0d12"/><path d="M15.2 18.5h1.6l.5 4h-2.6z" fill="#0b0d12"/><rect x="11" y="28" width="10" height="2" rx="1" fill="#f4f4f0"/></svg><span>Own<b>Your</b>TV</span></a>
    <nav>
      <a href="{home}#how">{nav0}</a>
      <a href="{home}computer.html">{nav1}</a>
      <a href="{home}#tvs">{nav2}</a>
      <a href="{home}#faq">{nav3}</a>
      <a href="{home}contact.html">{nav4}</a>
      <a class="nav-gh" href="https://github.com/pimado1/ownyourtv">GitHub</a>
    </nav>
    <a class="btn btn-lime bar-cta" href="{home}#how">{cta}</a>
    <a class="lang" id="lang" href="{ol3}" hreflang="{ol0}" lang="{ol0}" aria-label="{ol1}">{ol2}</a>
  </div>
</header>

<main class="wrap doc brand-page">
  <p class="eyebrow">{eye}</p>
  <h1>{h1}</h1>
  <p class="lead">{lead}</p>
  <div class="code-inline">Downloader <strong>{code}</strong></div>

  <h2>{h_apps}</h2>
  <p>{apps_note}</p>
  <ul class="pkg-list">
{apps}
  </ul>

  <h2>{h_notes}</h2>
  <ul>
{notes}
  </ul>

  <h2>{h_install}</h2>
  <ol>
{steps}
  </ol>

  <h2>{h_owners}</h2>
  <p>{owners} <a href="{issues}">{owners_link}</a></p>

  <h2>{faq}</h2>
  <p><a href="{home}#faq">{faq_link}</a></p>

  <p class="others">{other_label} {others}</p>
</main>

<footer>
  <div class="wrap">
    <a class="brand" href="{home}"><span>Own<b>Your</b>TV</span></a>
    <nav class="ft-nav">
      <a href="{home}#how">{nav0}</a>
      <a href="{home}computer.html">{nav1}</a>
      <a href="{home}#tvs">{nav2}</a>
      <a href="{home}other-tvs.html">{ft0}</a>
      <a href="{home}#faq">{nav3}</a>
      <a href="{home}contact.html">{nav4}</a>
      <a href="{home}privacy.html">{ft1}</a>
      <a href="https://github.com/pimado1/ownyourtv/blob/main/NOTICE.md">{ft2}</a>
      <a href="https://github.com/pimado1/ownyourtv/releases/latest">{ft3}</a>
      <a href="https://github.com/pimado1/ownyourtv">GitHub</a>
    </nav>
    <span>{ft4}</span>
  </div>
</footer>
<script src="{up}site/i18n.js?v={stamp}"></script>
<script src="{up}site/effects.js?v={stamp}"></script>
<!-- Cloudflare Web Analytics: counts visits to this website without cookies; the TV app sends nothing to it. -->
<script type='module' src='https://static.cloudflareinsights.com/beacon.min.js' data-cf-beacon='{{"token": "6277e9947f4b43d3aec17fa0660c9f18"}}'></script>
</body>
</html>
""".format(lang=lang, canon=canon, site=SITE, path=path, up=up, home=home, stamp=stamp, ol0=other_lang[0], ol1=other_lang[1],
           ol2=other_lang[2], ol3=other_lang[3], code=CODE, apps=apps, notes=notes, steps=steps, others=others, issues=issues,
           **{"nav%d" % i: v for i, v in enumerate(nav)}, **{"ft%d" % i: v for i, v in enumerate(ft)}, other_label=t["other"] + (":" if lang == "en" else " :"),
           **{k: (html.escape(v, quote=True) if k in ("title", "desc") else v) for k, v in t.items() if k not in ("steps", "nav", "ft")})


def main():
    m = re.search(r"style\.css\?v=(\d+)", (ROOT / "index.html").read_text())
    stamp = m.group(1) if m else "1"
    for lang, out in (("en", ROOT / "tv"), ("fr", ROOT / "fr" / "tv")):
        out.mkdir(parents=True, exist_ok=True)
        for key in BRANDS:
            (out / ("%s.html" % key)).write_text(page(lang, key, stamp))
    print("brand pages:", ", ".join(BRANDS))


if __name__ == "__main__":
    main()
