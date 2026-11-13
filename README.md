# Sudoku Auto Player — Android starter project

Target device: Xiaomi Redmi Note 14 5G, Android 16 / HyperOS 3.

## Included
- Kotlin Android project scaffold (compile SDK 36).
- Basic Start / Pause / Stop control panel.
- Accessibility-service declaration and text inspection foundation.
- Backtracking Sudoku solver.
- GitHub Actions workflow to build and upload a debug APK artifact.

## Important status
This is **not yet a finished auto-player**. The game-specific automation remains TODO:
1. User-selected game app and launch flow.
2. Screen capture with Android MediaProjection consent.
3. 9x9 board, given digits and keypad recognition.
4. Tap calibration and verification of each entered digit.
5. Home → New Game → Easy → Playing → Completed → repeat state machine.
6. Full-screen ad detection; dismiss only a legitimate visible Skip/Close control.
7. Pause/resume recovery and on-device testing.

The app intentionally avoids blind tapping. The current build is only a starter scaffold and must not be mistaken for working unattended gameplay automation.

## Build from GitHub Actions (phone only)
1. Open the **Actions** tab.
2. Run **Build Sudoku Auto Player APK** using **Run workflow**, or push a commit to `main`.
3. When successful, open the workflow run and download the `sudoku-auto-player-debug-apk` artifact.
4. Extract the ZIP and install `app-debug.apk`. Android may request permission to install apps from your browser or file manager.

Accessibility must be enabled manually. Any screen-capture flow must request Android's MediaProjection consent.

## Safety
- No ad clicking, ad bypassing, or paid-feature circumvention.
- Only dismiss ads when a normal Skip or Close control is clearly exposed.
- Stop if screen recognition is uncertain.
