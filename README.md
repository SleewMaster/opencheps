# OpenCheps Bot 0.6.0

Android chess analysis with Stockfish 18. Choose **Screen** or **Camera** on the main screen. This is an experimental build: recognition depends on the board theme, lighting and a correct initial calibration.

## Screen

Grant display-over-apps and full-screen capture permissions, show a flat 2D chess board in its starting position, and wait for calibration. Use **▣ ДОСКА** to select a board manually. The panel X, app Stop and notification Stop end the entire session. Android-protected windows cannot be captured by this mode.

## Camera

Camera permission is requested only when opening this mode. Screen capture and display-over-apps permissions are not needed. Point the rear camera at a **2D chess board on another screen**, in the initial position. Drag the four numbered corners clockwise from the top left. Select the colour at the bottom separately from the colour you play, then press **Начать**. Keep the phone stationary, ideally in a stand; tap the image to focus after calibration. **Углы заново** resets calibration for a new board or moved phone.

The selected quadrilateral is rectified to a 640 × 640 board before template recognition. Legal positions, including two missed plies, are considered; two consecutive matching frames are required to accept a position. Uncertain frames hide advice. Hints are projected back into the camera preview using the same four corners. Preview sampling excludes the app's own drawn hints.

Camera stops when the activity leaves the foreground. Returning clears old hints; changing size/orientation or recreating the activity requires calibration again. No frames are saved or uploaded. There is no microphone or network permission.

Physical 3D chess pieces, arbitrary mid-game calibration, large camera movement, glare, occlusion and all board themes are **not supported or guaranteed**. Camera compatibility is tested with Android's virtual camera and synthetic perspective images; a physical-device field test is still needed.

## Engine and build

Stockfish 18 runs locally. Default thinking time is 1.5 s; choose 3 s or 8 s presets, and one to three moves. Analysis time is an engine budget, not a total camera-to-hint latency guarantee.

Build with JDK 17, Gradle 8.7, Android SDK 34. CI downloads official Stockfish Android binaries, verifies SHA256, then runs unit tests, lint, builds ARM64/ARMv7 APKs and runs Android 14 UI/vision checks. CameraX is pinned to 1.4.0. Signing uses a private CI cache; the signing key is not included in source.

App package: `com.opencheps.bottrainer`. Camera mode does not run concurrently with screen capture.

## License

App code: MIT; preserve `LICENSE`. Bundled Stockfish: GPL-3.0, see `licenses/Stockfish-Copying.txt` and `THIRD_PARTY_NOTICES.md` in release v0.4.0. Source: https://github.com/official-stockfish/Stockfish/tree/sf_18 . The original MIT attribution remains in LICENSE.
