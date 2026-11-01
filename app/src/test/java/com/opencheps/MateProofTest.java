package com.opencheps;
import com.opencheps.engine.MateProof;import org.junit.Test;import static org.junit.Assert.*;
public class MateProofTest {
 public static final String WHITE="7k/8/5KQ1/8/8/8/8/8 w - - 0 1",BLACK="8/8/8/8/8/5kq1/8/7K b - - 0 1";
 @Test public void verifiesWhiteMate(){assertTrue(MateProof.inOne(WHITE,"g6g7"));}
 @Test public void verifiesBlackMate(){assertTrue(MateProof.inOne(BLACK,"g3g2"));}
 @Test public void stalemateIsNotMate(){assertFalse(MateProof.inOne(WHITE,"g6f7"));}
 @Test public void malformedAndIllegalMovesAreNotMate(){assertFalse(MateProof.inOne("fake","g6g7"));assertFalse(MateProof.inOne(WHITE,"g6e5"));}
}
