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
| `domain` | Plain Kotlin: expenses, settlements, balance calculation, statistics, CSV import. |
| `app` | The Android app (Compose UI, Koin). |
| `tools` | Python admin scripts: legacy import, Firestore member allow-list, rules deployment. |
| `firebase/rules-tests` | Firestore security rules tests (Node, Firestore emulator). |

## Firebase

Project `troskovi-ml` (see `.firebaserc`), Android app `hr.ml.expensetracker`.

One-time console setup:

1. Authentication > Sign-in method: enable **Google**.
2. Project settings > Your apps > Android app: add the **SHA-1** of every key that signs an APK you
   install (the debug keystore on each dev machine, later the release key). Get it with
   `keytool -list -v -keystore ~/.android/debug.keystore -storepass android -alias androiddebugkey`.
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

## Private data

Everything personal stays out of git. Put it in `private/` (gitignored):

- `private/Financije_ML_2023.xlsx` – the legacy workbook.
- `private/legacy_popis.csv`, `private/legacy_categories.txt` – generated by
  `python tools/export_legacy.py` (install `tools/requirements.txt` first).
- `private/members.json` – the two allow-listed Google accounts (input of `seed_members.py`).
- `private/serviceAccountKey.json` – Firebase Admin key for the scripts in `tools/`.

When `private/legacy_popis.csv` exists, `:domain:test` also runs a golden test against the full
history; otherwise that test is skipped. `google-services.json` (Firebase) and signing keystores are
gitignored too; `google-services.json.example` shows the expected shape.
