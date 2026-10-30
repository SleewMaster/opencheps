package com.opencheps.camera;

/** Optical tracking is an optimisation, never the sole way to observe a visible grid. */
public final class CameraGridTracker {
    public static final class Result {
        public final float[] corners;
        public final boolean readable,refitted;
        Result(float[] corners,boolean readable,boolean refitted){this.corners=corners;this.readable=readable;this.refitted=refitted;}
    }
    private BoardMotionTracker motion=new BoardMotionTracker();
    private float[] last;
    private float referenceEnergy;
    public void reset(){motion=new BoardMotionTracker();last=null;referenceEnergy=0;}
    public Result update(BoardMotionTracker.Frame frame,float[] approximate){
        if(last!=null){
            BoardMotionTracker.Result tracked=motion.update(frame,last);
            if(tracked!=null&&BoardGridLocator.matchesGrid(frame,tracked.corners)){
                last=tracked.corners.clone();
                return new Result(last.clone(),tracked.readable()&&sharp(frame,last),false);
            }
        }
        // A monitor's changing scan/moire pattern can invalidate fixed NCC patches.
        // Refit the checker grid in this very frame instead of dropping three frames
        // and repeatedly clearing the position consensus.
        float[] seed=last==null?approximate:last;
        float[] fitted=BoardGridLocator.locate(frame,seed);
        if(fitted==null)fitted=BoardGridLocator.locateExpanded(frame,seed);
        if(fitted==null)fitted=BoardGridLocator.locateAnySize(frame,new float[]{2,2,frame.width-3,2,frame.width-3,frame.height-3,2,frame.height-3});
        if(fitted==null)return null;
        boolean readable=sharp(frame,fitted);
        if(!readable)return new Result(fitted,false,true);
        last=fitted.clone();motion=new BoardMotionTracker();motion.update(frame,fitted);
        // Grid geometry remains usable even when there are too few stable optical
        // features. CameraBoard still requires background visibility and two legal reads.
        return new Result(fitted,true,true);
    }
    private boolean sharp(BoardMotionTracker.Frame frame,float[] corners){
        float value=BoardMotionTracker.gridEnergy(frame,corners);
        if(!Float.isFinite(value)||value<10)return false;
        if(referenceEnergy==0)referenceEnergy=value;
        return value/referenceEnergy>=.40f;
    }
}
