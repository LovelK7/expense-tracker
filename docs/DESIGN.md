# Expense tracker – design decisions (MVP)

Status: locked on 2026-10-24 after the kickoff brainstorm. Change only via a new decision noted here.

## Purpose

Two-person shared-expense tracker (Lovel and Marina) replacing the Google Drive workbook
`!Financije_ML_2023.xlsx` used since January 2023. Splitwise-like, but minimal: no groups, no
multi-currency, no friends list. Shared expenses only; private spending is out of scope.

## Decisions

| Topic | Decision |
|---|---|
| Backend | Firebase Auth (Google sign-in) + Cloud Firestore. Firestore offline persistence instead of Room for the MVP. |
| Access | Firestore security rules allow exactly two Google accounts. The allow-listed emails live in `private/members.json` (gitignored) and are deployed to Firestore, never committed. Lovel owns the Firebase project. Android package `hr.ml.expensetracker`. |
| UI language | Croatian. Code, identifiers, commits and docs in English. Category names stay Croatian. |
| Platform | Kotlin, Jetpack Compose, Material 3. compileSdk/targetSdk 36 (Android 16). minSdk 28. |
| Currency | EUR only. Amounts stored as integer cents (`Long`). |
| Legacy sheet | Kept alive for now: the app can export rows in the sheet's column layout. Retire later. |
| History | All legacy rows are imported as-is. "Prijenos" rows keep category "Prijenos" and their split type; they are **not** reinterpreted as settlements. |
| Settlements | New explicit record type from day one (`Settlement`), used instead of ad hoc "Prijenos" rows going forward. |
| Repo | Public GitHub repo. Secrets and private data gitignored (`private/`, `google-services.json`, `local.properties`, `*.jks`, `*.keystore`). Example files committed instead. |
| Distribution | Signed APK attached to GitHub Releases, sideloaded. No Play Store for the MVP. |
| Visual style (Stage 6) | Own palette, no wallpaper-based dynamic colour: warm off-white / warm near-black, deep teal primary, amber secondary, muted rose tertiary. Outfit (headlines, amounts) and Inter (body), bundled, tabular figures. One icon and colour per category (`ui/common/CategoryVisuals`). The balance card is teal when the signed-in person is owed, rose when they owe, neutral when settled. |

## Domain model

```
enum Person { LOVEL, MARINA }

enum SplitType {
  HALF,            // sheet: "podjela" – shared 50/50, payer is owed half
  FULL_FOR_OTHER   // sheet: "zajam"   – payer paid 100% on behalf of the other person
}

Expense(
  id: String, date: LocalDate, category: String, description: String,
  amountCents: Long, paidBy: Person, split: SplitType,
  createdAt, updatedAt: Instant, createdBy: Person, deleted: Boolean = false)

Settlement(id, date: LocalDate, from: Person, to: Person, amountCents: Long, note: String,
           createdAt, updatedAt, createdBy, deleted)
```

Categories are a controlled list (seeded from the sheet's "Kategorije" tab plus every category
actually used in the "POPIS" tab). Normalisation: trim, case-insensitive match ("hrana" → "Hrana");
the legacy list entry "Odjeća" maps to the used name "Odjeća i oprema".
The add form orders the category chips by real usage (Stage 6, `Categories.orderByUsage`): the
number of non-deleted expenses per category, all time, most used first, recomputed live from the
repository. Ties (and categories nobody has used) keep the static order `Categories.FOR_PICKER`
(the most used legacy categories at import time: Hrana, Prijevoz, Hrana van, Režije, Potrepštine,
Putovanja, MPO, Adaptacija; then the rest alphabetically), which is also the order before any data
has loaded. With the legacy history the first chips are Hrana, Prijevoz, Putovanja, Režije, Hrana
van. "Prijenos" is omitted for new expenses (transfers are settlements now); editing a legacy
"Prijenos" row still shows it, first.

## Balance rules (the "brain")

