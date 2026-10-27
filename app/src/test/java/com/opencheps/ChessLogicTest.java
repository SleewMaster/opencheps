package com.opencheps;

import com.opencheps.util.Side;
import com.opencheps.vision.FenBuilder;
import com.opencheps.vision.MoveAnalyzer;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChessLogicTest {
    private char[][] initial() {
        return board("rnbqkbnr","pppppppp","........","........",
                     "........","........","PPPPPPPP","RNBQKBNR");
    }
    private char[][] kings() {
        return board("....k...","........","........","........",
                     "........","........","........","....K...");
    }
    private char[][] board(String... rows) {
        if (rows.length != 8) throw new IllegalArgumentException("8 rows required");
        char[][] result = new char[8][8];
        for (int i = 0; i < 8; i++) result[i] = rows[i].toCharArray();
        return result;
    }
    private char[][] copy(char[][] src) {
        char[][] dst = new char[8][];
        for (int i=0;i<8;i++) dst[i] = src[i].clone();
        return dst;
    }
    private MoveAnalyzer.Result move(char[][] old, char[][] next) {
        return MoveAnalyzer.analyze(old, next);
    }
    @Test public void initialFenHasCompleteRights() {
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                FenBuilder.build(initial(),Side.WHITE,Side.WHITE,"KQkq","-",0,1));
    }
    @Test public void rotatedBoardProducesSameFen() {
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1",
                FenBuilder.build(FenBuilder.flip(initial()),Side.BLACK,Side.BLACK,
                        "KQkq","-",0,1));
    }
    @Test public void rejectsUnknownPiece() {
        char[][] b=initial(); b[4][4]='Z';
        assertFalse(FenBuilder.validate(b).ok);
        assertNull(FenBuilder.build(b,Side.WHITE,Side.WHITE));
    }
    @Test public void rejectsMalformedBoards() {
        assertFalse(FenBuilder.validate(null).ok);
        assertFalse(FenBuilder.validate(new char[7][8]).ok);
        assertFalse(FenBuilder.validate(new char[8][7]).ok);
    }
    @Test public void rejectsAdjacentKings() {
        char[][] b=kings(); b[0][4]='.'; b[6][4]='k';
        assertFalse(FenBuilder.validate(b).ok);
    }
    @Test public void identifiesQuietPawnDoublePush() {
        char[][] a=initial(), b=copy(a); b[6][4]='.'; b[4][4]='P';
        assertEquals(MoveAnalyzer.Kind.QUIET,move(a,b).kind);
        assertEquals(Side.WHITE,move(a,b).mover);
    }
    @Test public void identifiesBlackPawnPush() {
        char[][] a=initial(), b=copy(a); b[1][4]='.'; b[3][4]='p';
        assertEquals(MoveAnalyzer.Kind.QUIET,move(a,b).kind);
        assertEquals(Side.BLACK,move(a,b).mover);
    }
    @Test public void normalizingRotatedBoardPreservesMoves() {
        char[][] a=initial(), b=copy(a); b[1][4]='.'; b[3][4]='p';
        char[][] screenPrev=FenBuilder.flip(a), screenNext=FenBuilder.flip(b);
        assertEquals(MoveAnalyzer.Kind.QUIET,move(FenBuilder.flip(screenPrev),
                                                    FenBuilder.flip(screenNext)).kind);
    }
    @Test public void impossiblePawnTeleportIsRejected() {
        char[][] a=initial(), b=copy(a); b[6][4]='.'; b[3][4]='P';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,move(a,b).kind);
    }
    @Test public void backwardPawnIsRejected() {
        char[][] a=kings(), b=copy(a); a[5][4]='P'; b[5][4]='.'; b[6][4]='P';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,move(a,b).kind);
    }
    @Test public void knightMoveAndInvalidKnightJump() {
        char[][] a=initial(), b=copy(a); b[7][6]='.'; b[5][5]='N';
        assertEquals(MoveAnalyzer.Kind.QUIET,move(a,b).kind);
        char[][] c=copy(a); c[7][6]='.'; c[5][6]='N';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,move(a,c).kind);
    }
    @Test public void blockedBishopIsRejected() {
        char[][] a=initial(), b=copy(a); b[7][2]='.'; b[4][5]='B';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,move(a,b).kind);
    }
    @Test public void validCaptureAndForwardPawnCaptureRejected() {
        char[][] a=kings(); a[4][4]='P'; a[3][3]='p';
        char[][] b=copy(a); b[4][4]='.'; b[3][3]='P';
        assertEquals(MoveAnalyzer.Kind.CAPTURE,move(a,b).kind);
        char[][] c=kings(); c[4][4]='P'; c[3][4]='p';
        char[][] d=copy(c); d[4][4]='.'; d[3][4]='P';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,move(c,d).kind);
    }
    @Test public void validEnPassant() {
        char[][] a=kings(); a[3][4]='P'; a[3][3]='p';
        char[][] b=copy(a); b[3][4]='.'; b[3][3]='.'; b[2][3]='P';
        assertEquals(MoveAnalyzer.Kind.EN_PASSANT,move(a,b).kind);
    }
    @Test public void invalidEnPassantFromWrongRank() {
        char[][] a=kings(); a[4][4]='P'; a[4][3]='p';
        char[][] b=copy(a); b[4][4]='.'; b[4][3]='.'; b[3][3]='P';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,move(a,b).kind);
    }
    @Test public void castlingKingSide() {
        char[][] a=kings(); a[7][7]='R';
        char[][] b=copy(a); b[7][4]='.'; b[7][7]='.'; b[7][6]='K'; b[7][5]='R';
        assertEquals(MoveAnalyzer.Kind.CASTLE,move(a,b).kind);
    }
    @Test public void blockedCastlingRejected() {
        char[][] a=kings(); a[7][7]='R'; a[7][5]='B';
        char[][] b=copy(a); b[7][4]='.'; b[7][7]='.'; b[7][6]='K'; b[7][5]='R';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,move(a,b).kind);
    }
    @Test public void promotionMustBeOnFinalRank() {
        char[][] a=kings(); a[1][0]='P';
        char[][] b=copy(a); b[1][0]='.'; b[0][0]='Q';
        assertEquals(MoveAnalyzer.Kind.PROMOTION,move(a,b).kind);
        char[][] c=copy(a); c[1][0]='.'; c[0][0]='P';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,move(a,c).kind);
    }
    @Test public void sidewaysPromotionRejected() {
        char[][] a=kings(); a[2][0]='P';
        char[][] b=copy(a); b[2][0]='.'; b[1][0]='Q';
        assertEquals(MoveAnalyzer.Kind.UNKNOWN,move(a,b).kind);
    }
    @Test public void noChangeIsNone() {
        assertEquals(MoveAnalyzer.Kind.NONE,move(initial(),copy(initial())).kind);
    }
    @Test public void flipTwiceIsIdentity() {
        char[][] a=initial(), b=FenBuilder.flip(FenBuilder.flip(a));
        for(int r=0;r<8;r++) assertArrayEquals(a[r],b[r]);
    }
    @Test public void recoversTwoFastMovesE4G6() {
        char[][] before = initial(), after = copy(before);
        after[6][4] = '.'; after[4][4] = 'P'; // e2-e4
        after[1][6] = '.'; after[2][6] = 'p'; // g7-g6
        com.opencheps.vision.MoveSequenceRecovery.Result r =
                com.opencheps.vision.MoveSequenceRecovery.recover(before, after, Side.WHITE);
        assertNotNull(r);
        assertEquals(Side.WHITE, r.first.mover);
        assertEquals(Side.BLACK, r.second.mover);
        assertEquals(MoveAnalyzer.Kind.QUIET, r.first.kind);
        assertEquals(MoveAnalyzer.Kind.QUIET, r.second.kind);
        assertEquals('P', r.intermediate[4][4]);
        assertEquals('p', r.intermediate[1][6]);
    }
    @Test public void recoversTwoFastMovesInBlackBoardOrientation() {
        char[][] before = initial(), after = copy(before);
        after[6][4] = '.'; after[4][4] = 'P';
        after[1][6] = '.'; after[2][6] = 'p';
        char[][] blackPrevious = FenBuilder.flip(before);
        char[][] blackCurrent = FenBuilder.flip(after);
        com.opencheps.vision.MoveSequenceRecovery.Result r =
                com.opencheps.vision.MoveSequenceRecovery.recover(
                        FenBuilder.flip(blackPrevious),
                        FenBuilder.flip(blackCurrent), Side.WHITE);
        assertNotNull(r);
        assertEquals(Side.BLACK, r.second.mover);
    }
    @Test public void rejectsRecoveryForOneMoveAndNoChange() {
        char[][] a = initial(), b = copy(a);
        b[6][4] = '.'; b[4][4] = 'P';
        assertNull(com.opencheps.vision.MoveSequenceRecovery.recover(a,b,Side.WHITE));
        assertNull(com.opencheps.vision.MoveSequenceRecovery.recover(a,a,Side.WHITE));
    }

}

