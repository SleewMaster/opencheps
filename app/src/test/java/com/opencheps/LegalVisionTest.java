package com.opencheps;
import com.opencheps.vision.*;import com.opencheps.util.Side;
import org.junit.Test;import static org.junit.Assert.*;
public class LegalVisionTest {
 private ChessPosition fen(String f){String[] fields=f.split(" "),rows=fields[0].split("/");char[][] b=new char[8][8];for(int r=0;r<8;r++){int x=0;for(char c:rows[r].toCharArray()){if(Character.isDigit(c)){for(int j=0;j<c-'0';j++)b[r][x++]='.';}else b[r][x++]=c;}}return new ChessPosition(b,fields[1].equals("w")?Side.WHITE:Side.BLACK,fields[2],fields[3]);}
 private ChessPosition initial(){return fen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -");}
 private int perft(ChessPosition p,int d){if(d==0)return 1;int n=0;for(ChessPosition q:p.successors())n+=perft(q,d-1);return n;}
 private ChessPosition play(ChessPosition p,String uci){for(ChessPosition n:p.successors())if(n.move.equals(uci))return n;throw new AssertionError(uci);}
 private float[][] costs(char[][] b){float[][] c=new float[64][13];for(int i=0;i<64;i++){java.util.Arrays.fill(c[i],.5f);c[i][VisualMoveTracker.PIECES.indexOf(b[i/8][i%8])]=0;}return c;}
 @Test public void initialPerftThree(){assertEquals(20,perft(initial(),1));assertEquals(400,perft(initial(),2));assertEquals(8902,perft(initial(),3));}
 @Test public void kiwipetePerftTwo(){ChessPosition p=fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -");assertEquals(48,perft(p,1));assertEquals(2039,perft(p,2));}
 @Test public void illegalPinnedMoveExcluded(){ChessPosition p=fen("4r1k1/8/8/8/8/8/4R3/4K3 w - -");for(ChessPosition n:p.successors())assertNotEquals("e2f2",n.move);}
 @Test public void noCastlingWithoutRights(){ChessPosition p=fen("4k3/8/8/8/8/8/8/R3K2R w - -");for(ChessPosition n:p.successors())assertFalse(n.move.equals("e1g1")||n.move.equals("e1c1"));}
 @Test public void castleThroughCheckExcluded(){ChessPosition p=fen("4kr2/8/8/8/8/8/8/4K2R w K -");for(ChessPosition n:p.successors())assertNotEquals("e1g1",n.move);}
 @Test public void enPassantRequiresTarget(){ChessPosition p=fen("4k3/8/8/3pP3/8/8/8/4K3 w - -");for(ChessPosition n:p.successors())assertNotEquals("e5d6",n.move);}
 @Test public void enPassantRemovesPawn(){ChessPosition p=fen("4k3/8/8/3pP3/8/8/8/4K3 w - d6");ChessPosition n=play(p,"e5d6");assertEquals('.',n.board[3][3]);assertEquals('P',n.board[2][3]);}
 @Test public void enPassantCannotExposeKing(){ChessPosition p=fen("4k3/8/8/r4pPK/8/8/8/8 w - f6");for(ChessPosition n:p.successors())assertNotEquals("g5f6",n.move);}
 @Test public void underpromotion(){ChessPosition p=fen("4k3/P7/8/8/8/8/8/4K3 w - -");assertEquals('N',play(p,"a7a8n").board[0][0]);}
 @Test public void visualSingleMove(){ChessPosition p=initial(),q=play(p,"e2e4");VisualMoveTracker.Match m=VisualMoveTracker.match(p,costs(q.board));assertNotNull(m);assertEquals(1,m.plies);assertEquals("e2e4",m.position.move);}
 @Test public void visualTwoMissedMoves(){ChessPosition p=initial(),q=play(play(p,"e2e4"),"g7g6");VisualMoveTracker.Match m=VisualMoveTracker.match(p,costs(q.board));assertNotNull(m);assertEquals(2,m.plies);assertEquals("e2e4",m.intermediate.move);assertEquals("g7g6",m.position.move);}
 @Test public void visualUnchanged(){ChessPosition p=initial();assertEquals(0,VisualMoveTracker.match(p,costs(p.board)).plies);}
 @Test public void visualAmbiguityRejected(){ChessPosition p=initial();float[][] c=new float[64][13];for(float[] row:c)java.util.Arrays.fill(row,.2f);assertNull(VisualMoveTracker.match(p,c));}
 @Test public void visualImpossibleTeleportRejected(){ChessPosition p=initial();char[][] bad=ChessPosition.copy(p.board);bad[6][4]='.';bad[3][4]='P';assertNull(VisualMoveTracker.match(p,costs(bad)));}
}
