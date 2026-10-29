package com.opencheps.vision;

import android.graphics.Bitmap;

public class SquareSampler {

    public static final int N = 20;

    public static class Sample {
        public final float[] feature;
        public final float meanR, meanG, meanB;
        public final float meanLum;
        public final float edgeEnergy;
        Sample(float[] feature, float mr, float mg, float mb,
               float meanLum, float edgeEnergy) {
            this.feature = feature;
            this.meanR = mr; this.meanG = mg; this.meanB = mb;
            this.meanLum = meanLum;
            this.edgeEnergy = edgeEnergy;
        }
    }

    public static Sample sample(Bitmap bmp, int cx, int cy, int rad) {
        return sample(bmp,cx,cy,rad,PixelMask.NONE);
    }

    public static Sample sample(Bitmap bmp,int cx,int cy,int rad,PixelMask mask) {return sample(bmp,cx,cy,rad,mask,false);}
    public static Sample sampleCamera(Bitmap bmp,int cx,int cy,int rad,PixelMask mask) {return sample(bmp,cx,cy,rad,mask,true);}
    private static Sample sample(Bitmap bmp,int cx,int cy,int rad,PixelMask mask,boolean camera) {
        int W = bmp.getWidth(), H = bmp.getHeight();
        int size = rad * 2;
        int x0 = Math.max(0, cx - rad);
        int y0 = Math.max(0, cy - rad);
        int x1 = Math.min(W, x0 + size);
        int y1 = Math.min(H, y0 + size);
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) {
            return new Sample(new float[N * N], 0.5f, 0.5f, 0.5f, 0.5f, 0f);
        }

        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, x0, y0, w, h);

        float[] lumGrid = new float[N * N];
        float[] shape = new float[N * N * 2];
        float[] bg=new float[3];
        for(int c=0;c<3;c++) {
            int shift=(2-c)*8;int[] vals=new int[N*N];int n=0;
            for(int y=0;y<N;y++)for(int x=0;x<N;x++) {
                if(x>N*.18f&&x<N*.82f&&y>N*.18f&&y<N*.82f)continue;
                int sx=x*w/N,sy=y*h/N;
                if(mask.excludes(x0+sx,y0+sy))continue;
                vals[n++]=(px[sy*w+sx]>>shift)&255;
            }
            if(n==0)bg[c]=.5f;
            else {java.util.Arrays.sort(vals,0,n);bg[c]=vals[n/2]/255f;}
        }
        float sR = 0, sG = 0, sB = 0;
        int count = N * N;
        for (int y = 0; y < N; y++) {
            int sy = (y * h) / N;
            int rowBase = sy * w;
            for (int x = 0; x < N; x++) {
                int sx = (x * w) / N;
                int p = px[rowBase + sx];
                if(camera){
                    // Max-pool the small sample footprint so a thin, moving outline cannot
                    // disappear between point samples. Keep its actual foreground colour.
                    float strongest=-1;int rx=Math.max(1,w/N/2),ry=Math.max(1,h/N/2);
                    for(int yy=Math.max(0,sy-ry);yy<=Math.min(h-1,sy+ry);yy++)for(int xx=Math.max(0,sx-rx);xx<=Math.min(w-1,sx+rx);xx++){
                        if(mask.excludes(x0+xx,y0+yy))continue;
                        int v=px[yy*w+xx];float distance=Math.max(Math.abs(((v>>16)&255)/255f-bg[0]),Math.max(Math.abs(((v>>8)&255)/255f-bg[1]),Math.abs((v&255)/255f-bg[2])));
                        if(distance>strongest){strongest=distance;p=v;}
                    }
                }
                float rf = ((p >> 16) & 0xFF) / 255f;
                float gf = ((p >> 8)  & 0xFF) / 255f;
                float bf = ( p        & 0xFF) / 255f;
                lumGrid[y * N + x] = 0.299f * rf + 0.587f * gf + 0.114f * bf;
                float contrast=Math.max(Math.abs(rf-bg[0]),Math.max(Math.abs(gf-bg[1]),Math.abs(bf-bg[2])));
                float ink=Math.min(1f,Math.max(0f,(contrast-(camera?.015f:.045f))/.10f));
                float l=lumGrid[y*N+x];
                shape[(y*N+x)*2]=ink*l;
                shape[(y*N+x)*2+1]=ink*(1-l);
                if(mask.excludes(x0+sx,y0+sy)) {
                    shape[(y*N+x)*2]=Float.NaN;shape[(y*N+x)*2+1]=Float.NaN;
                }
                sR += rf; sG += gf; sB += bf;
            }
        }
        float mR = sR / count, mG = sG / count, mB = sB / count;
        float meanLum = 0.299f * mR + 0.587f * mG + 0.114f * mB;

        float edge = 0;
        for (int y = 1; y < N - 1; y++) {
            for (int x = 1; x < N - 1; x++) {
                float l = lumGrid[y * N + x];
                float dx = lumGrid[y * N + (x + 1)] - lumGrid[y * N + (x - 1)];
                float dy = lumGrid[(y + 1) * N + x] - lumGrid[(y - 1) * N + x];
                edge += dx * dx + dy * dy;
            }
        }
        edge /= (N - 2) * (N - 2);

        return new Sample(shape, mR, mG, mB, meanLum, (float) Math.sqrt(edge));
    }

    public static float lumDistance(float[] a, float[] b) {
        float s = 0;
        int n = Math.min(a.length, b.length);
        int valid=0;
        for (int i=0;i<n;i++) {
            if(Float.isNaN(a[i])||Float.isNaN(b[i]))continue;
            float d=a[i]-b[i];s+=d*d;valid++;
        }
        return valid<n*.45f ? Float.POSITIVE_INFINITY : s*n/valid;
    }
}

