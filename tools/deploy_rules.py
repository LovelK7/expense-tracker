"""Publish firestore.rules to the Firebase project through the Firebase Rules REST API.

Creates a ruleset from firestore.rules, then points the release "cloud.firestore" (the rules of
the (default) Firestore database) at it. --check only compiles the rules on the server.

Equivalent to `firebase deploy --only firestore:rules`, but authenticated with the
service-account key instead of a personal `firebase login`. The key's service account needs
permission to manage rules: on HTTP 403, grant it the IAM role "Firebase Rules Admin" and check
that the Firebase Rules API is enabled for the project.

Usage (from the repo root):
    python tools/deploy_rules.py [--check] [--rules PATH] [--key PATH] [--project ID]
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from firebase_common import DEFAULT_KEY, REPO_ROOT, load_key, resolve_project

API = "https://firebaserules.googleapis.com/v1"
SCOPES = ["https://www.googleapis.com/auth/firebase", "https://www.googleapis.com/auth/cloud-platform"]
RELEASE = "cloud.firestore"


def fail(action: str, response) -> None:
    try:
        error = response.json().get("error", {})
    except ValueError:
        error = {"message": response.text}
    lines = [f"{action} failed: HTTP {response.status_code} {error.get('message', '')}".rstrip()]
    # Compile errors come back as SourcePosition-annotated issues in the error details.
    for detail in error.get("details", []):
        for issue in detail.get("issues", []) if isinstance(detail, dict) else []:
            pos = issue.get("sourcePosition", {})
            lines.append(f"  line {pos.get('line', '?')}:{pos.get('column', '?')} "
                         f"{issue.get('severity', '')} {issue.get('description', '')}")
    sys.exit("\n".join(lines))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--rules", type=Path, default=REPO_ROOT / "firestore.rules")
    parser.add_argument("--key", type=Path, default=DEFAULT_KEY, help="service-account key JSON")
    parser.add_argument("--project", help="Firebase project id (default: .firebaserc)")
    parser.add_argument("--check", action="store_true", help="compile on the server, publish nothing")
    args = parser.parse_args()

    if not args.rules.is_file():
        sys.exit(f"Rules file not found: {args.rules}")
    key = load_key(args.key)
    project = resolve_project(key, args.project)

    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    session = AuthorizedSession(service_account.Credentials.from_service_account_info(key, scopes=SCOPES))
    source = {"files": [{"name": "firestore.rules", "content": args.rules.read_text(encoding="utf-8")}]}

    if args.check:
        r = session.post(f"{API}/projects/{project}:test", json={"source": source}, timeout=60)
        if not r.ok:
            fail("Compiling rules", r)
        issues = r.json().get("issues", [])
        for issue in issues:
            pos = issue.get("sourcePosition", {})
            print(f"line {pos.get('line', '?')}: {issue.get('severity', '')} {issue.get('description', '')}")
        if any(i.get("severity") == "ERROR" for i in issues):
            sys.exit("Rules do not compile.")
        print(f"Rules compile for {project}; nothing published (--check).")
        return

    r = session.post(f"{API}/projects/{project}/rulesets", json={"source": source}, timeout=60)
    if not r.ok:
        fail("Creating ruleset", r)
    ruleset = r.json()["name"]

    release = f"projects/{project}/releases/{RELEASE}"
    r = session.patch(f"{API}/{release}", json={"release": {"name": release, "rulesetName": ruleset}}, timeout=60)
    if r.status_code == 404:  # first deployment: the release does not exist yet
        r = session.post(f"{API}/projects/{project}/releases",
                         json={"name": release, "rulesetName": ruleset}, timeout=60)
    if not r.ok:
        fail("Releasing ruleset", r)
    print(f"Published {args.rules.name} to {project} as {ruleset}")


if __name__ == "__main__":
    main()
