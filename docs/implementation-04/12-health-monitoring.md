# Render health monitoring

`GET /health` is public and checks MongoDB on every request. It returns HTTP 200 with `{"status":"ok"}` when ready, or HTTP 503 with `{"status":"unavailable"}` if the database fails or exceeds the two-second probe deadline. Responses contain no account or connection details and use `Cache-Control: no-store`. `/healthz` and `/ready` remain available.

All three endpoints also accept `HEAD`, which UptimeRobot uses by default for HTTP monitors. HEAD runs the same database probe and returns the same status, content type, content length and cache policy as GET, with no response body. Previously, only GET handlers existed, so a healthy backend returned 405 to the monitor. [UptimeRobot request-method behavior](https://help.uptimerobot.com/en/articles/11358466-how-to-debug-a-monitor-showing-as-down-in-uptimerobot).

Use these settings when adding the UptimeRobot monitor:

| Setting | Value |
|---|---|
| Monitor type | HTTP(s) |
| Name | Hisab API |
| URL | `https://hisab-onlo.onrender.com/health` |
| Method | HEAD (HTTP monitor default), or GET when inspecting JSON content |
| Up HTTP status code | 200 |
| Interval | 5 minutes on the Free plan |
| Authentication / custom authorization header | None |
| Alerts | Select your own notification contact |

An optional keyword monitor can require the exact text `"status":"ok"`. The HTTP status monitor already detects database failures because the endpoint returns 503. There is no health environment variable, monitor API key, or login to add to the application. Confirm the deployed URL returns the expected JSON before enabling alerts. [UptimeRobot setup guide](https://help.uptimerobot.com/en/articles/11358364-how-to-create-your-first-monitor-on-uptimerobot-quick-setup-guide), [status-code settings](https://help.uptimerobot.com/en/articles/11360863-uptimerobot-advanced-settings-customize-your-monitor-configuration).

For Render's own readiness probe, set **Hisab → Settings → Health Check Path** to `/health`. The root Blueprint already declares that path, but the existing service was created separately and does not inherit Blueprint changes automatically. Its inspected configuration had an empty HTTP health path and automatic deployment on commits to `hisab-update`.

The inspected service uses Render's Free compute plan. Free services can sleep after 15 minutes without inbound traffic and can take about a minute to wake up. Their local uploads are ephemeral, and two services in this workspace share its monthly free-instance hours. Monitoring does not guarantee continuous availability or preserve uploaded files. [Render Free service behavior](https://render.com/docs/free).
