# OpenCheps Bot 0.8.0

**[Скачать APK 0.8.0 / Download APK](https://github.com/SleewMaster/opencheps/releases/download/v0.8.0/OpenCheps_Bot_0.8.0.apk)** · **[Описание и установка / Release notes](https://github.com/SleewMaster/opencheps/releases/tag/v0.8.0)**

Экспериментальная сборка для Android 8.0+ (ARM64). Выбор **Экран / Камера**, локальный Stockfish 18 и подсказки стрелками. Камера рассчитана на **2D-доску другого экрана в начальной расстановке**; настоящие объёмные фигуры пока не поддерживаются. Проверки прошли в эмуляторе; реальная съёмка телефоном ещё не проверена.

Android chess analysis with Stockfish 18. Choose **Screen** or **Camera** on the main screen. This is an experimental build: recognition depends on the board theme, lighting and a correct initial calibration.

## Screen

Grant display-over-apps and full-screen capture permissions, show a flat 2D chess board in its starting position, and wait for calibration. Use **▣ ДОСКА** to select a board manually. The panel X, app Stop and notification Stop end the entire session. Android-protected windows cannot be captured by this mode.

## Camera

Camera permission is requested only when opening this mode. Screen capture and display-over-apps permissions are not needed. Point the rear camera at a **2D chess board on another screen**, in the initial position. Drag the four numbered corners clockwise from the top left. Select the colour at the bottom separately from the colour you play, then press **Начать**. Keep the board fully visible; tap the image to focus after calibration. **Углы заново** resets calibration for a new board or moved phone.

The selected quadrilateral is rectified to a 640 × 640 board before template recognition. Legal positions, including two missed plies, are considered; two clear matching observations are required to accept a position. Uncertain frames hide advice. Hints are projected back into the camera preview using the same four corners. Preview sampling excludes the app's own drawn hints.

Camera hand-shake stabilization registers grid intersections against a fixed reference using robust projective fitting. The same tracked corners rectify frames and draw hints. Small translations, rotation, zoom and perspective changes are followed; excessive motion, leaving the frame and severe blur require reframing. Two clear matching observations may bridge one dropped frame. New engine output is blocked during uncertainty; one soft failure may keep a verified arrow for at most 450 ms, while detected move candidates or lost geometry hide it immediately. Completed advice can be reused only after its FEN is verified again. Sampling interval: 220 ms, with actual latency dependent on device speed.

**Camera voice:** enable «Озвучивать ход» for Russian spoken moves, for example «Пешка с е два на е четыре». Only completed, currently verified, legal recommendations are spoken; repeated camera frames do not repeat the same announcement. «Повторить» repeats the current verified hint. Speech stops on pause, close, a detected move or loss of the board. A Russian offline voice must be installed in Android text-to-speech settings; missing speech support leaves arrows working. No network voices are selected. The voice preference is saved. Interrupted preliminary analysis resumes when the board is verified again.

Camera stops when the activity leaves the foreground. Returning clears old hints; changing size/orientation or recreating the activity requires calibration again. No frames are saved or uploaded. There is no microphone or network permission.

Physical 3D chess pieces, arbitrary mid-game calibration, large camera movement, glare, occlusion and all board themes are **not supported or guaranteed**. Camera compatibility is tested with Android's virtual camera and synthetic perspective images; a physical-device field test is still needed.

## Engine and build

Stockfish 18 runs locally. Default thinking time is 1.5 s; choose 3 s or 8 s presets, and one to three moves. Analysis time is an engine budget, not a total camera-to-hint latency guarantee.

Build with JDK 17, Gradle 8.7, Android SDK 34. CI downloads official Stockfish Android binaries, verifies SHA256, then runs unit tests, lint, builds ARM64/ARMv7 APKs and runs Android 14 UI/vision checks. CameraX is pinned to 1.4.0. Signing uses a private CI cache; the signing key is not included in source.

App package: `com.opencheps.bottrainer`. Camera mode does not run concurrently with screen capture.

## License

App code: MIT; preserve `LICENSE`. Bundled Stockfish: GPL-3.0, see [the full license](licenses/Stockfish-Copying.txt) and [third-party notices](release/v0.8.0/THIRD_PARTY_NOTICES.txt). Source: https://github.com/official-stockfish/Stockfish/tree/sf_18 . The original MIT attribution remains in LICENSE.
