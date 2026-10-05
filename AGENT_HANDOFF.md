# Original-UI app continuation guide

Updated 2026-10-05 for **0.4.6 / 1000046**. The user resumed development after the earlier handoff and requested multilingual subtitles, preferring hard subs when available and allowing separate tracks. This preview is not production-ready.

## Requirements and publication

Preserve the supplied APK's original screens, layouts, profile picker/creator, navigation and native player. The reconstructed HTML interface was rejected. Keep guest access optional; use replacement catalog/media/Supabase services without executing provider ad pages or launching browsers.

Continue branch codex/evidence-driven-apk-analysis, draft PR #1, repository greenman9909-cmd/Spa-Ripper-Apk-Symbiot. Publication here is authorized. Release: v0.4.6-native-preview; APK Original-UI-AniPM-v0.4.6.apk. Do not use credentials pasted into chat, uninstall the user's official app, expose secrets, or modify their emulator. WebLoom was paused; do not delete/resume it or change billing.

## Implemented and tested

| Area | Current behavior and evidence |
| --- | --- |
| Hybrid discovery | AniList paged search/Browse, genres/sorts/quarter-based Simulcasts; ani.pm episode/audio availability. Two disjoint 25-item pages checked live; native Browse/Action and Frieren search exercised. Offline fallback remains bounded top-100 without equivalent paging/filtering. Catalog membership does not imply playable video. |
| Home | Ten collections including current season/airing. Stable finite pagination prevents repeating the hero. Eight vertical swipes traversed all ten collections and further swipes stayed at the final rows without a repeated hero. Return/rotation acceptance remains outstanding. |
| Episode metadata | AniZip mapping validated against AniList ID adds real names, images, descriptions and dates while preserving provider availability. Native One Piece/Frieren player/next rows show real images/names. Missing fields retain fallbacks; one synthetic season per AniList entry remains. |
| Native player / skips | Original HLS player, background resolution and provider headers. One Piece/Frieren played on the own AVD. Original Skip Intro appeared using real times. Click/boundary checks and all devices/audio versions remain unverified. |
| Subtitles | Native locales including es-ES, es-419, pt-BR; known labels recover languages marked und. Lazy WebVTT-to-ASS conversion feeds original libass. English/both Spanish files fetched/converted live; multilingual menu and visible Spain Spanish dialogue verified for Frieren episode 1. Other-language rendering remains to test. Styles/positions are simplified; timing, Unicode and line breaks kept. |
| Hard subs | Explicit matching hardsub_locale sources prioritized; separate tracks allowed. No tested live source advertised a hard-sub locale. Do not claim burned-in video generation or hard subs for all episodes. |
| Avatars | Original picker with replacement catalog artwork. Native selection/save and cloud avatar snapshot ani-cover-21 verified. This is not the original service's licensed character-avatar catalog. |
| Cloud auth | Native password login, encrypted session restart/update persistence, guest/cloud separation and avatar backup checked with an own temporary fixture. Signup/recovery routes added but email flows unverified. Fresh-device restore and conflict-aware multi-device sync remain incomplete. |
| Comments | Guest reads; authenticated create/reply/like/unlike/own delete/spoiler; paging, RLS and rate limiting. Existing reads, replies, likes and owner reply deletion exercised through native screens; cross-owner denial checked in SQL. Popularity ranking and moderation/admin reporting remain incomplete. |

Current validation: **44 Python tests, 58 original-model contracts**, plus live catalog/HLS/English-and-Spanish conversion/invalid-auth/private-read checks. Runtime evidence covers one Android 37 isolated AVD, not every title/device. Historical 0.4.3 checks exercised pause/seek/fullscreen. The user's device-specific Oops failure remains unreproduced.

## Compatibility traps

- Home must honor start/n, keep one snapshot and end with an empty page; never repeat rows to simulate infinite scrolling.
- Audio versions and adapter availability dates must agree with the asset. Access dates are not broadcast dates. Episode audio labels now describe the chosen asset.
- Preserve Kotlin suspension and bounded workers; synchronous provider I/O caused NetworkOnMainThreadException.
- The original subtitle overlay is a bundled local WebView/WASM libass renderer, not a provider embed player. It requires ASS. NativeSubtitles converts selected WebVTT on the request worker. Original HTML/WASM/fonts are unchanged. Never execute remote embed/ad scripts.
- Talkbox has its own DateTypeAdapter: yyyy-MM-dd'T'HH:mm:ssZ with +0000 offsets. General GsonHolder differs. Raw PostgreSQL fractions and plain Z comment dates both crashed the client; tests use the actual Talkbox configuration.
- LocalCommentsAdapter.getItemId parses IDs as long. UUID comment IDs crashed RecyclerView. UUIDs remain internal; numeric identity strings are returned and mapped back for writes/RPC.
- AniList total/lastPage are unreliable. Paging follows hasNextPage and uses a growing lower bound until the last page.

## Source map and services

