"""Check candidate repository files without printing matched values."""

from pathlib import Path
import re
import subprocess


PATTERNS = {
    "private key": rb"-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----",
    "Brevo API key": rb"(?i)\bxkeysib-[a-z0-9-]{20,}\b",
    "MongoDB URI with credentials": rb"(?i)mongodb(?:\+srv)?://[^\s/'\":]+:[^\s/'\"@]+@",
    "GitHub token": rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})\b",
    "AWS access key": rb"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b",
    "service account key": rb'"private_key"\s*:\s*"[^"]{30,}"',
}
PRIVATE_SUFFIXES = {".jks", ".keystore", ".pem", ".key", ".pfx", ".p12", ".db", ".sqlite", ".sqlite3"}
PRIVATE_NAMES = {"local.properties", "google-services.json", ".firebaserc"}


def main():
    root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
    files = subprocess.check_output(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=root
    ).decode("utf-8").split("\0")
    violations = []
    checked = 0
    for name in sorted(set(filter(None, files))):
        path = root / name
        if not path.is_file():
            continue
        checked += 1
        if path.name in PRIVATE_NAMES or path.suffix.lower() in PRIVATE_SUFFIXES or (
            path.name.startswith(".env") and path.name != ".env.example"
        ):
            violations.append((name, "private or machine-specific file"))
            continue
        content = path.read_bytes()
        if b"\0" in content[:8192]:
            continue
        for label, pattern in PATTERNS.items():
            if re.search(pattern, content):
                violations.append((name, label))
    for name, label in violations:
        print(f"{name} — {label}")
    print(f"Scanned {checked} candidate files, found {len(violations)} potential exposures")
    return int(bool(violations))


if __name__ == "__main__":
    raise SystemExit(main())
