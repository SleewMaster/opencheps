package com.opencheps;
import com.opencheps.camera.CameraAdviceGuard;
import org.junit.Test;import static org.junit.Assert.*;
public class CameraAdviceGuardTest {
    @Test public void holdsOneSoftFailure(){CameraAdviceGuard g=new CameraAdviceGuard();g.verified(1000);assertTrue(g.failure(1220,true,true,false));}
    @Test public void neverKeepsDetectedOldMove(){CameraAdviceGuard g=new CameraAdviceGuard();g.verified(1000);assertFalse(g.failure(1100,true,true,true));}
    @Test public void neverKeepsUntrackedArrows(){CameraAdviceGuard g=new CameraAdviceGuard();g.verified(1000);assertFalse(g.failure(1100,false,true,false));}
    @Test public void hidesStrongBlurImmediately(){CameraAdviceGuard g=new CameraAdviceGuard();g.verified(1000);assertFalse(g.failure(1100,true,false,false));}
    @Test public void neverHoldsTwice(){CameraAdviceGuard g=new CameraAdviceGuard();g.verified(1000);g.failure(1100,true,true,false);assertFalse(g.failure(1200,true,true,false));}
    @Test public void expiresEvenWithoutNewFrames(){CameraAdviceGuard g=new CameraAdviceGuard();g.verified(1000);assertEquals(230,g.remaining(1220));assertEquals(0,g.remaining(1450));assertFalse(g.failure(1451,true,true,false));}
    @Test public void resetCannotRestoreOldAdvice(){CameraAdviceGuard g=new CameraAdviceGuard();g.verified(1000);g.reset();assertFalse(g.failure(1100,true,true,false));}
}
