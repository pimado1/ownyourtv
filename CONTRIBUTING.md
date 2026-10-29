# Add your TV

OwnYourTV only ticks an app by default once it has been switched off on a real TV and everything still worked.

1. Run OwnYourTV on your TV (or `python3 ownyourtv.py <ip>`), switch off what it offers, and use the TV for a day:
   live TV, HDMI inputs, the remote, Wi-Fi, your apps.
2. Press **Share** on the app's last screen: it opens or updates your TV's report on GitHub, anonymously. If the app
   could not get that far, open a **My TV** issue instead, with what you switched off and what you checked.

Something wrong? Open a **Something is wrong** issue with a photo of the screen. French is fine too.
3. We add it to `data/catalog.json` with your TV in `v` (the TVs an entry was checked on), and copy the file to
   `app/src/main/assets/catalog.json`.

Each entry: `k` is the group (`ads`, `tracking`, `extras`, `apps`), `r` is whether it is ticked by default, `v` lists
the TVs it was checked on. Apps people may use (`apps`) are never ticked by default.
