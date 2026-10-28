package com.opencheps;
import com.opencheps.camera.CameraBackground;
import org.junit.Test;import static org.junit.Assert.*;
public class CameraBackgroundTest {
    private int[] board(){int[] p=new int[320*320];for(int y=0;y<320;y++)for(int x=0;x<320;x++)p[y*320+x]=((x/40+y/40)&1)==0?0xffeeeed2:0xff769656;return p;}
    private void fill(int[] p,int x1,int y1,int x2,int y2,int color){for(int y=y1;y<y2;y++)for(int x=x1;x<x2;x++)p[y*320+x]=color;}
    @Test public void acceptsVisibleBoard(){int[] p=board();assertTrue(new CameraBackground(p,320,320).visible(p,320,320));}
    @Test public void allowsTwoMoveHighlights(){int[] p=board();CameraBackground b=new CameraBackground(p,320,320);fill(p,160,160,200,200,0xffffff60);fill(p,160,240,200,280,0xffffff60);assertTrue(b.visible(p,320,320));}
    @Test public void rejectsHandOverEmptyCells(){int[] p=board();CameraBackground b=new CameraBackground(p,320,320);fill(p,80,80,200,200,0xffa98367);assertFalse(b.visible(p,320,320));}
    @Test public void allowsGlobalExposureShift(){int[] p=board();CameraBackground b=new CameraBackground(p,320,320);for(int i=0;i<p.length;i++){int c=p[i];p[i]=0xff000000|(Math.min(255,((c>>16)&255)+8)<<16)|(Math.min(255,((c>>8)&255)+5)<<8)|Math.min(255,(c&255)+10);}assertTrue(b.visible(p,320,320));}
    @Test public void ignoresPieceCenters(){int[] p=board();CameraBackground b=new CameraBackground(p,320,320);for(int r=0;r<8;r++)for(int f=0;f<8;f++)fill(p,f*40+12,r*40+12,f*40+28,r*40+28,0xff111111);assertTrue(b.visible(p,320,320));}
    @Test public void recoversWhenHandIsRemoved(){int[] clear=board(),covered=clear.clone();CameraBackground b=new CameraBackground(clear,320,320);fill(covered,80,80,200,200,0xffa98367);assertFalse(b.visible(covered,320,320));assertTrue(b.visible(clear,320,320));}
}
