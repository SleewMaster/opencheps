#!/usr/bin/env bash
set -euo pipefail
# Production APKs have already been uploaded. The UI-only build runs on x86.
mv app/src/main/jniLibs /tmp/opencheps-preview-jni
mkdir -p app/src/main/jniLibs app/src/debug/java/com/opencheps/ui
cat > app/src/debug/AndroidManifest.xml <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name="com.opencheps.ui.ArrowPreviewActivity" android:exported="true"/></application></manifest>
XML
cat > app/src/debug/java/com/opencheps/ui/ArrowPreviewActivity.java <<'JAVA'
package com.opencheps.ui;
import android.app.Activity;import android.os.Bundle;import android.graphics.*;import android.view.*;import android.widget.FrameLayout;
import com.opencheps.overlay.ArrowOverlayView;import java.util.*;
public class ArrowPreviewActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff0b121e);setContentView(root);
  root.addView(new View(this){final Paint p=new Paint(3);protected void onDraw(Canvas c){float cell=getWidth()/8f,top=(getHeight()-getWidth())*.48f;
   p.setColor(0xffeaf2ff);p.setTextSize(cell*.28f);p.setTypeface(Typeface.create("sans-serif-medium",1));c.drawText("OPEN CHEPS  /  ЯСНЫЙ ХОД",cell*.25f,top-cell*.6f,p);
   for(int r=0;r<8;r++)for(int f=0;f<8;f++){p.setColor(((r+f)&1)==0?0xffeeeed2:0xff769656);c.drawRect(f*cell,top+r*cell,(f+1)*cell,top+(r+1)*cell,p);}
   p.setTypeface(Typeface.DEFAULT);p.setTextSize(cell*.7f);p.setTextAlign(Paint.Align.CENTER);
   String[] rows={"r...k..r","pppp.ppp","........","....p...","........","........","PPPPPPPP","RNBQKBNR"};String keys="KQRBNPkqrbnp",glyphs="♔♕♖♗♘♙♚♛♜♝♞♟";
   for(int r=0;r<8;r++)for(int f=0;f<8;f++){int i=keys.indexOf(rows[r].charAt(f));if(i<0)continue;p.setColor(i<6?0xfff8fafc:0xff18212b);c.drawText(glyphs.substring(i,i+1),(f+.5f)*cell,top+(r+.77f)*cell,p);}
   p.setTextAlign(Paint.Align.LEFT);p.setColor(0xff91a9c8);p.setTextSize(cell*.23f);c.drawText("Круг — откуда. Подсветка — куда.",cell*.25f,top+getWidth()+cell*.65f,p);
  }});
  ArrowOverlayView arrows=new ArrowOverlayView(this);root.addView(arrows);
  root.post(() -> {float cell=root.getWidth()/8f,top=(root.getHeight()-root.getWidth())*.48f;int[] xy=new int[2];root.getLocationOnScreen(xy);float ox=xy[0],oy=xy[1]+top;
   arrows.setBoardBounds(new Rect((int)ox,(int)oy,(int)(ox+cell*8),(int)(oy+cell*8)));
   arrows.setArrows(Arrays.asList(new ArrowOverlayView.Arrow(ox+4.5f*cell,oy+6.5f*cell,ox+4.5f*cell,oy+4.5f*cell,0xffb9f66e,8,"e2→e4"),new ArrowOverlayView.Arrow(ox+6.5f*cell,oy+7.5f*cell,ox+5.5f*cell,oy+5.5f*cell,0xff67d7ff,6,null)));
  });
 }
}
JAVA
./gradlew :app:assembleDebug --no-daemon
