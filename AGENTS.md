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
├── docs/                      # formal compatibility docs (BUILDING/CODING_STANDARDS/etc.)
├── Aries-site/                # project site + categorized docs center
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
| Formal docs entries | `docs/` | keep BUILDING/CODING_STANDARDS/GIT_WORKFLOW/TECHNICAL_OVERVIEW/FAQ/AI_PR_REVIEW/FEISHU_COLLABORATION |
| Categorized docs site | `Aries-site/docs/` | docs-center source; also keeps same English compatibility filenames |

## CONVENTIONS (PROJECT-SPECIFIC)
- Add repositories only in `settings.gradle.kts`, never in module Gradle files.
- Keep Android resources tokenized (`m3t.xml`, `values-night/m3t.xml`) before adding inline style values.
- For user-visible text, use string resources.
- For device verification, prefer `<ANDROID_SDK_ROOT>\platform-tools\adb.exe`.
- Do not delete `docs/*.md` compatibility entries while README/CONTRIBUTING/Aries-site still link to them.

## ANTI-PATTERNS
- Do not edit vendored code under `app/src/main/cpp/thirdparty/` unless explicitly required.
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
- If touching native/CMake content, scope changes to first-party app logic before third-party trees.
