"""Shared helpers for the Firebase admin scripts in tools/.

The scripts authenticate with a service-account key at private/serviceAccountKey.json (gitignored).
Create it in the Firebase console: Project settings > Service accounts > Generate new private key.

Besides the key/project helpers (seed_members.py, deploy_rules.py) this module holds what the data
scripts (import_legacy.py, verify_import.py, export_sheet.py) share: the Firestore target (real
project or emulator) and pure re-implementations of the app's data rules (document encoding,
category normalisation, balance). Firebase packages are imported lazily, so the pure parts work
(and are unit tested) without them.
"""

from __future__ import annotations

import datetime as dt
import json
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_KEY = REPO_ROOT / "private" / "serviceAccountKey.json"
FIREBASERC = REPO_ROOT / ".firebaserc"


def load_key(path: Path) -> dict:
    """Returns the parsed service-account key, or exits with instructions when it is missing."""
    if not path.is_file():
        sys.exit(
            f"Service-account key not found: {path}\n"
            "Create one in the Firebase console (Project settings > Service accounts >\n"
            "Generate new private key) and save it at that path. private/ is gitignored;\n"
            "never commit the key."
        )
    try:
        key = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as e:
        sys.exit(f"Cannot read service-account key {path}: {e}")
    if key.get("type") != "service_account" or "private_key" not in key:
        sys.exit(f"{path} is not a service-account key (expected \"type\": \"service_account\").")
    return key


def resolve_project(key: dict, override: str | None) -> str:
    """The target project: --project, else .firebaserc's default; must match the key's project."""
    project = override
    if project is None and FIREBASERC.is_file():
        project = json.loads(FIREBASERC.read_text(encoding="utf-8")).get("projects", {}).get("default")
    project = project or key.get("project_id")
    if not project:
        sys.exit("No Firebase project id: pass --project or set a default in .firebaserc.")
    if key.get("project_id") and key["project_id"] != project:
        sys.exit(
            f"The service-account key belongs to project {key['project_id']!r}, "
            f"but the target is {project!r}. Use the key of the target project."
        )
    return project


# ---------------------------------------------------------------------------------------------
# Firestore targets for the data scripts.
# ---------------------------------------------------------------------------------------------

DEFAULT_EMULATOR_HOST = "127.0.0.1:8080"


def emulator_project(override: str | None) -> str:
    """--project, else .firebaserc's default (the project id the app's emulator build uses)."""
    if override:
        return override
    if FIREBASERC.is_file():
        project = json.loads(FIREBASERC.read_text(encoding="utf-8")).get("projects", {}).get("default")
        if project:
            return project
    sys.exit("No Firebase project id: pass --project or set a default in .firebaserc.")


def add_target_arguments(parser, *, writes: bool) -> None:
    """Adds --emulator, --project, --key and, for scripts that write, --yes-real-project."""
    parser.add_argument("--emulator", action="store_true",
                        help="use the local Firestore emulator (FIRESTORE_EMULATOR_HOST, "
                             f"default {DEFAULT_EMULATOR_HOST}); no service account needed")
    parser.add_argument("--project", help="Firebase project id (default: .firebaserc)")
    parser.add_argument("--key", type=Path, default=DEFAULT_KEY,
                        help="service-account key JSON (real project only)")
    if writes:
        parser.add_argument("--yes-real-project", action="store_true",
                            help="required to write to the real Firebase project")


