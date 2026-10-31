#!/usr/bin/env python3
"""Test-only audit: never edits/replaces the model's answer in Atlas."""
import json
import pathlib
import re
import sys
import langid

out = pathlib.Path(__file__).resolve().parent.parent / 'model-smoke'
rows = json.loads((out / 'presented.json').read_text())
report = []
failed = False
for row in rows:
    answer = row['final']
    # Quotes/code/proper nouns are not grounds for runtime censorship. Inspect natural-language sentences in this controlled Portuguese suite.
    language, score = langid.classify(answer)
    sections = [s for s in re.split(r'\n\s*\n|(?<=[.!?])\s+', answer) if len(s) >= 80]
    foreign = [s for s in sections if langid.classify(s)[0] != 'pt']
    meaningful = len(answer) >= 60
    passed = (not meaningful or language == 'pt') and not foreign and not re.search(r'[\u4e00-\u9fff\u0400-\u04ff]', answer)
    entry = dict(model=row['model'], case=row['id'], language=language, short_unverified=not meaningful, foreign_sections=foreign, language_passed=bool(passed), answer=answer)
    report.append(entry)
    print(json.dumps(entry, ensure_ascii=False), flush=True)
    failed |= not passed
(out / 'language-audit.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
# Factual correctness is not inferred from a few keywords or the language detector. Retain exact answers for review.
sys.exit(1 if failed else 0)
