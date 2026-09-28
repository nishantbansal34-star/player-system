# Player System

A real-life Solo Leveling System for Android: six small daily quests, levels, stats, loot, achievements, a shadow army of habits, and a home-screen widget where you tick quests off without opening the app.

## Install on your phone
1. Open the latest release: **Releases → PlayerSystem.apk** (on this repo's page).
2. Download the APK and open it. Allow "Install unknown apps" for your browser if Android asks.
3. Long-press your home screen → **Widgets** → **Player System** → drag **System · Daily Quests** onto the screen.

Installing a newer build over the old one keeps your progress (every build is signed with the same key).

## How it's built
- `app/src/main/assets/index.html` — the whole app UI and game logic (runs offline in a WebView).
- `SystemWidget.kt` + `Logic.kt` — the widget and the rules it needs (complete a quest, roll over at midnight).
- Every push to `main` builds the APK with GitHub Actions and attaches it to a new release.
