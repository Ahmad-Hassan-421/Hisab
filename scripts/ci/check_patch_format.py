"""Check whitespace in changed lines between two known Git revisions."""

import argparse
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base")
    args = parser.parse_args()
    if args.base and set(args.base) != {"0"}:
        base = args.base
    else:
        parent = subprocess.run(["git", "rev-parse", "--verify", "HEAD^"], capture_output=True, text=True)
        base = parent.stdout.strip() if parent.returncode == 0 else None
    command = ["git", "diff", "--check"] if base is None else ["git", "diff", "--check", base, "HEAD"]
    return subprocess.run(command, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
