# Agent Working Notes

## Test-device operations

- Any task involving pushing/testing on Android devices (ADB install, ADB push, ADB commands, packaging for device verification) should be run with escalation.
- Use shell_command with `sandbox_permissions: "require_escalated"` directly for these commands.
- Preferred adb path: `<ANDROID_SDK_ROOT>\platform-tools\adb.exe`.
- Unless the user explicitly says otherwise, after completing any code change you should automatically build a debug APK, install it to the connected test device, confirm the installation is complete, and launch `com.ai.phoneagent` once for verification.
- Default env helpers when needed:
  - `$env:JAVA_HOME='<ANDROID_STUDIO_JBR_PATH>'`
  - `$env:GRADLE_USER_HOME='<PROJECT_ROOT>\.gradle'`
  - `$env:ANDROID_USER_HOME='<PROJECT_ROOT>\.android'`
  - `$env:ANDROID_SDK_ROOT='<ANDROID_SDK_ROOT>'`

## UI/Theming constraints

- Align overall UI aesthetics with the existing home page visual style and Google official Material 3 theme guidance.
- Do not hardcode UI styles in layouts or code.
- For colors/dimens/typography/shape/widget styles, use centralized resources in `app/src/main/res/values/m3t.xml` (and `values-night/m3t.xml` for dark mode).
- For user-facing text, use string resources instead of inline literals.
- Avoid inline `dp`/`sp`/hex color/style values in XML unless there is no reusable token; if a new value is required, add a named token to `m3t.xml` first.
