"""Check real archive/recovery/deletion behavior in a disposable loopback database only."""
import json
import os
from pathlib import Path
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".hisab-qa/python"))
from pymongo import MongoClient


def main():
    uri, name = os.environ["MONGO_URI"], os.environ["MONGO_DATABASE"]
    if not uri.startswith("mongodb://127.0.0.1:") or not (name.startswith("hisab_phase") or name == "hisab_ci"):
        raise RuntimeError("Archive tests require an explicitly disposable loopback database")
    base = f"http://127.0.0.1:{os.environ['PORT']}"
    mongo = MongoClient(uri, serverSelectionTimeoutMS=5000)
    db = mongo[name]

    def api(path, token=None, body=None, status=200, method=None):
        request = Request(base + path, json.dumps(body).encode() if body is not None else None,
                          {"Content-Type": "application/json", **({"Authorization": "Bearer " + token} if token else {})}, method=method)
        try:
            response = urlopen(request, timeout=45)
        except HTTPError as error:
            response = error
        with response:
            raw = response.read()
            if path.startswith("/account/data/") and 200 <= response.status < 300:
                assert response.headers.get("Cache-Control") == "no-store", "Private responses must not be cached"
            assert response.status == status, (path, response.status, status, raw[:250])
            return json.loads(raw) if raw else None

    policy = api("/privacy/policy")["version"]
    password = "Archive-disposable-password-42!"

    def account(label):
        return api("/auth/register", body={"name": label, "email": f"{label}-{uuid.uuid4().hex}@example.invalid",
            "password": password, "privacyPolicyVersion": policy}, status=201)

    alice, bob, outsider = account("archive-alice"), account("archive-bob"), account("archive-outsider")
    aid, at = alice["user"]["id"], alice["token"]
    bid, bt = bob["user"]["id"], bob["token"]
    ot = outsider["token"]
    group = api("/groups", at, {"name": "Shared archive fixture", "members": [aid]}, 201)
    gid = group["id"]
    invite = api("/requests", at, {"receiverUniqueId": bob["user"]["uniqueId"], "groupId": gid}, 201)
    api("/requests/" + invite["id"], bt, {"status": "accepted"}, method="PATCH")
    records = [{"_id": str(uuid.uuid4()), "groupId": gid, "debtorId": aid, "creditorId": bid,
        "amount": 0.125, "description": f"Historical debt {i}", "timestamp": 1000+i, "type": "EXPENSE"} for i in range(1205)]
    db.hisabEntryDocument.insert_many(records)
    api("/groups/" + gid, ot, status=403, method="DELETE")
    with ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(lambda _: api("/groups/"+gid, at, method="DELETE"), range(2)))
    archive_page = api("/account/data/archives", at)
    assert isinstance(archive_page["serverTime"], int)
    archives = archive_page["items"]
    assert all(row["expiresAt"] > archive_page["serverTime"] for row in archives)
    assert len(archives) == 1 and archives[0]["recordCount"] == 1205
    sid = archives[0]["id"]
    assert api("/v2/groups/page", at)["items"] == []
    assert db.hisabEntryDocument.count_documents({"groupId": gid}) == 1205
    assert api("/v2/groups/"+gid+"/overview", bt)["entryCount"] == 1205
    assert db.archived_hisab_records.count_documents({"snapshotId": sid, "kind": "entry"}) == 1205
    source = db.hard_copy_of_user_data.find_one({"_id": sid})
    assert source["ownerId"] == aid and source["ownerEmail"] == alice["user"]["email"]
    assert isinstance(source["expiresAt"], datetime)
    assert any(index.get("expireAfterSeconds") == 0 for index in db.hard_copy_of_user_data.list_indexes())
    assert any(index.get("expireAfterSeconds") == 0 for index in db.archived_hisab_records.list_indexes())
    assert not any("password" in str(row).lower() or alice["user"]["email"] in str(row) for row in db.archived_hisab_records.find({"snapshotId": sid}))
    assert api("/account/data/archives", ot)["items"] == []
    api(f"/account/data/archives/{sid}/recover", ot, {}, 404)
    print("PASS concurrent private deletion archives 1,205 records once and preserves other members' financial data", flush=True)

    with ThreadPoolExecutor(max_workers=2) as pool:
        jobs = list(pool.map(lambda _: api(f"/account/data/archives/{sid}/recover", at, {}, 202), range(2)))
    assert jobs[0] == jobs[1] and jobs[0]["dueAt"] >= time.time()*1000+150000
    assert db.hisab_recovery_jobs.count_documents({"_id": sid}) == 1
    handshake = os.environ.get("HISAB_QA_RESTART_HANDSHAKE")
    if handshake:
        signal = Path(handshake)
        signal.write_text(sid, encoding="utf-8")
        for _ in range(90):
            if Path(handshake + ".done").exists(): break
            time.sleep(1)
        else: raise AssertionError("Local QA server restart did not complete")
        assert db.hisab_recovery_jobs.count_documents({"_id": sid, "status": "pending"}) == 1
        print("PASS pending recovery survives a real backend process restart", flush=True)
    # Advance only this disposable job's persisted due time; the actual production delay is verified above.
    db.hisab_recovery_jobs.update_one({"_id": sid}, {"$set": {"dueAt": 0}})
    for _ in range(45):
        if db.recovered_hisabs.find_one({"_id": sid}): break
        time.sleep(1)
    else: raise AssertionError("The real background worker did not deliver the recovery")
    recovered = api("/account/data/recovered", at)["items"]
    assert len(recovered) == 1 and recovered[0]["expiresAt"] is None and recovered[0]["status"] == "ready"
    loaded, cursor = [], None
    while True:
        path = f"/account/data/recovered/{sid}/entries?limit=41" + ("&cursor="+cursor if cursor else "")
        page = api(path, at)
        loaded.extend(page["items"])
        cursor = page.get("nextCursor")
        if not cursor: break
    assert len(loaded) == len({item["id"] for item in loaded}) == 1205
    assert all(item["groupId"] == sid for item in loaded)
    assert len(api(f"/account/data/recovered/{sid}/members", at)["items"]) == 2
    api(f"/account/data/recovered/{sid}", ot, status=404)
    api(f"/account/data/recovered/{sid}/entries", ot, status=404)
    api("/entries", at, {"groupId": sid, "debtorId": aid, "creditorId": bid, "amount": 1,
        "description": "Forbidden write", "clientEntryId": str(uuid.uuid4())}, 404)
    expired = datetime.now(timezone.utc)-timedelta(seconds=1)
    db.hard_copy_of_user_data.update_one({"_id": sid}, {"$set": {"expiresAt": expired}})
    db.archived_hisab_records.update_many({"snapshotId": sid}, {"$set": {"expiresAt": expired}})
    assert api("/account/data/archives", at)["items"] == []
    api(f"/account/data/archives/{sid}/recover", at, {}, 404)
    assert api(f"/account/data/recovered/{sid}", at)["recordCount"] == 1205
    assert db.recovered_hisab_records.count_documents({"snapshotId": sid, "expiresAt": {"$exists": True}}) == 0
    print("PASS durable delayed recovery, duplicate prevention, paginated read-only copies and independent recovered lifetime", flush=True)

    api(f"/account/data/recovered/{sid}", at, status=204, method="DELETE")
    api(f"/account/data/recovered/{sid}", at, status=204, method="DELETE")
    for collection in (db.hard_copy_of_user_data, db.recovered_hisabs, db.hisab_recovery_jobs):
        assert collection.count_documents({"_id": sid}) == 0
    for collection in (db.archived_hisab_records, db.recovered_hisab_records):
        assert collection.count_documents({"snapshotId": sid}) == 0
    api(f"/account/data/archives/{sid}/recover", at, {}, 404)
    print("PASS deleting a recovered copy permanently removes its source, records and job with safe replay", flush=True)

    private = api("/groups", at, {"name": "Private fixture", "members": [aid]}, 201)["id"]
    api("/groups/"+private, at, method="DELETE")
    assert db.hisabGroupDocument.find_one({"_id": private}) is None
    own = api("/groups", at, {"name": "Exclusive account data", "members": [aid]}, 201)["id"]
    snapshots = api("/account/data/archives", at)["items"]
    private_snapshot = snapshots[0]["id"]
    api(f"/account/data/archives/{private_snapshot}/recover", at, {}, 202)
    missing = db.archived_hisab_records.find_one({"snapshotId": private_snapshot})
    db.archived_hisab_records.delete_one({"_id": missing["_id"]})
    db.hisab_recovery_jobs.update_one({"_id": private_snapshot}, {"$set": {"dueAt": 0, "attempts": 4}})
    for _ in range(45):
        job = db.hisab_recovery_jobs.find_one({"_id": private_snapshot})
        if job["status"] == "failed": break
        time.sleep(1)
    else: raise AssertionError("An incomplete recovery source was not rejected")
    assert db.recovered_hisabs.find_one({"_id": private_snapshot}) is None
    assert db.recovered_hisab_records.count_documents({"snapshotId": private_snapshot}) == 0
    db.archived_hisab_records.insert_one(missing)
    retry = api(f"/account/data/archives/{private_snapshot}/recover", at, {}, 202)
    assert retry["status"] == "pending"
    print("PASS incomplete recovery rolls back without a partial copy and supports an explicit retry", flush=True)
    # Bob's private archive is preserved, but Alice's public historical identity must be anonymized inside it.
    db.hisabGroupDocument.update_one({"_id": gid}, {"$addToSet": {"members": outsider["user"]["id"]}})
    api("/groups/"+gid, bt, method="DELETE")
    api("/account/privacy/data-requests", at, {"type": "correction"}, 400)
    assert [row["id"] for row in api("/account/data/accounts", at)] == [aid]
    db.notification_devices.insert_one({"_id": str(uuid.uuid4()), "ownerId": aid, "token": "test-only-token"})
    db.notification_outbox.insert_one({"_id": str(uuid.uuid4()), "ownerId": aid})
    api("/account/data/delete-account", at, {"accountId": bid, "password": password}, 400)
    api("/account/data/delete-account", at, {"accountId": aid, "password": "wrong"}, 400)
    assert db.userDocument.find_one({"_id": aid})
    api("/account/data/delete-account", at, {"accountId": aid, "password": password}, 204)
    api("/account/data/accounts", at, status=401)
    assert db.userDocument.find_one({"_id": aid}) is None
    assert db.hisabGroupDocument.find_one({"_id": own}) is None
    shared = db.hisabGroupDocument.find_one({"_id": gid})
    assert shared and aid not in json.dumps(shared)
    assert db.hisabEntryDocument.count_documents({"groupId": gid}) == 1205
    assert db.hisabEntryDocument.count_documents({"groupId": gid, "$or": [{"debtorId": aid}, {"creditorId": aid}]}) == 0
    assert api("/v2/groups/"+gid+"/overview", ot)["entryCount"] == 1205
    for collection in (db.hard_copy_of_user_data, db.archived_hisab_records, db.recovered_hisabs,
        db.recovered_hisab_records, db.hisab_recovery_jobs, db.notification_devices, db.notification_outbox, db.privacy_data_requests):
        assert collection.count_documents({"ownerId": aid}) == 0
    assert db.hard_copy_of_user_data.count_documents({"ownerId": bid}) == 1
    for row in db.archived_hisab_records.find({"ownerId": bid}):
        assert aid not in json.dumps(row, default=str)
        assert alice["user"]["email"] not in json.dumps(row, default=str)
        assert alice["user"]["name"] != row.get("name")
    print("PASS password-confirmed account deletion removes private data, revokes sessions and anonymizes other owners' copies", flush=True)
    # Isolated account verifies both private directories paginate without duplicates or expired sources.
    directory_owner = outsider["user"]["id"]
    directory_now = int(time.time()*1000)-1000
    directory = [{"_id": str(uuid.uuid4()), "ownerId": directory_owner, "ownerEmail": outsider["user"]["email"],
        "originalGroupId": str(uuid.uuid4()), "name": f"Directory fixture {i}", "createdAt": directory_now,
        "deletedAt": directory_now, "expiresAt": datetime.now(timezone.utc)+timedelta(days=30),
        "recordCount": 0, "memberCount": 0, "status": "available"} for i in range(75)]
    db.hard_copy_of_user_data.insert_many(directory)
    copies = [{key: value for key, value in row.items() if key not in ("expiresAt", "ownerEmail")} for row in directory]
    for row in copies: row.update(status="ready", recoveredAt=directory_now)
    db.recovered_hisabs.insert_many(copies)
    for endpoint in ("archives", "recovered"):
        seen, cursor = set(), None
        while True:
            path = "/account/data/"+endpoint+"?limit=13"+("&cursor="+cursor if cursor else "")
            page = api(path, ot)
            assert len(page["items"]) <= 13
            assert not seen.intersection(row["id"] for row in page["items"])
            seen.update(row["id"] for row in page["items"])
            cursor = page.get("nextCursor")
            if not cursor: break
            api("/account/data/"+endpoint+"?cursor="+cursor, bt, status=400)
        assert seen == {row["_id"] for row in directory}
    print("PASS archived and recovered directories paginate with stable ties and account-bound cursors", flush=True)
    mongo.close()


if __name__ == "__main__":
    main()
