# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

"Vital Oceans" (`app_name`) — a native Android app in Kotlin + Jetpack Compose (Material 3), developed entirely from the terminal without Android Studio. Package/namespace is still the scaffold default `com.example.miprimeraapp`, so file paths say `miprimeraapp` while the product is Vital Oceans.

Not a git repository. `README.md` is the user-facing setup guide (in Spanish) and is kept current by hand — when build config, resources, or the theme change in a way that affects setup, update it too.

## Commands

All from the project root; the Gradle Wrapper supplies Gradle 8.13 (JDK 17 required, `local.properties` must point at the Android SDK).

```powershell
.\gradlew.bat assembleDebug     # APK -> app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat lintDebug         # report -> app/build/reports/lint-results-debug.html
.\gradlew.bat installDebug      # install onto the connected device
adb shell am start -n com.example.miprimeraapp/.MainActivity
```

There are no unit or instrumentation tests and no test source sets; `assembleDebug` + `lintDebug` is the full verification loop. Adding tests means creating `app/src/test/` or `app/src/androidTest/` and the corresponding dependencies first.

`build-check.log` is a captured build transcript (UTF-16, from a Windows console redirect) — a scratch artifact, not an input.

## Architecture

Single-module (`:app`), single-Activity, no navigation library, no ViewModels, no persistence, no network. State lives in composables and survives activity recreation via `rememberSaveable`.

- [MainActivity.kt](app/src/main/java/com/example/miprimeraapp/MainActivity.kt) — the Activity plus `LoaderScreen`. A `ready: Boolean` in `rememberSaveable` drives a `Crossfade` from the loader to `StartScreen`. `enableEdgeToEdge` is called twice: once in `onCreate` (dark bars over the blue loader) and again from a `LaunchedEffect(ready)` (light bars over the pale start screen) — any new top-level screen with a different background must extend that effect.
- `LoaderScreen` animates a single `Animatable` 0→1 over `LoaderDurationMillis` (6000 ms). That one fraction derives both the percentage text and which of the six `LoaderImages` is showing plus its zoom-in scale; the previous image stays painted underneath so the next grows over it. The progress is a demo, unrelated to real work. `savedProgress` is mirrored into `rememberSaveable` so rotation resumes rather than restarts.
- [StartScreen.kt](app/src/main/java/com/example/miprimeraapp/StartScreen.kt) — the onboarding cover. Two `rememberSaveable` booleans feed one shared `AlertDialog`.
- [ui/theme/Theme.kt](app/src/main/java/com/example/miprimeraapp/ui/theme/Theme.kt) — **all** theme declarations live here: `ThemePalette`, `ThemeTypography`, the `MiPrimeraAppTheme` wrapper, and `AppBackground`. `Color.kt` and `Type.kt` are deliberately empty stubs; the split caused cross-file resolution failures in the terminal/VS Code Kotlin tooling. Do not move declarations back into them.

### Theming conventions

- The color scheme is a hand-populated `lightColorScheme` used unconditionally — there is no dark scheme and no dynamic color. System dark mode does not change the app.
- `ThemeTypography` copies Material's default sizes/line heights/tracking and only swaps the font family, so metrics stay Material-correct.
- Despite the README's "custom fonts" section, `HeadingFontFamily`/`BodyFontFamily` in the theme are currently `FontFamily.SansSerif`. The real Bowlby One / Ones variable fonts (`res/font/`) are loaded locally inside `StartScreen.kt` as `CoverHeading`/`CoverBody`, and `CoverBody` falls back to SansSerif below API 26 because `FontVariation` needs Android 8+. `StartScreen` also uses hardcoded `Color(0xFF...)` values rather than the color scheme.
- `AppBackground` expects any `Scaffold` inside it to set `containerColor = Color.Transparent`.

## Resources

- Strings are Spanish and always go through `strings.xml` / `stringResource` (except the few literals already inline in `StartScreen`). Code comments in this repo are mostly Spanish — match the surrounding file.
- Resource names: lowercase, digits, underscores.
- Raster images go in `res/drawable-nodpi/` so Android does not rescale them by density; size them in Compose. Vectors go in `res/drawable/` as VectorDrawable XML — `painterResource` cannot read `.svg`, so SVGs must be converted, not renamed.
- App icon is `@drawable/mi_icono` (a 512×512 PNG), not an adaptive icon; `ic_launcher.xml` remains as the earlier placeholder.
- `minSdk = 23`, so guard anything newer with `Build.VERSION.SDK_INT` as `CoverBody` does.

## Encoding

Windows-heavy setup: `.vscode/settings.json` forces UTF-8, `gradle.properties` sets `-Dfile.encoding=UTF-8`, and some resource files carry a BOM. Keep new files UTF-8 and do not let accented Spanish text get mangled — the README already contains mojibake from an earlier encoding slip.
