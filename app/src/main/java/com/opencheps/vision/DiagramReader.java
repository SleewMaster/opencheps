package com.opencheps.vision;

/** Independent diagram reading for joining an already running game. History is never invented. */
public final class DiagramReader {
    public static final class Result {
        public final char[][] screen;
        public final boolean[] uncertain;
        public final float error;
        Result(char[][] b,boolean[] uncertain,float error){screen=b;this.uncertain=uncertain;this.error=error;}
        public boolean confident(){for(boolean u:uncertain)if(u)return false;return true;}
    }
    public static Result read(float[][] costs){
        if(costs==null||costs.length!=64)return null;
        char[][] board=new char[8][8];boolean[] uncertain=new boolean[64];float total=0;
        for(int i=0;i<64;i++){
            if(costs[i]==null||costs[i].length!=13)return null;
            float best=Float.POSITIVE_INFINITY,second=best;int label=0;
            for(int j=0;j<13;j++){float d=costs[i][j];if(d<best){second=best;best=d;label=j;}else if(d<second)second=d;}
            board[i/8][i%8]=VisualMoveTracker.PIECES.charAt(label);
            uncertain[i]=!Float.isFinite(best)||best>.12f||second-best<.018f;
            total+=best;
        }return new Result(board,uncertain,total/64);
    }
}
