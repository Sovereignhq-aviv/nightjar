# SleepWave

A free Android app that listens to your bedroom overnight, records anything that stands out, and
wakes you during light sleep instead of at a fixed time.

No account, no internet, no analytics, no subscription. Everything stays on the phone.

## What it does

**Sounds** is the main screen, because playing back what happened is the reason to open the app in
the morning.

| Feature | How it works |
| --- | --- |
| **Recordings and replay** | Anything that rises above the room's own quiet level gets an 8-second clip — and the clip reaches 3 seconds *backwards*, so you hear the start of the noise rather than the aftermath. Tap to play, drag the waveform to scrub, 1x/1.5x/2x, star to keep, share to send. |
| **Five kinds of noise** | Snoring, Talking, Rumble, Thump, Other. Filter the night by any of them. |
| **Highlights** | Plays the loudest dozen moments of the night back to back. |
| **Smart alarm window** | Inside your window (15/30/45 min before the deadline) it rings when you are already stirring. The bar it has to clear drops as the window runs out, so it nearly always finds a good moment — and it always rings by the deadline. |
| **Sleep graph and score** | Per-minute restlessness becomes awake / light / deep, drawn as an overnight curve with a stage ribbon underneath. |
| **Trends** | Hours, quality, bedtime consistency, recordings per night, quality by day of week, and how tags like "Caffeine" line up with your scores. |

## Getting it onto your phone

Nothing needs to be installed on your PC. GitHub builds the app in the cloud.

1. Open **Actions** → the newest green run → scroll to **Artifacts** → download **SleepWave-apk**.
   You get a zip with `SleepWave.apk` inside (about 16 MB).
2. Send that APK to the phone — email it to yourself, or drop it in Google Drive and open it there.
3. Tap it on the phone. Android will ask permission to install from that app; allow it once.

Every push rebuilds automatically, so changing something means: push, wait about three minutes,
download, install over the top.

## First night: three things to get right

The app nags about the first two on its own screens, because getting them wrong means a missed alarm
rather than a slightly worse graph.

1. **Battery use: Unrestricted.** Settings → Apps → SleepWave → Battery. Android will otherwise shut
   the app down mid-night. Samsung, Xiaomi and OnePlus are the worst offenders.
2. **Allow alarms and reminders.** Settings → Apps → SleepWave → Alarms & reminders.
3. **Turn your alarm volume up** and run *Test the alarm now* in Settings once. The alarm plays on the
   alarm stream, so it works with the ringer silenced — but not if the alarm volume is at zero.

Then: phone plugged in, screen down, on the nightstand or the edge of the mattress. **Do not put it
under a pillow or duvet** — a muffled microphone is the single biggest cause of a night with no
recordings.

## How the detection works

**Audio is never written to disk as it goes.** It sits in a 14-second loop in memory that is
continuously overwritten. Only a confirmed event causes a clip to be saved.

Each 32ms frame of microphone input gets a loudness figure and three frequency-band energies. A slow
noise floor tracks the room's own background level, so a city flat and a quiet cottage behave the
same. A stretch of sound above that floor becomes a "burst", and when it ends its shape decides what
it was:

- **Where the energy sits.** Below 500 Hz is a body noise through a mattress; 500–4000 Hz is a voice.
- **How long it lasted.** A rumble is under a second, a snore one to three, a sentence longer.
- **How fast it arrived.** A snore *swells* as the breath goes in. A rumble or thump is loud
  immediately and then decays. This is what separates the two, since they occupy nearly the same
  frequency band.
- **Whether it repeats on a breathing rhythm.** Three low bursts spaced two to six seconds apart is
  snoring; one on its own is not, however snore-shaped it looked.

Sleep stages come from how much movement and sound there is each minute, scaled against that night's
own 10th and 85th percentiles, with a mild sleep-pressure prior and smoothing that removes
physiologically impossible one-minute flickers.

### Where it is weaker than the paid apps

- **No REM.** Separating REM from light sleep needs heart-rate or EEG data a nightstand microphone
  does not have. REM is folded into light sleep.
- **It is a heuristic, not a trained model.** It separates still from restless reliably. Treat the
  exact deep/light split as an estimate.
- **Two people in a bed confuses it.** It cannot tell your movement from your partner's.
- **The alarm has one weak spot.** If Android kills the app entirely overnight, a backup notification
  channel still rings the system alarm sound and opens the wake-up screen — but that chime plays once
  rather than looping. Setting battery use to Unrestricted avoids this path.

## Storage

Roughly 250 KB per clip, up to 120 clips a night at the loose sensitivity — about 30 MB a night.

- **Unstarred clips are deleted after 7 days.** Adjustable in Settings.
- **Starred clips are kept forever.**
- **Nights are kept for a year** — the graphs and scores are a few hundred KB, and Trends needs
  history to be worth anything.

Sensitivity has three settings; the default catches a lot, which also means the air conditioning and
passing cars get clips. Turn it down in Settings if the morning list is mostly rubbish.

## Layout

```
app/src/main/java/org/sovereignhq/sleepwave/
  audio/      microphone loop, FFT, event classification, WAV writing
  sensor/     accelerometer movement counting
  sleep/      per-minute aggregation, sleep staging, the smart alarm decision
  service/    the all-night foreground service, notifications, alarm scheduling
  alarm/      the wake-up screen and the alarm sound player
  data/       models, JSON session storage, settings
  ui/         Compose screens, charts, waveforms, the docked player
app/src/test/ engine tests that run without a phone
```

[PRODUCT.md](PRODUCT.md) and [DESIGN.md](DESIGN.md) hold the product context and the design system.

`app/src/test` is the fastest way to check a change to the classifier or the alarm logic — it
synthesises the sound shapes rather than requiring you to go to sleep and hope. The cloud build runs
these before it builds the APK, so a broken change fails the build instead of shipping you a bad
alarm.

## Building locally instead (optional)

Only worth it if you want to iterate quickly. Needs JDK 17 and the Android command-line tools —
about 1.5 GB, no Android Studio. There is no `gradlew` in this repo on purpose; the cloud build uses
a pinned Gradle instead.

```bash
gradle assembleDebug
```
