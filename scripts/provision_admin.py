"""Assign the Hisab admin role to a verified existing account after operator review."""

import argparse
import os
import time
import uuid
from urllib.parse import parse_qs, urlsplit

from pymongo import MongoClient
from pymongo.errors import PyMongoError


class IneligibleAccount(Exception):
    pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--email", required=True, help="The account's verified registration email")
    parser.add_argument("--hisab-id", required=True, help="The shareable Hisab ID confirmed by the account owner")
    parser.add_argument("--operator", help="A reviewed audit identifier for the person performing the change")
    parser.add_argument("--apply", action="store_true", help="Update the role. The default is a read-only eligibility check.")
    args = parser.parse_args()
    if args.apply and (not args.operator or len(args.operator) > 100):
        parser.error("--apply requires an operator audit identifier of at most 100 characters")

    uri = os.environ.get("MONGO_URI", "")
    database = os.environ.get("MONGO_DATABASE", "")
    if not uri or not database:
        parser.error("Set MONGO_URI and MONGO_DATABASE explicitly")
    try:
        host = urlsplit(uri)
    except ValueError:
        parser.error("MONGO_URI is invalid")
    options = {name.lower(): [value.lower() for value in values] for name, values in parse_qs(host.query).items()}
    loopback = host.scheme == "mongodb" and host.hostname == "127.0.0.1" and database.startswith("hisab_phase")
    safe_tls = host.scheme == "mongodb+srv" and all(
        "false" not in options.get(name, []) for name in ("tls", "ssl")
    ) and all("true" not in options.get(name, []) for name in ("tlsinsecure", "tlsallowinvalidcertificates", "tlsallowinvalidhostnames"))
    if not loopback and not safe_tls:
        parser.error("Use an Atlas SRV connection with certificate verification or disposable loopback MongoDB")

    try:
        with MongoClient(uri, serverSelectionTimeoutMS=5_000) as client:
            db = client[database]
            users = db["userDocument"]
            identity = {"email": args.email, "uniqueId": args.hisab_id}

            def inspect(session=None):
                account = users.find_one(identity, {"role": 1, "emailVerifiedAt": 1, "accountStatus": 1}, session=session)
                if account is None:
                    raise IneligibleAccount("No account matches both identifiers")
                if account.get("accountStatus") not in (None, "active"):
                    raise IneligibleAccount("The matched account must be active")
                if not isinstance(account.get("emailVerifiedAt"), int) or account["emailVerifiedAt"] <= 0:
                    raise IneligibleAccount("The matched account must first verify its recovery email")
                return account

            def assign(session):
                account = inspect(session)
                if (account.get("role") or "user").lower() == "admin":
                    return "Account already has the admin role"
                now = int(time.time() * 1000)
                users.update_one({"_id": account["_id"]},
                                 {"$set": {"role": "admin", "updatedAt": now}, "$inc": {"sessionVersion": 1}},
                                 session=session)
                db["admin_audit_log"].insert_one({
                    "_id": str(uuid.uuid4()), "action": "admin_provisioned", "operator": args.operator,
                    "targetId": account["_id"], "createdAt": now,
                }, session=session)
                return "Admin role assigned. The account owner must sign in again."

            if args.apply:
                with client.start_session() as session:
                    print(session.with_transaction(assign))
            else:
                account = inspect()
                print("Account already has the admin role" if (account.get("role") or "user").lower() == "admin"
                      else "Verified account is eligible. The database was read only.")
    except IneligibleAccount as error:
        raise SystemExit(str(error)) from None
    except (PyMongoError, ValueError) as error:
        raise SystemExit(f"MongoDB operation failed ({type(error).__name__})") from None


if __name__ == "__main__":
    main()
