# Reports relay

When an owner presses **Share** on OwnYourTV's last screen, the TV sends this relay an anonymous report, and the
relay files it on GitHub: one issue per TV model (`TV: <brand> <model>`, label `tv-report`), one comment per
report, plus a `works`, `home-refused` or `home-blocked` label.

What a report holds: the TV's brand and model names, Android version, OwnYourTV version, whether the TV is in
the database, what happened to the home screen, how many apps were switched off and which catalogue apps could
not be. Never a name, an account, the list of installed apps, a serial number or a location. The relay keeps
nothing; the address a request comes from is only counted in memory to allow 5 reports a minute.

## Deploy

```bash
cd relay
npx wrangler login
npx wrangler secret put GITHUB_TOKEN
npx wrangler deploy
```

`GITHUB_TOKEN` is a fine-grained token limited to this repository with **Issues: read and write** and nothing else.
