# Environment variables

## Server

| Name | Local and hosted behavior |
|---|---|
| `MONGO_URI` | Required. Supply a MongoDB URI for a replica set or sharded cluster. Use TLS for hosted databases. |
| `MONGO_DATABASE` | Defaults to `hisab_db`. Choose a separate value for disposable tests and staging. |
| `JWT_SECRET` | Required and at least 32 UTF-8 bytes. Store a randomly generated value securely. Rotating it invalidates existing sessions. |
| `HOST` | Defaults to `0.0.0.0`. Use loopback for local ADB reverse, and leave the public Render listener on all container interfaces. |
| `PORT` | Defaults to 8081 locally. The Blueprint supplies 10000. |
| `HISAB_ENV` | Set to `production` on Render to require an explicit persistent upload directory. |
| `UPLOAD_DIR` | Defaults to `uploads` in local mode. Render uses `/var/data/uploads`. |
| `RENDER_EXTERNAL_URL` | Supplied by Render. A valid HTTPS origin makes stored profile paths absolute in API responses. |
| `CORS_ALLOWED_ORIGINS` | Optional comma-separated browser origins such as `https://dashboard.example.invalid`. Native Android can use the API without CORS. |
| `BREVO_API_KEY`, `BREVO_SENDER_EMAIL` | Optional pair. Configure both for welcome email delivery. |

The server reads the process environment. Use the [.env.example](../.env.example) template to see the variable names.

## Android

| Name | Behavior |
|---|---|
| `HISAB_DEBUG_API_URL` | Gradle property or environment variable. Defaults to `http://127.0.0.1:8081/`. Debug permits cleartext when configured with an HTTP origin. |
| `HISAB_RELEASE_API_URL` | Required Gradle property or environment variable for release builds. Accepts an HTTPS origin. |
| `HISAB_QA` | Optional debug Gradle property. When `true`, appends `.qa` to the application ID and requires a loopback or emulator host. |

Gradle properties take precedence over the corresponding environment variables. Origins are public build settings. JWT, MongoDB and Brevo secrets remain on the server.
