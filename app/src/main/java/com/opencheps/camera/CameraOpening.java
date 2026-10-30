package com.opencheps.camera;

import com.opencheps.util.Side;
import com.opencheps.vision.*;

/** Recognise a fully evidenced first white move without relabelling moved pieces as start squares. */
public final class CameraOpening {
    private CameraOpening() {}
    public static ChessPosition initial(){
        String[] rows={"rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"};
        char[][] board=new char[8][];for(int r=0;r<8;r++)board[r]=rows[r].toCharArray();
        return new ChessPosition(board,Side.WHITE,"KQkq","-");
    }
    public static VisualMoveTracker.Match match(float[][] costs){
        if(costs==null||costs.length!=64)return null;
        for(float[] cell:costs){if(cell==null||cell.length!=13)return null;for(float value:cell)if(!Float.isFinite(value)||value<0)return null;}
        VisualMoveTracker.Match result=VisualMoveTracker.match(initial(),costs);
        if(result==null||result.plies!=1||result.error>.035f||result.margin<.12f)return null;
        for(int i=0;i<64;i++)if(costs[i][VisualMoveTracker.PIECES.indexOf(result.position.board[i/8][i%8])]>.18f)return null;
        return result;
    }
}