class FirestoreTarget:
    """An open Firestore client plus a description of where it points. Use as a context manager.

    With --emulator it talks to the emulator using the Python client's "owner" credentials, which
    bypass the security rules just like the Admin SDK does on the real project. For the real
    project it authenticates with the service-account key; with writes=True it refuses to run
    unless --yes-real-project was passed.
    """

    def __init__(self, args, *, writes: bool):
        self.args = args
        self.writes = writes
        self.is_emulator = bool(args.emulator)
        self.client = None
        self.label = ""
        self._app = None

    def __enter__(self) -> "FirestoreTarget":
        if self.is_emulator:
            host = os.environ.get("FIRESTORE_EMULATOR_HOST") or DEFAULT_EMULATOR_HOST
            os.environ["FIRESTORE_EMULATOR_HOST"] = host
            project = emulator_project(self.args.project)
            from google.auth.credentials import AnonymousCredentials
            from google.cloud import firestore

            self.client = firestore.Client(project=project, credentials=AnonymousCredentials())
            self.label = f"the Firestore emulator at {host} (project {project})"
            return self

        if os.environ.get("FIRESTORE_EMULATOR_HOST"):
            sys.exit("FIRESTORE_EMULATOR_HOST is set but --emulator was not passed. "
                     "Pass --emulator, or unset the variable to use the real project.")
        if self.writes and not getattr(self.args, "yes_real_project", False):
            sys.exit("Refusing to write to the real Firebase project without --yes-real-project.\n"
                     "Use --emulator to test against the local emulator, or --dry-run to only "
                     "parse and print.")
        key = load_key(self.args.key)
        project = resolve_project(key, self.args.project)
        import firebase_admin
        from firebase_admin import credentials
        from firebase_admin import firestore as admin_firestore

        self._app = firebase_admin.initialize_app(
            credentials.Certificate(key), {"projectId": project}, name=f"tools-{id(self)}")
        self.client = admin_firestore.client(self._app)
        self.label = f"the real project {project}"
        return self

    def __exit__(self, *exc) -> None:
        if self._app is not None:
            import firebase_admin

            firebase_admin.delete_app(self._app)
        elif self.client is not None:
            self.client.close()


def read_collection(client, name: str) -> dict[str, dict]:
    """All documents of a collection as {id: fields}."""
    return {snap.id: snap.to_dict() for snap in client.collection(name).stream()}


def write_documents(client, collection: str, documents: dict[str, dict], batch_size: int = 500) -> int:
    """set() (full overwrite) every document in batched writes of at most batch_size; returns the
    number of batches committed."""
    ids = list(documents)
    col = client.collection(collection)
    batches = 0
    for start in range(0, len(ids), batch_size):
        batch = client.batch()
        for doc_id in ids[start:start + batch_size]:
            batch.set(col.document(doc_id), documents[doc_id])
        batch.commit()
        batches += 1
    return batches


def get_documents(client, collection: str, ids) -> dict[str, dict | None]:
    """{id: fields, or None when the document does not exist}, fetched in chunks."""
    col = client.collection(collection)
    ids = list(ids)
    result: dict[str, dict | None] = {}
    for start in range(0, len(ids), 300):
        for snap in client.get_all([col.document(i) for i in ids[start:start + 300]]):
            result[snap.id] = snap.to_dict() if snap.exists else None
    return result


# ---------------------------------------------------------------------------------------------
# Pure data rules shared with the app. Each mirrors a Kotlin source named in its docstring;
# keep them in sync (tools/tests/ covers them).
# ---------------------------------------------------------------------------------------------

EXPENSES = "expenses"        # app/.../data/FirestoreMappers.kt  Firestore.EXPENSES
SETTLEMENTS = "settlements"  # Firestore.SETTLEMENTS
PERSONS = ("LOVEL", "MARINA")
SPLITS = ("HALF", "FULL_FOR_OTHER")
EXPENSE_FIELDS = ("date", "category", "description", "amountCents", "paidBy", "split",
                  "createdAt", "updatedAt", "createdBy", "deleted")
SETTLEMENT_FIELDS = ("date", "from", "to", "amountCents", "note",
                     "createdAt", "updatedAt", "createdBy", "deleted")

LEGACY_CSV = REPO_ROOT / "private" / "legacy_popis.csv"
LEGACY_HEADER = ["date", "category", "description", "amount_cents", "paid_by", "split"]
LEGACY_ID_PREFIX = "legacy-"

