# Original-UI app continuation guide

Updated 2026-10-06 for **0.4.7 / 1000047**. The user wants production readiness; this build remains a tested preview. Compilation, model contracts and isolated playback are not complete product acceptance.

## Latest requirements

Preserve the supplied APK's original screens, resources, profile picker/creator, navigation and native player. The reconstructed HTML interface was rejected. Use AniList metadata, ani.pm availability and the existing native resolver without executing remote embed/ad pages or launching browsers.

**Require an account; remove guest access.** This supersedes earlier optional-login requests. Prefer hard subtitles when explicitly available in the selected language, allowing separate tracks otherwise. Spanish and other real provider languages are wanted. Never invent availability or claim unaudited translation accuracy.

Continue branch `codex/evidence-driven-apk-analysis`, draft PR [#1](https://github.com/greenman9909-cmd/Spa-Ripper-Apk-Symbiot/pull/1). Publishing here is authorized. Release `v0.4.7-native-preview`; APK `Original-UI-AniPM-v0.4.7.apk`. Never use credentials pasted in chat, uninstall the user's official app, expose secrets or modify their emulator. WebLoom was paused with authorization; do not delete/resume it or change billing.

## Implementation and evidence

| Area | Current behavior and limits |
| --- | --- |
| Original UI | Nine narrow backend/session/image hooks. Original screen code/layouts/resources/fonts/player retained; 4,665 original non-signature members byte-identical, excluding manifest/classes2/removed stamp. |
| Artwork | NativeArtwork hooks the original Cloudflare builder for adapter IDs, using cached banners/posters/episode images. Avoids official-CDN ANI paths. AniList extraLarge with null-safe fallback. Logos return empty for original title typography. Naruto search artwork visibly loaded in final 0.4.7. Quality/coverage depends on source. |
| Home | 16 collections, up to 32 cards per provider rail; airing/season use 20. Finite pagination uses a stable snapshot. Page zero refreshes after 15-second cooldown; later pages retain the snapshot. Failed refresh keeps valid old data. Thirty rapid vertical swipes kept the same PID in final 0.4.7; this does not conclusively solve the user's unreproduced crash. |
| Memory/locking | Responses capped at 4 Mi characters, metadata at 3 Mi characters; eight full episode lists retained, older titles keep summary/season metadata. Subtitle preferences resolved before playback-cache locking. Regression contracts passed. These mitigate plausible causes; no user crash trace established a root cause. |
| Discovery | AniList paged search/Browse, genres/sorts and quarter-based Simulcasts. Two disjoint 25-item pages checked live. Browse, Simulcasts, My Lists and Account opened natively in final build. Offline fallback bounded top-100 with limited paging/filtering. Catalog metadata does not establish playable video. |
| Episode metadata | AniZip mapping must match AniList ID. Real names/photos/descriptions/dates preserve provider availability. Details refresh after 120 seconds; valid old details survive I/O failure. Missing metadata retains fallbacks. |
| Seasons | AniZip groups plus original Black Clover 97940 English release divisions: 1–51, 52–102, 103–154, 155–170. Original picker showed 51/51/52/16. S2 selected, E52 loaded/played natively in final 0.4.7, E53 shown next. Absolute provider IDs preserved. Separate AniList sequels are NOT universally merged. |
| Playback/skips | Original native HLS player, provider headers and real skip metadata; resolution off UI thread. Black Clover S2 E52 played in final 0.4.7. Naruto/Shippuden episode 1 played in earlier 0.4.6 reproduction. Previous checks covered One Piece/Frieren and pause/seek/fullscreen. User Oops/fallback remains unreproduced. |
| Subtitles | 35 recognized language bases plus regional/script tags including es-ES/es-419/zh-TW/zh-CN. Only actual tracks exposed. English/both Spanish files fetched/converted live; Spain Spanish dialogue visibly verified in previous Frieren check. Extra labels do not create translations. VTT-to-ASS simplifies styles/positions but preserves timings/Unicode/line breaks. No linguistic accuracy audit or video burn-in. |
| Hard subs | Explicit matching hardsub_locale preferred. No tested live source advertised it; never claim hard subs for all episodes. Separate tracks allowed. |
| Accounts | Original landing without encrypted session. Confirmed own fixture login, picker, restart/update persistence and logout tested. Content/account/community routes require real cloud token. Primary cloud profile defaults/migrates to Profile 1. Public Auth settings: signup/email enabled, confirmation required, anonymous users disabled. Signup/email/recovery end-to-end UNVERIFIED. |
| Profiles/avatars | Original profile UI, five-profile limit; original avatar picker populated with catalog artwork. Previous save/cloud avatar snapshot verified. Not the original licensed character-avatar catalog. Fresh-device restore and concurrent multi-device sync incomplete. |
| Comments | Previous native reads/replies/like/unlike/own-delete and SQL cross-owner denial checked. Numeric native IDs, private votes, owner writes and rate limit. Database permits public reads; app now requires login. Popular ranking/moderation/report administration incomplete. |

Validation: **44 Python tests, 69 original-model contracts**, live catalog/HLS/English-and-Spanish conversion, invalid-login/private-read denial, v2/v3 signatures. Own confirmed fixture logged out/deleted after checks. See [VALIDATION_0.4.7.md](VALIDATION_0.4.7.md).

## Production blockers, in priority order

1. **Public account creation:** owner dashboard is now connected. Custom SMTP was disabled; default SMTP restricts recipients to project-team members. Site URL was localhost with no redirect allowlist. User has a provider and will configure SMTP directly. A privacy-preserving HTTPS return page is deployed; approval to use it as Site URL was requested. See [AUTH_SETUP_REPORT.md](AUTH_SETUP_REPORT.md) for actual deployment status and six passing return-page tests. Real signup, delivery, confirmation, duplicate/pending email, resend and recovery remain unverified. Do not silently auto-confirm users or create fake sessions. Pending signup currently returns a notice and HTTP 409; original error UI needs acceptance.
2. **Crash/Oops reproduction:** obtain affected version/device/title/audio/episode and a redacted trace. Own AVD did not reproduce crash/fallback. Test return navigation, rotation, deep scrolling, rapid title changes, resume, slow network, outages/retries across Android versions. Do not claim every user bug fixed from one device run.
3. **Blank Not now banner:** not reproduced with confirmed fixture. Original email-verification banner is a hypothesis, not a finding. No forced-dismiss hook was added. Verify pending-confirmation UI before removing it.
4. **Catalog unification:** map related AniList sequel IDs into verified season groups retaining correct source/audio/episode IDs. Search summaries may show one season before enriched details. Distinguish unavailable/unmapped titles from network errors; test Naruto/provider outages explicitly.
5. **Cloud state:** fresh-device restore, two-user UI isolation, concurrent edits and sync failure/retry UX. History currently returns an empty list although playheads persist; implement actual original-model history. Downloads/music/store unsupported; do not advertise working features.
6. **Player acceptance:** visible Latam Spanish/English/other languages, off, seek, next, restart, dub, actual skip click/boundaries and genuine matching hard-sub source. Provider streams can disappear; verify clear failure/retry UX.
7. **Release acceptance:** moderation/reporting, sorting, account deletion/password change, notifications, stale original-service links/subscription copy, supported-device matrix and controlled production signing/updates. Keep prerelease status until acceptance passes.

The signup shell test was rejected by automatic approval review with only “blocked by policy.” It was not retried through another mechanism. A separate own confirmed database fixture, sending no email, tested login/content only. Do not cite it as signup verification.

## Services and compatibility traps

Sources: `native_adapter/src/dev/apkforge/bridge/`. BackendBridge routes/caches; NativeDiscovery/NativeMetadata query AniList/AniZip; NativeArtwork maps original image builder; NativeHomeFeed/NativeCatalog/NativeSeasons build screen models; NativePlayback/NativeSubtitles handle HLS/skips/tracks; CloudSession/LocalProfiles manage encrypted auth/profiles; NativeCommunity/NativeLists/NativeRatings/NativeAccountState manage state.

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
python verify_native_contracts.py --stream-live --catalog-live --subtitles-live --cloud-negative
python build_preserved_apk.py --reference-apk 'C:\Users\green\Downloads\com.crunchyroll.crunchyroid_v3.61.0-770_Android-8.0.apk' --decoded recovered/crunchyroll-code
python native_ui_probe.py
```

Only mutate **emulator-5560** after verifying exact AVD **APKForge_Original_UI**. Never operate emulator-5554, an arbitrary fallback or the user's official app. UI probe masks passwords/refuses stale-null trees; transient activity transitions need fresh retry. Duplicate text needs observed current bounds/resource targeting.

Builder verifies input SHA, retains members, changes root manifest versions, rebuilds classes2/appends classes5. Nine hooks: s70/e, OkHttp factory, token storage, cr/l, cr/g, ll/a, do/b, gk/b, CloudflareImagesBuilder. Output SHA256: `6b39f0bbf8bcef86045c4feefa9e2242760269e77d247ff7337318ea8ea2d92b`; **50,878,187 bytes**. Provenance keeps runtimeVerified/migrationComplete false.

Keep signer private; same development key updates earlier previews, not official APK. Preserve preexisting untracked artifacts/. Never publish decoded original sources, fixture credentials/tokens/private logs/signed media URLs. android-build and .tools ignored.

Next release: bump version/code/tests, commit explicit source/tests/migrations/docs on existing branch and push; attach PR #1. Publish unique versioned APK/provenance/validation/report assets as a prerelease targeting full 40-character commit SHA. Never overwrite old downloads. .tools/verify_release.py has stale member assumptions; use current builder checks. Verify uploaded size/digest.
