# ARIES AI — AGENT NOTES

Android 11+ AI UI-automation app: Kotlin + Compose, one application module (`:app`) plus `:core:*` and `:feature:*` libraries. The repo also contains an Astro/VitePress site and a Cloudflare telemetry worker.

## SOURCE OF TRUTH
- Trust `app/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts`, and `.github/workflows/ci.yml` over README/docs prose.
- Toolchain: JDK 17, Gradle 9.4.1, AGP 9.2.1, Kotlin 2.4.10, KSP 2.3.12, compileSdk 37, targetSdk 36, minSdk 30.
- README and several docs are stale: they claim compileSdk 36 / Kotlin 2.2.21, and docs still describe Git submodules, CMake, and `app/src/main/cpp`. None of those exist now.

## LAYOUT
| Path | What lives there |
|---|---|
| `app/` | The only `com.android.application` module (`com.ai.phoneagent`). Key entrypoints/classes: `AriesAgentApp` (starts Koin), `MainActivity` (launcher), `PhoneAgentAccessibilityService`, `VirtualDisplayController`, `UiAutomationAgent`. |
| `app/.../core/` | App-side agent loop, action parser/executor, tools, screenshot cache, security guards. Not the root `core/` modules. |
| `app/.../vdiso/` | Shizuku virtual display: `ShizukuVirtualDisplayEngine`, `VdGlFrameDispatcher`, `ImeFocusDeadlockController`. |
| `app/.../data/`, `di/`, `net/`, `ui/`, `speech/` | Room/DataStore, Koin modules, API/SSE, Compose screens, sherpa-onnx. |
| `core/common`, `core/designsystem`, `core/prompt`, `core/shizuku` | JSON/version utils; M3 theme + `m3t_*` tokens; prompt repository; Shizuku bridge. |
| `feature/settings`, `feature/updates` | Settings UI; GitHub release/update checks. |
| `Aries-site/` | Astro site + VitePress docs; the only documentation tree. |
| `telemetry-worker/` | Cloudflare Worker + D1; independent npm project. |

## COMMANDS
From the repo root (Windows PowerShell):
```powershell
.\gradlew.bat assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat testDebugUnitTest      # JVM/Robolectric unit tests
.\gradlew.bat :app:testDebugUnitTest --tests "com.ai.phoneagent.core.CoreModuleTest"
.\gradlew.bat lintDebug              # CI gate
.\gradlew.bat installDebug           # requires a running emulator
```
- POSIX shells use `./gradlew`.
- CI uses JDK 17 and SDK platform `platforms;android-37.0` / build-tools `37.0.0`.
- Docs mention `jacocoTestReport`; no Jacoco plugin is configured. There is also no ktlint/detekt/spotless plugin — only Android Lint.
- `connectedAndroidTest` requires an emulator; see the emulator section.

## BUILD / LINT GOTCHAS
- Every Android module has `lint-baseline.xml` and `warningsAsErrors=true` (root `build.gradle.kts`). New warnings fail `lintDebug`; don't regenerate a baseline just to silence a new warning.
- No `.gitmodules`, no CMake/`externalNativeBuild`, no `app/src/main/cpp`; `ndkVersion` is still declared but nothing compiles native code. Prebuilt speech libs live in `app/src/main/jniLibs/{arm64-v8a,x86_64}/`; MNN libs are arm64-only (next bullet). `app/.cxx/` is stale and unused.
- `libMNN.so` / `libmnnllmapp.so` exist only for `arm64-v8a`; don't expect local MNN inference on an x86_64 emulator.
- CI still contains submodule/NDK/CMake steps left over from the removed native build; don't mirror them in local setup. Real CI gates: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, and an APK check for `lib/arm64-v8a/libsherpa-onnx-jni.so` + `libonnxruntime.so`.
- The X-ASR model is gitignored under `app/src/main/assets/sherpa-models/` (the repo keeps only its README). Without it the app builds but offline speech is unavailable; CI downloads it.
- `local.properties` needs `sdk.dir`; `github.token` is optional and only feeds debug `BuildConfig` for GitHub update checks. Never commit it.
- `gradle.properties` sets `android.overridePathCheck=true` because local paths may contain non-ASCII characters; don't remove it.

