package com.opencheps.vision;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

/** Find the actual flat-colour board footprint, then verify all 64 squares.
 * Unlike a checker contrast maximum this cannot slide a rank into the UI. */
public class BoardDetector {
    public static class Board {
        public final int left, top, side;
        public final float square;
        public final double confidence;
        public Board(int left, int top, int side, double confidence) {
            this.left=left; this.top=top; this.side=side;
            this.square=side/8f; this.confidence=confidence;
        }
    }
    public static Board locate(Bitmap src) {
        if (src==null) return null;
        float scale=Math.min(1f,800f/Math.max(src.getWidth(),src.getHeight()));
        int w=Math.round(src.getWidth()*scale), h=Math.round(src.getHeight()*scale);
        Bitmap small=scale<1 ? Bitmap.createScaledBitmap(src,w,h,true) : src;
        int[] px=new int[w*h]; small.getPixels(px,0,w,0,0,w,h);
        if(small!=src) small.recycle();
        int[] hist=new int[32768];
        for(int p:px) hist[(((p>>19)&31)<<10)|(((p>>11)&31)<<5)|((p>>3)&31)]++;
        List<Integer> colors=new ArrayList<>();
        for(int k=0;k<24 && colors.size()<10;k++) {
            int best=0;
            for(int i=1;i<hist.length;i++) if(hist[i]>hist[best]) best=i;
            if(hist[best]<px.length/250) break;
            int color=0xff000000|(((best>>10)*8+4)<<16)|((((best>>5)&31)*8+4)<<8)|((best&31)*8+4);
            hist[best]=0;
            boolean distinct=true;
            for(int c:colors) if(distance(c,color)<22*22) distinct=false;
            if(distinct) colors.add(color);
        }
        Board winner=null; double bestScore=0;
        for(int a=0;a<colors.size();a++) for(int b=a+1;b<colors.size();b++) {
            int ca=colors.get(a), cb=colors.get(b);
            if(distance(ca,cb)<30*30) continue;
            int tolerance=Math.min(28*28,Math.max(9*9,distance(ca,cb)/5));
            int[] rows=new int[h]; byte[] mask=new byte[w*h];
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int p=px[y*w+x];
                int da=distance(p,ca), db=distance(p,cb);
                if(Math.min(da,db)<tolerance) { mask[y*w+x]=(byte)(da<db?1:2); rows[y]++; }
            }
            // Each real board row contains background even with pieces/highlights.
            int y=0;
            while(y<h) {
                while(y<h && rows[y]<w*.22) y++;
                int top=y;
                int lastGood=y;
                while(y<h) {
                    if(rows[y]>=w*.22) lastGood=y;
                    else if(y-lastGood>4) break;
                    y++;
                }
                int bottom=lastGood+1, height=bottom-top;
                if(height<w*.30) continue;
                int[] cols=new int[w];
                for(int yy=top;yy<bottom;yy++) for(int x=0;x<w;x++) if(mask[yy*w+x]!=0) cols[x]++;
                int left=0,right=w;
                while(left<w && cols[left]<height*.24) left++;
                while(right>left && cols[right-1]<height*.24) right--;
                int width=right-left;
                if(width<w*.30 || Math.abs(width-height)>Math.max(width,height)*.065) continue;
                int side=Math.round((width+height)/2f);
                if(left+side>w+2 || top+side>h+2) continue;
                side=Math.min(side,Math.min(w-left,h-top));
                // Robust corner vote, excluding piece centres and coordinates.
                double matches=0, reverse=0; int total=0;
                float[] offsets={.10f,.22f,.78f,.90f};
                for(int r=0;r<8;r++) for(int f=0;f<8;f++) {
                    int one=0,two=0;
                    for(float oy:offsets) for(float ox:offsets) {
                        int xx=Math.min(w-1,Math.round(left+(f+ox)*side/8f));
                        int yy=Math.min(h-1,Math.round(top+(r+oy)*side/8f));
                        byte m=mask[yy*w+xx]; if(m==1) one++; if(m==2) two++;
                    }
                    if(((r+f)&1)==0) {matches+=one;reverse+=two;} else {matches+=two;reverse+=one;}
                    total+=16;
                }
                double ratio=Math.max(matches,reverse)/total;
                if(ratio<.62) continue;
                double score=ratio*ratio*side;
                if(score>bestScore) {
                    bestScore=score;
                    winner=new Board(Math.round(left/scale),Math.round(top/scale),Math.round(side/scale),ratio);
                }
            }
        }
        return winner;
    }
    private static int distance(int a,int b) {
        int r=((a>>16)&255)-((b>>16)&255),g=((a>>8)&255)-((b>>8)&255),bl=(a&255)-(b&255);
        return r*r+g*g+bl*bl;
    }
}
