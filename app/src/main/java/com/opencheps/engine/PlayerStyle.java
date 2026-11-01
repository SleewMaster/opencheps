package com.opencheps.engine;

import com.opencheps.util.Side;
import com.opencheps.vision.*;
import java.util.*;

/** A practical preference among engine-approved near-equals, never a replacement evaluator. */
public final class PlayerStyle {
    private PlayerStyle(){}
    public static int searchCount(boolean human,int displayed){return human?3:Math.max(1,Math.min(3,displayed));}
    public static int searchMillis(boolean human,int requested){return human?Math.max(5000,requested):requested;}
    public static List<StockfishEngine.Pv> choose(PositionHistory.Snapshot position,List<StockfishEngine.Pv> moves,boolean human,int displayed){
        return choose(position.fen,position.gameId,moves,human,displayed);
    }
    public static List<StockfishEngine.Pv> choose(String fen,long game,List<StockfishEngine.Pv> moves,boolean human,int displayed){
        if(moves==null||moves.isEmpty())return Collections.emptyList();
        StockfishEngine.Pv best=moves.get(0),chosen=best;
        PositionSeed seed=human?PositionSeed.parse(fen,Side.WHITE):null;
        if(seed!=null&&best.exact&&!best.mate&&best.depth>=12){
            Map<String,ChessPosition> legal=new HashMap<>();for(ChessPosition n:seed.position.successors())legal.put(n.move,n);
            ChessPosition first=legal.get(best.uci);
            if(first!=null){
                double rank=preference(seed.position,first,game),limit=best.cp< -100?10:18;
                for(StockfishEngine.Pv candidate:moves){
                    int loss=best.cp-candidate.cp;
                    if(candidate==best||candidate.mate||!candidate.exact||candidate.depth!=best.depth||loss<0||loss>limit)continue;
                    if((best.cp>=0&&candidate.cp<0)||(best.cp>=80&&candidate.cp<80))continue;
                    ChessPosition next=legal.get(candidate.uci);if(next==null)continue;
                    if(best.cp>0&&next.successors().isEmpty()&&!ChessPosition.inCheck(next.board,next.turn))continue;
                    double value=preference(seed.position,next,game)-loss*.5;
                    if(value>rank){rank=value;chosen=candidate;}
                }
            }
        }
        List<StockfishEngine.Pv> out=new ArrayList<>();out.add(chosen);
        for(StockfishEngine.Pv p:moves)if(out.size()<Math.max(1,Math.min(3,displayed))&&!contains(out,p.uci))out.add(p);
        return out;
    }
    private static boolean contains(List<StockfishEngine.Pv> list,String move){for(StockfishEngine.Pv p:list)if(p.uci.equals(move))return true;return false;}
    private static double preference(ChessPosition before,ChessPosition after,long game){
        String m=after.move;int fr='8'-m.charAt(1),ff=m.charAt(0)-'a',tr='8'-m.charAt(3),tf=m.charAt(2)-'a';
        char piece=Character.toLowerCase(before.board[fr][ff]);int home=before.turn==Side.WHITE?7:0,pieces=0,minors=0;
        for(char[] row:before.board)for(char p:row)if(p!='.')pieces++;
        for(char p:before.board[home])if(p!='.'&&Character.isUpperCase(p)==(before.turn==Side.WHITE)&&"nb".indexOf(Character.toLowerCase(p))>=0)minors++;
        double score=Math.floorMod((m+":"+game).hashCode(),5);
        if(piece=='n'||piece=='b')score+=3*(central(fr,ff)-central(tr,tf))+(fr==home&&tr!=home?16:0);
        if(piece=='p'&&(tf==3||tf==4))score+=5;
        if(piece=='k')score+=Math.abs(tf-ff)==2?24:pieces>12?-18:4*(central(fr,ff)-central(tr,tf));
        if(piece=='q'&&minors>=2)score-=14;
        if(ChessPosition.inCheck(after.board,after.turn))score+=7;
        if(ChessPosition.attacked(after.board,tr,tf,after.turn)&&!ChessPosition.attacked(after.board,tr,tf,before.turn))score-=18;
        return score;
    }
    private static double central(int r,int f){return Math.abs(r-3.5)+Math.abs(f-3.5);}
    /** Describes the visible move's purpose, without inventing a calculated continuation. */
    public static String idea(String fen,String move){
        PositionSeed seed=PositionSeed.parse(fen,Side.WHITE);if(seed==null||!UciInfo.isMove(move))return "";
        for(ChessPosition next:seed.position.successors())if(next.move.equals(move)){
            int fr='8'-move.charAt(1),ff=move.charAt(0)-'a',tr='8'-move.charAt(3),tf=move.charAt(2)-'a';char p=Character.toLowerCase(seed.position.board[fr][ff]);
            if(ChessPosition.inCheck(next.board,next.turn))return next.successors().isEmpty()?"Мат королю":"Шах и давление на короля";
            if(move.length()==5)return "Превращение пешки";
            if(p=='k'&&Math.abs(tf-ff)==2)return "Рокировка и безопасность короля";
            if(seed.position.board[tr][tf]!='.'||(p=='p'&&ff!=tf))return "Взятие и изменение материала";
            if((p=='n'||p=='b')&&fr==(seed.position.turn==Side.WHITE?7:0))return "Развитие лёгкой фигуры";
            if(p=='p'&&(tf==3||tf==4))return "Борьба за центр";
            return p=='k'?"Активизация короля":"Улучшение позиции фигуры";
        }return "";
    }
}
