# Third-party notices

Nightjar itself is MIT licensed. It builds on the following, all of which permit commercial and
non-commercial use. Every one is Apache License 2.0, which requires this attribution.

## The sound classifier

**YAMNet** — © Google LLC, Apache License 2.0.

A neural network trained on Google's AudioSet, a labelled collection of about two million sound
clips. Nightjar downloads the MediaPipe TensorFlow Lite build at compile time from
`storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/` and ships it inside the APK. It
runs entirely on the phone and sends nothing anywhere.

The class names Nightjar maps onto its own categories ("Snoring", "Fart", "Speech", "Thump, thud",
"Air conditioning" and the rest) come from the AudioSet ontology, released by Google under
Creative Commons Attribution 4.0.

## Libraries

| Component | Copyright | Licence |
| --- | --- | --- |
| MediaPipe Tasks (Audio) | Google LLC | Apache 2.0 |
| AndroidX Core, Activity, Lifecycle | The Android Open Source Project | Apache 2.0 |
| Jetpack Compose (UI, Foundation, Material 3) | The Android Open Source Project | Apache 2.0 |
| Kotlin standard library and compiler | JetBrains s.r.o. | Apache 2.0 |
| kotlinx.coroutines | JetBrains s.r.o. | Apache 2.0 |
| kotlinx.serialization | JetBrains s.r.o. | Apache 2.0 |
| JUnit 4 (tests only, not shipped) | JUnit contributors | Eclipse Public License 1.0 |

## Written from scratch, with no third-party code

These carry no external dependency, and are noted here because they are the parts people usually
assume were taken from a library:

- The FFT (`audio/Fft.kt`) — an iterative radix-2 transform with pre-baked Hann window and twiddle
  tables, written to be allocation-free so it can run thirty times a second all night without waking
  the garbage collector.
- Event classification (`audio/EventDetector.kt`) — the burst detector and the snore-versus-rumble
  decision, which turns on attack shape and breathing cadence.
- Breathing measurement (`sleep/BreathEstimator.kt`) — autocorrelation of the low-frequency envelope,
  with octave correction.
- Sleep staging (`sleep/SleepClassifier.kt`) and the smart alarm decision (`sleep/SmartAlarm.kt`).
- WAV writing, the waveform renderer, and every screen.

## Not affiliated with anyone

Nightjar is an independent project. It is not connected to, endorsed by, or derived from the code of
Sleep Cycle AB, Sleepwave, SnoreLab, or any other sleep application. No competing app was
decompiled, and no assets, code, or copy were taken from one. Any resemblance is in what the apps
do, not in how this one is built.
