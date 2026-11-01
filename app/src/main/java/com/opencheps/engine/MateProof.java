package com.opencheps.engine;

import com.opencheps.vision.*;
import com.opencheps.util.Side;

/** An engine's mate-in-one report can be checked exactly before ending a search early. */
public final class MateProof {
    private MateProof(){}
    public static boolean inOne(String fen,String move){
        PositionSeed seed=PositionSeed.parse(fen,Side.WHITE);
        if(seed==null||!UciInfo.isMove(move))return false;
        for(ChessPosition next:seed.position.successors())if(next.move.equals(move))
            return ChessPosition.inCheck(next.board,next.turn)&&next.successors().isEmpty();
        return false;
    }
}
