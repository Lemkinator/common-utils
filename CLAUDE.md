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

**Test stack**: Kotest 6 (`ShouldSpec` style) for pure Kotlin tests; plain JUnit 4
(`@RunWith(RobolectricTestRunner::class)`) for every Android/Robolectric test. Konsist
for architecture rules.

**Robolectric + JUnit 5**: Robolectric has no native JUnit 5 support, so every Robolectric
test in this module runs on plain JUnit 4 via `org.junit.vintage:junit-vintage-engine` on
`testRuntimeOnly`, which lets the JUnit Platform (`useJUnitPlatform()`) discover and run
them alongside the rest of the module's Kotest/JUnit 5 suite.
This repo previously bridged Robolectric onto JUnit 5 via the
experimental `tech.apter.junit5.jupiter:robolectric-extension`; it was reverted because
that bridge only isolates state **per test class**, not per test method, which let shared
Robolectric/Android state leak between a class's own test methods (worked around at the
time by hand-draining the main Looper, or collapsing multiple tests into one) and,
because Robolectric reuses its sandbox classloader across test classes with identical
`@Config`, potentially between unrelated classes too. Plain `RobolectricTestRunner`
resets Robolectric's own shadowed framework/Looper state before every test method, which
removed the need for hand-draining *that* state. It does **not** reset real third-party
static singletons (see next paragraph) — those still need manual `@After` cleanup.

Every `@HiltAndroidTest` Robolectric activity test must destroy its launched
`ActivityController` in `@After` — `AppCompatDelegate.setDefaultNightMode()` recreates
every live `AppCompatActivity` process-wide (including ones left over from a previous test
class), and a leaked activity's Hilt injection then fails against its now-dead test
component. More generally: Robolectric only resets its **own** shadowed framework
statics between tests — a real third-party static singleton (e.g.
`com.google.android.material.snackbar.SnackbarManager`, see `SnackBarUtilsRobolectricTest`)
is not a shadow and keeps whatever state a previous test left it in. Drain/reset any such
singleton after each test, not just defensively before it — for pending main-Looper tasks,
add `@get:Rule val drainMainLooper = DrainMainLooperRule()` rather than hand-rolling an
`@After` method per test class.

**Test order independence**: `io.kotest.provided.ProjectConfig` sets
`specExecutionOrder = SpecExecutionOrder.Random`, so Kotest spec execution order varies
run to run — this is Kotest's own engine (`kotest-runner-junit5`), separate from Jupiter,
so it does not affect the JUnit 4/Robolectric classes below. Those run via
`junit-vintage-engine`, which has no native class-order-randomization hook exposed
through Gradle; order-independence there is enforced by test hygiene (no shared mutable
state left behind) rather than by a randomizer — verified by repeated full-suite runs.

**`@NoCoverage` on `inline fun`**: Kover maps inline call-site coverage back to the original
definition via JaCoCo SMAP data. This requires `@NoCoverage` on all `inline fun` that are
never called non-inline, and unconditionally on `crossinline` default-value lambdas (the
default compiles to a definition-site private static method that's never invoked, since the
default is inlined at every call site regardless of runner). These annotations predate the
JUnit 4 migration above and haven't been re-audited against the new runner — some may now
be unnecessary; treat that as an open follow-up, not a settled fact.

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

**testFixtures capability gotcha (fixed 1.2.1):** AGP derives a testFixtures configuration's
published capability from the Gradle project name (`:lib`), not from the `artifactId` override
in the root `build.gradle.kts`'s hand-rolled `MavenPublication`. 1.2.0 shipped with the
testFixtures variant under the wrong capability (`lib-test-fixtures` instead of
`common-utils-test-fixtures`), silently breaking `testFixtures(libs.common.utils)` for every
consumer — never caught locally because composite-build substitution matches by project identity,
not capability. Fixed in `lib/build.gradle.kts` by additively declaring the correct capability
on the relevant configurations (see the comment there) rather than renaming the project, which
would've also silently renamed every `:lib:*` task to `:common-utils:*`.

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
