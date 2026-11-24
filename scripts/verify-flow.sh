#!/usr/bin/env bash
set -euo pipefail
# 使用统一的真实 API 与浏览器验收脚本，默认连接隔离测试服务 8081。
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "${PYTHON:-python}" "$SCRIPT_DIR/verify-flow.py" "$@"
