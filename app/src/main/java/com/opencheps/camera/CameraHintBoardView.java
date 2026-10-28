package com.opencheps.camera;
import android.content.Context;import android.graphics.*;import android.view.View;
import com.opencheps.vision.ChessPosition;import com.opencheps.util.Side;

/** A stable, upright miniature board on the phone, independent of camera shake and perspective. */
public final class CameraHintBoardView extends View {
    private final Paint paint=new Paint(3);
    private char[][] board;private String move;private Side perspective=Side.WHITE;
    public CameraHintBoardView(Context context){super(context);setVisibility(INVISIBLE);setContentDescription("Доска с подсказкой хода");}
    public void show(ChessPosition position,String move,Side side){if(position==null)return;board=ChessPosition.copy(position.board);this.move=move;perspective=side;setVisibility(VISIBLE);setContentDescription(MoveAnnouncement.label(CameraBoard.fen(position),move));invalidate();}
    public void clear(){board=null;move=null;setVisibility(INVISIBLE);invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(board==null)return;
        float size=Math.min(getWidth(),getHeight());paint.setStyle(Paint.Style.FILL);paint.setColor(0xff0b121e);canvas.drawRoundRect(0,0,size,size,12,12,paint);
        canvas.save();canvas.translate(size*.045f,size*.045f);canvas.scale(size*.91f/640,size*.91f/640);
        String keys="KQRBNPkqrbnp",glyphs="♔♕♖♗♘♙♚♛♜♝♞♟";
        for(int r=0;r<8;r++)for(int f=0;f<8;f++){
            paint.setColor(((r+f)&1)==0?0xffeeeed2:0xff769656);canvas.drawRect(f*80,r*80,(f+1)*80,(r+1)*80,paint);
            char piece=perspective==Side.BLACK?board[7-r][7-f]:board[r][f];int at=keys.indexOf(piece);
            if(at>=0){paint.setTypeface(Typeface.DEFAULT);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(65);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);paint.setColor(at<6?0xff17212e:0xfffafafa);canvas.drawText(glyphs.substring(at,at+1),(f+.5f)*80,(r+.79f)*80,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(at<6?0xfffafafa:0xff17212e);canvas.drawText(glyphs.substring(at,at+1),(f+.5f)*80,(r+.79f)*80,paint);}
        }
        CameraMoveArrow.draw(canvas,paint,move,perspective,0);canvas.restore();
    }
}
