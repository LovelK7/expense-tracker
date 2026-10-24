"""Write the allow-list from private/members.json into the Firestore document config/members.

Input  private/members.json:
    {"members": [{"email": "...", "person": "LOVEL"}, {"email": "...", "person": "MARINA"}]}
Output config/members:
    {"emails": ["..."], "persons": {"<email>": "LOVEL" | "MARINA"}}   (emails lowercased)

The app and firestore.rules read this document; clients can never write it (the Admin SDK used
here bypasses the rules). Re-running replaces the whole document.

Usage (from the repo root):
    python tools/seed_members.py [--dry-run] [--members PATH] [--key PATH] [--project ID]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from firebase_common import DEFAULT_KEY, REPO_ROOT, load_key, resolve_project

DEFAULT_MEMBERS = REPO_ROOT / "private" / "members.json"
PERSONS = ("LOVEL", "MARINA")


def build_document(members_file: Path) -> dict:
    """Validates members.json and returns the config/members document."""
    if not members_file.is_file():
        sys.exit(f"Members file not found: {members_file}")
    try:
        raw = json.loads(members_file.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        sys.exit(f"{members_file} is not valid JSON: {e}")
    entries = raw.get("members") if isinstance(raw, dict) else None
    if not isinstance(entries, list) or not entries:
        sys.exit(f'{members_file}: expected {{"members": [{{"email": ..., "person": ...}}, ...]}}')

    emails: list[str] = []
    persons: dict[str, str] = {}
    for i, entry in enumerate(entries):
        email = str(entry.get("email", "")).strip().lower() if isinstance(entry, dict) else ""
        person = entry.get("person") if isinstance(entry, dict) else None
        if "@" not in email:
            sys.exit(f"members[{i}]: missing or invalid email")
        if person not in PERSONS:
            sys.exit(f"members[{i}]: person must be one of {', '.join(PERSONS)}, got {person!r}")
        if email in persons:
            sys.exit(f"members[{i}]: duplicate email {email}")
        emails.append(email)
        persons[email] = person
    return {"emails": emails, "persons": persons}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--members", type=Path, default=DEFAULT_MEMBERS, help="members JSON file")
    parser.add_argument("--key", type=Path, default=DEFAULT_KEY, help="service-account key JSON")
    parser.add_argument("--project", help="Firebase project id (default: .firebaserc)")
    parser.add_argument("--dry-run", action="store_true", help="validate and print, write nothing")
    args = parser.parse_args()

    document = build_document(args.members)
    if args.dry_run:
        print(json.dumps(document, indent=2))
        return

    key = load_key(args.key)
    project = resolve_project(key, args.project)

    import firebase_admin  # imported late so --dry-run works without the package
    from firebase_admin import credentials, firestore

    app = firebase_admin.initialize_app(credentials.Certificate(key), {"projectId": project})
    try:
        ref = firestore.client(app).collection("config").document("members")
        ref.set(document)
        stored = ref.get().to_dict()
    finally:
        firebase_admin.delete_app(app)
    if stored != document:
        sys.exit(f"config/members read back differently: {stored!r}")
    people = ", ".join(sorted(document["persons"].values()))
    print(f"Wrote config/members in {project}: {len(document['emails'])} member(s) ({people})")


if __name__ == "__main__":
    main()
