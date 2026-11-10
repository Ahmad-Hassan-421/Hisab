# Production checklist

- [ ] Rotate MongoDB and Brevo credentials, generate a new JWT secret and audit existing administrator accounts.
- [ ] Review GitHub CI results and confirm the Android release signing identity.
- [ ] Rehearse backup and restore on a disposable MongoDB replica set, including existing indexes and uploads.
- [ ] Review Render region, compute, storage cost and outbound IP allowlist.
- [ ] Confirm the disk is mounted and writable by the application user. Verify the default image and existing profile images.
- [ ] Check HTTPS health, protected requests, sign-in, invitations, ledger operations and upload persistence.
- [ ] Verify the signed Android update on a controlled physical installation.
- [ ] Record monitoring and rollback contacts, and keep the previous approved release and backups available.
