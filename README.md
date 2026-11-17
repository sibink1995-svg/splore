# DualFM

Original dual-pane Android file manager (Kotlin + Jetpack Compose). No ads, no tracking.

Features: dual panes, Up button per pane, List / Compact / Grid view, Disk map (treemap of folder sizes),
copy/move to the other pane, rename, new folder, delete, multi-select (long-press), hidden files toggle.

## Build APK with GitHub
1. Create a new GitHub repo and push all of these files to the `main` branch.
2. Open the **Actions** tab -> **Build APK** run (starts automatically on push).
3. When it finishes, open the run and download the **DualFM-apk** artifact (zip). Unzip and install `app-release.apk`.

## Build locally
Open the folder in Android Studio and press Run, or run `gradle assembleRelease`.
