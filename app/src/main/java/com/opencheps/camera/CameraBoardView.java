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
    private int dragging=-1;
    private List<StockfishEngine.Pv> moves=Collections.emptyList();
    private Side perspective=Side.WHITE;
    public Runnable onSizeChanged;
    public CameraBoardView(Context c){super(c);setContentDescription("Четыре угла доски: перетащи маркеры к углам");}
    public float[] corners(){return corners.clone();}
    public boolean editing(){return editing;}
    public void setEditing(boolean value){editing=value;moves=Collections.emptyList();invalidate();}
    public void setMoves(List<StockfishEngine.Pv> values,Side side){moves=new ArrayList<>(values);perspective=side;invalidate();}
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
        super.onDraw(c);if(getWidth()==0)return;
        Path outline=new Path();outline.moveTo(corners[0],corners[1]);for(int i=1;i<4;i++)outline.lineTo(corners[i*2],corners[i*2+1]);outline.close();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(0xffb9f66e);c.drawPath(outline,paint);
        Matrix projection=new Matrix();boolean valid=CameraGeometry.valid(corners,getWidth(),getHeight());
        if(valid&&projection.setPolyToPoly(new float[]{0,0,640,0,640,640,0,640},0,corners,0,4)){
            c.save();c.concat(projection);c.clipRect(0,0,640,640);
            if(editing){paint.setColor(0x88b9f66e);paint.setStrokeWidth(1);for(int i=1;i<8;i++){c.drawLine(i*80,0,i*80,640,paint);c.drawLine(0,i*80,640,i*80,paint);}}
            else for(int i=moves.size()-1;i>=0;i--)drawMove(c,moves.get(i).uci,i);
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
    private void drawMove(Canvas c,String move,int index){
        if(!com.opencheps.engine.UciInfo.isMove(move))return;
        int ff=move.charAt(0)-'a',fr='8'-move.charAt(1),tf=move.charAt(2)-'a',tr='8'-move.charAt(3);
        if(perspective==Side.BLACK){ff=7-ff;fr=7-fr;tf=7-tf;tr=7-tr;}
        float sx=(ff+.5f)*80,sy=(fr+.5f)*80,ex=(tf+.5f)*80,ey=(tr+.5f)*80;
        float dx=ex-sx,dy=ey-sy,len=(float)Math.hypot(dx,dy);if(len<1)return;
        float ux=dx/len,uy=dy/len,px=-uy,py=ux;
        int color=new int[]{0xffb9f66e,0xff67d7ff,0xffffc566}[Math.min(index,2)];
        paint.setStyle(Paint.Style.STROKE);paint.setColor(color);paint.setStrokeWidth(index==0?3.5f:2);
        c.drawRoundRect(ex-34,ey-34,ex+34,ey+34,10,10,paint);
        float half=index==0?4:2.5f,neckX=ex-ux*25,neckY=ey-uy*25;
        Path path=new Path();path.moveTo(sx+ux*12+px*half,sy+uy*12+py*half);path.lineTo(neckX+px*half,neckY+py*half);path.lineTo(neckX+px*15,neckY+py*15);path.lineTo(ex,ey);path.lineTo(neckX-px*15,neckY-py*15);path.lineTo(neckX-px*half,neckY-py*half);path.lineTo(sx+ux*12-px*half,sy+uy*12-py*half);path.close();
        paint.setColor(0xff101925);paint.setStrokeWidth(5);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawPath(path,paint);
        paint.setColor(0xff101925);c.drawCircle(sx,sy,11,paint);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);c.drawCircle(sx,sy,11,paint);
        if(index==0){paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(15);paint.setTypeface(Typeface.DEFAULT_BOLD);float bx=ex-ux*26,by=ey-uy*26;paint.setColor(0xff101925);c.drawRoundRect(bx-17,by-10,bx+17,by+10,4,4,paint);paint.setColor(color);c.drawText(move.substring(2).toUpperCase(Locale.ROOT),bx,by+5,paint);}
    }
}
