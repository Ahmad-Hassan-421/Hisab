# Migrate from the local API to Render

## Rehearse and back up

1. Identify the database and upload directory used by the current service. The original server used MongoDB and defaulted to the `hisab_db` database. Keep the MongoDB document IDs, collection names and amount field types. The backend now requires a replica set or sharded topology.
2. Rotate the exposed credentials and restrict the new database user to Hisab. Check for duplicate email and Hisab ID values. Startup creates unique indexes on those fields and will stop if existing records violate them. Review any conflicts with the owner before touching the original data.
3. Take a database backup with the provider or MongoDB Database Tools. Save a separate copy of the whole upload directory, preserving filenames. Store both backups securely. If the database stays with its current provider, keep that datastore and validate using a restored copy first. [MongoDB backup tools](https://www.mongodb.com/docs/database-tools/mongodump/)
4. If moving the database, restore into a new, empty target before routing writes to it. With the proper source and target URI variables set securely, an example is shown below. The namespace mapping preserves collection names and document IDs. [MongoDB restore options](https://www.mongodb.com/docs/database-tools/mongorestore/)

```bash
mongodump --uri "$SOURCE_MONGO_URI" --db hisab_db --archive=hisab-backup.gz --gzip
mongorestore --uri "$TARGET_MONGO_URI" --archive=hisab-backup.gz --gzip \
  --nsInclude 'hisab_db.*' --nsFrom 'hisab_db.*' --nsTo 'hisab_rehearsal.*'
```

Compare counts for users, groups, entries and invitations. Sample document IDs, member lists, accepted and pending requests, timestamps and exact amounts. Start the new server against the isolated copy, verify readiness, then exercise the API and an Android staging build.

## Cut over

1. Agree on a write freeze for the final backup and file copy if moving data. Preserve the old service, credentials rotation record and source backup until verification finishes.
2. Configure Render with the approved database URI and name. After the disk is attached, copy the backed-up upload files into `/var/data/uploads` with an authorized SSH session or transfer. Verify ownership and read access for UID 10001. The old upload handler reused one literal filename. Preserve the surviving file. Earlier overwritten bytes require an independent backup.
3. Check HTTPS readiness and verify a few profile images, group memberships, ledger totals and pending invitations. Confirm the app can sign in again using the new JWT key. Re-enable writes once the new service is the authoritative endpoint.
4. Sign and distribute the release with the approved HTTPS origin. Test the update on a controlled installation signed with the same key before distributing it more broadly.

## Roll back

Use a known-good server package with rotated credentials and point Android at the corresponding HTTPS origin. If the same database is still in use, confirm that the older server reads any new documents before restoring traffic. If the target has accepted writes, reconcile those writes before switching to a separate source database. Restore backups to a fresh database first and compare records before authorizing any live overwrite. Keep an independent upload backup because disk snapshot restores revert the entire disk. [Render disk recovery](https://render.com/docs/disks)
