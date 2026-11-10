# Deploy the Hisab API to Render

The Blueprint defines one Docker web service for Ktor. Render's native runtimes do not include Java or Kotlin. The Docker build uses JDK 17 to run `sh ./gradlew --no-daemon installDist`, then copies the distribution into a JRE 17 image. At runtime the entrypoint prepares the mounted upload directory and runs the server as UID/GID 10001. Android is built and distributed separately. [Render language support](https://render.com/docs/language-support)

| Setting | Value |
|---|---|
| Blueprint | `render.yaml` at the repository root |
| Service | `hisab-api`, Docker web service, one instance |
| Docker build context | `./hisab-server` |
| Dockerfile | `./hisab-server/Dockerfile` |
| Start | Container entrypoint runs `/opt/hisab/bin/hisab-server` |
| Listener | `0.0.0.0:10000` in the Blueprint |
| Health check | `GET /healthz` |
| Database | Externally managed MongoDB replica set or sharded cluster |
| Uploads | 1 GB Render disk at `/var/data`, upload directory `/var/data/uploads` |
| Deployment trigger | Manual service deploys after initial creation |

## Before creating the service

1. Commit and push the audited files to the GitHub repository you intend to connect. Wait for the GitHub checks.
2. Read the [migration plan](MIGRATION_PLAN.md). Rotate the previously exposed credentials. Prepare a verified backup and an isolated database for the first rehearsal.
3. Check the compute and storage estimate in Render before applying the Blueprint. It selects the paid 0.5 CPU and 512 MB instance. Render lists disk storage at US$0.25 per GB per month. MongoDB hosting, workspace fees and usage can add costs. The attached disk limits the service to one instance and introduces a deployment interruption. [Render Blueprint reference](https://render.com/docs/blueprint-spec), [Persistent disks](https://render.com/docs/disks), [Render billing model](https://render.com/articles/how-much-does-cloud-application-hosting-cost-for-small-businesses)

## Create, configure and verify

1. In Render select **New** and **Blueprint**, connect the chosen GitHub repository, and select the branch that contains `render.yaml`.
2. Review the web service and disk before approving resource creation. Set the Blueprint's `region` before first creation if you need a region other than Render's default. Choose a region near the MongoDB cluster and intended users. Add Render's outbound ranges to the MongoDB network allowlist. Use a database principal scoped to the Hisab database with permission to create the documented indexes. Supply `MONGO_URI` in Render's secret field. [Blueprint regions](https://render.com/docs/blueprint-spec), [Outbound addresses](https://render.com/docs/outbound-ip-addresses)
3. Set `MONGO_DATABASE` to the intended database. The Blueprint generates `JWT_SECRET` on creation. Keep it stable across normal deploys. Add the Brevo settings only if you want welcome email delivery. [Environment variables](ENVIRONMENT_VARIABLES.md)
4. Deploy the service, inspect startup logs, and confirm that the health check returns `ok` over HTTPS. Verify the packaged image at `/static/default_profile.png` and confirm that a protected route such as `/groups?userId=test` returns HTTP 401 without a token. Use a controlled account for registration, login, ledger and upload tests.
5. Record the service's HTTPS origin. For a staging APK set `HISAB_DEBUG_API_URL` to the staging origin. For a production APK set `HISAB_RELEASE_API_URL` to the approved origin and build the signed release with the original signing key when updating an existing installation.
6. Confirm both the health endpoint and the phone's API and upload workflows after deployment. Monitor restarts, Ktor HTTP errors, database connectivity, heap use and disk consumption. Use the [checklist](PRODUCTION_CHECKLIST.md) and [rollback steps](MIGRATION_PLAN.md).

Render supplies `RENDER_EXTERNAL_URL`. Hisab uses its HTTPS origin when returning hosted profile-image links and stores the corresponding relative path. Its local Android builds can still resolve relative image paths. [Render default environment variables](https://render.com/docs/environment-variables)

## Local validation

The production-equivalent runtime is the Gradle `installDist` package. For a container rehearsal on a machine with Docker, build `docker build -t hisab-api ./hisab-server`, supply a disposable MongoDB endpoint reachable from the container, and mount a local volume at `/var/data`. Exercise restart and file persistence before using live uploads.

The Blueprint was checked against [Render's published schema](https://render.com/schema/render.yaml.json). The local environment has no Docker daemon or WSL distribution. Container execution and deployment remain verification steps for the chosen Render workspace.
