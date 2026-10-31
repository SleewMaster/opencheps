package com.opencheps.vision;

/** Collect a review proposal, not an automatically accepted chess position.
 * Small camera disagreements become visible uncertainty instead of restarting
 * the entire 64-square scan forever. The current frame supplies both labels
 * and learning descriptors; labels from different frames are never spliced. */
public final class DiagramConsensus {
    public static final int MAX_FRAMES=5;
    private char[][] previous;
    private final boolean[] changed=new boolean[64];
    private int frames;
    private int gaps;
    public void reset(){previous=null;frames=0;gaps=0;java.util.Arrays.fill(changed,false);}
    public int frames(){return frames;}
    public boolean accept(DiagramReader.Result current){
        if(current==null){if(++gaps>1)reset();return false;}
        gaps=0;
        int differences=0;
        if(previous!=null)for(int i=0;i<64;i++)if(previous[i/8][i%8]!=current.screen[i/8][i%8]){
            changed[i]=true;differences++;
        }
        frames++;
        previous=ChessPosition.copy(current.screen);
        for(int i=0;i<64;i++)current.uncertain[i]|=changed[i];
        return frames>=2&&(differences<=4||frames>=MAX_FRAMES);
    }
}