# domain/.../Categories.kt: Categories.DEFAULT and Categories.DEFAULT_ALIASES.
CATEGORIES = (
    "Adaptacija", "Banka", "Edukacija", "Gotovina", "Hrana", "Hrana van", "Kredit", "MPO",
    "Odjeća i oprema", "Osiguranje", "Ostalo", "Poklon", "Potrepštine", "Prijenos", "Prijevoz",
    "Putovanja", "Režije", "Stanarina", "Usluge", "Zabava", "Zdravlje",
)
CATEGORY_ALIASES = {"Odjeća": "Odjeća i oprema"}

_WHITESPACE = re.compile(r"\s+")


def _clean(s: str) -> str:
    return _WHITESPACE.sub(" ", s.strip())  # String.cleaned(): trim, collapse inner whitespace


def _category_key(s: str) -> str:
    return _clean(s).lower()


_CATEGORY_BY_KEY = {_category_key(c): _clean(c) for c in CATEGORIES}
_CATEGORY_BY_KEY.update({_category_key(a): _clean(t) for a, t in CATEGORY_ALIASES.items()})


def normalize_category(raw: str) -> str:
    """CategoryNormalizer.normalize: case-insensitive match onto the controlled list ("hrana" ->
    "Hrana"), aliases applied ("Odjeća" -> "Odjeća i oprema"); unknown names are returned trimmed
    with inner whitespace collapsed."""
    return _CATEGORY_BY_KEY.get(_category_key(raw), _clean(raw))


def is_known_category(raw: str) -> bool:
    """CategoryNormalizer.isKnown."""
    return _category_key(raw) in _CATEGORY_BY_KEY


def halve_half_even(value: int) -> int:
    """value / 2 rounded half-to-even (BalanceCalculator.kt halveHalfEven)."""
    floor, odd = divmod(value, 2)
    if not odd:
        return floor
    return floor if floor % 2 == 0 else floor + 1


def balance_cents(expenses, settlements=()) -> int:
    """BalanceCalculator.balanceCents over Firestore field dicts: the cents Lovel owes Marina
    (negative: Marina owes Lovel).

    The HALF amounts are totalled (Marina's minus Lovel's) and that difference is halved once,
    half-even; FULL_FOR_OTHER expenses and settlements count in full. Deleted records are skipped.
    """
    half = full = settled = 0
    for e in expenses:
        if e.get("deleted"):
            continue
        signed = e["amountCents"] if e["paidBy"] == "MARINA" else -e["amountCents"]
        if e["split"] == "HALF":
            half += signed
        else:
            full += signed
    for s in settlements:
        if s.get("deleted"):
            continue
        settled += s["amountCents"] if s["from"] == "MARINA" else -s["amountCents"]
    return halve_half_even(half) + full + settled


def category_totals(expenses) -> dict[str, int]:
    """Gross cents per category over non-deleted expenses, largest first (Statistics.byCategory)."""
    totals: dict[str, int] = {}
    for e in expenses:
        if not e.get("deleted"):
            totals[e["category"]] = totals.get(e["category"], 0) + e["amountCents"]
    return dict(sorted(totals.items(), key=lambda kv: (-kv[1], kv[0])))


def format_euros(cents: int) -> str:
    """Display format like Money.format: 1234567 -> "12.345,67 €", -53469 -> "-534,69 €"."""
    sign = "-" if cents < 0 else ""
    whole, frac = divmod(abs(cents), 100)
    return f"{sign}{whole:,}".replace(",", ".") + f",{frac:02d} €"


def legacy_id(row: int) -> str:
    """Document id of the 1-based legacy CSV data row, as LegacyCsvParser assigns it
    ("legacy-%04d"): legacy_id(1) == "legacy-0001"."""
    if row < 1:
        raise ValueError(f"legacy rows are 1-based, got {row}")
    return f"{LEGACY_ID_PREFIX}{row:04d}"


