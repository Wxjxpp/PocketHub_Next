#!/usr/bin/env python3
"""Generate a bilingual (zh/en) changelog for the release body.

Input:  a file with one commit subject per line (most recent first).
Output: single-line JSON
        {"summary":{"zh":"…","en":"…"},"items":[{"type":"feat","zh":"…","en":"…"}, …]}

AI mode  — when LLM_API_KEY (+ optional LLM_BASE_URL / LLM_MODEL) is set,
commits are sent to an OpenAI-compatible endpoint for a polished, plain-
language bilingual summary ("what changed, what it does for the user").
Fallback — deterministic, vague-but-accurate phrasing, always succeeds.
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

# No-LLM fallback: one item PER COMMIT, phrased from the actual commit
# subject (scope kept, PR refs stripped). Content stays 1:1 with what this
# release changed — never a generic line that could describe any release.
# The zh text keeps the English subject (the app shows it under a Chinese
# category tag: 修复/新增/提速/优化); configure LLM_API_KEY for polished
# Chinese summaries.

def fallback_items(commits):
    items = []
    for c in commits:
        m = re.match(r'^(feat|fix|perf|revert|refactor|improvement|docs|style|test|build|ci|chore)(?:\(([^)]+)\))?\s*[:：]\s*(.+)$', c, re.I)
        if not m:
            continue
        ctype = TYPE_MAP[m.group(1).lower()]
        scope = m.group(2) or ""
        msg = m.group(3).strip().replace("`", "")
        msg = re.sub(r'\s*\(#\d+\)$', '', msg)
        en = (scope + ": " + msg) if scope else msg
        if len(en) > 90:
            en = en[:87] + "…"
        items.append({"type": ctype, "zh": en, "en": en})
    seen, dedup = set(), []
    for it in items:
        if it["en"] in seen:
            continue
        seen.add(it["en"])
        dedup.append(it)
    return dedup[:8]


def fallback_summary(items):
    if not items:
        return {"zh": "例行维护更新", "en": "Routine maintenance update"}
    counts = {}
    for it in items:
        counts[it["type"]] = counts.get(it["type"], 0) + 1
    label = {"fix": "问题修复", "feat": "新功能", "perf": "性能优化",
             "improve": "体验优化", "revert": "改动回退"}
    parts = [f"{label[t]} {n} 项" for t, n in counts.items()]
    return {"zh": "本次更新包含 " + "、".join(parts),
            "en": "This release ships " + ", ".join(f"{n} {t}" for t, n in counts.items())}

LLM_PROMPT = """You write the "what's new" notes for PocketHub, a GitHub client app for Android.
Below are the commit messages of one release.

Reply with STRICT JSON only, no markdown fences:
{"summary":{"zh":"…","en":"…"},"items":[{"type":"feat","zh":"…","en":"…"}]}

Item rules:
- At most 6 items; merge related commits; skip pure chore/ci/docs noise.
- One short line each: ≤ 24 Chinese characters, ≤ 12 English words.
- Write for ordinary users — what they get, not how it's built. Avoid
  technical terms, class/API/DTO names, and PR numbers unless unavoidable.
- Start with a verb: 新增 / 修复 / 优化 / 提升 …; e.g. "修复 PR 列表状态显示错误".
- If a change is hard to describe precisely, use a broader but still accurate
  phrase, e.g. "修复若干已知问题" / "优化界面与交互" / "提升稳定性".
- "type" is one of feat|fix|perf|improve|revert.

Summary rules:
- One sentence (≤ 40 Chinese characters) capturing the theme and benefit of
  this release, e.g. "本次更新聚焦 PR 列表体验，状态一目了然". Do not repeat
  the item list."""

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
    if not clean:
        return None
    result = {"items": clean[:8]}
    sm = parsed.get("summary")
    if isinstance(sm, dict):
        szh = str(sm.get("zh", "")).strip()
        sen = str(sm.get("en", "")).strip()
        if szh or sen:
            result["summary"] = {"zh": szh or sen, "en": sen or szh}
    return result

def main():
    commits = read_commits(sys.argv[1] if len(sys.argv) > 1 else "/dev/stdin")
    out = None
    try:
        out = llm_items(commits)
    except Exception as e:
        print(f"LLM summary failed, falling back: {e}", file=sys.stderr)
    if not out:
        fb = fallback_items(commits)
        out = {"summary": fallback_summary(fb), "items": fb}
    json.dump(out, sys.stdout, ensure_ascii=False, separators=(",", ":"))

if __name__ == "__main__":
    main()
