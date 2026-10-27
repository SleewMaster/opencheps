#!/usr/bin/env bash
set -euo pipefail
mkdir -p ui-proof
trap 'adb logcat -d > ui-proof/logcat.txt || true' EXIT
adb shell wm size 720x1280
adb shell wm density 280
adb install -r -g app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb shell am start -W -f 0x14000000 -n com.opencheps.bottrainer/com.opencheps.ui.MainActivity
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
button=next((n for n in nodes if n.get('resource-id','').endswith('/modeDeep')),None)
if button is None:
    subprocess.run(['adb','shell','input','swipe','360','950','360','450','300'],check=True)
    subprocess.run(['adb','shell','uiautomator','dump','/sdcard/scrolled.xml'],check=True)
    xml=subprocess.check_output(['adb','shell','cat','/sdcard/scrolled.xml']).decode()
    button=next(n for n in E.fromstring(xml).iter('node') if n.get('resource-id','').endswith('/modeDeep'))
x1,y1,x2,y2=map(int,re.findall(r'\d+',button.attrib['bounds']))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PY
adb shell input swipe 360 1000 360 580 300
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
sleep 2
adb shell uiautomator dump /sdcard/overlay.xml
adb pull /sdcard/overlay.xml ui-proof/overlay.xml
python3 - <<'PY'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/overlay.xml').iter('node'))
# The CLI UI dump exposes only the active app on Android 14, not its overlay window.
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
PY
# Read the actual close button's screen coordinates from its attached native View.
adb shell am broadcast -n com.opencheps.bottrainer/com.opencheps.ui.OverlayStateReceiver
state=$(adb shell run-as com.opencheps.bottrainer cat files/overlay-state.txt)
[[ "$state" == OPEN* ]] || { adb logcat -d -s OC/Overlay:E; echo "Panel is not attached: $state"; exit 1; }
read -r status close_x close_y <<< "$state"
# There is no panel listener in this fixture. X must work independently.
adb shell input tap "$close_x" "$close_y"
adb shell uiautomator dump /sdcard/closed.xml
adb pull /sdcard/closed.xml ui-proof/closed.xml
adb shell am broadcast -n com.opencheps.bottrainer/com.opencheps.ui.OverlayStateReceiver
state=$(adb shell run-as com.opencheps.bottrainer cat files/overlay-state.txt)
[[ "$state" == CLOSED ]] || { echo "X left an attached panel: $state"; exit 1; }
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.OverlayTestActivity --es mode notification
sleep 1
adb shell uiautomator dump /sdcard/stopped.xml
adb pull /sdcard/stopped.xml ui-proof/stopped.xml
adb shell am broadcast -n com.opencheps.bottrainer/com.opencheps.ui.OverlayStateReceiver
state=$(adb shell run-as com.opencheps.bottrainer cat files/overlay-state.txt)
[[ "$state" == CLOSED ]] || { echo "Stop action left an attached panel: $state"; exit 1; }
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.OverlayTestActivity --es mode select
sleep 1
adb shell uiautomator dump /sdcard/select.xml
adb pull /sdcard/select.xml ui-proof/select.xml
adb exec-out screencap -p > ui-proof/selection.png
python3 - <<'PY'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/select.xml').iter('node'));b=next(n for n in nodes if n.get('text','').casefold()=='применить');x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']))
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
  if [[ "$result" == VISION_FAIL* ]]; then
    adb exec-out run-as com.opencheps.bottrainer cat files/vision-shot.png > ui-proof/vision-shot.png || true
    adb exec-out run-as com.opencheps.bottrainer cat files/vision-plain.png > ui-proof/vision-plain.png || true
    adb logcat -d -s NativeVisionCheck:E; echo "$result"; exit 1
  fi
  sleep 1
done
[[ "$result" == "VISION_OK 20" ]] || { adb logcat -d -s NativeVisionCheck:E; echo "Missing vision result: $result"; exit 1; }
echo "$result"
adb exec-out screencap -p > ui-proof/vision.png

