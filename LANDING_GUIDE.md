# Yoru landing

The public site is served from `docs/` on branch `codex/evidence-driven-apk-analysis`:
https://greenman9909-cmd.github.io/Spa-Ripper-Apk-Symbiot/

The repository-root `index.html` remains the local APK inspection studio.

## Files and behavior

- `docs/index.html`: Spanish landing, APK download, catalog controls, detail dialog, app concept illustration, FAQs and mobile navigation.
- `docs/landing.css`: dark/orange responsive design, reduced-motion support and keyboard focus states.
- `docs/landing.js`: AniList refresh on opening, genre/search queries, deduplicated 24-item pages (240 displayed-record limit), local web favorites (100-title limit), safe text rendering and source-image validation. No video playback or web Supabase login is claimed. Web favorites are explicitly separate from app/cloud lists.
- `docs/catalog.json`: real AniList snapshot collected on 2026-10-06 plus the Frieren spotlight. Display immediately, then refresh once. Failed refresh retains the selection with an explicit message; filters/search expose retry. HTTP 429 applies a one-minute pause, with no automatic request loop.
- `docs/return.js`: executes before the landing. Strips confirmation credentials from the URL, displays the existing return/error/recovery messages, and isolates the callback from catalog/image/storage requests. Ordinary section anchors remain usable. CSP/referrer policy restrict requests and credential-bearing referrers.

Download points to the verified versioned 0.4.10 release (50,915,051 bytes). Update version, size, direct download and release-note links together for another APK. The landing labels it a preview and does not advertise official Mega Fan entitlement, universally available videos or accepted offline downloads. The app illustration is labeled conceptual.

## Checks completed

- JavaScript syntax and six existing auth-return cases, plus anchor preservation, callback visibility and no landing network/storage activity during an auth callback.
- Browser: fresh 24-title catalog; second page produced 48 distinct IDs; Naruto search returned 24 results with no observed broken poster; Naruto details opened; save/list persisted after reload and the test favorite was removed.
- Desktop and narrow/mobile layouts checked, with no horizontal document overflow in inspected viewports. Mobile bottom navigation appeared. Synthetic expired confirmation displayed the existing error instructions with the URL cleared.
- APK link checked against the real GitHub asset; no application changes or Supabase settings changed for this landing.

Run `node test_auth_return.cjs` and `node --check docs/landing.js`. Local preview: `python -m http.server 8137 --bind 127.0.0.1 --directory docs`.

Do not move the root auth callback without updating the actual Supabase redirect configuration with authorization. Never put tokens, passwords, service-role keys or user screenshots into the site. Metadata/cover art availability and video availability are distinct.
