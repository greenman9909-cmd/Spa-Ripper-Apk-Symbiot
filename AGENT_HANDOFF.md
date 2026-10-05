# Continue the original-UI Android app

Updated 2026-10-05. Read this before changing the app. The user asked to stop feature work, publish the files and leave a concrete continuation guide. This is an experimental backend migration, **not a production-ready app**.

## User requirements

Preserve the original Crunchyroll APK screens, layouts, navigation, profile selector/creator and native player. The earlier web/reconstructed interface was rejected. Use replacement catalog/playback/account services, keep guest access optional, prevent playback from launching ad/browser windows, and complete Home, My Lists, Browse, Simulcasts, search, episode images, real skip-intro metadata, avatars and comments. The user has reported repeated Home rows and title/video errors. Do not report those device-specific failures as fixed without reproducing and checking them.

Publication to this GitHub repository is authorized. No permission is given to delete user data, uninstall their original app, expose credentials, or modify their emulator. WebLoom was paused at the user's request; do not delete or resume it as part of this task.

## Resume point and deliverables

- Repository: `greenman9909-cmd/Spa-Ripper-Apk-Symbiot`.
- Continue branch `codex/evidence-driven-apk-analysis`; draft PR #1 contains the implementation.
- Releases use versioned APK assets and prerelease tags. Release 0.4.4 was the last download before this handoff; 0.4.5 adds Home pagination and these instructions.
- Current output: `android-build/Original-UI-AniPM.apk`. Publish it as `Original-UI-AniPM-v0.4.5.apk`; Android versionName is `0.4.5`, versionCode `1000045`.
- Read `NATIVE_MIGRATION.md` for architecture and historical verification. Some historical counts there refer to earlier builds.

## What works, and what the evidence actually covers

Original startup, profile selection/creation, Home collections, basic Browse/search, series/episodes and local lists run through retained native screens. Local profiles separate watchlist, progress, ratings and lists. Model contracts cover list create/rename/delete, audio versions, next episode, rating data, native availability and HLS mapping.

One Piece episode 1 sub played with decoded frames in the original player on the isolated emulator; pause, seek and landscape fullscreen were exercised on 0.4.3. Bleach episode 1 sub loaded the original player. This does not establish support for every title, language, subtitle or device. The user's generic “Oops” error remains unreproduced.

0.4.5 fixes an identified backend pagination defect: `/home_feed` previously ignored `start` and `n`, returning the hero and all collections again. It now slices one stable feed snapshot, returns its full total, and ends with an empty page. First-page requests can refresh after ten minutes; later pages keep the snapshot. Three regression contracts cover disjoint pages, final-page termination and integer bounds. **Full UI scrolling has not been rechecked on this new build.** Home is still a finite eight-collection feed, not a full infinite catalog.

Current checks: 44 Python tests and 37 contracts against the actual original Android/Gson models passed for 0.4.5. Build verifies v2/v3 signatures. Supabase tests previously checked invalid login and denied private reads; **successful native login/restore is unverified**. Never treat signatures, model contracts or a successful compilation as full product verification.

## Files to change

`build_preserved_apk.py` retains original resources/screens, applies six backend/player hooks and appends adapter DEX. Only manifest root versionName/versionCode are changed; its decoded semantics are checked against the source. Bump `BUILD_VERSION`, `BUILD_CODE` and packaging tests for each release. Do not replace the native UI with HTML.

Adapter sources are in `native_adapter/src/dev/apkforge/bridge/`:

| File | Responsibility and current limitation |
| --- | --- |
| `BackendBridge.java` | Routes intercepted original service requests; unknown routes fail explicitly. Search ignores pagination; Browse uses top 100; categories/seasonal tags are empty. Details cache does not expire separately. |
| `NativeHomeFeed.java` | Eight provider collections; 0.4.5 snapshot pagination fix. Four fetch workers; partial collection failure is tolerated. |
| `NativeCatalog.java` | Series, one synthetic season, episodes/audio/next. Missing names use episode numbers; missing episode photos fall back to series posters. |
| `NativePlayback.java` | Anivexa source resolution, public MegaPlay source-wrapper decoding, bounded background I/O, HLS mapping and media headers. Intro/outro metadata is currently discarded. |
| `LocalProfiles.java`, `NativeLists.java`, `NativeRatings.java` | Guest/cloud storage separation and local profile state. Avatar defaults exist but avatar catalog is missing. |
| `CloudSession.java` | Supabase password login, encrypted tokens, best-effort snapshot backup/initial restore. No signup/recovery or conflict-aware sync. |
| `NativeAccountState.java` | Initializes original guest account observer so blank email banner disappears. |
| `NativeCommunity.java` | Empty comment reads; writes return 501. No comments database exists. |

Tests: `test_analysis.py`, `test_engine.py`, `native_adapter/tests/dev/apkforge/bridge/ModelContractProbe.java`. `verify_native_contracts.py` expects exactly 37 cases; update it when adding cases. `native_ui_probe.py` performs guarded native UI inspection/input.

## Providers and account project

