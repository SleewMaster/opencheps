package com.opencheps.ui;
import android.app.Activity;import android.os.Bundle;import android.view.*;import android.widget.*;
import com.opencheps.overlay.PositionSetupView;import com.opencheps.camera.CameraOpening;import com.opencheps.util.Side;import com.opencheps.vision.*;import java.io.*;
/** Real Android editor: camera side-to-move starts unselected and must be chosen. */
public class CameraTurnReviewActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);
        PositionSetupView editor=new PositionSetupView(this,CameraOpening.initial().board,new boolean[64],Side.WHITE,null,seed->{
            if(seed.position.turn!=Side.BLACK)throw new AssertionError("wrong selected turn");
            write("CAMERA_TURN_REVIEW_OK explicit black turn, no default selection");
        },()->{});
        editor.setReference(NativeVisionCheck.render(640,640,0,CameraOpening.initial().board,new int[]{0xffeeeed2,0xff769656}));final boolean[] rescanned={false};editor.setRescanAction(()->rescanned[0]=true);setContentView(editor);
        editor.post(()->{Button photo=find(editor,"Фото доски"),rescan=find(editor,"Пересканировать");if(photo==null||rescan==null)throw new AssertionError("camera review tools missing");photo.performClick();if(find(editor,"Распознанные фигуры")==null)throw new AssertionError("photo toggle failed");find(editor,"Распознанные фигуры").performClick();rescan.performClick();if(!rescanned[0])throw new AssertionError("rescan callback not called");Button confirm=find(editor,"Подтвердить позицию"),black=find(editor,"Ход чёрных");if(confirm==null||black==null||confirm.isEnabled())throw new AssertionError("camera review preselects turn");black.performClick();if(!confirm.isEnabled())throw new AssertionError("explicit turn did not enable confirmation");confirm.performClick();});
    }
    private Button find(View view,String text){if(view instanceof Button&&text.contentEquals(((Button)view).getText()))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button b=find(group.getChildAt(i),text);if(b!=null)return b;}}return null;}
    private void write(String text){try(FileWriter writer=new FileWriter(new File(getFilesDir(),"camera-turn-review.txt"))){writer.write(text);}catch(IOException e){throw new RuntimeException(e);}}
}
