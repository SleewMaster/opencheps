package com.opencheps;
import com.opencheps.vision.*;import org.junit.Test;import static org.junit.Assert.*;
public class FeatureAlignmentTest {
 private float[] rook(float tone){int n=SquareSampler.N;float[] f=new float[n*n*2];for(int y=3;y<18;y++)for(int x=5;x<15;x++)if(y<7||y>13||x<8||x>11){f[(y*n+x)*2]=tone;f[(y*n+x)*2+1]=1-tone;}return f;}
 @Test public void toleratesSmallDisplacementAndScale(){float[] template=rook(.85f),shifted=FeatureAlignment.warp(template,1.08f,.65f,0);float raw=SquareSampler.lumDistance(shifted,template);assertTrue(new FeatureAlignment(shifted,null).distance(template,false)<raw*.6f);}
 @Test public void unchangedPieceRetainsZeroError(){float[] f=rook(.8f);assertEquals(0,new FeatureAlignment(f,null).distance(f,false),.00001);}
 @Test public void alignmentDoesNotErasePieceColor(){float[] white=rook(.85f),black=rook(.15f),shifted=FeatureAlignment.warp(white,.92f,0,.65f);FeatureAlignment a=new FeatureAlignment(shifted,shifted);assertTrue(a.distance(white,true)<a.distance(black,true));}
}
