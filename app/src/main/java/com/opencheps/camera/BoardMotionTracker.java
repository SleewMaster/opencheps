package com.opencheps.camera;

import java.util.*;

/** Fixed-reference, robust projective registration of board grid intersections.
 * All coordinates are in the input frame; no Android dependency. */
public final class BoardMotionTracker {
    public static final class Frame {
        public final int width,height;
        public final float[] gray;
        public Frame(int w,int h,float[] pixels){width=w;height=h;gray=pixels;}
        public float at(double x,double y){
            if(x<0||y<0||x>=width-1||y>=height-1)return 0;
            int ix=(int)x,iy=(int)y;float a=(float)(x-ix),b=(float)(y-iy);
            int i=iy*width+ix;
            return (gray[i]*(1-a)+gray[i+1]*a)*(1-b)+(gray[i+width]*(1-a)+gray[i+width+1]*a)*b;
        }
        public static Frame fromArgb(int[] pixels,int w,int h,int maxSide){
            double scale=Math.min(1,Math.max(1,maxSide)/(double)Math.max(w,h));
            int sw=Math.max(1,(int)Math.round(w*scale)),sh=Math.max(1,(int)Math.round(h*scale));
            float[] g=new float[sw*sh];
            for(int y=0;y<sh;y++)for(int x=0;x<sw;x++){
                double fx=(x+.5)*w/sw-.5,fy=(y+.5)*h/sh-.5;int ix=Math.max(0,Math.min(w-2,(int)fx)),iy=Math.max(0,Math.min(h-2,(int)fy));
                float a=(float)Math.max(0,Math.min(1,fx-ix)),b=(float)Math.max(0,Math.min(1,fy-iy));int i=iy*w+ix;
                g[y*sw+x]=(luma(pixels[i])*(1-a)+luma(pixels[i+1])*a)*(1-b)+(luma(pixels[i+w])*(1-a)+luma(pixels[i+w+1])*a)*b;
            }return new Frame(sw,sh,g);
        }
        private static float luma(int p){return .299f*((p>>16)&255)+.587f*((p>>8)&255)+.114f*(p&255);}
    }
    public static final class Result {
        public final float[] corners;
        public final int inliers;
        public final float error,sharpness;
        Result(float[] p,int n,float e,float sharpness){corners=p;inliers=n;error=e;this.sharpness=sharpness;}
        public boolean readable(){return sharpness>=.40f;}
    }
    private static final int R=4,N=81;
    private static final double[] IDENTITY={1,0,0,0,1,0,0,0};
    private final List<Feature> features=new ArrayList<>();
    private int width,height;
    private float[] initial;
    private double[] prediction=IDENTITY.clone();
    private double radius;
    private static final class Feature {
        double x,y,energy;float[] patch;int quadrant;
    }
    private static final class Match {
        Feature source;double x,y,sharpness;
        Match(Feature f,double x,double y,double sharpness){source=f;this.x=x;this.y=y;this.sharpness=sharpness;}
    }
    public Result update(Frame frame,float[] corners){
        if(initial==null)return initialize(frame,corners);
        if(frame.width!=width||frame.height!=height)return null;
        List<Match> matches=new ArrayList<>();
        for(Feature f:features){
            double[] expected=map(prediction,f.x/width,f.y/height);
            double px=expected[0]*width,py=expected[1]*height;
            if(!inside(frame,px,py))continue;
            int cx=(int)Math.round(px),cy=(int)Math.round(py),range=(int)radius;
            double best=-2;int bx=cx,by=cy;
            for(int y=cy-range;y<=cy+range;y+=2)for(int x=cx-range;x<=cx+range;x+=2){
                double score=correlation(f,frame,x,y);if(score>best){best=score;bx=x;by=y;}
            }
            int ax=bx,ay=by;
            for(int y=ay-2;y<=ay+2;y++)for(int x=ax-2;x<=ax+2;x++){
                double score=correlation(f,frame,x,y);if(score>best){best=score;bx=x;by=y;}
            }
            if(best<.80)continue;
            double dx=peak(correlation(f,frame,bx-1,by),best,correlation(f,frame,bx+1,by));
            double dy=peak(correlation(f,frame,bx,by-1),best,correlation(f,frame,bx,by+1));
            double energy=energy(frame,bx+dx,by+dy);
            matches.add(new Match(f,bx+dx,by+dy,energy/Math.max(.0001,f.energy)));
        }
        if(matches.size()<16)return null;
        Random random=new Random(711);double[] bestModel=null;List<Match> bestInliers=Collections.emptyList();double bestError=Double.MAX_VALUE;
        for(int round=0;round<100;round++){
            List<Match> sample=new ArrayList<>();
            for(int attempts=0;sample.size()<4&&attempts<40;attempts++){Match m=matches.get(random.nextInt(matches.size()));if(!sample.contains(m))sample.add(m);}
            if(sample.size()!=4)continue;
            double[] model=fit(sample);if(model==null)continue;
            List<Match> inliers=new ArrayList<>();double error=0;
            for(Match m:matches){double e=error(model,m);if(e<1.8*1.8){inliers.add(m);error+=e;}}
            if(inliers.size()>bestInliers.size()||(inliers.size()==bestInliers.size()&&error<bestError)){bestModel=model;bestInliers=inliers;bestError=error;}
        }
        if(bestModel==null||bestInliers.size()<16||bestInliers.size()<features.size()*.45)return null;
        int quadrants=0;for(Match m:bestInliers)quadrants|=1<<m.source.quadrant;
        if(Integer.bitCount(quadrants)<3)return null;
        double[] refined=fit(bestInliers);if(refined==null)return null;
        float[] tracked=new float[8];double error=0;
        for(Match m:bestInliers)error+=error(refined,m);
        if(Math.sqrt(error/bestInliers.size())>1.3)return null;
        for(int i=0;i<4;i++){
            double[] p=map(refined,initial[i*2]/width,initial[i*2+1]/height),old=map(prediction,initial[i*2]/width,initial[i*2+1]/height);
            tracked[i*2]=(float)(p[0]*width);tracked[i*2+1]=(float)(p[1]*height);
            if(!Double.isFinite(p[0])||!Double.isFinite(p[1])||Math.hypot((p[0]-old[0])*width,(p[1]-old[1])*height)>radius*3.5)return null;
        }
        if(!valid(tracked))return null;
        double[] sharpness=new double[bestInliers.size()];for(int i=0;i<sharpness.length;i++)sharpness[i]=bestInliers.get(i).sharpness;
        Arrays.sort(sharpness);prediction=refined;
        return new Result(tracked,bestInliers.size(),(float)Math.sqrt(error/bestInliers.size()),(float)sharpness[sharpness.length/2]);
    }
    private Result initialize(Frame frame,float[] corners){
        if(corners==null||corners.length!=8)return null;
        width=frame.width;height=frame.height;
        // Fit unit board coordinates to frame coordinates once; subsequent fits always
        // refer back to this frame, avoiding drift from frame-to-frame accumulation.
        List<Match> control=new ArrayList<>();
        double[][] unit={{0,0},{1,0},{1,1},{0,1}};
        for(int i=0;i<4;i++){Feature f=new Feature();f.x=unit[i][0]*width;f.y=unit[i][1]*height;control.add(new Match(f,corners[2*i],corners[2*i+1],1));}
        double[] grid=fit(control);if(grid==null)return null;
        radius=Math.max(4,Math.min(16,Math.min(distance(corners,0,1),distance(corners,0,3))/8*.32));
        features.clear();
        for(int r=1;r<8;r++)for(int f=1;f<8;f++){
            double[] p=map(grid,f/8.,r/8.);int cx=(int)Math.round(p[0]*width),cy=(int)Math.round(p[1]*height);
            double score=-1;int bx=cx,by=cy;
            for(int y=cy-3;y<=cy+3;y++)for(int x=cx-3;x<=cx+3;x++){
                if(!inside(frame,x,y))continue;
                double value=cornerScore(frame,x,y);if(value>score){score=value;bx=x;by=y;}
            }
            if(score<8)continue;
            Feature feature=new Feature();feature.x=bx;feature.y=by;feature.quadrant=(r>=4?2:0)+(f>=4?1:0);feature.energy=energy(frame,bx,by);
            feature.patch=new float[N];double sum=0,square=0;int k=0;
            for(int y=-R;y<=R;y++)for(int x=-R;x<=R;x++){float value=frame.at(bx+x,by+y);feature.patch[k++]=value;sum+=value;square+=value*value;}
            double norm=Math.sqrt(Math.max(0,square-sum*sum/N));if(norm<20)continue;
            for(int i=0;i<N;i++)feature.patch[i]=(float)((feature.patch[i]-sum/N)/norm);
            boolean duplicate=false;for(Feature previous:features)if(Math.hypot(previous.x-bx,previous.y-by)<6)duplicate=true;
            if(!duplicate)features.add(feature);
        }
        if(features.size()<24){features.clear();return null;}
        initial=corners.clone();prediction=IDENTITY.clone();return new Result(corners.clone(),features.size(),0,1);
    }
    private boolean valid(float[] p){
        for(int i=0;i<4;i++){
            int a=i*2,b=((i+1)%4)*2,c=((i+2)%4)*2;
            if(p[a]<0||p[a+1]<0||p[a]>=width||p[a+1]>=height)return false;
            if((p[b]-p[a])*(p[c+1]-p[b+1])-(p[b+1]-p[a+1])*(p[c]-p[b])<=0)return false;
            double ratio=distance(p,i,(i+1)%4)/distance(initial,i,(i+1)%4);if(ratio<.65||ratio>1.5)return false;
        }return true;
    }
    private static double distance(float[] p,int a,int b){return Math.hypot(p[a*2]-p[b*2],p[a*2+1]-p[b*2+1]);}
    private static boolean inside(Frame frame,double x,double y){return x>=R+2&&y>=R+2&&x<frame.width-R-3&&y<frame.height-R-3;}
    private static double peak(double left,double center,double right){double d=left-2*center+right;return Math.abs(d)<1e-8?0:Math.max(-.75,Math.min(.75,.5*(left-right)/d));}
    private static double correlation(Feature f,Frame frame,int cx,int cy){
        if(!inside(frame,cx,cy))return -2;
        double sum=0,square=0,dot=0;int k=0;
        for(int y=-R;y<=R;y++)for(int x=-R;x<=R;x++){float v=frame.gray[(cy+y)*frame.width+cx+x];sum+=v;square+=v*v;dot+=f.patch[k++]*v;}
        double norm=Math.sqrt(Math.max(0,square-sum*sum/N));return norm<15?-2:dot/norm;
    }
    private static double energy(Frame frame,double cx,double cy){
        double value=0;
        for(int y=-R+1;y<R;y++)for(int x=-R+1;x<R;x++){
            double dx=frame.at(cx+x+1,cy+y)-frame.at(cx+x-1,cy+y),dy=frame.at(cx+x,cy+y+1)-frame.at(cx+x,cy+y-1);value+=dx*dx+dy*dy;
        }return value/49;
    }
    private static double cornerScore(Frame frame,int cx,int cy){
        double xx=0,yy=0,xy=0;
        for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++){
            double dx=frame.at(cx+x+1,cy+y)-frame.at(cx+x-1,cy+y),dy=frame.at(cx+x,cy+y+1)-frame.at(cx+x,cy+y-1);xx+=dx*dx;yy+=dy*dy;xy+=dx*dy;
        }return (xx+yy-Math.sqrt((xx-yy)*(xx-yy)+4*xy*xy))/50;
    }
    private double error(double[] model,Match m){double[] p=map(model,m.source.x/width,m.source.y/height);double dx=p[0]*width-m.x,dy=p[1]*height-m.y;return dx*dx+dy*dy;}
    private double[] fit(List<Match> matches){
        double[][] normal=new double[8][9];
        for(Match m:matches){double x=m.source.x/width,y=m.source.y/height,u=m.x/width,v=m.y/height;
            accumulate(normal,new double[]{x,y,1,0,0,0,-u*x,-u*y},u);accumulate(normal,new double[]{0,0,0,x,y,1,-v*x,-v*y},v);
        }
        for(int col=0;col<8;col++){
            int pivot=col;for(int r=col+1;r<8;r++)if(Math.abs(normal[r][col])>Math.abs(normal[pivot][col]))pivot=r;
            if(Math.abs(normal[pivot][col])<1e-11)return null;
            double[] swap=normal[col];normal[col]=normal[pivot];normal[pivot]=swap;
            double factor=normal[col][col];for(int c=col;c<=8;c++)normal[col][c]/=factor;
            for(int r=0;r<8;r++)if(r!=col){factor=normal[r][col];for(int c=col;c<=8;c++)normal[r][c]-=factor*normal[col][c];}
        }
        double[] result=new double[8];for(int i=0;i<8;i++)result[i]=normal[i][8];return result;
    }
    private static void accumulate(double[][] normal,double[] row,double value){for(int r=0;r<8;r++){for(int c=0;c<8;c++)normal[r][c]+=row[r]*row[c];normal[r][8]+=row[r]*value;}}
    private static double[] map(double[] m,double x,double y){double d=1+m[6]*x+m[7]*y;return new double[]{(m[0]*x+m[1]*y+m[2])/d,(m[3]*x+m[4]*y+m[5])/d};}
}
