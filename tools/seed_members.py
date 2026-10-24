"""Write the allow-list from private/members.json into the Firestore document config/members.

Input  private/members.json:
    {"members": [{"email": "...", "person": "LOVEL"}, {"email": "...", "person": "MARINA"}]}
Output config/members:
    {"emails": ["..."], "persons": {"<email>": "LOVEL" | "MARINA"}}   (emails lowercased)

The app and firestore.rules read this document; clients can never write it (the Admin SDK used
here bypasses the rules). Re-running replaces the whole document.

With --emulator the document goes to the local Firestore emulator instead (FIRESTORE_EMULATOR_HOST,
default 127.0.0.1:8080), no service account needed. Unless --members is given it then seeds the
two placeholder dev accounts that the debug build's "Dev: prijava kao ..." buttons sign in with
(DEV_MEMBERS below; keep in sync with app/src/debug/.../DevEnvironment.kt).

Usage (from the repo root):
    python tools/seed_members.py [--dry-run] [--members PATH] [--key PATH] [--project ID]
    python tools/seed_members.py --emulator [--members PATH] [--project ID]
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

from firebase_common import DEFAULT_KEY, FIREBASERC, REPO_ROOT, load_key, resolve_project

DEFAULT_MEMBERS = REPO_ROOT / "private" / "members.json"
PERSONS = ("LOVEL", "MARINA")
# Placeholder accounts for the emulator dev loop only (never real addresses).
DEV_MEMBERS = {"lovel.dev@example.com": "LOVEL", "marina.dev@example.com": "MARINA"}
DEFAULT_EMULATOR_HOST = "127.0.0.1:8080"


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


def dev_document() -> dict:
    return {"emails": list(DEV_MEMBERS), "persons": dict(DEV_MEMBERS)}


def _to_firestore_fields(document: dict) -> dict:
    """config/members in the Firestore REST encoding (string array + string map only)."""
    return {
        "emails": {"arrayValue": {"values": [{"stringValue": e} for e in document["emails"]]}},
        "persons": {
            "mapValue": {"fields": {e: {"stringValue": p} for e, p in document["persons"].items()}}
        },
    }


def _from_firestore_fields(fields: dict) -> dict:
    emails = [v["stringValue"] for v in fields["emails"]["arrayValue"].get("values", [])]
    persons = {e: v["stringValue"] for e, v in fields["persons"]["mapValue"].get("fields", {}).items()}
    return {"emails": emails, "persons": persons}


def seed_emulator(document: dict, project: str) -> None:
    """Writes config/members through the emulator's REST API. "Bearer owner" bypasses the rules."""
    host = os.environ.get("FIRESTORE_EMULATOR_HOST") or DEFAULT_EMULATOR_HOST
    url = f"http://{host}/v1/projects/{project}/databases/(default)/documents/config/members"
    headers = {"Authorization": "Bearer owner", "Content-Type": "application/json"}
    body = json.dumps({"fields": _to_firestore_fields(document)}).encode("utf-8")
    try:
        # PATCH without an update mask replaces the whole document.
        with urllib.request.urlopen(urllib.request.Request(url, body, headers, method="PATCH")) as r:
            stored = _from_firestore_fields(json.load(r)["fields"])
    except urllib.error.URLError as e:
        sys.exit(f"Cannot reach the Firestore emulator at {host}: {e}\n"
                 "Start it first: firebase emulators:start --only auth,firestore (see README).")
    if stored != document:
        sys.exit(f"config/members read back differently: {stored!r}")
    people = ", ".join(sorted(document["persons"].values()))
    print(f"Wrote config/members to the emulator at {host} (project {project}): "
          f"{len(document['emails'])} member(s) ({people})")


def emulator_project(override: str | None) -> str:
    """--project, else .firebaserc's default: the project id the app (google-services.json) uses."""
    if override:
        return override
    if FIREBASERC.is_file():
        project = json.loads(FIREBASERC.read_text(encoding="utf-8")).get("projects", {}).get("default")
        if project:
            return project
    sys.exit("No Firebase project id: pass --project or set a default in .firebaserc.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--members", type=Path, help=f"members JSON file (default: {DEFAULT_MEMBERS}; "
                        "with --emulator: the placeholder dev accounts)")
    parser.add_argument("--key", type=Path, default=DEFAULT_KEY, help="service-account key JSON")
    parser.add_argument("--project", help="Firebase project id (default: .firebaserc)")
    parser.add_argument("--dry-run", action="store_true", help="validate and print, write nothing")
    parser.add_argument("--emulator", action="store_true",
                        help="write to the local Firestore emulator (FIRESTORE_EMULATOR_HOST)")
    args = parser.parse_args()

    if args.emulator and args.members is None:
        document = dev_document()
    else:
        document = build_document(args.members or DEFAULT_MEMBERS)
    if args.dry_run:
        print(json.dumps(document, indent=2))
        return
    if args.emulator:
        seed_emulator(document, emulator_project(args.project))
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
