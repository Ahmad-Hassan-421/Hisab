"""Validate the administration API and provisioning in a disposable, loopback Hisab instance."""

import json
import os
import subprocess
import sys
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

root = Path(__file__).resolve().parents[2]
qa_modules = root / ".hisab-qa" / "python"
sys.path.append(str(qa_modules))
from pymongo import MongoClient


def main(android_case=None):
    uri = os.environ["MONGO_URI"]
    database = os.environ["MONGO_DATABASE"]
    if not uri.startswith("mongodb://127.0.0.1:") or not database.startswith("hisab_phase"):
        raise RuntimeError("Use only a disposable loopback MongoDB database")
    base = f"http://127.0.0.1:{os.environ['PORT']}"

    def expect(status, method, path, body=None, token=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = Request(base + path, json.dumps(body).encode() if body else None, headers, method=method)
        try:
            response = urlopen(request, timeout=12)
        except HTTPError as error:
            response = error
        with response:
            assert response.status == status, (method, path, response.status, status)
            raw = response.read()
            return json.loads(raw) if raw else None

    def provision(email, uid, apply=False):
        args = [sys.executable, str(root / "scripts" / "provision_admin.py"), "--email", email, "--hisab-id", uid]
        if apply:
            args += ["--apply", "--operator", "hisab-local-admin-qa"]
        modules = os.pathsep.join(value for value in (str(qa_modules), os.getenv("PYTHONPATH")) if value)
        return subprocess.run(args, cwd=root, env={**os.environ, "PYTHONPATH": modules}, capture_output=True, text=True, timeout=15)

    password = "Admin-QA-password-42!"
    batch = uuid.uuid4().hex[:10]
    policy_version = expect(200, "GET", "/privacy/policy")["version"]

    def register(name, label, **other):
        email = f"{label}-{batch}@example.invalid"
        result = expect(201, "POST", "/auth/register", {
            "email": email, "password": password, "name": name, "privacyPolicyVersion": policy_version, **other
        })
        assert result["user"]["role"] == "user"
        return result

    with MongoClient(uri, serverSelectionTimeoutMS=5_000) as mongo:
        db = mongo[database]
        users = db.userDocument
        owner = register("Admin QA", "admin")
        member = register("Directory Member QA", "member", role="admin")
        legacy = register("Legacy QA", "legacy")
        owner_profile = owner["user"]
        member_profile = member["user"]
        legacy_profile = legacy["user"]
        users.update_one({"_id": legacy_profile["id"]}, {"$unset": {"createdAt": "", "accountStatus": "", "role": ""}})

        expect(401, "GET", "/admin/users")
        expect(403, "GET", "/admin/overview", token=member["token"])
        expect(403, "GET", "/admin/users", token=member["token"])
        member_status = f"/admin/users/{member_profile['id']}/status"
        expect(403, "PATCH", member_status, {"status": "suspended"}, member["token"])
        own_profile = expect(200, "PUT", f"/users/{member_profile['id']}", {
            "name": member_profile["name"], "themePreference": "system", "profilePicUrl": "", "role": "admin", "accountStatus": "suspended"
        }, member["token"])
        assert own_profile["role"] == "user"
        assert provision(owner_profile["email"], owner_profile["uniqueId"]).returncode != 0
        users.update_one({"_id": owner_profile["id"]}, {"$set": {"emailVerifiedAt": int(time.time() * 1000)}})
        assert provision(owner_profile["email"], member_profile["uniqueId"]).returncode != 0
        assert provision(owner_profile["email"], owner_profile["uniqueId"]).returncode == 0
        assert db.admin_audit_log.count_documents({}) == 0
        expect(403, "GET", "/admin/users", token=owner["token"])
        assert provision(owner_profile["email"], owner_profile["uniqueId"], apply=True).returncode == 0
        assert provision(owner_profile["email"], owner_profile["uniqueId"], apply=True).returncode == 0
        expect(401, "GET", "/admin/users", token=owner["token"])
        admin_token = expect(200, "POST", "/auth/login", {"email": owner_profile["email"], "password": password})["token"]
        assert db.admin_audit_log.count_documents({"action": "admin_provisioned"}) == 1
        print("PASS Signed-in role checks and controlled, audited provisioning")

        new = register("Directory New QA", "new")
        overview = expect(200, "GET", "/admin/overview", token=admin_token)
        assert overview == {"registered": 4, "registeredInLast30Days": 3, "suspended": 0}

        def directory(**parameters):
            return expect(200, "GET", "/admin/users?" + urlencode(parameters), token=admin_token)

        all_accounts, parameters = [], {"limit": 1}
        for _ in range(6):
            page = directory(**parameters)
            all_accounts.extend(page["users"])
            cursor = page.get("nextCursor")
            if not cursor:
                break
            parameters["cursor"] = cursor
        assert len(all_accounts) == 4 and len({entry["id"] for entry in all_accounts}) == 4
        assert all_accounts[0]["id"] == new["user"]["id"]
        assert all_accounts[-1]["id"] == legacy_profile["id"] and all_accounts[-1].get("createdAt") is None
        assert all_accounts[-1]["role"] == "user" and all_accounts[-1]["status"] == "active"
        allowed = {"id", "publicId", "name", "email", "createdAt", "role", "status", "emailVerified", "avatarUrl"}
        assert all(entry.keys() <= allowed for entry in all_accounts)
        assert directory(q="directory member")["users"][0]["id"] == member_profile["id"]
        assert directory(q=member_profile["email"].upper())["users"][0]["id"] == member_profile["id"]
        assert directory(q=member_profile["uniqueId"])["users"][0]["id"] == member_profile["id"]
        assert not directory(q="[.*")["users"]
        expect(400, "GET", "/admin/users?limit=1000", token=admin_token)
        expect(400, "GET", "/admin/users?cursor=invalid", token=admin_token)
        print("PASS Legacy and new accounts, private field exclusion, bounded search, pagination, and overview")

        member_group = expect(201, "POST", "/groups", {
            "name": "Admin preservation QA", "members": [member_profile["id"]]
        }, member["token"])
        member_groups_path = "/groups?" + urlencode({"userId": member_profile["id"]})
        expect(403, "PATCH", f"/admin/users/{owner_profile['id']}/status", {"status": "suspended"}, admin_token)
        expect(204, "PATCH", member_status, {"status": "suspended"}, admin_token)
        expect(204, "PATCH", member_status, {"status": "suspended"}, admin_token)
        expect(401, "GET", member_groups_path, token=member["token"])
        expect(401, "POST", "/auth/login", {"email": member_profile["email"], "password": password})
        assert directory(status="suspended")["users"][0]["id"] == member_profile["id"]
        assert expect(200, "GET", "/admin/overview", token=admin_token)["suspended"] == 1
        expect(204, "PATCH", member_status, {"status": "active"}, admin_token)
        assert not directory(status="suspended")["users"]
        expect(401, "GET", member_groups_path, token=member["token"])
        member_new_token = expect(200, "POST", "/auth/login", {"email": member_profile["email"], "password": password})["token"]
        groups = expect(200, "GET", member_groups_path, token=member_new_token)
        assert [group["id"] for group in groups] == [member_group["id"]]
        assert db.admin_audit_log.count_documents({"action": "account_status_changed"}) == 2
        print("PASS Audited suspension and reactivation preserve existing Hisabs and invalidate old sessions")

        if android_case is not None:
            android_case(owner_profile["email"], member_profile["email"], password)

        users.update_one({"_id": owner_profile["id"]}, {"$set": {"role": "user"}})
        expect(403, "GET", "/admin/users", token=admin_token)
        print("PASS Current database role overrides a previously issued administrator token")


if __name__ == "__main__":
    main()
