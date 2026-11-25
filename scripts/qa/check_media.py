"""Verify durable private images only against a disposable loopback Hisab database."""
import argparse
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import sys
import time
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--restart-file", type=Path)
    parser.add_argument("--verify-restart", action="store_true")
    args = parser.parse_args()
    uri, name = os.environ["MONGO_URI"], os.environ["MONGO_DATABASE"]
    if not uri.startswith("mongodb://127.0.0.1:") or not (name.startswith("hisab_phase") or name == "hisab_ci"):
        raise RuntimeError("Use an explicitly disposable loopback database")
    base = f"http://127.0.0.1:{os.environ['PORT']}"
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / ".hisab-qa/python"))
    from pymongo import MongoClient
    db = MongoClient(uri, serverSelectionTimeoutMS=5000)[name]

    def api(path, token=None, body=None, method=None, status=200, headers=None):
        content_type = "application/json"
        if isinstance(body, dict):
            body = json.dumps(body).encode()
        elif isinstance(body, bytes):
            content_type = "multipart/form-data; boundary=hisab-media-fixture"
        request = Request(base + path, body, {"Content-Type": content_type,
            **({"Authorization": "Bearer " + token} if token else {}), **(headers or {})}, method=method)
        try:
            response = urlopen(request, timeout=30)
        except HTTPError as error:
            response = error
        with response:
            raw = response.read()
            assert response.status == status, (path, response.status, status, raw[:150])
            if response.headers.get("Content-Type", "").startswith("image/"):
                assert response.headers["Cache-Control"].startswith("private,")
                assert response.headers["Vary"] == "Authorization"
                return raw
            return json.loads(raw) if raw else None

    if args.verify_restart:
        fixture = json.loads(args.restart_file.read_text())
        assert api(fixture["path"], fixture["token"]).startswith(b"\xff\xd8")
        args.restart_file.unlink()
        print("PASS MongoDB image bytes and authorization survive a real backend process restart", flush=True)
        return

    policy = api("/privacy/policy")["version"]
    password = "Disposable-media-password-42!"

    def account(label):
        return api("/auth/register", body={"name": label, "email": f"{label}-{uuid.uuid4().hex}@example.invalid",
            "password": password, "privacyPolicyVersion": policy}, status=201)

    alice, bob, outsider = [account("media-" + name) for name in ("alice", "bob", "outsider")]
    aid, at = alice["user"]["id"], alice["token"]
    bid, bt, ot = bob["user"]["id"], bob["token"], outsider["token"]
    group = api("/groups", at, {"name": "Media fixture", "members": [aid]}, status=201)
    gid = group["id"]
    invitation = api("/requests", at, {"receiverUniqueId": bob["user"]["uniqueId"], "groupId": gid}, status=201)
    api("/requests/" + invitation["id"], bt, {"status": "accepted"}, method="PATCH")
    png = Path("hisab-server/src/main/resources/static/default_profile.png").read_bytes()

    def multipart(image):
        return (b'--hisab-media-fixture\r\nContent-Disposition: form-data; name="image"; filename="fixture.png"\r\n'
            b'Content-Type: image/png\r\n\r\n' + image + b'\r\n--hisab-media-fixture--\r\n')

    def upload(path, token):
        result = api(path, token, multipart(png))["url"]
        assert result.startswith("/media/")
        return result

    portrait = upload("/upload/profile-pic", at)
    image = api(portrait, at)
    assert image.startswith(b"\xff\xd8") and len(image) <= 512 * 1024
    assert api(portrait, bt) == image
    api(portrait, status=401)
    api(portrait, ot, status=404)
    api(portrait, bt, status=304, headers={"If-None-Match": '"' + portrait.split("/")[-1] + '"'})
    assert api("/users/" + aid, at)["profilePicUrl"] == portrait
    api("/users/me", at, {"name": "New name", "themePreference": "dark"}, method="PATCH")
    assert api("/users/" + aid, at)["profilePicUrl"] == portrait
    api("/users/" + bid, bt, {"name": "Bad attachment", "themePreference": "light", "profilePicUrl": portrait}, method="PUT", status=400)
    api("/upload/profile-pic", at, multipart(b"\x89PNGnot-a-real-image"), status=415)
    api("/upload/profile-pic", at, multipart(bytes(5 * 1024 * 1024 + 1)), status=413)
    print("PASS normalized JPEG, authenticated shared-profile access, validators, field patches and invalid/oversized uploads", flush=True)

    picture = upload(f"/groups/{gid}/picture", bt)
    assert api(picture, at).startswith(b"\xff\xd8")
    assert api(f"/v2/groups/{gid}", at)["pictureUrl"] == picture
    assert api("/v2/groups/page", bt)["items"][0]["pictureUrl"] == picture
    meta = api(f"/groups/{gid}/pictures?members={aid},{bid}", bt)
    assert meta["pictureUrl"] == picture and meta["members"][aid] == portrait
    api(f"/groups/{gid}/picture", ot, multipart(png), status=403)
    api(f"/groups/{gid}/picture", ot, method="DELETE", status=403)
    api(f"/groups/{gid}/pictures", ot, status=403)
    assert api("/media/access", ot, {"ids": [picture.split("/")[-1]]})["allowed"] == []
    api("/media/access", at, {"ids": [str(uuid.uuid4())] * 51}, status=400)

    # A private snapshot retains its immutable picture even after another member replaces it.
    api(f"/groups/{gid}", at, method="DELETE")
    snapshot = api("/account/data/archives", at)["items"][0]
    sid = snapshot["id"]
    assert snapshot["pictureUrl"] == picture
    replacement = upload(f"/groups/{gid}/picture", bt)
    assert replacement != picture and api(picture, at).startswith(b"\xff\xd8")
    api(picture, bt, status=404, headers={"If-None-Match": '"' + picture.split("/")[-1] + '"'})
    assert api("/media/access", bt, {"ids": [picture.split("/")[-1]]})["allowed"] == []
    api(f"/groups/{gid}/picture", at, multipart(png), status=403)
    api(f"/account/data/archives/{sid}/recover", at, {}, status=202)
    db.hisab_recovery_jobs.update_one({"_id": sid}, {"$set": {"dueAt": 0}})
    for _ in range(45):
        if db.recovered_hisabs.count_documents({"_id": sid}):
            break
        time.sleep(0.5)
    else:
        raise AssertionError("Recovery worker did not deliver media fixture")
    db.hard_copy_of_user_data.delete_one({"_id": sid})
    db.archived_hisab_records.delete_many({"snapshotId": sid})
    db.media_references.delete_many({"kind": "archive", "subjectId": sid})
    assert api(picture, at).startswith(b"\xff\xd8")
    assert api(f"/account/data/recovered/{sid}", at)["pictureUrl"] == picture
    api(f"/groups/{sid}/picture", at, multipart(png), status=403)
    api(f"/account/data/recovered/{sid}", at, method="DELETE", status=204)
    api(picture, at, status=404)
    print("PASS active-member picture changes, former-member revocation and independent read-only recovered-picture lifetime", flush=True)

    # Ownership follows the group, so deleting its uploader cannot delete the remaining member's picture.
    shared = api("/groups", at, {"name": "Uploader deletion fixture", "members": [aid]}, status=201)
    invite = api("/requests", at, {"receiverUniqueId": bob["user"]["uniqueId"], "groupId": shared["id"]}, status=201)
    api("/requests/" + invite["id"], bt, {"status": "accepted"}, method="PATCH")
    uploaded_by_alice = upload(f"/groups/{shared['id']}/picture", at)
    api("/account/data/delete-account", at, {"accountId": aid, "password": password}, status=204)
    assert db.media_assets.count_documents({"kind": "profile", "subjectId": aid}) == 0
    assert api(replacement, bt).startswith(b"\xff\xd8")
    assert api(uploaded_by_alice, bt).startswith(b"\xff\xd8")
    api(portrait, bt, status=404)
    assert db.media_references.count_documents({"ownerId": aid}) == 0
    if args.restart_file:
        args.restart_file.write_text(json.dumps({"path": replacement, "token": bt}))
    print("PASS permanent personal-image deletion preserves independently owned shared-group media", flush=True)


if __name__ == "__main__":
    main()
