"""Import the legacy history (private/legacy_popis.csv) into Firestore as expenses/{id} documents.

One document per CSV data row, encoded exactly like the app's ExpenseMapper (see
firebase_common.legacy_expense_document): categories normalised like the domain
CategoryNormalizer, createdAt = updatedAt = the expense date at 00:00 UTC, createdBy = paidBy,
deleted = false.

Document ids are "legacy-<4-digit 1-based CSV data row>" (legacy-0001 ...), the same ids the
domain LegacyCsvParser assigns. A content hash was rejected: the history has rows that are
identical in every column (same shop, day and amount), which a hash would collapse into one
document, and correcting a row in the sheet would leave the old version behind as a duplicate.
Row ids stay stable as long as the CSV is regenerated from the same sheet with rows only appended,
so re-running the import overwrites the same documents and never duplicates them.

Before writing, existing legacy-* documents are compared with what would be written. If any of
them differ (for example a row edited or soft-deleted in the app after an earlier import), the
import stops and lists them, so app edits are never overwritten silently; --overwrite-changed
writes anyway.

Targets: --dry-run parses and prints without touching Firestore; --emulator writes to the local
emulator; the real project is written only with --yes-real-project.

Usage (from the repo root):
    python tools/import_legacy.py --dry-run [--limit N]
    python tools/import_legacy.py --emulator [--limit N]
    python tools/import_legacy.py --yes-real-project
"""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from pathlib import Path

from firebase_common import (EXPENSES, LEGACY_CSV, LEGACY_ID_PREFIX, FirestoreTarget,
                             add_target_arguments, balance_cents, expense_rule_violations,
                             format_euros, get_documents, legacy_documents, read_legacy_csv,
                             write_documents)

BATCH_SIZE = 500  # Firestore's limit of writes per batch


def summarize(rows: list[dict[str, str]], docs: dict[str, dict]) -> None:
    renamed = Counter((row["category"], doc["category"]) for row, doc in zip(rows, docs.values())
                      if row["category"] != doc["category"])
    splits = Counter((d["paidBy"], d["split"]) for d in docs.values())
    print(f"Rows: {len(docs)} ({next(iter(docs))} .. {next(reversed(docs))})")
    print(f"Dates: {min(d['date'] for d in docs.values())} .. {max(d['date'] for d in docs.values())}")
    for (raw, normalized), n in sorted(renamed.items()):
        print(f"Category normalised: {raw!r} -> {normalized!r} ({n} row(s))")
    for (payer, split), n in sorted(splits.items()):
        print(f"  {payer:<6} {split:<14} {n:>5}")
    for doc_id, doc in docs.items():
        for problem in expense_rule_violations(doc):
            print(f"Warning: {doc_id} ({doc['date']} {doc['category']} {doc['description']!r}) would "
                  f"fail firestore.rules validExpense: {problem}. The app can read it but not edit or "
                  "delete it.")


def print_balance(label: str, docs) -> int:
    balance = balance_cents(docs)
    print(f"{label}: {balance} cents ({format_euros(balance)}; positive = Lovel owes Marina)")
    return balance


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--csv", type=Path, default=LEGACY_CSV, help="legacy CSV from export_legacy.py")
    parser.add_argument("--limit", type=int, help="import only the first N rows (smoke test)")
    parser.add_argument("--dry-run", action="store_true",
                        help="parse, normalise and print; do not connect to Firestore")
    parser.add_argument("--overwrite-changed", action="store_true",
                        help="also overwrite legacy-* documents that differ from the CSV")
    add_target_arguments(parser, writes=True)
    args = parser.parse_args()
    if args.limit is not None and args.limit < 1:
        parser.error("--limit must be at least 1")

    rows = read_legacy_csv(args.csv)
    if not rows:
        sys.exit(f"{args.csv} has no data rows.")
    if args.limit is not None:
        rows = rows[:args.limit]
    docs = legacy_documents(rows)
    summarize(rows, docs)
    print_balance("Balance of the rows to import", docs.values())

    if args.dry_run:
        print("Dry run: nothing written.")
        return

    with FirestoreTarget(args, writes=True) as target:
        client = target.client
        existing = get_documents(client, EXPENSES, docs)
        present = {i: d for i, d in existing.items() if d is not None}
        changed = sorted(i for i, d in present.items() if d != docs[i])
        if changed and not args.overwrite_changed:
            listed = ", ".join(changed[:10]) + (" ..." if len(changed) > 10 else "")
            sys.exit(f"{len(changed)} existing document(s) in {target.label} differ from the CSV "
                     f"(edited in the app, or the CSV changed): {listed}\n"
                     "Nothing written. Re-run with --overwrite-changed to replace them.")

        print(f"Writing {len(docs)} documents to {EXPENSES}/ in {target.label} "
              f"({len(present)} already exist and will be overwritten"
              f"{f', {len(changed)} of them changed' if changed else ''}) ...")
        batches = write_documents(client, EXPENSES, docs, BATCH_SIZE)
        print(f"Committed {batches} batch(es) of at most {BATCH_SIZE} writes.")

        stored = get_documents(client, EXPENSES, docs)
        missing = sorted(i for i, d in stored.items() if d is None)
        mismatched = sorted(i for i, d in stored.items() if d is not None and d != docs[i])
        if missing or mismatched:
            sys.exit(f"Read-back failed: {len(missing)} missing, {len(mismatched)} different "
                     f"(first: {(missing + mismatched)[:5]})")
        all_ids = [snap.id for snap in client.collection(EXPENSES).select([]).stream()]
        legacy_total = sum(1 for i in all_ids if i.startswith(LEGACY_ID_PREFIX))

    print(f"Read back {len(stored)} written documents: all match.")
    print_balance("Balance of the written documents (read back)", stored.values())
    print(f"{EXPENSES}/ now holds {len(all_ids)} document(s), {legacy_total} of them legacy-*.")
    extra = legacy_total - len(docs)
    if args.limit is None and extra > 0:
        print(f"Warning: {extra} legacy-* document(s) are not in the CSV (an earlier, longer CSV?).")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
