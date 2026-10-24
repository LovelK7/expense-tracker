"""Read every expense back from Firestore and check the legacy import against the CSV.

Prints the row count, the balance (BalanceCalculator formula) and the per-category totals of all
non-deleted expenses in Firestore, then compares the imported documents (ids legacy-*) with what
private/legacy_popis.csv says they should be: row count, balance, per-category totals and every
document field by id. Records added in the app later (other ids) and settlements are reported but
not compared, so they never make the check fail. Exit code 0 on PASS, 1 on FAIL.

Read-only. Usage (from the repo root):
    python tools/verify_import.py --emulator
    python tools/verify_import.py              # the real project (needs the service-account key)
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from firebase_common import (EXPENSES, LEGACY_CSV, LEGACY_ID_PREFIX, SETTLEMENTS, FirestoreTarget,
                             add_target_arguments, balance_cents, category_totals, format_euros,
                             legacy_documents, read_collection, read_legacy_csv)


def compare(expected: dict[str, dict], actual: dict[str, dict]) -> list[tuple[str, bool, str]]:
    """(check, passed, detail) rows comparing the legacy-* documents with the CSV's documents."""
    live = {i: d for i, d in actual.items() if not d.get("deleted")}
    checks = []

    checks.append(("row count", len(live) == len(expected),
                   f"Firestore {len(live)} non-deleted legacy-* / CSV {len(expected)}"))

    got, want = balance_cents(live.values()), balance_cents(expected.values())
    checks.append(("balance", got == want, f"Firestore {got} / CSV {want} cents"))

    got_cat, want_cat = category_totals(live.values()), category_totals(expected.values())
    diffs = [f"{c}: {got_cat.get(c, 0)} vs {want_cat.get(c, 0)}"
             for c in sorted(set(got_cat) | set(want_cat)) if got_cat.get(c, 0) != want_cat.get(c, 0)]
    checks.append(("per-category totals", not diffs,
                   f"{len(want_cat)} categories match" if not diffs else "; ".join(diffs)))

    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    different = sorted(i for i in set(expected) & set(actual) if actual[i] != expected[i])
    detail = f"{len(expected) - len(missing) - len(different)} of {len(expected)} identical"
    for label, ids in (("missing", missing), ("not in CSV", extra), ("different", different)):
        if ids:
            detail += f"; {label}: {len(ids)} ({', '.join(ids[:5])}{' ...' if len(ids) > 5 else ''})"
    checks.append(("documents by id", not (missing or extra or different), detail))
    return checks


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--csv", type=Path, default=LEGACY_CSV, help="legacy CSV from export_legacy.py")
    add_target_arguments(parser, writes=False)
    args = parser.parse_args()

    expected = legacy_documents(read_legacy_csv(args.csv))

    with FirestoreTarget(args, writes=False) as target:
        expenses = read_collection(target.client, EXPENSES)
        settlements = read_collection(target.client, SETTLEMENTS)

    live = [d for d in expenses.values() if not d.get("deleted")]
    live_settlements = [s for s in settlements.values() if not s.get("deleted")]
    print(f"Firestore: {target.label}")
    print(f"Expenses: {len(expenses)} documents, {len(live)} not deleted "
          f"({sum(1 for i in expenses if i.startswith(LEGACY_ID_PREFIX))} legacy-*)")
    print(f"Settlements: {len(settlements)} documents, {len(live_settlements)} not deleted")
    balance = balance_cents(live)
    print(f"Balance of expenses: {balance} cents ({format_euros(balance)})")
    if live_settlements:
        total = balance_cents(live, live_settlements)
        print(f"Balance with settlements: {total} cents ({format_euros(total)})")
    print("Sign convention: positive = Lovel owes Marina, negative = Marina owes Lovel")
    print("Per category (non-deleted expenses):")
    for category, cents in category_totals(live).items():
        print(f"  {category:<18} {cents:>10} cents  {format_euros(cents):>14}")

    legacy = {i: d for i, d in expenses.items() if i.startswith(LEGACY_ID_PREFIX)}
    print(f"\nComparing {len(legacy)} legacy-* documents with {args.csv} ({len(expected)} rows):")
    checks = compare(expected, legacy)
    for name, ok, detail in checks:
        print(f"  {'ok  ' if ok else 'FAIL'} {name}: {detail}")
    passed = all(ok for _, ok, _ in checks)
    print("PASS" if passed else "FAIL")
    sys.exit(0 if passed else 1)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
