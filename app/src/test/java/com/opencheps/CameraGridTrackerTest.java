package com.opencheps;
import com.opencheps.camera.*;import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class CameraGridTrackerTest {
 private static final int W=320,H=360;private static final float[] Q={40,50,280,50,280,290,40,290};
 private BoardMotionTracker.Frame scene(){float[]p=new float[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++)p[y*W+x]=(x>=40&&x<280&&y>=50&&y<290)?((((x-40)/30+(y-50)/30)&1)==0?220:100):24;return new BoardMotionTracker.Frame(W,H,p);}
 private void loseFeatures(CameraGridTracker g)throws Exception{java.lang.reflect.Field m=CameraGridTracker.class.getDeclaredField("motion");m.setAccessible(true);Object tracker=m.get(g);java.lang.reflect.Field f=BoardMotionTracker.class.getDeclaredField("features");f.setAccessible(true);((List<?>)f.get(tracker)).clear();}
 private void check(CameraGridTracker.Result r){assertNotNull(r);assertTrue(r.readable);for(int i=0;i<4;i++)assertTrue(Math.hypot(r.corners[2*i]-Q[2*i],r.corners[2*i+1]-Q[2*i+1])<2);}
 @Test public void lostOpticalFeaturesRefitInSameFrame()throws Exception{CameraGridTracker g=new CameraGridTracker();check(g.update(scene(),Q));loseFeatures(g);CameraGridTracker.Result r=g.update(scene(),Q);check(r);assertTrue(r.refitted);}
 @Test public void repeatedFeatureLossNeverConsumesClearReads()throws Exception{CameraGridTracker g=new CameraGridTracker();check(g.update(scene(),Q));for(int i=0;i<6;i++){loseFeatures(g);check(g.update(scene(),Q));}}
 @Test public void lostBoardDoesNotReuseOldGeometry(){CameraGridTracker g=new CameraGridTracker();check(g.update(scene(),Q));assertNull(g.update(new BoardMotionTracker.Frame(W,H,new float[W*H]),Q));check(g.update(scene(),Q));}
 @Test public void flatImageCannotSeedGeometry(){assertNull(new CameraGridTracker().update(new BoardMotionTracker.Frame(W,H,new float[W*H]),Q));}
 @Test public void severeBlurCannotBypassFocusCheck()throws Exception{CameraGridTracker g=new CameraGridTracker();check(g.update(scene(),Q));loseFeatures(g);BoardMotionTracker.Frame f=scene();float[]p=new float[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++){float sum=0;for(int dy=-7;dy<=7;dy++)for(int dx=-7;dx<=7;dx++)sum+=f.gray[Math.max(0,Math.min(H-1,y+dy))*W+Math.max(0,Math.min(W-1,x+dx))];p[y*W+x]=sum/225;}CameraGridTracker.Result r=g.update(new BoardMotionTracker.Frame(W,H,p),Q);assertTrue(r==null||!r.readable);}
}
