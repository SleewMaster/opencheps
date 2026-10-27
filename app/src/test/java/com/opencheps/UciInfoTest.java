package com.opencheps;
import com.opencheps.engine.UciInfo;
import org.junit.Test;import static org.junit.Assert.*;
public class UciInfoTest {
 @Test public void readsDepthMoveAndScore(){UciInfo p=UciInfo.parse("info depth 16 seldepth 20 multipv 1 score cp 37 nodes 1000 pv e2e4 e7e5");assertNotNull(p);assertEquals("e2e4",p.move);assertEquals(16,p.depth);assertEquals(37,p.cp);assertFalse(p.mate);}
 @Test public void readsAlternative(){assertEquals(3,UciInfo.parse("info depth 12 multipv 3 score cp -50 pv g1f3").index);}
 @Test public void readsPromotion(){assertEquals("a7a8n",UciInfo.parse("info depth 10 score cp 80 pv a7a8n").move);}
 @Test public void readsWinningMate(){UciInfo p=UciInfo.parse("info depth 18 score mate 3 pv h5h7");assertTrue(p.mate);assertEquals(99997,p.cp);}
 @Test public void readsLosingMate(){UciInfo p=UciInfo.parse("info depth 18 score mate -4 pv h5h7");assertTrue(p.mate);assertEquals(-99996,p.cp);}
 @Test public void rejectsNoScore(){assertNull(UciInfo.parse("info depth 10 pv e2e4"));}
 @Test public void rejectsMalformedNumbers(){assertNull(UciInfo.parse("info depth fast score cp 20 pv e2e4"));}
 @Test public void rejectsInvalidMove(){assertNull(UciInfo.parse("info depth 12 score cp 0 pv i2i4"));assertNull(UciInfo.parse("info depth 12 score cp 0 pv 0000"));}
 @Test public void rejectsDiagnostics(){assertNull(UciInfo.parse("info string loading network"));assertNull(UciInfo.parse("bestmove e2e4"));assertNull(UciInfo.parse(null));}
 @Test public void moveSyntax(){assertTrue(UciInfo.isMove("e1g1"));assertFalse(UciInfo.isMove("e7e8k"));}
}
