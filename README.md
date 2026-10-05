# APKForge — Evidence and recovery studio

A local, dependency-free tool for inspecting Android APKs and recovering packaged web apps. Upload an APK, decode its manifest, inspect assets and framework evidence, trace web dependencies, preview bundled HTML, and export the recovered files with SHA-256 provenance.

## Experimental original-screen Android APK

The native migration retains the supplied APK's original screens/resources and player. It combines AniList discovery, ani.pm availability, mapped episode images, native HLS and Supabase profiles/comments. Spanish dialogue has been verified in the original player. See [AGENT_HANDOFF.md](AGENT_HANDOFF.md) for current evidence and limits; full production migration remains unfinished.

Current preview: [Original-UI-AniPM-v0.4.6.apk](https://github.com/greenman9909-cmd/Spa-Ripper-Apk-Symbiot/releases/download/v0.4.6-native-preview/Original-UI-AniPM-v0.4.6.apk). App Info must show **0.4.6**, code **1000046**. Adds paged discovery, episode artwork, provider skip times, native avatar selection, cloud comments and multilingual subtitle conversion for the original renderer. Hard-sub sources are preferred only when explicitly available in the selected language; separate subtitles remain supported. Language coverage varies by episode.

**Continue here:** [AGENT_HANDOFF.md](AGENT_HANDOFF.md) documents remaining work, database/build details and safe device checks. Current validation: 44 Python tests, 58 original Android model contracts and live catalog/HLS/English-and-Spanish-subtitle checks. Native login/session persistence/avatar backup and comment reply/like/delete were exercised; fresh-device restore and conflict-aware sync remain unverified.

## Run the studio

Python 3.10 or newer; no pip packages or Android SDK required.

```bash
python server.py
```

On Windows, use `py server.py` if needed. Open [http://127.0.0.1:8787](http://127.0.0.1:8787). Change the port with `--port 8788`. Stop with Ctrl+C.

1. Choose or drop an APK.
2. Review the recovery map for each HTML entry, including missing files, external dependencies, native bridges, and analysis gaps.
3. Open **Android metadata** for package/version/SDK details, permissions, declared activities, aliases, services, providers, receivers, and launcher intent filters.
4. Open **Evidence & gaps** for framework markers with source paths, DEX analysis coverage, endpoint hints, and configuration findings.
5. Preview a bundled HTML entry, search/filter/page through all assets, and download the analysis, decoded manifest, or recovered web bundle.

The preview is sandboxed and blocks network calls. It does not install or execute Android code. The server binds only to loopback, validates the Host header and upload token, rejects cross-origin uploads, limits concurrent uploads, and keeps the last three sessions (at most 512 MB of original APK data) in memory. Session data expires on eviction or server shutdown.

## Inspect without a browser

```bash
python server.py --inspect app.apk --report analysis.json
python server.py --inspect app.apk --report analysis.json --export recovered.zip --entry assets/www/index.html
```

Omit `--report` to print JSON. Omit `--entry` when exactly one candidate exists. The CLI never overwrites an existing output or the input APK; use new output names. Invalid input exits with code 2 and a concise error. APK analysis also remains available to Python callers through `inspect_apk(data, name)`.

## What the engine checks

- **Archive integrity:** validates paths, duplicate entries, file/directory conflicts, symbolic links, encryption flags, declared sizes, and actual bytes read. Accepts stored/DEFLATE APK entries; other ZIP compression methods are rejected before decompression. Reads every member to EOF, checks ZIP CRCs, and hashes every file and the APK with SHA-256. This is integrity checking, **not APK signature verification**.
- **Manifest:** decodes text XML and Android binary XML with UTF-8/UTF-16 string pools, namespaces, string/reference/integer/boolean attributes, and strict bounds. Resource references such as `@0x7f11004c` stay unresolved.
- **DEX:** reads standard 035–040 string tables and Android modified UTF-8, including embedded NULs and surrogate pairs. Truncated structures, invalid offsets, unsupported DEX formats, and analysis limits become visible warnings; the remaining inventory is retained.
- **Framework evidence:** combines Flutter, React Native, Cordova, and Capacitor archive markers with manifest component names and DEX class references. Each marker includes its path and evidence type. “Corroborated” means more than one type of static evidence, not a confidence percentage or proof of the app's rendering engine.
- **Web dependencies:** walks literal HTML references, inline scripts/styles, CSS URLs/imports, and JS import/require/fetch references, including cycles, query strings, escaped paths, sibling assets, and HTML base URLs. Reports present, missing, external, unresolved-module, runtime-dependent, outside-assets, and invalid references.
- **Recovery gaps:** records static native bridge call patterns, inferred root-relative URL mappings, skipped text, and truncated dependency traversal. File presence never becomes a claim of runtime accuracy.
- **Endpoint hints:** collects HTTP(S) strings from analyzed DEX/web assets, links them to source files, and omits URL credentials, queries, and fragments. Strings may be unused, documentation links, or incomplete APIs; no request is sent to these URLs.
- **Configuration facts:** shows explicitly enabled debugging/cleartext traffic, split-package declarations, and explicitly exported components with declared permission guards. These are facts about declarations, not a vulnerability verdict; unspecified defaults are not guessed.
- **Inventory:** categorizes images, web assets, fonts, media, DEX, native libraries, and Android resources; lists native ABIs; supports bounded raster/text previews and paginated file search.

## Export fidelity

Exports retain the selected entry's containing tree. When detected dependencies reference sibling folders, the engine widens the export root to preserve those files and their original relative paths. It copies the entire selected tree, not just the static dependency graph, and does not rewrite app code.

The ZIP contains:

| File | Purpose |
| --- | --- |
| `web/` | Original packaged asset bytes under the inferred export root |
| `analysis.json` | Full schema-versioned inspection report |
| `recovery.json` | Selected candidate, original APK hash, source/output mapping, sizes, and file hashes |
| `RUN.txt` | Exact entry URL and localhost serving instructions |

ZIP output is deterministic for the same session and entry, with fixed timestamps and stable file ordering. Unsafe Windows filenames, reserved device names, and case-colliding files are rejected on export rather than silently renamed or overwritten. Nonportable metadata elsewhere in the APK can still be inventoried.

Unzip the bundle, enter `web`, and run:

```bash
python -m http.server 8000 --bind 127.0.0.1
```

Open the URL listed in `RUN.txt`. The entry can be nested after the root widens. Unlike the isolated studio preview, the export has normal browser behavior and **can contact its original remote services**. Root-relative paths, plugin calls, backend responses, and persistent storage may need adaptation.

## Bounded analysis and honest coverage

| Limit | Value |
| --- | --- |
| Upload | 256 MB |
| Expanded archive | 512 MB |
| Single archive entry | 32 MB |
| Archive entries | 20,000 |
| Decoded manifest | 4 MB / 50,000 nodes / binary depth 256 |
| String pool or DEX string count | 200,000 |
| DEX decoded string bytes | 16 MB per DEX |
| DEX analysis input | 64 MB total |
| Text analysis | 1 MB per file / 16 MB total |
| Analyzed HTML candidates | 200 |
| References | 10,000 per candidate / 20,000 total |
| Dependency file visits | 5,000 total |
| Framework evidence / distinct endpoint hints | 500 each |
| Concurrent uploads | 2 |

Analysis limits and unsupported content are reported as warnings or candidate gaps. Inventory and JSON exports retain all validated file metadata. `coverage` reports counts of what was actually analyzed and `runtimeVerified: false`; there is no invented accuracy score. These budgets bound individual stages, not total process memory or execution time.

Dependency checks use HTML parsing and conservative literal patterns, not a full JavaScript/CSS execution engine. Comments, import maps, computed imports, generated URLs, routing, compressed bundles, and native bridges may require review. The inferred web root may differ from the original WebView configuration; root-relative mappings are explicitly marked as assumptions. HTML base URLs are analyzed but blocked in preview by its policy.

Unsupported capabilities: XAPK/APKS containers and multi-APK assembly, resource-table decoding, APK signature verification, DEX instruction decompilation, native execution, remote API replay, emulator observation, and generated web screens. A split APK can be inventoried but may need its companions. This tool is not a malware detonation environment or a universal APK-to-website converter.

## Validation

```bash
python -m unittest discover -v
python -m compileall -q server.py analysis.py android_formats.py
```

The 43-test suite covers binary XML string pools and typed values, launcher aliases, malformed/truncated structures, DEX modified UTF-8, seeded random parser input, evidence corroboration, endpoint hint redaction, graph cycles, sibling exports, deterministic ZIPs and per-file hash fidelity, skipped analysis, archive corruption/traversal/symlinks/collisions/unsupported compression, CLI errors, HTTP isolation, export preflight, concurrency limits, and session eviction.

Locally validated on Windows with Python 3.11 and 3.14. CI runs the full suite on Windows and Ubuntu with Python 3.10, 3.12, and 3.14.

Manual checks on 2026-10-04:

- Browser: synthetic binary-manifest APK upload, decoded Android metadata, framework evidence, bundled HTML phone preview, asset filtering, analysis and bundle downloads, and no console errors.
- Real APK: official [Fossify Calculator 1.4.0](https://github.com/FossifyOrg/Calculator/releases/tag/1.4.0), `calculator-10-foss-release.apk`: 1,584 files validated/hashed; package `org.fossify.math`, version 1.4.0 / code 10, minimum SDK 26 / target SDK 36, and one DEX decoded without analysis warnings. It contains no bundled HTML; native runtime behavior was not tested. The APK is not redistributed in this repository.

## Next accuracy milestone

Static recovery works for packaged files. Measuring visual and behavioral fidelity requires observing the original app in an isolated Android runtime, capturing screenshots/UI trees and navigation flows, and comparing the recovered or reconstructed output against those observations. API fixtures would need explicit inputs and source evidence. These engines remain future work.

Official format/tool references:

- [Android binary resource structures](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/androidfw/include/androidfw/ResourceTypes.h)
- [DEX file format](https://source.android.com/docs/core/runtime/dex-format)
- [Android manifest activity declarations](https://developer.android.com/guide/topics/manifest/activity-element)
- [APK Analyzer](https://developer.android.com/tools/apkanalyzer)
- [Android Emulator](https://developer.android.com/studio/run/emulator)
