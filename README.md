# Troškovi

A small Android app for two people (Lovel and Marina) to track shared expenses and see who owes
whom. It replaces a Google Sheets workbook used since 2023: add an expense in a couple of taps,
split it 50/50 ("podjela") or pay it entirely for the other person ("zajam"), settle up, and look
at monthly and per-category totals. The UI is in Croatian; amounts are in euros.

Built with Kotlin, Jetpack Compose and Material 3, on Firebase: Google sign-in (Credential Manager)
and Cloud Firestore with offline persistence. Only two allow-listed Google accounts get in. See [`docs/DESIGN.md`](docs/DESIGN.md) for the design and the stage plan.

## Build

Requirements: JDK 21 and the Android SDK (platform 36).

```sh
cp local.properties.example local.properties   # set sdk.dir to your Android SDK
cp <downloaded>/google-services.json app/      # Firebase console > Project settings > Your apps
./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The debug APK ends up in `app/build/outputs/apk/debug/`. On Windows use `.\gradlew.bat`.
CI runs the same command on every push and pull request to `main` (with
`google-services.json.example` copied in as a placeholder; that build compiles but cannot sign in).

## Modules

| Module | What |
|---|---|
| `domain` | Plain Kotlin: expenses, settlements, recurring templates, balance calculation, statistics, CSV import. |
| `app` | The Android app (Compose UI, Koin). |
| `tools` | Python admin scripts: legacy import, Firestore member allow-list, rules deployment. |
| `firebase/rules-tests` | Firestore security rules tests (Node, Firestore emulator). |

### Početna

The home tab shows the balance (always over everything), "Podmiri" and every record grouped by
day. The chips above the list narrow it to one month ("Sve" shows all); the search icon in the
top bar searches category and description, ignoring case and diacritics ("rezije" finds
"Režije"; several words must all match). Both combine, and a line above the list then sums the
shown expenses ("23 troška · 312,40 €"). Edit, delete with undo and settle up work as usual on a
filtered list.

### Recurring templates (Predlošci)

A template is a monthly expense such as rent: name, category, description, amount, payer, split,
day of the month (1–31; in shorter months the last day) and an active switch. Manage them in
**Predlošci** (the repeat icon in the top bar, or "Uredi predloške" on the Dodaj tab); an
expense's "⋮" menu on Početna has **Spremi kao predložak**, which opens a new template prefilled
from it (name = description or category, day = the expense's day).

- **Dospjelo** cards on Početna, above the month chips: one per active template whose day has
  come this month (and, marked "Zaostalo", last month's if it was not posted or skipped). **Dodaj**
  posts the expense dated that day under the id `rec-<templateId>-<yyyy-MM>`, so the two phones
  posting the same month write one document; the card disappears once it exists. **Preskoči**
  skips that month (with undo). A template is never due for a month before it was created.
- **Dodaj tab**: a "Predlošci" row of chips (hidden without templates); a chip fills the whole
  form (dated today) and Spremi saves an ordinary expense.

Picking a category on the add, edit and template forms collapses the chip grid to the chosen chip
(with a chevron), so the description and the save button stay on screen; tap the chip to choose
another.

### Statistika

Statistika has three inner tabs under the top bar: **Brojke** (the figures below), **Trendovi**
and **Navike** (charts, see "Trendovi and Navike"). **Oboje | Lovel | Marina** applies to all
three; the inner tab, the person and the range survive switching tabs and rotating the phone.

The Brojke tab mirrors the legacy PIVOT sheet. Pick **Mjesec**, **Godina** or **Sve** and
step through months or years with the arrows (from the earliest to the latest expense; the
current month is the default). Periods are by the expense date; deleted expenses and settlements
are not counted. It shows the period total, each person's share (half of every shared expense
plus loans taken on their behalf) next to what they actually paid, totals per category (tap one
to list its expenses) and, for a year or all time, totals per month (all time: the last 24
months). The numbers come from `Statistics` and `Trends` in `:domain`, computed off the main
thread.

Transfers (category "Prijenos", money moved between the two of you) are not spending: they are
left out of every statistic and shown as one line under the summary ("Prijenosi (isključeni):
1.000,00 €"); the balance on Početna still counts them. The summary compares the period with the
previous month and the same month last year (Mjesec) or with the previous year (Godina; for the
current year up to today's date, "do danas"), e.g. "+12 % u odnosu na rujan 2026.". Expanding a
category also shows its last 12 months as small bars with the monthly average. Sve adds "Po
godinama": each year's total and monthly average. **Oboje | Lovel | Marina** below the period
switches to one person's payments: the total becomes what that person paid, with the number of
expenses and their share of the couple's spending. The rules are in
[`docs/DESIGN.md`](docs/DESIGN.md), "Statistics rules".

#### Trendovi and Navike

Both ignore the Mjesec/Godina/Sve period and have their own range: **12 mj | 24 mj | Sve**
(default 24 months, ending with the current month). Transfers are left out as in Brojke; with a
person selected every chart covers what that person paid. Tap or drag across any chart to read
the value at a point (shown above the chart, the point gets a marker); by default the latest
month (or day) is shown.

- **Trendovi**: monthly spending as a line with its 3-month rolling average (dashed); a stacked
  column per month for the six largest categories of the range plus "Ostalo"; small multiples
  for the eight largest categories (total, monthly average and a sparkline each); cumulative
  spending per calendar year, January to December, one line per year so years can be compared
  (every year the range touches is drawn in full); and "Tko plaća": what Lovel and Marina paid
  per month (in a person view: what that person paid next to their share, "Udio").
- **Navike**: headline tiles (average per month, median expense, expenses per week, average gap
  between days with spending, the longest stretch without spending and the most expensive day);
  the number of expenses per month and, as a separate chart, the average amount per expense;
  the share of spending per weekday and the average number of expenses per weekday; the average
  spending per day of the month (rent days stand out); a calendar heatmap of daily spending for
  the last 12 months; a histogram of expense amounts (0–10, 10–25, 25–50, 50–100, 100–250,
  250–500, 500+ €) with the median marked; the category mix per quarter as 100 % columns; and
  the ten most frequent descriptions (case and diacritics folded) with count, total and average.

The numbers come from `Analytics` and `Habits` in `:domain`; the charts are plain Compose
`Canvas` composables in `ui/charts/` (no chart library). Colours, chart order and the reasoning
behind them are in [`docs/DESIGN.md`](docs/DESIGN.md), "Analytics rules (Stage 9)".

### Design

A fixed palette instead of Android's wallpaper colours (`ui/theme/Color.kt`, light and dark): warm
off-white `#FAF7F2` / near-black `#17140F`, deep teal `#0F6B6B`, amber and muted rose accents.
The balance card is teal when you are owed money, rose when you owe, neutral when settled.
Type is [Outfit](https://fonts.google.com/specimen/Outfit) for headlines and amounts and
[Inter](https://fonts.google.com/specimen/Inter) for text, both with tabular figures. The TTFs in
`app/src/main/res/font/` are static instances (Outfit 500/600, Inter 400/500 at opsz 14) of the
variable fonts in the [google/fonts](https://github.com/google/fonts) repo, made with fontTools'
`varLib.instancer`; SIL Open Font License, texts in `app/src/main/assets/licenses/`. Category icons
are Material Symbols Rounded (Apache 2.0) vector drawables `res/drawable/ic_cat_*.xml`; icon and
colour per category are set in `ui/common/CategoryVisuals.kt` (unknown categories get a generic
icon). The add form lists the categories you use most first.

## Firebase

Project `troskovi-ml` (see `.firebaserc`), Android app `hr.ml.expensetracker`.

One-time console setup:

1. Authentication > Sign-in method: enable **Google**.
2. Project settings > Your apps > Android app: add the **SHA-1** of every key that signs an APK you
   install (the debug keystore on each dev machine and the release key, see "Releases"). Get it with
   `keytool -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey`
   (release key: `keytool -list -v -keystore private/release.jks -alias troskovi`).
   Then download `google-services.json` again into `app/`.
3. Firestore Database: create the `(default)` database.
4. Project settings > Service accounts > Generate new private key, saved as
   `private/serviceAccountKey.json` (never commit it).

Data layout:

| Path | Fields |
|---|---|
| `config/members` | `emails: [string]` (lowercase), `persons: { "<email>": "LOVEL" \| "MARINA" }` |
| `expenses/{id}` | `date` (`yyyy-MM-dd`), `category`, `description`, `amountCents` (int), `paidBy`, `split`, `createdAt`, `updatedAt` (timestamps), `createdBy`, `deleted` |
| `settlements/{id}` | `date`, `from`, `to`, `amountCents`, `note`, `createdAt`, `updatedAt`, `createdBy`, `deleted` |
| `recurring/{id}` | `name`, `category`, `description`, `amountCents`, `paidBy`, `split`, `dayOfMonth` (int 1–31), `active` (bool), `skippedPeriods` (`["yyyy-MM"]`), `createdAt`, `updatedAt`, `createdBy`, `deleted` |

An expense posted from a template (id `rec-<templateId>-<yyyy-MM>`) also has `templateId` and
`period` (`yyyy-MM`), always both; other expenses (and every legacy row) have neither.

Ids are generated on the client and are the document ids. Records are never deleted, only flagged
`deleted: true`. [`firestore.rules`](firestore.rules) lets only allow-listed accounts (verified
email in `config/members`) read and write, validates every field, pins `createdBy` to the writer's
own person and makes `config/members` read-only for clients.

### Admin tools

```sh
python -m pip install --user -r tools/requirements.txt
python tools/seed_members.py --dry-run   # validate private/members.json and print the document
python tools/seed_members.py             # write config/members
python tools/deploy_rules.py --check     # compile firestore.rules on the server, publish nothing
python tools/deploy_rules.py             # publish firestore.rules (Firebase Rules REST API)
```

`private/members.json` is `{"members": [{"email": "...", "person": "LOVEL"}, {"email": "...",
"person": "MARINA"}]}`. Both scripts authenticate with `private/serviceAccountKey.json` and refuse
to run against a project other than the key's. Seed the members first: until `config/members`
exists the rules deny everyone, including the two members.

#### Legacy history: import, verify, export

Three scripts move the legacy history between `private/` and Firestore. All of them take
`--emulator` (the local Firestore emulator, `FIRESTORE_EMULATOR_HOST`, default `127.0.0.1:8080`;
no key needed) or use the real project with `private/serviceAccountKey.json`. Only the import
writes, and it **refuses to write to the real project unless `--yes-real-project` is passed**.
Recommended order: import, then verify, then export.

```sh
python tools/import_legacy.py --dry-run                 # parse + normalise, print counts and balance
python tools/import_legacy.py --emulator [--limit 20]   # try it on the emulator first
python tools/import_legacy.py --yes-real-project        # the real import
python tools/verify_import.py [--emulator]              # read back, compare with the CSV: PASS/FAIL
python tools/export_sheet.py [--emulator] [--since 2026-10-25] [--xlsx private/Financije_ML_2023.xlsx]
```

- `import_legacy.py` writes one `expenses/legacy-NNNN` document per row of
  `private/legacy_popis.csv` (NNNN = 1-based CSV data row, the ids `LegacyCsvParser` uses), in
  batches of 500, with the categories normalised like the app does, `createdBy` = payer and the
  expense date at 00:00 UTC as `createdAt`/`updatedAt`. Re-running overwrites the same documents,
  so it never duplicates; it stops instead if a stored `legacy-*` document differs from the CSV
  (edited in the app), unless `--overwrite-changed`. It prints the balance read back from
  Firestore: −53469 cents (−534,69 €) for the full 1,069-row history. One legacy row has a price of
  0 €; it is imported as-is, but the rules only accept amounts above 0, so the app cannot edit or
  delete that row (the import prints a warning).
- `verify_import.py` prints the count, balance and per-category totals of everything in
  `expenses/`, then compares the `legacy-*` documents with the CSV (count, balance, per-category
  totals, every field by id). Exit code 1 on FAIL. Records added in the app are listed, not compared.
- `export_sheet.py` writes `private/export_popis.csv` in the POPIS layout (`Datum`, `Kategorija`,
  `Kupnja/trošak`, `Cijena €`, `Platio`, `Podjela/zajam`, `Mjesec`; `;`-separated, decimal comma,
  UTF-8 with BOM) from all non-deleted expenses and settlements. Settlements become `Prijenos` rows
  "Podmirenje: <note>" paid by the sender as `zajam`, which is how the sheet records a transfer.
  `--xlsx` also writes `<name>.updated.xlsx` next to the workbook (the original is not modified):
  the rows that are not already in it (everything except `legacy-*`) are appended to the POPIS
  table, which is extended together with the pivot source and validations that tracked it.
  openpyxl drops the chart styles and the web-extension pane in that copy; the category drop-down
  is restored.

Their unit tests (no Firestore needed; CI job `python-tools`):

```sh
python -m pip install --user -r tools/requirements-dev.txt
python -m pytest tools/tests
```

### Local development with the Firebase emulators

Google sign-in cannot work on an Android emulator without a real account, so debug builds can
run against the local Firebase emulator suite (Auth on port 9099, Firestore on 8080, see
[`firebase.json`](firebase.json)) and sign in with two placeholder accounts. Needs Node 20+,
Java 21 and the `firebase` CLI (`npx firebase-tools@15.33.0` works, or the copy installed in
`firebase/rules-tests` by `npm ci`).

1. Start the emulators from the repo root with the rules loaded. Use the project id the app's
   `google-services.json` names (`troskovi-ml`): the emulators run in single-project mode.
   ```sh
   firebase emulators:start --only auth,firestore --project troskovi-ml
   ```
   Nothing reaches the real project; the emulator data is gone when they stop.
2. Seed the allow-list with the dev accounts (`lovel.dev@example.com`,
   `marina.dev@example.com`); no service account needed:
   ```sh
   python tools/seed_members.py --emulator     # FIRESTORE_EMULATOR_HOST, default 127.0.0.1:8080
   ```
3. Build and install a debug APK with the flag (or put `useFirebaseEmulators=true` in
   `local.properties`):
   ```sh
   ./gradlew :app:installDebug -PuseFirebaseEmulators=true
   ```
   The app then talks to `10.0.2.2` (the host as seen from the Android emulator). When switching
   an installed app between the emulators and the real project, clear its data first
   (`adb shell pm clear hr.ml.expensetracker`): the offline cache would mix the two.
4. On the sign-in screen tap **Dev: prijava kao Lovel** or **Dev: prijava kao Marina**. They sign
   in through the Auth emulator with an unsigned fake Google ID token.

The flag only exists for the `debug` build type: the emulator hosts, the dev buttons and the
cleartext-traffic exception live in `app/src/debug/`; release builds use the no-op
`app/src/release/.../DevEnvironment.kt`.

### Rules tests

Needs Node 20+ and Java 21 (the Firestore emulator). No Firebase login: the tests run against the
`demo-troskovi` emulator project.

```sh
cd firebase/rules-tests
npm ci
npm run test:emulator
```

CI runs them in the `firebase-rules` job.

## Releases

Release APKs are signed with the release key and attached to a GitHub Release; they are installed
by sideloading (no Play Store). The release build type is not minified yet (`isMinifyEnabled` and
`isShrinkResources` are off): enabling R8 is a later step, it needs keep rules for Firebase and
Koin first.

### Signing

`app/build.gradle.kts` takes the release signing config from, in this order:

1. `private/keystore.properties` (gitignored; copy [`keystore.properties.example`](keystore.properties.example)).
   `storeFile` is relative to `app/`, so `../private/release.jks` is `private/release.jks`.
2. The environment variables `TROSKOVI_KEYSTORE_BASE64` (the keystore file, base64; decoded to the
   temp directory at configuration time), `TROSKOVI_KEYSTORE_PASSWORD`, `TROSKOVI_KEY_PASSWORD`
   and `TROSKOVI_KEY_ALIAS`. Setting only some of them fails the build.
3. Neither: `assembleRelease` still works but prints a warning and produces an unsigned
   `app-release-unsigned.apk`. The regular CI workflow never builds the release variant.

The key is `private/release.jks` (alias `troskovi`, RSA 2048). Keep a backup of it and its
passwords outside the repo: an installed app only accepts updates signed with the same key. Its
SHA-1 must be registered in Firebase (see "Firebase", step 2), or Google sign-in fails in release
builds. Release and debug builds are signed with different keys, so switching an installed app
between them needs an uninstall (`adb uninstall hr.ml.expensetracker`, which deletes the app's
local data).

The version comes from Gradle properties: `appVersionName` (default `0.1.0-dev`) and
`appVersionCode` (default `1`). A local signed build (PowerShell needs the quotes):

```powershell
.\gradlew.bat :app:assembleRelease "-PappVersionName=0.1.0-local" "-PappVersionCode=10000"
# -> app\build\outputs\apk\release\app-release.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

### Cutting a release

[`.github/workflows/release.yml`](.github/workflows/release.yml) runs on every pushed tag `v*`. The
tag must be `vX.Y.Z` (Y and Z below 100): it builds with versionName `X.Y.Z` and versionCode
`X*10000 + Y*100 + Z` (v0.1.0 -> 100, v1.2.3 -> 10203), verifies the signature with `apksigner`
and creates the GitHub Release `vX.Y.Z` with `troskovi-vX.Y.Z.apk` attached and auto-generated
notes.

```sh
git tag v0.1.0 && git push --tags
```

Repository secrets (Settings > Secrets and variables > Actions), set once:

| Secret | Value | PowerShell |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | `app/google-services.json`, base64 | `[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path "app\google-services.json"))) \| Set-Clipboard` |
| `TROSKOVI_KEYSTORE_BASE64` | `private/release.jks`, base64 | `[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path "private\release.jks"))) \| Set-Clipboard` |
| `TROSKOVI_KEYSTORE_PASSWORD` | `storePassword` from `private/keystore.properties` | |
| `TROSKOVI_KEY_PASSWORD` | `keyPassword` from `private/keystore.properties` | |
| `TROSKOVI_KEY_ALIAS` | `troskovi` | |

With the GitHub CLI: `gh secret set TROSKOVI_KEY_ALIAS --body troskovi`, and for the files
`[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path "private\release.jks"))) | gh secret set TROSKOVI_KEYSTORE_BASE64`
(on Linux/macOS: `base64 -w0 private/release.jks | gh secret set TROSKOVI_KEYSTORE_BASE64`). The
workflow stops with an error naming any secret that is missing.

## Private data

Everything personal stays out of git. Put it in `private/` (gitignored):

- `private/Financije_ML_2023.xlsx` – the legacy workbook.
- `private/legacy_popis.csv`, `private/legacy_categories.txt` – generated by
  `python tools/export_legacy.py` (install `tools/requirements.txt` first).
- `private/members.json` – the two allow-listed Google accounts (input of `seed_members.py`).
- `private/serviceAccountKey.json` – Firebase Admin key for the scripts in `tools/`.
- `private/release.jks`, `private/keystore.properties` – the release signing key and its
  passwords (see "Releases").

When `private/legacy_popis.csv` exists, `:domain:test` also runs a golden test against the full
history; otherwise that test is skipped. `google-services.json` (Firebase) and signing keystores are
gitignored too; `google-services.json.example` shows the expected shape.
