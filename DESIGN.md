# SleepWave design system

Everything below is implemented in `ui/theme/Theme.kt` and the `ui/components` package. Change it
there, not per screen.

## The scene that decided the theme

Someone in an unlit bedroom at 00:30 setting an alarm, and the same person at 07:05 still in bed,
squinting, scrolling through what the phone heard. A light theme is hostile in both moments, so the
app is **dark only** — not as a preference, as the only correct answer for where it is used.

**Dynamic Color is deliberately declined.** A wallpaper-derived pastel scheme would undo the one
property this app needs, which is being dim enough to look at in the dark.

## Colour

Strategy: **Restrained.** Near-black surfaces, one indigo accent for anything actionable, and a small
set of semantic hues that only ever encode data. No hue is used decoratively.

| Role | Value | Used for |
| --- | --- | --- |
| `background` | `#08090F` | the page |
| `surface` | `#11131F` | grouped panels, the playing row |
| `surfaceVariant` | `#1A1D2E` | chips, the docked player, raised rows |
| `outline` | `#272B41` | hairlines, gridlines, waveform track |
| `primary` | `#8B9CF9` | actions, selection, current state |
| `secondary` | `#6EE7B7` | success and positive deltas |
| `tertiary` | `#F7B267` | warnings, ratings, saved clips |
| `onSurface` | `#EAECF8` | body text — 15.8:1 on background |
| `onSurfaceVariant` | `#9AA2C4` | secondary text — 7.1:1, not a light gray |

Elevation is carried by those tonal steps, never by drop shadows.

### Data colours

`DataColors` sits outside the Material roles on purpose: a sleep stage is not "primary" or
"tertiary", it is *deep*. These behave like chart series and must stay stable.

- Stages — awake `tertiary`, light `primary`, deep `#3B5BDB`
- Events — snore indigo, talking mint, rumble amber, thump rose, other muted

## Typography

Roboto, the system face, through the Material type scale with a tight ~1.2 ratio — this is product UI
with many small labels, and exaggerated contrast would just be noise. All sizes in `sp` so the system
font-size setting works.

Display sizes are reserved for the two things read from a metre away in the dark: the clock on the
night screen and the quality score.

## Layout

- 20dp horizontal page gutter; 16dp between sections; 20dp radius on panels.
- **Touch targets 48dp minimum**, 8dp apart.
- Edge-to-edge with real inset handling. `Scaffold` insets the content, `NavigationBar` insets itself.
- **Cards are not the default.** The recordings list is deliberately flat and dense: a hundred
  recordings in a hundred cards is a hundred borders to read past. Panels get a surface; list rows
  do not.

## The signature motif

The **waveform**, at three scales, all fed by the same 120-point envelope computed once when a clip
is written:

1. 26dp thumbnail on every row — enough to recognise a clip at a glance.
2. 40dp full-width in the docked player, with drag-to-seek.
3. The whole-night trace on the sleep screen.

Bars are mirrored around a centre line, peak-preserving when downsampled. That mirroring is what
keeps a 26dp thumbnail reading as audio rather than as a bar chart.

## Motion

150–250ms, and only ever to convey state.

- Destinations: fade-through (`Crossfade`, 200ms).
- Docked player: slide up from the bottom with a fade, 220ms in / 180ms out.
- Score ring: sweeps in once on arrival, 620ms. The one reveal that earns its keep — the number is
  the answer to "how did I sleep", so it gets a beat of attention.
- Listening dot: 2.6s reversing pulse. It exists to answer "is this still on" at 3am.

No orchestrated load sequences. No decorative motion anywhere.

## Component vocabulary

- **Panels** — `NightCard`, one level deep, never nested.
- **Chips** — filters and segmented choices. Selected: accent fill, `onPrimary` text. Unselected:
  `surfaceVariant` with a leading colour dot for the kind.
- **Primary action** — filled `Button`, 64dp on the hero (Start the night), 48dp elsewhere.
- **One FAB per screen** — Highlights, on Sounds only, and only when there are at least two
  recordings and nothing is already playing.
- **Dialogs for decisions only** (stop tracking, delete). Snackbars for transient failures.
- **Empty states teach the screen**, including the useful diagnosis: a silent night usually means the
  microphone was blocked, not that the room was quiet.

## Deliberate deviations from Material

- **The alarm screen swallows System Back.** Everywhere else Back navigates and the predictive gesture
  is honoured, but dismissing an alarm should take a decision, not a stray back swipe. This matches
  AOSP Clock.
- **Screen brightness is forced to minimum** on the night screen, overriding the system value, and
  restored on exit.
