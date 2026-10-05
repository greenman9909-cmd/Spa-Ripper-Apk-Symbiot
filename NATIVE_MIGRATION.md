# Original-screen Android migration — experimental

This is an incomplete backend migration of the user-supplied Android 3.61.0 / 770 APK. The earlier reconstructed interface was rejected and is not included. The recipe keeps original manifest semantics except root version attributes, resource table, assets, native libraries and screen implementations, with six original backend/player integration classes hooked and a new adapter DEX appended. Read [AGENT_HANDOFF.md](AGENT_HANDOFF.md) for the current continuation plan.

The supplied APK SHA-256 is `9f44b888baf558269eb79a868854ee9f0004a11704df0eafa00d95453f0e87e1`. This recipe deliberately rejects other inputs. APK filenames are not package identity: `server.py --inspect INPUT.apk --expected-package com.crunchyroll.crunchyroid` checks the decoded manifest.

## Current behavior

- Optional local guest session, original profile selector and profile creation screens. Profiles and their preferences are stored on the device, separately from the original credential store.
- Public ani.pm catalog adapted to the original Home, Browse, search, series, season and episode models. Unfiltered Browse currently uses the provider's top-100 catalog, not its entire catalog. Missing episode names use their actual episode number.
- Home supplies eight provider collections: weekly, daily, monthly, all-time, movies, action, adventure and fantasy. Each uses the original carousel and its View All link keeps the provider filters. Independent collection requests run with a four-worker limit; an unavailable collection does not discard the other successful collections. A bounded two-minute response cache reduces repeat provider traffic. Summary refreshes retain already loaded episode lists.
- Local watchlist, ratings, playhead and private custom-list persistence per profile. Custom lists support creation, renaming, deletion, adding/removing series and manual position changes. Creation, empty detail, persistence and profile separation have been exercised in the original UI; other list operations still require UI verification.
- Legacy benefit labels describe replacement-backend capabilities to the retained screens. They are not official subscriptions, credentials, playback licenses or access to the original service's media.
- Original expired-client update event disabled for this replacement backend. Unknown routes fail explicitly and are logged without query strings, headers or request bodies.
- The adapter sits after the original request/response interceptors so their account-ID rewriting and response processing are retained. The original native play-service hostname is handled explicitly as well as the catalog hosts.
- Episode ratings use the original thumbs-up/down model instead of the series five-star model. Community reads return empty replacement guestbooks/comments; community writes fail explicitly. Episode skip metadata contains no invented intro/credits times.

**Native HLS now plays in the original Android player.** ani.pm remains the catalog provider; its public playback API exposes embeds. The alternate Anivexa watch endpoint returns MegaPlay embed links rather than direct streams. The adapter follows those links using their reference header, reads a numeric source ID and resolves the public sources response without executing player/ad scripts. The currently observed `enc` response uses the public player's AES-CBC URL-wrapper format (a UTF-8 key zero-padded to 32 bytes, 16-byte IV and PKCS padding). This is source-response decoding, not media DRM/license decryption. HLS is mapped into the retained HLS player models, with provider headers on the media data-source factory and bounded background workers preserving Kotlin suspension.

Missing native availability dates were also corrected: provider-available audio uses a fixed adapter access-date anchor, not an invented broadcast date. Missing dates made the original availability monitor reload metadata and prevented stream requests. One Piece episode 1 sub now plays; pause, ten-second seek and landscape fullscreen were exercised on the isolated emulator. This is not verification of every title, audio version or subtitle selection. Provider availability, quality and uptime remain external dependencies. Complete genre/simulcast browsing, avatars, music, store, downloads and other original service functions are unfinished. Language synchronization currently falls back to bundled resources. A signed APK is not evidence of a complete or stable product.

On 2026-10-05, the user selected pausing WebLoom to preserve its data and free the project slot. WebLoom is now INACTIVE. Spa-Ripper-AniPM (`yhccrdatocqqniblpshm`) was created in the agreed organization, eu-west-3, at the confirmed cost of 0/month. No project was deleted or upgraded.

The replacement password grant now targets Supabase Auth; passwords are neither stored nor logged. Cloud session tokens are encrypted using Android Keystore AES-GCM and used only for the Supabase host, while the retained app receives a local adapter token. Guest storage and each cloud user's device storage are separate. The deployed `account_state` table permits authenticated owners only, with RLS enabled and four ownership policies; public-key reads are denied. Authenticated profiles/list state have a best-effort snapshot backup and initial restore. A failed initial restore disables uploads to avoid overwriting an existing cloud copy. This is not conflict-aware multi-device synchronization. Signup, recovery, successful login through the original screen, and authenticated backup/restore still require end-to-end verification. Only invalid-login/private-read rejection has been exercised against the live project. Guest access remains available without cloud login.

## Build

Requires Java 21, Android SDK platform `android-37.0` and build tools `36.0.0`, and Apktool 3.0.3 at `.tools/apktool.jar`. The independently downloaded official Apktool JAR used locally has SHA-256 `dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423`.

Decode the supplied APK with Apktool's full smali decode, then run:

