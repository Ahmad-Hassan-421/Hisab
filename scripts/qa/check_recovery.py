"""Run beside a disposable Hisab server with its development Brevo sink set to port 18095."""

import json
import os
import re
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from queue import Empty, Queue
from threading import Thread
from urllib.error import HTTPError
from urllib.request import Request, urlopen

sys.path.append(str(Path(__file__).resolve().parents[2] / ".hisab-qa" / "python"))
from pymongo import MongoClient


def main(android_case=None):
    uri = os.environ["MONGO_URI"]
    database = os.environ["MONGO_DATABASE"]
    if not uri.startswith("mongodb://127.0.0.1:") or not database.startswith("hisab_phase"):
        raise RuntimeError("Use a disposable loopback MongoDB database")
    mongo = MongoClient(uri, serverSelectionTimeoutMS=5_000)
    db = mongo[database]
    users = db["userDocument"]
    emails = Queue()

    class EmailSink(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            matched = re.search(r"<code>([A-Za-z0-9_-]{43})</code>", body["htmlContent"])
            if matched:
                assert self.headers.get("api-key") == os.environ["BREVO_API_KEY"]
                assert body["sender"]["name"] == "Hisab by Hantalyze"
                emails.put((body["subject"], body["to"][0]["email"], matched[1]))
            self.send_response(201)
            self.send_header("Content-Length", "0")
            self.end_headers()

    def api(method, path, body=None, token=None):
        headers = {}
        data = None
        if body is not None:
            if path == "/auth/register":
                body = {**body, "privacyPolicyVersion": policy_version}
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = Request(f"http://127.0.0.1:{os.environ['PORT']}{path}", data, headers, method=method)
        try:
            response = urlopen(request, timeout=15)
        except HTTPError as error:
            response = error
        with response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None, response.headers

    def expect(status, method, path, body=None, token=None):
        result = api(method, path, body, token)
        assert result[0] == status, (method, path, result[0], status)
        return result[1]

    def receive(subject, address):
        actual_subject, actual_address, code = emails.get(timeout=8)
        assert (actual_subject, actual_address) == (subject, address)
        return code

    def no_delivery():
        try:
            emails.get(timeout=0.8)
        except Empty:
            return
        raise AssertionError("Unexpected email delivery")

    def account():
        return f"recovery-{uuid.uuid4().hex[:10]}@example.invalid"

    old_password = "Hisab-original-84!"
    new_password = "Hisab-updated-24!"
    address = account()
    verify_subject = "Verify your Hisab email"
    reset_subject = "Reset your Hisab password"
    reset_request = "/auth/recovery/request"
    reset_path = "/auth/recovery/reset"
    verification = "/auth/verification"
    identity = {"email": address}
    policy_version = expect(200, "GET", "/privacy/policy")["version"]

    with ThreadingHTTPServer(("127.0.0.1", 18095), EmailSink) as sink:
        daemon = Thread(target=sink.serve_forever, daemon=True)
        daemon.start()
        try:
            registered = expect(201, "POST", "/auth/register", {**identity, "name": "Recovery QA", "password": old_password})
            token = registered["token"]
            user = registered["user"]
            assert users.find_one({"email": address}), "Disposable test user was not found"
            users.update_one({"email": address}, {"$unset": {"sessionVersion": "", "emailVerifiedAt": ""}})
            expect(401, "GET", verification)
            assert expect(200, "GET", verification, token=token) == {"verified": False}
            assert expect(202, "POST", reset_request, identity) == expect(202, "POST", reset_request, {"email": account()})
            no_delivery()
            print("PASS Legacy and unknown accounts receive the same response without email delivery")

            expect(202, "POST", verification + "/request", token=token)
            verification_code = receive(verify_subject, address)
            expect(400, "POST", reset_path, {**identity, "code": verification_code, "password": new_password})
            expect(204, "POST", verification + "/confirm", {"code": verification_code}, token)
            expect(400, "POST", verification + "/confirm", {"code": verification_code}, token)
            assert expect(200, "GET", verification, token=token) == {"verified": True}
            print("PASS Email proof requires sign-in and a single-use mailbox code")

            expect(202, "POST", reset_request, identity)
            expired_code = receive(reset_subject, address)
            stored = users.find_one({"email": address})["passwordRecovery"]
            assert set(stored) == {"digest", "expiresAt"} and stored["digest"] != expired_code
            assert stored["expiresAt"] > int(time.time() * 1000)
            users.update_one({"email": address}, {"$set": {"passwordRecovery.expiresAt": 1}})
            expect(400, "POST", reset_path, {**identity, "code": expired_code, "password": new_password})
            expect(200, "POST", "/auth/login", {**identity, "password": old_password})
            print("PASS The database stores only a digest and an expired code leaves the password intact")

            expect(202, "POST", reset_request, identity)
            reset_code = receive(reset_subject, address)
            expect(400, "POST", reset_path, {**identity, "code": reset_code, "password": "short"})
            expect(400, "POST", reset_path, {"email": account(), "code": reset_code, "password": new_password})
            reset = {**identity, "code": reset_code, "password": new_password}
            with ThreadPoolExecutor(max_workers=2) as pool:
                results = list(pool.map(lambda _: api("POST", reset_path, reset)[0], range(2)))
            assert sorted(results) == [204, 400], results
            expect(400, "POST", reset_path, reset)
            expect(401, "GET", "/users/" + user["id"], token=token)
            expect(401, "POST", "/auth/login", {**identity, "password": old_password})
            authenticated = expect(200, "POST", "/auth/login", {**identity, "password": new_password})
            expect(200, "PUT", "/users/" + user["id"], {
                "name": "Recovery QA updated", "profilePicUrl": "", "themePreference": "dark"
            }, authenticated["token"])
            expect(401, "GET", "/users/" + user["id"], token=token)
            assert expect(200, "GET", verification, token=authenticated["token"]) == {"verified": True}
            print("PASS Concurrent reset consumes the code once, preserves profile security, and revokes old sessions")

            expect(202, "POST", reset_request, identity)
            receive(reset_subject, address)
            expect(202, "POST", reset_request, identity)
            no_delivery()
            other = expect(201, "POST", "/auth/register", {"email": account(), "name": "Rate limit QA", "password": old_password})
            for _ in range(4):
                expect(202, "POST", verification + "/request", token=other["token"])
            status, _, headers = api("POST", verification + "/request", token=other["token"])
            assert status == 429 and int(headers["Retry-After"]) > 0
            rate_bucket = db["account_recovery_limits"].find_one({"_id": re.compile(r"^verify-account:")}, sort=[("count", -1)])
            assert rate_bucket["count"] == 5 and rate_bucket["expiresAt"].replace(tzinfo=timezone.utc) > datetime.now(timezone.utc)
            print("PASS MongoDB rate limits reject repeated verification and suppress repeated delivery generically")
            receive(verify_subject, other["user"]["email"])

            if android_case is not None:
                android_case(expect, receive, account())
        finally:
            sink.shutdown()
            mongo.close()


if __name__ == "__main__":
    main()
