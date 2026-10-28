# Add your TV

OwnYourTV only ticks an app by default once it has been switched off on a real TV and everything still worked.

1. Run OwnYourTV on your TV (or `python3 ownyourtv.py <ip>`), switch off what it offers, and use the TV for a day:
   live TV, HDMI inputs, the remote, Wi-Fi, your apps.
2. Open an issue titled with your TV's brand and model, listing what you switched off and what you checked.
3. We add it to `data/catalog.json` with your TV in `v` (the TVs an entry was checked on), and copy the file to
   `app/src/main/assets/catalog.json`.

Each entry: `k` is the group (`ads`, `tracking`, `extras`, `apps`), `r` is whether it is ticked by default, `v` lists
the TVs it was checked on. Apps people may use (`apps`) are never ticked by default.
