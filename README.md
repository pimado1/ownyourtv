<p align="center"><img src="brand/banner-1280.png" alt="OwnYourTV" width="640"></p>

<h3 align="center">You paid for it. Own it.</h3>

<p align="center">Switch off the ads and tracking your Android TV or Google TV came with, and give it a home screen
that is yours. From the remote, no computer needed.</p>

---

## What it does

- **Ads**: promos, sponsored rows and store demos the maker put on your TV.
- **Tracking**: telemetry, advertising IDs and usage reports.
- **Clutter**: leftovers nobody uses on a TV.
- **Preinstalled apps**: keep the ones you use, switch off the rest.
- **Horizon**: a living home screen. A night sky with shooting stars, an aurora that takes the colour of the app you
  pick, your apps as a 3D deck of cards, the weather, quick settings on the remote's gear button.
- **One button puts everything back.** Apps are switched off, never deleted.

In English, French, Spanish, German, Italian and Portuguese, following your TV's language.

## Three ways to install

| | How | You need |
|---|---|---|
| **On the TV** | Install the app with [Downloader](https://www.aftvnews.com/downloader/), turn on USB debugging when it asks, say yes on the TV | Just the remote |
| **From a computer** | `python3 ownyourtv.py <tv ip>` | adb (Android platform-tools) |
| **With an AI agent** | Paste [PROMPT.md](PROMPT.md) into Claude Code | Claude Code, adb |

Every route uses the same catalogue (`data/catalog.json`) and the same safety rules: what Android or the TV needs to
start, play a channel or an HDMI input, take a remote, a keyboard or a network is never offered.

## How the TV app works without a computer

Android lets the owner turn on USB debugging. When it is on, OwnYourTV connects to the TV's own debugging port,
the TV asks you once whether to allow it, and from then on the app can switch apps off and set the home screen,
the way a computer would. Nothing leaves the TV. When you are done, "Finish and lock" turns debugging off again.

## Add your TV

Apps are ticked by default only once they have been checked on a real TV. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Build

```bash
./gradlew assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`. JDK 17 and the Android SDK (34) are needed.

## Licence

MIT, see [LICENSE](LICENSE). Third-party components are listed in [NOTICE.md](NOTICE.md).

---

### En français

**Tu l'as payée. Elle est à toi.** OwnYourTV coupe les pubs et le pistage installés d'usine sur ta TV Android TV ou
Google TV, et lui donne un accueil vivant, Horizon. Tout se fait à la télécommande, sans ordinateur, et un bouton
remet tout comme avant. Les applis sont désactivées, jamais supprimées.
