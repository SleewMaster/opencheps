package com.opencheps;
import com.opencheps.engine.*;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class CompletedAdviceTest {
 private List<StockfishEngine.Pv> move(String uci){return new ArrayList<>(Collections.singletonList(new StockfishEngine.Pv(uci,20,false,16)));}
 @Test public void keepsFirstTerminalAdviceAcrossRecapture(){CompletedAdvice c=new CompletedAdvice();c.put("position",move("e2e4"));c.put("position",move("d2d4"));assertEquals("e2e4",c.get("position").get(0).uci);}
 @Test public void differentPositionGetsDifferentAdvice(){CompletedAdvice c=new CompletedAdvice();c.put("one",move("e2e4"));c.put("two",move("g1f3"));assertEquals("g1f3",c.get("two").get(0).uci);}
 @Test public void cannotMutateCachedList(){CompletedAdvice c=new CompletedAdvice();List<StockfishEngine.Pv> p=move("e2e4");c.put("one",p);p.clear();c.get("one").clear();assertEquals(1,c.get("one").size());}
 @Test public void limitsMemoryAndResetsForNewGame(){CompletedAdvice c=new CompletedAdvice();for(int i=0;i<20;i++)c.put("p"+i,move("e2e4"));assertNull(c.get("p0"));assertNotNull(c.get("p19"));c.clear();assertNull(c.get("p19"));}
 @Test public void emptySearchDoesNotPoisonPosition(){CompletedAdvice c=new CompletedAdvice();c.put("p",Collections.emptyList());assertNull(c.get("p"));c.put("p",move("e2e4"));assertNotNull(c.get("p"));}
}
