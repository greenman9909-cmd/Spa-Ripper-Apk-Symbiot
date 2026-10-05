# Native Home/catalog preview 0.4.8

Validated 2026-10-06. This release addresses repeated Home cards and expensive refresh/cache work while preserving original UI resources and screen implementations. It remains a preview.

## Evidence and changes

- Live old-provider samples showed 29/32 daily and 30/32 monthly cards also present in the weekly list. Genre rails likewise shared leading cards. The physical-phone baseline visibly repeated titles between Action/Adventure and Comedy/Romance.
- Home selects at most 14 distinct anime per rail, excluding IDs already selected for earlier rails. One featured hero can also feature a card in the popular collection. Live revised feed: 16 rails, 179 distinct collection cards.
- Replaced the 15-second synchronous whole-feed refresh with a two-minute cache and one background refresh task. Warm expired requests return the active feed immediately; completed data applies only at a new page-zero request. Subsequent pages never install the prepared refresh mid-scroll. A failed warm refresh keeps the existing data and backs off briefly.
- Home refresh no longer clears the shared AniList discovery cache. Three consecutive 25-item catalog pages, including the boundary between AniList pages, returned distinct IDs.
- Summary updates retain episode arrays without serializing large episode lists under the image/title cache lock. Existing episode metadata remains available.
- The hero uses the original PANEL/UNDEFINED model handler. Original layouts, fonts, view code and native player are retained.

## Verification

- 44 Python tests passed, including UTF-8/UTF-16 manifest version preservation.
- 75 contracts passed against the original Android classes on the explicitly isolated AVD. Added cases cover global Home card selection, active-versus-ready snapshot paging, an immediate warm response while refresh is pending, and episode-array preservation.
- Live Home/catalog checks passed: all emitted rows decode as valid original models; unique row IDs across native pages, unique collection card IDs, bounded rail sizes, correct terminal page, disjoint catalog pages, episode-image URL and intro metadata.
- APK version 0.4.8 / code 1000048; v2/v3 signatures verified. Builder verified original member preservation apart from the declared manifest/classes2/signature changes and added bridge dex.
- Physical phone was updated in place from 0.4.7, preserving app data. Baseline before the update: 12 upward swipes, 281 rendered frames, 33 janky frames (11.74%), 90th/95th/99th percentiles 77/101/121 ms, same app process. This establishes observed frame delays, not a diagnosed crash.
- Follow-up physical-phone test completed after unlocking: 12 upward swipes, 329 frames, 36 janky frames (10.94%), 90th/95th/99th percentiles 57/133/150 ms, same app process. Visible genre rows now show different leading titles instead of the baseline repeats. The janky-frame share and 90th percentile improved modestly, while tail delays worsened in this single pass. The user subsequently reported that scrolling feels better on the installed 0.4.8 build. Do not claim universally smooth scrolling or statistical significance; this single device still shows frame delays.

## Artifact

- File: `Original-UI-AniPM-v0.4.8.apk`
- Size: 50,882,283 bytes
- SHA-256: `54869dfa3a35d9f354f1e24fcd1cd2a9c3d0b19f9641f3e371f45804985aa0db`

## Remaining acceptance

Verify perceived scrolling and frame timings after update, rapid horizontal/vertical gestures, returning from a title, and refreshing with slow/offline providers. The owner confirmed their email and reported that login now works; general signup/email delivery and password recovery remain separate blockers. Earlier native playback/subtitle evidence belongs to 0.4.7; this release did not repeat every playback, profile or cloud-sync scenario. Catalog entries with different AniList sequel IDs remain separate until verified franchise mapping is implemented. A finite Home feed still ends; use Browse/View All for catalog pagination rather than repeating rails to imitate infinite content.
