package com.opencheps.camera;

import android.graphics.*;
import com.opencheps.util.Side;
import com.opencheps.vision.*;

/** Each instance belongs to one calibration and is used only on the vision executor. */
public final class CameraBoard {
    public static final int SIZE=640;
    private final PieceClassifier classifier=new PieceClassifier();
    private final FrameConsensus consensus=new FrameConsensus();
    private ChessPosition position;
    private float confidenceScale=1;
    private BoardMotionTracker motion=new BoardMotionTracker();
    private boolean gridLocked;
    private volatile boolean reframe;
    private String problem="";
    private int[] cameraPixels;
    private boolean pendingChange;
    private boolean covered;
    private CameraBackground background;
    private final int[] boardPixels=new int[SIZE*SIZE];
    private final Side perspective;
    private final BoardDetector.Board bounds=new BoardDetector.Board(0,0,SIZE,1);
    public CameraBoard(Side perspective){this.perspective=perspective;}
    public static final class FrameResult {
        public final ChessPosition position;
        public final float[] corners;
        public final boolean tracked,readable,pendingChange;
        public final String problem;
        FrameResult(ChessPosition p,float[] c,boolean tracked,boolean readable,boolean pending){this(p,c,tracked,readable,pending,"");}
        FrameResult(ChessPosition p,float[] c,boolean tracked,boolean readable,boolean pending,String problem){position=p;corners=c;this.tracked=tracked;this.readable=readable;pendingChange=pending;this.problem=problem;}
    }
    /** Refit geometry without discarding the learned pieces and current legal position. */
    public void requestReframe(){reframe=true;}
    /** The same tracked corners rectify the sampled frame and place the displayed hints. */
    public FrameResult readFrame(Bitmap frame,float[] corners) {
        int w=frame.getWidth(),h=frame.getHeight();
        if(w<2||h<2)return new FrameResult(null,null,false,false,false);
        if(cameraPixels==null||cameraPixels.length!=w*h)cameraPixels=new int[w*h];
        frame.getPixels(cameraPixels,0,w,0,0,w,h);
        BoardMotionTracker.Frame gray=BoardMotionTracker.Frame.fromArgb(cameraPixels,w,h,480);
        float sx=gray.width/(float)w,sy=gray.height/(float)h;
        float[] scaled=corners.clone();for(int i=0;i<4;i++){scaled[i*2]*=sx;scaled[i*2+1]*=sy;}
        if(reframe){reframe=false;gridLocked=false;motion=new BoardMotionTracker();consensus.accept(null);}
        if(!gridLocked){
            float[] snapped=BoardGridLocator.locate(gray,scaled);
            if(snapped==null)snapped=BoardGridLocator.locate(gray,new float[]{2,2,gray.width-3,2,gray.width-3,gray.height-3,2,gray.height-3});
            if(snapped==null)return new FrameResult(null,null,false,true,false,"Не нахожу сетку 8×8. Покажи доску целиком; рамка должна охватывать клетки.");
            scaled=snapped;
        }
        BoardMotionTracker.Result tracked=motion.update(gray,scaled);
        if(tracked==null){consensus.accept(null);return new FrameResult(null,null,false,false,false,"Потерял сетку доски. Верни её в кадр или нажми «Углы заново» — партия сохранится.");}
        gridLocked=true;
        float[] real=tracked.corners.clone();for(int i=0;i<4;i++){real[i*2]/=sx;real[i*2+1]/=sy;}
        if(!tracked.readable()){consensus.accept(null);return new FrameResult(null,real,true,false,false,"Сетка найдена, но кадр смазан. Коснись изображения для фокуса.");}
        Bitmap square=rectify(frame,real);
        if(square==null){consensus.accept(null);return new FrameResult(null,null,false,false,false);}
        try {ChessPosition p=read(square);return new FrameResult(p,real,true,!covered,pendingChange,problem);}
        finally {square.recycle();}
    }
    public static Bitmap rectify(Bitmap source,float[] corners) {
        if(!CameraGeometry.valid(corners,source.getWidth(),source.getHeight()))return null;
        Matrix m=new Matrix();
        if(!m.setPolyToPoly(corners,0,new float[]{0,0,SIZE,0,SIZE,SIZE,0,SIZE},0,4))return null;
        Bitmap out=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);canvas.drawColor(Color.BLACK);
        canvas.drawBitmap(source,m,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        // Two camera/resampling passes can move a fine piece edge across a sampler pixel.
        // Consistent low-pass filtering reduces this aliasing and sensor noise, without
        // relaxing the legal matcher's confidence thresholds.
        Bitmap small=Bitmap.createScaledBitmap(out,SIZE/2,SIZE/2,true);
        Bitmap filtered=Bitmap.createScaledBitmap(small,SIZE,SIZE,true);
        out.recycle();small.recycle();return filtered;
    }
    public ChessPosition read(Bitmap rectified) {
        pendingChange=false;
        covered=false;
        problem="";
        if(!classifier.isTrained()) {
            if(!classifier.tryBootstrap(rectified,bounds)){problem="Сетку вижу. Для обучения покажи начальную расстановку; проверь фокус и блики.";return null;}
            // Reject a disagreement rather than attaching opposite labels to the templates.
            if(classifier.bootstrapSide()!=perspective){classifier.reset();problem="Сетку вижу. Проверь кнопку «Снизу»: цвет должен совпадать с нижними фигурами.";return null;}
            confidenceScale=CameraConfidence.scale(classifier.labelCosts(rectified,bounds));
            rectified.getPixels(boardPixels,0,SIZE,0,0,SIZE,SIZE);
            background=new CameraBackground(boardPixels,SIZE,SIZE);
            char[][] start={"rnbqkbnr".toCharArray(),"pppppppp".toCharArray(),"........".toCharArray(),"........".toCharArray(),"........".toCharArray(),"........".toCharArray(),"PPPPPPPP".toCharArray(),"RNBQKBNR".toCharArray()};
            position=new ChessPosition(start,Side.WHITE,"KQkq","-");
        }
        rectified.getPixels(boardPixels,0,SIZE,0,0,SIZE,SIZE);
        if(background!=null&&!background.visible(boardPixels,SIZE,SIZE)){covered=true;consensus.accept(null);problem="Доска частично закрыта или появился блик. Покажи все клетки.";return null;}
        float[][] screen=classifier.labelCosts(rectified,bounds),canonical=new float[64][];
        if(screen==null){consensus.accept(null);return null;}
        for(int i=0;i<64;i++) {
            canonical[i]=screen[perspective==Side.BLACK?63-i:i];
            for(int label=0;label<canonical[i].length;label++)canonical[i][label]*=confidenceScale;
        }
        VisualMoveTracker.Match match=VisualMoveTracker.match(position,canonical);
        if(match==null){consensus.accept(null);problem="Сетку вижу, но фигуры пока не совпали. Дождись конца анимации и проверь резкость.";return null;}
        pendingChange=match.plies>0;
        String key=fen(match.position);
        if(!consensus.accept(key)){problem="Доска найдена · подтверждаю положение фигур";return null;}
        position=match.position;
        classifier.commitConfirmed(perspective==Side.BLACK?FenBuilder.flip(position.board):position.board,rectified,bounds);
        return position;
    }
    public static String fen(ChessPosition p) {
        return FenBuilder.build(p.board,Side.WHITE,p.turn,p.rights,p.ep,0,1);
    }
}
