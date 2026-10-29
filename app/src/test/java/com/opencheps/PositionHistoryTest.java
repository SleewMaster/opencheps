package com.opencheps;
import com.opencheps.engine.PositionHistory;
import com.opencheps.util.Side;
import com.opencheps.vision.*;
import org.junit.Test;import static org.junit.Assert.*;
public class PositionHistoryTest {
 private static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
 private PositionHistory history(String fen){PositionHistory h=new PositionHistory();h.reset(fen);return h;}
 private ChessPosition next(ChessPosition p,String move){for(ChessPosition n:p.successors())if(n.move.equals(move))return n;throw new AssertionError("Illegal fixture move "+move);}
 private void play(PositionHistory h,String...moves){for(String m:moves)h.advance(next(PositionSeed.parse(h.snapshot().fen,Side.WHITE).position,m));}
 @Test public void threefoldHistoryReachesTheEngine(){PositionHistory h=history(START);play(h,"g1f3","g8f6","f3g1","f6g8","g1f3","g8f6","f3g1","f6g8");assertEquals(3,h.snapshot().repetitions);assertEquals(8,h.snapshot().halfmove);assertEquals(5,h.snapshot().fullmove);assertEquals("position fen "+START+" moves g1f3 g8f6 f3g1 f6g8 g1f3 g8f6 f3g1 f6g8",h.snapshot().command);}
 @Test public void twoMissedPliesKeepBothMoves(){PositionHistory h=history(START);ChessPosition a=next(PositionSeed.parse(START,Side.WHITE).position,"e2e4"),b=next(a,"c7c5");h.advance(a,b);assertEquals(2,h.snapshot().plies);assertEquals(2,h.snapshot().fullmove);assertTrue(h.snapshot().command.endsWith(" moves e2e4 c7c5"));}
 @Test public void invalidSecondPlyCannotPartiallyCommit(){PositionHistory h=history(START);ChessPosition start=PositionSeed.parse(START,Side.WHITE).position,a=next(start,"e2e4");String key=h.snapshot().key;try{h.advance(a,start);fail("must reject");}catch(IllegalArgumentException expected){}assertEquals(key,h.snapshot().key);assertEquals(0,h.snapshot().plies);}
 @Test public void snapshotsCannotChangeWhileSearching(){PositionHistory h=history(START);PositionHistory.Snapshot old=h.snapshot();play(h,"e2e4");assertEquals(START,old.fen);assertEquals(0,old.plies);assertFalse(old.key.equals(h.snapshot().key));}
 @Test public void restartingSameDiagramIsANewGame(){PositionHistory h=history(START);PositionHistory.Snapshot old=h.snapshot();h.reset(START);assertFalse(old.key.equals(h.snapshot().key));assertFalse(old.gameId==h.snapshot().gameId);}
 @Test public void joinPreservesKnownClocksWithoutInventingEarlierMoves(){PositionHistory h=history("4k1n1/8/8/8/8/8/8/4K1N1 b - - 73 40");play(h,"g8f6","g1f3");assertEquals(75,h.snapshot().halfmove);assertEquals(41,h.snapshot().fullmove);assertEquals(2,h.snapshot().plies);assertEquals(1,h.snapshot().repetitions);}
 @Test public void pawnAndCaptureResetClock(){PositionHistory h=history(START);play(h,"e2e4","d7d5","g1f3","g8f6");assertEquals(2,h.snapshot().halfmove);play(h,"e4d5");assertEquals(0,h.snapshot().halfmove);}
 @Test public void enPassantSurvivesCommandHistory(){PositionHistory h=history("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 15");play(h,"e5d6");assertEquals("4k3/8/3P4/8/8/8/8/4K3 b - - 0 15",h.snapshot().fen);assertTrue(h.snapshot().command.endsWith(" moves e5d6"));}
 @Test public void promotionIsKeptAsFiveCharacterMove(){PositionHistory h=history("4k3/P7/8/8/8/8/8/4K3 w - - 8 15");play(h,"a7a8q");assertTrue(h.snapshot().command.endsWith(" moves a7a8q"));assertEquals(0,h.snapshot().halfmove);}
 @Test public void castlingUpdatesRightsAndClock(){PositionHistory h=history("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 4 10");play(h,"e1g1");assertEquals("r3k2r/8/8/8/8/8/8/R4RK1 b kq - 5 10",h.snapshot().fen);}
 @Test public void irrelevantEnPassantDoesNotHideRepetition(){PositionHistory h=history("rnbqkbnr/pppppppp/8/8/P7/8/1PPPPPPP/RNBQKBNR b KQkq a3 0 1");play(h,"g8f6","g1f3","f6g8","f3g1");assertEquals(2,h.snapshot().repetitions);}
 @Test public void availableEnPassantIsADifferentPosition(){PositionHistory h=history("4k1n1/8/8/8/Pp6/8/8/4K1N1 b - a3 0 1");play(h,"g8f6","g1f3","f6g8","f3g1");assertEquals(1,h.snapshot().repetitions);}
 @Test public void lostCastlingRightPreventsFalseRepetition(){PositionHistory h=history("4k3/8/8/8/8/8/8/4K2R w K - 0 1");play(h,"h1h2","e8e7","h2h1","e7e8");assertEquals(1,h.snapshot().repetitions);}
}