build_preserved_apk.py checks the original SHA, preserves resources/assets/libraries/screens, changes only manifest root versions, rebuilds classes2 and appends classes5. Eight hooks: s70/e, OkHttp factory, guest token storage, cr/l, cr/g, ll/a, do/b and gk/b. **4,665 original non-signature members** remain byte-identical, excluding manifest/classes2/removed stamp.

Adapter sources: native_adapter/src/dev/apkforge/bridge/.
BackendBridge routes native requests; NativeDiscovery/NativeMetadata implement AniList/AniZip; NativeHomeFeed/NativeCatalog map UI models; NativePlayback handles HLS/skips/tracks; NativeSubtitles registers local URLs/converts cues; NativeAssets maps artwork; CloudSession/LocalProfiles handle auth/state; NativeCommunity handles cloud comments; NativeLists/NativeRatings/NativeAccountState retain local features.

Providers:
- https://ani.pm/api/partner/v1: top/titles/series. Use existing advertised UA; default Python UA can return 403. Public embed docs do not establish native direct streams.
- https://graphql.anilist.co POST metadata; https://api.ani.zip/mappings?anilist_id=ID episode metadata. Neither establishes video availability.
- https://anivexaapi-aniko2.hf.space/api/watch/ID/sub-or-dub/EP: ssub/sdub. Resolver validates HLS and applies provider UA/Referer to media/subtitles. Do not introduce media DRM/license bypasses.
- Supabase **Spa-Ripper-AniPM**, project **yhccrdatocqqniblpshm**, https://yhccrdatocqqniblpshm.supabase.co, eu-west-3, approved 0/month. Public publishable key already in client; never embed service-role keys.

All six checked-in migrations are deployed: account snapshots, comments, rate limit, votes, native IDs and comment avatars. Snapshots are owner-private. Comments permit guest read/authenticated owner write/delete. Vote rows are private; fixed-search-path helper exposes aggregates/caller flags. Latest security advisor warning: leaked-password protection disabled; do not silently add paid features.

Remove temporary own test accounts/comments after checks. Never publish fixture credentials, tokens, signing keys, decoded original sources or signed media URLs. android-build is ignored. Untracked artifacts predates this continuation: preserve/inspect before publication.

## Remaining acceptance work

1. Reproduce exact failed version/title/audio/episode. Check all Home rows, scroll restoration, tabs, rotation, refresh, retry and provider outages.
2. Visibly verify Latin American Spanish/English/other languages, off, seek, next episode, restart and dub. Verify skip click reaches real end. Check hard preference only with a confirmed matching source. Never advertise absent languages.
3. Discovery beyond two pages, empty search, sort/filter changes, Simulcasts selection. Separate future/unmapped catalog metadata from playable titles. Details cache has no independent expiry; fallback paging is limited.
4. Fresh-install restore, refresh failure, two-user UI isolation, signup confirmation/recovery and safe multi-device reconciliation.
5. Comment popularity sorting, moderation/report workflow, guest-write UX, avatar refresh, pagination and parent deletion.
6. Lists/watchlist/progress across profiles/cloud restore. History API remains empty; only playheads persist. Downloads/music/store are unsupported. Test multiple Android versions/sizes and production signing/distribution.

## Build, tests and publication

Workspace: C:\Users\green\Documents\antigravity\mysterious-fermi\Spa-Ripper-Apk-Symbiot.
Original: C:\Users\green\Downloads\com.crunchyroll.crunchyroid_v3.61.0-770_Android-8.0.apk.
SHA-256: 9f44b888baf558269eb79a868854ee9f0004a11704df0eafa00d95453f0e87e1.
Uptodown is the wrong package. Decode: recovered/crunchyroll-code. Optional JADX: recovered/crunchyroll-java/sources (238 failures; smali is truth). Apktool 3.0.3: .tools/apktool.jar. SDK platform android-37.0/build-tools 36.0.0.

Commands, with JDK/ADB on PATH:

    python -m unittest discover -q
    python build_preserved_apk.py --reference-apk 'C:\Users\green\Downloads\com.crunchyroll.crunchyroid_v3.61.0-770_Android-8.0.apk' --decoded recovered/crunchyroll-code
    python verify_native_contracts.py --stream-live --catalog-live --subtitles-live --cloud-negative
    python native_ui_probe.py

JDK: C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin.
ADB: C:\Users\green\AppData\Local\Android\Sdk\platform-tools.

Only mutate **emulator-5560** after verifying exact AVD **APKForge_Original_UI**. Never operate user emulator-5554 or select an arbitrary fallback. UI probe masks saved/printed passwords and rejects stale/null dumps; retry fresh during transitions.

Development signer updates previews using the same local key, not the official APK. Never uninstall the user's official app. Keep the signing key private.

Bump version/code and packaging tests. Commit source/tests/migrations/docs on the existing branch and push; attach PR #1. Publish unique versioned APK/provenance/truthful validation as a prerelease using the full 40-character commit SHA target. Never overwrite old assets. .tools/verify_release.py has stale manifest/member assumptions; use current build checks. Compilation alone is not product verification.
