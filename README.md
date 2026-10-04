# APKForge — App reconstruction studio

A working local prototype inspired by the idea of a website ripper, adapted to Android packages. Drop in an APK, inspect what is actually inside it, preview bundled HTML, and export a localhost web bundle.

## Run

Requires Python 3.10 or newer. No pip packages, Android SDK, or internet connection required for this prototype.

```sh
python server.py
```

On Windows, use `py server.py` if `python` is unavailable. Open **http://127.0.0.1:8787**. Keep the terminal open. Stop with Ctrl+C.

1. Choose an APK.
2. Review its framework signals, assets, and recovery limits.
3. If HTML exists in `assets/`, select an entry point for the phone preview.
4. Export the web bundle. Unzip it, enter its `web` directory, and run:

```sh
python -m http.server 8000 --bind 127.0.0.1
```

Open the HTML entry shown in `RUN.txt`. The export includes the selected HTML's entire containing folder, not files elsewhere in the APK. Unlike the studio's isolated preview, the exported app runs with normal browser behavior; its original network calls can occur.

## Implemented

- APK ZIP validation, path checks, upload and expansion limits.
- SHA-256 fingerprint and complete file inventory.
- Image, font, web, DEX, and native library categorization.
- Filename-based Flutter, React Native, Cordova, Capacitor, and HTML signals.
- HTML entry discovery and sandboxed phone preview.
- Searchable file explorer, raster image viewer, and bounded text inspection.
- ZIP export of a chosen web folder and JSON analysis export.
- Loopback-only server, host validation, upload token, isolated preview, blocked preview network access.
- In-memory storage for the most recent three packages; nothing installed on Android.

Limits: 256 MB upload, 512 MB declared expanded size, 32 MB per entry, 20,000 entries. Split APKs, XAPK/APKS containers, resource table decoding, native execution, decompilation, API replay, and generated web screens are not implemented. The manifest is retained raw, not decoded. Framework detections are clues, not guaranteed identifications. APKs without bundled HTML get inventory only. Absolute web paths, fetch(), persistent storage, plugins, and backend-dependent flows can fail in the isolated preview. The preview sandbox reduces exposure; this prototype is not a malware detonation environment.

## The distinctive product direction

The ambitious product is a **reconstruction studio with evidence attached to every output**. The browser hosts both recovered web content and, eventually, a streamed Android runtime for native content. A generated web version is a separate output, measured against observed app behavior.

| Engine | Input | Output | Current state |
| --- | --- | --- | --- |
| Recover | APK ZIP | Assets, candidate HTML, inventory | Implemented |
| Observe | APK in Android Emulator + authorized interactions | Screenshots, UI trees, navigation graph | Planned |
| Reconstruct | Observed screens + extracted assets | Editable web components with evidence links | Planned |
| Replay | Recorded authorized requests and responses | Local API fixtures + native bridge adapters | Planned |
| Verify | Original captures + reconstructed flows | Pixel differences, interaction coverage, missing behavior | Planned |

### What could make it compelling

**Evidence lens:** click any reconstructed button, image, or text to see whether it came from an asset, an observed UI node, or an inference. Never label an inferred screen as an exact recovery.

**Gap map:** show missing backend responses, login-dependent content, native bridge calls, unvisited screens, and unavailable split-package resources. Coverage is based on observed flows, not a made-up accuracy percentage.

**Two runtime paths:** preserve bundled web apps directly; run native apps in an Android runtime. Reconstruct native screens into web code only when requested. An APK is not a browser-executable source project.

**Replay timeline:** capture an authorized flow once and turn each step into an editable local scenario with explicit fixture inputs. APK files do not contain a remote service's server code or data.

**Repair workbench:** show a broken call, its triggering screen, and the proposed adapter together. Require review of generated adapters before accepting behavior changes.

### Suggested full architecture

- Python service for ingest, artifact indexing, and job management.
- Android SDK `apkanalyzer` for manifest, DEX, and compiled resource inspection.
- Android Emulator workers isolated per project, with streamed display and controlled input.
- UI tree and screenshot capture; accessibility-driven exploration with a bounded state graph.
- Asset matching plus optional local vision/code models for editable web reconstruction.
- Explicit replay fixtures for user-authorized traffic, with private data redacted before export.
- A test harness that replays observed interactions and measures visual differences.

A later Windows desktop edition would require benchmarking emulator memory and optional local model requirements. This prototype is dependency-free.

## Reality check

This is a prototype, not a universal APK cloner. Compiled Kotlin/Java, native libraries, Flutter rendering, Android services, authentication, and remote APIs need different strategies. Android's APK Analyzer already covers archive/manifest/DEX/resource inspection. Novelty would come from combining reconstruction, replay, evidence, and verification into one workflow; no claim is made that nobody has built similar concepts.

Official references:
- https://developer.android.com/tools/apkanalyzer
- https://developer.android.com/studio/run/emulator

## Validation

```sh
python -m unittest -v test_engine.py
```

Tests exercise a synthetic APK-shaped fixture, export fidelity, traversal rejection, invalid ZIP rejection, missing manifests, limits, native-only detection, and missing entry rejection. A supplied Nuvio 0.5.6-beta ARM64 APK was inventoried successfully after correcting an overly strict filename check. It contained no HTML, so real bundled-web app compatibility remains unverified.
