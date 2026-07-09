# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working
with code in this repository.

## Overview

Android utility library published to GitHub Packages as
`io.github.lemkinator:common-utils`. Single `:lib` module.
See `gradle/libs.versions.toml` for SDK and language versions.

## Build Commands

```bash
./gradlew build          # Build the library
./gradlew clean          # Clean build outputs
./gradlew tasks          # List all tasks
./gradlew assembleRelease
./gradlew test           # Run unit tests
./gradlew koverVerifyDebug    # Check coverage floor (debug variant only)
```

Tests live under `lib/src/test/java/de/lemke/commonutils/`.

**Test stack**: Kotest 6 (`ShouldSpec` style) for pure Kotlin tests; JUnit 5 `@Test` +
`tech.apter.junit5.jupiter:robolectric-extension` for Android/Robolectric tests. Konsist
for architecture rules. Plus a small **JUnit 4 island** (see below) for the two
`@AndroidEntryPoint` activities' tests.

**Robolectric + JUnit 5**: uses `tech.apter.junit5.jupiter:robolectric-extension`
(`RobolectricExtension`) — JUnit 5 native, no vintage engine needed. This is the
default for every test in the module *except* the two below.

**JUnit 4 island (Hilt activities only)**: `CommonUtilsAboutActivity`/
`CommonUtilsSettingsActivity` (+ nested `SettingsFragment`) are `@AndroidEntryPoint`
with `@Inject lateinit var settings: SettingsRepository`. Their tests
(`CommonUtilsAboutActivityTest`, `CommonUtilsSettingsActivityTest`) need
`@HiltAndroidTest`/`HiltAndroidRule`, which are JUnit 4-only — incompatible with
`RobolectricExtension`. These two files alone use
`@RunWith(RobolectricTestRunner::class)` + JUnit 4 `org.junit.Test`/`org.junit.Before`,
mirroring GetIcon's own proven pattern (`app/src/test/java/de/lemke/geticon/ui/*`).
`org.junit.vintage:junit-vintage-engine` on `testRuntimeOnly` lets the JUnit Platform
(`useJUnitPlatform()`) discover and run them alongside the rest of the Kotest/JUnit 5
suite — do not extend this island to any other test file; everything else stays
JUnit 5/Kotest. Every `@HiltAndroidTest` Robolectric activity test must destroy its
launched `ActivityController` in `@After` — `AppCompatDelegate.setDefaultNightMode()`
recreates every live `AppCompatActivity` process-wide (including ones left over from a
previous test class), and a leaked activity's Hilt injection then fails against its
now-dead test component.

**`@NoCoverage` on `inline fun`**: Kover maps inline call-site coverage back to the original
definition via JaCoCo SMAP data. Under JUnit 5 + `RobolectricExtension`, Robolectric's class
loader initialises after the JaCoCo JVM agent — test classes are never instrumented, so inlined
call-site instructions are invisible to coverage and definition-site phantom stubs remain
uncovered. This requires `@NoCoverage` on all `inline fun` that are never called non-inline.
`crossinline` default-value lambdas always need `@NoCoverage` regardless of test runner: the
default compiles to a definition-site private static method that is never invoked (the default
is inlined at every call site). Apps using JUnit 4 + `RobolectricTestRunner` (e.g. the
OneUI-Sample-App) don't have this problem and carry far fewer `@NoCoverage` annotations.

## Pre-commit Hook

Enable local static-analysis checks before every commit:

```bash
git config core.hooksPath .githooks
```

## Publishing

Publishing triggers automatically via CI when `gradle/libs.versions.toml`
version changes on `main`. To publish manually:

```bash
./gradlew publishAllPublicationsToGitHubPackagesRepository
```

Requires `GH_USERNAME` and `GH_ACCESS_TOKEN` in `github.properties`,
gradle properties, or environment variables.

## Architecture

**Single-module utility library** — no DI framework, no ViewModel layer.
Code is organized into:

- **Root utilities** (`lib/src/main/java/de/lemke/commonutils/`):
  ~19 extension-function files targeting `Context`, `Activity`, `Fragment`
  (e.g., `SharingUtils.kt`, `URLUtils.kt`, `EmailUtils.kt`).
