# Skjálfti

Your phone as a seismograph. A native Android app for the Reykjanes peninsula that reads the accelerometer, draws a live seismograph, and checks every nearby earthquake from the Icelandic Met Office against what your phone actually felt.

Two looks, one app: a **pixel console** (Departure Mono, Doto, Martian Mono, CRT scanlines, bevelled keys) and **liquid glass** (Kyant's Backdrop lens over a molten glow). Switch in Setup.

- **Live**: a 200 Hz seismograph that scrolls by real elapsed time, so it stays smooth at any refresh rate while the pixel columns stay locked in place. Axis level meters, gain, channel (X, Y, Z or combined) and an STA/LTA detector, the same trigger real seismographs use.
- **Log**: every quake in the last 24 hours within your radius, events per hour, and whether your phone felt it: **FELT**, **MISS** (recording but too faint), **OFF** (not recording) or **WAIT** (waves still arriving).
- **Night watch**: arm it at bedtime. A foreground service records all night and at 07:00 posts a report: how many quakes, how many your phone felt, the strongest, and a compressed trace of the night.
- **Location**: GPS detection without Google Play services (fused, GPS or network provider), named by the phone's geocoder or the nearest Reykjanes town. Save places with your own names, switch between them, or let it auto-update when the app opens. Six preset towns too.
- **Achievements**: first watch, seven nights, first felt, felt an M3, a hundred quakes logged.

## How FELT works

From the quake's position and depth we know the straight-line distance to your phone. P waves cross the crust here at roughly 6 km/s and S waves at roughly 3.5 km/s, so we know when shaking should arrive. If the detector fired in that window (with a few seconds of slack), the quake was felt. Triggers stronger than 0.15 g are treated as the phone being handled and never count.

A phone accelerometer is far less sensitive than a seismometer: expect to feel nearby M2+ quakes with the phone lying flat on a hard surface.

## Data

| Source | Used for |
| --- | --- |
| Veðurstofa Íslands, `api.vedur.is/quakes/events` | Quake time, position, depth, magnitude, region |
| The phone's location (optional) | Where distances and arrival times are measured from |
| The phone's accelerometer | Everything else |

No server, no account. Recorded triggers stay on the phone for a week.

## Install

Every push to `main` builds a signed APK and publishes it as a GitHub Release. Open the latest release on your phone and tap `skjalfti.apk`. Builds are signed with one key kept in the repo's Actions secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`), never in the code, so new versions install over the old one. Other branches build as debug-signed checks.

The app keeps itself current: it checks GitHub for a newer release when opened (at most hourly) and in the background every ~6 hours, shows a card on Live and one notification per version, and Setup → Updates downloads and installs it in place.

## Layout

```
app/src/main/java/app/skjalfti/
  seismo/   accelerometer ring buffer, high-pass filter, STA/LTA detector
  data/     IMO quake feed, FELT matching, on-disk store
  ui/       both skins: theme, glass, kit, trace renderer, screens
  watch/    night watch foreground service and morning report
  update/   GitHub release check, download, install, background watcher
```

Fonts are bundled under the SIL Open Font License (see `licenses/`).
