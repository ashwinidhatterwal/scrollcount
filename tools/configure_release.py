#!/usr/bin/env python3
"""One public metadata source for app, web policy and release checks. No secrets."""
import argparse, datetime, json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def generate(config):
    operator = config['operator'].strip()
    email = config['supportEmail'].strip()
    if not operator or len(operator) > 100:
        raise ValueError('Set a real operator name')
    if email and not re.fullmatch(r'[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+', email):
        raise ValueError('Enter a valid public support/privacy email')
    datetime.date.fromisoformat(config['effectiveDate'])
    if config['server'] != 'https://scrollcount-friends.actionbridge.workers.dev':
        raise ValueError('Update manifest/App Links deliberately before changing the production server')
    certificates = config.get('appSigningCertificates', [])
    if not isinstance(certificates, list) or any(not re.fullmatch(r'(?:[A-F0-9]{2}:){31}[A-F0-9]{2}', c) for c in certificates):
        raise ValueError('Use colon-separated uppercase certificate SHA-256 fingerprints')
    q = lambda value: json.dumps(value, ensure_ascii=True)
    java = '''package in.scrollcount.probe;
/** Generated from release-config.json by tools/configure_release.py. */
final class ReleaseInfo {
    static final String OPERATOR = %s;
    static final String EMAIL = %s;
    static final String EFFECTIVE_DATE = %s;
    static String summary() {
        return "\\n\\nOperator: "+OPERATOR+"\\nEffective date: "+EFFECTIVE_DATE
            +"\\nPrivacy contact: "+(EMAIL.isEmpty()?"Not configured for publication":EMAIL)
            +"\\nPublic policy: https://scrollcount-friends.actionbridge.workers.dev/privacy"
            +"\\nAccount deletion: https://scrollcount-friends.actionbridge.workers.dev/delete";
    }
}
''' % (q(operator), q(email), q(config['effectiveDate']))
    module = '// Generated public metadata; contains no credentials.\nexport const policy = '+json.dumps(config, ensure_ascii=True, indent=2)+';\n'
    return {
        ROOT/'app/src/main/java/in/scrollcount/probe/ReleaseInfo.java': java,
        ROOT/'backend/release-info.mjs': module,
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--email')
    parser.add_argument('--operator')
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--require-complete', action='store_true')
    args = parser.parse_args()
    path = ROOT/'release-config.json'
    config = json.loads(path.read_text())
    if args.email is not None: config['supportEmail'] = args.email.strip()
    if args.operator is not None: config['operator'] = args.operator.strip()
    outputs = generate(config)
    if args.require_complete and not config['supportEmail']:
        parser.error('Public privacy/support email is required. Run with --email YOUR_EMAIL.')
    if args.check:
        if args.email is not None or args.operator is not None: parser.error('Use --email/--operator without --check first')
        for file, expected in outputs.items():
            if not file.exists() or file.read_text() != expected:
                parser.error('Generated metadata is stale; run python3 tools/configure_release.py')
    else:
        path.write_text(json.dumps(config, indent=2)+'\n')
        for file, content in outputs.items(): file.write_text(content)
    print('Public metadata checked.' if args.check else 'Public metadata generated.')

if __name__ == '__main__': main()
