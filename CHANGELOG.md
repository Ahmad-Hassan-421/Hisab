# Changelog

## Unreleased

- Grouped Android and Ktor source by feature while retaining the application and database identities.
- Secured account responses, group membership checks, invitation decisions and image uploads.
- Added transactional ledger and invitation writes, replay protection for identified entries, decimal calculations and cache reconciliation.
- Added configurable API origins, release HTTPS enforcement, database readiness and repository checks.
- Prepared a Render Docker Blueprint with persistent profile uploads and an external MongoDB datastore.
- Updated the server's Netty, Logback and Jackson runtimes following the dependency security review.
