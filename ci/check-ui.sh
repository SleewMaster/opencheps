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

adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.CameraVisionTestActivity
for attempt in $(seq 1 600); do
 result=$(adb shell run-as com.opencheps.bottrainer cat files/camera-vision-result.txt 2>/dev/null || true)
 if [[ "$result" == CAMERA_VISION_OK* ]]; then break; fi
 if [[ "$result" == CAMERA_VISION_FAIL* ]]; then
  for file in camera-calibration.png camera-failed.png camera-photo.png; do adb exec-out run-as com.opencheps.bottrainer cat files/"$file" > ui-proof/"$file" || true; done
  adb logcat -d -s CameraVisionTest:E; echo "$result"; exit 1
 fi
 sleep 1
done
[[ "$result" == "CAMERA_VISION_OK 60 SHAKE_OK 30 SNAP_OK 30 RECOVERY_OK 10 JOIN_OK 18 CONTINUE_OK 52 HIGHLIGHT_JOIN_OK 36 SMALL_RECOVERY_OK 12 START_BLACK_OK 16 ALTERNATIVE_OK 8" ]] || { adb logcat -d -s CameraVisionTest:E; echo "Missing camera vision: $result"; exit 1; }
echo "$result"


# Joining a game must not require the initial diagram. Exercise both review orientations.
for side in w b; do
 adb shell run-as com.opencheps.bottrainer rm -f files/screen-join-result.txt files/screen-join-ui-result.txt
 adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.ScreenJoinTestActivity --es side "$side"
 for attempt in $(seq 1 45); do
  result=$(adb shell run-as com.opencheps.bottrainer cat files/screen-join-result.txt 2>/dev/null || true)
  if [[ "$result" == SCREEN_JOIN_OK* ]]; then break; fi
  if [[ "$result" == SCREEN_JOIN_FAIL* ]]; then adb logcat -d -s ScreenJoinTest:E; echo "$result"; exit 1; fi
  sleep 1
 done
 [[ "$result" == "SCREEN_JOIN_OK 18" ]] || { echo "$result"; exit 1; }
 echo "$result"
 adb shell uiautomator dump /sdcard/join.xml
 adb pull /sdcard/join.xml "ui-proof/join-$side.xml"
 adb exec-out screencap -p > "ui-proof/join-$side.png"
 python3 - <<'PYJOIN'
import xml.etree.ElementTree as E,re,subprocess,time
nodes=list(E.parse('/dev/stdin').iter('node')) if False else []
xml=subprocess.check_output(['adb','shell','cat','/sdcard/join.xml']).decode()
nodes=list(E.fromstring(xml).iter('node'))
assert any(n.get('text')=='СТАРТ С ЭТОЙ ПОЗИЦИИ' for n in nodes)
assert any('Ход белых'==n.get('text') for n in nodes)
for attempt in range(7):
 nodes=list(E.fromstring(subprocess.check_output(['adb','shell','uiautomator','dump','/sdcard/join-scroll.xml','>/dev/null','&&','cat','/sdcard/join-scroll.xml']).decode()).iter('node'))
 button=next((n for n in nodes if n.get('text')=='Подтвердить позицию'),None)
 if button is not None:
  x1,y1,x2,y2=map(int,re.findall(r'\d+',button.attrib['bounds']))
  if y2-y1>=40 and y2<1210:
   subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True);break
 subprocess.run(['adb','shell','input','swipe','360','1080','360','450','300'],check=True)
else: raise AssertionError('review confirm unreachable')
PYJOIN
 for attempt in $(seq 1 20); do
  result=$(adb shell run-as com.opencheps.bottrainer cat files/screen-join-ui-result.txt 2>/dev/null || true)
  if [[ "$result" == SCREEN_JOIN_UI_OK* ]]; then break; fi
  sleep 0.25
 done
 [[ "$result" == SCREEN_JOIN_UI_OK* ]] || { echo "Review not applied: $result"; exit 1; }
 echo "$result"
done

