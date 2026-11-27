"""Verify notification ownership and atomic event generation using disposable loopback data."""
import json
import os
from pathlib import Path
import secrets
import sys
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def main():
    uri, name = os.environ["MONGO_URI"], os.environ["MONGO_DATABASE"]
    if not uri.startswith("mongodb://127.0.0.1:") or not (name.startswith("hisab_phase") or name == "hisab_ci"):
        raise RuntimeError("Use a disposable loopback database")
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".hisab-qa/python"))
    from pymongo import MongoClient
    db = MongoClient(uri, serverSelectionTimeoutMS=5000)[name]
    base = f"http://127.0.0.1:{os.environ['PORT']}"
    owners = {}

    def api(path, token=None, body=None, method=None, status=200, expected_owner=None):
        header = {"Content-Type": "application/json"}
        if token:
            header.update({"Authorization": "Bearer " + token, "X-Hisab-Account": expected_owner or owners.get(token, "")})
        request = Request(base + path, json.dumps(body).encode() if body is not None else None, header, method=method)
        try:
            response = urlopen(request, timeout=30)
        except HTTPError as error:
            response = error
        with response:
            raw = response.read()
            assert response.status == status, (path, response.status, status, raw[:150])
            return json.loads(raw) if raw else None

    policy = api("/privacy/policy")["version"]
    password = "Disposable-notification-password-42!"

    def account(label):
        value = api("/auth/register", body={"name": label, "email": f"{label}-{uuid.uuid4().hex}@example.invalid",
            "password": password, "privacyPolicyVersion": policy}, status=201)
        owners[value["token"]] = value["user"]["id"]
        return value["user"]["id"], value["token"], value["user"]["uniqueId"]

    aid, at, _ = account("push-alice")
    bid, bt, uid = account("push-bob")
    oid, ot, _ = account("push-outsider")
    api("/notifications/preferences", status=401)
    assert api("/notifications/preferences", bt)["preferences"]["previews"] is False
    prefs = {"debts": True, "payments": True, "invitations": True, "recovery": True, "updates": True, "previews": True}
    api("/notifications/preferences", bt, prefs, method="PUT", expected_owner=aid, status=409)
    api("/notifications/preferences", bt, prefs, method="PUT")
    assert api("/notifications/preferences", at)["preferences"]["previews"] is False
    proof = {"installationId": str(uuid.uuid4()), "installationSecret": secrets.token_hex(32)}
    device = {**proof, "token": "disposable-fcm-token-" + uuid.uuid4().hex, "packageName": "com.example.hisab.qa",
        "versionCode": 4, "permissionGranted": True, "bindingRevision": 1}
    api("/notifications/devices", bt, device, method="PUT", status=204)
    api("/notifications/devices", ot, {**device, "installationSecret": secrets.token_hex(32), "bindingRevision": 2}, method="PUT", status=403)
    api("/notifications/devices", ot, {**device, "bindingRevision": 2}, method="PUT", status=204)
    api("/notifications/devices", bt, device, method="PUT", status=409)
    api("/notifications/devices/revoke", body={**proof, "bindingRevision": 1}, status=204)
    assert db.notification_devices.find_one({"_id": proof["installationId"]})["ownerId"] == oid
    api("/notifications/devices", bt, {**device, "bindingRevision": 3}, method="PUT", status=204)
    api("/notifications/devices/revoke", body={**proof, "installationSecret": secrets.token_hex(32), "bindingRevision": 4}, status=204)
    assert db.notification_devices.count_documents({"_id": proof["installationId"]}) == 1
    api("/notifications/devices/revoke", body={**proof, "bindingRevision": 4}, status=204)
    assert db.notification_devices.count_documents({"_id": proof["installationId"]}) == 0
    print("PASS private preferences, binding proof, delayed registration/revocation and cross-account write rejection", flush=True)

    group = api("/groups", at, {"name": "Notification ledger", "members": [aid]}, status=201)
    gid = group["id"]
    invitation = api("/requests", at, {"groupId": gid, "receiverUniqueId": uid}, status=201)
    assert db.notification_outbox.count_documents({"ownerId": bid, "kind": "INVITATION", "sourceId": invitation["id"]}) == 1
    api("/requests", at, {"groupId": gid, "receiverUniqueId": uid}, status=201)
    assert db.notification_outbox.count_documents({"ownerId": bid, "kind": "INVITATION", "sourceId": invitation["id"]}) == 1
    api("/requests/" + invitation["id"], bt, {"status": "accepted"}, method="PATCH")
    expense = {"groupId": gid, "debtorId": bid, "creditorId": aid, "amount": 100.125,
        "description": "Disposable exact notification debt", "clientEntryId": str(uuid.uuid4()), "type": "EXPENSE"}
    entry = api("/entries", at, expense, status=201)
    api("/entries", at, expense, status=201)
    assert db.notification_outbox.count_documents({"kind": "DEBT", "sourceId": entry["id"], "ownerId": bid}) == 1
    assert api("/v2/entries/" + entry["id"] + "?groupId=" + gid, bt)["id"] == entry["id"]
    api("/v2/entries/" + entry["id"] + "?groupId=" + gid, ot, status=403)
    payment = {**expense, "debtorId": aid, "creditorId": bid, "amount": 30.001, "clientEntryId": str(uuid.uuid4()), "type": "REPAYMENT"}
    paid = api("/entries", at, payment, status=201)
    notification = db.notification_outbox.find_one({"kind": "PAYMENT", "sourceId": paid["id"], "ownerId": bid})
    assert "70.124" in notification["body"] and "30.001" in notification["body"]
    count = db.notification_outbox.count_documents({"ownerId": bid})
    api("/entries", at, {**payment, "clientEntryId": str(uuid.uuid4()), "amount": 99}, status=409)
    assert db.notification_outbox.count_documents({"ownerId": bid}) == count
    api("/entries/" + paid["id"], at, method="DELETE")
    api("/v2/entries/" + paid["id"] + "?groupId=" + gid, bt, status=404)
    print("PASS atomic invitation/debt/payment events, idempotent replay, authoritative remaining debt and failed-write rollback", flush=True)
    api("/notifications/devices", bt, {**device, "bindingRevision": 5}, method="PUT", status=204)
    api("/account/data/delete-account", bt, {"accountId": bid, "password": password}, status=204)
    for collection in ("notification_devices", "notification_outbox", "notification_deliveries", "notification_preferences"):
        assert db[collection].count_documents({"ownerId": bid}) == 0
    print("PASS permanent account deletion removes device registrations, preferences and queued/delivery records", flush=True)


if __name__ == "__main__":
    main()
