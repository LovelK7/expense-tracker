"""Shared helpers for the Firebase admin scripts (seed_members.py, deploy_rules.py).

Both authenticate with a service-account key at private/serviceAccountKey.json (gitignored).
Create it in the Firebase console: Project settings > Service accounts > Generate new private key.
"""

from __future__ import annotations

import json
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