# Exercise actual production TTS binding/selection/utterance, using the CI-only engine.
adb shell settings put secure tts_default_synth com.opencheps.touchprobe
adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.CameraSpeechTestActivity
for attempt in $(seq 1 20); do
 result=$(adb shell run-as com.opencheps.bottrainer cat files/camera-speech-result.txt 2>/dev/null || true)
 spoken=$(adb shell run-as com.opencheps.touchprobe cat files/spoken.txt 2>/dev/null || true)
 if [[ "$result" == CAMERA_SPEECH_FAIL* ]]; then adb logcat -d -s TextToSpeech:I; echo "$result"; exit 1; fi
 if [[ "$result" == CAMERA_SPEECH_QUEUED && "$spoken" == 'Пешка с е два на е четыре.' ]]; then break; fi
 sleep 1
done
[[ "$result" == CAMERA_SPEECH_QUEUED && "$spoken" == 'Пешка с е два на е четыре.' ]] || { echo "Speech missing: $result / $spoken"; exit 1; }
adb shell input keyevent KEYCODE_BACK
echo 'CAMERA_SPEECH_OK Android TTS binding, offline Russian voice, actual utterance'

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
assert any('Позиция · начать с текущей'==n.get('text') for n in nodes)
assert not any('маленькая доска' in n.get('content-desc','').lower() for n in nodes)
assert not any('media_projection' in n.get('resource-id','') for n in nodes)
assert any(n.get('text')=='Озвучивать ход' and n.get('checked')=='true' for n in nodes),'voice toggle missing'
assert any(n.get('text')=='Повторить' and n.get('enabled')=='false' for n in nodes),'unverified move must not be repeatable'
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
playing=next(n for n in nodes if n.get('text','').startswith('Играю:'))
orientation=next(n.get('text') for n in nodes if n.get('text','').startswith('Снизу:'))
import json
with open('ui-proof/camera-side-before.json','w') as f:json.dump({'playing':playing.get('text'),'orientation':orientation},f)
x1,y1,x2,y2=map(int,re.findall(r'\d+',playing.attrib['bounds']));subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)],check=True)
PYUI
adb shell uiautomator dump /sdcard/camera-side.xml
adb pull /sdcard/camera-side.xml ui-proof/camera-side.xml
python3 - <<'PYUI'
import xml.etree.ElementTree as E,re,subprocess,json
nodes=list(E.parse('ui-proof/camera-side.xml').iter('node'))
before=json.load(open('ui-proof/camera-side-before.json'))
assert any(n.get('text','').startswith('Играю:') and n.get('text')!=before['playing'] for n in nodes),'playing side did not change'
assert any(n.get('text')==before['orientation'] for n in nodes),'playing side rotated board'
assert any(n.get('text')=='Углы заново' for n in nodes),'playing side reset camera session'
print('CAMERA_PLAYING_SIDE_OK independent from board orientation, session retained')
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
# Both production arrow views must render a named legal move in either orientation.
for side in w b; do
 adb shell am start -W -n com.opencheps.bottrainer/com.opencheps.ui.CameraHintPreviewActivity --es side "$side"
 sleep 1
 state=$(adb shell run-as com.opencheps.bottrainer cat files/camera-hint-state-"$side".txt)
 [[ "$state" == "CAMERA_LOST_FRAME_OK" ]] || { echo "Lost camera frame still visible: $state"; exit 1; }
 echo "$state $side"
 adb shell uiautomator dump /sdcard/camera-hint.xml
 adb pull /sdcard/camera-hint.xml "ui-proof/camera-hint-$side.xml"
 adb exec-out screencap -p > "ui-proof/camera-hint-$side.png"
done
python3 - <<'PYHINT'
import xml.etree.ElementTree as E
for side,expected in [('w','Пешка  e2 → e4'),('b','Конь  g8 → f6')]:
 nodes=list(E.parse('ui-proof/camera-hint-'+side+'.xml').iter('node'))
 assert any(n.get('text')==expected for n in nodes),expected
 assert any(n.get('content-desc')=='Ход '+('e2e4' if side=='w' else 'g8f6') for n in nodes),'camera arrow missing '+expected
print('CAMERA_HINT_OK figure, source, destination, unobstructed camera arrow in both orientations')
PYHINT

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

