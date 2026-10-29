package com.opencheps.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
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
import com.opencheps.vision.PieceTemplates;
import com.opencheps.vision.DiagramReader;
import com.opencheps.vision.PositionSeed;
import com.opencheps.camera.MoveAnnouncement;
import com.opencheps.engine.CompletedAdvice;

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
    public static final String ACTION_SELECT_BOARD="com.opencheps.SELECT_BOARD";
    public static final String ACTION_SETUP_POSITION="com.opencheps.SETUP_POSITION";
    private final List<PieceTemplates> templateBanks=new ArrayList<>();
    private final CompletedAdvice adviceCache=new CompletedAdvice();
    private boolean setupRequested,setupOpen,autoSetupOff;
    private char[][] startupCandidate;
    private int startupRepeats;
    private Rect manualBoardScreen;
    private int uncertainFrames;
    private final Handler controls=new Handler(android.os.Looper.getMainLooper());

    /** Close windows immediately, even if capture/engine teardown is still running. */
    public static void stopSession(Context context) {
        isRunning=false;
        OverlayService.dismissNow();
        context.stopService(new Intent(context,OverlayService.class));
        context.stopService(new Intent(context,ScreenCaptureService.class));
    }

    private static final int[]   ARROW_COLORS = { 0xFFB9F66E, 0xFF67D7FF, 0xFFFFC56B };
    private static final float[] ARROW_WIDTHS = { 18f, 13f, 10f };
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
    private volatile String engineFailure;
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
            stopSession(this);stopSelf(startId);
            return START_NOT_STICKY;
        }
        if(ACTION_SETUP_POSITION.equals(intent.getAction())) {
            if(worker!=null&&!destroyed)worker.post(()->{setupRequested=true;autoSetupOff=false;panelStatus("Покажи текущую доску…");});
            return START_NOT_STICKY;
        }
        if(ACTION_SELECT_BOARD.equals(intent.getAction())) {
            if(worker!=null&&!destroyed)worker.post(this::selectBoard);
            else {stopSession(this);stopSelf(startId);}
            return START_NOT_STICKY;
        }
        if(isRunning)return START_NOT_STICKY;

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
            @Override public void onStop() {if(!destroyed)stopSession(ScreenCaptureService.this);}
        }, null);

        workerThread = new HandlerThread("oc-worker", Thread.NORM_PRIORITY);
        workerThread.start();
        worker = new Handler(workerThread.getLooper());

        engine = new StockfishEngine();
        classifier = new PieceClassifier();
        loadTemplateBanks();

        analysis.execute(() -> {
            try {
                engine.start(getApplicationContext());
                worker.post(() -> {engineReady = true; panelStatus("Движок готов · " + userSide);});
            } catch (Exception e) {
                Log.e(TAG, "engine start failed", e);
                engineFailure="Ошибка движка: "+e.getMessage();panelStatus(engineFailure);
            }
        });

        setupVirtualDisplay();
        wirePanel();
        worker.postDelayed(sampleLoop, 200);
        panelStatus("Запуск · " + userSide);
        return START_NOT_STICKY;
    }

    private void wirePanel() {
        controls.postDelayed(() -> {
            if(destroyed)return;
            FloatingPanel p = OverlayService.panel();
            if(p==null){wirePanel();return;}
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
                    @Override public void onStop() {if(!destroyed)stopSession(ScreenCaptureService.this);}
                    @Override public void onRecalibrate() {
                        worker.post(() -> {
                            classifier.reset();resetGameState();autoSetupOff=false;startupCandidate=null;startupRepeats=0;adviceCache.clear();OverlayService.clearAll();panelEval("");
                            panelStatus("Ищу текущую позицию");
                        });
                    }
                    @Override public void onSideChange(Side newSide) {
                        worker.post(() -> {
                            userSide=newSide;Prefs.setUserSide(getApplicationContext(),newSide==Side.BLACK?"b":"w");
                            generation++;engine.cancelSearch();lastEngineFen="";candidateGrid=null;candidateRepeats=0;
                            currentArrows.clear();currentPvs.clear();OverlayService.clearAll();panelEval("");
                            panelStatus("Играешь "+(newSide==Side.WHITE?"белыми":"чёрными"));
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
        uncertainFrames=0;
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

    private void selectBoard() {
        engine.cancelSearch();generation++;positionVerified=false;
        currentArrows.clear();currentPvs.clear();OverlayService.clearAll();panelEval("");
        Rect suggested=manualBoardScreen!=null?manualBoardScreen:lastKnownBoard==null?null:boardBoundsOnScreen(lastKnownBoard);
        OverlayService.selectBoard(suggested,chosen -> {
            if(destroyed)return;
            worker.post(() -> {
                manualBoardScreen=chosen==null?null:new Rect(chosen);
                Prefs.saveBoard(this,manualBoardScreen,screenW,screenH);
                classifier.reset();resetGameState();drainReader();
                autoSetupOff=false;startupCandidate=null;startupRepeats=0;panelStatus(chosen==null?"Авто · покажи доску":"Рамка сохранена · покажи доску");
            });
        });
    }

    private final Runnable sampleLoop = new Runnable() {
        @Override public void run() {
            if(destroyed)return;
            try {
                if(paused||setupOpen||OverlayService.selectingBoard())drainReader();
                else if(!refreshDisplaySize())sampleOnce();
            }catch(Throwable t){Log.e(TAG,"sample error",t);}
            if(!destroyed&&worker!=null)worker.postDelayed(this,220);
        }
    };

    /** Resize the existing projection rather than reusing the single-use consent. */
    private boolean refreshDisplaySize() {
        DisplayMetrics dm=new DisplayMetrics();
        ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        if(dm.widthPixels==screenW&&dm.heightPixels==screenH)return false;
        screenW=dm.widthPixels;screenH=dm.heightPixels;density=dm.densityDpi;
        float scale=Math.min(1f,1200f/Math.max(screenW,screenH));
        captureW=Math.round(screenW*scale);captureH=Math.round(screenH*scale);
        capToScreenX=(float)screenW/captureW;capToScreenY=(float)screenH/captureH;
        ImageReader old=reader;
        reader=ImageReader.newInstance(captureW,captureH,PixelFormat.RGBA_8888,4);
        virtualDisplay.setSurface(null);virtualDisplay.resize(captureW,captureH,density);virtualDisplay.setSurface(reader.getSurface());
        if(old!=null)old.close();
        manualBoardScreen=Prefs.loadBoard(this,screenW,screenH);
        generation++;engine.cancelSearch();lastEngineFen="";positionVerified=false;candidateGrid=null;candidateRepeats=0;
        currentArrows.clear();currentPvs.clear();OverlayService.clearAll();panelEval("");
        panelStatus("Экран повёрнут · ищу доску");return true;
    }

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

            classifier.setAnnotationMask(OverlayService.captureMask(capToScreenX,capToScreenY));
            BoardDetector.Board board;
            if(manualBoardScreen!=null) {
                int left=Math.round(manualBoardScreen.left/capToScreenX),top=Math.round(manualBoardScreen.top/capToScreenY);
                int side=Math.round(manualBoardScreen.width()/capToScreenX);
                board=left>=0&&top>=0&&left+side<=bmp.getWidth()&&top+side<=bmp.getHeight()
                        ?new BoardDetector.Board(left,top,side,1):null;
            } else board=BoardDetector.locate(bmp);

            if (board == null) {
                invalidateSearch();startupCandidate=null;startupRepeats=0;OverlayService.clearArrowsOnly();
                lostBoardCount++;
                if (lostBoardCount < LOST_BOARD_THRESHOLD) {
                    if (lostBoardCount == 1) panelStatus("Ищу доску…");
                    return;
                }
                panelStatus("Доска не найдена · нажми ▣ ДОСКА");
                OverlayService.clearAll();
                return;
            }
            lostBoardCount = 0;
            lastKnownBoard = board;
            Rect screenBounds = boardBoundsOnScreen(board);
            OverlayService.setBoardBounds(screenBounds);

            if(setupRequested){setupRequested=false;openPosition(bmp,board,true);return;}
            if (!classifier.isTrained()) {
                boolean ok = classifier.tryBootstrap(bmp, board);
                if (!ok) {
                    if(!autoSetupOff&&openPosition(bmp,board,false))return;
                    panelStatus("Позиция посреди партии · нажми ПОЗИЦИЯ");
                    OverlayService.setBoardFrameColor(0x66FFB300);
                    return;
                }
                rememberTheme();
                Side detected = classifier.bootstrapSide();
                OverlayService.setBoardFrameColor(0x6600C853);
                positionVerified=true;
                confirmedGrid = startingGridFor(detected);
                observedStm = Side.WHITE;
                candidateGrid = null;
                candidateRepeats = 0;
                panelStatus("Готов · твоя сторона: " + userSide);
                if (observedStm == userSide) {
                    queryEngineAndRender(board, bmp);
                } else {
                    panelStatus("Жду ход соперника");
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
                // Some apps flip the view without changing the actual position.
                float[][] rotated=new float[64][];for(int i=0;i<64;i++)rotated[i]=costs[63-i];
                VisualMoveTracker.Match flipped=VisualMoveTracker.match(
                        new ChessPosition(canonical,observedStm,castlingRights,enPassantTarget),rotated);
                if(flipped!=null) {
                    classifier.setBoardPerspective(classifier.bootstrapSide()==Side.WHITE?Side.BLACK:Side.WHITE);
                    confirmedGrid=FenBuilder.flip(confirmedGrid);match=flipped;candidateGrid=null;candidateRepeats=0;
                    generation++;engine.cancelSearch();lastEngineFen="";currentPvs.clear();currentArrows.clear();OverlayService.clearAll();
                }
            }
            if(match==null) {
                invalidateSearch();candidateGrid=null;candidateRepeats=0;
                if(uncertainFrames>=3) {
                    PieceClassifier fresh=new PieceClassifier();
                    fresh.setAnnotationMask(OverlayService.captureMask(capToScreenX,capToScreenY));
                    if(fresh.tryBootstrap(bmp,board)) {
                        classifier=fresh;resetGameState();rememberTheme();adviceCache.clear();confirmedGrid=startingGridFor(fresh.bootstrapSide());
                        positionVerified=true;panelStatus("Новая партия распознана");
                        if(userSide==Side.WHITE)queryEngineAndRender(board,bmp);return;
                    }
                }
                if(++uncertainFrames<3)return;
                OverlayService.clearArrowsOnly();currentArrows.clear();currentPvs.clear();
                lastEngineFen="";panelEval("");panelStatus("Проверь доску · ПОЗИЦИЯ / ▣ ДОСКА");
                return;
            }
            uncertainFrames=0;
            if(!engineReady)panelStatus(engineFailure==null?"Доска готова · запускаю движок":engineFailure);
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
            invalidateSearch();
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

    private void invalidateSearch(){
        if(positionVerified){generation++;if(engine!=null)engine.cancelSearch();}
        positionVerified=false;
    }
    private void loadTemplateBanks(){
        try(java.io.InputStream in=new java.io.FileInputStream(new java.io.File(getFilesDir(),"screen-templates.txt"))){templateBanks.add(PieceTemplates.read(in));}catch(Exception ignored){}
        try(java.io.InputStream in=getAssets().open("vision/neo-v1.txt")){templateBanks.add(PieceTemplates.read(in));}catch(Exception e){Log.w(TAG,"Built-in descriptors unavailable",e);}
    }
    private void rememberTheme(){
        PieceTemplates bank=classifier.exportTemplates();if(!bank.complete())return;
        templateBanks.add(0,bank);while(templateBanks.size()>3)templateBanks.remove(1);
        // Persist only generic piece descriptors, never the screen or the current position.
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(getFilesDir(),"screen-templates.txt"))){bank.write(out);}catch(Exception e){Log.w(TAG,"Could not save theme descriptors",e);}
    }
    private boolean openPosition(Bitmap bmp,BoardDetector.Board board,boolean force){
        PieceClassifier bestClassifier=null;DiagramReader.Result best=null;float score=Float.POSITIVE_INFINITY;
        List<PieceTemplates> banks=new ArrayList<>(templateBanks);
        if(classifier.isTrained())banks.add(0,classifier.exportTemplates());
        for(PieceTemplates bank:banks){
            PieceClassifier reader=new PieceClassifier();reader.useTemplates(bank,userSide);
            reader.setAnnotationMask(OverlayService.captureMask(capToScreenX,capToScreenY));
            DiagramReader.Result result=DiagramReader.read(reader.labelCosts(bmp,board));if(result==null)continue;
            float rank=result.error;for(boolean u:result.uncertain)if(u)rank+=.005f;
            if(rank<score){score=rank;best=result;bestClassifier=reader;}
        }
        if(best==null){panelStatus("Нет шаблонов фигур · покажи начальную позицию один раз");return false;}
        Side perspective=classifier.isTrained()?classifier.bootstrapSide():userSide;
        char[][] canonical=perspective==Side.WHITE?best.screen:FenBuilder.flip(best.screen);
        if(!FenBuilder.validate(canonical).ok){
            Side alternate=ChessPosition.opposite(perspective);char[][] other=alternate==Side.WHITE?best.screen:FenBuilder.flip(best.screen);
            if(FenBuilder.validate(other).ok){perspective=alternate;canonical=other;}
        }
        if(!force){
            if(!FenBuilder.validate(canonical).ok||score>.10f){startupCandidate=null;startupRepeats=0;return false;}
            if(!gridsEqual(best.screen,startupCandidate)){startupCandidate=ChessPosition.copy(best.screen);startupRepeats=1;panelStatus("Читаю текущую позицию…");return true;}
            if(++startupRepeats<2)return true;
        }
        final PieceTemplates base=bestClassifier.exportTemplates();
        final float[][] features=bestClassifier.snapshotFeatures(bmp,board);
        setupOpen=true;invalidateSearch();generation++;engine.cancelSearch();lastEngineFen="";
        currentPvs.clear();currentArrows.clear();OverlayService.clearAll();panelEval("");
        OverlayService.editPosition(best.screen,best.uncertain,perspective,confirmedGrid==null?userSide:observedStm,seed->{
            if(destroyed)return;
            worker.post(()->{
                setupOpen=false;autoSetupOff=true;startupCandidate=null;startupRepeats=0;
                if(seed==null){panelStatus("Нажми ПОЗИЦИЯ, когда будешь готов");return;}
                resetGameState();adviceCache.clear();
                char[][] screen=seed.perspective==Side.WHITE?ChessPosition.copy(seed.position.board):FenBuilder.flip(seed.position.board);
                classifier=new PieceClassifier();classifier.useTemplates(PieceClassifier.withObserved(base,screen,features),seed.perspective);
                confirmedGrid=screen;observedStm=seed.position.turn;castlingRights=seed.position.rights;enPassantTarget=seed.position.ep;
                halfmoveClock=seed.halfmove;fullmoveNumber=seed.fullmove;
                positionVerified=false;panelStatus("Проверяю подтверждённую позицию…");drainReader();
            });
        });return true;
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
        List<StockfishEngine.Pv> cached=adviceCache.get(fen);
        if(cached!=null){lastEngineFen=fen;renderAnalysis(cached,fen,token,true);return;}
        char[][] canonical=classifier.bootstrapSide()==Side.BLACK?FenBuilder.flip(confirmedGrid):confirmedGrid;
        List<ChessPosition> legal=new ChessPosition(canonical,observedStm,castlingRights,enPassantTarget).successors();
        if(legal.isEmpty()){lastEngineFen=fen;panelStatus(ChessPosition.inCheck(canonical,observedStm)?"Мат · партия завершена":"Пат · партия завершена");return;}
        if(legal.size()==1){
            List<StockfishEngine.Pv> forced=java.util.Collections.singletonList(new StockfishEngine.Pv(legal.get(0).move,0,false,0));
            adviceCache.put(fen,forced);lastEngineFen=fen;renderAnalysis(forced,fen,token,true);panelStatus("Единственный допустимый ход");return;
        }
        final int think=Prefs.thinkMs(getApplicationContext());
        final int multiPv=Prefs.multiPv(getApplicationContext());
        analysisBusy=true;lastEngineFen=fen;
        panelStatus("Считаю · "+think/1000f+" с");
        analysis.execute(() -> {
            try {
                // Only terminal bestmove may become an arrow. Search iterations stay internal.
                final List<StockfishEngine.Pv> pvs=engine.topMoves(fen,think,multiPv);
                if(destroyed)return;
                worker.post(() -> {
                    analysisBusy=false;
                    if(token!=generation||paused||!positionVerified||observedStm!=userSide||candidateGrid!=null
                            ||!fen.equals(fenOf(confirmedGrid))) {lastEngineFen="";return;}
                    if(pvs.isEmpty()) {lastEngineFen="";panelStatus("Нет допустимых ходов");return;}
                    if(MoveAnnouncement.label(fen,pvs.get(0).uci)==null){lastEngineFen="";panelStatus("Перепроверяю допустимость хода");return;}
                    adviceCache.put(fen,pvs);
                    renderAnalysis(adviceCache.get(fen),fen,token,true);
                });
            } catch(Exception e) {
                Log.w(TAG,"analysis failed",e);
                if(!destroyed)worker.post(() -> {analysisBusy=false;lastEngineFen="";panelStatus("Повторяю анализ…");});
            }
        });
    }

    private void renderAnalysis(List<StockfishEngine.Pv> pvs,String fen,int token,boolean complete) {
        if(!complete||destroyed||paused||!positionVerified||token!=generation||observedStm!=userSide
                ||candidateGrid!=null||!fen.equals(fenOf(confirmedGrid))||pvs.isEmpty())return;
        currentPvs=new ArrayList<>(pvs);
        BoardDetector.Board latest=lastKnownBoard;
        currentArrows=buildArrows(currentPvs,latest);lastDisplayedArrowFen=fen;
        OverlayService.pushArrows(currentArrows,boardBoundsOnScreen(latest));
        StockfishEngine.Pv best=pvs.get(0);
        String eval=best.mate ? (best.cp>0?"Мат за ":"Мат нам за ")+Math.abs(plyToMate(best.cp))
                :String.format(java.util.Locale.US,"%+.2f",best.cp/100.0);
        String name=MoveAnnouncement.label(fen,best.uci);
        panelEval((name==null?formatUci(best.uci):name)+(best.depth>0?"\n"+eval:""));
        panelStatus(best.depth>0?"Готово · глубина "+best.depth:"Ход готов");
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
        String f = FenBuilder.build(grid, classifier.bootstrapSide(), observedStm,
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
        manualBoardScreen=Prefs.loadBoard(this,screenW,screenH);
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
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,com.opencheps.ui.MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true).setContentIntent(open)
                .addAction(0, "Выключить оверлей", stopPi)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(7, n);
        }
        startService(new Intent(this, com.opencheps.overlay.OverlayService.class));
    }

    @Override public void onTaskRemoved(Intent rootIntent){stopSession(this);super.onTaskRemoved(rootIntent);}

    @Override public void onDestroy() {
        OverlayService.dismissNow();
        controls.removeCallbacksAndMessages(null);
        isRunning=false;
        destroyed=true;
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if(engine!=null)engine.cancelSearch();
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

