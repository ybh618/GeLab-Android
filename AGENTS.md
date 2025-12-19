# Repository Guidelines
use context7
## Project Structure & Module Organization
- `app/src/main/java/com/gelabzero/app/`: Kotlin source (activities, agents, accessibility, data, overlay).
- `app/src/main/res/`: Android resources (themes, strings, XML service config).
- `app/src/main/AndroidManifest.xml`: app and service declarations.
- `gradle/`, `build.gradle.kts`, `settings.gradle.kts`: Gradle configuration and wrapper.

## Build, Test, and Development Commands
- `./gradlew assembleDebug`: builds a debug APK.
- `./gradlew installDebug`: installs the debug build on a connected device/emulator.
- `./gradlew lint`: runs Android Lint checks.
- `./gradlew test`: runs JVM unit tests (none are defined yet).

## Coding Style & Naming Conventions
- Language: Kotlin; target JVM 17 (see `app/build.gradle.kts`).
- Indentation: 4 spaces; follow Kotlin style and Android Studio defaults.
- Naming: `PascalCase` for classes/files, `camelCase` for functions/vars, `lower_snake_case` for resource IDs.
- Compose UI lives under `app/src/main/java/com/gelabzero/app/ui/`.

## Testing Guidelines
- No test sources are present under `app/src/test` or `app/src/androidTest` yet.
- When adding tests, follow Android’s defaults:
  - Unit tests in `app/src/test` with `*Test.kt` naming.
  - Instrumented tests in `app/src/androidTest` with `*Test.kt` naming.
  - Run with `./gradlew test` or `./gradlew connectedAndroidTest`.

## Commit & Pull Request Guidelines
- Git history is not available in this environment; no established commit message convention was detected.
- Use clear, imperative messages (e.g., "Add agent loop retry backoff").
- PRs should include a concise description, testing notes (commands + results), and screenshots if UI changes were made.

## Configuration Tips
- `local.properties` should contain your Android SDK path and should not be committed.
- Ensure Android SDK 34 and Java 17 are installed before building.
