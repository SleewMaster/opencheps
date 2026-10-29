package com.opencheps.ui;
import android.app.Activity;import android.os.Bundle;import android.graphics.Bitmap;import android.widget.TextView;
import com.opencheps.overlay.PositionSetupView;import com.opencheps.vision.*;import com.opencheps.util.Side;
import java.io.*;import java.util.*;

/** Production late-game diagram reader, serialized themes, legal continuation and review UI. */
public class ScreenJoinTestActivity extends Activity {
    private static final String LATE="8/n5r1/4Q1pk/P7/7P/8/6P1/6K1 w - - 0 40";
    @Override public void onCreate(Bundle b){super.onCreate(b);TextView status=new TextView(this);status.setText("Checking late game…");setContentView(status);
        new Thread(()->{try{int n=check();save("screen-join-result.txt","SCREEN_JOIN_OK "+n);runOnUiThread(()->review());}catch(Throwable t){android.util.Log.e("ScreenJoinTest","failed",t);save("screen-join-result.txt","SCREEN_JOIN_FAIL "+t);}}).start();}
    private int check()throws Exception{
        try(InputStream in=getAssets().open("vision/neo-v1.txt")){if(!PieceTemplates.read(in).complete())throw new AssertionError("built-in descriptors");}
        PositionSeed initial=PositionSeed.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",Side.WHITE);
        int[][] themes={{0xffeeeed2,0xff769656},{0xfff0d9b5,0xffb58863},{0xffdee8ef,0xff7a9bb3}};
        Bitmap start=NativeVisionCheck.render(640,640,0,initial.position.board,themes[0]);PieceClassifier training=new PieceClassifier();
        if(!training.tryBootstrap(start,new BoardDetector.Board(0,0,640,1)))throw new AssertionError("theme training");
        ByteArrayOutputStream serialized=new ByteArrayOutputStream();training.exportTemplates().write(serialized);start.recycle();
        PieceTemplates bank=PieceTemplates.read(new ByteArrayInputStream(serialized.toByteArray()));int count=0;
        for(String fen:new String[]{LATE,"4k3/pp3ppp/2n1b3/3pP3/8/2N2N2/PP3PPP/4K2R w - - 0 20","4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 20"})for(Side side:new Side[]{Side.WHITE,Side.BLACK})for(int[] theme:themes){
            PositionSeed seed=PositionSeed.parse(fen,side);if(seed==null)throw new AssertionError("seed "+fen);
            char[][] screen=side==Side.WHITE?seed.position.board:FenBuilder.flip(seed.position.board);
            Bitmap image=NativeVisionCheck.render(640,640,0,screen,theme);BoardDetector.Board bounds=new BoardDetector.Board(0,0,640,1);
            PieceClassifier reader=new PieceClassifier();reader.useTemplates(bank,side);
            DiagramReader.Result result=DiagramReader.read(reader.labelCosts(image,bounds));
            if(result==null||!Arrays.deepEquals(result.screen,screen))throw new AssertionError("late diagram side="+side+" theme="+Arrays.toString(theme)+" fen="+fen+" differences="+differences(screen,result,reader.labelCosts(image,bounds)));
            // Same production adaptation used after the user reviews the diagram.
            reader.useTemplates(PieceClassifier.withObserved(bank,screen,reader.snapshotFeatures(image,bounds)),side);image.recycle();
            ChessPosition next=seed.position.successors().get(0);Bitmap moved=NativeVisionCheck.render(640,640,0,side==Side.WHITE?next.board:FenBuilder.flip(next.board),theme);
            float[][] raw=reader.labelCosts(moved,bounds),costs=new float[64][];for(int i=0;i<64;i++)costs[i]=raw[side==Side.WHITE?i:63-i];
            VisualMoveTracker.Match match=VisualMoveTracker.match(seed.position,costs);moved.recycle();
            if(match==null||match.plies!=1||!Arrays.deepEquals(match.position.board,next.board))throw new AssertionError("late continuation "+next.move);
            count++;
        }return count;
    }
    private void review(){boolean flipped="b".equals(getIntent().getStringExtra("side"));Side side=flipped?Side.BLACK:Side.WHITE;PositionSeed seed=PositionSeed.parse(LATE,side);
        boolean[] uncertain=new boolean[64];uncertain[flipped?43:20]=true;
        setContentView(new PositionSetupView(this,flipped?FenBuilder.flip(seed.position.board):seed.position.board,uncertain,side,Side.WHITE,
            accepted->{if(accepted.error()!=null||accepted.position.turn!=Side.WHITE||accepted.perspective!=side||!Arrays.deepEquals(accepted.position.board,seed.position.board))throw new AssertionError("review changed position");TextView result=new TextView(this);result.setText("SCREEN_JOIN_UI_OK "+side);setContentView(result);save("screen-join-ui-result.txt","SCREEN_JOIN_UI_OK "+side);},()->{throw new AssertionError("review canceled");}));
    }
    private String differences(char[][] expected,DiagramReader.Result result,float[][] costs){
        if(result==null)return "null";StringBuilder s=new StringBuilder();
        for(int i=0;i<64;i++)if(expected[i/8][i%8]!=result.screen[i/8][i%8])s.append(ChessPosition.square(i/8,i%8)).append(':').append(expected[i/8][i%8]).append('/').append(result.screen[i/8][i%8]).append(' ').append(Arrays.toString(costs[i])).append("; ");return s.toString();
    }
    private void save(String name,String text){try(FileWriter w=new FileWriter(new File(getFilesDir(),name))){w.write(text);}catch(IOException e){throw new RuntimeException(e);}}
}
