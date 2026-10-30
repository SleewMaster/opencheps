package com.opencheps;
import com.opencheps.camera.CameraOpening;import com.opencheps.engine.PositionHistory;import com.opencheps.util.Side;import com.opencheps.vision.*;import org.junit.Test;import static org.junit.Assert.*;
public class CameraOpeningTest {
    private float[][] costs(ChessPosition position){float[][] c=new float[64][13];for(int i=0;i<64;i++){java.util.Arrays.fill(c[i],.6f);c[i][VisualMoveTracker.PIECES.indexOf(position.board[i/8][i%8])]=.005f;}return c;}
    @Test public void everyObservedFirstWhiteMoveGivesBlackTheTurn(){for(ChessPosition next:CameraOpening.initial().successors()){VisualMoveTracker.Match m=VisualMoveTracker.match(CameraOpening.initial(),costs(next));assertNotNull(m);assertEquals(next.move,m.position.move);assertEquals(1,m.plies);assertEquals(Side.BLACK,m.position.turn);}}
    @Test public void confirmedFirstMoveAndBlackReplyAreRecordedInOrder(){for(ChessPosition next:CameraOpening.initial().successors()){PositionHistory h=new PositionHistory();h.reset(FenBuilder.build(CameraOpening.initial().board,Side.WHITE,Side.WHITE,"KQkq","-",0,1));h.advance(next);assertEquals(1,h.snapshot().plies);ChessPosition reply=next.successors().get(0);h.advance(reply);assertEquals(2,h.snapshot().plies);assertEquals(2,h.snapshot().fullmove);assertTrue(h.snapshot().command.endsWith(" moves "+next.move+" "+reply.move));}}
}
