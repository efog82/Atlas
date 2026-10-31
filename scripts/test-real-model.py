#!/usr/bin/env python3
"""Actual GGUF inference and factual-quality comparison; never an Android benchmark."""
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
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Atlas-Personal-model-test'}), timeout=120)

source = (root / 'app/src/main/java/pt/atlas/personal/agent/NormalChat.kt').read_text()
prompt = textwrap.dedent(re.search(r'val system = """\n(.*?)\n\s*"""', source, re.S).group(1)).strip()
system = out / 'system.txt'
system.write_text(prompt)
report = {'hardware': 'GitHub Linux x86 CPU; not Redmi/Android', 'sampling': {'top_k': 40, 'top_p': 0.9, 'seed': 42}, 'models': [], 'responses': []}

def persist():
    (out / 'results.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))

def install(size):
    repo = f'Qwen/Qwen2.5-{size.upper()}-Instruct-GGUF'
    with get('https://huggingface.co/api/models/' + repo + '?blobs=true') as response:
        metadata = json.load(response)
    revision = metadata['sha']
    filename = f'qwen2.5-{size}-instruct-q4_k_m.gguf'
    entry = next(s for s in metadata['siblings'] if s['rfilename'] == filename)
    expected = entry['lfs']['sha256']
    model = out / filename
    with get(f'https://huggingface.co/{repo}/resolve/{revision}/{filename}?download=true') as response, model.open('wb') as target:
        while block := response.read(1024 * 1024):
            target.write(block)
    with model.open('rb') as content:
        assert content.read(4) == b'GGUF'
        content.seek(0)
        digest = hashlib.file_digest(content, 'sha256').hexdigest()
    assert digest == expected, 'Model differs from official LFS SHA-256'
    identity = {'size': size, 'model': repo, 'revision': revision, 'sha256': digest, 'bytes': model.stat().st_size, 'official_lfs_verified': True}
    report['models'].append(identity)
    persist()
    print(json.dumps(identity, ensure_ascii=False), flush=True)
    return model

for size in ('0.5b', '1.5b'):
    model = install(size)
    variants = [('app', prompt)]
    if size == '0.5b':
        variants += [('short', 'You are Atlas Personal, a helpful assistant. Answer the question directly and accurately in Portuguese. If you do not know, say so.')]
    for name, instruction in variants:
        system.write_text(instruction)
        for temperature in (0.0, 0.25, 0.65):
            for question, topic in [('O que era o espiritismo?', 'spiritism'), ('Como funciona a fotossíntese?', 'photosynthesis')]:
                cmd = [str(root / 'native/llama.cpp/build/bin/llama-cli'), '-m', str(model), '-t', '2', '-c', '2048', '-n', '180', '--samplers', 'top_k;top_p;temperature', '--temp', str(temperature), '--top-k', '40', '--top-p', '0.9', '--seed', '42', '-cnv', '-st', '-sysf', str(system), '-p', question, '--no-display-prompt', '--simple-io']
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=240)
                answer = result.stdout.strip()
                n = ''.join(c for c in unicodedata.normalize('NFD', answer.lower()) if not unicodedata.combining(c))
                if topic == 'spiritism':
                    concepts = 'espirit' in n and any(w in n for w in ('doutrina', 'kardec', 'reencarn'))
                else:
                    concepts = any(w in n for w in ('planta', 'alga', 'clorof')) and ('luz' in n or 'solar' in n) and any(w in n for w in ('dioxido', 'carbono', 'glicose', 'acucar'))
                entry = {'model_size': size, 'variant': name, 'system': instruction, 'temperature': temperature, 'seed': 42, 'question': question, 'answer': answer, 'stderr': result.stderr[-5000:], 'passed': result.returncode == 0 and concepts}
                report['responses'].append(entry)
                persist()
                print(json.dumps({k: entry[k] for k in ('model_size', 'variant', 'temperature', 'question', 'answer', 'passed')}, ensure_ascii=False), flush=True)
                if result.returncode:
                    print('Runner stderr:', result.stderr[-5000:], flush=True)
    model.unlink()  # Never publish weights; free space before the next model.
system.write_text(prompt)
# Do not turn a failed small-model quality check green merely because an alternative succeeds.
primary = [r for r in report['responses'] if r['model_size'] == '0.5b' and r['variant'] == 'app' and r['temperature'] == 0.65]
raise SystemExit(0 if len(primary) == 2 and all(r['passed'] for r in primary) else 1)
