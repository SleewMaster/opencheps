package com.opencheps.vision;

/** Conservative geometric mask of opaque arrow paint. Coordinates are screen pixels.
 * Translucent target fill is intentionally preserved: it still contains board evidence. */
public final class ArrowOcclusion implements PixelMask {
    public static final class Mark {
        public final float sx,sy,ex,ey,cell;
        public final boolean primary,label;
        public Mark(float sx,float sy,float ex,float ey,float cell,boolean primary,boolean label) {
            this.sx=sx;this.sy=sy;this.ex=ex;this.ey=ey;this.cell=cell;this.primary=primary;this.label=label;
        }
    }
    private final Mark[] marks;
    public ArrowOcclusion(Mark... marks){this.marks=marks.clone();}
    @Override public boolean excludes(float x,float y) {
        for(Mark a:marks)if(excludes(a,x,y))return true;
        return false;
    }
    private static boolean excludes(Mark a,float x,float y) {
        float cell=a.cell,dx=a.ex-a.sx,dy=a.ey-a.sy,length=(float)Math.hypot(dx,dy);
        if(cell<=0||length<1)return false;
        float ux=dx/length,uy=dy/length;
        if(Math.hypot(x-a.sx,y-a.sy)<=cell*.20f)return true;
        float along=(x-a.ex)*ux+(y-a.ey)*uy;
        float across=Math.abs(-(x-a.ex)*uy+(y-a.ey)*ux);
        float head=cell*(a.primary?.33f:.27f),wing=cell*(a.primary?.185f:.14f);
        if(along>=-head-cell*.04f&&along<=cell*.04f
                &&across<=wing*Math.max(0,-along)/head+cell*.055f)return true;
        float startX=a.sx+ux*cell*.16f,startY=a.sy+uy*cell*.16f;
        float neckX=a.ex-ux*head,neckY=a.ey-uy*head;
        if(segmentDistance(x,y,startX,startY,neckX,neckY)<cell*(a.primary?.10f:.08f))return true;
        // Destination frame, including anti-aliased edges.
        float ax=Math.abs(x-a.ex),ay=Math.abs(y-a.ey),edge=cell*.43f;
        if(ax<cell*.49f&&ay<cell*.49f
                &&(Math.abs(ax-edge)<cell*.065f||Math.abs(ay-edge)<cell*.065f))return true;
        if(a.label&&Math.abs(x-a.ex)<cell*.39f&&y>=a.ey+cell*.20f&&y<=a.ey+cell*.50f)return true;
        return false;
    }
    private static float segmentDistance(float x,float y,float ax,float ay,float bx,float by) {
        float dx=bx-ax,dy=by-ay,den=dx*dx+dy*dy;
        float t=den==0?0:Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/den));
        return (float)Math.hypot(x-ax-t*dx,y-ay-t*dy);
    }
}
