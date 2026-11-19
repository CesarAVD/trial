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

## Design
- `AlarmManager.setAlarmClock()` — exact, Doze-exempt, shows in status bar / lock screen.
- `AlarmService` — foreground service, loops the default alarm tone on the alarm stream + vibration, auto-snoozes after 10 min.
- `RingActivity` — full-screen intent over the lock screen; Snooze (10 min) / Dismiss.
- `BootReceiver` — re-registers alarms after reboot, time or timezone change.
