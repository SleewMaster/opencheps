package com.opencheps.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.opencheps.capture.ScreenCaptureService;
import com.opencheps.engine.*;
import com.opencheps.util.*;
import com.opencheps.vision.ChessPosition;
import java.util.*;
import java.util.concurrent.*;

/** Camera stays inside this visible activity. No screen capture or overlay permission is used. */
public class CameraActivity extends AppCompatActivity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService vision=Executors.newSingleThreadExecutor(),analysis=Executors.newSingleThreadExecutor();
    private final StockfishEngine engine=new StockfishEngine();
    private PreviewView preview;
    private CameraBoardView boardView;
    private CameraHintBoardView hintBoard;
    private ChessPosition verifiedPosition;
    private PositionHistory.Snapshot verifiedSearch;
    private final CompletedAdvice adviceCache=new CompletedAdvice();
    private String activeSearchKey="";
    private TextView status,moveLabel;
    private Button apply,pause,orientation;
    private CheckBox voiceToggle;
    private Button repeat;
    private CameraSpeech speech;
    private final MoveAnnouncement announcement=new MoveAnnouncement();
    private boolean displayComplete;
    private ProcessCameraProvider provider;
    private Camera camera;
    private CameraBoard board;
    private Side perspective=Side.WHITE;
    private boolean active,processing,paused,engineReady,engineFailed,analysisBusy,streaming,destroyed;
    private int generation;
    private final CameraAdviceGuard adviceGuard=new CameraAdviceGuard();
    private String verifiedFen="",displayFen="";
    private List<StockfishEngine.Pv> displayed=Collections.emptyList();
    private Side userSide;

    private final ActivityResultLauncher<String> permission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{
        if(granted)openCamera();else status.setText("Камера не разрешена. Нажми «Начать». Если запрет сохранён — разреши камеру в настройках приложения.");
    });

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScreenCaptureService.stopSession(this);userSide="b".equals(Prefs.userSide(this))?Side.BLACK:Side.WHITE;
        buildUi();
        speech=new CameraSpeech(this,(ready,message)->{
            if(destroyed)return;
            voiceToggle.setText(ready?"Озвучивать ход":"Голос: "+message);
            updateRepeat();
            if(ready)announce(false);
        });
        if(voiceToggle.isChecked())speech.start();
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)openCamera();
        else permission.launch(Manifest.permission.CAMERA);
        analysis.execute(()->{
            try{engine.start(getApplicationContext());main.post(()->{if(!destroyed){engineReady=true;if(!boardView.editing())status.setText("Покажи начальную расстановку и удерживай телефон");}});}
            catch(Exception e){main.post(()->{if(!destroyed){engineFailed=true;status.setText("Не удалось запустить Stockfish. Закрой и открой режим камеры.");}});}
        });
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setTextSize(12);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);return b;}
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0b121e);root.setPadding(dp(12),dp(8),dp(12),dp(4));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top);
        Button back=button("‹ Назад");top.addView(back,new LinearLayout.LayoutParams(dp(90),dp(48)));back.setOnClickListener(v->finish());
        TextView title=text("КАМЕРА  /  LIVE",14,0xffb9f66e);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button close=button("Закрыть");top.addView(close,new LinearLayout.LayoutParams(dp(86),dp(48)));close.setOnClickListener(v->finish());
        TextView help=text("Наведи на 2D-доску в начальной расстановке и нажми «Начать». Рамку подгоню к клеткам. Подсказка: выделенная фигура → зелёная клетка; справа появится ровная доска.",12,0xff93a8c3);help.setPadding(0,dp(4),0,dp(8));root.addView(help);
        FrameLayout stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);stage.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        boardView=new CameraBoardView(this);stage.addView(boardView,new FrameLayout.LayoutParams(-1,-1));
        hintBoard=new CameraHintBoardView(this);FrameLayout.LayoutParams inset=new FrameLayout.LayoutParams(dp(144),dp(144),Gravity.BOTTOM|Gravity.RIGHT);inset.setMargins(dp(8),dp(8),dp(8),dp(8));stage.addView(hintBoard,inset);
        boardView.onSizeChanged=()->reset(true);
        preview.getPreviewStreamState().observe(this,state->{
            streaming=state==PreviewView.StreamState.STREAMING;
            if(streaming)android.util.Log.i("OC/Camera","CAMERA_STREAMING");
            else {invalidateAdvice();if(active&&!boardView.editing())status.setText("Поток камеры остановлен · жду изображение");}
        });
        // Tap the visible board to focus when corner editing is finished.
        preview.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_UP&&camera!=null){
                MeteringPoint point=preview.getMeteringPointFactory().createPoint(event.getX(),event.getY());
                camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).setAutoCancelDuration(3,TimeUnit.SECONDS).build());v.performClick();
            }return true;
        });
        // Fixed line counts keep status changes from resizing the preview and invalidating
        // its calibrated corners, including on small displays and with larger system fonts.
        status=text("Разреши камеру и выставь углы доски",12,0xff93a8c3);status.setLines(3);status.setGravity(Gravity.CENTER_VERTICAL);root.addView(status);
        moveLabel=text("Подсказка появится здесь",21,0xffeaf2ff);moveLabel.setLines(2);moveLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);moveLabel.setPadding(0,0,0,dp(6));root.addView(moveLabel);
        LinearLayout buttons=new LinearLayout(this);root.addView(buttons);
        orientation=button("Снизу: белые");buttons.addView(orientation,new LinearLayout.LayoutParams(0,dp(52),1));orientation.setOnClickListener(v->{perspective=perspective==Side.WHITE?Side.BLACK:Side.WHITE;orientation.setText(perspective==Side.WHITE?"Снизу: белые":"Снизу: чёрные");reset(true);});
        apply=button("Начать");buttons.addView(apply,new LinearLayout.LayoutParams(0,dp(52),1));apply.setOnClickListener(v->{
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){permission.launch(Manifest.permission.CAMERA);return;}
            if(!boardView.editing()){invalidateAdvice();boardView.setEditing(true);apply.setText("Применить");status.setText("Выставь углы клеток и нажми «Применить». Текущая партия сохранится.");return;}
            if(!streaming){openCamera();status.setText("Дождись изображения камеры");return;}
            if(!CameraGeometry.valid(boardView.corners(),boardView.getWidth(),boardView.getHeight())){status.setText("Углы должны образовывать большую выпуклую доску без пересечений");return;}
            if(board==null){reset(false);board=new CameraBoard(perspective);}else{invalidateAdvice();board.requestReframe();}boardView.setEditing(false);apply.setText("Углы заново");paused=false;pause.setText("Пауза");status.setText("Ищу сетку и подтверждаю положение фигур");
        });
        pause=button("Пауза");buttons.addView(pause,new LinearLayout.LayoutParams(0,dp(52),1));pause.setOnClickListener(v->{
            paused=!paused;invalidateAdvice();pause.setText(paused?"Продолжить":"Пауза");status.setText(paused?"Пауза · камера открыта":"Продолжаю распознавание");
        });
        LinearLayout audio=new LinearLayout(this);audio.setGravity(Gravity.CENTER_VERTICAL);root.addView(audio,new LinearLayout.LayoutParams(-1,dp(48)));
        voiceToggle=new CheckBox(this);voiceToggle.setText("Озвучивать ход");voiceToggle.setTextSize(12);voiceToggle.setTextColor(0xffb9f66e);voiceToggle.setChecked(Prefs.get(this).getBoolean("camera_voice",true));audio.addView(voiceToggle,new LinearLayout.LayoutParams(0,-1,1));
        repeat=button("Повторить");repeat.setEnabled(false);audio.addView(repeat,new LinearLayout.LayoutParams(dp(100),-1));repeat.setOnClickListener(v->announce(true));
        voiceToggle.setOnCheckedChangeListener((v,enabled)->{
            Prefs.get(this).edit().putBoolean("camera_voice",enabled).apply();
            if(speech==null)return;
            if(enabled){if(speech.ready())announce(true);else speech.start();}
            else stopSpeech();
            updateRepeat();
        });
        TextView privacy=text("Кадры обрабатываются на телефоне, без сохранения. Настоящие объёмные фигуры пока не поддерживаются.",10,0xff829ab8);privacy.setPadding(0,dp(4),0,dp(4));root.addView(privacy);
    }
    private void openCamera(){
        if(destroyed||provider!=null)return;
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            if(destroyed)return;
            try{
                provider=future.get();
                if(!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)){status.setText("Задняя камера недоступна на этом устройстве");provider=null;return;}
                Preview p=new Preview.Builder().build();p.setSurfaceProvider(preview.getSurfaceProvider());
                camera=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p);
                camera.getCameraInfo().getCameraState().observe(this,s->{if(s.getError()!=null){invalidateAdvice();status.setText("Камера недоступна. Закрой другие приложения с камерой и открой этот режим снова.");}});
                status.setText("Выставь четыре угла и нажми «Начать»");
            }catch(Exception e){provider=null;status.setText("Не удалось открыть камеру. Проверь разрешение в настройках приложения.");}
        },ContextCompat.getMainExecutor(this));
    }
    private void reset(boolean editing){
        invalidateAdvice();adviceCache.clear();board=null;paused=false;if(pause!=null)pause.setText("Пауза");
        announcement.reset();
        if(boardView!=null)boardView.setEditing(editing);
        if(apply!=null)apply.setText("Начать");
        if(status!=null)status.setText("Выставь углы на начальной позиции. Снизу должен быть выбранный цвет.");
    }
    private void clearHintViews(){if(boardView!=null)boardView.clear();if(hintBoard!=null)hintBoard.clear();}
    private void stopSpeech(){if(speech!=null&&speech.stop())announcement.reset();}
    private void invalidateAdvice(){generation++;verifiedFen="";verifiedPosition=null;verifiedSearch=null;displayFen="";displayComplete=false;displayed=Collections.emptyList();adviceGuard.reset();activeSearchKey="";engine.cancelSearch();stopSpeech();updateRepeat();if(boardView!=null)clearHintViews();if(moveLabel!=null)moveLabel.setText("Подсказка появится здесь");}
    private void suspendAdvice(boolean hide){
        // A blurred frame must not restart a valid position's engine budget.
        // Analysis may finish into the history-keyed cache; rendering waits for verification.
        generation++;verifiedFen="";verifiedSearch=null;updateRepeat();
        if(hide){stopSpeech();clearHintViews();moveLabel.setText("Стабилизирую изображение…");}
        else {final int token=generation;main.postDelayed(()->{if(active&&!destroyed&&token==generation&&verifiedFen.isEmpty()){stopSpeech();clearHintViews();moveLabel.setText("Стабилизирую изображение…");}},adviceGuard.remaining(SystemClock.uptimeMillis()));}
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!active||destroyed)return;main.postDelayed(this,220);
        if(paused||processing||board==null||boardView.editing()||!streaming)return;
        Bitmap frame=preview.getBitmap();if(frame==null){invalidateAdvice();return;}
        final int token=generation;final CameraBoard session=board;final float[] corners=boardView.corners();processing=true;
        vision.execute(()->{
            CameraBoard.FrameResult result=null;
            try{result=session.readFrame(frame,corners);}catch(RuntimeException e){android.util.Log.w("OC/Camera","frame rejected",e);}
            finally{frame.recycle();}
            CameraBoard.FrameResult observation=result;main.post(()->{processing=false;if(!active||destroyed||token!=generation||paused||session!=board)return;
                if(observation!=null&&observation.corners!=null)boardView.setTrackedCorners(observation.corners);
                ChessPosition answer=observation==null?null:observation.position;
                if(answer==null||observation.history==null){
                    boolean hardLoss=observation==null||!observation.tracked||!observation.readable;
                    boolean hold=adviceGuard.failure(SystemClock.uptimeMillis(),!hardLoss,!hardLoss,observation!=null&&observation.pendingChange);
                    suspendAdvice(!hold);
                    status.setText(observation!=null&&!observation.problem.isEmpty()?observation.problem:"Жду чёткий кадр доски");return;
                }
                adviceGuard.verified(SystemClock.uptimeMillis());
                verifiedPosition=answer;verifiedSearch=observation.history;String fen=verifiedSearch.fen;
                if(!fen.equals(verifiedFen)){
                    generation++;if(!verifiedSearch.key.equals(activeSearchKey)){activeSearchKey="";engine.cancelSearch();}verifiedFen=fen;
                    if(!fen.equals(displayFen)){displayFen="";displayComplete=false;displayed=Collections.emptyList();stopSpeech();clearHintViews();moveLabel.setText("Подсказка появится здесь");}
                    updateRepeat();
                }
                if(answer.turn!=userSide){status.setText("Жду ход соперника · доска распознана");return;}
                List<StockfishEngine.Pv> cached=adviceCache.get(verifiedSearch.key);
                if(cached!=null){render(cached,fen,generation,true);return;}
                if(!engineReady){status.setText(engineFailed?"Stockfish недоступен — открой режим заново":"Запускаю Stockfish…");return;}
                if(!analysisBusy)analyze(verifiedSearch);
            });
        });
    }};
    private void analyze(PositionHistory.Snapshot search){
        final String fen=search.fen;
        analysisBusy=true;activeSearchKey=search.key;final CameraBoard session=board;
        int time=Prefs.thinkMs(this),count=Prefs.multiPv(this);status.setText("Считаю · "+time/1000f+" с");
        analysis.execute(()->{
            try{List<StockfishEngine.Pv> moves=engine.topMoves(search,time,count);
                main.post(()->{
                    boolean completed=search.key.equals(activeSearchKey);analysisBusy=false;activeSearchKey="";
                    if(!completed||destroyed||board!=session||moves.isEmpty()||MoveAnnouncement.label(fen,moves.get(0).uci)==null)return;
                    adviceCache.put(search.key,moves);
                    if(verifiedSearch!=null&&search.key.equals(verifiedSearch.key))render(adviceCache.get(search.key),fen,generation,true);
                });
            }catch(Exception e){main.post(()->{analysisBusy=false;activeSearchKey="";if(!destroyed&&verifiedSearch!=null&&search.key.equals(verifiedSearch.key))status.setText("Ошибка анализа · повторяю");});}
        });
    }
    private void render(List<StockfishEngine.Pv> moves,String fen,int token,boolean complete){
        if(!complete||!active||destroyed||paused||token!=generation||!fen.equals(verifiedFen)||moves.isEmpty())return;
        if(MoveAnnouncement.label(fen,moves.get(0).uci)==null){status.setText("Перепроверяю допустимость хода");return;}
        displayed=new ArrayList<>(moves);displayFen=fen;displayComplete=complete;boardView.setMoves(moves,perspective);
        showMove(moves.get(0),complete);updateRepeat();if(complete)announce(false);
    }
    private void showMove(StockfishEngine.Pv best,boolean complete){
        String uci=best.uci;
        String score=best.mate?"мат "+Math.abs(100000-Math.abs(best.cp)):String.format(Locale.forLanguageTag("ru"),"%+.2f",best.cp/100f);
        String label=MoveAnnouncement.label(verifiedFen,uci);
        moveLabel.setText((label==null?uci.substring(0,2)+" → "+uci.substring(2,4):label)+(best.depth>0?"\nОценка "+score:""));
        if(verifiedPosition!=null)hintBoard.show(verifiedPosition,uci,perspective);
        status.setText((complete?"Лучший ход":"Уточняю ход")+" · "+(userSide==Side.WHITE?"белые":"чёрные"));
    }
    private boolean canAnnounce(){return active&&!destroyed&&!paused&&speech!=null&&speech.ready()&&voiceToggle.isChecked()&&displayComplete&&!displayed.isEmpty()&&!verifiedFen.isEmpty()&&verifiedFen.equals(displayFen);}
    private void updateRepeat(){if(repeat!=null)repeat.setEnabled(canAnnounce());}
    private void announce(boolean requested){
        if(!canAnnounce())return;
        String uci=displayed.get(0).uci;
        String message=requested?MoveAnnouncement.describe(verifiedFen,uci):announcement.pending(verifiedFen,uci,displayComplete);
        if(message!=null&&speech.speak(message))announcement.accepted(verifiedFen);
    }
    @Override protected void onResume(){super.onResume();active=true;main.removeCallbacks(tick);main.post(tick);}
    @Override protected void onPause(){active=false;main.removeCallbacks(tick);invalidateAdvice();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;active=false;main.removeCallbacksAndMessages(null);if(speech!=null)speech.close();engine.cancelSearch();vision.shutdown();analysis.execute(engine::stop);analysis.shutdown();if(provider!=null)provider.unbindAll();super.onDestroy();}
}
