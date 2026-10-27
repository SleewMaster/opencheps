package com.opencheps.vision;

/** Screen annotations to ignore while matching a captured chessboard. */
public interface PixelMask {
    boolean excludes(float x,float y);
    PixelMask NONE=(x,y)->false;
}
