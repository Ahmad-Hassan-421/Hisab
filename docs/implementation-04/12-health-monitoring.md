# Render health monitoring

Added `GET /health` returning `{"status":"ok"}` with HTTP 200, plus `GET /ready` for database readiness. Updated `render.yaml` to use `/health` as the health-check path. No credentials are exposed.
