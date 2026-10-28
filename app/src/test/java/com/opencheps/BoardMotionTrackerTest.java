package com.opencheps;
import com.opencheps.camera.BoardMotionTracker;
import org.junit.Test;import static org.junit.Assert.*;

public class BoardMotionTrackerTest {
    private static final int W=320,H=360;
    private static final float[] CORNERS={40,50,280,50,280,290,40,290};
    private static final double[] ID={1,0,0,0,1,0,0,0,1};
    private float scene(double x,double y){
        if(x<40||x>=280||y<50||y>=290)return 24;
        int f=(int)((x-40)/30),r=(int)((y-50)/30);
        float background=((r+f)&1)==0?220:100;
        if((r<2||r>5)&&Math.hypot((x-40)%30-15,(y-50)%30-15)<8)return r<2?35:250;
        return background;
    }
    private BoardMotionTracker.Frame frame(double[] m){
        double a=m[0],b=m[1],c=m[2],d=m[3],e=m[4],f=m[5],g=m[6],h=m[7],i=m[8];
        double[] inv={e*i-f*h,c*h-b*i,b*f-c*e,f*g-d*i,a*i-c*g,c*d-a*f,d*h-e*g,b*g-a*h,a*e-b*d};
        float[] px=new float[W*H];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float value=0;for(double oy:new double[]{-.25,.25})for(double ox:new double[]{-.25,.25}){
                double den=inv[6]*(x+ox)+inv[7]*(y+oy)+inv[8];value+=scene((inv[0]*(x+ox)+inv[1]*(y+oy)+inv[2])/den,(inv[3]*(x+ox)+inv[4]*(y+oy)+inv[5])/den);
            }px[y*W+x]=value/4;
        }return new BoardMotionTracker.Frame(W,H,px);
    }
    private double[] pose(double dx,double dy,double degrees,double scale){double a=Math.toRadians(degrees),co=Math.cos(a)*scale,si=Math.sin(a)*scale;return new double[]{co,-si,160-co*160+si*170+dx,si,co,170-si*160-co*170+dy,0,0,1};}
    private void check(BoardMotionTracker.Result result,double[] pose,double tolerance){
        assertNotNull(result);assertTrue(result.readable());assertTrue(result.inliers>=16);
        for(int j=0;j<4;j++){double x=CORNERS[j*2],y=CORNERS[j*2+1],den=pose[6]*x+pose[7]*y+pose[8];
            double ex=(pose[0]*x+pose[1]*y+pose[2])/den,ey=(pose[3]*x+pose[4]*y+pose[5])/den;
            if(Math.hypot(result.corners[j*2]-ex,result.corners[j*2+1]-ey)>tolerance)throw new AssertionError("corner "+j+" error="+Math.hypot(result.corners[j*2]-ex,result.corners[j*2+1]-ey));
        }
    }
    private BoardMotionTracker start(){BoardMotionTracker tracker=new BoardMotionTracker();assertNotNull(tracker.update(frame(ID),CORNERS));return tracker;}
    @Test public void stationaryDoesNotDrift(){check(start().update(frame(ID),CORNERS),ID,.5);}
    @Test public void tracksSubpixelTranslation(){double[] p=pose(4.25,-3.2,0,1);check(start().update(frame(p),CORNERS),p,1.2);}
    @Test public void tracksRotationAndScale(){double[] p=pose(2,-1,1.5,1.01);check(start().update(frame(p),CORNERS),p,1.5);}
    @Test public void tracksMildPerspective(){double[] p={1,.003,2,-.002,1,-1,.00003,-.000025,1};check(start().update(frame(p),CORNERS),p,1.5);}
    @Test public void repeatedShakeReturnsToAnchor(){BoardMotionTracker t=start();for(int j=0;j<20;j++){double[] p=pose(Math.sin(j)*4,Math.cos(j)*3,Math.sin(j*.7),1+Math.cos(j*.6)*.007);check(t.update(frame(p),CORNERS),p,1.8);}check(t.update(frame(ID),CORNERS),ID,.6);}
    @Test public void rejectsBlackout(){assertNull(start().update(new BoardMotionTracker.Frame(W,H,new float[W*H]),CORNERS));}
    @Test public void recoversAfterLostFrame(){BoardMotionTracker t=start();t.update(new BoardMotionTracker.Frame(W,H,new float[W*H]),CORNERS);double[] p=pose(3,2,0,1);check(t.update(frame(p),CORNERS),p,1.2);}
    @Test public void toleratesPartialOcclusion(){BoardMotionTracker t=start();double[] p=pose(3,-2,.5,1);BoardMotionTracker.Frame f=frame(p);for(int y=0;y<170;y++)for(int x=0;x<150;x++)f.gray[y*W+x]=60;check(t.update(f,CORNERS),p,1.5);}
    @Test public void rejectsSizeChange(){assertNull(start().update(new BoardMotionTracker.Frame(100,100,new float[10000]),CORNERS));}
    @Test public void flatInitialImageCannotCalibrate(){assertNull(new BoardMotionTracker().update(new BoardMotionTracker.Frame(W,H,new float[W*H]),CORNERS));}
    @Test public void lowContrastAndExposureRemainTrackable(){BoardMotionTracker t=new BoardMotionTracker();BoardMotionTracker.Frame a=frame(ID);for(int i=0;i<a.gray.length;i++)a.gray[i]=a.gray[i]*.3f+60;assertNotNull(t.update(a,CORNERS));double[] p=pose(3,-2,.4,1);BoardMotionTracker.Frame b=frame(p);for(int i=0;i<b.gray.length;i++)b.gray[i]=b.gray[i]*.3f+65;check(t.update(b,CORNERS),p,1.5);}
    private BoardMotionTracker.Frame blur(BoardMotionTracker.Frame source,int radius){float[] out=new float[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++){float sum=0;int n=0;for(int oy=-radius;oy<=radius;oy++)for(int ox=-radius;ox<=radius;ox++){sum+=source.gray[Math.max(0,Math.min(H-1,y+oy))*W+Math.max(0,Math.min(W-1,x+ox))];n++;}out[y*W+x]=sum/n;}return new BoardMotionTracker.Frame(W,H,out);}
    @Test public void toleratesMildBlur(){double[] p=pose(2,-2,.4,1);check(start().update(blur(frame(p),1),CORNERS),p,1.5);}
    @Test public void rejectsSevereBlurForRecognition(){BoardMotionTracker.Result result=start().update(blur(frame(ID),7),CORNERS);assertTrue(result==null||!result.readable());}
}
