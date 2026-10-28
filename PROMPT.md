# OwnYourTV: the prompt for an AI coding agent

Paste this into Claude Code (or any agent that can run commands on your computer), with your TV's IP address.

---

I want to take back my Android TV / Google TV with OwnYourTV (this repository). My TV's IP address is <TV_IP>.

1. Check that `adb` is installed (Android platform-tools). If not, tell me how to install it for my system.
2. Remind me to turn on Developer options and USB debugging on the TV (Settings › System › About › click
   "Android TV OS build" 7 times, then Settings › System › Developer options › USB debugging).
3. Run `python3 ownyourtv.py <TV_IP>` and show me what it found, grouped as Ads, Tracking, Clutter and
   Preinstalled apps. Explain each group in one line.
4. Ask me which preinstalled apps I use (streaming, voice, Chromecast, AirPlay) before touching any of them.
5. Only after I say yes, run `python3 ownyourtv.py <TV_IP> --apply`.
6. Tell me how to undo it (`python3 ownyourtv.py <TV_IP> --undo`) and remind me to switch USB debugging off.

Rules: never uninstall a system app, only disable it; never disable anything `ownyourtv.py` does not offer; never
open an app whose name says factory, test or fac menu.
