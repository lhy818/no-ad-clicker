# SPEC — 无广告自动点击器 (clean-room)

Source of truth for `my-version/`. Written from observed behaviour and the
component/permission inventory of the reference app (Evidence
ev_402080fb49263de1ab5082cb54306ed61f21c24d729aeba6e9faa75858d831c2), not from its code.
No reference code, strings, resources, constants or identifiers are reused.

## 1. Observed behaviour of the reference (Observation unless marked)
- O1 An accessibility service is used to perform gestures (gesture dispatch capability declared; gesture dispatch API referenced 3x).
- O2 A floating overlay with draggable numbered point markers is shown over other apps.
- O3 Supported actions: single point tap, multi-point tap sequence, swipe (start/end points), and a recorded free gesture sequence.
- O4 Scripts can be saved, and imported from files (JSON and two custom script mime types).
- O5 Execution repeats; the reference offers loop/timer settings (Inference, medium: from settings screens present, exact options Unknown).
- O6 Ads, analytics, forum/VIP, online upgrade, WeChat, affiliate link — EXCLUDED by user.

## 2. Requirements for my-version
R1 Package `com.lhy.noadclicker`; permissions: none dangerous. No INTERNET, no location, no phone state, no ads/analytics.
R2 Accessibility service: `canPerformGestures=true`, `canRetrieveWindowContent=false`, no event types consumed beyond the minimum. Floating UI is drawn as an accessibility overlay (no SYSTEM_ALERT_WINDOW needed).
R3 Floating control panel (draggable): Play/Stop, +Tap, +Swipe, Record, Settings, Save, Close.
R4 Markers: each tap step shows one numbered draggable circle; each swipe shows start and end circles. Marker centre = gesture coordinate. Long-press a marker → edit its step (press ms, delay-after ms) or delete it. While running, markers ignore touches so gestures pass through.
R5 Step model: TAP(x,y,pressMs,delayAfterMs); SWIPE(x,y,x2,y2,durationMs,delayAfterMs). Defaults: tap press 50 ms, swipe 300 ms, delay 500 ms. Coordinates ≥ 0; press/duration 1..60000 ms; delay 0..3,600,000 ms.
R6 Loop policy: INFINITE; COUNT n (n ≥ 1) rounds; DURATION t ms (stop starting new steps once elapsed ≥ t). Stop button halts immediately after the in-flight gesture.
R7 Recording: a transparent full-screen layer captures the user's touches. Each finger-down→up becomes a TAP if total movement < 24 px, else a SWIPE from down point to up point, with duration = down→up time (clamped to R5). The previous step's delay-after = time between previous up and this down. The captured gesture is re-dispatched immediately so the user's app still reacts. Stop recording → steps replace the current script.
R8 Script JSON: `{"format":"noadclicker","version":1,"name":str,"loop":{"mode":"INFINITE|COUNT|DURATION","count":int,"durationMs":long},"steps":[{"type":"TAP|SWIPE",...}]}`. Decoder rejects wrong format/version, unknown type, missing/out-of-range fields with an error message; never partially loads.
R9 Storage: scripts saved as JSON files in app-private storage; main screen lists them (load / delete / export via system file picker); import via system file picker or "open with" for .json.
R10 Main screen: status of accessibility service, button to open accessibility settings, button to show the floating panel.

## 3. Tests (derived from R5–R8; must fail on a wrong answer)
- Codec round-trip preserves every field; rejects bad format, version, type, ranges.
- Loop policy: COUNT 3 → exactly 3 rounds; DURATION stops at threshold; INFINITE never stops by itself.
- Recorder classification: movement 23 px → TAP, 24 px → SWIPE; delay between gestures assigned to previous step; clamping.
- Mutation check: deliberately break the threshold / count logic and confirm the suite fails.

## 4. Unknown
- Reference's exact loop options, random-offset/anti-detection features, script mime formats' internal layout (not needed; we use our own JSON).
