---
target: Nightjar alarm, Settings alarm section, Tonight time picker
total_score: 26
max_score: 40
na_heuristics: 
p0_count: 1
p1_count: 3
timestamp: 2026-10-11T18-14-39Z
slug: sovereignhq-nightjar-ui-components-alarmsurface-kt
---
# Nightjar alarm, Settings alarm, Tonight: critique (11 Oct 2026)
Score 26/40 (Acceptable). Heuristics: status 2, real-world 2, control 3, consistency 2, error-prevention 2, recognition 3, flexibility 3, minimal 3, recovery 3, help 3.
Specificity: authored for the product (quiet vs dismiss, costly fallback, honest copy); never designed for the Hebrew RTL phone it runs on.
Detector: scans no .kt files (web-only); greps found 6 default-locale date formats, 5 non-auto-mirrored icons, ~5 clickables likely under 48dp.
## Priority issues
- [P0] Sums render reversed on a Hebrew phone ("57 − 23" shows as "23 − 57"): AlarmSurface question/entry Text need TextDirection.Ltr. harden
- [P1] Keypad mirrors to 3-2-1, Enter on the wrong side: AlarmSurface Keypad Row. harden
- [P1] Chart labels mirror while charts don't: weekday labels sit under the wrong bars (Charts.kt:134, :260). Global fix: lock LayoutDirection.Ltr in NightjarTheme (copy is English-only). adapt
- [P1] Quiet period doesn't cover the scan walk: button disabled once quiet, scanning doesn't re-arm, no countdown. clarify
- [P2] 12h wheel vs 24h clocks elsewhere; Hebrew day/month names in English headings (Format.kt Locale.getDefault()). typeset
## Persona flags
Partner: alarm screen and camera at full brightness; Test alarm fires with no warning. Casey: Back / Can't scan it? are adjacent ~44dp links. Sam: no button roles/selected state, no liveRegion on errors.
## Minor
Leading-space icon gaps; registering a code saves the first barcode seen with no confirm; wrong answer clears the entry; wake-code Turn off has no confirm.
