package com.opencheps.ui;
import android.app.Activity;import android.os.Bundle;import android.graphics.*;import android.widget.TextView;
import com.opencheps.camera.*;import com.opencheps.vision.*;import com.opencheps.util.Side;
import java.io.*;

/** Production rectifier + camera consensus + legal recognition; includes blur/exposure/noise. */
public class CameraVisionTestActivity extends Activity {
    private static final String[] START={"rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"};
    private static final int[][] THEMES={{0xffeeeed2,0xff769656},{0xfff0d9b5,0xffb58863},{0xffdee8ef,0xff7a9bb3},{0xffcdcdcd,0xffb4b4b4},{0xff3c3c3c,0xff646464}};
    @Override public void onCreate(Bundle b){super.onCreate(b);TextView result=new TextView(this);result.setText("Checking camera perspective…");setContentView(result);
        new Thread(()->{String message;try{message="CAMERA_VISION_OK "+check();}catch(Throwable t){android.util.Log.e("CameraVisionTest","failure",t);message="CAMERA_VISION_FAIL "+t;}
            final String answer=message;try(FileWriter w=new FileWriter(new File(getFilesDir(),"camera-vision-result.txt"))){w.write(answer);}catch(IOException e){throw new RuntimeException(e);}runOnUiThread(()->result.setText(answer));}).start();
    }
    private int check(){int passed=0;
        for(int theme=0;theme<THEMES.length;theme++)for(boolean flipped:new boolean[]{false,true})for(boolean angled:new boolean[]{false,true}){
            float[] corners=angled?new float[]{120,80,660,140,700,720,60,760}:new float[]{80,80,720,80,720,720,80,720};
            char[][] start=new char[8][];for(int r=0;r<8;r++)start[r]=START[r].toCharArray();
            char[][] e4=ChessPosition.copy(start);e4[6][4]='.';e4[4][4]='P';
            char[][] e5=ChessPosition.copy(e4);e5[1][4]='.';e5[3][4]='p';
            CameraBoard tracker=new CameraBoard(flipped?Side.BLACK:Side.WHITE);
            for(int ply=0;ply<3;ply++){
                char[][] canonical=ply==0?start:ply==1?e4:e5;
                Bitmap raw=NativeVisionCheck.render(640,640,0,flipped?FenBuilder.flip(canonical):canonical,THEMES[theme]);
                Bitmap shot=photograph(raw,corners,ply>0);Bitmap square=CameraBoard.rectify(shot,corners);
                if(square==null)throw new AssertionError("rectification");
                ChessPosition first=tracker.read(square),accepted=tracker.read(square);
                if(first!=null||accepted==null||!java.util.Arrays.deepEquals(accepted.board,canonical))throw new AssertionError("theme="+theme+" flipped="+flipped+" angled="+angled+" ply="+ply+" accepted="+(accepted==null?null:accepted.move));
                raw.recycle();shot.recycle();square.recycle();passed++;
            }
            Bitmap covered=Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888);covered.eraseColor(0xff333333);
            if(tracker.read(covered)!=null)throw new AssertionError("covered camera frame accepted");covered.recycle();
        }return passed;
    }
    private Bitmap photograph(Bitmap source,float[] corners,boolean changedLighting){
        Bitmap shot=Bitmap.createBitmap(800,840,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(shot);c.drawColor(0xff121820);
        Matrix m=new Matrix();m.setPolyToPoly(new float[]{0,0,640,0,640,640,0,640},0,corners,0,4);
        Paint p=new Paint(3);p.setFilterBitmap(true);c.drawBitmap(source,m,p);
        // A second frame is not pixel-identical to the calibration frame.
        if(changedLighting){int[] px=new int[800*840];shot.getPixels(px,0,800,0,0,800,840);java.util.Random random=new java.util.Random(19);
            for(int i=0;i<px.length;i++){int v=px[i],noise=random.nextInt(5)-2;int r=Math.min(255,Math.max(0,((v>>16)&255)+noise+3)),g=Math.min(255,Math.max(0,((v>>8)&255)+noise+3)),b=Math.min(255,Math.max(0,(v&255)+noise+3));px[i]=0xff000000|(r<<16)|(g<<8)|b;}shot.setPixels(px,0,800,0,0,800,840);
        }return shot;
    }
}
