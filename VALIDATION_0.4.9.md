# Native artwork, seasons and download preview 0.4.9

Updated 2026-10-06. Version **0.4.9 / 1000049**. This is a prerelease; it is not complete production acceptance.

## Changes

- Wide search/series/episode artwork now goes through the retained Glide loader. The worker fetches the real source, crops it to 16:9 and scales down only, at most 1280 pixels wide. This addresses the previously observed extremely thin panorama images without changing the original screen layouts. Tall posters still use actual extra-large provider artwork. Small source images are not upscaled or presented as newly created HD images.
- Search collapses explicit, unambiguous same-format TV prequel/sequel relationships. Series details expose the available episodes in the original season picker, retaining each season's source ID and episode number. Next episode crosses source IDs at a season boundary. The latest available member supplies the grouped detail artwork. Movies, OVAs, ambiguous branches and unreleased related entries are not guessed into seasons. Home/Browse can still contain separate sequel entries; this is not universal franchise consolidation.
- All 17 genre cards request actual artwork, and their routes retain the selected filter. A provider failure leaves the categories clickable rather than failing the entire page.
- Confirmed replacement accounts receive the local full-capability/offline flags used by the retained UI. This grants no official subscription, purchase, token, license or access to official media.
- Download preparation selects a real HLS quality variant, accepts complete VOD playlists without DRM, saves actual subtitle tracks as private local ASS files and hands the validated source to the retained download queue/cache. Completed-download metadata is associated with the authenticated owner. Live/SAMPLE-AES/non-identity DRM playlists are rejected. Queue completion and offline playback have not passed device acceptance yet.
- Separate subtitles use bold white text, black outline and bottom-center alignment, as requested. Original cue times, line breaks and Unicode remain; complex source styles/positions are simplified. Burned-in subtitle appearance belongs to the source video. No automated translation or burn-in was added.
- Retains the 0.4.8 Home snapshot/deduplication fixes, required Supabase login, original screens/fonts/resources and native video player.

## Verification

- 44 Python tests passed.
- 93 contracts passed against the actual original Android models on the isolated AVD. Cases include decoded artwork proportions/no upscaling, the Glide fetcher contract, season source IDs, next across sequel boundaries, dubbed next, the retained capability checks, HLS quality/DRM rejection, download-response local subtitle fields, exact original callback/index signatures, and subtitle styling.
- Live checks made during this implementation verified 17 nonempty correctly filtered genre lists and their original artwork models; One-Punch Man's three available seasons, 12 episodes each, source IDs 21087/97668/153800; and an actual complete non-DRM HLS variant for episode 1.
- That One-Punch Man episode exposed 18 actual subtitle locales: ar-SA, bg-BG, zh-CN, da-DK, nl-NL, en-US, fi-FI, fr-FR, de-DE, he-IL, id-ID, no-NO, pl-PL, pt-PT, es-ES, sv-SE, th-TH and tr-TR. This is one episode's availability, not a guarantee for every title. Japanese sub and English dub are supported when returned; no Spanish dub was found.
- Live Home/catalog checks on the earlier 0.4.9 candidate verified 16 rails / 179 distinct collection cards, stable paging, three disjoint catalog pages including a provider-page boundary, an actual episode image and intro metadata. Frieren E1 English, Spain Spanish and Latin American Spanish files were fetched and converted. The final APK passed the 93 contracts and the live genre/franchise/manifest check. Its final repeated catalog check received **Metadata HTTP 429** before Home/subtitle checks could run; those checks were not represented as passing again. An earlier combined run also failed at genre-artwork acquisition, while the separate final genre run passed. These show real provider availability/rate-limit dependencies, not a diagnosed physical-app crash.
- APK v2/v3 signatures passed; builder verified 4,782 original members byte-identical with declared manifest/classes2/signature changes and the added bridge dex. Twelve narrow hooks are recorded in provenance. Original screen implementations/resources remain retained.

## Physical phone status and next acceptance

A preliminary 0.4.9 build was installed in place, retaining the owner's signed-in app data; Home reopened with native poster rails. The final APK includes subsequent next-episode/download validation changes and has **not** been installed on that phone. The USB device disconnected before the final search/season/download test, and the user postponed physical testing until tomorrow. No later phone actions were performed.

Next session: update in place using `adb install --no-incremental -r` after verifying the intended device; do not uninstall or wipe. Confirm search artwork and One-Punch Man's season picker, open S2 E1 and test next across seasons. Download one episode, wait for completion, then verify offline playback, subtitles, restart, delete and account/profile isolation. Measure scrolling again and check an actual skip-intro click. Keep traces scoped to this app and redact credentials, tokens and signed media URLs.

Public signup/email delivery/recovery, fresh-device cloud restore, conflict-aware sync, moderation, provider-rate-limit/backoff and outage UX, and the supported-device matrix remain production blockers. Earlier 0.4.8 phone scroll measurements and user feedback are historical evidence, not a fresh 0.4.9 scrolling test.

## Artifact

- File: `Original-UI-AniPM-v0.4.9.apk`
- Size: 50,898,667 bytes
- SHA-256: `3da1401399ff1f4ad1fc769124c814cd8b8f5f3ac72101812991b2824eebb90c`
- Provenance intentionally leaves `runtimeVerified` and `migrationComplete` false.
