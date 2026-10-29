package com.opencheps.ui;
import android.app.Activity;import android.os.Bundle;import android.graphics.*;import android.widget.TextView;
import com.opencheps.camera.*;import com.opencheps.vision.*;import com.opencheps.util.Side;
import java.io.*;

/** Production rectifier + camera consensus + legal recognition; includes blur/exposure/noise. */
public class CameraVisionTestActivity extends Activity {
    private static final String[] START={"rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"};
    private static final int[][] THEMES={{0xffeeeed2,0xff769656},{0xfff0d9b5,0xffb58863},{0xffdee8ef,0xff7a9bb3},{0xffcdcdcd,0xffb4b4b4},{0xff3c3c3c,0xff646464}};
    @Override public void onCreate(Bundle b){super.onCreate(b);TextView result=new TextView(this);result.setText("Checking camera perspective…");setContentView(result);
        new Thread(()->{String message;try{message="CAMERA_VISION_OK "+check()+" SHAKE_OK "+checkShake()+" SNAP_OK "+checkCoarse()+" RECOVERY_OK "+checkRecovery();}catch(Throwable t){android.util.Log.e("CameraVisionTest","failure",t);message="CAMERA_VISION_FAIL "+t;}
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
                if(ply==0)save(square,"camera-calibration.png");
                if(first!=null||accepted==null||!java.util.Arrays.deepEquals(accepted.board,canonical)){
                    save(square,"camera-failed.png");save(shot,"camera-photo.png");
                    throw new AssertionError("theme="+theme+" flipped="+flipped+" angled="+angled+" ply="+ply+" accepted="+(accepted==null?null:accepted.move)+diagnose(tracker,square,flipped));
                }
                if(tracker.historySnapshot().plies!=ply||tracker.historySnapshot().fullmove!=(ply==2?2:1))throw new AssertionError("camera move history lost at ply "+ply);
                raw.recycle();shot.recycle();square.recycle();passed++;
            }
            Bitmap covered=Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888);covered.eraseColor(0xff333333);
            if(tracker.read(covered)!=null)throw new AssertionError("covered camera frame accepted");covered.recycle();
        }return passed;
    }
    private void save(Bitmap b,String name){try(FileOutputStream o=new FileOutputStream(new File(getFilesDir(),name))){b.compress(Bitmap.CompressFormat.PNG,100,o);}catch(IOException e){throw new RuntimeException(e);}}
    private int checkShake(){int passed=0;
        for(int theme=0;theme<THEMES.length;theme++)for(boolean flipped:new boolean[]{false,true}){
            float[] anchor={120,80,660,140,700,720,60,760};
            char[][] start=new char[8][];for(int r=0;r<8;r++)start[r]=START[r].toCharArray();
            char[][] e4=ChessPosition.copy(start);e4[6][4]='.';e4[4][4]='P';
            char[][] e5=ChessPosition.copy(e4);e5[1][4]='.';e5[3][4]='p';
            CameraBoard session=new CameraBoard(flipped?Side.BLACK:Side.WHITE);
            for(int ply=0;ply<3;ply++){
                char[][] expected=ply==0?start:ply==1?e4:e5;
                Bitmap raw=NativeVisionCheck.render(640,640,0,flipped?FenBuilder.flip(expected):expected,THEMES[theme]);
                for(int repeat=0;repeat<2;repeat++){
                    int step=ply*2+repeat;float[] actual=shaken(anchor,step);
                    Bitmap shot=photograph(raw,actual,step>0);
                    if((step&1)!=0){Bitmap small=Bitmap.createScaledBitmap(shot,640,672,true),soft=Bitmap.createScaledBitmap(small,800,840,true);shot.recycle();small.recycle();shot=soft;}
                    CameraBoard.FrameResult result=session.readFrame(shot,anchor);
                    if(!result.tracked||result.corners==null) {save(shot,"camera-photo.png");throw new AssertionError("shake geometry theme="+theme+" flipped="+flipped+" step="+step);}
                    for(int i=0;i<4;i++)if(Math.hypot(result.corners[i*2]-actual[i*2],result.corners[i*2+1]-actual[i*2+1])>3){save(shot,"camera-photo.png");throw new AssertionError("shake alignment theme="+theme+" flipped="+flipped+" step="+step+" corners="+java.util.Arrays.toString(result.corners));}
                    if(repeat==1&&(result.position==null||!java.util.Arrays.deepEquals(result.position.board,expected))){
                        Bitmap square=CameraBoard.rectify(shot,result.corners);save(square,"camera-failed.png");save(shot,"camera-photo.png");String diagnostic=diagnose(session,square,flipped);square.recycle();
                        throw new AssertionError("shake recognition theme="+theme+" flipped="+flipped+" ply="+ply+diagnostic);
                    }shot.recycle();
                }raw.recycle();passed++;
            }
            Bitmap raw=NativeVisionCheck.render(640,640,0,flipped?FenBuilder.flip(e5):e5,THEMES[theme]);
            float[] actual=shaken(anchor,5);Bitmap clear=photograph(raw,actual,true),covered=clear.copy(Bitmap.Config.ARGB_8888,true);Canvas canvas=new Canvas(covered);Paint hand=new Paint();hand.setColor(0xffa98367);canvas.drawRect(250,200,430,430,hand);
            if(session.readFrame(covered,anchor).position!=null)throw new AssertionError("hand occlusion accepted");
            CameraBoard.FrameResult recovered=session.readFrame(clear,anchor);if(recovered.position==null)recovered=session.readFrame(clear,anchor);
            if(recovered.position==null||!java.util.Arrays.deepEquals(recovered.position.board,e5))throw new AssertionError("did not recover after hand");
            raw.recycle();clear.recycle();covered.recycle();
        }return passed;
    }
    private int checkCoarse(){int passed=0;
        for(int theme=0;theme<THEMES.length;theme++)for(boolean flipped:new boolean[]{false,true}){
            float[] actual={120,80,660,140,700,720,60,760},rough={80,60,675,135,720,810,10,805};
            char[][] start=new char[8][];for(int r=0;r<8;r++)start[r]=START[r].toCharArray();
            char[][] e4=ChessPosition.copy(start);e4[6][4]='.';e4[4][4]='P';char[][] e5=ChessPosition.copy(e4);e5[1][4]='.';e5[3][4]='p';
            CameraBoard session=new CameraBoard(flipped?Side.BLACK:Side.WHITE);
            for(char[][] position:new char[][][]{start,e4,e5}){
                Bitmap raw=NativeVisionCheck.render(640,640,0,flipped?FenBuilder.flip(position):position,THEMES[theme]),shot=photograph(raw,actual,true);
                CameraBoard.FrameResult result=session.readFrame(shot,rough);result=session.readFrame(shot,rough);
                if(result.position==null||!java.util.Arrays.deepEquals(result.position.board,position)){save(shot,"camera-photo.png");throw new AssertionError("coarse calibration theme="+theme+" flipped="+flipped+" reason="+result.problem);}
                passed++;raw.recycle();shot.recycle();
            }
            // Geometry can be re-applied after moves without learning a fake initial position.
            session.requestReframe();Bitmap raw=NativeVisionCheck.render(640,640,0,flipped?FenBuilder.flip(e5):e5,THEMES[theme]),shot=photograph(raw,actual,true);
            CameraBoard.FrameResult result=session.readFrame(shot,rough);result=session.readFrame(shot,rough);
            if(result.position==null||!java.util.Arrays.deepEquals(result.position.board,e5))throw new AssertionError("reframe lost current game theme="+theme+" reason="+result.problem);
            raw.recycle();shot.recycle();
        }return passed;
    }
    private int checkRecovery(){int passed=0;
        for(int theme=0;theme<THEMES.length;theme++)for(boolean flipped:new boolean[]{false,true}){
            float[] anchor={120,80,660,140,700,720,60,760},shifted=anchor.clone();
            for(int i=0;i<4;i++){shifted[2*i]+=48;shifted[2*i+1]+=32;}
            char[][] start=new char[8][];for(int r=0;r<8;r++)start[r]=START[r].toCharArray();
            char[][] e4=ChessPosition.copy(start);e4[6][4]='.';e4[4][4]='P';
            char[][] e5=ChessPosition.copy(e4);e5[1][4]='.';e5[3][4]='p';
            char[][] nf3=ChessPosition.copy(e5);nf3[7][6]='.';nf3[5][5]='N';
            CameraBoard session=new CameraBoard(flipped?Side.BLACK:Side.WHITE);
            for(char[][] expected:new char[][][]{start,e4,e5}){
                Bitmap raw=NativeVisionCheck.render(640,640,0,flipped?FenBuilder.flip(expected):expected,THEMES[theme]),shot=photograph(raw,anchor,true);
                CameraBoard.FrameResult result=session.readFrame(shot,anchor);result=session.readFrame(shot,anchor);
                if(result.position==null)throw new AssertionError("recovery fixture calibration "+theme+" "+result.problem);
                raw.recycle();shot.recycle();
            }
            String saved=session.historySnapshot().command;
            Bitmap blackout=Bitmap.createBitmap(800,840,Bitmap.Config.ARGB_8888);blackout.eraseColor(0xff121820);
            for(int i=0;i<4;i++)if(session.readFrame(blackout,anchor).position!=null)throw new AssertionError("lost board accepted");
            blackout.recycle();
            for(char[][] expected:new char[][][]{e5,nf3}){
                Bitmap raw=NativeVisionCheck.render(640,640,0,flipped?FenBuilder.flip(expected):expected,THEMES[theme]),shot=photograph(raw,shifted,true);
                CameraBoard.FrameResult result=null;
                for(int i=0;i<10;i++){result=session.readFrame(shot,anchor);if(result.position!=null)break;}
                if(result.position==null||!java.util.Arrays.deepEquals(result.position.board,expected)){save(shot,"camera-photo.png");throw new AssertionError("automatic recovery theme="+theme+" flipped="+flipped+" reason="+result.problem);}
                for(int i=0;i<4;i++)if(Math.hypot(result.corners[2*i]-shifted[2*i],result.corners[2*i+1]-shifted[2*i+1])>4)throw new AssertionError("recovered grid misaligned");
                if(expected==e5&&!saved.equals(result.history.command))throw new AssertionError("recovery erased game history");
                if(expected==nf3&&(result.history.plies!=3||result.history.halfmove!=1||!result.history.command.endsWith(" moves e2e4 e7e5 g1f3")))throw new AssertionError("post-recovery move missing");
                raw.recycle();shot.recycle();
            }passed++;
        }return passed;
    }
    private float[] shaken(float[] corners,int step){
        if(step==0)return corners.clone();
        double angle=Math.toRadians(Math.sin(step*1.2)*.65),scale=1+Math.cos(step*.8)*.004,dx=Math.sin(step)*7,dy=Math.cos(step*.9)*5;
        float[] p=new float[8];for(int i=0;i<4;i++){double x=corners[i*2]-400,y=corners[i*2+1]-420;p[i*2]=(float)(400+scale*(Math.cos(angle)*x-Math.sin(angle)*y)+dx);p[i*2+1]=(float)(420+scale*(Math.sin(angle)*x+Math.cos(angle)*y)+dy);}return p;
    }
    private String diagnose(CameraBoard tracker,Bitmap square,boolean flipped){try{
        java.lang.reflect.Field cf=CameraBoard.class.getDeclaredField("classifier"),pf=CameraBoard.class.getDeclaredField("position");cf.setAccessible(true);pf.setAccessible(true);
        PieceClassifier classifier=(PieceClassifier)cf.get(tracker);ChessPosition old=(ChessPosition)pf.get(tracker);
        float[][] seen=classifier.labelCosts(square,new BoardDetector.Board(0,0,640,1)),costs=new float[64][];for(int i=0;i<64;i++)costs[i]=seen[flipped?63-i:i];
        java.util.List<ChessPosition> all=new java.util.ArrayList<>();all.add(old);for(ChessPosition p:old.successors()){all.add(p);all.addAll(p.successors());}
        java.util.TreeMap<Float,String> rank=new java.util.TreeMap<>();for(ChessPosition p:all){float total=0,max=0;int cell=0;for(int i=0;i<64;i++){float c=costs[i][VisualMoveTracker.PIECES.indexOf(p.board[i/8][i%8])];total+=c;if(c>max){max=c;cell=i;}}rank.put(total,p.move+" mean="+total/64+" max="+max+" cell="+ChessPosition.square(cell/8,cell%8));}
        StringBuilder out=new StringBuilder();for(int i=0;i<4&&!rank.isEmpty();i++)out.append(" | ").append(rank.pollFirstEntry());return out.toString();
    }catch(Exception e){return "diagnostic error "+e;}}
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
