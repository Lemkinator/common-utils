# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working
with code in this repository.

## Overview

Android utility library published to GitHub Packages as
`io.github.lemkinator:common-utils`. Single `:lib` module.
Min SDK 26, Target SDK 36, Kotlin 2.3.21, Java 21.

## Build Commands

```bash
./gradlew build          # Build the library
./gradlew clean          # Clean build outputs
./gradlew tasks          # List all tasks
./gradlew assembleRelease
./gradlew test           # Run unit tests
./gradlew koverVerify    # Check coverage floor (≥12%)
```

Tests live under `lib/src/test/java/de/lemke/commonutils/`.

**Test stack**: Kotest 6 (`ShouldSpec` style) + Robolectric via
`kotest-extensions-robolectric` for Android tests. Konsist for architecture
rules. Kover enforces ≥12% line coverage.

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

## Hilt Dispatcher Qualifiers

`@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher` are provided by
`CoroutineDispatchersModule` (shipped in this artifact). Consumers with
Hilt can inject them directly; the module installs into
`SingletonComponent`.

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

## Hilt (same-module)

Hilt is added as a same-module `implementation` dep — qualifiers and
`CoroutineDispatchersModule` ship compiled into the published AAR.
Consumers see `@IoDispatcher` / `@DefaultDispatcher` / `@MainDispatcher`
because those annotation classes live in our AAR, not in Hilt's runtime jar,
so no transitive exposure is needed. Every consuming app already declares
Hilt directly. A separate `:lib-di` subproject would let consumers opt out
but adds a publish target for zero practical gain.

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
