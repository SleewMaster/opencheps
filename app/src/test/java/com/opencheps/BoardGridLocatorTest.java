package com.opencheps;
import com.opencheps.camera.*;import org.junit.Test;import static org.junit.Assert.*;
public class BoardGridLocatorTest {
    private static final int W=480,H=520;
    private static final float[] TRUE={80,50,430,60,420,430,60,420},ROUGH={50,40,435,60,425,470,40,465};
    private BoardMotionTracker.Frame scene(boolean inverted,boolean noise,int missing){
        // Inverse projective transform, computed from four correspondences.
        double[][] a=new double[8][9];double[][] unit={{0,0},{1,0},{1,1},{0,1}};
        for(int i=0;i<4;i++){double x=TRUE[i*2],y=TRUE[i*2+1],u=unit[i][0],v=unit[i][1];a[2*i]=new double[]{x,y,1,0,0,0,-u*x,-u*y,u};a[2*i+1]=new double[]{0,0,0,x,y,1,-v*x,-v*y,v};}
        for(int c=0;c<8;c++){int p=c;for(int r=c+1;r<8;r++)if(Math.abs(a[r][c])>Math.abs(a[p][c]))p=r;double[] swap=a[c];a[c]=a[p];a[p]=swap;double factor=a[c][c];for(int j=c;j<9;j++)a[c][j]/=factor;for(int r=0;r<8;r++)if(r!=c){factor=a[r][c];for(int j=c;j<9;j++)a[r][j]-=factor*a[c][j];}}
        float[] pixels=new float[W*H];java.util.Random random=new java.util.Random(5);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            double d=1+a[6][8]*x+a[7][8]*y,u=(a[0][8]*x+a[1][8]*y+a[2][8])/d,v=(a[3][8]*x+a[4][8]*y+a[5][8])/d;float value=25;
            if(u>=0&&u<1&&v>=0&&v<1){int f=(int)(u*8),r=(int)(v*8);value=(((r+f)&1)==0)^inverted?220:100;
                if((r<2||r>5)&&Math.hypot(u*8-f-.5,v*8-r-.5)<.22)value=r<2?40:250;
                if(f>=8-missing)value=25;
            }pixels[y*W+x]=value+(noise?random.nextInt(9)-4:0);
        }return new BoardMotionTracker.Frame(W,H,pixels);
    }
    private void check(float[] result){assertNotNull(result);for(int i=0;i<4;i++)assertTrue(Math.hypot(result[i*2]-TRUE[i*2],result[i*2+1]-TRUE[i*2+1])<3);}
    @Test public void snapsMarginsAndTilt(){check(BoardGridLocator.locate(scene(false,false,0),ROUGH));}
    @Test public void retainsPreciseCorners(){check(BoardGridLocator.locate(scene(false,false,0),TRUE));}
    @Test public void findsBoardInWholePreview(){check(BoardGridLocator.locate(scene(false,false,0),new float[]{2,2,W-3,2,W-3,H-3,2,H-3}));}
    @Test public void invertedBoardColours(){check(BoardGridLocator.locate(scene(true,false,0),ROUGH));}
    @Test public void cameraNoise(){check(BoardGridLocator.locate(scene(false,true,0),ROUGH));}
    @Test public void missingFileIsNotEightByEight(){assertNull(BoardGridLocator.locate(scene(false,false,1),ROUGH));}
    @Test public void uniformFrameRejected(){assertNull(BoardGridLocator.locate(new BoardMotionTracker.Frame(W,H,new float[W*H]),ROUGH));}
    @Test public void stripesRejected(){float[] p=new float[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++)p[y*W+x]=((x/42)&1)==0?220:100;assertNull(BoardGridLocator.locate(new BoardMotionTracker.Frame(W,H,p),ROUGH));}
    @Test public void recoverySearchIncludesBoardOutsideOldCrop(){float[] shifted=TRUE.clone();for(int i=0;i<4;i++){shifted[2*i]-=28;shifted[2*i+1]-=20;}check(BoardGridLocator.locateExpanded(scene(false,true,0),shifted));}
}
