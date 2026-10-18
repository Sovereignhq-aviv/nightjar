# Nightjar

## What it is

A free Android app that listens to a bedroom overnight, records anything that stands out, and wakes
you during light sleep instead of at a fixed time. A replacement for a paid sleep tracker, built
because the features worth having were behind a subscription.

## Who it's for

Started as a tool for one household. **Since 18 Oct 2026 it is being readied for strangers**: people
who find it, install it, and set it up with nobody to ask. That means a first open that explains
itself, setup steps inside the app rather than in this README, and the get-out-of-bed check in plain
view under the alarm, because it is the reason to choose this alarm. Still no accounts, no analytics,
no cloud. The only network use stays the optional update check against GitHub's public API.

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
- **Play Store distribution (under review, 18 Oct 2026).** It installs from a file today. A Play listing is being weighed; it would mean dropping the self-updater from that build, since Play forbids apps that install their own updates.
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
