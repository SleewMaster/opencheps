package com.opencheps.camera;

/** A position must occur in consecutive camera frames before it is used. */
public final class FrameConsensus {
    private String candidate;
    private int repeats;
    public boolean accept(String key) {
        if(key==null){candidate=null;repeats=0;return false;}
        if(key.equals(candidate))repeats++;else{candidate=key;repeats=1;}
        return repeats>=2;
    }
}
