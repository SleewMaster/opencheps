package com.opencheps.camera;

import com.opencheps.engine.PositionHistory;

/** Recommendations are compared with confirmed history; they never advance that history. */
public final class CameraMoveFeedback {
    private PositionHistory.Snapshot suggested;
    private String move;
    public void reset(){suggested=null;move=null;}
    public void recommend(PositionHistory.Snapshot position,String uci){
        if(position==null||MoveAnnouncement.label(position.fen,uci)==null)return;
        suggested=position;move=uci;
    }
    public String observed(PositionHistory.Snapshot actual){
        if(suggested==null||actual==null)return null;
        if(actual.gameId!=suggested.gameId){reset();return null;}
        if(actual.plies<=suggested.plies)return null;
        String prefix=suggested.command+(suggested.plies==0?" moves ":" ");
        if(!actual.command.startsWith(prefix)){reset();return null;}
        String played=actual.command.substring(prefix.length()).split(" ")[0],expected=move;
        String fen=suggested.fen;reset();
        if(played.equals(expected)||MoveAnnouncement.label(fen,played)==null)return null;
        return "Сыграно "+coordinates(played)+" вместо "+coordinates(expected);
    }
    private static String coordinates(String uci){return uci.substring(0,2)+" → "+uci.substring(2,4)+(uci.length()==5?"="+Character.toUpperCase(uci.charAt(4)):"");}
}
