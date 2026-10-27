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
    private final Side perspective;
    private final BoardDetector.Board bounds=new BoardDetector.Board(0,0,SIZE,1);
    public CameraBoard(Side perspective){this.perspective=perspective;}
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
        if(!classifier.isTrained()) {
            if(!classifier.tryBootstrap(rectified,bounds))return null;
            // Reject a disagreement rather than attaching opposite labels to the templates.
            if(classifier.bootstrapSide()!=perspective){classifier.reset();return null;}
            confidenceScale=CameraConfidence.scale(classifier.labelCosts(rectified,bounds));
            char[][] start={"rnbqkbnr".toCharArray(),"pppppppp".toCharArray(),"........".toCharArray(),"........".toCharArray(),"........".toCharArray(),"........".toCharArray(),"PPPPPPPP".toCharArray(),"RNBQKBNR".toCharArray()};
            position=new ChessPosition(start,Side.WHITE,"KQkq","-");
        }
        float[][] screen=classifier.labelCosts(rectified,bounds),canonical=new float[64][];
        if(screen==null){consensus.accept(null);return null;}
        for(int i=0;i<64;i++) {
            canonical[i]=screen[perspective==Side.BLACK?63-i:i];
            for(int label=0;label<canonical[i].length;label++)canonical[i][label]*=confidenceScale;
        }
        VisualMoveTracker.Match match=VisualMoveTracker.match(position,canonical);
        if(match==null){consensus.accept(null);return null;}
        String key=fen(match.position);
        if(!consensus.accept(key))return null;
        position=match.position;
        classifier.commitConfirmed(perspective==Side.BLACK?FenBuilder.flip(position.board):position.board,rectified,bounds);
        return position;
    }
    public static String fen(ChessPosition p) {
        return FenBuilder.build(p.board,Side.WHITE,p.turn,p.rights,p.ep,0,1);
    }
}
