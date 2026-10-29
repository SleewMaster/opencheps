package com.opencheps.camera;

/** Snaps an approximate camera quadrilateral onto the eight-by-eight checker grid.
 * Uses background edges, not piece artwork; pixels and coordinates share the input frame. */
public final class BoardGridLocator {
    private static final int S=320;
    private BoardGridLocator(){}
    public static float[] locate(BoardMotionTracker.Frame frame,float[] approximate){
        if(approximate==null||approximate.length!=8)return null;
        double[] projection=homography(approximate);if(projection==null)return null;
        float[] pixels=new float[S*S];
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){
            double[] p=map(projection,x/(double)S,y/(double)S);pixels[y*S+x]=frame.at(p[0],p[1]);
        }
        BoardMotionTracker.Frame image=new BoardMotionTracker.Frame(S,S,pixels);
        java.util.List<double[]> horizontal=axis(image,false),vertical=axis(image,true);
        float[] q=null;double best=-1;
        for(double[] x:vertical)for(double[] y:horizontal){
            float[] candidate={(float)x[0],(float)y[0],(float)(x[0]+8*x[1]),(float)y[0],(float)(x[0]+8*x[1]),(float)(y[0]+8*y[1]),(float)x[0],(float)(y[0]+8*y[1])};
            if(checkerMatches(image,candidate)<52)continue;
            double value=score(image,candidate);if(value>best){best=value;q=candidate;}
        }
        if(q==null)return null;
        for(double step:new double[]{6,3,1.5,.6,.25})for(int round=0;round<5;round++){
            boolean changed=false;
            for(int coordinate=0;coordinate<8;coordinate++){
                float old=q[coordinate],chosen=old;double local=best;
                for(int direction:new int[]{-1,1}){
                    q[coordinate]=(float)(old+direction*step);
                    if(q[coordinate]<-12||q[coordinate]>S+12)continue;
                    double value=score(image,q);if(value>local+.0001){local=value;chosen=q[coordinate];}
                }
                q[coordinate]=chosen;if(chosen!=old){best=local;changed=true;}
            }if(!changed)break;
        }
        if(best<7||checkerMatches(image,q)<61)return null;
        float[] output=new float[8];
        for(int i=0;i<4;i++){
            double[] p=map(projection,q[i*2]/S,q[i*2+1]/S);output[i*2]=(float)p[0];output[i*2+1]=(float)p[1];
            if(!Double.isFinite(p[0])||!Double.isFinite(p[1])||p[0]<0||p[1]<0||p[0]>=frame.width||p[1]>=frame.height)return null;
        }
        // Refine in original frame coordinates with a narrower edge probe. The coarse
        // rectified search can otherwise stop on a several-pixel-wide score plateau.
        double precise=score(frame,output,.012/8);
        for(double step:new double[]{1,.5,.2})for(int round=0;round<6;round++){
            boolean changed=false;
            for(int coordinate=0;coordinate<8;coordinate++){
                float old=output[coordinate],chosen=old;double local=precise;
                for(int direction:new int[]{-1,1}){
                    output[coordinate]=(float)(old+direction*step);
                    int limit=(coordinate&1)==0?frame.width:frame.height;
                    if(output[coordinate]<1||output[coordinate]>=limit-1)continue;
                    double value=score(frame,output,.012/8);
                    if(value>local+.0001){local=value;chosen=output[coordinate];}
                }
                output[coordinate]=chosen;if(chosen!=old){precise=local;changed=true;}
            }if(!changed)break;
        }
        return checkerMatches(frame,output)>=61?output:null;
    }
    /** Search beyond the previous crop after a translation, retaining its perspective. */
    public static float[] locateExpanded(BoardMotionTracker.Frame frame,float[] previous){
        if(previous==null||previous.length!=8)return null;
        float cx=0,cy=0;for(int i=0;i<4;i++){cx+=previous[2*i]/4;cy+=previous[2*i+1]/4;}
        float[] expanded=new float[8];
        for(int i=0;i<4;i++){
            expanded[2*i]=Math.max(1,Math.min(frame.width-2,cx+(previous[2*i]-cx)*1.32f));
            expanded[2*i+1]=Math.max(1,Math.min(frame.height-2,cy+(previous[2*i+1]-cy)*1.32f));
        }
        return locate(frame,expanded);
    }
    private static java.util.List<double[]> axis(BoardMotionTracker.Frame image,boolean vertical){
        double[] response=new double[S];
        for(int i=4;i<S-4;i++){
            double total=0;int count=0;
            int from=vertical?88:32,to=vertical?232:288;
            for(int j=from;j<to;j+=2){
                double a=0,b=0;for(int d=1;d<=3;d++){
                    a+=vertical?image.at(i-d,j):image.at(j,i-d);b+=vertical?image.at(i+d,j):image.at(j,i+d);
                }total+=Math.min(70,Math.abs(a-b)/3);count++;
            }response[i]=total/count;
        }
        java.util.List<double[]> candidates=new java.util.ArrayList<>();
        for(double spacing=23;spacing<=44;spacing+=.25)for(double start=0;start<=112;start+=.5){
            if(start+8*spacing>S+1)continue;
            double sum=0;for(int i=1;i<8;i++){
                int at=(int)Math.round(start+i*spacing);double value=0;
                for(int d=-1;d<=1;d++)if(at+d>=0&&at+d<S)value=Math.max(value,response[at+d]);sum+=value;
            }
            // Prefer a whole visible board over shifting the same periodic grid by one cell.
            sum-=Math.abs(start)*.015+Math.abs(start+8*spacing-S)*.015;
            if(sum/7>=5)candidates.add(new double[]{start,spacing,sum});
        }
        candidates.sort((a,b)->Double.compare(b[2],a[2]));
        java.util.List<double[]> peaks=new java.util.ArrayList<>();
        for(double[] candidate:candidates){
            boolean close=false;for(double[] p:peaks)if(Math.abs(p[0]-candidate[0])<8&&Math.abs(p[1]-candidate[1])<2){close=true;break;}
            if(!close)peaks.add(candidate);if(peaks.size()==12)break;
        }return peaks;
    }
    private static double score(BoardMotionTracker.Frame image,float[] q){return score(image,q,.035/8);}
    private static double score(BoardMotionTracker.Frame image,float[] q,double delta){
        double[] h=homography(q);if(h==null)return -100;
        double total=0;int count=0;
        for(int edge=1;edge<8;edge++)for(int square=0;square<8;square++)for(double offset:new double[]{.13,.87}){
            double v=(square+offset)/8.,u=edge/8.;
            double[] a=map(h,u-delta,v),b=map(h,u+delta,v),c=map(h,v,u-delta),d=map(h,v,u+delta);
            double sign=((edge+square)&1)==0?1:-1;
            total+=clamp(sign*(image.at(b[0],b[1])-image.at(a[0],a[1])));
            total+=clamp(sign*(image.at(d[0],d[1])-image.at(c[0],c[1])));count+=2;
        }
        return Math.abs(total/count);
    }
    private static double clamp(double value){return Math.max(-45,Math.min(45,value));}
    private static int checkerMatches(BoardMotionTracker.Frame image,float[] q){
        double[] h=homography(q);if(h==null)return 0;
        double[] average=new double[2];int[] n=new int[2];double[][] values=new double[8][8];
        for(int r=0;r<8;r++)for(int f=0;f<8;f++){
            double sum=0;for(double dy:new double[]{.12,.88})for(double dx:new double[]{.12,.88}){double[] p=map(h,(f+dx)/8,(r+dy)/8);sum+=image.at(p[0],p[1]);}
            double v=sum/4;values[r][f]=v;int parity=(r+f)&1;average[parity]+=v;n[parity]++;
        }
        double a=average[0]/n[0],b=average[1]/n[1];if(Math.abs(a-b)<9)return 0;
        int matches=0;double mid=(a+b)/2;
        for(int r=0;r<8;r++)for(int f=0;f<8;f++)if((values[r][f]>mid)==((((r+f)&1)==0)?a>b:b>a))matches++;
        return matches;
    }
    static double[] homography(float[] p){
        double dx1=p[2]-p[4],dx2=p[6]-p[4],dx3=p[0]-p[2]+p[4]-p[6];
        double dy1=p[3]-p[5],dy2=p[7]-p[5],dy3=p[1]-p[3]+p[5]-p[7];
        double denominator=dx1*dy2-dx2*dy1;if(Math.abs(denominator)<1e-8)return null;
        double g=(dx3*dy2-dx2*dy3)/denominator,h=(dx1*dy3-dx3*dy1)/denominator;
        return new double[]{p[2]-p[0]+g*p[2],p[6]-p[0]+h*p[6],p[0],p[3]-p[1]+g*p[3],p[7]-p[1]+h*p[7],p[1],g,h};
    }
    static double[] map(double[] h,double x,double y){double d=1+h[6]*x+h[7]*y;return new double[]{(h[0]*x+h[1]*y+h[2])/d,(h[3]*x+h[4]*y+h[5])/d};}
}
