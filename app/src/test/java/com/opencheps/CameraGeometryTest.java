package com.opencheps;
import com.opencheps.camera.*;
import org.junit.Test;import static org.junit.Assert.*;
public class CameraGeometryTest {
    @Test public void acceptsPerspectiveBoard(){assertTrue(CameraGeometry.valid(new float[]{100,80,500,120,550,600,60,620},640,700));}
    @Test public void rejectsCrossedCorners(){assertFalse(CameraGeometry.valid(new float[]{100,80,550,600,500,120,60,620},640,700));}
    @Test public void rejectsReversedOrder(){assertFalse(CameraGeometry.valid(new float[]{60,620,550,600,500,120,100,80},640,700));}
    @Test public void rejectsThinBoard(){assertFalse(CameraGeometry.valid(new float[]{10,10,500,10,500,20,10,20},640,700));}
    @Test public void rejectsOutOfFrame(){assertFalse(CameraGeometry.valid(new float[]{-1,80,500,120,550,600,60,620},640,700));}
    @Test public void rejectsNonFinite(){assertFalse(CameraGeometry.valid(new float[]{100,80,Float.NaN,120,550,600,60,620},640,700));}
    @Test public void requiresConsecutiveFrames(){FrameConsensus f=new FrameConsensus();assertFalse(f.accept("start"));assertTrue(f.accept("start"));assertFalse(f.accept("e4"));assertTrue(f.accept("e4"));}
    @Test public void oneDroppedFrameKeepsClearEvidence(){FrameConsensus f=new FrameConsensus();f.accept("e4");assertFalse(f.accept(null));assertTrue(f.accept("e4"));}
    @Test public void repeatedUncertaintyResetsEvidence(){FrameConsensus f=new FrameConsensus();f.accept("e4");f.accept(null);assertFalse(f.accept(null));assertFalse(f.accept("e4"));assertTrue(f.accept("e4"));}
    @Test public void competingFramesDoNotCommit(){FrameConsensus f=new FrameConsensus();f.accept("e4");assertFalse(f.accept("d4"));assertFalse(f.accept("e4"));}
    private float[][] signal(float value){float[][] c=new float[64][13];for(float[] row:c)row[0]=value;return c;}
    @Test public void normalizesFaintPieceSignal(){assertEquals(8f,CameraConfidence.scale(signal(.0125f)),.0001f);}
    @Test public void neverRelaxesAbsoluteErrorCaps(){assertEquals(1f,CameraConfidence.scale(signal(.4f)),.0001f);}
    @Test public void boundsFaintSignalAmplification(){assertEquals(10f,CameraConfidence.scale(signal(.001f)),.0001f);}
    @Test public void rejectsInvalidCalibrationScale(){float[][] c=signal(.02f);c[0][0]=Float.NaN;assertEquals(1f,CameraConfidence.scale(c),.0001f);}
    @Test public void handlesFaintPiecesOnOneSquareColour(){float[][] c=signal(.2f);for(int i=0;i<8;i++)c[i][0]=.0125f;assertEquals(8f,CameraConfidence.scale(c),.0001f);}
}
