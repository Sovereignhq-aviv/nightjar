# Nightjar

An Android app that listens to your bedroom overnight, records the things that stand out, and wakes
you during light sleep instead of at a fixed time.

**No account, no analytics, no subscription.** Audio is analysed on the phone and continuously
thrown away. Nothing is ever uploaded, because there is nowhere to upload it to.

The app makes exactly one kind of network request, and only if you leave it switched on: an
unauthenticated GET to GitHub's public releases API, asking what the newest version is. No
identifier, no device details, nothing about your recordings. Turn it off in Settings and Nightjar
makes no network requests at all.

Named after the bird, which is mostly known for the noise it makes at night.

---

## Why it exists

Because the features worth having in a sleep app were behind a subscription, and because the part I
actually wanted — hearing what happened in the room — is the part every sleep tracker treats as a
footnote. So in this app it is the first screen.

## What it does

| | |
| --- | --- |
| **Recordings and replay** | Anything rising above the room's own quiet level gets an 8-second clip — reaching *back* 3 seconds, so you hear the start of the noise rather than its aftermath. Tap to play, drag the waveform to scrub, 1x/1.5x/2x, star to keep forever, share to send. |
| **It names the noise** | Snoring, talking, rumbles, thumps — and more specifically than that. When the model says "Air conditioning", you learn why half your list is rubbish. Tap any label to correct it. |
| **Highlights** | The loudest dozen moments of the night, back to back. |
| **Smart alarm window** | Within your window (15/30/45 min) it rings while you are already stirring. The bar it has to clear falls as the window runs out, so it nearly always finds a good moment — and it always rings by the deadline. |
| **Maths to switch it off** | Optional. One to three sums, one to three digits. There is also a button that silences the alarm instantly *without* dismissing it, for getting up without waking anyone else. |
| **Sleep graph, score, REM** | Awake / light / deep / REM per minute, with a quality score. |
| **Trends** | Hours, quality, bedtime consistency, recordings per night, quality by weekday, and a week-on-week snoring alert. |

Android 8.1 or newer. Any phone; no wearable, no subscription, no companion device.

## Installing it

There is no Play Store listing. Grab the APK and sideload it. **After the first install it updates
itself** — see below.

