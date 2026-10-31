package com.opencheps;

import com.opencheps.vision.*;
import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class DiagramConsensusTest {
    private DiagramReader.Result diagram(int count, char piece){
        float[][] costs=new float[64][13];
        for(int i=0;i<64;i++){
            Arrays.fill(costs[i],.5f);
            costs[i][VisualMoveTracker.PIECES.indexOf(i<count?piece:'.')]=0;
        }
        return DiagramReader.read(costs);
    }
    @Test public void exactDiagramStillNeedsTwoFrames(){
        DiagramConsensus c=new DiagramConsensus();
        assertFalse(c.accept(diagram(4,'P')));
        assertTrue(c.accept(diagram(4,'P')));
    }
    @Test public void oneFlickeringPieceDoesNotRestartWholeScan(){
        DiagramConsensus c=new DiagramConsensus();
        assertFalse(c.accept(diagram(1,'B')));
        DiagramReader.Result current=diagram(1,'N');
        assertTrue(c.accept(current));
        assertTrue(current.uncertain[0]);
        assertFalse(current.uncertain[1]);
        assertEquals('N',current.screen[0][0]);
    }
    @Test public void noisyScanIsBoundedAndDisagreementsStayVisible(){
        DiagramConsensus c=new DiagramConsensus();
        for(int i=0;i<DiagramConsensus.MAX_FRAMES-1;i++)assertFalse(c.accept(diagram(32,i%2==0?'P':'N')));
        DiagramReader.Result current=diagram(32,'B');
        assertTrue(c.accept(current));
        for(int i=0;i<32;i++){assertTrue(current.uncertain[i]);assertEquals('B',current.screen[i/8][i%8]);}
        assertFalse(current.uncertain[63]);
    }
    @Test public void lostGridClearsEarlierScanAndItsUncertainty(){
        DiagramConsensus c=new DiagramConsensus();
        c.accept(diagram(1,'B'));c.accept(diagram(1,'N'));c.accept(null);c.accept(null);
        DiagramReader.Result fresh=diagram(1,'P');
        assertFalse(c.accept(fresh));assertFalse(fresh.uncertain[0]);
        assertTrue(c.accept(diagram(1,'P')));
        c.reset();assertEquals(0,c.frames());
    }
    @Test public void singleBlurredFrameDoesNotEraseTheScan(){
        DiagramConsensus c=new DiagramConsensus();
        assertFalse(c.accept(diagram(1,'B')));assertFalse(c.accept(null));
        assertTrue(c.accept(diagram(1,'N')));
    }
}
