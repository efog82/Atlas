#!/usr/bin/env python3
"""Check actual compiler commands, not just a CMake option."""
import json
import re
from pathlib import Path

commands = list(Path('app/.cxx').rglob('compile_commands.json'))
assert commands, 'CMake compile_commands.json not found'
seen = set()
for path in commands:
    for entry in json.loads(path.read_text()):
        source = entry['file'].replace('\\', '/')
        group = 'bridge' if source.endswith('/atlas.cpp') else 'kernels' if '/ggml/src/ggml-cpu/' in source else None
        if not group:
            continue
        command = entry.get('command') or ' '.join(entry['arguments'])
        levels = re.findall(r'(?:^|\s)-O(0|1|2|3|s|g|z|fast)(?=\s|$)', command)
        assert levels and levels[-1] == '3', f'Unoptimized inference source: {source}: {levels}'
        seen.add(group)
assert seen == {'bridge', 'kernels'}, f'Missing native compiler evidence: {seen}'
print('Verified -O3 in actual JNI and CPU-kernel compiler commands (including debug APK)')
