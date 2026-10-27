package com.opencheps.vision;
import java.util.List;

/** Choose a legal position using whole-board image evidence, not independent OCR labels. */
public final class VisualMoveTracker {
    public static final String PIECES=".PNBRQKpnbrqk";
    public static class Match {
        public final ChessPosition position, intermediate;
        public final int plies;
        public final float error, margin;
        Match(ChessPosition p,ChessPosition mid,int plies,float e,float margin){
            this.position=p;this.intermediate=mid;this.plies=plies;this.error=e;this.margin=margin;
        }
    }
    public static Match match(ChessPosition old,float[][] costs) {
        if(costs==null)return null;
        float[] baseline=new float[64];float sum=0;
        for(int i=0;i<64;i++){baseline[i]=costs[i][PIECES.indexOf(old.board[i/8][i%8])];sum+=baseline[i];}
        Search s=new Search(old,costs,baseline,sum);
        s.consider(old,null,0);
        List<ChessPosition> first=old.successors();
        for(ChessPosition p:first)s.consider(p,null,1);
        // No skipped-frame search when the unchanged position is an excellent fit.
        if(s.bestPlies!=0 || s.best>0.30f) {
            for(ChessPosition p:first)for(ChessPosition q:p.successors())s.consider(q,p,2);
        }
        float margin=s.second-s.best;
        // Require a clear unique match. A popup, animation, or covered piece
        // must not be committed as a chess move.
        if(s.best/64f>.085f || (s.bestPlies>0 && margin<.025f)) return null;
        // Changed cells must individually look like the proposed arrival/empty.
        for(int i=0;i<64;i++) {
            float c=costs[i][PIECES.indexOf(s.winner.board[i/8][i%8])];
            if(c>.30f) return null;
        }
        return new Match(s.winner,s.middle,s.bestPlies,s.best/64f,margin);
    }
    private static class Search {
        final ChessPosition old; final float[][] costs; final float[] baseline;final float sum;
        float best=Float.MAX_VALUE,second=Float.MAX_VALUE;ChessPosition winner,middle;int bestPlies;
        Search(ChessPosition old,float[][] c,float[] b,float sum){this.old=old;costs=c;baseline=b;this.sum=sum;}
        void consider(ChessPosition p,ChessPosition mid,int plies){
            float score=sum;
            for(int i=0;i<64;i++)if(p.board[i/8][i%8]!=old.board[i/8][i%8])
                score+=costs[i][PIECES.indexOf(p.board[i/8][i%8])]-baseline[i];
            // Different paths to the same diagram should not create fake ambiguity.
            if(winner!=null && same(p.board,winner.board))return;
            if(score<best){second=best;best=score;winner=p;middle=mid;bestPlies=plies;}
            else if(score<second)second=score;
        }
        boolean same(char[][] a,char[][] b){for(int r=0;r<8;r++)if(!java.util.Arrays.equals(a[r],b[r]))return false;return true;}
    }
}
