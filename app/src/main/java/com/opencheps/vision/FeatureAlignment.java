package com.opencheps.vision;

/** Small camera registration errors should not change the piece type.
 * Every comparison uses one bounded transform for both sampling methods.
 * Empty-cell evidence is deliberately not transformed. */
public final class FeatureAlignment {
    private final float[][] pooled,points;
    private final float[] penalties;
    public FeatureAlignment(float[] feature,float[] point){
        pooled=new float[15][];points=point==null?null:new float[15][];penalties=new float[15];
        int i=0;
        for(float scale:new float[]{1,.92f,1.08f})for(float[] shift:new float[][]{{0,0},{-.65f,0},{.65f,0},{0,-.65f},{0,.65f}}){
            pooled[i]=warp(feature,scale,shift[0],shift[1]);
            if(points!=null)points[i]=warp(point,scale,shift[0],shift[1]);
            penalties[i]=(Math.abs(scale-1)*.018f+(Math.abs(shift[0])+Math.abs(shift[1]))*.0015f)*SquareSampler.N*SquareSampler.N;i++;
        }
    }
    public float distance(float[] template,boolean dark){
        float best=Float.POSITIVE_INFINITY;
        for(int i=0;i<pooled.length;i++){
            float d=SquareSampler.lumDistance(pooled[i],template);
            if(dark&&points!=null)d=.25f*d+.75f*SquareSampler.lumDistance(points[i],template);
            best=Math.min(best,d+penalties[i]);
        }
        return best;
    }
    public static float[] warp(float[] source,float scale,float dx,float dy){
        int n=SquareSampler.N;float center=(n-1)*.5f;float[] out=new float[source.length];
        if(source.length!=n*n*2)throw new IllegalArgumentException("Unexpected piece feature size");
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){
            float sx=(x-center-dx)/scale+center,sy=(y-center-dy)/scale+center;
            int ix=(int)Math.floor(sx),iy=(int)Math.floor(sy);float ax=sx-ix,ay=sy-iy;
            for(int c=0;c<2;c++)out[(y*n+x)*2+c]=(at(source,ix,iy,c)*(1-ax)+at(source,ix+1,iy,c)*ax)*(1-ay)+(at(source,ix,iy+1,c)*(1-ax)+at(source,ix+1,iy+1,c)*ax)*ay;
        }return out;
    }
    private static float at(float[] p,int x,int y,int c){int n=SquareSampler.N;return x<0||y<0||x>=n||y>=n?0:p[(y*n+x)*2+c];}
}
