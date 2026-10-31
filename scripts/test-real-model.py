#!/usr/bin/env python3
"""Download public GGUFs, verify official LFS digests, run the app's C++ renderer/sampler."""
import hashlib
import json
import pathlib
import re
import subprocess
import textwrap
import urllib.request

root = pathlib.Path(__file__).resolve().parent.parent
out = root / 'model-smoke'
out.mkdir(exist_ok=True)

def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Atlas-Personal-model-integration'}), timeout=120)

def constant(source, name):
    return json.loads(re.search(r'const val ' + name + r' = ("[^\n]+")', source).group(1))

normal = (root / 'app/src/main/java/pt/atlas/personal/agent/NormalChat.kt').read_text()
policy = (root / 'app/src/main/java/pt/atlas/personal/ai/ModelCompatibility.kt').read_text()
base = textwrap.dedent(re.search(r'val system = """\n(.*?)\n\s*"""', normal, re.S).group(1)).strip()
system = '\n\n'.join([base, constant(policy, 'language'), constant(policy, 'factual')])
(out / 'system.txt').write_text(system)
models = [('qwen3', 'Qwen/Qwen3-0.6B-GGUF'), ('gemma3', 'ggml-org/gemma-3-1b-it-GGUF'), ('qwen25', 'Qwen/Qwen2.5-0.5B-Instruct-GGUF')]
identities = []
for label, repo in models:
    with get('https://huggingface.co/api/models/' + repo + '?blobs=true') as response:
        meta = json.load(response)
    candidates = [f for f in meta['siblings'] if f['rfilename'].lower().endswith('.gguf') and 'q4_k_m' in f['rfilename'].lower() and 'mmproj' not in f['rfilename'].lower()]
    assert len(candidates) == 1, f'Expected one Q4_K_M text GGUF in {repo}'
    entry = candidates[0]
    filename, revision = entry['rfilename'], meta['sha']
    model = out / (label + '.gguf')
    with get(f'https://huggingface.co/{repo}/resolve/{revision}/{filename}?download=true') as source, model.open('wb') as target:
        while block := source.read(1024 * 1024):
            target.write(block)
    with model.open('rb') as source:
        assert source.read(4) == b'GGUF'
        source.seek(0)
        digest = hashlib.file_digest(source, 'sha256').hexdigest()
    assert digest == entry['lfs']['sha256'], 'Official LFS checksum mismatch'
    identity = {'label': label, 'repository': repo, 'filename': filename, 'revision': revision, 'sha256': digest, 'bytes': model.stat().st_size, 'license': meta.get('cardData', {}).get('license'), 'hardware': 'Linux x86 CPU, not Android/Redmi'}
    identities.append(identity)
    (out / 'models.json').write_text(json.dumps(identities, ensure_ascii=False, indent=2))
    print(json.dumps(identity), flush=True)
    # These are checked against SamplingConfig.recommended by the shared Kotlin integration tests.
    if label == 'qwen3':
        sampling = dict(temperature=0.7, topP=0.8, topK=20, minP=0.0, repeatPenalty=1.0)
    elif label == 'gemma3':
        sampling = dict(temperature=1.0, topP=0.95, topK=64, minP=0.0, repeatPenalty=1.0)
    else:
        sampling = dict(temperature=0.7, topP=0.8, topK=20, minP=0.0, repeatPenalty=1.05)
    requests = []
    for case, question in [('explanation', 'Explica em três frases, em português, como funciona a fotossíntese.'), ('false-premise', 'Porque é que Lisboa é a capital de Espanha? Responde em português e verifica a premissa da pergunta.')]:
        requests.append(dict(id=case, messages=[dict(role='system', content=system), dict(role='user', content=question)], thinking=False, sampling=sampling, maxTokens=256))
    requests.append(dict(id='multi-turn', messages=[dict(role='system', content=system), dict(role='user', content='Vamos conversar em português.'), dict(role='assistant', content='Sim, podemos conversar em português.'), dict(role='user', content='Explica em três frases o que é uma biblioteca.')], thinking=False, sampling=sampling, maxTokens=256))
    if label == 'qwen3':
        requests.append(dict(id='thinking', messages=[dict(role='system', content=system), dict(role='user', content='Quanto é 12 mais 7? Responde brevemente em português.')], thinking=True, sampling=dict(temperature=0.6, topP=0.95, topK=20, minP=0.0, repeatPenalty=1.0), maxTokens=1024))
    request_file = out / (label + '-requests.json')
    request_file.write_text(json.dumps(requests, ensure_ascii=False, indent=2))
    subprocess.run([str(root / 'scripts/model-integration/build/atlas-model-runner'), str(model), str(request_file), str(out / (label + '-raw.json'))], check=True, timeout=600)
    model.unlink()
