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
```

Tests live under `lib/src/test/java/de/lemke/commonutils/`.

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

## Configuration

- Version catalog: `gradle/libs.versions.toml`
- Build config: configuration cache enabled, JVM max heap 2048m
- Maven artifact: `io.github.lemkinator:common-utils` at version defined
  in `libs.versions.toml`
- Package namespace: `de.lemke.commonutils`