def read_legacy_csv(path: Path) -> list[dict[str, str]]:
    """The data rows of private/legacy_popis.csv (written by export_legacy.py) as dicts."""
    import csv

    if not path.is_file():
        sys.exit(f"Legacy CSV not found: {path}\nRun python tools/export_legacy.py first.")
    with path.open(encoding="utf-8-sig", newline="") as f:
        reader = csv.reader(f)
        header = [h.strip() for h in next(reader, [])]
        if header != LEGACY_HEADER:
            sys.exit(f"{path}: unexpected header {header}, expected {LEGACY_HEADER}")
        rows = []
        for line, fields in enumerate(reader, start=2):
            if not fields or fields == [""]:
                continue
            if len(fields) != len(LEGACY_HEADER):
                sys.exit(f"{path} line {line}: expected {len(LEGACY_HEADER)} fields, got {len(fields)}")
            rows.append(dict(zip(LEGACY_HEADER, fields)))
    return rows


def legacy_expense_document(row: dict[str, str]) -> dict:
    """The expenses/{id} fields for one legacy CSV row, encoded like ExpenseMapper.toDocument.

    Same values as LegacyCsvParser + CategoryNormalizer: category normalised, description kept as
    written (may be empty), createdAt = updatedAt = the expense date at 00:00 UTC,
    createdBy = paidBy, deleted = false. Raises ValueError on a malformed row.
    """
    date = dt.date.fromisoformat(row["date"].strip())
    try:
        amount = int(row["amount_cents"].strip())
    except ValueError:
        raise ValueError(f"bad amount_cents {row['amount_cents']!r}") from None
    paid_by = row["paid_by"].strip()
    split = row["split"].strip()
    if paid_by not in PERSONS:
        raise ValueError(f"bad paid_by {paid_by!r}")
    if split not in SPLITS:
        raise ValueError(f"bad split {split!r}")
    if amount < 0:  # Expense.init: amountCents must not be negative
        raise ValueError(f"negative amount_cents {amount}")
    stamp = dt.datetime(date.year, date.month, date.day, tzinfo=dt.timezone.utc)
    return {
        "date": date.isoformat(),
        "category": normalize_category(row["category"]),
        "description": row["description"],
        "amountCents": amount,
        "paidBy": paid_by,
        "split": split,
        "createdAt": stamp,
        "updatedAt": stamp,
        "createdBy": paid_by,
        "deleted": False,
    }


def legacy_documents(rows: list[dict[str, str]]) -> dict[str, dict]:
    """{legacy-NNNN: document} for the rows in file order (row 1 = first data row)."""
    docs = {}
    for i, row in enumerate(rows, start=1):
        try:
            docs[legacy_id(i)] = legacy_expense_document(row)
        except ValueError as e:
            sys.exit(f"Legacy CSV data row {i}: {e}")
    return docs


def expense_rule_violations(doc: dict) -> list[str]:
    """Why firestore.rules validExpense() would reject doc as a client write; empty = accepted.

    The Admin SDK (and the emulator's owner access) bypass the rules, so the import can store
    such a document. The app can still read it, but cannot edit or soft-delete it, because every
    client update is validated again.
    """
    if set(doc) != set(EXPENSE_FIELDS):
        return [f"field set {sorted(doc)} != {sorted(EXPENSE_FIELDS)}"]
    problems = []
    if not (isinstance(doc["date"], str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", doc["date"])):
        problems.append("date is not a yyyy-MM-dd string")
    if not (isinstance(doc["category"], str) and 0 < len(doc["category"]) <= 100):
        problems.append("category must be a string of 1..100 characters")
    if not (isinstance(doc["description"], str) and len(doc["description"]) <= 1000):
        problems.append("description must be a string of at most 1000 characters")
    amount = doc["amountCents"]
    if not (isinstance(amount, int) and not isinstance(amount, bool) and amount > 0):
        problems.append(f"amountCents must be an integer > 0, got {amount!r}")
    for field in ("paidBy", "createdBy"):
        if doc[field] not in PERSONS:
            problems.append(f"{field} must be LOVEL or MARINA, got {doc[field]!r}")
    if doc["split"] not in SPLITS:
        problems.append(f"split must be HALF or FULL_FOR_OTHER, got {doc['split']!r}")
    for field in ("createdAt", "updatedAt"):
        if not isinstance(doc[field], dt.datetime):
            problems.append(f"{field} must be a timestamp")
    if not isinstance(doc["deleted"], bool):
        problems.append("deleted must be a boolean")
    return problems
