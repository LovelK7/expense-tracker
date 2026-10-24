# CLAUDE.md

Two-person shared-expense tracker for Android ("Troškovi"). The locked design, domain rules and
stage plan live in [`docs/DESIGN.md`](docs/DESIGN.md): read it before changing behaviour.

## Commands (Windows / PowerShell; use `./gradlew` elsewhere)

```powershell
.\gradlew.bat :domain:test                 # pure-Kotlin unit tests (JUnit 5)
.\gradlew.bat :app:testDebugUnitTest       # Android JVM unit tests (JUnit 4)
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug           # -> app\build\outputs\apk\debug\app-debug.apk
# Everything CI runs:
.\gradlew.bat :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Emulator (AVD `Pixel_9_new`, API 35):

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
Start-Process "$sdk\emulator\emulator.exe" -ArgumentList '-avd','Pixel_9_new','-no-window','-no-audio','-no-boot-anim'
& "$sdk\platform-tools\adb.exe" wait-for-device      # then poll: adb shell getprop sys.boot_completed -> 1
& "$sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$sdk\platform-tools\adb.exe" shell am start -n hr.ml.expensetracker/.MainActivity
```

## Layout

- `:domain` – pure Kotlin/JVM, no Android deps: models, balance engine, statistics (Stage 1).
- `:app` – Compose + Material 3 UI, ViewModels exposing `StateFlow`, Koin DI (`di/AppModule.kt`).
  Package `hr.ml.expensetracker`.
- `tools/` – Python admin scripts (Stage 1). Their output goes to `private/`, never into git.
- Versions: `gradle/libs.versions.toml`. compileSdk is locked at 36, which pins some AndroidX
  libraries one release behind (see the comment there).

## Conventions

- UI text is Croatian and lives in `app/src/main/res/values/strings.xml`; code, identifiers,
  commits and docs are English. Category names stay Croatian.
- Money is `Long` cents everywhere; format only for display (`Money.format`, "534,70 €").
- Balance sign: positive = Lovel owes Marina. HALF sums are totalled first and halved once
  (half-even). Don't change the formula without updating `docs/DESIGN.md`.
- New domain logic gets JUnit 5 tests in `:domain` with synthetic fixtures. Tests using real data
  read from `private/` and must skip (JUnit `Assumptions`) when it is absent.
- No secrets or personal data in git: `private/`, `google-services.json`, `local.properties`,
  keystores are gitignored; commit `*.example` files instead.
- Each stage is one reviewed commit on `main`; CI (`.github/workflows/android.yml`) must be green.
