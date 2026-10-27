package com.opencheps.camera;

/** TL, TR, BR, BL corners in displayed preview coordinates; no Android dependency. */
public final class CameraGeometry {
    private CameraGeometry() {}
    public static boolean valid(float[] p, int width, int height) {
        if(p==null || p.length!=8 || width<1 || height<1)return false;
        float area=0;
        for(int i=0;i<4;i++) {
            int a=i*2,b=((i+1)%4)*2,c=((i+2)%4)*2;
            if(!Float.isFinite(p[a])||!Float.isFinite(p[a+1]) || p[a]<0||p[a+1]<0
                    ||p[a]>width||p[a+1]>height)return false;
            float dx=p[b]-p[a],dy=p[b+1]-p[a+1];
            if(dx*dx+dy*dy<80*80)return false;
            if(dx*(p[c+1]-p[b+1])-dy*(p[c]-p[b])<=0)return false;
            area+=p[a]*p[b+1]-p[b]*p[a+1];
        }
        return area>20000;
    }
}
