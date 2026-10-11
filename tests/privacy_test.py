"""Narrow permissions, observation-only service and persistent-data boundary checks."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
a='{http://schemas.android.com/apk/res/android}'
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
assert {p.get(a+'name') for p in manifest.findall('uses-permission')}=={'android.permission.POST_NOTIFICATIONS','android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.RECEIVE_BOOT_COMPLETED'}
assert manifest.find('application').get(a+'allowBackup')=='false'
assert {p.get(a+'name') for p in manifest.findall('queries/package')}=={'com.instagram.android','com.google.android.youtube'}
service=ET.parse(root/'app/src/main/res/xml/probe_service.xml').getroot()
assert service.get(a+'isAccessibilityTool')=='false'
source=(root/'app/src/main/java/in/scrollcount/probe/ProbeService.java').read_text()
assert source.index('if(detector==null)')<source.index('event.getSource()')
walk=source[source.index('void walk('):source.index('void collect(')]
assert walk.index('n.isPassword()')<walk.index('n.getText()')
assert 'if(ProbeStore.verbose)' in walk
store=(root/'app/src/main/java/in/scrollcount/probe/ProbeStore.java').read_text()
assert 'verbose=on&&debug' in store
history=(root/'app/src/main/java/in/scrollcount/probe/HistoryStore.java').read_text()
for term in ['fingerprint','caption','creator','getText','getContentDescription']:
 assert term not in history,term
for file in (root/'app/src/main/java').rglob('*.java'):
 text=file.read_text()
 for term in ['performAction(', 'dispatchGesture(', 'MediaProjection','getEventText(']:assert term not in text,(file,term)
 if 'HttpURLConnection' in text:assert file.name=='FriendApi.java','network is confined to optional signaling'
 if file.name in ['ProbeService.java','HistoryStore.java','CountingEngine.java']:assert 'FriendApi' not in text and 'HttpURLConnection' not in text,'counting remains offline'
assert manifest.find('application').get(a+'usesCleartextTraffic')=='false'
api=(root/'app/src/main/java/in/scrollcount/probe/FriendApi.java').read_text()
assert 'setInstanceFollowRedirects(false)' in api and 'https' in api and '.workers.dev' in api
cipher=(root/'app/src/main/java/in/scrollcount/probe/FriendCipher.java').read_text()
assert 'AES/GCM/NoPadding' in cipher and 'updateAAD' in cipher
sync=(root/'app/src/main/java/in/scrollcount/probe/FriendSync.java').read_text()
assert 'storedSharing()' in sync and 'FriendCipher.encrypt' in sync
assert 'privateKey' not in sync[sync.index('JSONObject request='):sync.index('handler.post(()->{if(g!=generation)return;api.request')]
print('PASS: narrow permissions, no backup/control, offline counting and isolated opt-in HTTPS signaling, unsupported-app early return, editable/password exclusion and content-free storage')

job=manifest.find("application/service[@"+a+"name='.HourlyFriendJob']")
assert job.get(a+'permission')=='android.permission.BIND_JOB_SERVICE'
job_source=(root/'app/src/main/java/in/scrollcount/probe/HourlyFriendJob.java').read_text()
assert 'setPersisted(true)' in job_source and 'setRequiredNetworkType' in job_source
assert 'setOverrideDeadline' not in job_source and 'WakeLock' not in job_source

for resource,sections in [('backup_rules.xml',[None]),('data_extraction_rules.xml',['cloud-backup','device-transfer'])]:
 rules=ET.parse(root/'app/src/main/res/xml'/resource).getroot()
 for section in sections:
  node=rules if section is None else rules.find(section)
  assert {x.get('domain') for x in node.findall('exclude')}=={'root','file','database','sharedpref','external'}
  assert all(x.get('path')=='.' for x in node.findall('exclude'))
print('PASS: credentials and history excluded from cloud backup and Android device transfer')