# Camera mode launches from the actual source selector with no projection consent.
adb shell am start -W -f 0x14000000 -n com.opencheps.bottrainer/com.opencheps.ui.MainActivity
adb shell input swipe 360 350 360 1100 300
adb shell uiautomator dump /sdcard/sources.xml
adb pull /sdcard/sources.xml ui-proof/sources.xml
python3 - <<'PYUI'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/sources.xml').iter('node'))
b=next(n for n in nodes if n.get('resource-id','').endswith('/sourceCamera'))
x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
b=next(n for n in nodes if n.get('resource-id','').endswith('/btnStart'))
x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PYUI
for attempt in $(seq 1 20); do
  if adb logcat -d -s OC/Camera:I | grep -q CAMERA_STREAMING; then break; fi
  sleep 1
done
adb logcat -d -s OC/Camera:I | grep CAMERA_STREAMING
adb shell uiautomator dump /sdcard/camera.xml
adb pull /sdcard/camera.xml ui-proof/camera.xml
adb exec-out screencap -p > ui-proof/camera.png
python3 - <<'PYUI'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/camera.xml').iter('node'))
assert any('КАМЕРА  /  LIVE'==n.get('text') for n in nodes)
assert not any('media_projection' in n.get('resource-id','') for n in nodes)
def click(label):
 b=next(n for n in nodes if n.get('text')==label);x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']));subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
click('Начать')
PYUI
sleep 2
adb shell uiautomator dump /sdcard/calibrating.xml
adb pull /sdcard/calibrating.xml ui-proof/calibrating.xml
python3 - <<'PYUI'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/calibrating.xml').iter('node'))
assert any(n.get('text')=='Углы заново' for n in nodes),'camera Start did not apply corners'
b=next(n for n in nodes if n.get('text')=='Закрыть');x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']));subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PYUI
adb shell uiautomator dump /sdcard/camera-closed.xml
adb pull /sdcard/camera-closed.xml ui-proof/camera-closed.xml
python3 - <<'PYUI'
import xml.etree.ElementTree as E
nodes=list(E.parse('ui-proof/camera-closed.xml').iter('node'))
assert any(n.get('resource-id','').endswith('/sourceCamera') and n.get('checked')=='true' for n in nodes),'camera did not return to saved source choice'
assert not any(n.get('text')=='КАМЕРА  /  LIVE' for n in nodes)
PYUI
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.CameraVisionTestActivity
for attempt in $(seq 1 45); do
 result=$(adb shell run-as com.opencheps.bottrainer cat files/camera-vision-result.txt 2>/dev/null || true)
 if [[ "$result" == CAMERA_VISION_OK* ]]; then break; fi
 if [[ "$result" == CAMERA_VISION_FAIL* ]]; then adb logcat -d -s CameraVisionTest:E; echo "$result"; exit 1; fi
 sleep 1
done
[[ "$result" == "CAMERA_VISION_OK 60" ]] || { adb logcat -d -s CameraVisionTest:E; echo "Missing camera vision: $result"; exit 1; }
echo "$result"

# Denied permission must leave a usable camera screen and a working exit.
adb shell pm revoke com.opencheps.bottrainer android.permission.CAMERA
adb shell am start -W -f 0x14000000 -n com.opencheps.bottrainer/com.opencheps.ui.MainActivity
adb shell uiautomator dump /sdcard/camera-reenter.xml
adb pull /sdcard/camera-reenter.xml ui-proof/camera-reenter.xml
python3 - <<'PYUI'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/camera-reenter.xml').iter('node'))
b=next(n for n in nodes if n.get('resource-id','').endswith('/btnStart'));x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']));subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PYUI
adb shell uiautomator dump /sdcard/camera-permission.xml
adb pull /sdcard/camera-permission.xml ui-proof/camera-permission.xml
python3 - <<'PYUI'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/camera-permission.xml').iter('node'))
b=next(n for n in nodes if n.get('resource-id','').endswith('/permission_deny_button'));x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']));subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PYUI
adb shell uiautomator dump /sdcard/camera-denied.xml
adb pull /sdcard/camera-denied.xml ui-proof/camera-denied.xml
python3 - <<'PYUI'
import xml.etree.ElementTree as E,re,subprocess
nodes=list(E.parse('ui-proof/camera-denied.xml').iter('node'))
assert any('Камера не разрешена.' in n.get('text','') for n in nodes),'denial was not handled'
b=next(n for n in nodes if n.get('text')=='Закрыть');x1,y1,x2,y2=map(int,re.findall(r'\d+',b.attrib['bounds']));subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
print('CAMERA_UI_OK preview, corners, source persistence, close, permission denial')
PYUI
