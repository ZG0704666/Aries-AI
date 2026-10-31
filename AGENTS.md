# ARIES AI PROJECT KNOWLEDGE BASE

**Scope:** repository root only

## OVERVIEW
Android app for AI-driven UI automation on Android 11+, with Kotlin + Compose + native components.

## STRUCTURE
```text
.
├── app/                       # Android app module (`com.ai.phoneagent`)
├── core/                      # shared modules: common/designsystem/prompt/shizuku
├── feature/                   # feature modules: settings/updates
├── Aries-site/                # project site + the only documentation directory
│   └── docs/                  # categorized docs + compatibility entry files
└── settings.gradle.kts        # module graph + repository policy
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| App behavior/UI | `app/src/main/java/com/ai/phoneagent/` | entry activities and services live here |
| Compose/theming | `app/src/main/res/values/m3t.xml` | use tokens; avoid hardcoded UI values |
| Module wiring | `settings.gradle.kts` | includes `:core:*` and `:feature:*` modules |
| Dependency/repo policy | `settings.gradle.kts` | `FAIL_ON_PROJECT_REPOS` enforced |
| Build config | `app/build.gradle.kts` | SDK versions, build types, native build, deps |
| Project documentation | `Aries-site/docs/` | single source for docs-center, categorized docs, and English compatibility entries |

## CONVENTIONS (PROJECT-SPECIFIC)
- Add repositories only in `settings.gradle.kts`, never in module Gradle files.
- Keep Android resources tokenized (`m3t.xml`, `values-night/m3t.xml`) before adding inline style values.
- For user-visible text, use string resources.
- For device verification, prefer `<ANDROID_SDK_ROOT>\platform-tools\adb.exe` and use an Android Studio emulator only.
- Keep all project documentation under `Aries-site/docs/`; do not recreate a second root-level `docs/` tree.
- Update README, CONTRIBUTING, and docs-center routes whenever documentation paths change.

## ANDROID EMULATOR DEVELOPMENT & TESTING
- Routine development, installation, UI verification, and automated testing must run on an Android Studio Android Emulator. Do not require or use a physical Android device when a listed emulator is available.
- The approved AVDs are `aries test` (Android 16 / API 36), `Pixel 10` (Android 17 / API 37), `Pixel 5` (Android 11 / API 30), and `Pixel 8` (Android 14 / API 34).
- Before any device operation, run `adb devices -l` and identify a running emulator serial. Resolve the serial from the AVD name; do not assume that a serial such as `emulator-5554` is stable.
- When more than one approved emulator is running, choose the emulator that matches the task's API or Android-version requirement and document the selected AVD and serial in the test report.
- Every install, launch, logcat, screenshot, test, and package-management command must explicitly target the selected emulator with `adb -s <emulator-serial>` (or the equivalent Gradle device selector). Never run an unscoped adb command against a mixed device list.
- If a physical device appears in `adb devices -l`, leave it untouched. Do not install, launch, inspect, reboot, disconnect, or modify it. If no approved emulator is available, inspect the configured AVDs with `emulator -list-avds` and start a suitable approved AVD before testing.
- Wait for the selected emulator to finish booting before installation or launch. Confirm boot completion with `adb -s <emulator-serial> shell getprop sys.boot_completed`.
- Discover the application module, APK path, package name, and launcher Activity from the project configuration or build outputs; do not assume them.
- After relevant changes, build with the project's Gradle wrapper, install/update the debug APK on the selected emulator, launch the app, and inspect startup, relevant Logcat output, and affected UI flows. Capture an emulator screenshot when visual verification matters.
- Report the selected AVD/serial, build result, installation result, tests actually executed, and any runtime or visual issues. If the emulator, SDK, build, or test environment is unavailable, report the exact blocker instead of claiming the app was tested.

## ANTI-PATTERNS
- 语音引擎已迁移至 sherpa-onnx（预编译 so 走 `app/src/main/jniLibs/`）；旧 sherpa-ncnn 子模块与 `app/src/main/cpp/`（CMake 构建）已于 2026-10-31 移除，如需回退请从 git 历史整体恢复。
- Do not hardcode new `dp`/`sp`/hex colors where a reusable token should exist.
- Do not bypass `FAIL_ON_PROJECT_REPOS` by adding Maven repos in module `build.gradle.kts`.

## COMMANDS
```bash
# run from repository root
./gradlew assembleDebug
./gradlew testDebugUnitTest

# Optional checks
./gradlew lint
./gradlew connectedAndroidTest

# Install debug APK
./gradlew installDebug
```

## NOTES
- LSP for Kotlin is unavailable in this environment; validate changes with Gradle tasks.
- There is no native/CMake build anymore: speech native libraries ship as prebuilt `.so` files in `app/src/main/jniLibs/`.
