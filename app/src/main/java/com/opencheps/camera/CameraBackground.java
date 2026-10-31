package com.opencheps.camera;

import java.util.Arrays;

/** Checks visibility of cell background corners, independently of piece recognition. */
public final class CameraBackground {
    private final float[][] reference;
    private final float[][] observed;
    public CameraBackground(int[] pixels,int width,int height){
        reference=sample(pixels,width,height);
        observed=new float[reference.length][];for(int i=0;i<reference.length;i++)observed[i]=reference[i].clone();
        // Joining a running game captures last-move/check highlights too. They are
        // transient paint, not the board background: otherwise their disappearance
        // plus the next two highlights looks like four or more covered cells.
        float[][] plain=new float[2][3];
        for(int parity=0;parity<2;parity++)for(int channel=0;channel<3;channel++){
            float[] values=new float[128];int n=0;
            for(int cell=0;cell<64;cell++)if(((cell/8+cell%8)&1)==parity)
                for(int corner=0;corner<4;corner++)values[n++]=reference[cell*4+corner][channel];
            Arrays.sort(values);plain[parity][channel]=(values[63]+values[64])/2;
        }
        for(int cell=0;cell<64;cell++){
            float[] color=plain[(cell/8+cell%8)&1];int outliers=0;
            for(int corner=0;corner<4;corner++)if(error(reference[cell*4+corner],color)>42)outliers++;
            if(outliers>=2)for(int corner=0;corner<4;corner++)reference[cell*4+corner]=color.clone();
        }
    }
    private static float error(float[] a,float[] b){float e=0;for(int c=0;c<3;c++)e=Math.max(e,Math.abs(a[c]-b[c]));return e;}
    public boolean visible(int[] pixels,int width,int height){
        float[][] current=sample(pixels,width,height);float[] offset=new float[3];
        for(int channel=0;channel<3;channel++){
            float[] differences=new float[256];for(int i=0;i<256;i++)differences[i]=current[i][channel]-observed[i][channel];
            Arrays.sort(differences);offset[channel]=(differences[127]+differences[128])/2;
        }
        int badCells=0;
        for(int cell=0;cell<64;cell++){
            int badCorners=0;
            for(int corner=0;corner<4;corner++){
                int i=cell*4+corner;float plainError=0,observedError=0;
                for(int c=0;c<3;c++){
                    plainError=Math.max(plainError,Math.abs(current[i][c]-reference[i][c]-offset[c]));
                    observedError=Math.max(observedError,Math.abs(current[i][c]-observed[i][c]-offset[c]));
                }
                // The original appearance must verify itself, even when several
                // cells contain highlights or a stable monitor lighting gradient.
                // Also allow the estimated plain color when a highlight disappears.
                if(Math.min(plainError,observedError)>42)badCorners++;
            }
            // Two last-move squares plus a checked king may legitimately change colour.
            if(badCorners>=2&&++badCells>3)return false;
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
