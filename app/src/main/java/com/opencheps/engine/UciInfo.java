package com.opencheps.engine;

/** A single validated UCI principal-variation update. Android-independent. */
public final class UciInfo {
    public final String move;
    public final int index,depth,cp;
    public final boolean mate;
    private UciInfo(String move,int index,int depth,int cp,boolean mate) {
        this.move=move;this.index=index;this.depth=depth;this.cp=cp;this.mate=mate;
    }
    public static boolean isMove(String s) {return s!=null&&s.matches("[a-h][1-8][a-h][1-8][qrbn]?");}
    public static UciInfo parse(String line) {
        if(line==null||!line.startsWith("info ")||!line.contains(" pv "))return null;
        String[] tok=line.trim().split("\\s+");int index=1,depth=0,cp=0;boolean mate=false,score=false;String move=null;
        try {
            for(int i=1;i<tok.length-1;i++) switch(tok[i]) {
                case "depth":depth=Integer.parseInt(tok[++i]);break;
                case "multipv":index=Integer.parseInt(tok[++i]);break;
                case "cp":cp=Integer.parseInt(tok[++i]);score=true;break;
                case "mate":int n=Integer.parseInt(tok[++i]);mate=true;cp=n>0?100000-n:-100000-n;score=true;break;
                case "pv":move=tok[i+1];i=tok.length;break;
                default:break;
            }
        }catch(NumberFormatException e){return null;}
        return score&&index>0&&depth>0&&isMove(move)?new UciInfo(move,index,depth,cp,mate):null;
    }
}
