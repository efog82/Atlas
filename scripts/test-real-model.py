#!/usr/bin/env python3
"""Real Qwen GGUF CPU smoke test; no Android performance claims."""
import hashlib
import json
import pathlib
import re
import subprocess
import textwrap
import unicodedata
import urllib.request

root = pathlib.Path(__file__).resolve().parent.parent
out = root / 'model-smoke'
out.mkdir(exist_ok=True)
def get(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'Atlas-Personal-model-test'})
    return urllib.request.urlopen(request, timeout=120)
repo = 'Qwen/Qwen2.5-0.5B-Instruct-GGUF'
with get('https://huggingface.co/api/models/' + repo) as response:
    metadata = json.load(response)
revision = metadata['sha']
filename = 'qwen2.5-0.5b-instruct-q4_k_m.gguf'
assert any(s['rfilename'] == filename for s in metadata['siblings'])
model = out / filename
with get(f'https://huggingface.co/{repo}/resolve/{revision}/{filename}?download=true') as response, model.open('wb') as target:
    while block := response.read(1024 * 1024):
        target.write(block)
assert model.open('rb').read(4) == b'GGUF'
digest = hashlib.file_digest(model.open('rb'), 'sha256').hexdigest()
source = (root / 'app/src/main/java/pt/atlas/personal/agent/NormalChat.kt').read_text()
prompt = textwrap.dedent(re.search(r'val system = """\n(.*?)\n\s*"""', source, re.S).group(1)).strip()
system = out / 'system.txt'
system.write_text(prompt)
report = {'model': repo, 'revision': revision, 'sha256': digest, 'hardware': 'GitHub Linux x86 CPU; not Redmi/Android', 'responses': []}
failed = False
for question, markers in [('O que é o espiritismo?', ['espirit', 'doutrina', 'kardec', 'reencarn']), ('Como funciona a fotossíntese?', ['luz', 'solar', 'clorof', 'dioxido', 'carbono'])]:
    cmd = [str(root / 'native/llama.cpp/build/bin/llama-cli'), '-m', str(model), '-t', '2', '-c', '2048', '-n', '180', '--temp', '0', '--seed', '42', '-cnv', '-st', '-sysf', str(system), '-p', question, '--no-display-prompt', '--simple-io', '--log-disable']
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=240)
    answer = result.stdout.strip()
    normalized = ''.join(c for c in unicodedata.normalize('NFD', answer.lower()) if not unicodedata.combining(c))
    passed = result.returncode == 0 and sum(marker in normalized for marker in markers) >= 2
    report['responses'].append({'question': question, 'answer': answer, 'stderr': result.stderr[-4000:], 'passed': passed})
    print(question, '\n', answer, '\nMeaningful-answer smoke:', passed, flush=True)
    failed |= not passed
(out / 'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
raise SystemExit(1 if failed else 0)
