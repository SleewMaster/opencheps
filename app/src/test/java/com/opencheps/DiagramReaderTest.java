package com.opencheps;
import com.opencheps.vision.*;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class DiagramReaderTest {
 private float[][] costs(){float[][] a=new float[64][13];for(float[] row:a){Arrays.fill(row,.5f);row[0]=0;}return a;}
 @Test public void readsSparseEndgameWithoutHistory(){float[][] a=costs();a[63][0]=.5f;a[63][VisualMoveTracker.PIECES.indexOf('K')]=.01f;a[0][0]=.5f;a[0][VisualMoveTracker.PIECES.indexOf('k')]=.01f;DiagramReader.Result r=DiagramReader.read(a);assertTrue(r.confident());assertEquals('K',r.screen[7][7]);assertEquals('k',r.screen[0][0]);}
 @Test public void ambiguousPieceMustBeReviewed(){float[][] a=costs();a[9][0]=.5f;a[9][1]=.05f;a[9][2]=.06f;assertTrue(DiagramReader.read(a).uncertain[9]);}
 @Test public void occludedCellMustBeReviewed(){float[][] a=costs();Arrays.fill(a[20],Float.POSITIVE_INFINITY);assertTrue(DiagramReader.read(a).uncertain[20]);}
 @Test public void rejectsMalformedCosts(){assertNull(DiagramReader.read(null));assertNull(DiagramReader.read(new float[10][]));}
}
