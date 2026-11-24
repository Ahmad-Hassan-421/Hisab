"""Exercise bounded, authorized cursor pages and authoritative balances on disposable data."""

import argparse
from decimal import Decimal, ROUND_HALF_UP
import json
import os
from pathlib import Path
import random
import subprocess
import sys
import time
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=int(os.environ.get("PORT", "18081")))
    args = parser.parse_args()
    name = os.environ.get("MONGO_DATABASE", "")
    if name != "hisab_ci" and not name.startswith("hisab_phase"):
        raise RuntimeError("Use a disposable Hisab QA database")
    container = os.environ.get("HISAB_QA_MONGO_CONTAINER")
    database = None
    if not container:
        uri = os.environ["MONGO_URI"]
        if not uri.startswith("mongodb://127.0.0.1:"):
            raise RuntimeError("Local fixtures require loopback MongoDB")
        sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".hisab-qa/python"))
        from pymongo import MongoClient
        database = MongoClient(uri, serverSelectionTimeoutMS=5_000)[name]

    def fixture(collection, documents=None, update=None):
        if database is not None:
            if documents:
                database[collection].insert_many(documents)
            if update:
                database[collection].update_one(update[0], update[1])
            return
        target = "db.getCollection(" + json.dumps(collection) + ")"
        command = (target + ".insertMany(" + json.dumps(documents) + ")") if documents else (
            target + ".updateOne(" + json.dumps(update[0]) + "," + json.dumps(update[1]) + ")")
        completed = subprocess.run(["docker", "exec", "-i", container, "mongosh", name, "--quiet"],
                                   input=command + "\n", capture_output=True, text=True)
        if completed.returncode:
            raise RuntimeError("Disposable fixture insertion failed")

    def api(path, token=None, body=None, status=200, method=None):
        request = Request(f"http://127.0.0.1:{args.port}" + path,
                          json.dumps(body).encode() if body is not None else None,
                          {"Content-Type": "application/json", **({"Authorization": "Bearer " + token} if token else {})},
                          method=method)
        try:
            response = urlopen(request, timeout=25)
        except HTTPError as error:
            response = error
        with response:
            raw = response.read()
            assert response.status == status, (path.split("?")[0], response.status, status, raw[:200])
            return json.loads(raw) if raw else None

    policy = api("/privacy/policy")["version"]
    run = uuid.uuid4().hex

    def account(label):
        return api("/auth/register", body={"name": label, "email": f"{label}-{run}@example.invalid",
                   "password": "Disposable-pagination-password-42!", "privacyPolicyVersion": policy}, status=201)

    owner, outsider = account("owner"), account("outsider")
    actor, token = owner["user"]["id"], owner["token"]
    group = api("/groups", token, {"name": "Large pagination fixture", "members": [actor]}, 201)
    group_id = group["id"]
    now = int(time.time() * 1000) - 10_000
    member_ids = [str(uuid.UUID(int=100_000 + i)) for i in range(139)]
    fixture("userDocument", [{"_id": key, "name": f"Pagination member {i}", "email": f"fixture-{run}-{i}@example.invalid",
        "passwordHash": "unusable-test-only-hash", "uniqueId": str(700_000_000_000 + i), "profilePicUrl": "",
        "createdAt": now, "updatedAt": now} for i, key in enumerate(member_ids)])
    fixture("hisabGroupDocument", update=({"_id": group_id}, {"$set": {"members": [actor] + member_ids}}))
    groups = [{"_id": str(uuid.UUID(int=200_000 + i)), "name": f"Paged Hisab {i}", "members": [actor],
               "exMembers": [], "createdAt": now} for i in range(1237)]
    groups[-1].pop("createdAt")
    fixture("hisabGroupDocument", groups)
    first = api("/v2/groups/page?limit=41", token)
    assert len(first["items"]) == 41 and first["nextCursor"]
    assert all("members" not in item and "memberCount" in item for item in first["items"])
    api("/v2/groups/page?" + urlencode({"cursor": first["nextCursor"]}), outsider["token"], status=400)
    api("/v2/groups/page?limit=101", token, status=400)
    api("/v2/groups/page?cursor=malformed", token, status=400)
    time.sleep(0.01)
    newer = api("/groups", token, {"name": "Created between pages", "members": [actor]}, 201)

    def remaining(base, initial):
        results = list(initial["items"])
        cursor = initial.get("nextCursor")
        seen_cursors = set()
        while cursor:
            assert cursor not in seen_cursors
            seen_cursors.add(cursor)
            page = api(base + ("&" if "?" in base else "?") + urlencode({"cursor": cursor, "limit": 41}), token)
            assert len(page["items"]) <= 41
            results.extend(page["items"])
            cursor = page.get("nextCursor")
        return results

    loaded = remaining("/v2/groups/page", first)
    ids = [item["id"] for item in loaded]
    assert len(ids) == len(set(ids)) == 1238
    assert newer["id"] not in ids
    assert set(ids) == {group_id} | {item["_id"] for item in groups}
    print("PASS 1,238 Hisabs, timestamp ties, legacy dates, concurrent insertion and account-bound cursors", flush=True)
    fixture("hisabGroupDocument", update=({"_id": groups[0]["_id"]}, {"$set": {"members": [actor, outsider["user"]["id"]]}}))
    suggestions = api(f"/v2/groups/{group_id}/suggested-members", token)
    assert [item["id"] for item in suggestions["items"]] == [outsider["user"]["id"]]
    assert suggestions["items"][0]["email"] == ""
    members = remaining(f"/v2/groups/{group_id}/members", api(f"/v2/groups/{group_id}/members?limit=41", token))
    assert {member["id"] for member in members} == {actor, *member_ids}
    assert all(member["email"] == "" and "passwordHash" not in member for member in members)
    api(f"/v2/groups/{group_id}/members", outsider["token"], status=403)
    print("PASS bounded member profiles, no password/email leakage and membership authorization", flush=True)
    invitations = [{"_id": str(uuid.UUID(int=600_000 + i)), "senderId": member_ids[i % len(member_ids)],
                    "senderName": f"Member {i}", "senderEmail": "private-fixture@example.invalid", "receiverId": actor,
                    "groupId": group_id, "groupName": "Friends", "status": "pending", "createdAt": now} for i in range(517)]
    fixture("hisabRequestDocument", invitations)
    request_page = api("/v2/requests/page?limit=41", token)
    pending = remaining("/v2/requests/page", request_page)
    assert len(pending) == len({item["id"] for item in pending}) == 517
    assert all(item.get("senderEmail", "") == "" for item in pending)
    api("/v2/requests/page?" + urlencode({"cursor": request_page["nextCursor"]}), outsider["token"], status=400)
    assert api("/v2/requests/page", outsider["token"])["items"] == []
    print("PASS 517 paged invitations, private account scoping and bounded member suggestions", flush=True)

    rng = random.Random(121)
    entries, expected_exact, expected_legacy = [], {}, {}
    for i in range(5147):
        other = member_ids[i % len(member_ids)]
        debtor, creditor = (other, actor) if i % 7 == 0 else (actor, other)
        amount = rng.randint(1, 5000) / 1000
        if i % 113 == 0:
            amount = 0.0005 if i % 2 == 0 else 1 / 3
        entry = {"_id": str(uuid.UUID(int=300_000 + i)), "groupId": group_id, "debtorId": debtor,
                 "creditorId": creditor, "amount": amount, "description": f"Pagination record {i}",
                 "timestamp": now, "type": "REPAYMENT" if i % 7 == 0 else "EXPENSE"}
        entries.append(entry)
        pair = tuple(sorted((debtor, creditor)))
        value = Decimal(str(amount))
        exact = value.as_tuple().exponent >= -3
        target = expected_exact if exact else expected_legacy
        target[pair] = target.get(pair, Decimal(0)) + (value if debtor == pair[0] else -value)
    for offset in range(0, len(entries), 1000):
        fixture("hisabEntryDocument", entries[offset:offset + 1000])
    expected = {pair: expected_exact.get(pair, Decimal(0)) + expected_legacy.get(pair, Decimal(0)).quantize(
        Decimal("0.001"), rounding=ROUND_HALF_UP) for pair in set(expected_exact) | set(expected_legacy)}
    overview = api(f"/v2/groups/{group_id}/overview", token)
    observed = {(pair["firstId"], pair["secondId"]): Decimal(pair["amount"]) for pair in overview["pairs"]}
    assert observed == expected, [(key, observed.get(key), value) for key, value in expected.items() if observed.get(key) != value][:3]
    assert overview["entryCount"] == len(entries) and overview["self"]["id"] == actor
    first_history = api("/v2/entries/page?" + urlencode({"groupId": group_id, "limit": 41}), token)
    history = remaining("/v2/entries/page?" + urlencode({"groupId": group_id}), first_history)
    assert len(history) == len({entry["id"] for entry in history}) == len(entries)
    assert {entry["id"] for entry in history} == {entry["_id"] for entry in entries}
    payments = remaining(f"/v2/entries/page?groupId={group_id}&kind=payments",
                         api(f"/v2/entries/page?groupId={group_id}&kind=payments&limit=41", token))
    assert len(payments) == sum(entry["type"] == "REPAYMENT" for entry in entries)
    assert all(entry["type"] == "REPAYMENT" for entry in payments)
    api(f"/v2/entries/page?groupId={group_id}", outsider["token"], status=403)
    api(f"/v2/groups/{group_id}/overview", outsider["token"], status=403)
    print("PASS 5,147 history records: complete unique pages, payment filter and exact/legacy balance parity", flush=True)
    payload = {"groupId": group_id, "debtorId": actor, "creditorId": member_ids[0], "amount": 0.001,
               "description": "Bounded batch fixture", "type": "EXPENSE"}
    batch = [{**payload, "clientEntryId": str(uuid.uuid4())} for _ in range(1000)]
    assert len(api("/entries/batch", token, {"entries": batch}, 201)) == 1000
    assert len(api("/entries/batch", token, {"entries": batch}, 201)) == 1000
    updated = api(f"/v2/groups/{group_id}/overview", token)
    assert updated["entryCount"] == len(entries) + 1000
    pair = tuple(sorted((actor, member_ids[0])))
    changed = {(item["firstId"], item["secondId"]): Decimal(item["amount"]) for item in updated["pairs"]}
    assert changed[pair] == expected[pair] + (Decimal(1) if actor == pair[0] else Decimal(-1))
    api("/v2/entries/page?" + urlencode({"groupId": group_id, "cursor": first_history["nextCursor"]}), token, status=409)
    api("/entries/batch", token, {"entries": batch + [batch[0]]}, 400)
    print("PASS 1,000-entry batch, idempotent replay, bounded input and stale-history rejection", flush=True)


if __name__ == "__main__":
    main()
