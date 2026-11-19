# Simple Alarm (Android)

Minimal alarm clock: framework APIs only, no dependencies. minSdk 26, targetSdk 35.

## Get the APK
GitHub → Actions → "Build APK" → latest run → artifact `SimpleAlarm-apk`.
Install `app-release.apk` on the phone (allow "Install unknown apps" for your browser/file manager).

Local build: `./gradlew assembleRelease` (needs Android SDK).

## Samsung setup (once)
- Allow notifications when prompted.
- Settings → Apps → Simple Alarm → Battery → **Unrestricted** (the in-app warning links there).
- Make sure it isn't in Battery → Background usage limits → Sleeping / Deep sleeping apps.

## How it works
- Set **earliest (x)** and **latest (y)** wake time by dragging the two thumbs on the 24 h dial (00 at top), or with −/+ (5 min steps).
- Rings at x, x+5, x+10, … and always at y (forced if the last gap is < 5 min). Window may cross midnight.
- Escalation by position f = i/(n−1): volume 15%→100% of alarm volume, fade-in 33 s→3 s, vibration none→soft→strong→max, ring length 1→3 min; final ring forces alarm stream to max and rings up to 10 min.
- **Stop** = silence this ring (next one still comes). **I'm up** = cancel the rest of this window.
- "Preview first/last" plays the gentlest / most aggressive ring.

## Design
- `WakeWindow` — window model, ring-time math, persistence.
- `AlarmScheduler` — books only the next ring with `AlarmManager.setAlarmClock()`; each ring books the following one.
- `AlarmService` — foreground service; `Profile` maps intensity → sound/vibration.
- `DialView` — the 24 h circular slider.
- `RingActivity` — full-screen ring UI over the lock screen.
- `BootReceiver` — re-books after reboot / time / timezone change.
