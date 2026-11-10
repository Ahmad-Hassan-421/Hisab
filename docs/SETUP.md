# Local setup

Install JDK 17, Python 3 for the API checks, Android SDK platform 34, build tools 34.0.0, and ADB. Android Studio can create `Hisab/local.properties` to locate the SDK. In a command-line setup, set `ANDROID_HOME` to the SDK directory.

## MongoDB and the API

Use a disposable MongoDB replica set. For a local MongoDB installation, make a fresh data directory before running these commands.

```text
mongod --dbpath path-to-disposable-data --replSet rs0 --bind_ip 127.0.0.1 --port 27017
mongosh --port 27017 --eval 'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'
```

Set the environment in the shell that launches the backend. This PowerShell example creates a key for local use and chooses a separate database.

```powershell
$env:MONGO_URI = 'mongodb://127.0.0.1:27017/?replicaSet=rs0&serverSelectionTimeoutMS=5000'
$env:MONGO_DATABASE = 'hisab_local'
$env:JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$env:HOST = '127.0.0.1'
$env:PORT = '8081'
Set-Location hisab-server
.\gradlew.bat run
```

Keep the local signing key stable for sessions that should survive server restarts. The shell must load any `.env` values explicitly. The backend initializes database indexes and returns `ok` at `GET /healthz` after connecting.

## Android

Open `Hisab/` in Android Studio or build from a new terminal at the workspace root. For a phone connected by USB, authorize debugging and forward the app's loopback port to the API.

```powershell
adb devices -l
adb reverse tcp:8081 tcp:8081
Set-Location Hisab
.\gradlew.bat assembleDebug -PHISAB_QA=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The QA package is `com.example.hisab.qa`. For a standard debug build, omit `-PHISAB_QA=true`. An emulator can use `-PHISAB_DEBUG_API_URL=http://10.0.2.2:8081/` or ADB reverse. An HTTPS staging origin can be supplied to a standard debug build with the same property.
