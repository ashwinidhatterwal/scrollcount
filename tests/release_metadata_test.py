"""Metadata consistency, public policy gating and Android 8 API guard checks."""
import importlib.util, json, tempfile
from pathlib import Path
root = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('release_config', root/'tools/configure_release.py')
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
config = json.loads((root/'release-config.json').read_text())
for path, expected in module.generate(config).items():
    assert path.read_text() == expected, f'Stale generated file: {path}'
valid = dict(config, supportEmail='privacy@example.org')
outputs = module.generate(valid)
assert all('privacy@example.org' in value for value in outputs.values())
for invalid in ['bad', 'a@b', '<script>@bad.example']:
    try: module.generate(dict(config, supportEmail=invalid))
    except ValueError: pass
    else: raise AssertionError('Invalid email accepted')
try: module.generate(dict(config, appSigningCertificates=['not-a-certificate']))
except ValueError: pass
else: raise AssertionError('Invalid App Links certificate accepted')
ui = (root/'app/src/main/java/in/scrollcount/probe/Ui.java').read_text()
assert 'if(android.os.Build.VERSION.SDK_INT>=28)view.setAccessibilityHeading(true)' in ui
for file in (root/'app/src/main/java').rglob('*.java'):
    if file.name != 'Ui.java': assert '.setAccessibilityHeading(' not in file.read_text(), file
service = (root/'app/src/main/java/in/scrollcount/probe/ProbeService.java').read_text()
assert 'indicator.show(indicatorInstagram+indicatorYouTube)' in service
badge = (root/'app/src/main/java/in/scrollcount/probe/PlayerIndicator.java').read_text()
assert 'Scrolls' in badge and 'platform.equals' not in badge
activity=(root/'app/src/main/java/in/scrollcount/probe/ProbeActivity.java').read_text()
inventory=(root/'app/src/main/java/in/scrollcount/probe/ProbeStore.java').read_text()
assert 'Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode' in inventory
assert 'ProbeStore.refreshVersions(this)' in activity
assert 'minSdk = 26' in (root/'app/build.gradle.kts').read_text()
assert 'dependsOn(checkReleasePolicy)' in (root/'app/build.gradle.kts').read_text()
print('PASS: synchronized public metadata, email/certificate rejection, API-28 heading guards, combined badge and signed-release gate')
