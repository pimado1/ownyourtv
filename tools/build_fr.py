"""Writes the French pages in docs/fr/ from the English ones in docs/ and the FR dictionary in docs/site/i18n.js.

Search engines read a page as it is served, not after a script swapped its words, so each language gets its own
page. Run it after editing a page or the dictionary, before tools/site_version.py and before pushing.
"""
import json, re, subprocess, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "docs"
PAGES = ["index.html", "privacy.html", "contact.html"]
SITE = "https://ownyour.tv/"
META = {  # what sits outside data-i18n: titles and descriptions
    "index.html": ("OwnYourTV : ta TV, sans pubs ni pistage", "Coupe les pubs et le pistage installés d'usine sur ta TV Android TV ou Google TV, et donne-lui un accueil calme, avec profils et mode enfant. À la télécommande, sans ordinateur. Gratuit et open source."),
    "privacy.html": ("Confidentialité · OwnYourTV", "Ce qu'OwnYourTV envoie, et quand : rien, sauf si tu choisis une ville pour la météo ou appuies sur Partager."),
    "contact.html": ("Contact · OwnYourTV", "Ajouter ta TV à la liste, signaler un souci ou écrire au développeur d'OwnYourTV."),
}

FR = json.loads(subprocess.check_output(["node", "-e",
    "const fs=require('fs'),vm=require('vm');const c={document:{querySelectorAll:()=>[],getElementById:()=>null,documentElement:{}},"
    "localStorage:{getItem:()=>null,setItem(){}},navigator:{language:'en'},location:{}};vm.createContext(c);"
    "vm.runInContext(fs.readFileSync(process.argv[1],'utf8'),c);process.stdout.write(JSON.stringify(c.FR))",
    str(ROOT / "site/i18n.js")]))


def nbsp(text):
    """French puts a space before : ; ? ! and inside « »: a non-breaking one, so the mark never starts a line alone."""
    return re.sub(r"« ", "«\u00a0", re.sub(r" ([:;?!»])", "\u00a0\\1", text))


FR = {k: nbsp(v) for k, v in FR.items()}
META = {k: (nbsp(a), nbsp(b)) for k, (a, b) in META.items()}


def inner_span(html, start):
    """End of the element whose opening tag starts at `start`: matching close tag, nested same-name tags counted."""
    name = re.match(r"<([a-zA-Z0-9]+)", html[start:]).group(1)
    open_end = html.index(">", start) + 1
    depth, i = 1, open_end
    pat = re.compile(r"<(/?)%s\b[^>]*>" % name)
    while depth:
        m = pat.search(html, i)
        depth += -1 if m.group(1) else 1
        i = m.end()
    return open_end, m.start()


def translate(html):
    missing = []
    out, pos = [], 0
    body_end = html.find("<script")  # the scripts build their own markup with t()
    for m in re.finditer(r'<[a-zA-Z0-9]+[^>]*\sdata-i18n="([^"]+)"[^>]*>', html[:body_end]):
        if m.start() < pos:
            continue
        key = m.group(1)
        a, b = inner_span(html, m.start())
        out.append(html[pos:a])
        if key in FR:
            out.append(FR[key])
        else:
            missing.append(key)
            out.append(html[a:b])
        pos = b
    out.append(html[pos:])
    html = "".join(out)
    for attr, data in (("alt", "data-i18n-alt"), ("aria-label", "data-i18n-aria")):
        def sub(m):
            tag, key = m.group(0), m.group(1)
            if key not in FR:
                missing.append(key)
                return tag
            return re.sub(r'\s%s="[^"]*"' % attr, ' %s="%s"' % (attr, FR[key].replace('"', "&quot;")), tag)
        html = re.sub(r'<[^>]*\s%s="([^"]+)"[^>]*>' % data, sub, html)
    return html, missing


def alternates(page):
    path = "" if page == "index.html" else page
    return ('<link rel="alternate" hreflang="en" href="%s%s">\n<link rel="alternate" hreflang="fr" href="%sfr/%s">\n'
            '<link rel="alternate" hreflang="x-default" href="%s%s">\n' % (SITE, path, SITE, path, SITE, path))


def lang_link(html, to_fr, page):
    path = "" if page == "index.html" else page
    target = ("fr/" + path) if to_fr else ("../" + path)
    label = ("FR", "Français", "fr") if to_fr else ("EN", "English", "en")
    return re.sub(r'<(button|a) class="lang" id="lang"[^>]*>.*?</\1>',
                  '<a class="lang" id="lang" href="%s" hreflang="%s" lang="%s" aria-label="%s">%s</a>' % (target or "./", label[2], label[2], label[1], label[0]),
                  html, flags=re.S)


report = {}
(ROOT / "fr").mkdir(exist_ok=True)
for page in PAGES:
    src = (ROOT / page).read_text()
    # English page: canonical, alternates and a link to the French page.
    en = re.sub(r'\n<link rel="(?:alternate|canonical)"[^>]*>', "", src)
    path = "" if page == "index.html" else page
    en = en.replace('<meta name="viewport"', '<link rel="canonical" href="%s%s">\n%s<meta name="viewport"' % (SITE, path, alternates(page)), 1)
    en = lang_link(en, True, page)
    (ROOT / page).write_text(en)
    fr, missing = translate(en)
    report[page] = missing
    fr = fr.replace('<html lang="en">', '<html lang="fr">', 1)
    fr = fr.replace('<link rel="canonical" href="%s%s">' % (SITE, path), '<link rel="canonical" href="%sfr/%s">' % (SITE, path), 1)
    title, desc = META[page]
    fr = re.sub(r"<title>.*?</title>", "<title>%s</title>" % title, fr, count=1)
    fr = re.sub(r'<meta name="description" content="[^"]*">', '<meta name="description" content="%s">' % desc.replace('"', "&quot;"), fr, count=1)
    fr = re.sub(r'<meta property="og:description" content="[^"]*">', '<meta property="og:description" content="%s">' % desc.replace('"', "&quot;"), fr, count=1)
    fr = re.sub(r'<meta property="og:title" content="[^"]*">', '<meta property="og:title" content="%s">' % title.replace('"', "&quot;"), fr, count=1)
    fr = re.sub(r'(href|src|poster)="site/', r'\1="../site/', fr)
    fr = lang_link(fr, False, page)
    (ROOT / "fr" / page).write_text(fr)

urls = []
for page in PAGES:
    path = "" if page == "index.html" else page
    urls.append("  <url><loc>%s%s</loc><xhtml:link rel=\"alternate\" hreflang=\"fr\" href=\"%sfr/%s\"/></url>" % (SITE, path, SITE, path))
    urls.append("  <url><loc>%sfr/%s</loc><xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"%s%s\"/></url>" % (SITE, path, SITE, path))
for brand in sorted((ROOT / "tv").glob("*.html")):
    path = "tv/" + brand.name
    urls.append("  <url><loc>%s%s</loc><xhtml:link rel=\"alternate\" hreflang=\"fr\" href=\"%sfr/%s\"/></url>" % (SITE, path, SITE, path))
    urls.append("  <url><loc>%sfr/%s</loc><xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"%s%s\"/></url>" % (SITE, path, SITE, path))
(ROOT / "sitemap.xml").write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" '
                                  'xmlns:xhtml="http://www.w3.org/1999/xhtml">\n' + "\n".join(urls) + "\n</urlset>\n")
(ROOT / "robots.txt").write_text("User-agent: *\nAllow: /\nSitemap: %ssitemap.xml\n" % SITE)
for page, missing in report.items():
    print(page, "untranslated:", ", ".join(sorted(set(missing))) or "none")