## DOCS / SITE
- Docs live only in `Aries-site/docs/` (mostly Chinese filenames plus English compatibility entry files). VitePress rewrites them to English slugs.
- Adding a doc: create the `.md` with an H1, add `file -> slug` in `Aries-site/docs/.vitepress/slugs.json`, and add the file to a category in `categories.json`. `npm run check` fails on missing/malformed/duplicate slugs, docs missing from the sidebar, dead links, unbalanced HTML, and broken README links.
- Run npm commands from `Aries-site/`, not from `site/` or `docs/`:
```bash
npm install
npm run dev:docs     # VitePress
npm run check
npm run build:docs   # docs-only build
npm run build:index  # regenerate route-map.json
npm run build        # full pipeline; also refreshes tracked site.generated.json
```
- `npm run check` expects the legacy redirect artifacts; run `npm run build:legacy` if they are missing or stale.
- Full `npm run build` runs `build:data`, which updates tracked `Aries-site/site/src/data/site.generated.json` from GitHub Releases (network failure falls back to cached data; `npm run build:data -- --offline` skips the fetch). For docs-only changes prefer `build:docs` + `build:index` to avoid unrelated diffs.
- `route-map.json` is generated; don't hand-edit it. `legacy-routes.json` is the frozen old-route map; keep old links working.

## TELEMETRY WORKER
- `telemetry-worker/` is independent: `npm run check` (tsc), `npm run dev` (wrangler), `npm run deploy`, `npm run db:local` / `db:remote` (D1 migrations).

## EMULATOR TESTING
- Use Android Studio emulators only; leave physical devices untouched. Approved AVDs: `aries test` (API 36), `Pixel 10` (API 37), `Pixel 5` (API 30), `Pixel 8` (API 34).
- Use `adb` from `<ANDROID_SDK_ROOT>\platform-tools\adb.exe`. Run `adb devices -l`, resolve the serial from the AVD name, and prefix every adb/install/logcat/screenshot command with `adb -s <serial>`; don't assume `emulator-5554`.
- Wait for `adb -s <serial> shell getprop sys.boot_completed` to return `1`.
- After changes: build, `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`, launch `com.ai.phoneagent/.MainActivity`, inspect logcat and affected UI; screenshot when visual behavior matters.
- Report AVD/serial, build/install/test results, and runtime/visual issues. If the emulator or SDK is unavailable, report the blocker instead of claiming the app was tested.
- Shizuku/virtual-display behavior is ROM-dependent; emulator coverage is limited.

## CONVENTIONS / ANTI-PATTERNS
- Declare Maven repositories only in `settings.gradle.kts`; module-level `repositories {}` fails `FAIL_ON_PROJECT_REPOS`.
- Reuse `m3t_*` tokens in `core/designsystem/src/main/res/values/m3t.xml` / `values-night/m3t.xml` (plus app-specific `app/src/main/res/values/m3t.xml`); avoid new hardcoded dp/sp/hex values.
- Put user-visible text in string resources; existing UI copy is Chinese, so match it.
- New Koin singletons/ViewModels belong in `app/src/main/java/com/ai/phoneagent/di/`; the DI contract tests in `app/src/test/.../di/` are the safety net.
- Ignore `._*` AppleDouble files and untracked local dirs (`temp/`, `source/`, `key/`, `.atomcode/`). `temp/` contains third-party reference clones, not project code.
- Don't read `UI design/*.pen` with text tools; it is an encrypted pen.dev design file (use the pencil MCP tools).

## NOTES
- Kotlin LSP is unavailable here; validate changes with Gradle tasks (`assembleDebug`, `testDebugUnitTest`, `lintDebug`), not editor diagnostics.
- Detailed architecture docs live in `Aries-site/docs/` (e.g. `TECHNICAL_OVERVIEW.md`, `自动化引擎/自动化 Agent 主循环.md`, `开发规范与工具/构建脚本与工具链.md`), but trust the build files for toolchain facts.
