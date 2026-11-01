package com.opencheps;
import com.opencheps.engine.*;import org.junit.Test;import static org.junit.Assert.*;
public class QuickDecisionTest {
 public static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
 public static final String CASTLE="r3k2r/ppp2ppp/2n1bn2/8/8/2N1PN2/PPP2PPP/R3K2R w KQkq - 0 10";
 private UciInfo info(int d,int index,int cp,String move){return UciInfo.parse("info depth "+d+" multipv "+index+" score cp "+cp+" pv "+move);}
 @Test public void stableCastleFinishesEarly(){QuickDecision q=new QuickDecision(CASTLE,1,1,false);assertFalse(q.accept(info(12,1,30,"e1g1")));assertTrue(q.accept(info(13,1,32,"e1g1")));}
 @Test public void stableQuietMoveNeedsMoreDepth(){QuickDecision q=new QuickDecision(START,1,1,false);for(int d=12;d<16;d++)assertFalse(q.accept(info(d,1,30,"e2e4")));assertTrue(q.accept(info(16,1,30,"e2e4")));}
 @Test public void allAlternativesMustCompleteAtSameDepth(){QuickDecision q=new QuickDecision(START,1,3,false);for(int d=12;d<=16;d++){assertFalse(q.accept(info(d,1,30,"e2e4")));assertFalse(q.accept(info(d-1,2,25,"d2d4")));assertFalse(q.accept(info(d,2,25,"d2d4")));assertEquals(d==16,q.accept(info(d,3,24,"g1f3")));}}
 @Test public void humanModeAlsoNeedsStableChoice(){QuickDecision q=new QuickDecision(START,7,3,true);for(int d=12;d<=16;d++){assertFalse(q.accept(info(d,1,30,"a2a3")));assertFalse(q.accept(info(d,2,24,"g1f3")));assertEquals(d==16,q.accept(info(d,3,20,"d2d4")));}}
 @Test public void changingChoiceDoesNotStop(){QuickDecision q=new QuickDecision(CASTLE,1,1,false);assertFalse(q.accept(info(12,1,30,"e1g1")));assertFalse(q.accept(info(13,1,30,"e1c1")));assertFalse(q.accept(info(14,1,30,"e1g1")));}
 @Test public void changingEvaluationDoesNotStop(){QuickDecision q=new QuickDecision(CASTLE,1,1,false);assertFalse(q.accept(info(12,1,30,"e1g1")));assertFalse(q.accept(info(13,1,70,"e1g1")));}
 @Test public void repeatedDepthIsNotExtraEvidence(){QuickDecision q=new QuickDecision(CASTLE,1,1,false);for(int n=0;n<8;n++)assertFalse(q.accept(info(12,1,30,"e1g1")));}
 @Test public void boundIsNotEvidence(){QuickDecision q=new QuickDecision(CASTLE,1,1,false);for(int d=12;d<25;d++)assertFalse(q.accept(UciInfo.parse("info depth "+d+" score cp 30 lowerbound pv e1g1")));}
 @Test public void illegalMoveIsNotEvidence(){QuickDecision q=new QuickDecision(START,1,1,false);for(int d=12;d<25;d++)assertFalse(q.accept(info(d,1,30,"e1g1")));}
 @Test public void checkDefenseUsesFullBudget(){QuickDecision q=new QuickDecision("4k3/8/8/8/8/4r3/8/4K2R w K - 0 10",1,1,false);for(int d=12;d<25;d++)assertFalse(q.accept(info(d,1,30,"e1d1")));}
 @Test public void losingPositionUsesFullBudget(){QuickDecision q=new QuickDecision(START,1,1,false);for(int d=12;d<25;d++)assertFalse(q.accept(info(d,1,-100,"e2e4")));}
 @Test public void mateAndInvalidFenUseExistingVerification(){for(String fen:new String[]{CASTLE,"bad"}){QuickDecision q=new QuickDecision(fen,1,1,false);for(int d=12;d<25;d++)assertFalse(q.accept(UciInfo.parse("info depth "+d+" score mate 3 pv e1g1")));}}
}
