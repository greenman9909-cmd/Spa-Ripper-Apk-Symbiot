# Original-UI app continuation guide

Updated 2026-10-06 for **0.4.9 / 1000049**. The user wants production readiness; this build remains a tested preview. Compilation, model contracts and isolated playback are not complete product acceptance.

## Latest requirements

Preserve the supplied APK's original screens, resources, profile picker/creator, navigation and native player. The reconstructed HTML interface was rejected. Use AniList metadata, ani.pm availability and the existing native resolver without executing remote embed/ad pages or launching browsers.

**Require an account; remove guest access.** This supersedes earlier optional-login requests. Prefer hard subtitles when explicitly available in the selected language, allowing separate tracks otherwise. Spanish and other real provider languages are wanted. Never invent availability or claim unaudited translation accuracy.

Continue branch `codex/evidence-driven-apk-analysis`, draft PR [#1](https://github.com/greenman9909-cmd/Spa-Ripper-Apk-Symbiot/pull/1). Publishing here is authorized. Release `v0.4.9-native-preview`; APK `Original-UI-AniPM-v0.4.9.apk`. Never use credentials pasted in chat, uninstall the user's official app, expose secrets or modify their emulator. The user explicitly authorized USB debugging and updating their existing preview on their physical phone; retain that narrow scope and verify the intended device before each mutation. WebLoom was paused with authorization; do not delete/resume it or change billing.

## Implementation and evidence

| Area | Current behavior and limits |
| --- | --- |
| Original UI | Twelve narrow backend/session/image/download hooks. Original screen code/layouts/resources/fonts/player retained; current builder reports 4,782 original members byte-identical, excluding declared manifest/classes2/signature changes and removed stamp. |
| Artwork | Retained Glide worker crops replacement wide/search/episode images to 16:9, scales down only (max 1280 wide), with bounded fetch/decode and original disk/memory cache. Raw panoramas previously rendered as thin strips. Actual AniList extraLarge tall art retained; no invented pixels. Latest available verified TV member supplies grouped details/search art. Original licensed logo art is absent, retaining title typography. Final visual acceptance postponed; source coverage/quality vary. |
| Home | 16 collections, up to 14 distinct cards per row, excluding IDs already displayed in earlier collections. Live 0.4.8 feed: 16 rails / 179 cards. The featured hero may also feature a card from the popular row. Two-minute freshness replaces 15-second whole-feed reloads. A warm expired feed returns immediately while one background task prepares a replacement; only a later page-zero request installs it. Later pages keep the active snapshot. No global AniList discovery-cache invalidation. Original hero payload uses the native PANEL/UNDEFINED handler. See 0.4.8 phone frame measurements; do not infer all scroll/crash reports resolved from process survival. |
| Memory/locking | Responses capped at 4 Mi characters, metadata at 3 Mi characters; eight full episode lists retained, older titles keep summary/season metadata. Summary updates retain the episode-array reference instead of serializing entire long series under the shared artwork/title lock. Home network work runs outside its snapshot lock. Subtitle preferences resolved before playback-cache locking. No user crash trace established a root cause. |
| Discovery | AniList paged search/Browse, genres/sorts and quarter-based Simulcasts. Two disjoint 25-item pages checked live. Browse, Simulcasts, My Lists and Account opened natively in final build. Offline fallback bounded top-100 with limited paging/filtering. Catalog metadata does not establish playable video. |
| Episode metadata | AniZip mapping must match AniList ID. Real names/photos/descriptions/dates preserve provider availability. Details refresh after 120 seconds; valid old details survive I/O failure. Missing metadata retains fallbacks. |
| Seasons | NativeFranchises follows explicit unambiguous same-format TV PREQUEL/SEQUEL edges, excluding movies/OVAs/unreleased entries. Grouped details retain source IDs/episode numbering; next crosses series-ID boundaries. Search collapses known families; Home/Browse can still show separate sequels. Final live One-Punch Man: three available seasons, 12 episodes each, first assets ANI21087E1 / ANI97668E1 / ANI153800E1. Black Clover original 170 episodes retain 51/51/52/16 groups. Branches and very large families are bounded, not universal semantic season mapping. |
| Playback/skips | Original native HLS player, provider headers and real skip metadata; resolution off UI thread. Black Clover S2 E52 played in final 0.4.7. Naruto/Shippuden episode 1 played in earlier 0.4.6 reproduction. Previous checks covered One Piece/Frieren and pause/seek/fullscreen. User Oops/fallback remains unreproduced. |
| Subtitles | Separate ASS tracks now use bold white text/black outline/bottom-center alignment. 35 recognized language bases plus regional/script tags including es-ES/es-419/zh-TW/zh-CN. Only actual tracks exposed. English/both Spanish files fetched/converted live; Spain Spanish dialogue visibly verified in previous Frieren check. Extra labels do not create translations. VTT-to-ASS simplifies styles/positions but preserves timings/Unicode/line breaks. No linguistic accuracy audit or video burn-in. |
| Downloads/capabilities | Confirmed replacement accounts receive free local full-capability/offline flags; no official subscription/purchase/license. NativeDownloads validates complete non-DRM HLS, selects actual quality, saves actual subtitles privately and uses retained queue/cache callbacks and provider headers. Completed metadata is owner-associated. Final HLS manifest/model/signature checks pass; physical completion/offline playback/restart/delete/account-profile isolation are NOT accepted yet. |
| Hard subs | Explicit matching hardsub_locale preferred. No tested live source advertised it; never claim hard subs for all episodes. Separate tracks allowed. |
| Accounts | Original landing without encrypted session. Confirmed own fixture login, picker, restart/update persistence and logout tested. The owner subsequently confirmed their own email and reported successful access on their physical phone. Content/account/community routes require real cloud token. Primary cloud profile defaults/migrates to Profile 1. Signup/email enabled, confirmation required, anonymous users disabled. General signup/email delivery readiness and recovery remain unverified. |
| Profiles/avatars | Original profile UI, five-profile limit; original avatar picker populated with catalog artwork. Previous save/cloud avatar snapshot verified. Not the original licensed character-avatar catalog. Fresh-device restore and concurrent multi-device sync incomplete. |
| Comments | Previous native reads/replies/like/unlike/own-delete and SQL cross-owner denial checked. Numeric native IDs, private votes, owner writes and rate limit. Database permits public reads; app now requires login. Popular ranking/moderation/report administration incomplete. |

Current validation: **44 Python tests, 93 original-model contracts**, final live genre/franchise/non-DRM HLS manifest check, v2/v3 signatures. Earlier 0.4.9 candidate Home/catalog/Frieren English-and-Spanish conversion checks passed. A repeated final catalog test failed with **Metadata HTTP 429** before Home/subtitle tests ran; an earlier combined run failed at genre-artwork acquisition. Do not treat an app_process probe failure as an observed physical-app crash, or earlier passes as guaranteed service availability. See [VALIDATION_0.4.9.md](VALIDATION_0.4.9.md).

## Immediate next session: physical acceptance

The user postponed USB testing until tomorrow. A preliminary 0.4.9 APK reopened signed-in native Home after an in-place update. Final source/asset includes later next-episode and download-validator changes and has NOT been installed on that phone. Do not claim the final search fix or offline playback visibly accepted.

After the owner connects/unlocks the intended phone, verify its identity, update in place with `adb install --no-incremental -r`, then inspect current UI before each action. Check search images (One-Punch Man/Naruto), S1/S2/S3 selector and source episode clicks, next across seasons, and real Spanish subtitle rendering. Start one download, await completion, verify offline playback/subtitles after restart, delete it and test account/profile isolation. Measure Home scrolling again. Never bypass a lock screen, change unrelated settings, uninstall/wipe data, reuse chat credentials or operate a different device.

Downloads still need expiry/retry behavior, disk-limit/cleanup and deletion of private subtitle files checked. Original queue/index is shared; adapter completion metadata is owner-specific, which does not establish UI isolation. Restart/offline episode-catalog loading may still require online metadata. A failing subtitle fetch currently aborts preparation. Add provider Retry-After/backoff and last-good metadata handling; 17 live genre tests plus other repeated fresh-process queries can hit AniList rate limits. Preserve real errors and do not hammer the API until a test passes.

## Production blockers, in priority order

1. **Public account creation:** owner dashboard is connected; custom SMTP was disabled at last inspection. Default SMTP restricts recipients to project-team members. User has a provider and will configure SMTP directly. HTTPS return page is deployed and saved as Site URL with explicit user approval. The owner received an authorized resend, confirmed their account and reported successful login. The return page now distinguishes an invalid/used link from actual account status and recommends trying native login first. See [AUTH_SETUP_REPORT.md](AUTH_SETUP_REPORT.md) for evidence and six passing page tests. General signup/delivery, duplicate/pending email, resend UI and recovery still need acceptance. Do not silently auto-confirm users or create fake sessions. Pending signup returns a notice and HTTP 409; original error UI needs improvement.
2. **Crash/Oops reproduction:** obtain affected version/device/title/audio/episode and a redacted trace. Own AVD did not reproduce crash/fallback. Test return navigation, rotation, deep scrolling, rapid title changes, resume, slow network, outages/retries across Android versions. Do not claim every user bug fixed from one device run.
3. **Blank Not now banner:** not reproduced with confirmed fixture. Original email-verification banner is a hypothesis, not a finding. No forced-dismiss hook was added. Verify pending-confirmation UI before removing it.
4. **Catalog acceptance:** verify the new grouped search/detail behavior on the phone, ambiguous branches, many seasons, missing/unmapped seasons and source/audio IDs. Home/Browse still can display related entries separately. Search stores at most 2,000 raw records and fetches at most four pages per call; late-discovered prequels can change root/order, so stress paging stability. Distinguish unavailability from HTTP 429/outages; test Naruto explicitly.
5. **Cloud state:** fresh-device restore, two-user UI isolation, concurrent edits and sync failure/retry UX. History currently returns an empty list although playheads persist; implement actual original-model history. Download preparation is implemented but offline acceptance is pending; music/store remain unsupported. Do not advertise accepted downloads until completion, offline playback and isolation pass.
6. **Player acceptance:** visible Latam Spanish/English/other languages, off, seek, next, restart, dub, actual skip click/boundaries and genuine matching hard-sub source. Provider streams can disappear; verify clear failure/retry UX.
7. **Release acceptance:** moderation/reporting, sorting, account deletion/password change, notifications, stale original-service links/subscription copy, supported-device matrix and controlled production signing/updates. Keep prerelease status until acceptance passes.

The signup shell test was rejected by automatic approval review with only “blocked by policy.” It was not retried through another mechanism. A separate own confirmed database fixture, sending no email, tested login/content only. Do not cite it as signup verification.

## Services and compatibility traps

Sources: `native_adapter/src/dev/apkforge/bridge/`. BackendBridge routes/caches; NativeDiscovery/NativeMetadata query AniList/AniZip; NativeArtwork maps original image builder; NativeHomeFeed/NativeCatalog/NativeSeasons/NativeFranchises build screen models and verified season groups; NativePlayback/NativeSubtitles/NativeDownloads handle HLS/skips/tracks/offline preparation; CloudSession/LocalProfiles manage encrypted auth/profiles; NativeCommunity/NativeLists/NativeRatings/NativeAccountState manage state.

- ani.pm: `https://ani.pm/api/partner/v1`, top/titles/series. Existing advertised UA matters; default Python UA can return 403. Public iframe docs do not establish native direct streams.
- Metadata: `https://graphql.anilist.co` POST; `https://api.ani.zip/mappings?anilist_id=ID`. Neither establishes video availability.
- Resolver: `https://anivexaapi-aniko2.hf.space/api/watch/ID/sub-or-dub/EP`, ssub/sdub. Validate HLS and apply provider headers to media/subtitles. Do not add DRM/license bypasses.
- Supabase **Spa-Ripper-AniPM**, **yhccrdatocqqniblpshm**, `https://yhccrdatocqqniblpshm.supabase.co`, eu-west-3, approved zero/month. Public publishable key in client; NEVER embed service-role keys.

Six checked-in migrations deployed: snapshots, comments, rate limit, votes, native IDs and avatars. Snapshots owner-private; comments public read/owner write/delete. Votes private; fixed-search-path helper exposes aggregates/caller flags. Earlier advisor warned leaked-password protection disabled; subsequent advisor returned no lints. Verify Auth security separately before production without silently adding paid features.

- Honor Home start/n and stable snapshot; never repeat hero/rows to fake infinite scroll.
- Preserve Kotlin suspension/bounded workers: synchronous I/O previously caused NetworkOnMainThreadException.
- Subtitle overlay is original bundled local WebView/WASM libass, NOT remote embed playback. It expects ASS; convert on request worker; retain original HTML/WASM/fonts.
- Talkbox requires yyyy-MM-dd'T'HH:mm:ssZ with +0000. PostgreSQL fractional/plain-Z dates previously crashed its separate adapter.
- Native comment RecyclerView IDs must be numeric longs; UUIDs remain internal and are mapped for writes/RPC.
- AniList total/lastPage unreliable; use hasNextPage/growing lower bound.
- Episode versions/access dates must match chosen audio. Adapter availability dates are not broadcast dates.
- Artwork hook must not network on UI thread; retain builder behavior for other IDs.

Black Clover English release group sources: [season 1](https://store.crunchyroll.com.au/products/black-clover-complete-season-1-eps-1-51-blu-ray.html), [season 2](https://store.crunchyroll.com/products/black-clover--season-2--bluray-704400103360.html), [season 3](https://store.crunchyroll.com/products/black-clover-season-3-complete-collection-bluray-704400107818.html). Bound to original 170 episodes, never a future continuation.

## Build and safe device work

Workspace: `C:\Users\green\Documents\antigravity\mysterious-fermi\Spa-Ripper-Apk-Symbiot`.
Original: `C:\Users\green\Downloads\com.crunchyroll.crunchyroid_v3.61.0-770_Android-8.0.apk`.
Original SHA256: `9f44b888baf558269eb79a868854ee9f0004a11704df0eafa00d95453f0e87e1`.
Uptodown is the wrong package. Decode: recovered/crunchyroll-code; optional JADX recovered/crunchyroll-java/sources has 238 failures, smali is truth. SDK android-37.0/build-tools 36.0.0, apktool .tools/apktool.jar.
JDK: `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin`; ADB: `C:\Users\green\AppData\Local\Android\Sdk\platform-tools`.

```powershell
python -m unittest discover -q
python verify_native_contracts.py
python verify_native_contracts.py --franchise-live
python verify_native_contracts.py --catalog-live --subtitles-live
# Space live batches to respect provider rate limits; no automatic retry loop.
python build_preserved_apk.py --reference-apk 'C:\Users\green\Downloads\com.crunchyroll.crunchyroid_v3.61.0-770_Android-8.0.apk' --decoded recovered/crunchyroll-code
python native_ui_probe.py
```

Only mutate **emulator-5560** after verifying exact AVD **APKForge_Original_UI**. Never operate emulator-5554, an arbitrary fallback or the user's official app. UI probe masks passwords/refuses stale-null trees; transient activity transitions need fresh retry. Duplicate text needs observed current bounds/resource targeting.

Builder verifies input SHA, retains members, changes root manifest versions, rebuilds classes2/appends classes5. Twelve hooks: s70/e, OkHttp factory, token storage, cr/l, cr/g, ll/a, do/b, gk/b, CloudflareImagesBuilder, BestImageModelLoader, ExoPlayerLocalVideosManagerImpl and uy/a. Output SHA256: `3da1401399ff1f4ad1fc769124c814cd8b8f5f3ac72101812991b2824eebb90c`; **50,898,667 bytes**. Provenance keeps runtimeVerified/migrationComplete false.

Keep signer private; same development key updates earlier previews, not official APK. Preserve preexisting untracked artifacts/. Never publish decoded original sources, fixture credentials/tokens/private logs/signed media URLs. android-build and .tools ignored.

Next release: bump version/code/tests, commit explicit source/tests/migrations/docs on existing branch and push; attach PR #1. Publish unique versioned APK/provenance/validation/report assets as a prerelease targeting full 40-character commit SHA. Never overwrite old downloads. .tools/verify_release.py has stale member assumptions; use current builder checks. Verify uploaded size/digest.
