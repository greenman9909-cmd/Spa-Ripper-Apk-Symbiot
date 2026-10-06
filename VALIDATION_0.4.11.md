# Native hybrid playback preview 0.4.11

Updated 2026-10-06. Version **0.4.11 / 1000051**. This remains a preview.

## Changes

- Keep the original native screens, resources, image views and player. No remote embed page or browser playback was added.
- When ani.pm episode availability fails or returns a malformed series response, use the episode API of the existing native playback provider. AniList still supplies metadata/artwork and ani.pm remains preferred.
- Validate matching positive MAL identity and each episode's exact AniList source ID/audio. Reject the provider's no-MAL branch because its public implementation guesses audio availability. Merge only actual listed audio records, deduplicate numbers, preserve sparse absolute numbering and real photos/durations/descriptions. Invalid/empty availability keeps native metadata-only details.
- Share short-lived watch bundles between playback and skip metadata. Concurrent requests fetch once and receive independent JSON copies. Media URLs remain in bounded memory: 16 entries / 2 Mi characters, 45-second freshness and five-second failure cooldown. No media tokens enter the persistent public metadata cache.
- Optional skip metadata does not initiate a second full media resolution. Real bundle intervals remain available; source-only intervals become available after ordinary stream resolution. No generic intro duration is invented.
- Accept declared direct HLS with the media factory's matching Referer after verifying its manifest. Preserve the existing source resolver fallback without executing embed scripts. Sources needing other headers are not silently played with the wrong headers.
- Coalesce franchise detail/up-next loading. Fetch related available seasons on three bounded workers with an 18-second shared deadline, preserve source order and cancel pending work. Retry incomplete groups after 15 seconds. Missing requested availability cannot substitute another playable season.
- Disconnect image downloads when Glide cancels an offscreen image. Check cancellation before bitmap decoding, during reads and before opening a canceled connection. Preserve size limits, cropping and original views.
- Preserve bold white / black outline separate-subtitle styling, real SRT/VTT/ASS conversion and matching hard-sub preference. Burned-in source text cannot be restyled; available languages remain episode-specific.

## Evidence

The final APK passes **44 Python tests**, **141 contracts on the actual original Android models**, and **v2/v3 signatures**. The six original callback cases and landing/anchor/network isolation also pass. Builder verification retains **4,782 original members byte-identical**, with the same twelve declared hooks. `runtimeVerified` and `migrationComplete` remain false.

- Android live secondary availability: Naruto **220**, Frieren **28**, Bleach **366** episodes, exact native source/audio IDs, real thumbnail URLs and navigable season models. These are provider observations, not universal playback guarantees.
- One Piece E1 HLS manifest/model and Frieren E1 English, Spain Spanish and Latin American Spanish subtitle fetch/conversion passed.
- The preceding 0.4.11 candidate, authenticated in Medium_Phone_API_37.0_2, opened Naruto's original details/player. Captured frames changed and time advanced to 0:50. E1 navigated to E2 with E3 shown as next. This establishes visible E1 playback and next-episode navigation, not full E2 or offline acceptance.
- Naruto E1's native subtitle selector exposed **English and None only**. Do not advertise Spanish for that episode. The Frieren checks fetched/converted real Spanish cues; visible rendering and linguistic accuracy were not established.
- Android Studio subsequently replaced the test app with **9.99.0 / 999999**, showing the old connection error. Extracted certificate digests differed from our signer; that error did not come from our installed preview. The owner stopped Run and authorized an app-only reinstall in this AVD, acknowledging local-data loss. Final installation/login evidence follows below.
- An intermediate cache-only request was withdrawn by the owner. Its attempted command did not return success and was stopped. No cache-success claim is made; no further cache deletion is part of this work.
- With the owner's new explicit approval, only the differently signed preview package was removed from Medium Phone 2 and the final 0.4.11 installed. The original login screen opened. The owner must enter credentials directly; chat credentials were not reused. Signed-in final acceptance is pending.

## Remaining work

Accept final signed-in Home refresh/scroll, grouped season clicks, visible Spanish/style/fullscreen/skip timing, a completed download followed by offline restart/deletion and account/profile isolation. Physical-phone acceptance remains pending. Production blockers in VALIDATION_0.4.10.md remain: public signup/email/recovery, cloud restore/conflicts, offline cleanup/quotas/expiry, moderation/account actions and device matrix. ani.pm remains unavailable with validated TLS on this test network. No certificate or network-filter bypass was installed.

## Primary contract

Verified against the deployed service and its public [server.js](https://huggingface.co/spaces/anivexaapi/aniko2/blob/main/server.js) and [scraper.js](https://huggingface.co/spaces/anivexaapi/aniko2/blob/main/scraper.js). GET `/api/episodes/{anilistId}` returns `meta.malId` and `episodes.sub` / `episodes.dub`. GET `/api/watch/{anilistId}/{audio}/{episode}` returns the existing watch bundle. No new proxy was introduced.

## Artifact

- `Original-UI-AniPM-v0.4.11.apk`: **50,923,243 bytes**.
- SHA-256: `4f1214a6309488b6f7fe14ba00830a88c47aacbb98b205c570737e3bf99e3fe6`.
- Same development signer as earlier previews. No official signature/subscription is represented.
