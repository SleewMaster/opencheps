package com.opencheps.camera;

import com.opencheps.engine.PositionHistory;
import com.opencheps.util.Side;

/** Pin one advice per turn. Recover a missed intermediate frame from confirmed history
 * plus a fresh observation of the final position; never advance history from advice. */
public final class CameraTurnGate {
    private long gameId=-1;
    private Side player;
    private String readyKey="",hintKey="",move;
    private int hintPly;
    private PositionHistory.Snapshot hinted;
    private String pendingKey="";
    private int confirmations;
    private boolean opponentSeen,blocked;
    public void reset(){gameId=-1;player=null;readyKey="";hintKey="";move=null;hinted=null;pendingKey="";confirmations=0;opponentSeen=false;blocked=false;}
    public boolean observe(PositionHistory.Snapshot current,Side side){
        if(current==null||side==null)return false;
        if(current.gameId!=gameId||side!=player){reset();gameId=current.gameId;player=side;}
        readyKey="";if(blocked)return false;
        boolean yours=current.fen.split(" ")[1].equals(side==Side.WHITE?"w":"b");
        if(!hintKey.isEmpty()&&current.plies>hintPly){
            String prefix=hinted.command+(hintPly==0?" moves ":" ");
            if(!current.command.startsWith(prefix)){blocked=true;return false;}
            if(!yours){opponentSeen=true;pendingKey="";confirmations=0;}
            else {
                if(current.plies-hintPly!=2){blocked=true;return false;}
                if(!opponentSeen){
                    if(current.key.equals(pendingKey))confirmations++;else{pendingKey=current.key;confirmations=1;}
                    if(confirmations<2)return false;
                }
                hintKey="";move=null;hinted=null;opponentSeen=false;pendingKey="";confirmations=0;
            }
        }else if(current.key.equals(hintKey)){
            pendingKey="";confirmations=0;
        }
        if(yours)readyKey=current.key;
        return yours;
    }
    public String choose(PositionHistory.Snapshot current,String candidate){
        if(!permits(current)||MoveAnnouncement.label(current.fen,candidate)==null)return null;
        if(hintKey.equals(current.key))return move;
        hintKey=current.key;hintPly=current.plies;hinted=current;move=candidate;opponentSeen=false;return move;
    }
    public boolean permits(PositionHistory.Snapshot current){return !blocked&&current!=null&&current.gameId==gameId&&readyKey.equals(current.key);}
    public boolean requiresReview(){return blocked;}
    public boolean waitingForConfirmation(){return !blocked&&!pendingKey.isEmpty();}
}
