package com.opencheps;
import com.opencheps.camera.MoveAnnouncement;
import org.junit.Test;import static org.junit.Assert.*;

public class MoveAnnouncementTest {
    private static final String START="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    @Test public void saysPawnAndCoordinates(){assertEquals("Пешка с е два на е четыре.",MoveAnnouncement.describe(START,"e2e4"));}
    @Test public void saysKnight(){assertEquals("Конь с джи один на эф три.",MoveAnnouncement.describe(START,"g1f3"));}
    @Test public void rejectsIllegalAndMalformedMoves(){for(String move:new String[]{"e2e5","e7e5","e2e2","(none)","0000","e2e4q","i2e4"})assertNull(MoveAnnouncement.describe(START,move));}
    @Test public void partialAnalysisIsNeverAnnounced(){assertNull(new MoveAnnouncement().pending(START,"e2e4",false));}
    @Test public void repeatsAcrossFramesAreSuppressedEvenIfPvChanges(){MoveAnnouncement a=new MoveAnnouncement();assertNotNull(a.pending(START,"e2e4",true));a.accepted(START);for(int i=0;i<30;i++)assertNull(a.pending(START,"d2d4",true));}
    @Test public void failedEnqueueCanRetry(){MoveAnnouncement a=new MoveAnnouncement();assertNotNull(a.pending(START,"e2e4",true));assertNotNull(a.pending(START,"e2e4",true));}
    @Test public void newPositionIsAnnounced(){MoveAnnouncement a=new MoveAnnouncement();a.accepted(START);String fen="rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";assertNotNull(a.pending(fen,"e7e5",true));}
    @Test public void newGameResetsSuppression(){MoveAnnouncement a=new MoveAnnouncement();a.accepted(START);a.reset();assertNotNull(a.pending(START,"e2e4",true));}
    @Test public void captureIsSpoken(){assertEquals("Пешка с е четыре берёт на дэ пять.",MoveAnnouncement.describe("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1","e4d5"));}
    @Test public void enPassantIsSpoken(){assertEquals("Пешка с е пять берёт на дэ шесть.",MoveAnnouncement.describe("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1","e5d6"));}
    @Test public void castleBothWaysAndColours(){assertEquals("Короткая рокировка.",MoveAnnouncement.describe("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1","e1g1"));assertEquals("Длинная рокировка.",MoveAnnouncement.describe("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1","e8c8"));}
    @Test public void promotionIsSpoken(){String fen="4k3/P7/8/8/8/8/8/4K3 w - - 0 1";assertTrue(MoveAnnouncement.describe(fen,"a7a8q").endsWith("Превращение в ферзя."));assertTrue(MoveAnnouncement.describe(fen,"a7a8n").endsWith("Превращение в коня."));}
    @Test public void malformedPositionIsRejected(){for(String fen:new String[]{"", "8/8 w - -", "9/8/8/8/8/8/8/8 w - -", "8/8/8/8/8/8/8/8 x - -"})assertNull(MoveAnnouncement.describe(fen,"e2e4"));assertNull(MoveAnnouncement.describe(null,"e2e4"));assertNull(MoveAnnouncement.describe(START,null));}
}
