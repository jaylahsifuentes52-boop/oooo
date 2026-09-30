# TvPatcher 2.0

Two-tab Android utility: OTP verification and PATCHING. The app never embeds a Discord bot token and opens Meta login in the external browser rather than handling credentials.

## Configure
Set `API_BASE_URL` in `app/src/main/java/com/tvmods/tvpatcher/MainActivity.kt` to the HTTPS URL of the companion verification service.

## Build
Push this project to GitHub and the included workflow builds debug/release APKs. The original upload did not contain a Gradle wrapper script, so the workflow generates one when needed.

## ADB/Bytezuku
Grant Patch Access opens Bytezuku when it is installed and not already providing the Shizuku-compatible binder. Once authorized, the app uses its privileged service to execute shell/ADB-style commands for OBB/cache operations.

## Auth limitation
The app can only claim `You Have Authed!` when the exact redeem marker is visible in `logcat`. If the real game does not log redeem text, the app intentionally reports that it cannot observe it instead of pretending the check succeeded.
