package com.opencheps.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.opencheps.R;
import com.opencheps.engine.StockfishEngine;
import com.opencheps.overlay.ArrowOverlayView;
import com.opencheps.overlay.FloatingPanel;
import com.opencheps.overlay.OverlayService;
import com.opencheps.util.Prefs;
import com.opencheps.util.Side;
import com.opencheps.vision.BoardDetector;
import com.opencheps.vision.ChessPosition;
import com.opencheps.vision.VisualMoveTracker;
import com.opencheps.vision.FenBuilder;
import com.opencheps.vision.MoveAnalyzer;
import com.opencheps.vision.PieceClassifier;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScreenCaptureService extends Service {

    public static volatile boolean isRunning;
    private static final String TAG = "OC/Capture";
    private static final String CHANNEL_ID = "opencheps_capture";

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_USER_SIDE   = "user_side";
    public static final String ACTION_STOP       = "com.opencheps.STOP";

    private static final int[]   ARROW_COLORS = { 0xFFB9F66E, 0xFF67D7FF, 0xFFFFC56B };
    private static final float[] ARROW_WIDTHS = { 18f, 13f, 10f };
    private static final long    SAMPLE_PERIOD_MS = 500;
    private static final int     LOST_BOARD_THRESHOLD = 8;

    private MediaProjection projection;
    private VirtualDisplay   virtualDisplay;
    private ImageReader      reader;
    private HandlerThread    workerThread;
    private Handler          worker;

    private final java.util.concurrent.ExecutorService analysis = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean analysisBusy;
    private boolean positionVerified;
    private int generation;
    private volatile boolean destroyed;
    private StockfishEngine  engine;
    private boolean engineReady;
    private PieceClassifier  classifier;

    private int captureW, captureH, density;
    private int screenW, screenH;
    private float capToScreenX = 1f, capToScreenY = 1f;
    private volatile boolean busy = false;
    private volatile boolean paused = false;

    private Side userSide = Side.WHITE;

    private char[][] confirmedGrid;
    private char[][] candidateGrid;
    private int candidateRepeats;
    private Side observedStm = Side.WHITE;
    private String castlingRights = "KQkq";
    private String enPassantTarget = "-";
    private int halfmoveClock = 0, fullmoveNumber = 1;
    private List<StockfishEngine.Pv> currentPvs = new ArrayList<>();
    private String lastEngineFen = "";
    private String lastDisplayedArrowFen = "";
    private int lostBoardCount = 0;
    private BoardDetector.Board lastKnownBoard;
    private List<ArrowOverlayView.Arrow> currentArrows = new ArrayList<>();

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            // MediaProjection consent is single-use. Android must never restart
            // this foreground service without a fresh capture permission intent.
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Missing capture consent; stopping instead of crashing");
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        isRunning=true;
        startInForeground();
        String sideStr = intent.getStringExtra(EXTRA_USER_SIDE);
        if (sideStr == null) sideStr = Prefs.userSide(this);
        userSide = "b".equals(sideStr) ? Side.BLACK : Side.WHITE;

        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(resultCode, data);
        if (projection == null) {
            Log.e(TAG, "MediaProjection acquisition failed");
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, null);

        workerThread = new HandlerThread("oc-worker", Thread.NORM_PRIORITY);
        workerThread.start();
        worker = new Handler(workerThread.getLooper());

        engine = new StockfishEngine();
        classifier = new PieceClassifier();

        analysis.execute(() -> {
            try {
                engine.start(getApplicationContext());
                worker.post(() -> {engineReady = true; panelStatus("Движок готов · " + userSide);});
            } catch (Exception e) {
                Log.e(TAG, "engine start failed", e);
                panelStatus("Ошибка движка: " + e.getMessage());
            }
        });

        setupVirtualDisplay();
        wirePanel();
        worker.postDelayed(sampleLoop, 1000);
        panelStatus("Запуск · " + userSide);
        return START_NOT_STICKY;
    }

    private void wirePanel() {
        new Handler(getMainLooper()).postDelayed(() -> {
            FloatingPanel p = OverlayService.panel();
            if (p != null) {
                p.placeBottomLeft(screenW, screenH);
                p.setUserSide(userSide);
                p.setListener(new FloatingPanel.Listener() {
                    @Override public void onPauseToggle(boolean isPaused) {
                        paused=isPaused;
                        worker.post(() -> {
                            generation++;engine.cancelSearch();lastEngineFen="";candidateGrid=null;candidateRepeats=0;
                            currentArrows.clear();currentPvs.clear();OverlayService.clearAll();panelEval("");
                            panelStatus(isPaused?"Пауза":"Продолжаю");
                        });
                    }
                    @Override public void onStop() { stopSelf(); }
                    @Override public void onRecalibrate() {
                        worker.post(() -> {
                            classifier.reset();resetGameState();OverlayService.clearAll();panelEval("");
                            panelStatus("Новая партия · покажи начальную позицию");
                        });
                    }
                    @Override public void onSideChange(Side newSide) {
                        worker.post(() -> {
                            userSide=newSide;Prefs.setUserSide(getApplicationContext(),newSide==Side.BLACK?"b":"w");
                            classifier.reset();resetGameState();OverlayService.clearAll();panelEval("");
                            panelStatus("Сторона: "+newSide+" · покажи начальную позицию");
                        });
                    }
                });
            }
        }, 400);
    }

    private void resetGameState() {
        generation++;
        if(engine!=null)engine.cancelSearch();
        positionVerified=false;
        confirmedGrid = null;
        candidateGrid = null;
        candidateRepeats = 0;
        lastEngineFen = "";
        lastDisplayedArrowFen = "";
        observedStm = Side.WHITE;
        castlingRights = "KQkq";
        enPassantTarget = "-";
        halfmoveClock = 0;
        fullmoveNumber = 1;
        currentArrows.clear();
        currentPvs.clear();
    }

    private final Runnable sampleLoop = new Runnable() {
        @Override public void run() {
            if(destroyed) return;
            if(paused) { drainReader(); worker.postDelayed(this,500); return; }
            // Overlays are part of MediaProjection. Never recognize our own arrows.
            drainReader();
            OverlayService.hideArrowsForCapture(true);
            worker.postDelayed(() -> {
                try { if(!paused)sampleOnce(); }
                catch(Throwable t){Log.e(TAG,"sample error",t);}
                finally {OverlayService.hideArrowsForCapture(false);}
                if(!destroyed)worker.postDelayed(this,220);
            },90);
        }
    };

    private void drainReader() {
        if (reader == null) return;
        while (true) {
            Image im;
            try { im = reader.acquireNextImage(); }
            catch (Exception e) { break; }
            if (im == null) break;
            im.close();
        }
    }

    private void sampleOnce() {
        if (busy || reader == null) return;
        Image img;
        try { img = reader.acquireLatestImage(); }
        catch (Exception e) { return; }
        if (img == null) return;
        busy = true;
        Bitmap bmp = null;
        try {
            bmp = imageToBitmap(img);
            img.close();
            img = null;

            BoardDetector.Board board = BoardDetector.locate(bmp);

            if (board == null) {
                positionVerified=false;OverlayService.clearArrowsOnly();
                lostBoardCount++;
                if (lostBoardCount < LOST_BOARD_THRESHOLD) {
                    if (lostBoardCount == 1) panelStatus("Ищу доску…");
                    return;
                }
                panelStatus("Board lost (" + lostBoardCount + ")");
                OverlayService.clearAll();
                return;
            }
            lostBoardCount = 0;
            lastKnownBoard = board;
            Rect screenBounds = boardBoundsOnScreen(board);
            OverlayService.setBoardBounds(screenBounds);

            if (!engineReady) {
                panelStatus("Доска найдена · запускаю движок");
                return;
            }

            if (!classifier.isTrained()) {
                boolean ok = classifier.tryBootstrap(bmp, board);
                if (!ok) {
                    panelStatus("Покажи начальную позицию");
                    OverlayService.setBoardFrameColor(0x66FFB300);
                    return;
                }
                Side detected = classifier.bootstrapSide();
                if (detected != userSide) {
                    classifier.reset();
                    panelStatus("Board shows " + detected + " — flip or change colour");
                    OverlayService.setBoardFrameColor(0x66D32F2F);
                    return;
                }
                OverlayService.setBoardFrameColor(0x6600C853);
                positionVerified=true;
                confirmedGrid = startingGridFor(userSide);
                observedStm = Side.WHITE;
                candidateGrid = null;
                candidateRepeats = 0;
                panelStatus("Готов · твоя сторона: " + userSide);
                if (observedStm == userSide) {
                    queryEngineAndRender(board, bmp);
                } else {
                    panelStatus(observedStm + "'s turn — waiting");
                }
                return;
            }

            char[][] canonical = classifier.bootstrapSide()==Side.BLACK
                    ? FenBuilder.flip(confirmedGrid) : confirmedGrid;
            float[][] screenCosts=classifier.labelCosts(bmp,board);
            float[][] costs=new float[64][];
            for(int i=0;i<64;i++) costs[i]=screenCosts[classifier.bootstrapSide()==Side.BLACK?63-i:i];
            VisualMoveTracker.Match match=VisualMoveTracker.match(
                    new ChessPosition(canonical,observedStm,castlingRights,enPassantTarget),costs);
            if(match==null) {
                positionVerified=false;
                candidateGrid=null;candidateRepeats=0;
                OverlayService.clearArrowsOnly();currentArrows.clear();currentPvs.clear();
                lastEngineFen="";panelEval("");panelStatus("Проверяю доску · убери перекрывающие окна");
                return;
            }
            char[][] grid=classifier.bootstrapSide()==Side.BLACK
                    ? FenBuilder.flip(match.position.board) : match.position.board;
            if(match.plies==0) {
                positionVerified=true;
                candidateGrid=null;candidateRepeats=0;
                if(observedStm==userSide) {
                    if(!fenOf(confirmedGrid).equals(lastEngineFen)) queryEngineAndRender(board,bmp);
                    else if(!analysisBusy) {repushCurrentArrows(board);panelStatus("Твой ход");}
                } else {OverlayService.clearArrowsOnly();panelStatus("Жду ход соперника");}
                return;
            }
            positionVerified=false;
            if(analysisBusy)engine.cancelSearch();
            OverlayService.clearArrowsOnly();panelEval("");
            if(!gridsEqual(grid,candidateGrid)) {candidateGrid=grid;candidateRepeats=1;panelStatus("Проверяю ход…");return;}
            candidateRepeats++;
            if(candidateRepeats<2)return;
            if(match.plies==2) {
                char[][] middle=classifier.bootstrapSide()==Side.BLACK
                        ? FenBuilder.flip(match.intermediate.board) : match.intermediate.board;
                updatePositionMetadata(confirmedGrid,middle,MoveAnalyzer.analyze(canonical,match.intermediate.board));
                updatePositionMetadata(middle,grid,MoveAnalyzer.analyze(match.intermediate.board,match.position.board));
            } else updatePositionMetadata(confirmedGrid,grid,MoveAnalyzer.analyze(canonical,match.position.board));
            generation++;
            positionVerified=true;
            confirmedGrid=grid;observedStm=match.position.turn;
            castlingRights=match.position.rights;enPassantTarget=match.position.ep;
            classifier.commitConfirmed(grid,bmp,board);
            candidateGrid=null;candidateRepeats=0;
            currentPvs.clear();currentArrows.clear();lastEngineFen="";lastDisplayedArrowFen="";
            if(observedStm==userSide)queryEngineAndRender(board,bmp);
            else panelStatus("Жду ход соперника");
        } catch (Exception e) {
            Log.w(TAG, "loop iter failed: " + e.getMessage(), e);
        } finally {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            if (img != null) try { img.close(); } catch (Exception ignored) {}
            busy = false;
        }
    }

    /** Track castling and en-passant strictly across confirmed board changes. */
    private void updatePositionMetadata(char[][] oldScreen, char[][] newScreen,
                                        MoveAnalyzer.Result move) {
        char[][] prev = classifier.bootstrapSide() == Side.BLACK
                ? FenBuilder.flip(oldScreen) : oldScreen;
        char[][] next = classifier.bootstrapSide() == Side.BLACK
                ? FenBuilder.flip(newScreen) : newScreen;
        StringBuilder rights = new StringBuilder();
        if (castlingRights.contains("K") && prev[7][4] == 'K' && next[7][4] == 'K'
                && prev[7][7] == 'R' && next[7][7] == 'R') rights.append('K');
        if (castlingRights.contains("Q") && prev[7][4] == 'K' && next[7][4] == 'K'
                && prev[7][0] == 'R' && next[7][0] == 'R') rights.append('Q');
        if (castlingRights.contains("k") && prev[0][4] == 'k' && next[0][4] == 'k'
                && prev[0][7] == 'r' && next[0][7] == 'r') rights.append('k');
        if (castlingRights.contains("q") && prev[0][4] == 'k' && next[0][4] == 'k'
                && prev[0][0] == 'r' && next[0][0] == 'r') rights.append('q');
        castlingRights = rights.length() == 0 ? "-" : rights.toString();

        enPassantTarget = "-";
        boolean pawnMoved = false, captured = false;
        char pawn = move.mover == Side.WHITE ? 'P' : 'p';
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                char before = prev[r][f], after = next[r][f];
                if (before == pawn && after != pawn) pawnMoved = true;
                if (before != '.' && before != after
                        && Character.isUpperCase(before) != (move.mover == Side.WHITE)) {
                    captured = true;
                }
            }
        }
        int from = move.mover == Side.WHITE ? 6 : 1;
        int to = move.mover == Side.WHITE ? 4 : 3;
        for (int f = 0; f < 8; f++) {
            if (prev[from][f] == pawn && next[from][f] == '.'
                    && prev[to][f] == '.' && next[to][f] == pawn) {
                enPassantTarget = "" + (char)('a' + f)
                        + (move.mover == Side.WHITE ? "3" : "6");
                break;
            }
        }
        halfmoveClock = pawnMoved || captured ? 0 : halfmoveClock + 1;
        if (move.mover == Side.BLACK) fullmoveNumber++;
    }

    private void queryEngineAndRender(BoardDetector.Board board, Bitmap currentBitmap) {
        if(confirmedGrid==null||analysisBusy||!engineReady||paused)return;
        final String fen=fenOf(confirmedGrid);
        if(fen.isEmpty())return;
        if(fen.equals(lastEngineFen)){repushCurrentArrows(board);return;}
        final int token=generation;
        final int think=Prefs.thinkMs(getApplicationContext());
        final int multiPv=Prefs.multiPv(getApplicationContext());
        analysisBusy=true;lastEngineFen=fen;
        panelStatus("Считаю · "+think/1000f+" с");
        analysis.execute(() -> {
            try {
                final List<StockfishEngine.Pv> pvs=engine.topMoves(fen,think,multiPv, partial -> {
                    if(!destroyed)worker.post(() -> renderAnalysis(partial,fen,token,false));
                });
                if(destroyed)return;
                worker.post(() -> {
                    analysisBusy=false;
                    if(token!=generation||paused||!positionVerified||observedStm!=userSide||candidateGrid!=null
                            ||!fen.equals(fenOf(confirmedGrid))) {lastEngineFen="";return;}
                    if(pvs.isEmpty()) {lastEngineFen="";panelStatus("Нет допустимых ходов");return;}
                    renderAnalysis(pvs,fen,token,true);
                });
            } catch(Exception e) {
                Log.w(TAG,"analysis failed",e);
                if(!destroyed)worker.post(() -> {analysisBusy=false;lastEngineFen="";panelStatus("Повторяю анализ…");});
            }
        });
    }

    private void renderAnalysis(List<StockfishEngine.Pv> pvs,String fen,int token,boolean complete) {
        if(destroyed||paused||!positionVerified||token!=generation||observedStm!=userSide
                ||candidateGrid!=null||!fen.equals(fenOf(confirmedGrid))||pvs.isEmpty())return;
        currentPvs=new ArrayList<>(pvs);
        BoardDetector.Board latest=lastKnownBoard;
        currentArrows=buildArrows(currentPvs,latest);lastDisplayedArrowFen=fen;
        OverlayService.pushArrows(currentArrows,boardBoundsOnScreen(latest));
        StockfishEngine.Pv best=pvs.get(0);
        String eval=best.mate ? (best.cp>0?"Мат за ":"Мат нам за ")+Math.abs(plyToMate(best.cp))
                :String.format(java.util.Locale.US,"%+.2f",best.cp/100.0);
        panelEval(formatUci(best.uci)+"  ·  "+eval);
        panelStatus((complete?"Готово":"Уточняю")+" · глубина "+best.depth);
    }

    private void repushCurrentArrows(BoardDetector.Board board) {
        if (currentArrows == null || currentArrows.isEmpty()) return;
        currentArrows = rebuildArrowsForNewBounds(board);
        OverlayService.pushArrows(currentArrows, boardBoundsOnScreen(board));
    }

    private List<ArrowOverlayView.Arrow> rebuildArrowsForNewBounds(BoardDetector.Board board) {
        // Recompute absolute arrow coordinates when the board is relocated.
        return buildArrows(currentPvs, board);
    }

    private List<ArrowOverlayView.Arrow> buildArrows(
            List<StockfishEngine.Pv> pvs, BoardDetector.Board board) {
        List<ArrowOverlayView.Arrow> out = new ArrayList<>();
        Side persp = classifier.bootstrapSide();
        float cellX = board.square * capToScreenX;
        float cellY = board.square * capToScreenY;
        float cellAvg = (cellX + cellY) * 0.5f;
        float ox = board.left * capToScreenX;
        float oy = board.top  * capToScreenY;
        for (int i = 0; i < pvs.size(); i++) {
            String uci = pvs.get(i).uci;
            if (uci == null || uci.length() < 4) continue;
            int fx = uci.charAt(0) - 'a', fy = 8 - (uci.charAt(1) - '0');
            int tx = uci.charAt(2) - 'a', ty = 8 - (uci.charAt(3) - '0');
            if (persp == Side.BLACK) {
                fx = 7 - fx; fy = 7 - fy; tx = 7 - tx; ty = 7 - ty;
            }
            float sx = ox + (fx + 0.5f) * cellX, sy = oy + (fy + 0.5f) * cellY;
            float ex = ox + (tx + 0.5f) * cellX, ey = oy + (ty + 0.5f) * cellY;
            String lbl = i==0 ? formatUci(uci) : null;
            float width = Math.max(6f, cellAvg * (i==0 ? .115f : .075f));
            out.add(new ArrowOverlayView.Arrow(sx, sy, ex, ey, ARROW_COLORS[i], width, lbl));
        }
        return out;
    }

    private String fenOf(char[][] grid) {
        if (grid == null) return "";
        String f = FenBuilder.build(grid, classifier.bootstrapSide(), userSide,
                castlingRights, enPassantTarget, halfmoveClock, fullmoveNumber);
        return f == null ? "" : f;
    }

    private boolean gridsEqual(char[][] a, char[][] b) {
        if (a == null || b == null) return false;
        for (int r = 0; r < 8; r++) if (!Arrays.equals(a[r], b[r])) return false;
        return true;
    }

    private char[][] startingGridFor(Side userSide) {
        char[][] base = {
            {'r','n','b','q','k','b','n','r'},
            {'p','p','p','p','p','p','p','p'},
            {'.','.','.','.','.','.','.','.'},
            {'.','.','.','.','.','.','.','.'},
            {'.','.','.','.','.','.','.','.'},
            {'.','.','.','.','.','.','.','.'},
            {'P','P','P','P','P','P','P','P'},
            {'R','N','B','Q','K','B','N','R'},
        };
        if (userSide == Side.BLACK) {
            char[][] o = new char[8][8];
            for (int r = 0; r < 8; r++) for (int f = 0; f < 8; f++) o[r][f] = base[7-r][7-f];
            return o;
        }
        return base;
    }

    private int plyToMate(int cp) { return cp > 0 ? 100000 - cp : -(100000 + cp); }

    private String formatUci(String uci) {
        if (uci == null || uci.length() < 4) return uci == null ? "?" : uci;
        return uci.substring(0, 2) + "→" + uci.substring(2, 4)
                + (uci.length() > 4 ? "=" + Character.toUpperCase(uci.charAt(4)) : "");
    }

    private Rect boardBoundsOnScreen(BoardDetector.Board board) {
        return new Rect(
                Math.round(board.left * capToScreenX),
                Math.round(board.top  * capToScreenY),
                Math.round((board.left + board.side) * capToScreenX),
                Math.round((board.top  + board.side) * capToScreenY));
    }

    private void panelStatus(String s) {
        FloatingPanel p = OverlayService.panel();
        if (p != null) p.setStatus(s);
    }

    private void panelEval(String s) {
        FloatingPanel p = OverlayService.panel();
        if (p != null) p.setEval(s);
    }

    private void setupVirtualDisplay() {
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        float maxEdge = Math.max(screenW, screenH);
        float scale = Math.min(1f, 1200f / maxEdge);
        captureW = Math.round(screenW * scale);
        captureH = Math.round(screenH * scale);
        density  = dm.densityDpi;
        capToScreenX = (float) screenW / captureW;
        capToScreenY = (float) screenH / captureH;

        Log.i(TAG, "screen=" + screenW + "x" + screenH
                + " capture=" + captureW + "x" + captureH);

        reader = ImageReader.newInstance(captureW, captureH, PixelFormat.RGBA_8888, 4);
        virtualDisplay = projection.createVirtualDisplay(
                "OpenChepsVD", captureW, captureH, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, worker);
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int w = image.getWidth();
        int h = image.getHeight();
        int rowPadding = rowStride - pixelStride * w;
        int bitmapW = w + rowPadding / pixelStride;

        Bitmap raw = Bitmap.createBitmap(bitmapW, h, Bitmap.Config.ARGB_8888);
        buf.rewind();
        raw.copyPixelsFromBuffer(buf);
        if (bitmapW != w) {
            Bitmap cropped = Bitmap.createBitmap(raw, 0, 0, w, h);
            raw.recycle();
            return cropped;
        }
        return raw;
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent stopIntent = new Intent(this, ScreenCaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .addAction(0, "Stop", stopPi)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(7, n);
        }
        startService(new Intent(this, com.opencheps.overlay.OverlayService.class));
    }

    @Override public void onDestroy() {
        isRunning=false;
        destroyed=true;
        if (worker != null) worker.removeCallbacksAndMessages(null);
        analysis.execute(() -> {try {if(engine!=null)engine.stop();}catch(Exception ignored){}});
        analysis.shutdown();
        if (virtualDisplay != null) virtualDisplay.release();
        if (reader != null) reader.close();
        if (projection != null) projection.stop();
        if (workerThread != null) workerThread.quitSafely();
        stopService(new Intent(this, com.opencheps.overlay.OverlayService.class));
        super.onDestroy();
    }
}

