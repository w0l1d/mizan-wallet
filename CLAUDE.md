# OpenWolf

@.wolf/OPENWOLF.md

This project uses OpenWolf for context management. Read and follow .wolf/OPENWOLF.md every session. Check .wolf/cerebrum.md before generating code. Check .wolf/anatomy.md before reading files.


# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
./gradlew assembleDebug           # Build debug APK
./gradlew build                   # Build + run all tests
./gradlew test                    # Run all unit tests
./gradlew detekt                  # Run linting
./gradlew detektFormat            # Auto-fix lint issues
./gradlew verifyPaparazziDebug    # Run screenshot tests
./gradlew connectedDebugAndroidTest # Instrumentation tests (requires device)
```

**Run a single test class:**
```bash
./gradlew :<module-path>:testDebugUnitTest --tests="com.ivy.package.ClassName"
# Example:
./gradlew :shared:ui:core:testDebugUnitTest -w-tests="com.ivy.ui.FormatMoneyUseCaseTest"
```

**Module path syntax:** Colons replace slashes — `shared/ui/core` → `:shared:ui:core`

## Architecture

Three-layer clean architecture with strict dependency rules:

```
UI Layer (Composables, ViewModels, ViewState mappers)
    ↓
Domain Layer (UseCases, business logic)
    ↓
Data Layer (Repositories, Room DB, Ktor HTTP, DataStore)
```

**Data flow:** Raw DB/network model → Domain model → ViewState/UI model

**Key principles:**
- Repositories are main-safe (IO on background threads via coroutines)
- Error handling uses Arrow's `Either` type
- Composables are dumb; ViewModels translate between UI and domain
- Feature modules expose public APIs; implementations are internal

## Module Structure

```
shared/base/            ← Foundation utilities, no project dependencies
shared/data/model/      ← Domain model data classes
shared/data/core/       ← Repositories, Room DAOs, Ktor clients, DataStore
shared/domain/          ← Use cases and business logic
shared/ui/core/         ← Composables, formatters, UI utilities
shared/ui/navigation/   ← Navigation logic
feature/<name>/         ← One module per feature screen
  feature/poll/public/  ← Public API for poll feature
  feature/poll/impl/    ← Implementation (Firebase Firestore)
widget/<name>/          ← App widgets using Glance
temp/                   ← Legacy code being migrated (avoid adding to)
buildSrc/               ← Gradle convention plugins
```

**Dependency rule:** `feature → shared:ui:core → shared:domain → shared:data:core → shared:data:model → shared:base`

Features can depend on other features but only through their `public/` API module when one exists.

## Convention Plugins (buildSrc)

Apply in module `build.gradle.kts` via:
- `ivy.feature` — standard feature module (includes Compose, Hilt, navigation)
- `ivy.compose` — Jetpack Compose setup
- `ivy.room` — Room database
- `ivy.hilt` — Hilt DI
- `ivy.paparazzi` — screenshot testing
- `ivy.integration.testing` — instrumentation tests
- `ivy.widget` — app widget with Glance

## Key Technologies

| Area | Library |
|------|---------|
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| DB | Room (SQLite) |
| HTTP | Ktor Client (OkHttp engine) |
| Reactive | Kotlin Coroutines + Flow |
| Functional | Arrow Kt (Either, Option) |
| Linting | Detekt + KtLint + Compose rules |
| Unit Tests | JUnit4 + Kotest assertions |
| Screenshot Tests | Paparazzi |
| Mocking | MockK |
| Serialization | Kotlinx Serialization |

## Testing Patterns

- Use `io.kotest.matchers` for assertions (`.shouldBe`, `.shouldNotBe`)
- Use `@RunWith(TestParameterInjector::class)` for parameterized tests
- Use `runBlocking` for suspend functions in unit tests
- Screenshot tests go in `src/test/java/` (Paparazzi runs on JVM, no device)
- Instrumentation tests go in `src/androidTest/java/`

## Dependency Versions

All versions in `gradle/libs.versions.toml`. Reference via `libs.module.name` or `libs.bundles.name` in build files. Do not hardcode dependency versions.

## Developer Guidelines

Detailed architecture and coding guidelines live in `docs/guidelines/`:
- `Architecture.md` — layered architecture rules
- `Screen-Architecture.md` — ViewModel/Composable patterns
- `Data-Modeling.md` — domain model conventions
- `Error-Handling.md` — Arrow Either usage
- `Unit-Testing.md` — test structure and patterns
- `Screenshot-Testing.md` — Paparazzi usage

Zakat feature scholarly reference (Sharia correctness, worked examples, code mapping):
`docs/zakat-knowledge/`.