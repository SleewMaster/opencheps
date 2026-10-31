package com.opencheps.ui;
import android.app.Activity;import android.os.Bundle;import android.graphics.*;import android.view.*;import android.widget.*;
import com.opencheps.camera.*;import com.opencheps.engine.StockfishEngine;import com.opencheps.vision.*;import com.opencheps.util.Side;import java.util.*;
/** CI-only preview of the unobstructed production camera arrow, with a real legal position and move. */
public class CameraHintPreviewActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);
        boolean black="b".equals(getIntent().getStringExtra("side"));Side side=black?Side.BLACK:Side.WHITE;
        char[][] position=new char[8][];String[] rows={"rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"};for(int i=0;i<8;i++)position[i]=rows[i].toCharArray();
        ChessPosition chess=new ChessPosition(position,side,"KQkq","-");String move=black?"g8f6":"e2e4";
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xff0b121e);setContentView(root);
        TextView label=new TextView(this);label.setText(MoveAnnouncement.label(CameraBoard.fen(chess),move));label.setTextSize(22);label.setTextColor(0xffeaf2ff);root.addView(label);
        FrameLayout stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        Bitmap board=NativeVisionCheck.render(640,640,0,black?FenBuilder.flip(position):position,new int[]{0xffeeeed2,0xff769656});
        final float[] corners=new float[8];stage.addView(new View(this){protected void onDraw(Canvas c){c.drawColor(0xff17212e);Matrix m=new Matrix();m.setPolyToPoly(new float[]{0,0,640,0,640,640,0,640},0,corners,0,4);c.drawBitmap(board,m,new Paint(3));}});
        CameraBoardView camera=new CameraBoardView(this);stage.addView(camera);
        stage.post(()->{float w=stage.getWidth(),h=stage.getHeight();float[] q={45,40,w-35,65,w-55,h-65,30,h-45};System.arraycopy(q,0,corners,0,8);stage.getChildAt(0).invalidate();camera.setEditing(false);camera.setTrackedCorners(q);camera.setMoves(Collections.singletonList(new StockfishEngine.Pv(move,38,false)),side);
            int[] first=capture(camera);String second=black?"d7d5":"d2d4";camera.setMoves(Arrays.asList(new StockfishEngine.Pv(move,38,false),new StockfishEngine.Pv(second,20,false)),side);if(!Arrays.equals(first,capture(camera)))throw new AssertionError("camera displays multiple move instructions");
            camera.trackingLost();Bitmap hidden=Bitmap.createBitmap(camera.getWidth(),camera.getHeight(),Bitmap.Config.ARGB_8888);camera.draw(new Canvas(hidden));int[] pixels=new int[hidden.getWidth()*hidden.getHeight()];hidden.getPixels(pixels,0,hidden.getWidth(),0,0,hidden.getWidth(),hidden.getHeight());for(int pixel:pixels)if(pixel!=0)throw new AssertionError("lost camera retains a visible frame or arrow");hidden.recycle();
            try(java.io.FileWriter out=new java.io.FileWriter(new java.io.File(getFilesDir(),"camera-hint-state-"+(black?"b":"w")+".txt"))){out.write("CAMERA_LOST_FRAME_OK CAMERA_SINGLE_ARROW_OK");}catch(java.io.IOException e){throw new RuntimeException(e);}
            camera.setTrackedCorners(q);camera.setMoves(Collections.singletonList(new StockfishEngine.Pv(move,38,false)),side);});
    }
    private int[] capture(View view){Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());bitmap.recycle();return pixels;}
}
