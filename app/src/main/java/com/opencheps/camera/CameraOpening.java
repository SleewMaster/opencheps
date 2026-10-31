package com.opencheps.camera;

import com.opencheps.util.Side;
import com.opencheps.vision.*;

/** Known initial position, also used to prove the first white move from a camera frame. */
public final class CameraOpening {
    private CameraOpening() {}
    public static ChessPosition initial(){
        String[] rows={"rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"};
        char[][] board=new char[8][];for(int r=0;r<8;r++)board[r]=rows[r].toCharArray();
        return new ChessPosition(board,Side.WHITE,"KQkq","-");
    }
}
