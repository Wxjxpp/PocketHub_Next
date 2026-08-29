#!/usr/bin/env python3
"""Generate a bilingual (zh/en) changelog for the release body.

Input:  a file with one commit subject per line (most recent first).
Output: single-line JSON {"items":[{"type":"feat","zh":"…","en":"…"}, …]}

AI mode  — when LLM_API_KEY (+ optional LLM_BASE_URL / LLM_MODEL) is set,
commits are sent to an OpenAI-compatible endpoint for a polished bilingual
summary ("what changed, what it does, what it fixes", max 8 items).
Fallback — deterministic conventional-commit formatting, always succeeds.
"""
import json, os, re, sys, urllib.request

def read_commits(path):
    out = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                out.append(line[:200])
    return out[:40]

TYPE_MAP = {
    "feat": "feat", "fix": "fix", "perf": "perf", "revert": "revert",
    "refactor": "improve", "improvement": "improve", "docs": "improve",
    "style": "improve", "test": "improve", "build": "improve", "ci": "improve",
    "chore": "improve",
}

def fallback_items(commits):
    items = []
    for c in commits:
        m = re.match(r'^(feat|fix|perf|revert|refactor|improvement|docs|style|test|build|ci|chore)(?:\(([^)]+)\))?\s*[:：]\s*(.+)$', c, re.I)
        if not m:
            continue
        ctype = TYPE_MAP[m.group(1).lower()]
        scope = m.group(2) or ""
        msg = m.group(3).strip()
        # Strip trailing refs like (#123)
        msg = re.sub(r'\s*\(#\d+\)$', '', msg)
        items.append({
            "type": ctype,
            "en": f"{scope}: {msg}" if scope else msg,
            "zh": f"{scope}: {msg}" if scope else msg,
        })
    # Dedupe, cap at 8
    seen, dedup = set(), []
    for it in items:
        if it["en"] in seen:
            continue
        seen.add(it["en"])
        dedup.append(it)
    return dedup[:8]

LLM_PROMPT = """You are writing release notes for a GitHub Android client app called PocketHub.
Below are commit messages from one release. Produce a concise, user-facing bilingual changelog.
Rules:
- At most 8 items; merge related commits; drop pure chore/ci noise.
- For each item: classify type as one of feat|fix|perf|improve|revert; write "zh" (简体中文) and "en" (English).
- Describe what changed, what it does for the user, or what it fixes. Plain language, no commit jargon, no PR numbers.
- Reply with STRICT JSON only, no markdown fences: {"items":[{"type":"feat","zh":"…","en":"…"}]}"""

def llm_items(commits):
    key = os.environ.get("LLM_API_KEY", "").strip()
    if not key:
        return None
    base = os.environ.get("LLM_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    model = os.environ.get("LLM_MODEL", "gpt-4o-mini")
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": LLM_PROMPT},
            {"role": "user", "content": "\n".join(commits)[:6000]},
        ],
        "temperature": 0.3,
        "max_tokens": 900,
    }
    req = urllib.request.Request(
        f"{base}/chat/completions",
        data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read())
    content = data["choices"][0]["message"]["content"].strip()
    content = re.sub(r'^```(?:json)?\s*|\s*```$', '', content)
    parsed = json.loads(content)
    items = parsed.get("items") or []
    clean = []
    for it in items:
        if not isinstance(it, dict):
            continue
        zh = str(it.get("zh", "")).strip()
        en = str(it.get("en", "")).strip()
        if not zh and not en:
            continue
        clean.append({
            "type": str(it.get("type", "improve")).strip() or "improve",
            "zh": zh or en,
            "en": en or zh,
        })
    return clean[:8] or None

def main():
    commits = read_commits(sys.argv[1] if len(sys.argv) > 1 else "/dev/stdin")
    items = None
    try:
        items = llm_items(commits)
    except Exception as e:
        print(f"LLM summary failed, falling back: {e}", file=sys.stderr)
    if not items:
        items = fallback_items(commits)
    json.dump({"items": items}, sys.stdout, ensure_ascii=False, separators=(",", ":"))

if __name__ == "__main__":
    main()
