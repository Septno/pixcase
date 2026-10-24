#!/usr/bin/env python3
"""单条 commit message 标题校验(统一规范见 CONTRIBUTING.md「Commit 规范」)。

用法:commit-msg-check.py <标题行>
失败时向 stderr 打印原因并以 1 退出;Merge / Revert / fixup 提交直接放行。
本地 commit-msg hook 与 CI 的范围校验脚本共用本文件,保证两处规则一致。
"""
import re
import sys

TITLE = sys.argv[1] if len(sys.argv) > 1 else ""
TYPES = ("feat", "fix", "docs", "refactor", "test", "chore", "build", "ci", "perf", "style")
MAX_TITLE_CHARS = 70

SKIP_PREFIXES = ("Merge ", "fixup! ", "Revert ")


def fail(reason: str) -> None:
    print(f"commit-msg 校验失败:{reason}", file=sys.stderr)
    print(f"  当前标题:{TITLE}", file=sys.stderr)
    print("  规范见 CONTRIBUTING.md「Commit 规范」", file=sys.stderr)
    sys.exit(1)


def main() -> int:
    if TITLE.startswith(SKIP_PREFIXES):
        return 0
    if not re.match(r"^(" + "|".join(TYPES) + r"): \S", TITLE):
        fail("标题必须形如 <type>: <中文标题>(type ∈ " + "|".join(TYPES) + ")")
    if len(TITLE) > MAX_TITLE_CHARS:
        fail(f"标题 {len(TITLE)} 字符,超过 {MAX_TITLE_CHARS} 上限")
    return 0


if __name__ == "__main__":
    sys.exit(main())
