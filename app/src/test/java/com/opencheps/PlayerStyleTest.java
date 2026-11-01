package com.opencheps;
import com.opencheps.engine.*;
import com.opencheps.vision.*;
import com.opencheps.util.Side;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;

public class PlayerStyleTest {
 private static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
 private StockfishEngine.Pv p(String move,int cp,int depth){return new StockfishEngine.Pv(move,cp,false,depth);}
 private String choose(String fen,StockfishEngine.Pv... moves){return PlayerStyle.choose(fen,7,Arrays.asList(moves),true,1).get(0).uci;}
 @Test public void prefersDevelopmentAmongCloseMoves(){assertEquals("g1f3",choose(START,p("a2a3",30,16),p("g1f3",24,16)));}
 @Test public void preservesClearlyBestMove(){assertEquals("a2a3",choose(START,p("a2a3",90,16),p("g1f3",20,16)));}
 @Test public void protectsWinningEvaluation(){assertEquals("a2a3",choose(START,p("a2a3",90,16),p("g1f3",79,16)));}
 @Test public void doesNotCrossIntoNegativeEvaluation(){assertEquals("a2a3",choose(START,p("a2a3",2,16),p("g1f3",-1,16)));}
 @Test public void mixedDepthsCannotInfluenceStyle(){assertEquals("a2a3",choose(START,p("a2a3",30,17),p("g1f3",30,16)));}
 @Test public void boundsCannotInfluenceStyle(){assertEquals("a2a3",choose(START,p("a2a3",30,16),new StockfishEngine.Pv("g1f3",30,false,16,false)));assertEquals("a2a3",choose(START,new StockfishEngine.Pv("a2a3",30,false,16,false),p("g1f3",30,16)));}
 @Test public void shallowOrMissingScoresKeepAuthoritativeMove(){assertEquals("a2a3",choose(START,p("a2a3",0,0),p("g1f3",0,16)));assertEquals("a2a3",choose(START,p("a2a3",30,10),p("g1f3",30,10)));}
 @Test public void illegalAlternativeCannotBeChosen(){assertEquals("a2a3",choose(START,p("a2a3",30,16),p("g1g5",30,16)));}
 @Test public void mateHasPriorityOverStyle(){assertEquals("g6g7",choose(MateProofTest.WHITE,new StockfishEngine.Pv("g6g7",99999,true,16),p("g6g5",99990,16)));assertEquals("a2a3",choose(START,new StockfishEngine.Pv("a2a3",-99996,true,16),p("g1f3",0,16)));}
 @Test public void stockfishModePreservesEngineOrder(){List<StockfishEngine.Pv> moves=Arrays.asList(p("a2a3",30,16),p("g1f3",24,16));assertEquals(moves,PlayerStyle.choose(START,7,moves,false,3));}
 @Test public void choiceIsStableAndKeepsSelectedEvaluation(){List<StockfishEngine.Pv> moves=Arrays.asList(p("a2a3",30,16),p("g1f3",24,16));List<StockfishEngine.Pv> first=PlayerStyle.choose(START,7,moves,true,3);for(int n=0;n<10;n++)assertEquals(first,PlayerStyle.choose(START,7,moves,true,3));assertEquals(24,first.get(0).cp);assertEquals(2,first.size());assertEquals("a2a3",moves.get(0).uci);}
 @Test public void blackDevelopsToo(){String fen=START.replace(" w "," b ");assertEquals("g8f6",choose(fen,p("a7a6",30,16),p("g8f6",24,16)));}
 @Test public void invalidBoardAndEmptyAnalysisAreSafe(){assertEquals("a2a3",choose("bad",p("a2a3",30,16),p("g1f3",30,16)));assertTrue(PlayerStyle.choose(START,7,Collections.emptyList(),true,1).isEmpty());}
 @Test public void searchBudgetAndCandidatesAreExplicit(){assertEquals(5000,PlayerStyle.searchMillis(true,1500));assertEquals(8000,PlayerStyle.searchMillis(true,8000));assertEquals(1500,PlayerStyle.searchMillis(false,1500));assertEquals(3,PlayerStyle.searchCount(true,1));assertEquals(1,PlayerStyle.searchCount(false,1));}
 @Test public void ideaMatchesVisibleMove(){assertEquals("Развитие лёгкой фигуры",PlayerStyle.idea(START,"g1f3"));assertEquals("Мат королю",PlayerStyle.idea(MateProofTest.WHITE,"g6g7"));assertEquals("",PlayerStyle.idea(START,"g1g5"));}
 @Test public void nearEqualPropertyAcrossOpeningContinuations(){ChessPosition start=PositionSeed.parse(START,Side.WHITE).position;for(ChessPosition first:start.successors()){
  String fen=FenBuilder.build(first.board,Side.WHITE,first.turn,first.rights,first.ep,0,1);List<StockfishEngine.Pv> candidates=new ArrayList<>();int i=0;for(ChessPosition next:first.successors()){if(i==3)break;candidates.add(p(next.move,30-9*i++,16));}
  for(int game=0;game<20;game++){StockfishEngine.Pv selected=PlayerStyle.choose(fen,game,candidates,true,1).get(0);assertTrue(candidates.contains(selected));assertTrue(30-selected.cp<=18);boolean legal=false;for(ChessPosition next:first.successors())if(next.move.equals(selected.uci))legal=true;assertTrue(legal);}
 }}
}
