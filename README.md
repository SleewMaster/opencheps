## 0.10.0: стабильная подсказка и запуск посреди партии

В захвате экрана отображается только окончательный `bestmove` Stockfish. Совет закреплён за подтверждённой позицией и повторно используется после краткого пропадания доски. Единственный законный ход показывается сразу; для остальных сохраняется выбранное время анализа (по умолчанию 1,5 с).

Можно подключиться к уже начатой партии. Встроенные численные шаблоны узнают стандартные 2D-фигуры Neo; один раз изученные из начальной позиции темы сохраняются на телефоне. При старте с текущей позиции приложение показывает доску для проверки: исправьте сомнительные клетки, выберите сторону снизу и очередь хода, затем подтвердите. Кнопка **ПОЗИЦИЯ** открывает эту проверку в любой момент; доступен ввод FEN.

Из одного снимка нельзя восстановить очередь хода, права на рокировку, последнее двойное продвижение пешки или счётчик 50 ходов. Рокировки и взятие на проходе при подключении по умолчанию выключены; укажите известные права или вставьте полный FEN. Затем новые ходы отслеживаются законными переходами. Свежий кадр перепроверяется до показа совета.

Шаблоны — компактные векторы признаков из изолированных образцов фигур, предоставленных для разработки. Скриншоты, имена игроков и координаты экранов в приложении и репозитории не сохраняются. Иные стили могут потребовать исправления распознавания или однократного обучения с начальной позиции. Это изменение относится к захвату экрана; камера сохраняет свой процесс начальной калибровки.

---

# OpenCheps Bot 0.10.0

**[Скачать APK 0.10.0 / Download APK](https://github.com/SleewMaster/opencheps/releases/download/v0.10.0/OpenCheps_Bot_0.10.0.apk)** · **[Описание и установка / Release notes](https://github.com/SleewMaster/opencheps/releases/tag/v0.10.0)**

Экспериментальная сборка для Android 8.0+ (ARM64). Выбор **Экран / Камера**, локальный Stockfish 18 и подсказки стрелками. Камера рассчитана на **2D-доску другого экрана в начальной расстановке**; настоящие объёмные фигуры пока не поддерживаются. Проверки прошли в эмуляторе; реальная съёмка телефоном ещё не проверена.

Android chess analysis with Stockfish 18. Choose **Screen** or **Camera** on the main screen. This is an experimental build: recognition depends on the board theme, lighting and a correct initial calibration.

## Screen

Grant display-over-apps and full-screen capture permissions and show a flat 2D chess board. Starting positions calibrate automatically. To join a game already in progress, review the detected diagram and explicitly select the side to move. Use **ПОЗИЦИЯ** to correct pieces, orientation and history fields, or import a complete FEN. Built-in recognition covers standard Neo pieces; other styles may need corrections or one-time initial-position theme learning. Use **▣ ДОСКА** to select a board manually. The panel X, app Stop and notification Stop end the entire session. Android-protected windows cannot be captured by this mode.

## Camera

Camera permission is requested only when opening this mode. Screen capture and display-over-apps permissions are not needed. Point the rear camera at a **2D chess board on another screen**, in the initial position. Drag the four numbered corners clockwise from the top left. Select the colour at the bottom separately from the colour you play, then press **Начать**. Keep the board fully visible; tap the image to focus after calibration. **Углы заново** lets you reframe the same game; return to the main screen to learn a different board.

The initial frame is snapped to the complete 8×8 checker grid before learning pieces. If the rough frame fails, the whole preview is searched. Extra monitor margins are excluded. «Углы заново» now re-applies geometry without discarding the trained pieces or current game. The phone shows a stable miniature board, a wide arrow, source/destination highlights and a named move such as «Конь g1 → f3». Diagnostic messages distinguish missing grid, blur, wrong bottom colour and unrecognised pieces.

The selected quadrilateral is rectified to a 640 × 640 board before template recognition. Legal positions, including two missed plies, are considered; two clear matching observations are required to accept a position. Uncertain frames hide advice. Hints are projected back into the camera preview using the same four corners. Preview sampling excludes the app's own drawn hints.

Camera hand-shake stabilization registers grid intersections against a fixed reference using robust projective fitting. The same tracked corners rectify frames and draw hints. Small translations, rotation, zoom and perspective changes are followed; excessive motion, leaving the frame and severe blur require reframing. Two clear matching observations may bridge one dropped frame. New engine output is blocked during uncertainty; one soft failure may keep a verified arrow for at most 450 ms, while detected move candidates or lost geometry hide it immediately. Completed advice can be reused only after its FEN is verified again. Sampling interval: 220 ms, with actual latency dependent on device speed.

**Camera voice:** enable «Озвучивать ход» for Russian spoken moves, for example «Пешка с е два на е четыре». Only completed, currently verified, legal recommendations are spoken; repeated camera frames do not repeat the same announcement. «Повторить» repeats the current verified hint. Speech stops on pause, close, a detected move or loss of the board. A Russian offline voice must be installed in Android text-to-speech settings; missing speech support leaves arrows working. No network voices are selected. The voice preference is saved. Interrupted preliminary analysis resumes when the board is verified again.

Camera stops when the activity leaves the foreground. Returning clears old hints; changing size/orientation or recreating the activity requires calibration again. No frames are saved or uploaded. There is no microphone or network permission.

Physical 3D chess pieces, arbitrary mid-game calibration, glare, occlusion and all board themes are **not supported or guaranteed**. Camera compatibility is tested with Android's virtual camera and synthetic perspective images; a physical-device field test is still needed.

## Engine and build

Stockfish 18 runs locally. Default thinking time is 1.5 s; choose 3 s or 8 s presets, and one to three moves. Analysis time is an engine budget, not a total camera-to-hint latency guarantee.

Build with JDK 17, Gradle 8.7, Android SDK 34. CI downloads official Stockfish Android binaries, verifies SHA256, then runs unit tests, lint, builds ARM64/ARMv7 APKs and runs Android 14 UI/vision checks. CameraX is pinned to 1.4.0. Signing uses a private CI cache; the signing key is not included in source.

App package: `com.opencheps.bottrainer`. Camera mode does not run concurrently with screen capture.

## License

App code: MIT; preserve `LICENSE`. Bundled Stockfish: GPL-3.0, see [the full license](licenses/Stockfish-Copying.txt) and [third-party notices](release/v0.10.0/THIRD_PARTY_NOTICES.txt). Source: https://github.com/official-stockfish/Stockfish/tree/sf_18 . The original MIT attribution remains in LICENSE.
