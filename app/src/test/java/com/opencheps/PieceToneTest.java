package com.opencheps;
import com.opencheps.vision.*;import org.junit.Test;import static org.junit.Assert.*;
public class PieceToneTest {
 private float[] feature(float value){float[] f=new float[800];for(int i=0;i<80;i++){f[i*2]=value;f[i*2+1]=1-value;}return f;}
 @Test public void separatesDimWhiteFromBlackInTheSameFrame(){PieceTone t=new PieceTone(new float[][]{feature(.62f),feature(.68f),feature(.20f),feature(.25f),new float[800]});assertEquals(0,t.penalty(0,'P'),0);assertTrue(t.penalty(0,'p')>.02f);assertEquals(0,t.penalty(2,'p'),0);assertTrue(t.penalty(2,'P')>.02f);assertEquals(0,t.penalty(4,'P'),0);}
 @Test public void ambiguousBrightnessDoesNotForceAColor(){PieceTone t=new PieceTone(new float[][]{feature(.49f),feature(.50f),feature(.51f),feature(.52f)});assertEquals(0,t.penalty(0,'P'),0);assertEquals(0,t.penalty(0,'p'),0);}
}
