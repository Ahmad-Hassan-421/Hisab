"""Exercise the versioned privacy agreement against disposable Hisab and MongoDB."""

import json
import os
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

sys.path.append(str(Path(__file__).resolve().parents[2] / ".hisab-qa" / "python"))
from pymongo import MongoClient


def main(android_case=None):
    uri = os.environ["MONGO_URI"]
    name = os.environ["MONGO_DATABASE"]
    if not uri.startswith("mongodb://127.0.0.1:") or not name.startswith("hisab_phase"):
        raise RuntimeError("Use a disposable loopback database")
    base = f"http://127.0.0.1:{os.environ['PORT']}"

    def expect(code, method, path, data=None, bearer=None):
        headers = {"Content-Type": "application/json"}
        if bearer:
            headers["Authorization"] = f"Bearer {bearer}"
        request = Request(base + path, json.dumps(data).encode() if data is not None else None, headers, method=method)
        try:
            result = urlopen(request, timeout=30)
        except HTTPError as error:
            result = error
        with result:
            assert result.status == code, (method, path, result.status, code)
            return json.loads(result.read())

    policy = expect(200, "GET", "/privacy/policy")
    published = policy["version"]
    acknowledgment = policy["acknowledgmentVersion"]
    assert policy["effectiveDate"] and len(policy["sections"]) >= 8
    status = "/account/privacy/status"
    accept = "/account/privacy/acknowledge"
    request_data = "/account/privacy/data-requests"
    password = "Privacy-only-42!"

    def details():
        return {"name": "Privacy QA", "email": f"privacy-{uuid.uuid4().hex[:10]}@example.invalid", "password": password}

    def clear_acknowledgment(users, user_id):
        fields = {key: "" for key in ("privacyPolicyVersion", "privacyPolicyAcknowledgmentVersion", "privacyPolicyAcceptedAt", "privacyPolicyHistory")}
        users.update_one({"_id": user_id}, {"$unset": fields})

    with MongoClient(uri, serverSelectionTimeoutMS=5_000) as mongo:
        db = mongo[name]
        users = db.userDocument
        credentials = details()
        expect(428, "POST", "/auth/register", credentials)
        expect(428, "POST", "/auth/register", {**credentials, "privacyPolicyVersion": "previous"})
        assert users.find_one({"email": credentials["email"]}) is None
        before_signup = int(time.time() * 1000)
        registered = expect(201, "POST", "/auth/register", {
            **credentials, "privacyPolicyVersion": published, "privacyPolicyAcceptedAt": 4
        })
        token = registered["token"]
        user_id = registered["user"]["id"]
        stored_signup = users.find_one({"_id": user_id})
        assert before_signup <= stored_signup["privacyPolicyAcceptedAt"] <= int(time.time() * 1000)
        assert stored_signup["privacyPolicyVersion"] == published and len(stored_signup["privacyPolicyHistory"]) == 1
        assert expect(200, "GET", status, bearer=token)["acknowledged"]
        print("PASS Signup requires the current version and persists a server-generated acknowledgment")

        legacy_credentials = details()
        prior = expect(201, "POST", "/auth/register", {**legacy_credentials, "privacyPolicyVersion": published})
        legacy_id = prior["user"]["id"]
        clear_acknowledgment(users, legacy_id)
        legacy_token = expect(200, "POST", "/auth/login", legacy_credentials)["token"]
        groups = "/groups?" + urlencode({"userId": legacy_id})
        expect(401, "GET", status)
        assert not expect(200, "GET", status, bearer=legacy_token)["acknowledged"]
        expect(200, "GET", "/users/" + legacy_id, bearer=legacy_token)
        expect(428, "GET", "/users/" + user_id, bearer=legacy_token)
        expect(428, "GET", "/users/by-unique-id/" + registered["user"]["uniqueId"], bearer=legacy_token)
        expect(428, "GET", groups, bearer=legacy_token)
        expect(428, "GET", "/entries?groupId=qa", bearer=legacy_token)
        expect(428, "GET", "/requests?receiverId=" + legacy_id, bearer=legacy_token)
        expect(428, "POST", "/groups", {"name": "Legacy QA", "members": [legacy_id]}, legacy_token)
        expect(409, "POST", accept, {"version": "previous"}, legacy_token)
        assert users.find_one({"_id": legacy_id}).get("privacyPolicyVersion") is None
        first_request = expect(202, "POST", request_data, {"type": "access", "ownerId": user_id}, legacy_token)
        assert expect(202, "POST", request_data, {"type": "access"}, legacy_token) == first_request
        assert db.privacy_data_requests.find_one({"_id": first_request["reference"]})["ownerId"] == legacy_id
        expect(401, "POST", request_data, {"type": "deletion"})
        expect(400, "POST", request_data, {"type": "unrecognized"}, legacy_token)
        assert users.find_one({"_id": legacy_id}).get("privacyPolicyVersion") is None
        print("PASS Legacy accounts can sign in, read account information, and ask for data without acknowledging")

        with ThreadPoolExecutor(max_workers=2) as executor:
            results = list(executor.map(lambda _: expect(200, "POST", accept, {"version": published}, legacy_token), range(2)))
        assert results[0] == results[1] and results[0]["acknowledged"]
        assert len(users.find_one({"_id": legacy_id})["privacyPolicyHistory"]) == 1
        expect(200, "GET", groups, bearer=legacy_token)
        repeated_login = expect(200, "POST", "/auth/login", legacy_credentials)["token"]
        assert expect(200, "GET", status, bearer=repeated_login)["acceptedAt"] == results[0]["acceptedAt"]
        print("PASS Concurrent acknowledgment records once and persists across sign-ins")

        users.update_one({"_id": legacy_id}, {"$set": {"privacyPolicyVersion": "earlier-editorial-revision"}})
        assert expect(200, "GET", status, bearer=legacy_token)["acknowledged"]
        users.update_one({"_id": legacy_id}, {"$set": {"privacyPolicyAcknowledgmentVersion": "prior-required-revision"}})
        assert not expect(200, "GET", status, bearer=legacy_token)["acknowledged"]
        expect(428, "GET", groups, bearer=legacy_token)
        expect(200, "POST", accept, {"version": published}, legacy_token)
        history = users.find_one({"_id": legacy_id})["privacyPolicyHistory"]
        assert len(history) == 2 and history[-1]["acknowledgmentVersion"] == acknowledgment
        print("PASS Editorial updates keep acknowledgment, and required updates record a new decision")

        if android_case is not None:
            phone = details()
            account = expect(201, "POST", "/auth/register", {**phone, "privacyPolicyVersion": published})["user"]
            clear_acknowledgment(users, account["id"])
            android_case(phone["email"], password)
            assert users.find_one({"_id": account["id"]})["privacyPolicyVersion"] == published
            assert db.privacy_data_requests.count_documents({"ownerId": account["id"], "type": "access"}) == 1


if __name__ == "__main__":
    main()
