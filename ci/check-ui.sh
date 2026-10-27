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
