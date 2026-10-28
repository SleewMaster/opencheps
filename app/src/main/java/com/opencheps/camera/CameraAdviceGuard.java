package com.opencheps.camera;

/** A brief soft failure may retain a verified arrow, never a detected move or lost geometry. */
public final class CameraAdviceGuard {
    public static final long MAX_HOLD_MS=450;
    private long lastVerified;
    private boolean seen;
    private int misses;
    public void verified(long now){lastVerified=now;seen=true;misses=0;}
    public void reset(){seen=false;misses=0;}
    public boolean failure(long now,boolean tracked,boolean readable,boolean pendingMove){
        misses++;return seen&&tracked&&readable&&!pendingMove&&misses==1&&now>=lastVerified&&remaining(now)>0;
    }
    public long remaining(long now){return seen?Math.max(0,MAX_HOLD_MS-(now-lastVerified)):0;}
}
