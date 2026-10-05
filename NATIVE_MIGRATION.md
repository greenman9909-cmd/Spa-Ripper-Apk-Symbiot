# Original-screen Android migration — experimental

This is an incomplete backend migration of the user-supplied Android 3.61.0 / 770 APK. The earlier reconstructed interface was rejected and is not included. The recipe keeps the original manifest, resource table, assets, native libraries and screen implementations, with three original backend classes patched and a new adapter DEX appended.

The supplied APK SHA-256 is `9f44b888baf558269eb79a868854ee9f0004a11704df0eafa00d95453f0e87e1`. This recipe deliberately rejects other inputs. APK filenames are not package identity: `server.py --inspect INPUT.apk --expected-package com.crunchyroll.crunchyroid` checks the decoded manifest.

## Current behavior

- Optional local guest session, original profile selector and profile creation screens. Profiles and their preferences are stored on the device, separately from the original credential store.
- Public ani.pm catalog adapted to the original Home, Browse, search, series, season and episode models. Unfiltered Browse currently uses the provider's top-100 catalog, not its entire catalog. Missing episode names use their actual episode number.
- Local watchlist, ratings, playhead and private custom-list persistence per profile. Custom lists support creation, renaming, deletion, adding/removing series and manual position changes. Creation, empty detail, persistence and profile separation have been exercised in the original UI; other list operations still require UI verification.
- Legacy benefit labels describe replacement-backend capabilities to the retained screens. They are not official subscriptions, credentials, playback licenses or access to the original service's media.
- Original expired-client update event disabled for this replacement backend. Unknown routes fail explicitly and are logged without query strings, headers or request bodies.
- The adapter sits after the original request/response interceptors so their account-ID rewriting and response processing are retained. The original native play-service hostname is handled explicitly as well as the catalog hosts.
- Episode ratings use the original thumbs-up/down model instead of the series five-star model. Community reads return empty replacement guestbooks/comments; community writes fail explicitly. Episode skip metadata contains no invented intro/credits times.

**Playback is unfinished.** ani.pm's public API provides embed playback, not a documented HLS/DASH URL for the retained native player. Attempts to start playback currently reach the original connection-error screen and can repeatedly reload metadata. The corrected episode progression/audio-version contracts have not resolved this runtime failure. This APK does not claim ad-free native playback. Supabase login, cloud profile sync, complete genre/simulcast browsing, avatars, music, store, downloads and other original service functions are also unfinished. Language synchronization currently falls back to bundled resources, and the guest email-verification banner still needs handling. A signed APK is not evidence of a complete or stable product.

On 2026-10-05, creation of the user-authorized new free Supabase project was rejected by Supabase because the account already has two active free projects. No new project was created and no existing project was paused, deleted or upgraded.

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
- `python verify_native_contracts.py` passes 23 cases against the actual retained Android/Gson model classes in the isolated `APKForge_Original_UI` emulator. This includes the episode-rating crash regression, series ratings, catalog navigation, guestbooks, custom-list creation/rename/delete/validation, audio versions, next/end-of-series progression and watchlist parent metadata.
- Original My Lists opens without the former unmapped custom-lists/history crash. The original custom-list form creates `Native Test`, displays its empty detail and returns it in the collection. The list persists across APK updates; switching to another original profile shows separate empty lists/watchlist.
- Original Account displays the original profile header, Switch Profile and viewing-preference controls. Original watchlist displays Bleach and its S1 E1 entry after correcting its episode metadata.
- Native playback remains unverified and fails as described above. Model-contract tests do not establish working playback, complete navigation or visual fidelity for every screen.

The native migration is not considered complete. Release notes report the validated flows and remaining limitations rather than a fidelity or accuracy percentage.

The Android contract check compiles only `native_adapter/tests`, loads the current signed APK through Android `app_process`, and uses the original model classes/Gson configuration. Test code is excluded from the appended APK payload. `native_ui_probe.py` requires the exact isolated AVD name before reading/clicking, rejects stale UI dumps, and selects current original-app controls by text or description. Its optional ASCII fixture input rejects password fields.
