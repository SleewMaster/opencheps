package com.opencheps.vision;

import com.opencheps.util.Side;

/** Explicit history fields for starting observation in the middle of a game. */
public final class PositionSeed {
    public final ChessPosition position;
    public final Side perspective;
    public final int halfmove,fullmove;
    public PositionSeed(char[][] canonical,Side turn,Side perspective,String rights,String ep,int halfmove,int fullmove){
        this.position=new ChessPosition(ChessPosition.copy(canonical),turn,rights,ep);
        this.perspective=perspective;this.halfmove=halfmove;this.fullmove=fullmove;
    }
    public String error(){
        char[][] b=position.board;FenBuilder.Validation v=FenBuilder.validate(b);
        if(!v.ok)return "Проверь фигуры: нужны два короля, без соседних королей и пешек на краю.";
        if(position.turn==null||perspective==null)return "Выбери очередь хода и сторону снизу.";
        if(ChessPosition.inCheck(b,ChessPosition.opposite(position.turn)))return "У стороны, которая уже сходила, король под шахом. Проверь очередь хода и фигуры.";
        String rights=position.rights;
        if(rights==null||!rights.matches("-|K?Q?k?q?"))return "Неверные права на рокировку.";
        for(char c:rights.toCharArray())if(c!='-'&&!canHaveRight(b,c))return "Для рокировки король и ладья должны быть на исходных клетках.";
        String ep=position.ep;
        if(ep==null)return "Укажи поле взятия на проходе или −.";
        if(!ep.equals("-")){
            if(!ep.matches("[a-h][36]"))return "Поле взятия на проходе: a3…h3, a6…h6 или −.";
            int r='8'-ep.charAt(1),f=ep.charAt(0)-'a';boolean white=position.turn==Side.WHITE;
            if(r!=(white?2:5)||b[r][f]!='.'||b[r+(white?1:-1)][f]!=(white?'p':'P')||b[r+(white?-1:1)][f]!='.')return "Поле на проходе не соответствует последнему двойному ходу пешки.";
        }
        if(halfmove<0||fullmove<1)return "Неверный счётчик ходов.";
        return null;
    }
    public String fen(){return FenBuilder.build(position.board,Side.WHITE,position.turn,position.rights,position.ep,halfmove,fullmove);}
    public static boolean canHaveRight(char[][] b,char right){
        boolean white=Character.isUpperCase(right);int r=white?7:0,f=Character.toLowerCase(right)=='k'?7:0;
        return b[r][4]==(white?'K':'k')&&b[r][f]==(white?'R':'r');
    }
    public static PositionSeed parse(String fen,Side perspective){
        if(fen==null)return null;String[] a=fen.trim().split("\\s+");if(a.length!=4&&a.length!=6)return null;
        if(!a[1].matches("w|b"))return null;String[] rows=a[0].split("/",-1);if(rows.length!=8)return null;
        char[][] b=new char[8][8];
        for(int r=0;r<8;r++){int f=0;for(char c:rows[r].toCharArray()){
            if(c>='1'&&c<='8'){int end=f+c-'0';if(end>8)return null;while(f<end)b[r][f++]='.';}
            else {if(f>=8||"PNBRQKpnbrqk".indexOf(c)<0)return null;b[r][f++]=c;}
        }if(f!=8)return null;}
        try{
            PositionSeed seed=new PositionSeed(b,a[1].equals("w")?Side.WHITE:Side.BLACK,perspective,a[2],a[3],a.length==6?Integer.parseInt(a[4]):0,a.length==6?Integer.parseInt(a[5]):1);
            return seed.error()==null?seed:null;
        }catch(NumberFormatException e){return null;}
    }
}
