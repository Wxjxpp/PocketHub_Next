#!/usr/bin/env python3
"""Format the GitHub release notes file.

Usage: format_notes.py <version> <changelog.json>
Layout: human-readable zh/en sections (visible on the GitHub web page),
plus a single-line <!--pockethub-changelog {json}--> block the in-app
update dialog reads directly (locale-matched, no runtime translation).
"""
import json, sys

def main():
    version = sys.argv[1]
    try:
        data = json.loads(open(sys.argv[2], encoding="utf-8").read())
        items = data.get("items", [])
        summary = data.get("summary") or {}
    except Exception:
        items, summary = [], {}

    lines = [f"## PocketHub v{version}", ""]
    if items:
        szh = str(summary.get("zh", "")).strip()
        sen = str(summary.get("en", "")).strip()
        if szh:
            lines.append(f"**{szh}**")
            lines.append("")
        lines.append("### 更新说明")
        lines += [f"- {it.get('zh', '')}" for it in items if it.get("zh")]
        lines.append("")
        if sen:
            lines.append(f"**{sen}**")
            lines.append("")
        lines.append("### What's new")
        lines += [f"- {it.get('en', '')}" for it in items if it.get("en")]
    else:
        lines.append(f"Auto-built release APK for PocketHub v{version}.")
    # Single-line machine block (HTML comment = invisible on the web page).
    lines.append(f"<!--pockethub-changelog {json.dumps({'summary': summary, 'items': items}, ensure_ascii=False, separators=(',', ':'))}-->")
    print("\n".join(lines))

if __name__ == "__main__":
    main()
