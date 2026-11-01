package com.opencheps.engine;

import com.opencheps.vision.ChessPosition;

/** Spend extra time on tactical positions while leaving the fast setting available. */
public final class CameraSearchBudget {
    private CameraSearchBudget(){}
    public static int millis(ChessPosition position,int requested,boolean strong){
        int selected=Math.max(500,Math.min(15000,requested));
        if(!strong)return selected;
        int target=3000;
        if(position!=null){
            int captures=0,checks=0,pieces=0;boolean promotion=false;
            for(char[] row:position.board)for(char p:row)if(p!='.')pieces++;
            for(ChessPosition next:position.successors()){
                String m=next.move;int fr='8'-m.charAt(1),ff=m.charAt(0)-'a',tr='8'-m.charAt(3),tf=m.charAt(2)-'a';
                if(position.board[tr][tf]!='.'||(Character.toLowerCase(position.board[fr][ff])=='p'&&ff!=tf))captures++;
                if(ChessPosition.inCheck(next.board,next.turn))checks++;
                if(m.length()==5)promotion=true;
            }
            if(ChessPosition.inCheck(position.board,position.turn)||captures>=3||checks>=2||promotion)target=6000;
            else if(pieces<=10)target=5000;
        }
        return Math.max(selected,target);
    }
}
