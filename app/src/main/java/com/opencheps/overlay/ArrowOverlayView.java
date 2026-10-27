package com.opencheps.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class ArrowOverlayView extends View {

    public static class Arrow {
        public final float sx, sy, ex, ey;
        public final int color;
        public final float widthPx;
        public final String label;
        public Arrow(float sx, float sy, float ex, float ey,
                     int color, float widthPx, String label) {
            this.sx = sx; this.sy = sy; this.ex = ex; this.ey = ey;
            this.color = color; this.widthPx = widthPx; this.label = label;
        }
    }

    private final List<Arrow> arrows = new ArrayList<>();
    private Rect boardBounds;
    private int boardFrameColor = 0x6600C853;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  headPath = new Path();

    public ArrowOverlayView(Context ctx) {
        super(ctx);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        shadow.setStyle(Paint.Style.STROKE);
        shadow.setStrokeCap(Paint.Cap.ROUND);
        shadow.setColor(0x99000000);
        head.setStyle(Paint.Style.FILL);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(2f);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(30f);
        label.setFakeBoldText(true);
        labelBg.setStyle(Paint.Style.FILL);
        labelBg.setColor(0xCC000000);
    }

    // ScreenCaptureService invokes these methods from a worker thread. All
    // mutations must run on the View/UI thread to avoid ConcurrentModification
    // while onDraw iterates over arrows.
    public void setArrows(List<Arrow> a) {
        final List<Arrow> snapshot = a == null ? new ArrayList<>() : new ArrayList<>(a);
        post(() -> {
            arrows.clear();
            arrows.addAll(snapshot);
            invalidate();
        });
    }

    public void setBoardBounds(Rect r) {
        final Rect snapshot = r == null ? null : new Rect(r);
        post(() -> {
            boardBounds = snapshot;
            invalidate();
        });
    }

    public void setBoardFrameColor(int c) {
        post(() -> {
            boardFrameColor = c;
            invalidate();
        });
    }

    public void clearArrowsOnly() {
        post(() -> {
            arrows.clear();
            invalidate();
        });
    }

    public void clearAll() {
        post(() -> {
            arrows.clear();
            boardBounds = null;
            invalidate();
        });
    }

    @Override protected void onDraw(Canvas c) {
        int[] origin=new int[2];getLocationOnScreen(origin);
        c.save();c.translate(-origin[0],-origin[1]);
        if(boardBounds!=null) {
            frame.setColor((boardFrameColor&0x00ffffff)|0x30000000);
            frame.setStrokeWidth(2f);c.drawRect(boardBounds,frame);
        }
        float cell=boardBounds==null?80f:boardBounds.width()/8f;
        // Alternative moves underneath the primary move.
        for(int i=arrows.size()-1;i>=0;i--) {
            Arrow a=arrows.get(i);boolean primary=i==0;
            float dx=a.ex-a.sx,dy=a.ey-a.sy,len=(float)Math.hypot(dx,dy);
            if(len<1)continue;
            float ux=dx/len,uy=dy/len,px=-uy,py=ux;
            float width=cell*(primary?.105f:.065f), half=width/2f;
            float headLength=cell*(primary?.33f:.27f),wing=cell*(primary?.185f:.14f);
            float startX=a.sx+ux*cell*.16f,startY=a.sy+uy*cell*.16f;
            float neckX=a.ex-ux*headLength,neckY=a.ey-uy*headLength;
            // The tip is exactly the centre of the destination square.
            headPath.reset();headPath.moveTo(startX+px*half,startY+py*half);
            headPath.lineTo(neckX+px*half,neckY+py*half);
            headPath.lineTo(neckX+px*wing,neckY+py*wing);
            headPath.lineTo(a.ex,a.ey);
            headPath.lineTo(neckX-px*wing,neckY-py*wing);
            headPath.lineTo(neckX-px*half,neckY-py*half);
            headPath.lineTo(startX-px*half,startY-py*half);headPath.close();
            head.setColor((a.color&0x00ffffff)|(primary?0x28000000:0x14000000));
            float inset=cell*.07f,radius=cell*.12f;
            c.drawRoundRect(a.ex-cell/2+inset,a.ey-cell/2+inset,
                    a.ex+cell/2-inset,a.ey+cell/2-inset,radius,radius,head);
            stroke.setColor(a.color);stroke.setStrokeWidth(cell*(primary?.042f:.024f));
            c.drawRoundRect(a.ex-cell/2+inset,a.ey-cell/2+inset,
                    a.ex+cell/2-inset,a.ey+cell/2-inset,radius,radius,stroke);
            shadow.setStrokeWidth(cell*.055f);shadow.setStrokeJoin(Paint.Join.ROUND);shadow.setColor(0xE0101925);
            c.drawPath(headPath,shadow);head.setColor(a.color);c.drawPath(headPath,head);
            head.setColor(0xED101925);c.drawCircle(a.sx,a.sy,cell*.14f,head);
            stroke.setColor(a.color);stroke.setStrokeWidth(cell*.045f);c.drawCircle(a.sx,a.sy,cell*.14f,stroke);
            head.setColor(a.color);c.drawCircle(a.sx,a.sy,cell*.047f,head);
            if(primary&&a.label!=null) {
                String destination=a.label.contains("→")?a.label.substring(a.label.indexOf('→')+1):a.label;
                destination=destination.toUpperCase(java.util.Locale.ROOT);
                label.setTextSize(cell*.17f);label.setColor(a.color);
                float tw=label.measureText(destination),pad=cell*.06f;
                float bx=a.ex-tw/2-pad,by=a.ey+cell*.24f;
                labelBg.setColor(0xF0101925);
                c.drawRoundRect(bx,by,bx+tw+2*pad,by+cell*.23f,cell*.05f,cell*.05f,labelBg);
                c.drawText(destination,a.ex-tw/2,by+cell*.17f,label);
            }
        }
        c.restore();
    }
}
