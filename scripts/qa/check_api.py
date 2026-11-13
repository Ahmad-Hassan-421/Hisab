import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import hmac
import json
import os
from pathlib import Path
import socket
import time
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--extended", action="store_true")
    args = parser.parse_args()
    base = f"http://127.0.0.1:{args.port}"
    public_origin = os.environ.get("RENDER_EXTERNAL_URL", "").rstrip("/")
    run = uuid.uuid4().hex[:12]
    password = "QA-only-password-42!"
    passed = failed = 0

    def request(method, path, token=None, body=None, content_type="application/json"):
        headers = {"Content-Type": content_type}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if body is not None and not isinstance(body, bytes):
            body = json.dumps(body).encode()
        req = urllib.request.Request(base + path, method=method, headers=headers, data=body)
        try:
            response = urllib.request.urlopen(req, timeout=12)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            raw = response.read()
            try:
                data = json.loads(raw)
            except (ValueError, UnicodeDecodeError):
                data = raw
            return response.status, data

    def check(label, condition, observed=""):
        nonlocal passed, failed
        passed += int(bool(condition))
        failed += int(not condition)
        print(f"{'PASS' if condition else 'FAIL'} {label} {observed}", flush=True)

    def expect(label, status, method, path, token=None, body=None):
        actual, data = request(method, path, token, body)
        check(label, actual == status, f"(HTTP {actual}, expected {status})")
        return data

    def concurrent(operation):
        with ThreadPoolExecutor(max_workers=2) as executor:
            return list(executor.map(operation, range(2)))

    def signed_jwt(claims):
        secret = os.environ["JWT_SECRET"]
        def encode(raw):
            return base64.urlsafe_b64encode(raw).rstrip(b"=")
        head = encode(b'{"alg":"HS256","typ":"JWT"}')
        payload = encode(json.dumps(claims).encode())
        message = head + b"." + payload
        return (message + b"." + encode(hmac.digest(secret.encode(), message, hashlib.sha256))).decode()

    if args.extended:
        ready = expect("database-backed readiness", 200, "GET", "/healthz")
        check("readiness body is minimal", ready == b"ok")
        expect("invalid JSON", 400, "POST", "/auth/register", body=b"{")

    users = []
    for who in ("alice", "bob", "outsider"):
        credentials = {"name": who, "email": f"{who}-{run}@example.invalid", "password": password}
        created = expect(f"register {who}", 201, "POST", "/auth/register", body=credentials)
        if not isinstance(created, dict) or "user" not in created:
            raise RuntimeError("Failed to create isolated fixtures")
        check(f"registration hides {who}'s hash", "passwordHash" not in created["user"])
        users.append((credentials, created["token"], created["user"]))
    (alice, a, user_a), (_, b, user_b), (_, x, user_x) = users
    aid, bid, xid = (user["id"] for user in (user_a, user_b, user_x))

    expect("duplicate email", 409, "POST", "/auth/register", body=alice)
    if args.extended:
        racing = {**alice, "email": f"race-{run}@example.invalid"}
        outcomes = concurrent(lambda _: request("POST", "/auth/register", body=racing)[0])
        check("concurrent registration has one winner", sorted(outcomes) == [201, 409], str(outcomes))
    expect("malformed registration", 400, "POST", "/auth/register", body={})
    expect("wrong password", 401, "POST", "/auth/login", body={**alice, "password": "wrong"})
    login = expect("valid login", 200, "POST", "/auth/login", body=alice)
    check("login hides hash", isinstance(login, dict) and "passwordHash" not in login.get("user", {"passwordHash": ""}))
    expect("missing auth", 401, "GET", f"/groups?userId={aid}")
    expect("invalid auth", 401, "GET", f"/groups?userId={aid}", "not-a-jwt")
    if args.extended and "JWT_SECRET" in os.environ:
        claims = {"iss": "hisab-server", "aud": "hisab-app", "userId": aid}
        expired = signed_jwt({**claims, "exp": int(time.time()) - 60})
        expect("expired token rejected", 401, "GET", f"/groups?userId={aid}", expired)
        no_subject = signed_jwt({**claims, "userId": "", "exp": int(time.time()) + 60})
        expect("blank token subject rejected", 401, "GET", f"/groups?userId={aid}", no_subject)
    profile = expect("own profile lookup", 200, "GET", f"/users/{aid}", a)
    check("profile hides hash", isinstance(profile, dict) and "passwordHash" not in profile)
    check("default profile uses the configured avatar", isinstance(profile, dict) and
          profile.get("profilePicUrl") == "https://ssl.gstatic.com/images/branding/product/1x/avatar_circle_blue_512dp.png")
    expect("lookup by invite ID", 200, "GET", f"/users/by-unique-id/{user_b['uniqueId']}", a)
    profile_update = {"name": "Alice QA", "themePreference": "dark", "profilePicUrl": "/static/default_profile.png"}
    expect("reject cross-user profile update", 403, "PUT", f"/users/{aid}", x, profile_update)
    expect("edit own profile", 200, "PUT", f"/users/{aid}", a, profile_update)

    group = expect("create group", 201, "POST", "/groups", a, {"name": f"QA {run}", "members": [aid]})
    gid = group["id"]
    expect("list own groups", 200, "GET", f"/groups?userId={aid}", a)
    expect("deny listing another user's groups", 403, "GET", f"/groups?userId={aid}", x)
    invite = {"receiverUniqueId": user_b["uniqueId"], "groupId": gid}
    expect("deny outsider invite", 403, "POST", "/requests", x, invite)
    if args.extended:
        connection = socket.create_connection(("127.0.0.1", args.port), timeout=5)
        socket_input = connection.makefile("rb")
        ws_key = base64.b64encode(os.urandom(16)).decode()
        connection.sendall(
            (f"GET /ws HTTP/1.1\r\nHost: 127.0.0.1:{args.port}\r\nUpgrade: websocket\r\n"
             f"Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: {ws_key}\r\n"
             f"Authorization: Bearer {b}\r\n\r\n").encode()
        )
        check("authenticated socket upgrades", b" 101 " in socket_input.readline())
        while socket_input.readline().strip():
            pass
    invite_created = expect("send invitation", 201, "POST", "/requests", a, invite)
    rid = invite_created["id"]
    if args.extended:
        with connection, socket_input:
            prefix = socket_input.read(2)
            event = b"{}"
            if len(prefix) == 2 and prefix[0] & 15 == 1:
                length = prefix[1] & 127
                if length in (126, 127):
                    length = int.from_bytes(socket_input.read(2 if length == 126 else 8), "big")
                event = socket_input.read(length)
            check("receiver receives WebSocket invitation event", json.loads(event).get("type") == "NEW_REQUEST")
        retries = concurrent(lambda _: request("POST", "/requests", a, invite))
        check("concurrent invitation replays retain the same request",
              all(status == 201 and result.get("id") == rid for status, result in retries))
    expect("list own invitations", 200, "GET", f"/requests?receiverId={bid}", b)
    expect("deny listing another user's invitations", 403, "GET", f"/requests?receiverId={bid}", x)
    expect("deny outsider deciding invitation", 403, "PATCH", f"/requests/{rid}", x, {"status": "declined"})
    expect("reject unsupported invitation state", 400, "PATCH", f"/requests/{rid}", b, {"status": "unknown"})
    expect("accept invitation", 200, "PATCH", f"/requests/{rid}", b, {"status": "accepted"})
    if args.extended:
        expect("acceptance replay is safe", 200, "PATCH", f"/requests/{rid}", b, {"status": "accepted"})
        expect("decline after acceptance is rejected", 409, "PATCH", f"/requests/{rid}", b, {"status": "declined"})
    groups = expect("joined group appears in recipient list", 200, "GET", f"/groups?userId={bid}", b)
    check("recipient is a group member", isinstance(groups, list) and any(g["id"] == gid and bid in g["members"] for g in groups))

    entry = {"groupId": gid, "debtorId": bid, "creditorId": aid, "amount": 1.234, "description": "fixture expense"}
    if args.extended:
        entry["clientEntryId"] = str(uuid.uuid4())
    expect("deny outsider reading ledger", 403, "GET", f"/entries?groupId={gid}", x)
    expect("deny outsider writing ledger", 403, "POST", "/entries", x, entry)
    expect("reject negative amount", 400, "POST", "/entries", a, {**entry, "amount": -1})
    expect("reject unrelated entry participants", 400, "POST", "/entries", a, {**entry, "debtorId": xid})
    if args.extended:
        expect("reject precision beyond thousandths", 400, "POST", "/entries", a, {**entry, "amount": 1.2345})
        for decimal in (b"1.23400000000000000001", b"1000000000.000000001"):
            body = json.dumps({**entry, "amount": "DECIMAL"}).encode().replace(b'"DECIMAL"', decimal)
            expect("validate the exact decimal before storage", 400, "POST", "/entries", a, body)
        invalid_batch = [entry, {**entry, "amount": -1, "clientEntryId": str(uuid.uuid4())}]
        expect("reject entire invalid batch", 400, "POST", "/entries/batch", a, {"entries": invalid_batch})
        check("invalid batch leaves ledger empty", request("GET", f"/entries?groupId={gid}", a) == (200, []))
    created = expect("create expense", 201, "POST", "/entries", a, entry)
    eid = created["id"]
    repayment = {**entry, "debtorId": aid, "creditorId": bid, "type": "REPAYMENT"}
    if args.extended:
        retries = concurrent(lambda _: request("POST", "/entries", a, entry))
        check("concurrent entry replays retain the same ID",
              all(status == 201 and result.get("id") == eid for status, result in retries))
        expect("reject reused ID with changed amount", 409, "POST", "/entries", a, {**entry, "amount": 2})
        expect("prevent clearing an unsettled ledger", 409, "DELETE", f"/entries/group/{gid}", a)
        repayment["clientEntryId"] = str(uuid.uuid4())
        overpayment = {**repayment, "amount": 1.235, "clientEntryId": str(uuid.uuid4())}
        status, result = request("POST", "/entries", a, overpayment)
        check("reject a repayment larger than the outstanding balance", status == 409, f"(HTTP {status})")
        if status == 201:
            expect("remove unexpected overpayment fixture", 200, "DELETE", f"/entries/{result['id']}", a)
        excessive_batch = [
            {**repayment, "amount": 0.700, "clientEntryId": str(uuid.uuid4())}
            for _ in range(2)
        ]
        status, result = request("POST", "/entries/batch", a, {"entries": excessive_batch})
        check("reject a batch whose repayments together exceed the balance", status == 409, f"(HTTP {status})")
        if status == 201:
            for item in result:
                expect("remove unexpected repayment fixture", 200, "DELETE", f"/entries/{item['id']}", a)
        check("rejected repayments leave the ledger intact", len(request("GET", f"/entries?groupId={gid}", a)[1]) == 1)
        competing = [
            {**repayment, "clientEntryId": str(uuid.uuid4())}
            for _ in range(2)
        ]
        outcomes = concurrent(lambda index: request("POST", "/entries/batch", a, {"entries": [competing[index]]}))
        check("concurrent full repayments post only once", sorted(status for status, _ in outcomes) == [201, 409])
        winner = next(index for index, (status, _) in enumerate(outcomes) if status == 201)
        for index, (status, result) in enumerate(outcomes):
            if status == 201 and index != winner:
                expect("remove unexpected concurrent repayment fixture", 200, "DELETE", f"/entries/{result[0]['id']}", a)
        expect("repayment replay succeeds after settlement", 201, "POST", "/entries/batch", a, {"entries": [competing[winner]]})
    else:
        expect("create repayment batch", 201, "POST", "/entries/batch", a, {"entries": [repayment]})
    history = expect("list group ledger", 200, "GET", f"/entries?groupId={gid}", b)
    check("ledger round-trips the amount", isinstance(history, list) and any(e["id"] == eid and e["amount"] == 1.234 for e in history))
    if args.extended:
        check("retries did not create duplicate entries", isinstance(history, list) and len(history) == 2)

    png = Path("hisab-server/src/main/resources/static/default_profile.png").read_bytes()
    boundary = f"qa-{run}"
    upload = (f'--{boundary}\r\nContent-Disposition: form-data; name="image"; filename="sample.png"\r\n'
              "Content-Type: image/png\r\n\r\n").encode() + png + f"\r\n--{boundary}--\r\n".encode()
    upload_type = f"multipart/form-data; boundary={boundary}"
    status, first = request("POST", "/upload/profile-pic", a, upload, upload_type)
    check("upload profile image", status == 200, f"(HTTP {status})")
    status, second = request("POST", "/upload/profile-pic", b, upload, upload_type)
    check("each upload gets a distinct path", status == 200 and first != second)
    url = first.get("url", "") if isinstance(first, dict) else ""
    portable = url.startswith(f"{public_origin}/static/uploads/")
    check("upload returns a portable path", portable)
    if portable:
        status, downloaded = request("GET", url.removeprefix(public_origin))
        check("profile image is served unchanged", status == 200 and downloaded == png)
    if args.extended:
        non_image = upload.replace(png, b"this is not a valid image")
        status, _ = request("POST", "/upload/profile-pic", a, non_image, upload_type)
        check("non-image upload rejected", status == 415)
        oversized = upload.replace(png, png + bytes(5 * 1024 * 1024))
        status, _ = request("POST", "/upload/profile-pic", a, oversized, upload_type)
        check("oversize upload rejected", status == 413)
        expect("reject device-local image URI", 400, "PUT", f"/users/{aid}", a,
               {**profile_update, "profilePicUrl": "file:///data/local/tmp/fixture.png"})

    expect("deny outsider deleting entry", 403, "DELETE", f"/entries/{eid}", x)
    expect("deny outsider clearing ledger", 403, "DELETE", f"/entries/group/{gid}", x)
    expect("clear ledger as member", 200, "DELETE", f"/entries/group/{gid}", a)
    empty = expect("reload cleared ledger", 200, "GET", f"/entries?groupId={gid}", b)
    check("ledger is empty after clear", empty == [])
    if args.extended:
        legacy = {key: value for key, value in entry.items() if key != "clientEntryId"}
        expect("retain support for fractional legacy entries", 201, "POST", "/entries", a, {**legacy, "amount": 1.0025})
    expect("leave group", 200, "POST", f"/groups/{gid}/leave", b)
    if args.extended:
        former_member_repayment = {**repayment, "amount": 1.003, "clientEntryId": str(uuid.uuid4())}
        expect("settle a rounded balance with a former member", 201, "POST", "/entries", a, former_member_repayment)
        expect("clear a settled legacy balance", 200, "DELETE", f"/entries/group/{gid}", a)
        expect("reject a new expense for a former member", 400, "POST", "/entries", a,
               {**entry, "clientEntryId": str(uuid.uuid4())})
        rejoin = expect("reinvite the former member", 201, "POST", "/requests", a, invite)
        expect("rejoin group", 200, "PATCH", f"/requests/{rejoin['id']}", b, {"status": "accepted"})
        direct = {**repayment, "type": "DIRECT_DEBT", "amount": 2, "clientEntryId": str(uuid.uuid4())}
        expect("record debt owed to another member", 201, "POST", "/entries", a, direct)
        expect("creditor leaves with a balance", 200, "POST", f"/groups/{gid}/leave", b)
        paid = {**entry, "type": "REPAYMENT", "amount": 2, "clientEntryId": str(uuid.uuid4())}
        expect("record payment to a former member", 201, "POST", "/entries", a, paid)
        expect("clear after paying a former member", 200, "DELETE", f"/entries/group/{gid}", a)
    expect("deny former member reading ledger", 403, "GET", f"/entries?groupId={gid}", b)
    expect("deny outsider deleting group", 403, "DELETE", f"/groups/{gid}", x)
    expect("delete group as member", 200, "DELETE", f"/groups/{gid}", a)
    print(f"{passed} passed, {failed} failed")
    return int(bool(failed))


if __name__ == "__main__":
    raise SystemExit(main())
