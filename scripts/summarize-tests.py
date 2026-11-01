#!/usr/bin/env python3
"""Report executed JVM tests separately from compiled device tests."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
reports = list((root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
assert reports, 'No JVM JUnit reports; do not claim tests passed'
totals = dict(tests=0, failures=0, errors=0, skipped=0)
for path in reports:
    suite = ET.parse(path).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, 0))
totals['passed'] = totals['tests'] - totals['failures'] - totals['errors'] - totals['skipped']
apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
assert apk.is_file(), 'No debug APK'
with apk.open('rb') as stream:
    digest = hashlib.file_digest(stream, 'sha256').hexdigest()
report = dict(jvm=totals, android_instrumented_executed=False,
              apk_sha256=digest, apk_bytes=apk.stat().st_size)
out = root / 'app/build/reports/verification-summary.json'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(report, indent=2))
print(json.dumps(report), flush=True)
assert totals['failures'] == totals['errors'] == 0
