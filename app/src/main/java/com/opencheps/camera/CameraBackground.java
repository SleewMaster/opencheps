package com.opencheps.camera;

import java.util.Arrays;

/** Checks visibility of cell background corners, independently of piece recognition. */
public final class CameraBackground {
    private final float[][] reference;
    public CameraBackground(int[] pixels,int width,int height){reference=sample(pixels,width,height);}
    public boolean visible(int[] pixels,int width,int height){
        float[][] current=sample(pixels,width,height);float[] offset=new float[3];
        for(int channel=0;channel<3;channel++){
            float[] differences=new float[256];for(int i=0;i<256;i++)differences[i]=current[i][channel]-reference[i][channel];
            Arrays.sort(differences);offset[channel]=(differences[127]+differences[128])/2;
        }
        int badCells=0;
        for(int cell=0;cell<64;cell++){
            int badCorners=0;
            for(int corner=0;corner<4;corner++){
                int i=cell*4+corner;float error=0;
                for(int c=0;c<3;c++)error=Math.max(error,Math.abs(current[i][c]-reference[i][c]-offset[c]));
                if(error>42)badCorners++;
            }
            // Two last-move highlight squares are allowed; a larger covered region is not.
            if(badCorners>=2&&++badCells>2)return false;
        }return true;
    }
    private static float[][] sample(int[] pixels,int width,int height){
        float[][] values=new float[256][3];int index=0;
        for(int r=0;r<8;r++)for(int f=0;f<8;f++)for(float y:new float[]{.13f,.87f})for(float x:new float[]{.13f,.87f}){
            int cx=Math.round((f+x)*width/8f),cy=Math.round((r+y)*height/8f),n=0;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                int px=pixels[Math.max(0,Math.min(height-1,cy+dy))*width+Math.max(0,Math.min(width-1,cx+dx))];
                values[index][0]+=(px>>16)&255;values[index][1]+=(px>>8)&255;values[index][2]+=px&255;n++;
            }for(int c=0;c<3;c++)values[index][c]/=n;index++;
        }return values;
    }
}
