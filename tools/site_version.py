"""Stamps the website's CSS and script links with a version, so browsers fetch the new files after an update
instead of mixing a new page with old cached ones (GitHub Pages lets them keep files for 10 minutes).

    python3 tools/site_version.py
"""
import re
import time
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent / "docs"
stamp = time.strftime("%Y%m%d%H%M")
for page in DOCS.glob("*.html"):
    text = page.read_text()
    new = re.sub(r'((?:href|src)="site/[^"?]+\.(?:css|js))(\?v=\d+)?"', lambda m: f'{m.group(1)}?v={stamp}"', text)
    if new != text:
        page.write_text(new)
        print(page.name, stamp)
