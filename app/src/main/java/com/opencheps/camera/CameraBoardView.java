package com.opencheps.camera;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import com.opencheps.engine.StockfishEngine;
import com.opencheps.util.Side;
import java.util.*;

/** Corners and hints use the exact same preview-coordinate homography as recognition. */
public final class CameraBoardView extends View {
    private final Paint paint=new Paint(3);
    private final float[] corners=new float[8];
    private boolean editing=true;
    private boolean aligned;
    private int dragging=-1;
    private List<StockfishEngine.Pv> moves=Collections.emptyList();
    private Side perspective=Side.WHITE;
    public Runnable onSizeChanged;
    public CameraBoardView(Context c){super(c);setContentDescription("Четыре угла доски: перетащи маркеры к углам");}
    public float[] corners(){return corners.clone();}
    public void setTrackedCorners(float[] value){if(!editing&&CameraGeometry.valid(value,getWidth(),getHeight())){System.arraycopy(value,0,corners,0,8);aligned=true;invalidate();}}
    public void trackingLost(){aligned=false;moves=Collections.emptyList();setContentDescription("Доска потеряна · ищу сетку");invalidate();}
    public boolean editing(){return editing;}
    public void setEditing(boolean value){editing=value;aligned=false;moves=Collections.emptyList();invalidate();}
    public void setMoves(List<StockfishEngine.Pv> values,Side side){moves=new ArrayList<>(values);perspective=side;setContentDescription(moves.isEmpty()?"Доска камеры":"Ход "+moves.get(0).uci);invalidate();}
    public void clear(){moves=Collections.emptyList();invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        float side=Math.min(w*.78f,h*.78f),x=(w-side)/2,y=(h-side)/2;
        float[] p={x,y,x+side,y,x+side,y+side,x,y+side};System.arraycopy(p,0,corners,0,8);
        setEditing(true);if(onSizeChanged!=null)onSizeChanged.run();
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!editing)return false;
        float x=e.getX(),y=e.getY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                float best=64*getResources().getDisplayMetrics().density;
                for(int i=0;i<4;i++){float d=(float)Math.hypot(x-corners[i*2],y-corners[i*2+1]);if(d<best){best=d;dragging=i;}}
                return dragging>=0;
            case MotionEvent.ACTION_MOVE:
                if(dragging<0)return false;
                corners[dragging*2]=Math.max(0,Math.min(getWidth(),x));corners[dragging*2+1]=Math.max(0,Math.min(getHeight(),y));invalidate();return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:dragging=-1;performClick();return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(getWidth()==0||(!editing&&!aligned))return;
        Path outline=new Path();outline.moveTo(corners[0],corners[1]);for(int i=1;i<4;i++)outline.lineTo(corners[i*2],corners[i*2+1]);outline.close();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(0xffb9f66e);c.drawPath(outline,paint);
        Matrix projection=new Matrix();boolean valid=CameraGeometry.valid(corners,getWidth(),getHeight());
        if(valid&&projection.setPolyToPoly(new float[]{0,0,640,0,640,640,0,640},0,corners,0,4)){
            c.save();c.concat(projection);c.clipRect(0,0,640,640);
            if(editing){paint.setColor(0x88b9f66e);paint.setStrokeWidth(1);for(int i=1;i<8;i++){c.drawLine(i*80,0,i*80,640,paint);c.drawLine(0,i*80,640,i*80,paint);}}
            else for(int i=moves.size()-1;i>=0;i--)CameraMoveArrow.draw(c,paint,moves.get(i).uci,perspective,i);
            c.restore();
        }
        if(editing){
            float density=getResources().getDisplayMetrics().density;
            for(int i=0;i<4;i++){
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xff101925);c.drawCircle(corners[i*2],corners[i*2+1],16*density,paint);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);paint.setColor(valid?0xffb9f66e:0xffffb4c1);c.drawCircle(corners[i*2],corners[i*2+1],16*density,paint);
                paint.setStyle(Paint.Style.FILL);paint.setTextSize(14*density);paint.setTextAlign(Paint.Align.CENTER);c.drawText(""+(i+1),corners[i*2],corners[i*2+1]+5*density,paint);
            }
        }
    }
}
