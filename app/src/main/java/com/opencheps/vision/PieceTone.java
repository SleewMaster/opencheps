package com.opencheps.vision;

/** Estimate the two foreground-brightness groups in this frame. Board-square
 * color is not used as a proxy for piece color. Ambiguous tones add no penalty. */
public final class PieceTone {
    private final float[] tones,weights;
    private float low,high;
    private boolean separated;
    public PieceTone(float[][] features){
        tones=new float[features.length];weights=new float[features.length];low=1;high=0;int n=0;
        for(int i=0;i<features.length;i++){
            float bright=0,ink=0;for(int j=0;j+1<features[i].length;j+=2){float a=features[i][j],b=features[i][j+1];if(Float.isFinite(a)&&Float.isFinite(b)){bright+=a;ink+=a+b;}}
            if(ink<20)continue;tones[i]=bright/ink;weights[i]=Math.min(150,ink);low=Math.min(low,tones[i]);high=Math.max(high,tones[i]);n++;
        }
        if(n<4)return;
        for(int round=0;round<10;round++){
            float a=0,b=0,wa=0,wb=0,mid=(low+high)*.5f;
            for(int i=0;i<tones.length;i++)if(weights[i]>0){if(tones[i]<mid){a+=tones[i]*weights[i];wa+=weights[i];}else{b+=tones[i]*weights[i];wb+=weights[i];}}
            if(wa==0||wb==0)return;low=a/wa;high=b/wb;
        }
        separated=high-low>=.16f;
    }
    public float penalty(int cell,char piece){
        if(!separated||weights[cell]==0||piece=='.')return 0;
        float mid=(low+high)*.5f,confidence=Math.min(1,Math.abs(tones[cell]-mid)/((high-low)*.5f));
        if(confidence<.4f||(tones[cell]>mid)==Character.isUpperCase(piece))return 0;
        return .035f*confidence;
    }
}
