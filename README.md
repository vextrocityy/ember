# Ember — f.lux for Chromecast with Google TV

Tints the screen warmer after sunset and warmer still at bedtime, then returns to normal at sunrise. It works over every app (YouTube, Netflix, the home screen), so the color changes gradually, the way f.lux does on a PC.

| f.lux feature | Ember |
|---|---|
| Daytime / Sunset / Bedtime colors | ✅ 1200–6500K, with f.lux's preset names |
| Sunset from your location | ✅ Calculated on the device, no internet needed (defaults to Bothell, WA) |
| Wake time | ✅ Never brightens before you wake |
| Fast / slow transitions | ✅ 20 sec, 20 min or 60 min |
| Disable for an hour | ✅ |
| Movie mode (2.5 h) | ✅ Holds at 4500K or warmer, no dimming |
| Preview the day (fast-forward) | ✅ Plays 24 hours in 12 seconds |
| Dim at bedtime | ✅ Off / 15 / 30 / 45 % |
| Starts on boot | ✅ |

## 1. Build the APK

**Option A — GitHub (nothing to install)**
1. Create a new repo on github.com and upload everything in this folder. Drag-and-drop works.
2. The **Actions** tab builds the app automatically (about 3 min).
3. Each build is published as a release, so this link always points to the newest one:
   `https://github.com/<you>/<repo>/releases/latest/download/ember.apk`
   (The repo must be public for the Chromecast to download it without signing in.)

**Option B — Android Studio:** open this folder, then go to *Build › Build APK(s)*.

## 2. Install it on the Chromecast

Developer options must be on (Settings › System › About › click *Android TV OS build* 7×).

- **From your PC:**
  ```
  adb connect <chromecast-ip>
  adb install -r ember.apk
  ```
- **No PC:** in the **Downloader** app, enter the release link from step 1.

## 3. One-time permission (required)

Google TV hides the "draw over other apps" setting, so grant it from your PC:
```
adb shell appops set com.ember.tv SYSTEM_ALERT_WINDOW allow
```
Then open Ember. The overlay turns on, and it starts again by itself after a reboot.

## Using it

- **◀ ▶** change the value in the highlighted row. Hold the button to change it faster.
- **OK** toggles a setting or runs an action.
- While you adjust a color, the whole screen previews it for 3 seconds.

## Good to know

- **It only tints the Chromecast's picture.** The Samsung's own menus and its other inputs aren't affected.
- **Warm settings also dim the screen.** Android apps can only lay a tint on top of the picture; they can't remove blue light directly. Ember is tuned to keep blacks dark, so at 1900K it is warm but noticeably darker, much like f.lux's Candle setting.
- **Don't stack it with the TV's Warm2.** Set the TV's Color Tone to Standard or Warm1 for the Chromecast input and let Ember handle the evening shift.

## Code map

| File | What it does |
|---|---|
| `Schedule.kt` | f.lux's daily color curve |
| `Sun.kt` | Sunrise and sunset calculation |
| `ColorMath.kt` | Converts a color temperature into a tint color (lookup table built by `tools/table.py`) |
| `OverlayService.kt` | The always-on tint window and its smooth transitions |
| `MainActivity.kt` / `DayGraph.kt` | The remote-friendly settings screen and the 24-hour graph |
