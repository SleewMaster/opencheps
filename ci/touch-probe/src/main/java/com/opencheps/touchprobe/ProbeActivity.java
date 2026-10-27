package com.opencheps.touchprobe;
import android.app.Activity;import android.os.Bundle;import android.widget.*;import android.view.Gravity;
public class ProbeActivity extends Activity {
 int taps;
 @Override public void onCreate(Bundle b){super.onCreate(b);taps=getPreferences(0).getInt("taps",0);FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff25354a);setContentView(root);
 Button button=new Button(this);button.setText("TAPS: "+taps);button.setFilterTouchesWhenObscured(false);
 button.setOnTouchListener((v,event)->{android.util.Log.i("ProbeEvents","touch "+event.getActionMasked()+" flags="+event.getFlags());return false;});
 button.setOnClickListener(v->{getPreferences(0).edit().putInt("taps",++taps).apply();button.setText("TAPS: "+taps);});
 root.addView(button,new FrameLayout.LayoutParams(-1,140,Gravity.CENTER));}
}
