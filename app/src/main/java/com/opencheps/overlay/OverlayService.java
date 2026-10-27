package com.opencheps.overlay;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.util.List;

public class OverlayService extends Service {

    private static final String TAG = "OC/Overlay";
    private static volatile OverlayService instance;
    private volatile com.opencheps.vision.ArrowOcclusion.Mark[] annotations=new com.opencheps.vision.ArrowOcclusion.Mark[0];
    private volatile com.opencheps.vision.ArrowOcclusion.Mark[] previousAnnotations=new com.opencheps.vision.ArrowOcclusion.Mark[0];
    private volatile long previousUntil;
    private volatile android.widget.FrameLayout selector;
    private boolean dismissed;

    private WindowManager wm;
    private ArrowOverlayView arrows;
    private FloatingPanel panel;

    @Nullable @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        arrows = new ArrowOverlayView(this);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // Android 12+ blocks touches through an opaque untrusted full-screen overlay.
        float maximum=.8f;
        if(Build.VERSION.SDK_INT>=31) {
            android.hardware.input.InputManager input=getSystemService(android.hardware.input.InputManager.class);
            if(input!=null)maximum=input.getMaximumObscuringOpacityForTouch();
        }
        lp.alpha=Math.min(.75f,Math.max(0,maximum-.05f));

        try { wm.addView(arrows, lp); }
        catch (Exception e) { Log.e(TAG, "addView arrows failed", e); }

        panel = new FloatingPanel(this);
        panel.attach();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(!com.opencheps.capture.ScreenCaptureService.isRunning) {dismissWindows();stopSelf();}
        return START_NOT_STICKY;
    }

    private void dismissWindows() {
        if(dismissed)return;dismissed=true;
        closeSelector();
        try {if(arrows!=null)wm.removeViewImmediate(arrows);}catch(Exception ignored){}
        if(panel!=null)panel.detach();
        annotations=new com.opencheps.vision.ArrowOcclusion.Mark[0];
        previousAnnotations=annotations;
        if(instance==this)instance=null;
    }
    @Override public void onDestroy() {dismissWindows();super.onDestroy();}

    public static void dismissNow() {
        OverlayService s=instance;
        if(s!=null)new android.os.Handler(s.getMainLooper()).post(s::dismissWindows);
    }
    private void remember(List<ArrowOverlayView.Arrow> a,Rect bounds) {
        java.util.ArrayList<com.opencheps.vision.ArrowOcclusion.Mark> marks=new java.util.ArrayList<>();
        float cell=bounds==null?80f:bounds.width()/8f;
        if(a!=null)for(int i=0;i<a.size();i++) {
            ArrowOverlayView.Arrow p=a.get(i);
            int letters=p.label==null?0:p.label.substring(p.label.indexOf('→')+1).length();
            marks.add(new com.opencheps.vision.ArrowOcclusion.Mark(p.sx,p.sy,p.ex,p.ey,cell,i==0,letters));
        }
        previousAnnotations=annotations;previousUntil=android.os.SystemClock.uptimeMillis()+500;
        annotations=marks.toArray(new com.opencheps.vision.ArrowOcclusion.Mark[0]);
    }
    public static com.opencheps.vision.PixelMask captureMask(float sx,float sy) {
        OverlayService s=instance;
        if(s==null)return com.opencheps.vision.PixelMask.NONE;
        java.util.ArrayList<com.opencheps.vision.ArrowOcclusion.Mark> all=new java.util.ArrayList<>(java.util.Arrays.asList(s.annotations));
        if(android.os.SystemClock.uptimeMillis()<s.previousUntil)all.addAll(java.util.Arrays.asList(s.previousAnnotations));
        com.opencheps.vision.ArrowOcclusion mask=new com.opencheps.vision.ArrowOcclusion(all.toArray(new com.opencheps.vision.ArrowOcclusion.Mark[0]));
        Rect panelBounds=s.panel==null?null:s.panel.screenBounds();
        return (x,y)->mask.excludes(x*sx,y*sy)||(panelBounds!=null&&panelBounds.contains((int)(x*sx),(int)(y*sy)));
    }
    public static boolean selectingBoard(){return instance!=null&&instance.selector!=null;}

    private void closeSelector() {
        if(selector!=null) {try{wm.removeViewImmediate(selector);}catch(Exception ignored){}selector=null;}
        if(panel!=null)panel.setVisible(true);
    }
    public static void selectBoard(Rect suggested,java.util.function.Consumer<Rect> apply) {
        OverlayService s=instance;if(s==null)return;
        new android.os.Handler(s.getMainLooper()).post(() -> {
            s.closeSelector();if(s.panel!=null)s.panel.setVisible(false);
            s.selector=new android.widget.FrameLayout(s);
            BoardSelectionView selection=new BoardSelectionView(s,suggested);
            s.selector.addView(selection);
            android.widget.LinearLayout buttons=new android.widget.LinearLayout(s);
            buttons.setPadding(12,12,12,12);buttons.setBackgroundColor(0xee101925);
            for(String title:new String[]{"Применить","Авто","Отмена"}) {
                android.widget.Button b=new android.widget.Button(s);b.setText(title);b.setTextSize(12);
                b.setOnClickListener(v -> {
                    Rect chosen=title.equals("Применить")?selection.screenSelection():null;
                    s.closeSelector();if(!title.equals("Отмена"))apply.accept(chosen);
                });
                buttons.addView(b,new android.widget.LinearLayout.LayoutParams(0,android.view.ViewGroup.LayoutParams.WRAP_CONTENT,1));
            }
            android.widget.FrameLayout.LayoutParams bp=new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
            s.selector.addView(buttons,bp);
            WindowManager.LayoutParams sp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
            s.selector.setFitsSystemWindows(true);
            sp.gravity=Gravity.TOP|Gravity.START;
            try{s.wm.addView(s.selector,sp);}catch(Exception e){s.closeSelector();}
        });
    }

    public static FloatingPanel panel() {
        return instance == null ? null : instance.panel;
    }

    public static void pushArrows(List<ArrowOverlayView.Arrow> a, Rect bounds) {
        if (instance == null) return;
        instance.remember(a,bounds);
        instance.arrows.setBoardBounds(bounds);
        instance.arrows.setArrows(a);
    }

    public static void setBoardBounds(Rect bounds) {
        if(instance!=null){instance.arrows.setBoardBounds(bounds);if(instance.panel!=null)instance.panel.avoidBoard(bounds);}
    }

    public static void setBoardFrameColor(int color) {
        if (instance != null) instance.arrows.setBoardFrameColor(color);
    }

    public static void clearArrowsOnly() {
        if(instance!=null){instance.remember(null,null);instance.arrows.clearArrowsOnly();}
    }

    public static void clearAll() {
        if(instance!=null){instance.remember(null,null);instance.arrows.clearAll();}
    }
}

