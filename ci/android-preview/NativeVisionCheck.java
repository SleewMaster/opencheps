package com.opencheps.ui;
import android.app.Activity;import android.graphics.*;import android.view.View;import android.widget.TextView;
import com.opencheps.overlay.ArrowOverlayView;import com.opencheps.vision.*;import com.opencheps.util.Side;
import java.util.*;import java.io.*;
/** Runs the production bitmap sampler, detector and matcher against production arrow paint. */
public final class NativeVisionCheck {
 private static final String[] START={"rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"};
 private static final int[][] THEMES={{0xffeeeed2,0xff769656},{0xfff0d9b5,0xffb58863},{0xffdee8ef,0xff7a9bb3},{0xffcdcdcd,0xffb4b4b4},{0xff3c3c3c,0xff646464}};
 static void start(Activity activity,ArrowOverlayView arrows,View root,TextView result){run(activity,arrows,root,result,0,0);}
 private static void run(Activity activity,ArrowOverlayView arrows,View root,TextView result,int index,int passed){
  if(index==THEMES.length*2){finish(activity,result,"VISION_OK "+passed);return;}
  try{
   int w=root.getWidth(),h=root.getHeight();float cell=w/8f,top=(h-w)*.48f;int[] xy=new int[2];root.getLocationOnScreen(xy);
   boolean flipped=(index&1)!=0;int[] colors=THEMES[index/2];
   char[][] initial=new char[8][];for(int r=0;r<8;r++)initial[r]=START[r].toCharArray();
   char[][] after=ChessPosition.copy(initial);after[6][4]='.';after[4][4]='P';
   char[][] screenInitial=flipped?FenBuilder.flip(initial):initial,screenAfter=flipped?FenBuilder.flip(after):after;
   Bitmap plain=render(w,h,top,screenInitial,colors);
   BoardDetector.Board board=BoardDetector.locate(plain);
   if(board==null||Math.abs(board.left)>4||Math.abs(board.top-top)>5||Math.abs(board.side-w)>5)throw new AssertionError("detector theme "+index);
   PieceClassifier classifier=new PieceClassifier();if(!classifier.tryBootstrap(plain,board))throw new AssertionError("bootstrap theme "+index);
   if(classifier.bootstrapSide()!=(flipped?Side.BLACK:Side.WHITE))throw new AssertionError("orientation "+index);
   float fx=flipped?3.5f:4.5f,fr=flipped?1.5f:6.5f,tr=flipped?3.5f:4.5f;
   ArrowOverlayView.Arrow a=new ArrowOverlayView.Arrow(xy[0]+fx*cell,xy[1]+top+fr*cell,xy[0]+fx*cell,xy[1]+top+tr*cell,0xffb9f66e,8,"e2→e4");
   arrows.setBoardBounds(new Rect(xy[0],xy[1]+Math.round(top),xy[0]+w,xy[1]+Math.round(top)+w));arrows.setArrows(Collections.singletonList(a));
   arrows.post(()->{
    try{
     classifier.setAnnotationMask(new ArrowOcclusion(new ArrowOcclusion.Mark(fx*cell,top+fr*cell,fx*cell,top+tr*cell,cell,true,true)));
     for(boolean moved:new boolean[]{false,true}){
      Bitmap bmp=render(w,h,top,moved?screenAfter:screenInitial,colors);Canvas c=new Canvas(bmp);int save=c.saveLayerAlpha(0,0,w,h,191);arrows.draw(c);c.restoreToCount(save);
      float[][] seen=classifier.labelCosts(bmp,board),costs=new float[64][];for(int i=0;i<64;i++)costs[i]=seen[flipped?63-i:i];
      VisualMoveTracker.Match match=VisualMoveTracker.match(new ChessPosition(initial,Side.WHITE,"KQkq","-"),costs);
      if(match==null||match.plies!=(moved?1:0)||(moved&&!match.position.move.equals("e2e4"))){
       try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"vision-shot.png"))){bmp.compress(Bitmap.CompressFormat.PNG,100,out);}
       try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"vision-plain.png"))){plain.compress(Bitmap.CompressFormat.PNG,100,out);}
       throw new AssertionError("visible arrow theme="+index+" moved="+moved+" match="+(match==null?"null":match.position.move));
      }
      bmp.recycle();
     }
     plain.recycle();run(activity,arrows,root,result,index+1,passed+2);
    }catch(Throwable t){fail(activity,result,t);}
   });
  }catch(Throwable t){fail(activity,result,t);}
 }
 private static Bitmap render(int w,int h,float top,char[][] grid,int[] colors){
  Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bmp);c.drawColor(0xff0b121e);Paint p=new Paint(3);float cell=w/8f;
  for(int r=0;r<8;r++)for(int f=0;f<8;f++){p.setColor(colors[(r+f)&1]);c.drawRect(f*cell,top+r*cell,(f+1)*cell,top+(r+1)*cell,p);}
  String keys="KQRBNPkqrbnp",glyphs="♔♕♖♗♘♙♚♛♜♝♞♟";p.setTypeface(Typeface.DEFAULT);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(cell*.74f);
  for(int r=0;r<8;r++)for(int f=0;f<8;f++){int i=keys.indexOf(grid[r][f]);if(i<0)continue;p.setColor(i<6?0xfff8fafc:0xff151c24);c.drawText(glyphs.substring(i,i+1),(f+.5f)*cell,top+(r+.78f)*cell,p);}
  return bmp;
 }
 private static void fail(Activity a,TextView result,Throwable t){android.util.Log.e("NativeVisionCheck","failed",t);finish(a,result,"VISION_FAIL "+t.getMessage());}
 private static void finish(Activity a,TextView result,String message){result.setText(message);try(FileWriter w=new FileWriter(new File(a.getFilesDir(),"vision-result.txt"))){w.write(message);}catch(IOException e){throw new RuntimeException(e);}}
}
