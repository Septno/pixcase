#!/bin/sh
# 校验一个提交范围内的全部 commit message(CI 与本地均可调)。
# 用法:scripts/check-commit-messages.sh [range],默认 origin/main..HEAD。
# Merge / Revert / fixup 提交自动跳过(与 hook 共用同一规则文件)。
range="${1:-origin/main..HEAD}"
script_dir=$(dirname "$0")

if ! command -v python >/dev/null 2>&1; then
  echo "check-commit-messages: 未找到 python,跳过校验" >&2
  exit 0
fi

count=0
for hash in $(git log --format=%H "$range" 2>/dev/null); do
  subject=$(git log --format=%s -1 "$hash")
  case "$subject" in
    "Merge "*|"fixup! "*|"Revert "*) continue ;;
  esac
  python "$script_dir/commit-msg-check.py" "$subject" || exit 1
  count=$((count + 1))
done

echo "commit message 校验通过($count 条)"
