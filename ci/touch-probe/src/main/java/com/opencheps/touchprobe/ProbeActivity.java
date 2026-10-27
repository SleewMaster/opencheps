package com.opencheps.touchprobe;
import android.app.Activity;import android.os.Bundle;import android.widget.*;import android.view.Gravity;
public class ProbeActivity extends Activity {
 int taps;
 @Override public void onCreate(Bundle b){super.onCreate(b);FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff25354a);setContentView(root);
 Button button=new Button(this);button.setText("TAPS: 0");button.setOnClickListener(v->button.setText("TAPS: "+(++taps)));
 root.addView(button,new FrameLayout.LayoutParams(-1,140,Gravity.CENTER));}
}
