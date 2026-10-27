package com.opencheps;
import com.opencheps.vision.ArrowOcclusion;import com.opencheps.vision.SquareSampler;
import org.junit.Test;import static org.junit.Assert.*;
public class ArrowOcclusionTest {
 private ArrowOcclusion vertical(){return new ArrowOcclusion(new ArrowOcclusion.Mark(450,650,450,450,100,true,true));}
 @Test public void masksOriginTipAndShaft(){ArrowOcclusion m=vertical();assertTrue(m.excludes(450,650));assertTrue(m.excludes(450,450));assertTrue(m.excludes(450,550));}
 @Test public void preservesEvidenceBesideArrow(){ArrowOcclusion m=vertical();assertFalse(m.excludes(477,550));assertFalse(m.excludes(477,435));}
 @Test public void masksLabelAndFrame(){ArrowOcclusion m=vertical();assertTrue(m.excludes(450,481));assertTrue(m.excludes(493,450));}
 @Test public void masksDiagonal(){ArrowOcclusion m=new ArrowOcclusion(new ArrowOcclusion.Mark(650,750,550,550,100,false,false));assertTrue(m.excludes(600,650));assertFalse(m.excludes(700,650));}
 @Test public void leavesHalfTheDestinationVisible(){ArrowOcclusion m=vertical();int valid=0,total=0;for(int y=408;y<492;y+=4)for(int x=408;x<492;x+=4){total++;if(!m.excludes(x,y))valid++;}assertTrue(valid>total*.45f);}
 @Test public void ignoresMaskedFeaturePixels(){assertEquals(0f,SquareSampler.lumDistance(new float[]{0,Float.NaN,1,1},new float[]{0,100,1,1}),0.00001f);}
 @Test public void rejectsAlmostFullyCoveredSquare(){assertTrue(Float.isInfinite(SquareSampler.lumDistance(new float[]{Float.NaN,Float.NaN,1,Float.NaN},new float[4])));}
}
