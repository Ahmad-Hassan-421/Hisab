# Security

For a vulnerability report, use a private channel with the repository owner or GitHub private vulnerability reporting if enabled. Include the affected component, steps to reproduce with test data and the impact. Share diagnostics after removing passwords, bearer tokens, database URIs and account data.

The original server source contained a credential-bearing MongoDB fallback, a Brevo API key, a JWT signing secret and a hardcoded administrator enrollment password. The source now reads credentials from the environment, and registration creates regular users. Before publishing or deploying, rotate the MongoDB and Brevo credentials, choose a fresh JWT signing secret, and audit existing administrator accounts. A signing-key change requires users to sign in again.

If this project was shared in a different Git repository, scan that repository and its history. Rotate exposed credentials first. Coordinate any history rewrite with the owner and all users of the affected remote. Old build artifacts and copies can also contain the original values.
