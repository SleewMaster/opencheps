package com.opencheps.overlay;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.opencheps.util.Side;
import com.opencheps.vision.*;

/** Review a captured diagram and supply the facts a screenshot cannot reveal. */
public final class PositionSetupView extends ScrollView {
    private char[][] screen;
    private final boolean[] uncertain;
    private Side perspective,turn;
    private int selected=-1,halfmove=0,fullmove=1;
    private final TextView message;
    private final Button bottom,white,black;
    private final CheckBox[] rights=new CheckBox[4];
    private final EditText ep,fen;
    private final BoardEditor board;
    public PositionSetupView(Context context,char[][] screen,boolean[] uncertain,Side perspective,Side turn,
                             java.util.function.Consumer<PositionSeed> apply,Runnable cancel){
        super(context);this.screen=ChessPosition.copy(screen);this.uncertain=uncertain.clone();this.perspective=perspective;this.turn=turn;
        setBackgroundColor(0xff0b121e);setFillViewport(true);
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(12),dp(18),dp(24));addView(body);
        text(body,"СТАРТ С ЭТОЙ ПОЗИЦИИ",20,0xffeaf2ff);
        text(body,"Проверь фигуры. Нажми клетку, чтобы исправить. Жёлтым отмечено сомнение распознавания.",13,0xffa4b7d1);
        board=new BoardEditor(context);body.addView(board,new LinearLayout.LayoutParams(-1,dp(280)));
        LinearLayout palette=row(body);
        for(char piece:".PNBRQK".toCharArray())pieceButton(palette,piece);
        palette=row(body);for(char piece:".pnbrqk".toCharArray())pieceButton(palette,piece);
        bottom=button(body,"",()->{this.perspective=ChessPosition.opposite(this.perspective);refresh();});
        text(body,"Чей ход сейчас?",15,0xffeaf2ff);LinearLayout turns=row(body);
        white=button(turns,"Ход белых",()->{this.turn=Side.WHITE;refresh();});black=button(turns,"Ход чёрных",()->{this.turn=Side.BLACK;refresh();});
        text(body,"Рокировки ещё разрешены? Отметь только известные права. По расположению фигур их определить нельзя.",12,0xffa4b7d1);
        LinearLayout castles=row(body);int index=0;for(String title:new String[]{"Б. O-O","Б. O-O-O","Ч. O-O","Ч. O-O-O"}){
            CheckBox c=new CheckBox(context);c.setText(title);c.setTextColor(0xffd8e6f7);c.setTextSize(10);rights[index++]=c;castles.addView(c,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        ep=edit(body,"На проходе: − или поле после двойного хода пешки");ep.setText("-");ep.setSingleLine(true);
        fen=edit(body,"Или вставь FEN позиции");fen.setSingleLine(true);
        button(body,"Загрузить FEN",()->{
            PositionSeed seed=PositionSeed.parse(fen.getText().toString(),this.perspective);
            if(seed==null){showError("Некорректный FEN. Проверь фигуры, очередь хода и права.");return;}
            this.screen=this.perspective==Side.WHITE?ChessPosition.copy(seed.position.board):FenBuilder.flip(seed.position.board);
            this.turn=seed.position.turn;halfmove=seed.halfmove;fullmove=seed.fullmove;ep.setText(seed.position.ep);
            for(int i=0;i<4;i++)rights[i].setChecked(seed.position.rights.indexOf("KQkq".charAt(i))>=0);
            java.util.Arrays.fill(this.uncertain,false);refresh();
        });
        message=text(body,"После подтверждения проверю доску ещё раз. Если партия продолжается, дождись неподвижной позиции.",12,0xffa4b7d1);
        button(body,"Подтвердить позицию",()->{
            StringBuilder r=new StringBuilder();for(int i=0;i<4;i++)if(rights[i].isChecked())r.append("KQkq".charAt(i));
            PositionSeed seed=new PositionSeed(canonical(),this.turn,this.perspective,r.length()==0?"-":r.toString(),ep.getText().toString().trim(),halfmove,fullmove);
            String error=seed.error();if(error!=null){showError(error);return;}apply.accept(seed);
        });
        button(body,"Отмена",cancel);refresh();
        setFocusableInTouchMode(true);requestFocus();setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){cancel.run();return true;}return false;});
    }
    private char[][] canonical(){return perspective==Side.WHITE?screen:FenBuilder.flip(screen);}
    private void refresh(){
        bottom.setText("Снизу: "+(perspective==Side.WHITE?"белые":"чёрные")+"  ↻");
        white.setTextColor(turn==Side.WHITE?0xff90d44e:0xffa4b7d1);black.setTextColor(turn==Side.BLACK?0xff90d44e:0xffa4b7d1);
        char[][] b=canonical();for(int i=0;i<4;i++){boolean possible=PositionSeed.canHaveRight(b,"KQkq".charAt(i));rights[i].setEnabled(possible);if(!possible)rights[i].setChecked(false);}
        board.invalidate();
    }
    private void showError(String s){message.setText(s);message.setTextColor(0xffffb4c1);post(()->smoothScrollTo(0,message.getTop()));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout row(LinearLayout body){LinearLayout row=new LinearLayout(getContext());body.addView(row);return row;}
    private TextView text(LinearLayout parent,String s,int size,int color){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(6),0,dp(6));parent.addView(v);return v;}
    private EditText edit(LinearLayout body,String hint){EditText e=new EditText(getContext());e.setTextColor(0xffeaf2ff);e.setHintTextColor(0xff91a9c8);e.setTextSize(13);e.setHint(hint);body.addView(e);return e;}
    private Button button(LinearLayout parent,String label,Runnable action){Button b=new Button(getContext());b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setOnClickListener(v->action.run());
        parent.addView(b,new LinearLayout.LayoutParams(parent.getOrientation()==LinearLayout.HORIZONTAL?0:-1,dp(48),parent.getOrientation()==LinearLayout.HORIZONTAL?1:0));return b;}
    private void pieceButton(LinearLayout parent,char piece){Button b=button(parent,glyph(piece),()->{if(selected>=0){screen[selected/8][selected%8]=piece;uncertain[selected]=false;refresh();}});b.setTextSize(24);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setContentDescription("Поставить "+piece);}
    private static String glyph(char piece){int i="KQRBNPkqrbnp".indexOf(piece);return i<0?"×":"♔♕♖♗♘♙♚♛♜♝♞♟".substring(i,i+1);}
    private final class BoardEditor extends View {
        private final Paint p=new Paint(3);float ox,cell;
        BoardEditor(Context c){super(c);setContentDescription("Распознанная позиция. Нажми клетку для исправления.");}
        @Override protected void onDraw(Canvas c){cell=Math.min(getWidth(),getHeight())/8f;ox=(getWidth()-cell*8)/2;
            for(int r=0;r<8;r++)for(int f=0;f<8;f++){
                int i=r*8+f;float x=ox+f*cell,y=r*cell;p.setStyle(Paint.Style.FILL);p.setColor(((r+f)&1)==0?0xffeeeed2:0xff769656);c.drawRect(x,y,x+cell,y+cell,p);
                if(uncertain[i]||selected==i){p.setColor(selected==i?0xff67d7ff:0xffffc56b);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));c.drawRect(x+2,y+2,x+cell-2,y+cell-2,p);p.setStyle(Paint.Style.FILL);}
                char piece=screen[r][f];if(piece!='.'){
                    p.setTextAlign(Paint.Align.CENTER);p.setTextSize(cell*.78f);p.setColor(Character.isUpperCase(piece)?0xffffffff:0xff1b2430);
                    if(Character.isUpperCase(piece)){p.setColor(0xff263526);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));c.drawText(glyph(piece),x+cell*.5f,y+cell*.79f,p);p.setStyle(Paint.Style.FILL);p.setColor(0xffffffff);}
                    c.drawText(glyph(piece),x+cell*.5f,y+cell*.79f,p);
                }
                p.setTextAlign(Paint.Align.LEFT);p.setTextSize(cell*.19f);p.setColor(0xff263526);
                if(f==0)c.drawText(""+(perspective==Side.WHITE?8-r:r+1),x+2,y+cell*.19f,p);
                if(r==7)c.drawText(""+(char)('a'+(perspective==Side.WHITE?f:7-f)),x+cell*.8f,y+cell*.96f,p);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_UP){int f=(int)Math.floor((e.getX()-ox)/cell),r=(int)Math.floor(e.getY()/cell);if(f>=0&&f<8&&r>=0&&r<8){selected=r*8+f;invalidate();performClick();}return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
