package com.opencheps.camera;

import java.util.Arrays;

/** Express camera errors in units of the calibrated visible piece signal. */
public final class CameraConfidence {
    private CameraConfidence() {}
    public static float scale(float[][] startingCosts) {
        if(startingCosts==null||startingCosts.length!=64)return 1;
        float[] signal=new float[32];int n=0;
        for(int r:new int[]{0,1,6,7})for(int f=0;f<8;f++) {
            float value=startingCosts[r*8+f][0];
            if(!Float.isFinite(value)||value<=0)return 1;
            signal[n++]=value;
        }
        Arrays.sort(signal);
        // Lower quartile represents the weaker piece colour while resisting one bad crop.
        // Scaling only upwards also tightens the existing absolute error caps for faint pieces.
        return Math.min(10f,Math.max(1f,.10f/Math.max(.005f,signal[8])));
    }
}
