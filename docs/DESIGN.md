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
| Charts (Stage 9) | Compose `Canvas` composables in `ui/charts/`, no chart library. Vico was the preferred option, but its newest 2.x (2.5.2) needs compileSdk 37 (core-ktx 1.19) and fails `checkDebugAarMetadata`; 2.5.1 builds but scales column thickness with zoom, has no stacked-segment gaps or end labels and pulls in appcompat. Charts take plain data (domain reports or small chart data classes), never a ViewModel. |

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

// Stage 10
Expense(..., templateId: String? = null, period: String? = null)   // both or neither, "yyyy-MM"

RecurringTemplate(id, name, category, description, amountCents, paidBy, split,
                  dayOfMonth: Int /* 1..31 */, active: Boolean,
                  skippedPeriods: Set<String /* "yyyy-MM" */>,
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

## Analytics rules (Stage 9)

Statistika has three inner tabs: **Brojke** (Stage 8, unchanged), **Trendovi** and **Navike**.
All numbers are computed in `:domain` (`Analytics`, `Habits`), pure and unit tested, with the
Stage 8 filters: no deleted expenses, no transfers ("Prijenos"), with a person only what that
person paid. The person selection is shared by all three inner tabs; the inner tab, the person
and the range live in `StatisticsViewModel` (one repository listener; Trendovi and Navike are
computed on `Dispatchers.Default` only when the expenses, the range or the person change).

- **Range** ("12 mj | 24 mj | Sve", default 24 mj; the Mjesec/Godina/Sve period does not apply):
  calendar months ending with the later of today's month and the month of the latest non-deleted
  expense; 12/24 months back but not before the month of the earliest non-deleted expense (Sve
  starts there). Transfers count for the range, so it does not change with the person.
- **Money** is cents; every average is rounded half-even on cents. Ratios that are not money
  (expenses per week, days between) are shown with one decimal.
- **Mjesečna potrošnja**: spending per month (zero-filled) and a 3-month trailing average: the
  month and the two before it, using months before the range when they exist, but no month
  before the first month with spending (so the first months average over 1 or 2 months).
- **Stacked categories** (per month in Trendovi, per quarter as 100 % columns in Navike): the six
  largest categories of the range by total (ties by name) plus "Ostalo" for everything else when
  that is not zero. The real category "Ostalo" never takes one of the six places; it is part of
  the folded "Ostalo". Quarters are those overlapping the range (the first and last can be
  partial).
- **Kategorije pojedinačno**: the eight largest categories (the real "Ostalo" included), each with
  its total and monthly average over the range's months from the first month with spending on.
- **Kumulativno po godinama**: every calendar year the range touches (at most the newest six), in
  full: January to December, the range's last year to its last month; each point is the year's
  spending up to the end of that month.
- **Tko plaća**: paid per person and month over both people's spending, and each person's share
  per month (the Stage 8 share rule applied per month: HALF sums halved half-even, Lovel gets the
  rounded half). Oboje shows both people's payments; a person view shows that person's payments
  and their share ("Udio", dashed).
- **Observed days** (Navike): from the range's first day (or the first spending day, when later)
  to its last day (or the later of today and the last spending day, when earlier).
- **Tiles**: average per month (as above); median expense (the middle amount, or the half-even
  mean of the two middle ones); expenses per week = count × 7 / observed days; average gap =
  (last − first spending day) / (days with spending − 1); the longest run of days without
  spending between two spending days (with its dates); the most expensive day (the earliest of
  equal days).
- **Koliko često**: expenses per month and, as a separate chart (never a second axis), the
  average amount per expense (months without expenses have no point).
- **Dani u tjednu / Dan u mjesecu**: per weekday the share of spending and the expenses divided
  by how often that weekday occurs in the observed days; per day of the month the spending
  divided by how often that day occurs (the 31st only in long months).
- **Kalendar potrošnje**: the last 12 months of the observed days (from the day after the same
  date a year earlier); a day's level is 0 without spending, otherwise 1–5 by four thresholds:
  the 20/40/60/80th percentiles (nearest rank) of the days with spending, rounded half-even to
  whole euros.
- **Raspodjela iznosa**: fixed half-open buckets 0–10, 10–25, 25–50, 50–100, 100–250, 250–500 and
  500+ €, with the median marked.
- **Najčešći troškovi**: the ten most frequent non-blank descriptions, grouped by
  `SearchText.fold` with inner whitespace collapsed; shown with the most used spelling and
  category (ties: the most recent), count, total and average; ordered by count, total, label.

**Chart conventions** (the dataviz rules): one y-axis per chart from 0 with clean ticks, solid
hairline grid; lines 2 dp, a derived line (rolling average, share) 1.5 dp dashed; columns at most
24 dp with at least 2 dp between them, stacked segments separated by a 2 dp surface gap, the top
rounded; a 10 dp marker with a 2 dp surface ring on the selected point; a legend for two or more
series (category legends with the icon); direct labels only at line ends, dropped when they would
collide; text always in the text colours. Every chart has a tap/drag read-out above it (values
first). No pie charts.

**Colours**: categorical colour follows the category (`CategoryVisuals`). Multi-series category
charts draw the categories in the fixed `CategoryVisuals.CHART_ORDER` (stack bottom to top and
legend order; "Ostalo" grey always last), an order chosen with the dataviz palette validator. In
Stage 9 the category hues were re-stepped within their hue families so that the stacks the real
history produces (every range × person) pass the validator's adjacent-pair gates in both themes
(OKLab ΔE ≥ 8 under protan/deutan simulation, ≥ 15 for normal vision, lightness band, chroma
floor, 3:1 against the card); the dark set now sits in the dark lightness band (OKLCH L
0.48–0.67) instead of the former pastels. Rare categories (Zdravlje, Poklon, Usluge, …) that have
never been in a top six rely on the 2 dp gaps, the legend and the read-out. Single-series charts
use the primary teal. Years (cumulative chart) take slot `floorMod(2026 − year, 6)` of a
validated six-hue order, so a year keeps its colour; "Tko plaća" uses a validated teal/rose pair
for Lovel/Marina. The heatmap uses a one-hue teal ramp of five steps (light: light to dark; dark
theme: dim to bright), validated as an ordinal ramp, with a neutral cell for days without spending.

## Recurring templates (Stage 10)

Monthly only. `RecurringSchedule` in `:domain` (pure, tested) decides what is due:

- For `today` a template is due for a period P (a calendar month) when its due date
  `P.atDay(min(dayOfMonth, P.lengthOfMonth()))` is not after `today` (31 → 28/29 February, 30
  April), P is not in `skippedPeriods`, and no non-deleted expense has `templateId == id &&
  period == P`. Only the current month and the previous one (overdue, "Zaostalo") are
  considered. Inactive and deleted templates are never due.
- Decision (Stage 10): a template is never due for a month before the month it was created in
  (in the device time zone). Without it, every new template would immediately show last month
  as overdue although that rent was usually entered by hand already.
- Posting ("Dodaj" on a Dospjelo card) writes the template's fields on the due date with
  `createdBy` = the signed-in person, `templateId` and `period`, under the deterministic id
  `rec-<templateId>-<yyyy-MM>`. Both phones posting the same month produce the same document.
  `ExpenseRepository.addOrRestore` reads the document first (server, or the cache offline) and,
  if it exists (posted meanwhile, or posted and then deleted), keeps its stored `createdBy` and
  `createdAt`, which the rules never let change; a concurrent second write that still carries
  other authorship is rejected by the rules and rolled back harmlessly.
- "Preskoči" adds P to `skippedPeriods` (undo removes it again, applied to the newest stored
  version of the template).
- A template chip on the Dodaj tab only prefills the form (dated today); that expense is an
  ordinary one without `templateId`. Statistics and the export treat posted expenses like any
  other expense.

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
   Stage 9 (see "Analytics rules"): inner tabs Brojke (all of the above) | Trendovi (monthly line
   with rolling average, stacked categories per month, small multiples, cumulative years, who
   pays) | Navike (tiles, frequency, weekdays, day of month, calendar heatmap, amount histogram,
   category mix per quarter, most frequent descriptions), with their own range 12 mj | 24 mj | Sve.
   Početna's rows keep the description and the payer line to one line each (ellipsis).
4. **Predlošci** (Stage 10; full screen over the tabs like the expense editor, opened from the top
   bar's repeat icon, "Uredi predloške" on the Dodaj tab, or "Spremi kao predložak" in an
   expense's menu): templates with an active switch; add/edit form (name, category chips in the
   usage order, description, amount, payer, split, day of month 1–31, active); soft delete with
   confirmation and undo. Početna shows a "Dospjelo" card per due item above the month chips;
   the Dodaj tab shows a "Predlošci" chip row above the amount (hidden without templates).
   On the add, edit and template forms the category grid collapses to the selected chip once a
   category is chosen (tap it to reopen the grid); an empty new form starts with the grid open,
   a prefilled one (edit, template, "Spremi kao predložak") collapsed. The state lives in the
   ViewModel.

Deferred (v2 backlog): home-screen quick-add widget, receipt photos,
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
- Stage 10: `recurring/{id}` (template fields as in the model, `dayOfMonth` integer,
  `skippedPeriods` a sorted list of `yyyy-MM` strings). Expenses may carry `templateId`
  (string, 1–64 characters) and `period` (`yyyy-MM`, month 01–12), only together; the rules'
  field check accepts exactly the ten base fields or the ten plus both. `recurring` has the same
  member-only access, strict validation (name 1–100, description ≤ 1000, `dayOfMonth` int
  1–31, `active` bool, every skipped period `yyyy-MM`, at most 600), immutable
  `createdBy`/`createdAt` and no hard delete. Rules cannot loop over a list, so
  `skippedPeriods` is joined with commas, matched as a whole and its length checked against the
  element count. `tools/firebase_common.py` mirrors the expense check.
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
