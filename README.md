# SleepWave

A free Android sleep tracker with a smart alarm. Listens to the room overnight, works out when
you are restless, and wakes you at a light moment inside a window you choose instead of jolting
you out of deep sleep at a fixed time.

No account, no internet, no analytics, no subscription. Everything stays on the phone.

## What it does

| Feature | How it works |
| --- | --- |
| **Smart alarm window** | Inside your window (15/30/45 min before the deadline) it rings when you are stirring. The bar it has to clear drops as the window runs out, so it nearly always finds a good moment — and it always rings by the deadline. |
| **Sleep graph + score** | Per-minute restlessness becomes awake / light / deep, drawn as an overnight curve with a stage ribbon underneath. Score combines duration, deep-sleep share, wake-ups and time actually asleep. |
| **Snore and noise clips** | Snoring is identified by low-frequency bursts repeating on a breathing cadence. Only then does audio hit the disk — 20-second clips, playable in the morning. |
| **Trends** | Hours, quality, bedtime consistency, quality by day of week, snoring per night, and how tags like "Caffeine" line up with your scores. |

## Getting it onto your phone

Nothing needs to be installed on the PC. GitHub builds the app in the cloud and hands you a file.

1. Push this folder to GitHub (see below). The build starts on its own.
2. Open the repository on github.com → **Actions** tab → click the newest run.
3. Wait for the green tick (about 4 minutes the first time).
4. Scroll to **Artifacts** → download **SleepWave-apk**. You get a zip with `SleepWave.apk` inside.
5. Send that APK to the phone — email it to yourself, or drop it in Google Drive and open it there.
6. Tap it on the phone. Android will ask permission to install from that app; allow it once.

Every later push rebuilds automatically, so changing something means: push, wait, download, install
over the top.

## First night: three things to get right

The app nags you about the first two on the home screen, because getting them wrong means a missed
alarm rather than a slightly worse graph.

1. **Battery use: Unrestricted.** Settings → Apps → SleepWave → Battery. Android will otherwise
   shut the app down in the middle of the night. Samsung, Xiaomi and OnePlus are the worst for this.
2. **Allow alarms and reminders.** Settings → Apps → SleepWave → Alarms & reminders.
3. **Turn your alarm volume up** and run *Test the alarm now* in Settings once. The alarm plays on
   the alarm stream, so it works with the ringer silenced — but not if the alarm volume is at zero.

Then: phone plugged in, screen down, on the nightstand or the edge of the mattress. It never needs
to touch you.

## How the sleep detection actually works

- The microphone runs at 16 kHz. Each 32 ms frame gets a loudness figure and three frequency-band
  energies. **Raw audio is never written to disk** — it lives in a 25-second rolling memory buffer
  and is overwritten continuously. Only a confirmed snore or loud noise causes a clip to be saved.
- A slow-moving noise floor tracks the room's own background level, so a city flat and a quiet
  cottage behave the same.
- Each minute becomes a single restlessness number from how *often* something happened and how
  *loud* the worst of it was, combined with accelerometer nudges if the phone is on the mattress.
- Staging scales those numbers against the night's own 10th and 85th percentiles, applies a mild
  sleep-pressure prior (deep sleep clusters early, light sleep late), then smooths out anything
  physiologically impossible — a one-minute deep-sleep blip is a measurement artefact, not a stage.

### Where it is weaker than the paid apps

- **No REM.** Separating REM from light sleep needs heart-rate or EEG data a phone on a nightstand
  does not have. REM is folded into light sleep.
- **It is a heuristic, not a trained model.** It separates still from restless reliably. Treat the
  exact deep/light split as an estimate.
- **Two people in a bed confuses it.** It cannot tell your movement from your partner's.
- **The alarm has one weak spot.** If Android kills the app entirely overnight, a backup
  notification channel still rings the system alarm sound and opens the wake-up screen — but that
  chime plays once rather than looping. Setting battery use to Unrestricted avoids this path.

## Pushing it to GitHub

```bash
gh repo create sovereignhq/sleepwave --private --source=. --push
```

## Building locally instead (optional)

Only worth it if you want to iterate quickly. Needs a JDK 17 and the Android command-line tools —
about 1.5 GB, no Android Studio required. There is no `gradlew` in this repo on purpose; the cloud
build uses a pinned Gradle instead.

```bash
gradle assembleDebug
```

## Layout

```
app/src/main/java/org/sovereignhq/sleepwave/
  audio/      microphone loop, FFT, snore detection, WAV writing
  sensor/     accelerometer movement counting
  sleep/      per-minute aggregation, sleep staging, the smart alarm decision
  service/    the all-night foreground service, notifications, alarm scheduling
  alarm/      the wake-up screen and the sound player
  data/       models, JSON session storage, settings
  ui/         Compose screens and charts
app/src/test/ engine tests that run without a phone
```

`app/src/test` is the fastest way to check a change to the staging or alarm logic:
`gradle testDebugUnitTest`. The cloud build runs these before it builds the APK, so a broken
change fails the build rather than shipping you a bad alarm.