Define `balance = amount Lovel owes Marina`, in cents. Positive: Lovel pays Marina. Negative: Marina pays Lovel.

```
balance =   Σ HALF           paidBy MARINA / 2
          − Σ HALF           paidBy LOVEL  / 2
          + Σ FULL_FOR_OTHER paidBy MARINA
          − Σ FULL_FOR_OTHER paidBy LOVEL
          + Σ Settlement from MARINA to LOVEL
          − Σ Settlement from LOVEL to MARINA
```

Deleted records are excluded. Rounding: halves use banker's rounding on cents only at the final
step (compute the HALF sums first, then divide once).

This is exactly the legacy "SALDO" sheet. Golden value: with every POPIS row as of 2026-10-24
(1,069 rows) imported with whole-cent amounts, `balance` must equal **−53469 cents**.
The sheet itself shows −534.70 € because it sums raw floats: about 50 legacy rows carry sub-cent
prices (e.g. fuel at 17.787 €). Rounding each row to cents on import is the intended behaviour; the
1-cent difference is accepted and documented, not patched with an adjustment row.

Statistics (the "dashboard"): totals per category, totals per calendar month, each person's half,
for a selected month or year, excluding deleted rows. Mirrors the legacy "PIVOT" sheet.

## Statistics rules (Stage 8)

All in `:domain` (`Statistics`, `Trends`, `Percent`), pure and unit tested; the Statistika tab only
formats them.

- **Transfers are not spending.** An expense in category `Categories.TRANSFER` ("Prijenos") is money
  moved between the two people (the legacy way of settling up), not consumption. It is left out of
  every spending figure: totals, expense counts, per-category, per-person share and paid, monthly
  and yearly totals, comparisons and trends. It still counts in the balance (`BalanceCalculator`
  is unchanged) and still appears on Početna. Statistika shows one line under the summary when
  the period has any: "Prijenosi (isključeni): 1.000,00 €". Settlements are not spending either.
- **Stanarina** (rent) is an ordinary category.
- **Person view** ("Oboje | Lovel | Marina"): with a person selected every figure covers only the
  expenses that person paid (`paidBy`). The summary shows "Plaćeno · <name>", the count and the
  person's paid total as a whole percentage of both people's spending in the period. The share
  table ("Udio") and "Udio po osobi" are hidden. The selection lives in the ViewModel.
- **Percentages** shown as whole numbers are rounded half-even: change = (current − reference) ×
  100 / reference, share = part × 100 / whole. Display "12 %" with a non-breaking space; a change
  carries a sign: "+12 %", "−5 %" (real minus sign U+2212), "0 %". Category shares keep one
  decimal ("34,5 %").
- **Comparisons** in the summary card. Mjesec: against the previous month and the same month a
  year earlier ("+12 % u odnosu na rujan 2026."). Godina: against the previous year; for the
  current year both ranges run from 1 January to today's date ("… u odnosu na 2025. (do danas)",
  29 February maps to the 28th). A comparison is hidden when the reference period has no spending
  (`PeriodComparison.changePercent == null`). Sve has none. Months are compared in full, also the
  current month.
- **Category trend** (an expanded category): the category's spending in the 12 months ending with
  the selected period's last month, clipped to the later of today's month and the latest expense
  (Godina 2026 ends at the current month; Sve ends there too). Months of the selected period are
  drawn stronger. "Prosjek: X €/mj" is the window's total divided by the window's months from the
  first month with any spending (of the selected person) on, half-even; the share is the
  category's part of the period total.
- **Po godinama** (Sve): one row per calendar year, newest first, every year from the first to the
  last with spending, with its total and monthly average. The average divides by the year's
  months from the first month with any spending to the current month (or the latest expense when
  that is later), clipped to the year: 2023–2025 by 12, 2026 by 10 (on 24 October 2026). The
  24-month "Po mjesecima" list stays below it.
