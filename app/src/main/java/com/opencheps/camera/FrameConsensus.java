package com.opencheps.camera;

/** Two matching clear observations; one dropped frame may lie between them. */
public final class FrameConsensus {
    private String candidate;
    private int repeats;
    private int gaps;
    public boolean accept(String key) {
        return accept(key,2);
    }
    public boolean accept(String key,int required) {
        if(key==null){if(++gaps>1){candidate=null;repeats=0;}return false;}
        gaps=0;
        if(key.equals(candidate))repeats++;else{candidate=key;repeats=1;}
        return repeats>=Math.max(2,required);
    }
}