```powershell
python build_preserved_apk.py --reference-apk "PATH_TO_ORIGINAL.apk" --decoded "PATH_TO_FULL_DECODE"
```

The output is `android-build/Original-UI-AniPM.apk` and an adjacent provenance JSON listing original members and hashes. The build uses a local development signing key. It cannot update an officially signed installation; test on a separate emulator instead of uninstalling a user's app or deleting their data.

The repository contains the adapter and build recipe. Original decoded sources, signing keys, credentials, SDK/tool downloads and build files are ignored by Git. APK binaries are distributed separately as explicitly experimental release assets.

## Verification on 2026-10-04

- 43 Python tests pass.
- APK assembles, installs and passes Android v2/v3 signature verification.
- Separate Android API 37 emulator: startup reaches the original profile selector without the expired-version gate; original Home and Browse load provider titles; original Add Profile form saves a second local profile and returns to the original selector.
- Native catalog panel type was corrected after an opening-title crash. Native service routes for ratings, featured music and related titles were subsequently added; complete series navigation still needs final verification.

## Additional verification on 2026-10-05

- 43 Python tests pass. The signed APK retains 4,666 original non-signature archive members byte for byte; only the explicitly patched DEX and original signing stamp are excluded from that count.
- `python verify_native_contracts.py --cloud-negative` passes 29 cases against the actual retained Android/Gson model classes in the isolated `APKForge_Original_UI` emulator, plus two live Supabase rejection checks. This includes the episode-rating crash regression, series ratings, catalog navigation, guestbooks, custom-list creation/rename/delete/validation, audio versions, next/end-of-series progression, watchlist stream/parent metadata, Home collection filtering and preserving episode details during summary refreshes.
- Original My Lists opens without the former unmapped custom-lists/history crash. The original custom-list form creates `Native Test`, displays its empty detail and returns it in the collection. The list persists across APK updates; switching to another original profile shows separate empty lists/watchlist.
- Original Account displays the original profile header, Switch Profile and viewing-preference controls. Original watchlist displays Bleach and its S1 E1 entry after correcting its episode metadata.
- Native Home loads additional provider carousels. The replacement account initializes the original pending-state observer: the blank email banner disappears for Guest without changing its layout or presenter.
- The earlier playback failure described in release 0.4.2 is superseded by the native HLS checks below. Model-contract tests alone do not establish working playback, complete navigation or visual fidelity for every screen.

## Native HLS verification on 2026-10-05 (0.4.3)

- 43 Python tests and 34 original Android model contracts pass. Additional contracts cover the original availability gate, HLS URL/protocol/subtitle mapping, absence of synthetic DRM/session tokens, non-HLS mapper fallback, a fixed provider-decryption compatibility vector and invalid source URL rejection.
- `python verify_native_contracts.py --stream-live` validated a live One Piece episode 1 HLS manifest and original player model on Android. Actual UI testing subsequently showed decoded video frames, progress from 0:00 to 1:11, pause, seeking to 1:21 and fullscreen with the original controls and preserved aspect ratio. No browser was launched in the tested playback flow.
- The signed output preserves 4,666 original non-signature archive members; original primary/framework DEX files and all resources remain byte-identical. Player integration hooks are listed individually in the provenance report. Temporary diagnostic hooks were removed before release.
- Supabase configuration/RLS is retained from 0.4.2; successful native login and cloud restore remain unverified. No credentials or backend from the reference Yoru app were imported.

Observed provider protocol references: [Anivexa watch response](https://anivexaapi-aniko2.hf.space/api/watch/21/sub/1), [MegaPlay public player client](https://megaplay.buzz/lib/app.main.js), and [ani.pm developers](https://ani.pm/developers). These third-party endpoints can change; no signed media URLs are committed.

## Identifiable installation packaging (0.4.4)

Earlier previews retained the source versionName `3.61.0` and versionCode `770`, and shared the same APK filename. This made older downloads and installations difficult to distinguish. The 0.4.4 preview now has versionName `0.4.4`, code `1000044`, and a versioned download filename. Only the two root manifest version attributes are rewritten; the build compares the entire decoded manifest after normalizing those attributes to guarantee components, permissions and configuration are unchanged.

44 Python tests pass, including UTF-8/UTF-16 manifest version fixtures; 34 Android model contracts pass. The signed update installs over the previous development-signed preview with its data intact and Android reports the new version. 4,665 original non-signature members remain byte-identical; manifest version attributes, the backend DEX and signing stamp are explicit exceptions. Native HLS code is unchanged. Browse, opening Bleach, and starting Bleach episode 1 sub were rechecked on 0.4.3 before this metadata-only rebuild. The user's report of failing title/video pages has not yet been reproduced or attributed to a specific installed build/device.

The native migration is not considered complete. Release notes report the validated flows and remaining limitations rather than a fidelity or accuracy percentage.

The Android contract check compiles only `native_adapter/tests`, loads the current signed APK through Android `app_process`, and uses the original model classes/Gson configuration. Test code is excluded from the appended APK payload. `native_ui_probe.py` requires the exact isolated AVD name before reading/clicking, rejects stale UI dumps, and selects current original-app controls by text or description. Its optional ASCII fixture input rejects password fields.
