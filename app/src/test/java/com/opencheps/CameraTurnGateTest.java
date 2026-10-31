package com.opencheps;
import com.opencheps.camera.*;import com.opencheps.engine.PositionHistory;import com.opencheps.util.Side;import com.opencheps.vision.*;import org.junit.Test;import static org.junit.Assert.*;
public class CameraTurnGateTest {
    private PositionHistory start(){PositionHistory h=new PositionHistory();h.reset("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");return h;}
    private ChessPosition play(ChessPosition p,String move){for(ChessPosition n:p.successors())if(n.move.equals(move))return n;throw new AssertionError(move);}
    @Test public void freezesOneMoveForSameTurn(){PositionHistory h=start();CameraTurnGate g=new CameraTurnGate();assertTrue(g.observe(h.snapshot(),Side.WHITE));assertEquals("e2e4",g.choose(h.snapshot(),"e2e4"));for(int i=0;i<20;i++){assertTrue(g.observe(h.snapshot(),Side.WHITE));assertEquals("e2e4",g.choose(h.snapshot(),"d2d4"));}}
    @Test public void waitsForOpponentThenUnlocksNextTurn(){PositionHistory h=start();CameraTurnGate g=new CameraTurnGate();g.observe(h.snapshot(),Side.WHITE);g.choose(h.snapshot(),"e2e4");ChessPosition e4=play(CameraOpening.initial(),"e2e4"),e5=play(e4,"e7e5");h.advance(e4);assertFalse(g.observe(h.snapshot(),Side.WHITE));assertNull(g.choose(h.snapshot(),"e7e5"));h.advance(e5);assertTrue(g.observe(h.snapshot(),Side.WHITE));assertEquals("g1f3",g.choose(h.snapshot(),"g1f3"));}
    @Test public void missedIntermediateNeedsFreshFinalFrameThenContinues(){
        PositionHistory h=start();CameraTurnGate g=new CameraTurnGate();g.observe(h.snapshot(),Side.WHITE);g.choose(h.snapshot(),"e2e4");
        ChessPosition e4=play(CameraOpening.initial(),"e2e4"),e5=play(e4,"e7e5");h.advance(e4,e5);
        assertFalse(g.observe(h.snapshot(),Side.WHITE));assertTrue(g.waitingForConfirmation());assertFalse(g.requiresReview());assertNull(g.choose(h.snapshot(),"g1f3"));
        assertTrue(g.observe(h.snapshot(),Side.WHITE));assertFalse(g.waitingForConfirmation());assertEquals("g1f3",g.choose(h.snapshot(),"g1f3"));
        for(int i=0;i<20;i++){assertTrue(g.observe(h.snapshot(),Side.WHITE));assertEquals("g1f3",g.choose(h.snapshot(),"b1c3"));}
    }
    @Test public void blackAlternativeAndFastReplyRecoverWithoutManualReview(){
        PositionHistory h=start();ChessPosition e4=play(CameraOpening.initial(),"e2e4");h.advance(e4);CameraTurnGate g=new CameraTurnGate();g.observe(h.snapshot(),Side.BLACK);g.choose(h.snapshot(),"d7d5");
        ChessPosition d6=play(e4,"d7d6"),nf3=play(d6,"g1f3");h.advance(d6,nf3);
        assertFalse(g.observe(h.snapshot(),Side.BLACK));assertFalse(g.requiresReview());assertNull(g.choose(h.snapshot(),"g8f6"));
        assertTrue(g.observe(h.snapshot(),Side.BLACK));assertEquals("g8f6",g.choose(h.snapshot(),"g8f6"));
    }
    @Test public void staleFrameCancelsPendingRecoveryAndKeepsOriginalAdvice(){
        PositionHistory h=start();CameraTurnGate g=new CameraTurnGate();PositionHistory.Snapshot before=h.snapshot();g.observe(before,Side.WHITE);g.choose(before,"e2e4");
        ChessPosition e4=play(CameraOpening.initial(),"e2e4"),e5=play(e4,"e7e5");h.advance(e4,e5);
        assertFalse(g.observe(h.snapshot(),Side.WHITE));assertTrue(g.observe(before,Side.WHITE));assertFalse(g.waitingForConfirmation());assertEquals("e2e4",g.choose(before,"d2d4"));
        assertFalse(g.observe(h.snapshot(),Side.WHITE));assertTrue(g.observe(h.snapshot(),Side.WHITE));
    }
    @Test public void newReviewedPositionClearsOldTurnLock(){
        PositionHistory h=start();CameraTurnGate g=new CameraTurnGate();g.observe(h.snapshot(),Side.WHITE);g.choose(h.snapshot(),"e2e4");
        ChessPosition e4=play(CameraOpening.initial(),"e2e4"),e5=play(e4,"e7e5");h.advance(e4,e5);assertFalse(g.observe(h.snapshot(),Side.WHITE));h.reset(h.snapshot().fen);
        assertTrue(g.observe(h.snapshot(),Side.WHITE));assertEquals("g1f3",g.choose(h.snapshot(),"g1f3"));
    }
    @Test public void noAdviceOnOpponentTurn(){PositionHistory h=start();CameraTurnGate g=new CameraTurnGate();assertFalse(g.observe(h.snapshot(),Side.BLACK));assertNull(g.choose(h.snapshot(),"e2e4"));}
    @Test public void noAdviceFromAnotherGameOrUnobservedPosition(){PositionHistory h=start(),other=start();CameraTurnGate g=new CameraTurnGate();g.observe(h.snapshot(),Side.WHITE);assertNull(g.choose(other.snapshot(),"e2e4"));assertFalse(g.permits(null));assertFalse(g.observe(null,Side.WHITE));}
}
