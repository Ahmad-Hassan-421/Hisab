"""Reset Hisab account data using an operator-supplied MongoDB connection."""

import argparse
import json
import os
import sys

from pymongo import MongoClient
from pymongo.errors import PyMongoError


ACCOUNT_COLLECTIONS = (
    "userDocument",
    "hisabGroupDocument",
    "hisabEntryDocument",
    "hisabRequestDocument",
    "admin_audit_log",
    "privacy_data_requests",
    "account_recovery_limits",
)
PUBLIC_COLLECTIONS = {"invite_message_document"}


def reset_accounts(database, apply):
    existing = set(database.list_collection_names())
    unexpected = existing - set(ACCOUNT_COLLECTIONS) - PUBLIC_COLLECTIONS
    if unexpected:
        raise ValueError("Review unrecognized collections before resetting account data")
    targets = [name for name in ACCOUNT_COLLECTIONS if name in existing]
    before = {name: database[name].count_documents({}) for name in targets}
    if not apply:
        return {"applied": False, "counts": before}

    def erase(session):
        return {name: database[name].delete_many({}, session=session).deleted_count for name in targets}

    with database.client.start_session() as session:
        removed = session.with_transaction(erase)
    after = {name: database[name].count_documents({}) for name in targets}
    if any(after.values()):
        raise ValueError("New account data appeared during the reset. Stop application writes and inspect before retrying")
    return {"applied": True, "deleted": removed, "remaining": after}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True, help="Exact Hisab database name")
    parser.add_argument("--apply", action="store_true", help="Delete accounts and their related records in a transaction")
    args = parser.parse_args()
    uri = os.environ.get("MONGO_URI")
    if not uri or os.environ.get("MONGO_DATABASE") != args.database:
        parser.error("Set MONGO_URI and MONGO_DATABASE. --database must match MONGO_DATABASE")
    if args.database in {"admin", "config", "local"}:
        parser.error("Choose an application database")
    try:
        with MongoClient(uri, serverSelectionTimeoutMS=5_000, appname="hisab-authorized-account-reset") as client:
            print(json.dumps(reset_accounts(client[args.database], args.apply)))
    except (PyMongoError, ValueError) as error:
        # Driver exception text can contain connection details. Print reviewed local messages only.
        if isinstance(error, ValueError) and str(error) in {
            "Review unrecognized collections before resetting account data",
            "New account data appeared during the reset. Stop application writes and inspect before retrying",
        }:
            print(str(error), file=sys.stderr)
        else:
            print(f"Account reset failed ({type(error).__name__})", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
