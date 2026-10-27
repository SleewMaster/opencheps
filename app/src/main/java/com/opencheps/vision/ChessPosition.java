package com.opencheps.vision;
import com.opencheps.util.Side;
import java.util.ArrayList;
import java.util.List;

/** Legal successor positions used to constrain visual recognition. Canonical orientation. */
public final class ChessPosition {
    public final char[][] board;
    public final Side turn;
    public final String rights, ep, move;
    public ChessPosition(char[][] b, Side t, String rights, String ep) { this(b,t,rights,ep,""); }
    private ChessPosition(char[][] b,Side t,String rights,String ep,String move) {
        this.board=b;this.turn=t;this.rights=rights;this.ep=ep;this.move=move;
    }
    private boolean own(char p) { return p!='.' && Character.isUpperCase(p)==(turn==Side.WHITE); }
    private static boolean in(int r,int f) {return r>=0&&r<8&&f>=0&&f<8;}
    public List<ChessPosition> successors() {
        List<ChessPosition> result=new ArrayList<>();
        for(int r=0;r<8;r++) for(int f=0;f<8;f++) {
            char p=board[r][f]; if(!own(p)) continue;
            char type=Character.toLowerCase(p);
            if(type=='p') {
                int d=turn==Side.WHITE?-1:1, nr=r+d;
                if(in(nr,f) && board[nr][f]=='.') {
                    add(result,r,f,nr,f,false);
                    if(r==(d<0?6:1)&&board[r+2*d][f]=='.') add(result,r,f,r+2*d,f,false);
                }
                for(int df:new int[]{-1,1}) if(in(nr,f+df)) {
                    char victim=board[nr][f+df];
                    if(victim!='.'&&!own(victim)&&Character.toLowerCase(victim)!='k') add(result,r,f,nr,f+df,false);
                    else if(victim=='.' && ep.equals(square(nr,f+df)) && r==(d<0?3:4)
                            && board[r][f+df]==(d<0?'p':'P')) add(result,r,f,nr,f+df,true);
                }
            } else if(type=='n'||type=='k') {
                int[][] steps=type=='n'?new int[][]{{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}}
                        :new int[][]{{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
                for(int[] s:steps) if(in(r+s[0],f+s[1])) add(result,r,f,r+s[0],f+s[1],false);
                if(type=='k' && f==4 && r==(turn==Side.WHITE?7:0) && !inCheck(board,turn)) {
                    for(boolean shortSide:new boolean[]{true,false}) {
                        char right=turn==Side.WHITE?(shortSide?'K':'Q'):(shortSide?'k':'q');
                        int rf=shortSide?7:0, d=shortSide?1:-1;
                        if(rights.indexOf(right)<0 || board[r][rf]!=(turn==Side.WHITE?'R':'r')) continue;
                        boolean clear=true; for(int x=f+d;x!=rf;x+=d) if(board[r][x]!='.') clear=false;
                        if(clear && !attacked(board,r,f+d,opposite(turn)) && !attacked(board,r,f+2*d,opposite(turn)))
                            add(result,r,f,r,f+2*d,false);
                    }
                }
            } else {
                for(int dr=-1;dr<=1;dr++) for(int df=-1;df<=1;df++) {
                    if(dr==0&&df==0) continue;
                    if(type=='b' && (dr==0||df==0)) continue;
                    if(type=='r' && dr!=0&&df!=0) continue;
                    int nr=r+dr,nf=f+df;
                    while(in(nr,nf)) {
                        add(result,r,f,nr,nf,false);
                        if(board[nr][nf]!='.') break;
                        nr+=dr;nf+=df;
                    }
                }
            }
        }
        return result;
    }
    private void add(List<ChessPosition> out,int fr,int ff,int tr,int tf,boolean enPassant) {
        char p=board[fr][ff],victim=board[tr][tf];
        if(own(victim)||Character.toLowerCase(victim)=='k') return;
        char[][] next=copy(board);next[fr][ff]='.';next[tr][tf]=p;
        if(enPassant) next[fr][tf]='.';
        if(Character.toLowerCase(p)=='k' && Math.abs(tf-ff)==2) {
            int rf=tf>ff?7:0;next[tr][tf>ff?5:3]=next[tr][rf];next[tr][rf]='.';
        }
        if(inCheck(next,turn)) return;
        String newRights=rights;
        if(p=='K') newRights=newRights.replace("K","").replace("Q","");
        if(p=='k') newRights=newRights.replace("k","").replace("q","");
        int[] corners={56,63,0,7};String[] cs={"Q","K","q","k"};
        for(int i=0;i<4;i++) if(fr*8+ff==corners[i]||tr*8+tf==corners[i]) newRights=newRights.replace(cs[i],"");
        if(newRights.isEmpty()) newRights="-";
        String newEp=Character.toLowerCase(p)=='p'&&Math.abs(tr-fr)==2?square((tr+fr)/2,ff):"-";
        String uci=square(fr,ff)+square(tr,tf);
        if(Character.toLowerCase(p)=='p'&&(tr==0||tr==7)) {
            for(char promotion:new char[]{'q','r','b','n'}) {
                char[][] promoted=copy(next);promoted[tr][tf]=turn==Side.WHITE?Character.toUpperCase(promotion):promotion;
                out.add(new ChessPosition(promoted,opposite(turn),newRights,newEp,uci+promotion));
            }
        } else out.add(new ChessPosition(next,opposite(turn),newRights,newEp,uci));
    }
    public static Side opposite(Side s) {return s==Side.WHITE?Side.BLACK:Side.WHITE;}
    public static String square(int r,int f) {return ""+(char)('a'+f)+(char)('8'-r);}
    public static char[][] copy(char[][] b) {char[][] n=new char[8][];for(int i=0;i<8;i++)n[i]=b[i].clone();return n;}
    public static boolean inCheck(char[][] b,Side side) {
        char king=side==Side.WHITE?'K':'k';
        for(int r=0;r<8;r++)for(int f=0;f<8;f++)if(b[r][f]==king)return attacked(b,r,f,opposite(side));
        return true;
    }
    public static boolean attacked(char[][] b,int r,int f,Side by) {
        boolean white=by==Side.WHITE;
        int pawnRow=r+(white?1:-1);
        for(int df:new int[]{-1,1}) if(in(pawnRow,f+df)&&b[pawnRow][f+df]==(white?'P':'p'))return true;
        for(int[] s:new int[][]{{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}})
            if(in(r+s[0],f+s[1])&&b[r+s[0]][f+s[1]]==(white?'N':'n'))return true;
        for(int dr=-1;dr<=1;dr++)for(int df=-1;df<=1;df++) {
            if(dr==0&&df==0)continue;
            int nr=r+dr,nf=f+df,dist=1;
            while(in(nr,nf)) {
                char p=b[nr][nf];
                if(p!='.') {
                    if(Character.isUpperCase(p)==white) {
                        char t=Character.toLowerCase(p);
                        if(t=='q'||(t=='b'&&dr!=0&&df!=0)||(t=='r'&&(dr==0||df==0))||(t=='k'&&dist==1))return true;
                    }
                    break;
                }
                nr+=dr;nf+=df;dist++;
            }
        }
        return false;
    }
}
