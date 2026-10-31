package com.opencheps.camera;

import com.opencheps.engine.PositionHistory;
import com.opencheps.util.Side;

/** One advice per observed turn. A two-ply inference alone cannot unlock another advice. */
public final class CameraTurnGate {
    private long gameId=-1;
    private Side player;
    private String readyKey="",hintKey="",move;
    private int hintPly;
    private boolean opponentSeen,blocked;
    public void reset(){gameId=-1;player=null;readyKey="";hintKey="";move=null;opponentSeen=false;blocked=false;}
    public boolean observe(PositionHistory.Snapshot current,Side side){
        if(current==null||side==null)return false;
        if(current.gameId!=gameId||side!=player){reset();gameId=current.gameId;player=side;}
        readyKey="";if(blocked)return false;
        boolean yours=current.fen.split(" ")[1].equals(side==Side.WHITE?"w":"b");
        if(!hintKey.isEmpty()&&current.plies>hintPly){
            if(!yours)opponentSeen=true;
            else if(!opponentSeen){blocked=true;return false;}
            else {hintKey="";move=null;opponentSeen=false;}
        }
        if(yours)readyKey=current.key;
        return yours;
    }
    public String choose(PositionHistory.Snapshot current,String candidate){
        if(!permits(current)||MoveAnnouncement.label(current.fen,candidate)==null)return null;
        if(hintKey.equals(current.key))return move;
        hintKey=current.key;hintPly=current.plies;move=candidate;opponentSeen=false;return move;
    }
    public boolean permits(PositionHistory.Snapshot current){return !blocked&&current!=null&&current.gameId==gameId&&readyKey.equals(current.key);}
    public boolean requiresReview(){return blocked;}
}