Latest build: [**Releases**](https://github.com/Sovereignhq-aviv/nightjar/releases/latest).

1. Download `Nightjar.apk` from the [latest release](https://github.com/Sovereignhq-aviv/nightjar/releases/latest)
   — about 43 MB, most of which is the sound-classification model. (Unreleased builds are also
   attached to every run under the **Actions** tab.)
2. Get it onto the phone via Google Drive or a USB cable. **Not email** — Gmail blocks `.apk`
   attachments outright.
3. Open it on the phone. Android refuses the first time with *"your phone isn't allowed to install
   unknown apps from this source"*: tap **Settings** in that dialog, enable **Allow from this
   source**, press Back, then **Install**. Once per app you open APKs with.

### Updating

Settings → **Updates**. It checks GitHub a few times a day while the app is open, and offers a
one-tap download and install when there is something newer. Android shows its own confirmation
before anything is installed — nothing here can replace the app silently, which is deliberate.

The first update also needs a one-off Android permission ("allow installing unknown apps" for
Nightjar). The app puts a button in front of you when that moment arrives.

Every version is signed with the same key, so an update installs over the top and keeps all your
recordings and settings.

### Before you trust it with a morning

The app flags the first three itself, because getting them wrong means a missed alarm rather than a
slightly worse graph.

1. **Battery use → Unrestricted.** Android will otherwise shut the app down mid-night. Samsung,
   Xiaomi and OnePlus are the worst offenders.
2. **Alarms & reminders → allowed.**
3. **Wake-up screen → allowed.** Android 14 only grants this automatically to apps it recognises as
   alarm clocks. Denied, the alarm can only be stopped from the notification shade.
4. **Run the microphone check in Settings**, with the phone where it will actually sleep. Twelve
   seconds, and it tells you whether the room is audible from there. A muffled microphone is by far
   the most common cause of a night with nothing recorded.

## If you share a bed

This app records sound in a room where someone else is asleep. Please tell them.

Beyond the courtesy: recording another person without consent is a criminal offence in many places,
including Israel and the two-party-consent US states. The app is built so nothing ever leaves the
phone, which helps, but it does not make consent optional. Nightjar is a tool for hearing your own
night, not for monitoring somebody else's.

## How the detection works

**Audio is never written to disk as it goes.** It lives in a 14-second loop in memory that is
continuously overwritten. A clip reaches disk only when something is worth keeping.

Each 32ms frame gets a loudness figure and three frequency-band energies. A slow-moving noise floor
tracks the room's own background level, so a city flat and a quiet cottage behave identically. A
stretch above that floor becomes a *burst*, and when it ends its shape is measured:

- **Where the energy sits.** Below 500 Hz is a body noise through a mattress; 500–4000 Hz is a voice.
- **How long it lasted.** A rumble is under a second, a snore one to three, a sentence longer.
- **How fast it arrived.** A snore *swells* as the breath goes in; a rumble or thump is loud
  immediately and then decays. This is what separates two things occupying nearly the same frequency
  band.
- **Whether it repeats on a breathing rhythm.** Three low bursts two to six seconds apart is snoring.
  One on its own is not, however snore-shaped it looked.

That cheap heuristic decides *that* something happened. **YAMNet** — a Google model trained on two
million sounds — then decides *what* it was, running on the saved clip rather than on the live stream.
A neural network answering a question a hundred times a night costs nothing; running one across eight
hours of audio would cost your battery.

**Sleep staging** comes from per-minute restlessness scaled against that night's own 10th and 85th
percentiles, so absolute volume never matters. **REM** is separated from deep sleep by breathing
regularity, measured by autocorrelating the low-frequency envelope: deep sleep is metronomic, REM is
irregular while the body stays still. Without a breathing measurement those two are genuinely
indistinguishable, which is why this app claimed no REM at all until it could measure one.

### What it cannot do

- **It is not a medical device.** No diagnosis, no apnoea detection, no health-app integration.
- **REM is the least certain number here.** It is inferred from breathing regularity, not measured
  from brain activity, and it is labelled as an estimate in the app for that reason.
- **Two people in one bed confuses the staging.** It cannot tell your movement from theirs.
- **It cannot tell two voices apart.** One person snoring and another talking are both just events.
- **The alarm has one weak spot.** If Android kills the app outright, a backup notification channel
  still rings the system alarm sound, but that chime plays once rather than looping. Unrestricted
  battery use avoids this path.

## Storage

About 250 KB per clip, up to 120 clips a night at the loosest setting — roughly 30 MB a night.

- Unstarred clips are deleted after **7 days** (adjustable).
- Starred clips are kept **forever**, as are any whose label you corrected.
- Nights — the graphs and scores, a few hundred KB a year — are kept for **a year**, because Trends
  is worthless without history.

## Building it

The cloud build is the reference: push, wait about three minutes, download the APK from Actions. It
needs no local toolchain at all, which is why the project was built this way.

To build locally you need JDK 17 and the Android command-line tools (about 1.5 GB, no Android Studio
required). There is deliberately no `gradlew` wrapper in the repository — CI pins Gradle instead, so
the repo holds no binaries:

```bash
gradle assembleDebug
```

The tests are worth knowing about. They synthesise the sound shapes the classifier is supposed to
recognise, so a change to the detection logic can be checked in seconds rather than by going to sleep
and hoping:

```bash
gradle testDebugUnitTest
```

They have earned their place more than once — they caught the breathing detector reporting exactly
half the real rate, because a periodic signal correlates just as strongly with two of its own periods.
That would have been invisible on a phone.

## Layout

```
app/src/main/java/org/sovereignhq/nightjar/
  audio/      microphone loop, FFT, event classification, YAMNet, WAV writing
  sensor/     accelerometer movement counting
  sleep/      per-minute aggregation, staging, breathing, the smart alarm decision
  service/    the all-night foreground service, notifications, alarm scheduling
  alarm/      the wake-up screen, the sound player, the maths puzzles
  data/       models, JSON session storage, settings
  ui/         Compose screens, charts, waveforms, the docked player
app/src/test/ engine tests that need no phone
```

[PRODUCT.md](PRODUCT.md) holds the product context and the trade-offs behind it.
[DESIGN.md](DESIGN.md) holds the design system — palette, type scale, motion, and the deliberate
deviations from Material.

## Licence

MIT — see [LICENSE](LICENSE). Do what you like with it.

Third-party components, all Apache 2.0 and all requiring attribution, are listed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

### Releasing a new version

Push a tag and CI does the rest — builds, tests, publishes the release, and the in-app updater picks
it up from there:

```bash
git tag v1.2.0 && git push origin v1.2.0
```

Bump `versionName` in `app/build.gradle.kts` to match the tag first; the updater compares the two.

**Not affiliated with anyone.** Nightjar is independent, and is not connected to, endorsed by, or
derived from the code of Sleep Cycle AB, Sleepwave, SnoreLab or any other sleep app. Nothing was
decompiled and no code, assets or copy were taken from a competitor. Any resemblance is in what these
apps do, not in how this one is built.
