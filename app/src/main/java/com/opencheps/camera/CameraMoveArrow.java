package com.opencheps.camera;
import android.graphics.*;import com.opencheps.util.Side;import java.util.Locale;
/** Shared, board-space arrow for camera and the stable 2D hint board. */
public final class CameraMoveArrow {
    public static void draw(Canvas c,Paint paint,String move,Side perspective,int index){
        if(!com.opencheps.engine.UciInfo.isMove(move))return;
        int ff=move.charAt(0)-'a',fr='8'-move.charAt(1),tf=move.charAt(2)-'a',tr='8'-move.charAt(3);
        if(perspective==Side.BLACK){ff=7-ff;fr=7-fr;tf=7-tf;tr=7-tr;}
        float sx=(ff+.5f)*80,sy=(fr+.5f)*80,ex=(tf+.5f)*80,ey=(tr+.5f)*80;
        float dx=ex-sx,dy=ey-sy,len=(float)Math.hypot(dx,dy);if(len<1)return;
        float ux=dx/len,uy=dy/len,px=-uy,py=ux;
        int color=new int[]{0xffb9f66e,0xff67d7ff,0xffffc566}[Math.min(index,2)];
        paint.setStyle(Paint.Style.FILL);paint.setColor(index==0?0x45b9f66e:0x2267d7ff);
        c.drawRoundRect(ex-37,ey-37,ex+37,ey+37,8,8,paint);
        paint.setColor(0x55ffc566);c.drawRoundRect(sx-37,sy-37,sx+37,sy+37,8,8,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setColor(color);paint.setStrokeWidth(index==0?5:3);
        c.drawRoundRect(ex-34,ey-34,ex+34,ey+34,10,10,paint);
        float half=index==0?9:4,neckX=ex-ux*33,neckY=ey-uy*33;
        Path path=new Path();path.moveTo(sx+ux*12+px*half,sy+uy*12+py*half);path.lineTo(neckX+px*half,neckY+py*half);path.lineTo(neckX+px*25,neckY+py*25);path.lineTo(ex,ey);path.lineTo(neckX-px*25,neckY-py*25);path.lineTo(neckX-px*half,neckY-py*half);path.lineTo(sx+ux*12-px*half,sy+uy*12-py*half);path.close();
        paint.setColor(0xff101925);paint.setStrokeWidth(6);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(color);c.drawPath(path,paint);
        paint.setColor(0xff101925);c.drawCircle(sx,sy,11,paint);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);c.drawCircle(sx,sy,11,paint);
        if(index==0){paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(18);paint.setTypeface(Typeface.DEFAULT_BOLD);float bx=(sx+ex)/2,by=(sy+ey)/2;paint.setColor(0xff101925);c.drawRoundRect(bx-49,by-13,bx+49,by+13,4,4,paint);paint.setColor(color);c.drawText(move.substring(0,2)+" → "+move.substring(2,4),bx,by+5,paint);}
    }
}
