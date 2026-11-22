"""One-time reset of Hisab user and financial data.

This script is intentionally destructive. It requires MONGO_URI and MONGO_DATABASE
from the environment and will not run unless --apply is supplied.
"""
import argparse
import os
import sys

try:
    from pymongo import MongoClient
except ImportError:
    print("Install PyMongo first: python -m pip install pymongo", file=sys.stderr)
    raise SystemExit(2)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true", help="Actually delete data. Without this, only print counts.")
    args = parser.parse_args()

    mongo_uri = os.environ.get("MONGO_URI", "").strip()
    database_name = os.environ.get("MONGO_DATABASE", "hisab_db").strip()
    if not mongo_uri:
        print("MONGO_URI must be set", file=sys.stderr)
        raise SystemExit(2)

    client = MongoClient(mongo_uri)
    db = client[database_name]
    collections = ["userDocument", "hisabGroupDocument", "hisabEntryDocument",
                   "hisabRequestDocument", "admin_audit_log", "privacy_data_requests",
                   "account_recovery_limits"]

    print(f"Database: {database_name}")
    for name in collections:
        print(f"{name}: {db[name].count_documents({})}")

    if not args.apply:
        print("Dry run only. Re-run with --apply to delete these documents.")
        return

    confirmation = input("Type RESET ALL HISAB DATA to continue: ").strip()
    if confirmation != "RESET ALL HISAB DATA":
        print("Cancelled")
        raise SystemExit(1)

    for name in collections:
        result = db[name].delete_many({})
        print(f"Deleted {result.deleted_count} documents from {name}")

    # Keep invite_message_document so the 100-message catalog remains available.


if __name__ == "__main__":
    main()
