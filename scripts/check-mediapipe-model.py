#!/usr/bin/env python3
"""Verify actual public task download and official bundle envelope. NOT an Android inference test."""
import hashlib
import json
import pathlib
import urllib.request
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
out = root / 'knowledge-smoke'
out.mkdir(exist_ok=True)
repo = 'litert-community/Gemma3-1B-IT'
filename = 'Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task'
report = {'repository': repo, 'filename': filename, 'android_inference_tested': False}
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Atlas-Personal-model-verification'}), timeout=60)
try:
    with get(f'https://huggingface.co/api/models/{repo}?blobs=true') as response:
        meta = json.load(response)
    revision = meta['sha']
    entry = next((s for s in meta['siblings'] if s['rfilename'] == filename), None)
    if entry is None:
        # A format migration can remove .task from main; inspect a bounded public history.
        with get(f'https://huggingface.co/api/models/{repo}/commits/main') as response:
            commits = json.load(response)
        for commit in commits[:40]:
            with get(f'https://huggingface.co/api/models/{repo}/revision/{commit["id"]}?blobs=true') as response:
                candidate = json.load(response)
            entry = next((s for s in candidate['siblings'] if s['rfilename'] == filename), None)
            if entry:
                meta, revision = candidate, candidate['sha']
                break
    assert entry is not None, 'Public model repository no longer exposes this .task in inspected revisions'
    url = f'https://huggingface.co/{repo}/resolve/{revision}/{filename}?download=true'
    report.update(revision=revision, url=url, license=meta.get('cardData', {}).get('license'), bytes=entry.get('size'), expected_sha256=entry.get('lfs', {}).get('sha256'))
    model = out / 'verified-mediapipe.task'
    digest = hashlib.sha256()
    count = 0
    try:
        with get(url) as source, model.open('wb') as target:
            while block := source.read(1024 * 1024):
                count += len(block)
                assert count <= 700_000_000, 'Unexpected model size'
                target.write(block)
                digest.update(block)
        assert digest.hexdigest() == entry['lfs']['sha256'], 'Official LFS SHA256 mismatch'
        with zipfile.ZipFile(model) as archive:
            names = archive.namelist()
            for required in ('TF_LITE_PREFILL_DECODE', 'TOKENIZER_MODEL', 'METADATA'):
                assert required in names, f'Bundle missing {required}'
            report['entries'] = names
        report.update(ok=True, sha256=digest.hexdigest(), bytes=count)
    finally:
        model.unlink(missing_ok=True)
except Exception as error:
    report.update(ok=False, error=str(error))
finally:
    (out / 'mediapipe-download.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps(report, ensure_ascii=False), flush=True)
if not report.get('ok'):
    raise SystemExit(1)
