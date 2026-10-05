# Native preview 0.4.7 validation

Checks conducted 2026-10-05; report updated 2026-10-06. Package com.crunchyroll.crunchyroid, **0.4.7 / 1000047**. Prerelease; production acceptance incomplete.

## Integrity

- Original-UI-AniPM-v0.4.7.apk: **50,878,187 bytes**.
- SHA256: **6b39f0bbf8bcef86045c4feefa9e2242760269e77d247ff7337318ea8ea2d92b**.
- APK v2/v3 signatures verified with private development signer.
- Nine backend/session/artwork hooks; 4,665 original non-signature members unchanged. Original layouts/resources/fonts/native player retained.
- Provenance runtimeVerified/migrationComplete remain false pending wider acceptance.

## Automated/live checks

- **44 Python tests passed**.
- **69 original-model contracts passed**: artwork builder, null-safe extraLarge, Black Clover seasons/absolute audio IDs/next episode, new languages, summary refresh, metadata budget and detail eviction retaining seasons.
- Live disjoint 25-item catalog pages, episode artwork and skip metadata checked.
- Live HLS manifest and English/both Spanish VTT-to-ASS conversion passed.
- Invalid login and unauthenticated private-state reads denied.
- git diff --check passed.

## Native device checks

Only own Android 37 AVD APKForge_Original_UI (emulator-5560) modified.

- Final installed version confirmed 0.4.7/1000047.
- No encrypted session: original Log In/Create Account landing. Confirmed own fixture login reached profile picker/Home. Restart/update retained session. Logout returned to landing.
- Naruto search visibly showed Naruto, Shippuden and Boruto artwork in original search rows.
- Black Clover original picker displayed four groups (51/51/52/16). S2 displayed episodes 52 onward with real names/photos. E52 opened/played original native player; E53 shown next; no Home fallback in this check.
- Thirty fast Home swipes (15 down/15 up), PID **2655** before and after. Bounded evidence, not proof of crash freedom.
- Browse, Simulcasts, My Lists and Account loaded. Primary cloud profile now Profile 1.
- Own confirmed fixture logged out/deleted; query confirmed zero remaining fixture accounts.

Earlier 0.4.6 reproduction played Naruto/Shippuden episode 1 and showed Spain Spanish dialogue for Frieren. Avatar/cloud-comments tests remain historical evidence, not a full rerun of every feature here.

## Limitations

Public Auth settings: signup/email enabled, confirmation required. Real signup delivery/confirmation/recovery unverified. Automatic approval review rejected the signup shell test with “blocked by policy”; separate confirmed fixture did not test signup/send email. Owner-dashboard access requested to inspect configuration.

Reported user-device crash/Oops/Home fallback and blank Not now popup unreproduced. Multi-version/device/rotation/network retry, fresh profile restore/concurrent sync, real history, universal sequel merging, moderation/account settings and all subtitle/skip interactions still need acceptance. Recognizing 35 language bases does not mean 35 tracks per episode; translation accuracy not audited.

[AGENT_HANDOFF.md](AGENT_HANDOFF.md) lists prioritized blockers and continuation instructions.
