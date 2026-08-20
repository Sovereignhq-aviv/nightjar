# Nightjar

## What it is

A free Android app that listens to a bedroom overnight, records anything that stands out, and wakes
you during light sleep instead of at a fixed time. A replacement for a paid sleep tracker, built
because the features worth having were behind a subscription.

## Who it's for

One household, on one Android phone. Not a product with users — a tool with an owner. That shapes
every trade-off: no accounts, no onboarding funnel, no analytics, no cloud. Storage decisions are
made for one phone, and honesty about accuracy beats a confident-looking number.

## What people actually want from it

In priority order, which is not the order sleep apps usually assume:

1. **Hear what happened.** Snoring, talking, outbursts, gut noises, thumps. Playing these back is
   the reason the app gets opened in the morning, so it is the first destination, not a sub-screen.
2. **Not be woken badly.** Ring inside a chosen window at a moment when you are already stirring.
3. **See the shape of the night.** A graph and a score, with an honest account of what they can and
   cannot know.
4. **Notice patterns over weeks.** Bedtime consistency, quality by weekday, whether caffeine shows up.

## Register

product — this is app UI in service of a task, not a marketing surface. Earned familiarity beats
novelty; the tool should disappear into the job.

## Platform

android

Jetpack Compose, Material 3, minSdk 27, no third-party UI or DSP dependencies.

## Non-goals

- **Medical claims.** No sleep-apnoea detection, no diagnosis, no health-app integration.
- **Play Store distribution.** It installs from a file. No store listing, no review process.
- **Multi-person separation.** It cannot tell whose noise is whose in a shared bed, and does not
  pretend to.
- **REM detection.** Not possible from a nightstand microphone. REM is folded into light sleep and
  labelled as such.

## Constraints that drive design

- **The screen is looked at in the dark**, or within a minute of waking. Dark-only theme, minimum
  brightness while tracking, no bright surfaces anywhere.
- **A missed alarm is the worst possible failure.** Reliability work (foreground service, wake lock,
  system alarm-clock backstop, battery-optimisation nagging) outranks features.
- **Audio is the expensive thing.** Roughly 250 KB per clip, up to 120 clips a night. Raw audio never
  touches disk except as a saved clip; unstarred clips are deleted after a week.
- **A night can hold 120 recordings.** Every list decision follows from that: dense rows, a fixed
  time column, filters by kind, a docked player that survives scrolling.
