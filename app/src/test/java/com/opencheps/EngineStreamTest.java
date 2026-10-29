package com.opencheps;
import com.opencheps.engine.StockfishEngine;
import com.opencheps.engine.PositionHistory;
import com.opencheps.vision.PositionSeed;
import com.opencheps.vision.ChessPosition;
import com.opencheps.util.Side;
import org.junit.Test;import static org.junit.Assert.*;
import java.io.*;import java.lang.reflect.Field;import java.util.*;
public class EngineStreamTest {
 private StockfishEngine engine(Reader source,Writer commands)throws Exception{
  StockfishEngine e=new StockfishEngine();set(e,"ready",true);set(e,"out",new BufferedReader(source));set(e,"in",new BufferedWriter(commands));return e;
 }
 private void set(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 @Test public void terminalBestmoveIsAuthoritative()throws Exception{
  StockfishEngine e=engine(new StringReader("info depth 12 score cp 25 pv d2d4\nbestmove e2e4\n"),new StringWriter());
  assertEquals("e2e4",e.topMoves("fake",1500,1).get(0).uci);
 }
 @Test public void publishesBeforeSearchCompletes()throws Exception{
  PipedWriter source=new PipedWriter();PipedReader input=new PipedReader(source);StringWriter commands=new StringWriter();StockfishEngine e=engine(input,commands);
  List<StockfishEngine.Pv> partial=new ArrayList<>();
  Thread feeder=new Thread(() -> {try{Thread.sleep(320);source.write("info depth 14 score cp 22 pv e2e4\n");source.flush();Thread.sleep(80);source.write("bestmove e2e4\n");source.close();}catch(Exception ex){throw new RuntimeException(ex);}});
  feeder.start();List<StockfishEngine.Pv> finalMoves=e.topMoves("fake",1500,1,p -> {partial.addAll(p);e.cancelSearch();});feeder.join();
  assertEquals("e2e4",partial.get(0).uci);assertEquals(14,partial.get(0).depth);assertEquals("e2e4",finalMoves.get(0).uci);assertTrue(commands.toString().contains("stop\n"));
 }
 @Test public void noLegalMoveGivesEmptyResult()throws Exception{assertTrue(engine(new StringReader("bestmove (none)\n"),new StringWriter()).topMoves("fake",1500,1).isEmpty());}
 @Test public void sendsConfirmedMovesAndResetsOnlyBetweenGames()throws Exception{
  String start="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
  PositionHistory h=new PositionHistory();h.reset(start);
  for(String move:new String[]{"g1f3","g8f6","f3g1","f6g8"}){
   ChessPosition p=PositionSeed.parse(h.snapshot().fen,Side.WHITE).position;
   for(ChessPosition n:p.successors())if(n.move.equals(move)){h.advance(n);break;}
  }
  StringWriter commands=new StringWriter();
  StockfishEngine e=engine(new StringReader("readyok\ninfo depth 15 score cp 20 pv e2e4\nbestmove e2e4\nbestmove d2d4\nreadyok\nbestmove e2e4\n"),commands);
  assertEquals("e2e4",e.topMoves(h.snapshot(),1500,1).get(0).uci);
  assertTrue(commands.toString().contains("position fen "+start+" moves g1f3 g8f6 f3g1 f6g8\n"));
  e.topMoves(h.snapshot(),1500,1);
  assertEquals(1,commands.toString().split("ucinewgame",-1).length-1);
  h.reset(start);e.topMoves(h.snapshot(),1500,1);
  assertEquals(2,commands.toString().split("ucinewgame",-1).length-1);
 }
}