- ani.pm catalog: `https://ani.pm/api/partner/v1`, routes `/top`, `/titles`, `/series/{anilistId}`. Use the existing advertised User-Agent `APKForge/0.4 (Android)`; a default Python UA has returned 403. Public playback documentation is embed-oriented. Do not invent direct native stream endpoints.
- Alternate watch API: `https://anivexaapi-aniko2.hf.space/api/watch/{anilistId}/{sub|dub}/{episode}`. `ssub`/`sdub` contain stream bundles. Candidate links are embeds, not HLS playlists. Existing resolver reads the public source response and validates an actual `#EXTM3U` manifest.
- Playback requires provider User-Agent and Referer on playlists/segments, configured in `NativePlayback.configureMediaFactory`. Keep Kotlin suspension/background workers; synchronous resolution previously caused `NetworkOnMainThreadException`. Do not execute ad scripts or add DRM/license bypasses.
- AniList metadata can support a larger catalog, but it is not a streaming source. Verify primary API schemas and title mappings before adding it. Never fabricate episode images, air dates or intro times.
- Supabase: **Spa-Ripper-AniPM**, project `yhccrdatocqqniblpshm`, URL `https://yhccrdatocqqniblpshm.supabase.co`, region eu-west-3. Public publishable key is already in the app; never embed service-role keys.
- Applied schema: `supabase/migrations/20261005_account_state.sql`. Table `account_state` stores an owner-only JSON snapshot. RLS has four authenticated ownership policies; anonymous reads denied. No other account/comments schema has been deployed.
- Organization: `asimhuma3795-5163's projects`; WebLoom is inactive. Project creation at 0/month was already approved. Do not create another project or introduce billing changes.
- Never use the historical Yoru Supabase project or credentials pasted into chat. Do not commit tokens, passwords, signing keys, original decoded sources or provider signed URLs.

## Highest-priority unfinished work and acceptance checks

1. **Reproduce the user's failure and verify Home.** Record installed APK version and the failed title/audio/episode; inspect sanitized native logs on the isolated device. Scroll through all Home rows, return from series/player, switch tabs, rotate and refresh. No duplicated hero/rows, automatic jump to top, stale-page reuse, request loop or crash. Test 0.4.5 before further UI-related changes.
2. **Real catalog navigation.** Implement original categories, sort/genre filters and seasonal tag models; wire Simulcasts. Add a paged metadata provider or documented provider paging. Search must honor offsets/limits and total. Test several pages, empty results, filter changes, retry and returning from detail. Do not duplicate rows to simulate infinite scrolling. Catalog-only items must honestly report playback availability.
3. **Episode metadata and player controls.** Verify a real per-episode image source/mapping, retain provider audio availability and use actual names/images when present. Read original skip-events model from decoded sources, map real intro/outro ranges from source bundles, and exercise the retained skip button at start/end boundaries. Do not use a generic fixed 90-second intro. Verify next episode, resume, subtitles and dub playback.
4. **Profiles and cloud accounts.** Implement original avatar-catalog endpoints/models so the native picker works. Complete signup/recovery and test sign-in, sign-out, session refresh, restart, two-user isolation and restore on a second isolated install. Improve snapshot conflict handling before claiming multi-device sync. Do not test with the user's pasted password or overwrite existing snapshots.
5. **Comments.** Design owned replacement comment storage with authentication, RLS, validation, pagination and safe deletion/reporting. Match retained comment service/model contracts; test guest read and authenticated write plus cross-user denial. Empty responses are placeholders, not implementation.
6. **Release readiness.** Test network loss/provider errors and multiple Android versions/screen sizes. Resolve unsupported routes needed by real screens, avoid synthetic maturity/audio metadata, document provider limits and production signing strategy. Downloads, music and store are unfinished; scope them explicitly. Production-ready requires these checks, not just a renamed preview.

## Local build and testing

Working repository: `C:\Users\green\Documents\antigravity\mysterious-fermi\Spa-Ripper-Apk-Symbiot`.

Original input: `C:\Users\green\Downloads\com.crunchyroll.crunchyroid_v3.61.0-770_Android-8.0.apk`. Required SHA-256: `9f44b888baf558269eb79a868854ee9f0004a11704df0eafa00d95453f0e87e1`. The similarly named Uptodown APK is the store package and is the wrong input.

Full Apktool decode: `recovered/crunchyroll-code`; optional JADX sources: `recovered/crunchyroll-java/sources` (238 decompilation failures: use smali as truth). Windows long paths may require `\\?\` prefixes. Apktool 3.0.3 is `.tools/apktool.jar`. Android SDK needs platform `android-37.0` and build tools `36.0.0`.

```powershell
$env:PATH='C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin;C:\Users\green\AppData\Local\Android\Sdk\platform-tools;'+$env:PATH
python -m unittest discover -q
python build_preserved_apk.py --reference-apk 'C:\Users\green\Downloads\com.crunchyroll.crunchyroid_v3.61.0-770_Android-8.0.apk' --decoded recovered/crunchyroll-code
python verify_native_contracts.py
python verify_native_contracts.py --stream-live --cloud-negative
python native_ui_probe.py
```

Only mutate **emulator-5560** after checking exact AVD **APKForge_Original_UI**. Never install, uninstall, clear data/logs, rotate or send input to user emulator-5554. The checked-in probe guards AVD identity. If the own emulator is absent, start that AVD or stop native tests; do not select an arbitrary connected device.

Signing key and tools are ignored local build dependencies. The development-signed APK cannot update an official installation. It can update earlier previews using the same local key; do not uninstall the user's official package to make installation succeed. Preserve that key privately for preview updates.

## Publishing the next increment

Commit source/tests/docs on the existing branch and push. Keep PR #1 attached; update its body to final implementation and evidence. Copy the new APK to a versioned filename, compute SHA-256, attach provenance and truthful validation notes. Create an experimental GitHub prerelease with a **full 40-character commit SHA** as `--target` (a short SHA has failed). Use a body file for multiline notes. Do not overwrite older release assets or publish signed temporary URLs as permanent download links.

`.tools/verify_release.py` is stale: it assumes an unchanged manifest and 4,666 preserved members. Since 0.4.4, only version attributes change and the preserved-member count is 4,665, excluding manifest, classes2.dex, signing stamp and META-INF. Update its assumptions before using it. Compare `version_manifest(originalManifest)` against the built manifest and all other original members byte for byte. New release notes must distinguish earlier playback evidence from new runtime checks.
