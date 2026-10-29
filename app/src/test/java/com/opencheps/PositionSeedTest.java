package com.opencheps;
import com.opencheps.vision.*;import com.opencheps.util.Side;
import org.junit.Test;import static org.junit.Assert.*;
public class PositionSeedTest {
 private final String late="8/n5r1/4Q1pk/P7/7P/8/6P1/6K1 w - - 0 40";
 @Test public void lateGameDoesNotNeedInitialPosition(){PositionSeed p=PositionSeed.parse(late,Side.WHITE);assertNotNull(p);assertEquals(late,p.fen());assertFalse(p.position.successors().isEmpty());}
 @Test public void turnIsIndependentOfOrientation(){PositionSeed p=PositionSeed.parse(late,Side.BLACK);assertEquals(Side.WHITE,p.position.turn);assertEquals(Side.BLACK,p.perspective);}
 @Test public void rightsAreNotInferredFromRooks(){PositionSeed p=PositionSeed.parse("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 10",Side.WHITE);for(ChessPosition next:p.position.successors())assertFalse(next.move.equals("e1g1")||next.move.equals("e1c1"));}
 @Test public void explicitCastlingRightsArePreserved(){PositionSeed p=PositionSeed.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 10",Side.WHITE);boolean found=false;for(ChessPosition next:p.position.successors())if(next.move.equals("e1g1"))found=true;assertTrue(found);}
 @Test public void rightsNeedTheirKingAndRook(){assertNull(PositionSeed.parse(late.replace(" w - -"," w K -"),Side.WHITE));}
 @Test public void rejectsWrongTurnWhenLastMoverIsInCheck(){assertNull(PositionSeed.parse("7k/8/8/8/8/8/8/K6R w - - 0 1",Side.WHITE));assertNotNull(PositionSeed.parse("7k/8/8/8/8/8/8/K6R b - - 0 1",Side.WHITE));}
 @Test public void legalEnPassantIsPreserved(){PositionSeed p=PositionSeed.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 20",Side.WHITE);assertNotNull(p);boolean found=false;for(ChessPosition n:p.position.successors())if(n.move.equals("e5d6"))found=true;assertTrue(found);}
 @Test public void rejectsInventedEnPassant(){assertNull(PositionSeed.parse(late.replace(" - - "," - d6 "),Side.WHITE));}
 @Test public void rejectsKinglessDiagram(){assertNull(PositionSeed.parse("8/8/8/8/8/8/8/8 w - - 0 1",Side.WHITE));}
 @Test public void rejectsMalformedFen(){for(String s:new String[]{"",late+" extra",late.replace("0 40","x 40"),late.replace(" w "," x "),late.replace("8/n","9/n")})assertNull(PositionSeed.parse(s,Side.WHITE));}
}
