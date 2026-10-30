package com.opencheps;
import com.opencheps.camera.CameraOpening;import com.opencheps.vision.*;import org.junit.Test;import static org.junit.Assert.*;
public class CameraOpeningTest {
    private float[][] costs(ChessPosition position){float[][] c=new float[64][13];for(int i=0;i<64;i++){java.util.Arrays.fill(c[i],.6f);c[i][VisualMoveTracker.PIECES.indexOf(position.board[i/8][i%8])]=.005f;}return c;}
    @Test public void recognisesEveryLegalFirstWhiteMove(){for(ChessPosition next:CameraOpening.initial().successors()){VisualMoveTracker.Match m=CameraOpening.match(costs(next));assertNotNull(m);assertEquals(next.move,m.position.move);assertEquals(1,m.plies);}}
    @Test public void unchangedStartUsesExistingBootstrap(){assertNull(CameraOpening.match(costs(CameraOpening.initial())));}
    @Test public void secondBlackMoveMustNotBeGuessedAsFirstWhiteMove(){ChessPosition next=CameraOpening.initial().successors().get(0).successors().get(0);assertNull(CameraOpening.match(costs(next)));}
    @Test public void oneCoveredPieceCannotStartGame(){ChessPosition next=CameraOpening.initial().successors().get(0);float[][] c=costs(next);c[0][VisualMoveTracker.PIECES.indexOf('r')]=.22f;assertNull(CameraOpening.match(c));}
    @Test public void uncertainPhotoNeedsPositionReview(){ChessPosition next=CameraOpening.initial().successors().get(0);float[][] c=costs(next);for(int i=0;i<64;i++)c[i][VisualMoveTracker.PIECES.indexOf(next.board[i/8][i%8])]=.05f;assertNull(CameraOpening.match(c));}
    @Test public void nonFiniteEvidenceRejected(){float[][] c=costs(CameraOpening.initial().successors().get(0));c[0][0]=Float.NaN;assertNull(CameraOpening.match(c));}
}
