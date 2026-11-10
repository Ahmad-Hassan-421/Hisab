# Deployment

The Ktor backend is the deployable service. Android is distributed as an APK or bundle. Build the server with `./gradlew installDist` and start the resulting `build/install/hisab-server/bin/hisab-server` launcher. Use its `.bat` counterpart on Windows.

Set the MongoDB URI and database, a new JWT secret, a writable persistent upload directory, and the host and port. Netty uses `PORT` when supplied. Check `GET /healthz` before configuring Android against the service. Build Android releases with the service's HTTPS origin.

See [Render deployment](RENDER_DEPLOYMENT.md) for hosting and [Migration](MIGRATION_PLAN.md) for data and rollback.
