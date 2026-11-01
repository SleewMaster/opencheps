package com.opencheps.engine;

import com.opencheps.util.Side;
import com.opencheps.vision.*;
import java.util.*;

/** End a stable quiet search early; still wait for the engine's terminal bestmove. */
public final class QuickDecision {
    private final String fen;
    private final long game;
    private final boolean human,enabled;
    private final int count;
    private final Map<String,ChessPosition> legal=new HashMap<>();
    private final StockfishEngine.Pv[] iteration;
    private int depth,completed,lastDepth,lastScore,stable;
    private String lastMove="";
    public QuickDecision(String fen,long game,int count,boolean human){
        this.fen=fen;this.game=game;this.human=human;
        PositionSeed seed=PositionSeed.parse(fen,Side.WHITE);int captures=0,checks=0;boolean promotion=false;
        if(seed!=null)for(ChessPosition next:seed.position.successors()){
            legal.put(next.move,next);String m=next.move;int fr='8'-m.charAt(1),ff=m.charAt(0)-'a',tr='8'-m.charAt(3),tf=m.charAt(2)-'a';
            if(seed.position.board[tr][tf]!='.'||(Character.toLowerCase(seed.position.board[fr][ff])=='p'&&ff!=tf))captures++;
            if(ChessPosition.inCheck(next.board,next.turn))checks++;
            if(m.length()==5)promotion=true;
        }
        enabled=seed!=null&&!ChessPosition.inCheck(seed.position.board,seed.position.turn)&&captures<2&&checks<2&&!promotion;
        this.count=Math.max(1,Math.min(Math.min(3,count),legal.size()));iteration=new StockfishEngine.Pv[this.count];
    }
    public boolean accept(UciInfo info){
        if(!enabled||info==null||info.index>count||info.depth<depth)return false;
        if(info.depth>depth){depth=info.depth;Arrays.fill(iteration,null);}
        iteration[info.index-1]=new StockfishEngine.Pv(info.move,info.cp,info.mate,info.depth,info.exact);
        if(depth<=completed)return false;
        for(StockfishEngine.Pv p:iteration)if(p==null||!p.exact||p.mate||!legal.containsKey(p.uci))return false;
        completed=depth;
        StockfishEngine.Pv selected=PlayerStyle.choose(fen,game,Arrays.asList(iteration),human,1).get(0);
        ChessPosition next=legal.get(selected.uci);String m=selected.uci;
        PositionSeed seed=PositionSeed.parse(fen,Side.WHITE);int fr='8'-m.charAt(1),ff=m.charAt(0)-'a',tr='8'-m.charAt(3),tf=m.charAt(2)-'a';
        char piece=Character.toLowerCase(seed.position.board[fr][ff]);
        boolean castle=piece=='k'&&Math.abs(tf-ff)==2;
        boolean capture=seed.position.board[tr][tf]!='.'||(piece=='p'&&ff!=tf);
        if(depth<12||selected.cp< -50||capture||ChessPosition.inCheck(next.board,next.turn)){stable=0;return false;}
        stable=m.equals(lastMove)&&depth==lastDepth+1&&Math.abs(selected.cp-lastScore)<=12?stable+1:1;
        lastMove=m;lastDepth=depth;lastScore=selected.cp;
        return castle?depth>=13&&stable>=2:depth>=16&&stable>=3;
    }
}
