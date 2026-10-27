#!/usr/bin/env bash
set -euo pipefail
mkdir -p ui-proof
adb shell wm size 720x1280
adb shell wm density 280
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.MainActivity
sleep 2
adb shell uiautomator dump /sdcard/main.xml
adb pull /sdcard/main.xml ui-proof/main.xml
adb exec-out screencap -p > ui-proof/main.png
# Check actual view hierarchy and exercise a preset with native UI events.
python3 - <<'PY'
import xml.etree.ElementTree as E,re,subprocess
root=E.parse('ui-proof/main.xml');nodes=list(root.iter('node'))
assert any('Твой следующий' in n.get('text','') for n in nodes)
cta=next(n for n in nodes if n.get('resource-id','').endswith('/btnStart'))
assert cta.get('enabled')=='true'
button=next(n for n in nodes if n.get('resource-id','').endswith('/modeDeep'))
x1,y1,x2,y2=map(int,re.findall(r'\d+',button.attrib['bounds']))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PY
adb shell uiautomator dump /sdcard/deep.xml
adb pull /sdcard/deep.xml ui-proof/deep.xml
python3 - <<'PY'
import xml.etree.ElementTree as E
nodes=E.parse('ui-proof/deep.xml').iter('node')
label=next(n.get('text') for n in nodes if n.get('resource-id','').endswith('/thinkLabel'))
assert '8,0' in label,label
print('Native UI preset verified:',label)
PY
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.ArrowPreviewActivity
sleep 2
adb exec-out screencap -p > ui-proof/arrows.png
# Regression: a different UID must receive touches under the full-screen overlay.
adb install -r ci/touch-probe/build/outputs/apk/debug/probe-debug.apk
adb shell appops set com.opencheps.bottrainer SYSTEM_ALERT_WINDOW allow
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.OverlayTestActivity --es mode overlay
sleep 1
adb shell am start -W -n com.opencheps.touchprobe/.ProbeActivity
adb shell uiautomator dump /sdcard/overlay.xml
adb pull /sdcard/overlay.xml ui-proof/overlay.xml
python3 - <<'PY'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/overlay.xml').iter('node'))
assert any(n.get('resource-id','').endswith('/panelStop') for n in nodes),'panel absent'
b=next(n for n in nodes if n.get('text')=='TAPS: 0');x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PY
adb shell uiautomator dump /sdcard/touch.xml
adb pull /sdcard/touch.xml ui-proof/touch.xml
adb exec-out screencap -p > ui-proof/overlay.png
python3 - <<'PY'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/touch.xml').iter('node'))
assert any(n.get('text')=='TAPS: 1' for n in nodes),'overlay blocked another app touch'
# No listener is attached by this test: X must work independently.
b=next(n for n in nodes if n.get('resource-id','').endswith('/panelStop'));x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PY
adb shell uiautomator dump /sdcard/closed.xml
adb pull /sdcard/closed.xml ui-proof/closed.xml
python3 - <<'PY'
import xml.etree.ElementTree as E
assert not any(n.get('resource-id','').endswith('/panelStop') for n in E.parse('ui-proof/closed.xml').iter('node')),'X did not remove overlay'
PY
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.OverlayTestActivity --es mode notification
sleep 1
adb shell uiautomator dump /sdcard/stopped.xml
adb pull /sdcard/stopped.xml ui-proof/stopped.xml
python3 - <<'PY'
import xml.etree.ElementTree as E
assert not any(n.get('resource-id','').endswith('/panelStop') for n in E.parse('ui-proof/stopped.xml').iter('node')),'Stop action left an orphan window'
PY
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.OverlayTestActivity --es mode select
sleep 1
adb shell uiautomator dump /sdcard/select.xml
adb pull /sdcard/select.xml ui-proof/select.xml
adb exec-out screencap -p > ui-proof/selection.png
python3 - <<'PY'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/select.xml').iter('node'));b=next(n for n in nodes if n.get('text')=='Применить');x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PY
adb shell uiautomator dump /sdcard/frame.xml
adb pull /sdcard/frame.xml ui-proof/frame.xml
python3 - <<'PY'
import xml.etree.ElementTree as E
assert any(n.get('text')=='FRAME_OK' for n in E.parse('ui-proof/frame.xml').iter('node')),'manual frame not applied'
PY
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.OverlayTestActivity --es mode vision
for attempt in $(seq 1 30); do
  result=$(adb shell run-as com.opencheps.bottrainer cat files/vision-result.txt 2>/dev/null || true)
  if [[ "$result" == VISION_OK* ]]; then break; fi
  if [[ "$result" == VISION_FAIL* ]]; then adb logcat -d -s NativeVisionCheck:E; echo "$result"; exit 1; fi
  sleep 1
done
[[ "$result" == "VISION_OK 20" ]] || { adb logcat -d -s NativeVisionCheck:E; echo "Missing vision result: $result"; exit 1; }
echo "$result"
adb exec-out screencap -p > ui-proof/vision.png
