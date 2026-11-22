"""Verify an account reset and fresh signup using disposable local MongoDB only."""

import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen

root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / ".hisab-qa" / "python"))
sys.path.insert(0, str(root / "scripts"))
from pymongo import MongoClient
from reset_accounts import ACCOUNT_COLLECTIONS, reset_accounts


def main():
    uri, name = os.environ["MONGO_URI"], os.environ["MONGO_DATABASE"]
    if not uri.startswith("mongodb://127.0.0.1:") or not name.startswith("hisab_phase"):
        raise RuntimeError("Run this check only with disposable loopback MongoDB")

    def api(method, path, expected, body=None, token=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = Request(f"http://127.0.0.1:{os.environ['PORT']}{path}",
                          json.dumps(body).encode() if body else None, headers, method=method)
        try:
            response = urlopen(request, timeout=15)
        except HTTPError as error:
            response = error
        with response:
            assert response.status == expected, (method, path, response.status, expected)
            raw = response.read()
            return json.loads(raw) if raw else None

    policy = api("GET", "/privacy/policy", 200)["version"]
    identity = {"name": "Reset QA", "email": "reset-qa@example.invalid", "password": "Reset-QA-account-42!",
                "privacyPolicyVersion": policy}
    account = api("POST", "/auth/register", 201, identity)
    user_id = account["user"]["id"]
    with MongoClient(uri, serverSelectionTimeoutMS=5_000) as client:
        database = client[name]
        for collection in ACCOUNT_COLLECTIONS:
            if collection != "userDocument":
                database[collection].insert_one({"ownerId": user_id, "fixture": "disposable-account-data"})
        original_templates = database.invite_message_document.count_documents({})
        assert original_templates > 0
        preview = reset_accounts(database, apply=False)
        assert preview["counts"]["userDocument"] == 1
        assert database.userDocument.count_documents({}) == 1
        database.unrecognized_collection.insert_one({"test": True})
        try:
            reset_accounts(database, apply=True)
        except ValueError:
            pass
        else:
            raise AssertionError("Reset must stop for an unrecognized collection")
        assert database.userDocument.count_documents({}) == 1
        database.drop_collection("unrecognized_collection")
        result = reset_accounts(database, apply=True)
        assert not any(result["remaining"].values())
        assert database.invite_message_document.count_documents({}) == original_templates
        assert not any(reset_accounts(database, apply=True)["deleted"].values())
        api("GET", f"/users/{user_id}", 401, token=account["token"])
        api("POST", "/auth/login", 401, identity)
        replacement = api("POST", "/auth/register", 201, identity)
        assert replacement["user"]["id"] != user_id
        api("GET", "/health", 200)
        print("PASS Reset clears account records, rejects old sessions, preserves public templates and permits fresh signup")


if __name__ == "__main__":
    main()
