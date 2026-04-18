from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def resolve_node() -> str:
    candidates = [
        os.environ.get("JIUYANGONGSHE_NODE"),
        os.environ.get("NODE"),
        r"C:\Program Files\nodejs\node.exe",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return candidate
    return "node"


def build_env() -> dict[str, str]:
    env = os.environ.copy()
    node_path_candidates = [
        env.get("NODE_PATH"),
        str(
            Path.home()
            / ".cache"
            / "codex-runtimes"
            / "codex-primary-runtime"
            / "dependencies"
            / "node"
            / "node_modules"
        ),
    ]
    for candidate in node_path_candidates:
        if candidate and Path(candidate).exists():
            env["NODE_PATH"] = candidate
            break
    return env


def main() -> None:
    script_path = Path(__file__).with_name("fetch_jiuyangongshe_action.js")
    command = [resolve_node(), str(script_path), "--mode", "login", "--headed", "--timeout-ms", "600000"]
    completed = subprocess.run(command, env=build_env(), check=False)
    raise SystemExit(completed.returncode)


if __name__ == "__main__":
    main()