- The Sve range runs from the earliest to the latest non-deleted expense of either person,
  transfers included, so it does not change with the person selection.

## Screens (MVP)

1. **Dodaj trošak** (add expense): amount, category chips, optional description, payer toggle
   (Lovel/Marina), split toggle (Podjela/Zajam), date defaulting to today. Target: two taps for a
   common grocery entry.
2. **Početna** (home): balance card ("Marina duguje Lovelu 534,70 €"), "Podmiri" (settle up)
   button, recent expenses list with edit and soft delete.
   - Month filter and search (Stage 7): chips above the list ("Sve", then every month with a
     record, newest first) and a search field behind the top-bar search icon (category and
     description, case- and diacritics-insensitive, every word must match; `RecordFilter` in
     `:domain`). They combine; while one is active a line shows the shown expenses' count and sum
     ("23 troška · 312,40 €", settlements listed but not summed) or "Nema rezultata". The balance
     card always stays global. The selection lives in the ViewModel (survives tab switches and
     rotation, not a restart).
3. **Statistika** (stats): month/year picker, per-category totals, monthly trend, per-person half.
   Stage 8 (see "Statistics rules"): transfers excluded, period comparisons, a 12-month trend per
   expanded category, "Po godinama" in Sve, and a person view (Oboje | Lovel | Marina).

Deferred (v2 backlog): recurring rent template, home-screen quick-add widget, receipt photos,
budgets, custom split ratios, push notifications, web dashboard.

## Architecture

- `:domain` – pure Kotlin/JVM module, no Android dependencies. Models, `BalanceCalculator`,
  `Statistics`, category normalisation, legacy CSV parser. Fully unit tested.
- `:app` – Android app: Compose UI, ViewModels (Kotlin Flows), DI (Koin), Firebase data layer
  (added in Stage 2). Repository interfaces live in `:domain`, Firestore implementations in `:app`
  (or a `:data` module if it grows).
- `tools/` – Python admin scripts (import from the legacy xlsx, export/backup). Never commit output.

## Firestore layout (Stage 2)

- `config/members`: `{ emails: [lowercase email], persons: { "<email>": "LOVEL" | "MARINA" } }`.
  Written only by `tools/seed_members.py` (Admin SDK); readable by members. Dots in the email map
  keys are fine (plain map keys, never used as field paths). The signed-in email maps to the
  `Person` that becomes `createdBy` on new records.
- `expenses/{id}`, `settlements/{id}`: client-generated ids = document ids; fields mirror the
  domain models (`date` as `yyyy-MM-dd` string, `amountCents` integer, Firestore timestamps,
  enum names as strings, `deleted` boolean). No hard deletes.
- `firestore.rules`: members only (verified token email in `config/members.emails`); strict field
  validation; `createdBy` = writer's person on create; `createdBy`/`createdAt` immutable on update.

## Private data and secrets

`private/` holds the legacy workbook, generated CSVs and anything personal. It is gitignored.
Tests that need the real history read `private/legacy_popis.csv` and are skipped when it is absent;
committed tests use small synthetic fixtures.

## Stages

| Stage | Deliverable | Validation |
|---|---|---|
| 0 | Gradle/Compose scaffold, modules, CI, CLAUDE.md, .gitignore, README | `assembleDebug` passes locally and in CI; app launches on the emulator |
| 1 | `:domain` models + balance engine + stats + legacy import tool | Unit tests green; golden test reproduces −534.70 € |
| 2 | Firebase project wiring, Google sign-in, Firestore repo, security rules | Rules tests on the Firebase emulator; two allow-listed accounts only |
| 3 | Add expense + home screen | Compose UI tests; manual emulator run |
| 4 | Stats screen | Unit + UI tests |
| 5 | Historical import into Firestore, legacy-layout export, signed release v0.1 | Balance in app equals the sheet; APK installed on both phones |

Each stage is a reviewed commit/PR. Fable orchestrates and reviews; Opus workers implement.
