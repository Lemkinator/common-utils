<!--suppress HtmlDeprecatedAttribute CheckImageSize-->
<div align="center">

[![Website](https://img.shields.io/website?down_color=red&down_message=offline&up_color=blue&up_message=online&url=https%3A%2F%2Fwww.leonard-lemke.com)](https://www.leonard-lemke.com/rr)
[![License](https://badgen.net/badge/license/Apache%202.0/blue)](https://opensource.org/licenses/Apache-2.0)
[![API Level](https://badgen.net/badge/API/26%2B/green)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2FLemkinator%2Fcommon-utils%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.kotlin&label=kotlin&color=7F52FF&logo=kotlin)](https://kotlinlang.org/)

[![Last Commit](https://badgen.net/github/last-commit/Lemkinator/common-utils)](https://github.com/Lemkinator/common-utils/commits/)
[![Issues](https://badgen.net/github/open-issues/Lemkinator/common-utils)](https://github.com/Lemkinator/common-utils/issues)
[![Pull Requests](https://badgen.net/github/open-prs/Lemkinator/common-utils)](https://github.com/Lemkinator/common-utils/pulls)
[![Contributors](https://badgen.net/github/contributors/Lemkinator/common-utils)](https://github.com/Lemkinator/common-utils/graphs/contributors)

[![Repo Size](https://img.shields.io/github/repo-size/Lemkinator/common-utils)](https://github.com/Lemkinator/common-utils)
[![Lines of Code](https://sloc.xyz/github/Lemkinator/common-utils)](https://github.com/Lemkinator/common-utils)
[![CodeFactor](https://www.codefactor.io/repository/github/lemkinator/common-utils/badge/main)](https://www.codefactor.io/repository/github/lemkinator/common-utils/overview/main)
[![codecov](https://codecov.io/gh/Lemkinator/common-utils/graph/badge.svg)](https://codecov.io/gh/Lemkinator/common-utils)

# Common utils

This lib consists of common utils that I use in my Android Apps.

<img loading="lazy" src="lib/src/test/screenshots/about_default_dark.png" height="350" alt="About"/>
<img loading="lazy" src="lib/src/test/screenshots/about_me_default_dark.png" height="350" alt="About me"/>
<img loading="lazy" src="lib/src/test/screenshots/libs_default_dark.png" height="350" alt="Open source libraries"/>
<img loading="lazy" src="lib/src/test/screenshots/oobe_default_dark.png" height="350" alt="Onboarding"/>
<img loading="lazy" src="lib/src/test/screenshots/settings_default_dark.png" height="350" alt="Settings"/>

<br><br>

## Settings migration

`migrateSettings` moves settings that older app versions stored elsewhere into the app's settings store.
Call it once at app start, before anything reads settings.
The Hilt provider of the app's settings is the natural place:

```kotlin
@Provides
@Singleton
fun provideUserSettings(@ApplicationContext context: Context): UserSettings =
    UserSettings(
        PreferenceManager.getDefaultSharedPreferences(context).migrateSettings(context) {
            dataStore("userSettings") {
                keys("iconSize", "maskEnabled")
                key("textsize", to = "textSize")
                key("errorLimit") { it.toString() }
            }
            sharedPreferences("legacyPrefs") { keys("lastSync") }
            renames { key("pdf_page_snap_pref", to = "pdfPageSnap") }
        },
    )
```

- `dataStore(name)` reads `files/datastore/<name>.preferences_pb` directly, without a DataStore instance.
- `sharedPreferences(name)` reads another SharedPreferences file.
- `renames` moves keys to new names inside the target store.
  Its `key(from, to, convert)` requires a `to` that differs from `from`, and it offers no `keys()`.
- `key(from, to, convert)` maps one key. `convert` returns a Boolean, Int, Long, Float, String or `Set<String>`, or null to drop the value.
- If `convert` throws, the call logs the exception and drops the value. It still removes the source, so startup continues.
- A key that already exists in the target stays untouched. Among the sources, the first declared one wins.
- The call commits the target before it returns. Only then does it remove each source, so a second call finds nothing to move.
- A missing source is a no-op. A corrupt DataStore file stays on disk, and startup continues.
- Every call also moves the library's own `lastInAppReview` out of its former `InAppReviewUtils` file.
  Call `migrateSettings(context)` even when the app has nothing else to move.
- The app must stop opening a migrated DataStore file, since the call deletes it.

<br><br>

## Apps using Common utils

<div>
  <a href="https://github.com/Lemkinator/oneurl"><img src="https://github-readme-stats.vercel.app/api/pin/?username=Lemkinator&repo=oneurl&title_color=0891b2&text_color=ffffff&icon_color=0891b2&bg_color=1c1917&hide_border=true&locale=en"  alt="OneURL"/></a>
  <a href="https://github.com/Lemkinator/sudoku"><img src="https://github-readme-stats.vercel.app/api/pin/?username=Lemkinator&repo=sudoku&title_color=0891b2&text_color=ffffff&icon_color=0891b2&bg_color=1c1917&hide_border=true&locale=en"  alt="Sudoku"/></a>
  <a href="https://github.com/Lemkinator/geticon"><img src="https://github-readme-stats.vercel.app/api/pin/?username=Lemkinator&repo=geticon&title_color=0891b2&text_color=ffffff&icon_color=0891b2&bg_color=1c1917&hide_border=true&locale=en"  alt="GetIcon"/></a>
</div>

<br><br>

## Stats

![Alt](https://repobeats.axiom.co/api/embed/61dfa06aca8ff95627bc7f603a2b01e5cab49252.svg "Repobeats analytics image")

<br>

## Code Coverage

[![Coverage Sunburst](https://codecov.io/gh/Lemkinator/common-utils/graphs/sunburst.svg)](https://codecov.io/gh/Lemkinator/common-utils)

<br>

<a href="https://www.star-history.com/?repos=Lemkinator%2Fcommon-utils&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=Lemkinator/common-utils&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=Lemkinator/common-utils&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=Lemkinator/common-utils&type=date&legend=top-left" />
 </picture>
</a>

</div>
