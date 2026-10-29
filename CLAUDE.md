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
./gradlew apiCheck       # Compare the public ABI with lib/api/lib.api
./gradlew apiDump        # Rewrite lib/api/lib.api after an intended API change
./gradlew buildHealth    # Dependency analysis report and gate
```

Tests live under `lib/src/test/java/de/lemke/commonutils/`.

**Test stack**: Kotest 6 (`ShouldSpec` style) for pure Kotlin tests; plain JUnit 4
(`@RunWith(RobolectricTestRunner::class)`) for every Android/Robolectric test. Konsist
for architecture rules.

**`ShadowFileProvider`** (published testFixtures) — Robolectric shadow for both
`FileProvider.getUriForFile` overloads and the provider's `query`, `getType`, `openFile` and
`delete`, which normalizes paths to `/` before matching; apply
`@Config(shadows = [ShadowFileProvider::class])` instead of `mockkStatic(FileProvider::class)`
whenever a test needs a real (not stubbed) URI or reads one back through a `ContentResolver`, since
the stock implementation compares raw `getCanonicalPath()` strings and throws on Windows. The shadow
resolves roots per call and keeps no strategy cache. `FileProviderContentContract` calls
`resetFileProviderCache()` in its own `@After`, so no subclass can drop it: stock `getUriForFile` reads `sCache` before Robolectric attaches the provider
to the next test, so a root cached by the previous test makes it throw.
`FileProviderContentContract` (`src/testShared`) holds the stock-provider tests; the Robolectric shadow test and
`StockFileProviderInstrumentedTest` (stock `FileProvider` on a device) both run it.

**`assertLaunchLatchConventions`** (published testFixtures,
`LaunchLatchConventions.kt`): each app's Konsist `CodingConventionsTest` calls
`Konsist.scopeFromProduction().assertLaunchLatchConventions()`. It fails on raw
activity launches, raw result registration and every `show`/`showNow` whose
receiver is no allowlisted non-dialog type. An app passes its own non-dialog
types as `extraShowReceivers`.

**`assertNoMockedOwnTypes`** (published testFixtures, `MockingConventions.kt`): each app's Konsist
`CodingConventionsTest` calls `Konsist.scopeFromTest().assertNoMockedOwnTypes()`. It fails on
a `mockk`, `spyk`, `mockkClass`, `mockkStatic`, `mockkObject` or `mockkConstructor` call and on a `@MockK`,
`@RelaxedMockK` or `@SpyK` property whose type the project declares: own types get a fake, MockK stays at external
boundaries. The KDoc lists the forms it detects and the inferred-type cases it misses.

**Published testFixtures** declare kotest, coroutines-test, JUnit 4, Hilt testing, Konsist and Robolectric as
`compileOnly`, so a consumer keeps its own versions and adds the ones its fixture groups need to its own test classpath:

- `MainDispatcherExtension(dispatcher)` — Kotest extension; sets a fresh test `Dispatchers.Main` around each root test.
  Needs kotest and coroutines-test.
- `PausableDispatcher(delegate, whileResumed)` — holds blocks dispatched between `pause()` and `resume()`; `resume()`
  hands them to the delegate in dispatch order and no later block overtakes them. `WhileResumed.Dispatch` (default)
  hands every other block to the delegate; `WhileResumed.RunInPlace` runs it in place like `Dispatchers.Unconfined`.
  Needs only kotlinx-coroutines.
- `HiltTestRule(this)` and `assertHiltTestRuleConventions()` — the one Hilt rule of a `@HiltAndroidTest`; it closes
  everything registered with `closeAfterTest` after the test. The rule needs hilt-android-testing and JUnit 4; the
  convention check also needs Robolectric and Konsist, built and tested against the version in `gradle/libs.versions.toml` only.
- `assertClassBodyOrder()`, `assertArchitecture(rootPackage)`, `assertNoCommonUtilsTypeExtensions()` and
  `assertLaunchLatchConventions()` — Konsist rules that each app's `CodingConventionsTest` and `ArchitectureTest`
  call on `Konsist.scopeFromProduction()`. Need Konsist; built and tested against the version in `gradle/libs.versions.toml` only.
- `assertNoMockedOwnTypes()` — Konsist rule that each app's `CodingConventionsTest` calls on `Konsist.scopeFromTest()`.
  Needs Konsist; built and tested against the version in `gradle/libs.versions.toml` only.
- `assertBackupConventions()` — Konsist rule that each app with a backup calls on `Konsist.scopeFromProduction()`: no class
  is both `@Entity` and `@Serializable`. Needs Konsist.
- `BackupSectionContract<E>` and `DirectBackupTransaction` — the tests every backup section runs through `BackupEngine`
  (JUnit 4; see "Backup"), and a transaction that runs in place. `FakeBackupDocuments` fakes `BackupDocuments`; `BackupEngine.exportFiles()` returns the files of
  an export and fails on `BackupExport.TooLarge`.
- `RecordingDocumentsProvider` (records and performs `deleteDocument`), `ShadowFileProvider`, `Activity.receivePickerResult`
  (returns the picker intent it answered),
  `File.hasPngSignature`, `RecordingIntentLauncher` — export and launcher fakes. The provider and the shadow need Robolectric.
- `Context.cacheWriteUriPattern(pathName, directoryName, fileName)` and `Context.cacheFileOf` — the content URI layout of a
  cache write; `directoryName` is `share` or `clipboard`.
- `FakeInAppReview(available)` (`canShow()` returns `available`; records `showIfPossible`/`showOrFinish` calls; `showOrFinish` finishes the activity) — the fake of the `InAppReview` interface that `PlayInAppReview(settings)` implements.
- `idleMainLooper`, `awaitMainLooperUntil`, `ActivityScenario.read` and `returnFromLaunchedScreen` (Robolectric);
  `ActivityScenario.waitUntil` and `RecyclerView.isSettled` (instrumented).
- `ViewModel.clearForTest()` — cancels `viewModelScope` and calls `onCleared`.

**Robolectric + JUnit 5**: See the shared Robolectric/JUnit 5 policy in
`A:\repo\android\CLAUDE.md`. This repo previously bridged Robolectric onto JUnit 5 via the
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

**`@NoCoverage` on `inline fun`**: Kover maps inline call-site coverage back to the original
definition via JaCoCo SMAP data. This requires `@NoCoverage` on all `inline fun` that are
never called non-inline, and unconditionally on `crossinline` default-value lambdas (the
default compiles to a definition-site private static method that's never invoked, since the
default is inlined at every call site regardless of runner). These annotations predate the
JUnit 4 migration above and haven't been re-audited against the new runner — some may now
be unnecessary; treat that as an open follow-up, not a settled fact.

## Dependency Analysis

`./gradlew buildHealth` runs in the `check-formatting` CI job. It fails on unused dependencies, incorrect
configurations and unused annotation processors; other advice warns. The `dependencyAnalysis {}` block in the root
`build.gradle.kts` holds the facade bundles, the narrow guava exclude and the `hilt-android` exclude. Each bundle
names its primary and only the transitive internals the report lists, so it never masks a declared artifact.
`hilt-android` is excluded from the incorrect-configuration check: the plugin reads the types Hilt generates into the
module as public API, while the library keeps Hilt `implementation` (see Hilt). `oneui-design` and the testFixtures
`androidx.test:core` are `api` because their types appear in the public API. Report:
`build/reports/dependency-analysis/build-health-report.txt`.

## Pre-commit Hook

Enable local static-analysis checks before every commit:

```bash
git config core.hooksPath .githooks
```

## Architecture

**Single-module utility library** — ships opt-in Hilt modules and one ViewModel, `BackupViewModel`, which hosts
the backup flow of the settings screen.
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

## Preference XML ↔ Settings Binding Convention

See the shared convention in `A:\repo\android\CLAUDE.md` — applies to this library's own screens
(`CommonUtilsSettingsActivity`) exactly as it does to every consumer app.

## Version Policy

See the shared version policy in `A:\repo\android\CLAUDE.md`. Two additional exceptions specific to
this library:

1. **`oneui-design`** — versioned against OneUI major releases (e.g. `0.9.10+oneui8`); review
   manually per bump.
2. **Hilt version** — must match consumer apps to avoid duplicate-class errors. Renovate's `hilt`
   group bumps both repos together.

## Backup

Export and import of an app's data to one zip file. The pure-Kotlin core (`backup/core`, no `android.*`, `androidx.*`,
`java.*` or `javax.*` import, guarded by `ArchitectureTest`) holds the manifest, read, upgrade, validate, Merge,
Replace and the optional signature. `backup/` holds the Android layer: `ZipBackupContainer`, `BackupDocuments`,
`BackupStateHolder`, `BackupViewModel`.

A section id matches `[a-z0-9_-]+`; the engine refuses another id. An export that exceeds an import limit returns
`BackupExport.TooLarge` instead of files the import refuses; the holder deletes the partial document and shows
`BackupResult.ExportTooLarge`.

An app opts in with:

- one `BackupSection<E>` per data kind, bound `@Binds @IntoSet`; a `MergeableSection` adds merge rules and
  enables Merge, otherwise the app offers Replace only. Entries are `@Serializable` DTOs, never Room entities.
- one `BackupSectionLabel(sectionId, @StringRes label)` per section, `@Provides @IntoSet`: the import preview names the
  section by it, or by its id if the app binds none.
- `BackupApp`, and a `BackupTransaction` bound to its database transaction (an object expression: the interface has a
  generic method, so no lambda).
- `@Module(includes = [CommonUtilsBackupModule::class])` on its own module, with `CommonUtilsCoroutinesModule` bound for
  `@DefaultDispatcher`, plus `R.xml.preferences_backup` in the preference list it passes to
  `setupCommonUtilsSettingsActivity`, called during process start (`Application.onCreate`) because the list is static
  and a recreated screen after process death needs it. The module binds `BackupScreenDeps`; the settings fragment injects
  `Optional<BackupScreenDeps>` through Hilt, so it needs no static setup and works after process death. An app without
  the module binds nothing and the screen removes the Backup category.

An app that signs its backups binds `@Provides fun signer(): BackupSigner = HmacSha256BackupSigner(keyId, key)` in its
module: its export carries `signature.json`, and its import refuses an unsigned file, or one with a changed manifest or section file, before any write. An
app without that binding exports and reads plain files, and ignores a signature it finds. The key never enters a repo.

`BackupSectionContract` and `assertBackupConventions()` (testFixtures) test a section through the whole engine.
Merge helpers `newerWins` and `higherRankWins` end in a total order, so every device keeps the same entry, only if
their `order` tells two different entries apart: pass `encodedOrder`, or the local entry stays on a tie.
Stamps in the file are ISO-8601 text with a UTC offset.
The import skips and counts an entry whose `upgrade`, decoding or `validate` throws any exception.
An `upgrade` step throws for an entry it cannot map.
The import refuses a file with no known section.
It refuses a section file with more than 100,000 entries.
It refuses a section file that nests deeper than 64 levels.
It refuses an entry of more than 100,000 values.
It refuses an entry of more than 1 MiB, counted between two separators.
A scan of the bytes finds the nesting, value and entry byte limits before any decode builds a tree.
The decode counts the entries of a section.
Size caps: 16 MiB uncompressed per section and 32 MiB in all.
The manifest holds at most 64 KiB, the signature file at most 4 KiB.
The import streams at most 33 MiB of the picked document.
It never holds the zip file in memory.
`BackupEngine.read` empties the map it receives.
Each section file leaves the map when its decode starts.
The reader decodes a section file one entry at a time.
While a section decodes, memory holds the section files still to come.
It also holds the bytes and the text of that section, one entry tree and the typed entries so far.
Turning the bytes into text needs a temporary buffer of up to twice the section size.
The manifest and the signature file obey the nesting limit.
A section file over a limit shows "The file could not be read."

## Launches and Exports

Helpers that start another screen return a `LaunchOutcome`. A RESUMED collector marks its
state handled when `LaunchOutcome.isSettled` is true; on `Dropped` the state stays pending.

- `startActivityForOutcome(intent, errorMessage)` — gated start; any start failure toasts `errorMessage`
  (default: the generic error). The share helpers pass the sharing-not-supported message.
- `launchCreateDocument(fileName, mimeType, extension, launcher)` — `ACTION_CREATE_DOCUMENT`
  picker for any type; `exportBitmap` is its PNG case.
- `registerForDocumentPick { pick -> }` (activity or fragment) — the picker launcher; it
  delivers a `DocumentPick`.
- `registerForOpenDocument { uri -> }` (activity or fragment) and `launchOpenDocument(mimeType, launcher)` — the gated
  picker for an existing document; `uri` is null when the user cancels. A device without a picker shows the error toast.
- `File.share(context, mimeType, title)` / `List<File>.share(context, mimeType, title)` — PNG and no chooser title by
  default.
- `BitmapExportStateHolder(scope, exporter)` — one save, copy or share at a time, exposed
  as `BitmapExport` state. Only `Running` blocks: a new export replaces a waiting `Result`, and a document
  created while one runs is deleted. The screen passes each `BitmapExport.Result` to
  `launchBitmapExport(result, picker)` and calls `onHandled(result)` when that returns true.
  Tests drive the holder with the testFixtures `FakeBitmapExporter`.
- Recent colors: `MAX_RECENT_COLORS`, `List<Int>.withRecentColor`, the
  `delegates.recentColors(default)` delegate, and `showColorPickerOnce(tag, color, recent)`.

## Hilt

Hilt is a same-module `implementation` dep: apps that include a module apply Hilt themselves, so the library
does not leak its Hilt version onto their compile classpath. Qualifier annotations (`@IoDispatcher`,
`@DefaultDispatcher`, `@MainDispatcher`, `@ApplicationScope`) ship compiled into the published AAR.

No binding installs itself into a consumer DI graph. An opt-in module carries `@DisableInstallInCheck` and apps include
it. The one `@InstallIn(SingletonComponent::class)` module of the library, `BackupScreenOptionalModule`, holds only
`@BindsOptionalOf BackupScreenDeps`: it adds no binding, so an app without a backup is unaffected.

`CommonUtilsCoroutinesModule` provides the four qualified bindings; an app opts in with:

```kotlin
@Module(includes = [CommonUtilsCoroutinesModule::class])
@InstallIn(SingletonComponent::class)
interface AppCoroutinesModule
```

An app that includes it must drop its own bindings for `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher` and
`@ApplicationScope` (a duplicate binding is a Dagger compile error), and its Dagger/Hilt version must be at least the
library's.

`CommonUtilsInAppReviewModule` binds `InAppReview` to `PlayInAppReview` (needs a `SettingsRepository` binding); an app
includes it from its own `InAppReviewModule`, which a test replaces with `@TestInstallIn` to bind `FakeInAppReview`. The app must drop its own `@Binds` for `InAppReview`, because a duplicate binding fails compilation.

Hilt modules need no ProGuard/R8 keep rules.

## Configuration

- Version catalog: `gradle/libs.versions.toml`
- Build config: configuration cache enabled, JVM max heap 2048m
- Maven artifact: `io.github.lemkinator:common-utils` at version defined
  in `libs.versions.toml`
- Package namespace: `de.lemke.commonutils`

## Releases

Releases are a manual owner step. A push to main that changes the
`common-utils` version in `gradle/libs.versions.toml` publishes.
Agents never bump the version or release. Every release bumps only the patch
number, breaking changes included.

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
  last, commits `acceptedTosVersion` and starts the main activity. Returns a `LaunchOutcome`:
  on `Dropped` the step stays open and restores its controls.
- `isOnboardingStep()` — for dual-context steps (also reachable standalone).

Why: each step is task root ⇒ predictive back = app exit, no main behind. Redirect
before building main ⇒ no leak on first start. Commit only past the last step ⇒
atomic (kill mid-chain replays from OOBE). Full chain on any first-run trigger
(install or TOS bump) for one uniform, reliable commit point.
