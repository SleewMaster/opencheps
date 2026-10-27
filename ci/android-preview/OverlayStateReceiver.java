package com.opencheps.ui;
import android.content.*;import android.view.View;import java.io.*;import java.lang.reflect.Field;
import com.opencheps.overlay.*;import com.opencheps.R;
/** Debug-only observation of the separate non-focusable overlay window. */
public class OverlayStateReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  String value="CLOSED";
  try{
   FloatingPanel panel=OverlayService.panel();
   if(panel!=null){Field f=FloatingPanel.class.getDeclaredField("root");f.setAccessible(true);View root=(View)f.get(panel);
    if(root!=null&&root.isAttachedToWindow()&&root.getWidth()>0){View close=root.findViewById(R.id.panelStop);int[] xy=new int[2];close.getLocationOnScreen(xy);value="OPEN "+(xy[0]+close.getWidth()/2)+" "+(xy[1]+close.getHeight()/2);}
   }
   try(FileWriter w=new FileWriter(new File(context.getFilesDir(),"overlay-state.txt"))){w.write(value);}
  }catch(Exception e){throw new RuntimeException(e);}
 }
}
