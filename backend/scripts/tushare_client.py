from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path
from typing import Any


def _load_local_env() -> None:
    env_file = Path(__file__).resolve().parents[1] / ".env"
    if not env_file.exists():
        return
    for raw_line in env_file.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        name = name.strip()
        if name and name not in os.environ:
            os.environ[name] = value.strip()


@lru_cache(maxsize=1)
def get_tushare_pro() -> Any:
    _load_local_env()
    token = os.environ.get("TUSHARE_TOKEN", "").strip()
    if not token:
        raise RuntimeError("TUSHARE_TOKEN is not configured")

    try:
        import tushare as ts  # type: ignore
    except ImportError as exc:  # pragma: no cover - dependency bootstrap issue
        raise RuntimeError("tushare is required. Install it with pip install tushare") from exc

    pro = ts.pro_api(token)
    http_url = os.environ.get("TUSHARE_HTTP_URL", "").strip()
    if http_url:
        pro._DataApi__http_url = http_url
    return pro


def normalize_ts_code(value: Any) -> str:
    text = str(value or "").strip().upper()
    return text


def ts_code_to_symbol(ts_code: Any) -> str:
    text = normalize_ts_code(ts_code)
    if "." in text:
        return text.split(".", 1)[0]
    return text


def symbol_to_ts_code(symbol: str) -> str:
    code = "".join(ch for ch in str(symbol).strip() if ch.isdigit())
    if len(code) != 6:
        return str(symbol).strip().upper()
    if code.startswith(("600", "601", "603", "605", "688", "900", "730", "700")):
        suffix = ".SH"
    elif code.startswith(("430", "440", "830", "831", "832", "833", "834", "835", "836", "837", "838", "839", "870", "871", "872", "873", "874", "875", "876", "877", "878", "879", "920")) or code.startswith("8"):
        suffix = ".BJ"
    else:
        suffix = ".SZ"
    return f"{code}{suffix}"
