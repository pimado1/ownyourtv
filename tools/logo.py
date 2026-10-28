"""Draws the OwnYourTV mark (an unlocked TV: lime screen as the lock body, an open shackle on top, a keyhole,
a stand) and renders the launcher icons, the in-app logo and the TV banner."""
import os, sys
from PIL import Image, ImageDraw, ImageFont
INK = (11, 13, 18, 255); LIME = (198, 244, 50, 255); PAPER = (244, 244, 240, 255)
B = sys.argv[1]

def mark(S):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(im); u = S / 100
    w = 8 * u; cx = 56 * u; r = 15 * u; top = 6 * u
    d.arc([cx - r, top, cx + r, top + 2 * r], 180, 360, fill=PAPER, width=int(w))
    d.rectangle([cx + r - w, top + r, cx + r, 40 * u], fill=PAPER)               # right leg, into the screen
    d.rectangle([cx - r, top + r, cx - r + w, top + r + 6 * u], fill=PAPER)      # left leg, lifted: open
    d.ellipse([cx - r, top + r + 6 * u - w / 2, cx - r + w, top + r + 6 * u + w / 2], fill=PAPER)
    d.rounded_rectangle([6 * u, 38 * u, 94 * u, 82 * u], radius=int(11 * u), fill=LIME)   # the screen
    d.ellipse([44.5 * u, 49 * u, 55.5 * u, 60 * u], fill=INK)                     # keyhole
    d.polygon([(47.2 * u, 56 * u), (52.8 * u, 56 * u), (54.6 * u, 71 * u), (45.4 * u, 71 * u)], fill=INK)
    d.rounded_rectangle([34 * u, 86 * u, 66 * u, 91 * u], radius=int(2.5 * u), fill=PAPER)  # the stand
    return im

res = f"{B}/app/src/main/res"
for name, s in [("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)]:
    os.makedirs(f"{res}/{name}", exist_ok=True)
    ic = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0)); ImageDraw.Draw(ic).rounded_rectangle([0, 0, 1023, 1023], radius=230, fill=INK)
    ic.alpha_composite(mark(1024).resize((780, 780), Image.LANCZOS), (122, 110))
    ic.resize((s, s), Image.LANCZOS).save(f"{res}/{name}/ic_launcher.png")
os.makedirs(f"{res}/drawable-nodpi", exist_ok=True)
mark(1024).resize((384, 384), Image.LANCZOS).save(f"{res}/drawable-nodpi/logo.png")

F = ImageFont.truetype(f"{B}/app/src/main/assets/fonts/display.ttf", 118)
ban = Image.new("RGBA", (1280, 720), INK); d = ImageDraw.Draw(ban)
parts = [("Own", PAPER), ("Your", LIME), ("TV", PAPER)]
tw = sum(d.textlength(t, font=F) for t, _ in parts); ms = 300; gap = 40
x0 = (1280 - ms - gap - tw) / 2
ban.alpha_composite(mark(1024).resize((ms, ms), Image.LANCZOS), (int(x0), 200))
x = x0 + ms + gap
for t, c in parts:
    d.text((x, 290), t, font=F, fill=c); x += d.textlength(t, font=F)
os.makedirs(f"{res}/drawable-xhdpi", exist_ok=True)
ban.resize((320, 180), Image.LANCZOS).convert("RGB").save(f"{res}/drawable-xhdpi/banner.png")
os.makedirs(f"{B}/brand", exist_ok=True)
ban.convert("RGB").save(f"{B}/brand/banner-1280.png")
mark(1024).save(f"{B}/brand/mark.png")
print("ok")
