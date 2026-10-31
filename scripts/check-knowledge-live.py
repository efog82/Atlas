#!/usr/bin/env python3
"""Informative live probe, no personal data. Failure is recorded, never called online validation."""
import json
import pathlib
import urllib.parse
import urllib.request
out = pathlib.Path('knowledge-smoke')
out.mkdir(exist_ok=True)
parameters = dict(action='query',format='json',formatversion='2',prop='extracts|revisions',explaintext='1',exintro='1',exchars='8000',exlimit='max',rvprop='ids|timestamp',redirects='1',generator='search',gsrsearch='fotossíntese',gsrlimit='4')
url = 'https://pt.wikipedia.org/w/api.php?' + urllib.parse.urlencode(parameters)
result = {'url': url, 'contains_personal_data': False, 'success': False}
try:
    request = urllib.request.Request(url, headers={'User-Agent':'AtlasPersonal/0.4 (Android; https://github.com/efog82/Atlas)','Accept':'application/json'})
    with urllib.request.urlopen(request, timeout=20) as response:
        data = response.read(2_000_001)
    assert len(data) <= 2_000_000
    result['response'] = json.loads(data)
    assert result['response'].get('query',{}).get('pages'), 'No encyclopedia pages returned'
    result['success'] = True
except Exception as error:
    result['error'] = str(error)
(out / 'wikipedia-live.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps(result, ensure_ascii=False), flush=True)
raise SystemExit(0 if result['success'] else 1)
