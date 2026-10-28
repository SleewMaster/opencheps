package com.opencheps.camera;

import com.opencheps.util.Side;
import com.opencheps.vision.ChessPosition;

/** A completed, legal recommendation is announced once per position, not once per camera frame. */
public final class MoveAnnouncement {
    private String spokenFen="";
    public String pending(String fen,String uci,boolean complete){
        return complete&&!spokenFen.equals(fen)?describe(fen,uci):null;
    }
    public void accepted(String fen){spokenFen=fen;}
    public void reset(){spokenFen="";}
    public static String label(String fen,String uci){
        if(describe(fen,uci)==null)return null;
        ChessPosition p=parse(fen);char type=Character.toLowerCase(p.board['8'-uci.charAt(1)][uci.charAt(0)-'a']);
        String name=type=='p'?"Пешка":type=='n'?"Конь":type=='b'?"Слон":type=='r'?"Ладья":type=='q'?"Ферзь":"Король";
        String result=name+"  "+uci.substring(0,2)+" → "+uci.substring(2,4);
        if(uci.length()==5)result+=" = "+Character.toUpperCase(uci.charAt(4));
        return result;
    }

    public static String describe(String fen,String uci){
        if(fen==null||uci==null||!uci.matches("[a-h][1-8][a-h][1-8][qrbn]?"))return null;
        ChessPosition position=parse(fen);if(position==null)return null;
        boolean legal=false;
        for(ChessPosition next:position.successors())if(uci.equals(next.move)){legal=true;break;}
        if(!legal)return null;
        int ff=uci.charAt(0)-'a',fr='8'-uci.charAt(1),tf=uci.charAt(2)-'a',tr='8'-uci.charAt(3);
        char type=Character.toLowerCase(position.board[fr][ff]);
        if(type=='k'&&Math.abs(tf-ff)==2)return tf>ff?"Короткая рокировка.":"Длинная рокировка.";
        String piece=type=='p'?"Пешка":type=='n'?"Конь":type=='b'?"Слон":type=='r'?"Ладья":type=='q'?"Ферзь":"Король";
        boolean capture=position.board[tr][tf]!='.'||(type=='p'&&ff!=tf);
        String text=piece+" с "+square(uci.charAt(0),uci.charAt(1))+(capture?" берёт на ":" на ")+square(uci.charAt(2),uci.charAt(3))+".";
        if(uci.length()==5){char p=uci.charAt(4);text+=" Превращение в "+(p=='q'?"ферзя":p=='r'?"ладью":p=='b'?"слона":"коня")+".";}
        return text;
    }
    private static String square(char file,char rank){
        String[] files={"а","бэ","цэ","дэ","е","эф","джи","аш"};
        String[] ranks={"один","два","три","четыре","пять","шесть","семь","восемь"};
        return files[file-'a']+" "+ranks[rank-'1'];
    }
    private static ChessPosition parse(String fen){
        String[] fields=fen.split(" ");if(fields.length<4||!(fields[1].equals("w")||fields[1].equals("b")))return null;
        String[] rows=fields[0].split("/",-1);if(rows.length!=8)return null;
        char[][] board=new char[8][8];
        for(int r=0;r<8;r++){
            int f=0;for(char p:rows[r].toCharArray()){
                if(p>='1'&&p<='8'){int end=f+p-'0';if(end>8)return null;while(f<end)board[r][f++]='.';}
                else{if(f>=8||"pPnNbBrRqQkK".indexOf(p)<0)return null;board[r][f++]=p;}
            }if(f!=8)return null;
        }
        return new ChessPosition(board,fields[1].equals("w")?Side.WHITE:Side.BLACK,fields[2],fields[3]);
    }
}
