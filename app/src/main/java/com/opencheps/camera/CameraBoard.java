package com.opencheps.camera;

import android.graphics.*;
import com.opencheps.util.Side;
import com.opencheps.engine.PositionHistory;
import com.opencheps.vision.*;

/** Each instance belongs to one calibration and is used only on the vision executor. */
public final class CameraBoard {
    public static final int SIZE=640;
    private final PieceClassifier classifier=new PieceClassifier();
    private final FrameConsensus consensus=new FrameConsensus();
    private ChessPosition position;
    private final PositionHistory history=new PositionHistory();
    private int unreadPositions;
    private float confidenceScale=1;
    private final CameraGridTracker geometry=new CameraGridTracker();
    private volatile boolean reframe;
    private String problem="";
    private int[] cameraPixels;
    private boolean pendingChange;
    private boolean covered;
    private CameraBackground background;
    private final int[] boardPixels=new int[SIZE*SIZE];
    private Side perspective;
    private final java.util.List<PieceTemplates> banks;
    private final FrameConsensus reviewConsensus=new FrameConsensus();
    private volatile boolean reviewRequested;
    private boolean autoReviewOff;
    private Review pendingReview;
    private final BoardDetector.Board bounds=new BoardDetector.Board(0,0,SIZE,1);
    public CameraBoard(Side perspective){this(perspective,java.util.Collections.emptyList());}
    public CameraBoard(Side perspective,java.util.List<PieceTemplates> banks){this.perspective=perspective;this.banks=new java.util.ArrayList<>(banks);classifier.setCameraMode(true);}
    public static java.util.List<PieceTemplates> loadBanks(android.content.Context context){
        java.util.List<PieceTemplates> result=new java.util.ArrayList<>();
        for(String file:new String[]{"camera-templates.txt","screen-templates.txt"})try(java.io.InputStream in=new java.io.FileInputStream(new java.io.File(context.getFilesDir(),file))){result.add(PieceTemplates.read(in));}catch(Exception ignored){}
        try(java.io.InputStream in=context.getAssets().open("vision/neo-v1.txt")){result.add(PieceTemplates.read(in));}catch(Exception ignored){}
        return result;
    }
    public static final class Review {
        public final DiagramReader.Result diagram;
        public final Side perspective;
        private final PieceTemplates bank;
        private final float[][] features;
        private final int[] pixels;
        Review(DiagramReader.Result d,Side side,PieceTemplates bank,float[][] features,int[] pixels){diagram=d;perspective=side;this.bank=bank;this.features=features;this.pixels=pixels;}
    }
    public void requestReview(){reviewRequested=true;autoReviewOff=false;}
    public void cancelReview(){reviewRequested=false;autoReviewOff=true;pendingReview=null;reviewConsensus.accept(null);}
    /** Called on the vision executor after explicit review; a fresh frame must still verify it. */
    public void applyReview(Review review,PositionSeed seed){
        if(seed==null||seed.error()!=null)throw new IllegalArgumentException("Invalid reviewed position");
        perspective=seed.perspective;
        char[][] screen=perspective==Side.WHITE?seed.position.board:FenBuilder.flip(seed.position.board);
        classifier.useTemplates(PieceClassifier.withObserved(review.bank,screen,review.features),perspective);
        position=seed.position;history.reset(seed.fen());confidenceScale=1;unreadPositions=0;
        background=new CameraBackground(review.pixels,SIZE,SIZE);
        consensus.accept(null);consensus.accept(null);cancelReview();
    }
    public PieceTemplates templates(){return classifier.exportTemplates();}
    private Review readReview(Bitmap square){
        java.util.List<PieceTemplates> candidates=new java.util.ArrayList<>(banks);
        if(classifier.isTrained())candidates.add(0,classifier.exportTemplates());
        PieceClassifier selected=null;DiagramReader.Result best=null;float score=Float.POSITIVE_INFINITY;
        for(PieceTemplates bank:candidates){
            PieceClassifier reader=new PieceClassifier();reader.setCameraMode(true);reader.useTemplates(bank,perspective);
            DiagramReader.Result d=DiagramReader.read(reader.labelCosts(square,bounds));if(d==null)continue;
            float rank=d.error;for(boolean u:d.uncertain)if(u)rank+=.005f;
            if(rank<score){score=rank;best=d;selected=reader;}
        }
        if(best==null){problem="Нет шаблонов фигур. Покажи начальную позицию один раз.";return null;}
        // Independent point samples expose disagreements caused by pooling thin contours.
        // Keep the pooled proposal, but require the user to review each disagreement.
        PieceClassifier pointReader=new PieceClassifier();pointReader.useTemplates(selected.exportTemplates(),perspective);
        DiagramReader.Result point=DiagramReader.read(pointReader.labelCosts(square,bounds));
        for(int i=0;i<64;i++)if(point==null||point.screen[i/8][i%8]!=best.screen[i/8][i%8])best.uncertain[i]=true;
        String key=java.util.Arrays.deepToString(best.screen);
        if(!reviewConsensus.accept(key)){problem="Читаю текущую позицию · удерживай доску в кадре";return null;}
        int[] pixels=new int[SIZE*SIZE];square.getPixels(pixels,0,SIZE,0,0,SIZE,SIZE);
        return new Review(best,perspective,selected.exportTemplates(),selected.snapshotFeatures(square,bounds),pixels);
    }
    public static final class FrameResult {
        public final ChessPosition position;
        public final float[] corners;
        public final boolean tracked,readable,pendingChange;
        public final String problem;
        public final PositionHistory.Snapshot history;
        public final Review review;
        FrameResult(ChessPosition p,float[] c,boolean tracked,boolean readable,boolean pending){this(p,c,tracked,readable,pending,"");}
        FrameResult(ChessPosition p,float[] c,boolean tracked,boolean readable,boolean pending,String problem){this(p,c,tracked,readable,pending,problem,null);}
        FrameResult(ChessPosition p,float[] c,boolean tracked,boolean readable,boolean pending,String problem,PositionHistory.Snapshot history){this(p,c,tracked,readable,pending,problem,history,null);}
        FrameResult(ChessPosition p,float[] c,boolean tracked,boolean readable,boolean pending,String problem,PositionHistory.Snapshot history,Review review){this.review=review;position=p;corners=c;this.tracked=tracked;this.readable=readable;pendingChange=pending;this.problem=problem;this.history=history;}
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
        if(reframe){reframe=false;geometry.reset();consensus.accept(null);}
        CameraGridTracker.Result tracked=geometry.update(gray,scaled);
        if(tracked==null){reviewConsensus.accept(null);consensus.accept(null);return new FrameResult(null,null,false,false,false,"Не вижу всю сетку · держи все 64 клетки внутри изображения камеры.");}
        float[] real=tracked.corners.clone();for(int i=0;i<4;i++){real[i*2]/=sx;real[i*2+1]/=sy;}
        if(!tracked.readable){reviewConsensus.accept(null);consensus.accept(null);return new FrameResult(null,real,true,false,false,"Сетка найдена, но кадр смазан. Коснись изображения для фокуса.");}
        Bitmap square=rectify(frame,real);
        if(square==null){reviewConsensus.accept(null);consensus.accept(null);return new FrameResult(null,null,false,false,false);}
        try {
            ChessPosition p=read(square);
            return new FrameResult(p,real,true,!covered,pendingChange,problem,p==null?null:history.snapshot(),pendingReview);
        }
        finally {square.recycle();}
    }
    public PositionHistory.Snapshot historySnapshot(){return history.snapshot();}
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
        pendingReview=null;
        pendingChange=false;
        covered=false;
        problem="";
        if(reviewRequested){pendingReview=readReview(rectified);return null;}
        if(!classifier.isTrained()) {
            if(!classifier.tryBootstrap(rectified,bounds)){if(!autoReviewOff)pendingReview=readReview(rectified);else problem="Нажми «Позиция», чтобы начать с текущей расстановки.";return null;}
            // Reject a disagreement rather than attaching opposite labels to the templates.
            if(classifier.bootstrapSide()!=perspective){classifier.reset();problem="Сетку вижу. Проверь кнопку «Снизу»: цвет должен совпадать с нижними фигурами.";return null;}
            confidenceScale=CameraConfidence.scale(classifier.labelCosts(rectified,bounds));
            rectified.getPixels(boardPixels,0,SIZE,0,0,SIZE,SIZE);
            background=new CameraBackground(boardPixels,SIZE,SIZE);
            position=CameraOpening.initial();
            history.reset(fen(position));
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
        if(match==null){consensus.accept(null);if(++unreadPositions%3==0)geometry.reset();problem=unreadPositions>=6?"Позиция не совпала с историей · нажми «Позиция», чтобы продолжить с текущей доски.":"Сетку вижу · проверяю фигуры после хода";return null;}
        unreadPositions=0;
        pendingChange=match.plies>0;
        String key=fen(match.position);
        if(!consensus.accept(key)){problem="Доска найдена · подтверждаю положение фигур";return null;}
        if(match.plies==2)history.advance(match.intermediate,match.position);
        else if(match.plies==1)history.advance(match.position);
        position=match.position;
        classifier.commitConfirmed(perspective==Side.BLACK?FenBuilder.flip(position.board):position.board,rectified,bounds);
        return position;
    }
    public static String fen(ChessPosition p) {
        return FenBuilder.build(p.board,Side.WHITE,p.turn,p.rights,p.ep,0,1);
    }
}
