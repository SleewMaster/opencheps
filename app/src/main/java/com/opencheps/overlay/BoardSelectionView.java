package com.opencheps.overlay;
import android.annotation.SuppressLint;
import android.content.Context;import android.graphics.*;import android.view.MotionEvent;import android.view.View;

/** A user-selected square board region; it never reads another app's internals. */
public class BoardSelectionView extends View {
    private final Paint p=new Paint(3);
    private final Rect suggestion;
    private final RectF box=new RectF();
    private float downX,downY;private RectF before;private int mode;
    public BoardSelectionView(Context c,Rect suggested){super(c);suggestion=suggested==null?null:new Rect(suggested);}
    private void initialize(){
        if(box.width()>0||getWidth()==0)return;
        int[] xy=new int[2];getLocationOnScreen(xy);
        if(suggestion!=null)box.set(suggestion.left-xy[0],suggestion.top-xy[1],suggestion.right-xy[0],suggestion.bottom-xy[1]);
        else {float side=Math.min(getWidth()*.94f,getHeight()*.72f);float left=(getWidth()-side)/2;float top=(getHeight()-side)/2;box.set(left,top,left+side,top+side);}
        constrain();
    }
    private void constrain(){
        float side=Math.max(120,Math.min(Math.min(getWidth(),getHeight()),box.width()));
        float x=Math.max(0,Math.min(getWidth()-side,box.left)),y=Math.max(0,Math.min(getHeight()-side,box.top));box.set(x,y,x+side,y+side);
    }
    @Override protected void onDraw(Canvas c){
        initialize();p.setColor(0xaa08101b);p.setStyle(Paint.Style.FILL);
        c.drawRect(0,0,getWidth(),box.top,p);c.drawRect(0,box.bottom,getWidth(),getHeight(),p);
        c.drawRect(0,box.top,box.left,box.bottom,p);c.drawRect(box.right,box.top,getWidth(),box.bottom,p);
        p.setColor(0xff67d7ff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawRect(box,p);
        p.setColor(0x5567d7ff);p.setStrokeWidth(1);
        for(int i=1;i<8;i++){float d=box.width()*i/8;c.drawLine(box.left+d,box.top,box.left+d,box.bottom,p);c.drawLine(box.left,box.top+d,box.right,box.top+d,p);}
        p.setStyle(Paint.Style.FILL);p.setColor(0xffb9f66e);c.drawCircle(box.left,box.top,18,p);c.drawCircle(box.right,box.bottom,18,p);
        p.setColor(0xffeaf2ff);p.setTextSize(14*getResources().getDisplayMetrics().scaledDensity);
        c.drawText("Совмести рамку с 64 клетками",20,Math.max(60,box.top-60),p);
        p.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);c.drawText("Тяни углы для размера, середину для перемещения",20,Math.max(90,box.top-30),p);
    }
    @SuppressLint("ClickableViewAccessibility") @Override public boolean onTouchEvent(MotionEvent e){
        initialize();float x=e.getX(),y=e.getY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=x;downY=y;before=new RectF(box);
                float radius=48*getResources().getDisplayMetrics().density;
                mode=Math.hypot(x-box.left,y-box.top)<radius?1:Math.hypot(x-box.right,y-box.bottom)<radius?2:3;return true;
            case MotionEvent.ACTION_MOVE:
                float dx=x-downX,dy=y-downY;
                if(mode==1){float d=(dx+dy)/2,side=Math.max(120,before.width()-d);box.set(before.right-side,before.bottom-side,before.right,before.bottom);}
                else if(mode==2){float side=Math.max(120,before.width()+(dx+dy)/2);box.set(before.left,before.top,before.left+side,before.top+side);}
                else box.set(before.left+dx,before.top+dy,before.right+dx,before.bottom+dy);
                constrain();invalidate();return true;
            case MotionEvent.ACTION_UP:performClick();return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    public Rect screenSelection(){initialize();int[] xy=new int[2];getLocationOnScreen(xy);return new Rect(Math.round(box.left)+xy[0],Math.round(box.top)+xy[1],Math.round(box.right)+xy[0],Math.round(box.bottom)+xy[1]);}
}
