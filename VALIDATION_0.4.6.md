# Native preview 0.4.6 validation

VersionName 0.4.6; versionCode 1000046. APK SHA-256:

d5ba1ea609bac3056c251c72ab350e34d432034298f53392001c27741f5c8fe5

APK size: 50,869,995 bytes. Android v2/v3 signatures verify. Strict reference input hash, original manifest semantics except root version attributes, and 4,665 original non-META-INF members verified by the build. Eight original integration classes hooked; original resources, screen implementations, libraries, subtitle HTML/WASM/fonts retained. Adapter classes are appended as classes5.dex.

## Automated evidence

- 44 Python tests pass.
- 58 contracts pass against the retained original Android/Gson/player classes. Added discovery/category/filter/paging, episode enrichment, real skip range, artwork, language normalization, WebVTT-to-ASS conversion and Talkbox-specific date/numeric-ID regression contracts.
- Live Android checks: disjoint 25-item catalog pages; actual episode image; real intro range; One Piece episode 1 sub HLS manifest/model; Frieren episode 1 English/Spanish/Latin American Spanish subtitle fetch/conversion; invalid cloud login and denied anonymous private snapshot access.
- Supabase migrations for snapshots/comments/rate-limit/votes/native IDs/avatars deployed. Cross-owner delete denial and anonymous write/private-read denial checked in transactional SQL. Owner reply deletion was also confirmed in the database after native UI deletion.

## Native UI evidence

Only emulator-5560, exact AVD APKForge_Original_UI, Android API 37, was operated. The user's emulator was not modified.

- Upgrade install works and retains preview state/session.
- Original Home traversed all ten collections in eight swipes; further swipes stayed at the final rows without repeating the hero.
- Native Browse/Action and search for Frieren populated original layouts. Opening Frieren and One Piece started the original native player, with actual episode title/description/image and next-episode artwork.
- Original player settings showed Japanese audio and selectable English, German, Latin American Spanish, Spain Spanish, French, Italian, Brazilian Portuguese, Russian, Arabic and None for Frieren episode 1. Spain Spanish was selected, visibly rendered over changing decoded video frames and persisted in account preferences.
- The original Skip Intro control appeared with provider metadata. Its click/end-boundary behavior still needs a targeted check.
- Native Supabase sign-in with a temporary own fixture, session persistence across restart/update, guest/cloud state separation, original artwork picker/select/save and owner snapshot upload were exercised.
- Existing comments loaded; native reply creation, like and owner reply deletion worked. Date and UUID stable-ID crashes found during testing were fixed and covered by contracts.

## Limits

This is a tested preview, not production readiness. Subtitle languages vary by episode. No tested source advertised hard-sub metadata; matching explicit variants are preferred, but the app does not burn subtitles into video. WebVTT conversion simplifies provider styling/positioning.

Fresh-device cloud restore, conflict-aware multi-device synchronization, signup confirmation/recovery, all language/audio/device combinations, Home return/rotation, deeper discovery/filter paging and the user's generic Oops failure remain unverified. Comment popularity ranking/moderation, history, downloads, music and store remain incomplete. An earlier Supabase advisor warned about disabled leaked-password protection; the final check returned no lints. Auth security options still need dedicated production validation.

See AGENT_HANDOFF.md for the continuation plan and safe build/device/publication procedure. Temporary own test accounts and their cloud data are removed after validation; credentials are not included in source or release assets.
