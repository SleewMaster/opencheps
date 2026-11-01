package com.opencheps;
import com.opencheps.camera.CameraOpening;import com.opencheps.engine.*;import com.opencheps.vision.*;import com.opencheps.util.Side;import org.junit.Test;import static org.junit.Assert.*;
public class CameraSearchBudgetTest {
 @Test public void strongerQuietSearchGetsThreeSeconds(){assertEquals(3000,CameraSearchBudget.millis(CameraOpening.initial(),1500,true));}
 @Test public void fastModeUsesTheChosenBudget(){assertEquals(1500,CameraSearchBudget.millis(CameraOpening.initial(),1500,false));}
 @Test public void keepsLongerUserSelection(){assertEquals(8000,CameraSearchBudget.millis(CameraOpening.initial(),8000,true));}
 @Test public void checkGetsExtraTime(){ChessPosition p=PositionSeed.parse("4k3/8/8/8/8/8/4r3/4K3 w - - 0 1",Side.WHITE).position;assertEquals(6000,CameraSearchBudget.millis(p,1500,true));}
 @Test public void promotionGetsExtraTime(){ChessPosition p=PositionSeed.parse("4k3/P7/8/8/8/8/8/4K3 w - - 0 1",Side.WHITE).position;assertEquals(6000,CameraSearchBudget.millis(p,1500,true));}
}
