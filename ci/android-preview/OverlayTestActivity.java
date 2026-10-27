package com.opencheps.ui;
import android.app.Activity;import android.os.*;import android.content.Intent;import android.graphics.*;import android.widget.*;import android.view.*;
import com.opencheps.capture.ScreenCaptureService;import com.opencheps.overlay.*;import java.util.*;
public class OverlayTestActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);ScreenCaptureService.stopSession(this);
 FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff0b121e);setContentView(root);TextView result=new TextView(this);result.setTextColor(-1);result.setTextSize(18);result.setGravity(Gravity.CENTER);root.addView(result,new FrameLayout.LayoutParams(-1,-1));
 String mode=getIntent().getStringExtra("mode");
 if("vision".equals(mode)){
  ArrowOverlayView arrows=new ArrowOverlayView(this);root.addView(arrows);
  root.post(()->NativeVisionCheck.start(this,arrows,root,result));return;
 }
 ScreenCaptureService.isRunning=true;startService(new Intent(this,OverlayService.class));
 new Handler(getMainLooper()).postDelayed(()->{
  int w=root.getWidth(),h=root.getHeight();Rect board=new Rect(0,h/3,w,h/3+w);OverlayService.setBoardBounds(board);
  OverlayService.pushArrows(Collections.singletonList(new ArrowOverlayView.Arrow(w*.56f,h*.60f,w*.56f,h*.45f,0xffb9f66e,10,"e2→e4")),board);
  if("notification".equals(mode))startService(new Intent(this,ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_STOP));
  if("select".equals(mode))OverlayService.selectBoard(board,chosen->{result.setText(chosen!=null&&chosen.width()==chosen.height()?"FRAME_OK":"FRAME_AUTO");ScreenCaptureService.stopSession(this);});
 },700);
 }
}
