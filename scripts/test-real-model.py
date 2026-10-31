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
report = {'model': repo, 'revision': revision, 'sha256': digest, 'hardware': 'GitHub Linux x86 CPU; not Redmi/Android', 'sampling': {'top_k': 40, 'top_p': 0.9, 'temperature': 0.65, 'seed': 42}, 'responses': []}
# Compare a short English instruction and lower temperatures without injecting factual answers.
variants = [('app', prompt), ('short', 'You are Atlas Personal, a helpful assistant. Answer the question directly and accurately in Portuguese. If you do not know, say so.')]
for name, instruction in variants:
    system.write_text(instruction)
    for temperature in (0.0, 0.25, 0.65):
        for question, topic in [('O que era o espiritismo?', 'spiritism'), ('Como funciona a fotossíntese?', 'photosynthesis')]:
            cmd = [str(root / 'native/llama.cpp/build/bin/llama-cli'), '-m', str(model), '-t', '2', '-c', '2048', '-n', '180', '--samplers', 'top_k;top_p;temperature', '--temp', str(temperature), '--top-k', '40', '--top-p', '0.9', '--seed', '42', '-cnv', '-st', '-sysf', str(system), '-p', question, '--no-display-prompt', '--simple-io']
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=240)
            answer = result.stdout.strip()
            normalized = ''.join(c for c in unicodedata.normalize('NFD', answer.lower()) if not unicodedata.combining(c))
            if topic == 'spiritism':
                concepts = 'espirit' in normalized and any(word in normalized for word in ('doutrina', 'kardec', 'reencarn'))
            else:
                concepts = any(word in normalized for word in ('planta', 'alga', 'clorof')) and ('luz' in normalized or 'solar' in normalized) and any(word in normalized for word in ('dioxido', 'carbono', 'glicose', 'acucar'))
            passed = result.returncode == 0 and concepts
            entry = {'variant': name, 'system': instruction, 'temperature': temperature, 'seed': 42, 'question': question, 'answer': answer, 'stderr': result.stderr[-4000:], 'passed': passed}
            report['responses'].append(entry)
            print(json.dumps({k: entry[k] for k in ('variant', 'temperature', 'question', 'answer', 'passed')}, ensure_ascii=False), flush=True)
            if result.returncode:
                print('Runner stderr:', result.stderr[-4000:], flush=True)
            (out / 'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
system.write_text(prompt)
# A diagnostic comparison: keep the primary app configuration a real quality gate.
primary = [r for r in report['responses'] if r['variant'] == 'app' and r['temperature'] == 0.65]
raise SystemExit(0 if all(r['passed'] for r in primary) else 1)
