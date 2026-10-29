package com.opencheps.engine;

import com.opencheps.util.Side;
import com.opencheps.vision.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Confirmed legal moves from a known starting FEN, including repetition and move clocks. */
public final class PositionHistory {
    private static final AtomicLong IDS=new AtomicLong();
    private long gameId;
    private String anchor;
    private ChessPosition current;
    private int halfmove,fullmove;
    private final List<String> moves=new ArrayList<>(),keys=new ArrayList<>();
    public static final class Snapshot {
        public final String fen,command,key;
        public final long gameId;
        public final int plies,repetitions,halfmove,fullmove;
        private Snapshot(String fen,String command,long gameId,int plies,int repetitions,int halfmove,int fullmove){
            this.fen=fen;this.command=command;this.key=gameId+":"+command;this.gameId=gameId;this.plies=plies;
            this.repetitions=repetitions;this.halfmove=halfmove;this.fullmove=fullmove;
        }
    }
    public void clear(){anchor=null;current=null;moves.clear();keys.clear();gameId=IDS.incrementAndGet();}
    public void reset(String fen){
        PositionSeed seed=PositionSeed.parse(fen,Side.WHITE);
        if(seed==null)throw new IllegalArgumentException("Invalid history anchor");
        clear();anchor=seed.fen();current=seed.position;halfmove=seed.halfmove;fullmove=seed.fullmove;keys.add(repetitionKey(current));
    }
    public boolean started(){return current!=null;}
    /** Validate the entire observed sequence before committing either of two missed plies. */
    public void advance(ChessPosition... observed){
        if(current==null)throw new IllegalStateException("History has no anchor");
        List<ChessPosition> accepted=new ArrayList<>();ChessPosition previous=current;
        for(ChessPosition target:observed){
            if(target==null)throw new IllegalArgumentException("Missing intermediate position");
            ChessPosition found=null;
            for(ChessPosition next:previous.successors())if(same(next,target)&&(target.move.isEmpty()||next.move.equals(target.move))){found=next;break;}
            if(found==null)throw new IllegalArgumentException("Observed move is not a legal continuation");
            accepted.add(found);previous=found;
        }
        for(ChessPosition next:accepted){
            String u=next.move;char piece=current.board['8'-u.charAt(1)][u.charAt(0)-'a'];
            boolean capture=current.board['8'-u.charAt(3)][u.charAt(2)-'a']!='.';
            halfmove=Character.toLowerCase(piece)=='p'||capture?0:halfmove+1;
            if(current.turn==Side.BLACK)fullmove++;
            moves.add(u);current=next;keys.add(repetitionKey(next));
        }
    }
    public Snapshot snapshot(){
        if(current==null)throw new IllegalStateException("History has no anchor");
        String fen=FenBuilder.build(current.board,Side.WHITE,current.turn,current.rights,current.ep,halfmove,fullmove);
        String command="position fen "+anchor+(moves.isEmpty()?"":" moves "+String.join(" ",moves));
        int repetitions=0;String key=keys.get(keys.size()-1);for(String k:keys)if(k.equals(key))repetitions++;
        return new Snapshot(fen,command,gameId,moves.size(),repetitions,halfmove,fullmove);
    }
    private static boolean same(ChessPosition a,ChessPosition b){return a.turn==b.turn&&a.rights.equals(b.rights)&&a.ep.equals(b.ep)&&Arrays.deepEquals(a.board,b.board);}
    private static String repetitionKey(ChessPosition p){
        String ep="-";
        if(!p.ep.equals("-"))for(ChessPosition next:p.successors()){
            String u=next.move;int fr='8'-u.charAt(1),ff=u.charAt(0)-'a',tr='8'-u.charAt(3),tf=u.charAt(2)-'a';
            if(Character.toLowerCase(p.board[fr][ff])=='p'&&ff!=tf&&p.board[tr][tf]=='.'&&u.substring(2,4).equals(p.ep)){ep=p.ep;break;}
        }
        String f=FenBuilder.build(p.board,Side.WHITE,p.turn,p.rights,ep,0,1);return f.substring(0,f.lastIndexOf(" 0 1"));
    }
}