- **Pre-built Activities** (`ui/activity/`): Drop-in About, Settings,
  OOBE, LibraryInfo, and AboutMe screens.
- **UI Widgets** (`ui/widget/`, `ui/fragment/`): Reusable components like
  `InfoBottomSheet`, `NoEntryView`, `DimmingView`.
- **Data layer** (`data/`): `SettingsRepository.kt`
  (SharedPreferences-backed app settings) and `DelegatesAdvanced.kt`
  (type-safe `ReadWriteProperty` delegates for preferences).

**UI stack**: Samsung OneUI Design system (`io.github.tribalfs:oneui-design`)
is the primary UI framework. Jetpack Compose / Material3 is used
selectively. View Binding is enabled.

**Key dependencies**: Lottie (animations), AboutLibraries (OSS license
screen), Play Core (in-app updates + reviews), Splashscreen.

**Dependency exclusions**: Many AndroidX libraries are excluded in the
root `build.gradle.kts` to avoid version conflicts with OneUI Design's
bundled dependencies — be careful when adding new dependencies.

## Lifecycle Collection Convention

- `StateFlow<UiState>` → `collectState` (current value, re-emits on
  config change)
- `Channel<Event>` → `collectEvents` (one-shot, consumed once)
- ViewModels expose events as `Channel<Event>(BUFFERED)`; state as
  `StateFlow<UiState>`

## Version Policy

**Default: use the latest stable version of every dependency.**
Renovate keeps minor/patch updates current; bump majors manually
with release-note review.

Document any pin or downgrade with a `# Why pinned:` comment in
`libs.versions.toml`. Known exception classes:

1. **Kotlin + KSP lockstep** — KSP minor must match Kotlin minor
   (e.g. Kotlin `2.3.21` requires KSP `2.3.x`). Renovate's `kotlin`
   group enforces this.
2. **Static-analysis on fresh Kotlin majors** — Detekt typically
   lags new Kotlin releases by 1–3 months. Stay on the latest
   pre-release/alpha that supports your Kotlin version until a stable
   one lands. The `static-analysis` Renovate group bumps them together.
3. **Plugin AGP compatibility windows** — check plugin docs before
   bumping AGP.
4. **`oneui-design`** — versioned against OneUI major releases
   (e.g. `0.9.10+oneui8`); review manually per bump.
5. **Hilt version** — must match consumer apps to avoid
   duplicate-class errors. Renovate's `hilt` group bumps both repos
   together.

## Hilt

Hilt is added as a same-module `implementation` dep. Qualifier annotations
(`@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`) ship compiled into
the published AAR. No Hilt module is published: libraries must not install
bindings into consumer DI graphs uninvited. Each consumer app declares its own
`DispatchersModule`.

## Configuration

- Version catalog: `gradle/libs.versions.toml`
- Build config: configuration cache enabled, JVM max heap 2048m
- Maven artifact: `io.github.lemkinator:common-utils` at version defined
  in `libs.versions.toml`
- Package namespace: `de.lemke.commonutils`

## First-Run Flow

OneUI is activity-oriented; this lib is **multi-activity** (no single-activity /
Navigation Component). Shared screens (OOBE, About, AboutMe, Settings, Libs) are
activities. Fragments only for genuine sibling/tab content inside one screen.

First run = an ordered **chain of task-root activities**, OOBE first, advanced by
finish-then-start-next (`OnboardingUtils.kt`):

- `setupOnboarding(steps)` — app steps after OOBE (omit for OOBE-only apps).
- `onboardIfNeeded(versionCode, versionName, allowSkip)` — call FIRST in the launcher
  activity's `onCreate`, before inflating UI; returns `null` (caller must `?: return`)
  when it launched OOBE. `allowSkip` (gated by the app) honors `EXTRA_SKIP_ONBOARDING`
  for benchmarks.
- `advanceOnboarding()` — a step calls this when done; starts the next step or, past the
  last, commits `acceptedTosVersion` and starts the main activity.
- `isOnboardingStep()` — for dual-context steps (also reachable standalone).

Why: each step is task root ⇒ predictive back = app exit, no main behind. Redirect
before building main ⇒ no leak on first start. Commit only past the last step ⇒
atomic (kill mid-chain replays from OOBE). Full chain on any first-run trigger
(install or TOS bump) for one uniform, reliable commit point.
