from __future__ import annotations

import json
import math
import os
import re
import shutil
import subprocess
from collections import Counter
from pathlib import Path
from typing import Any

YI = "亿"
WAN = "万"
JY_FETCH_SCRIPT = Path(__file__).with_name("fetch_jiuyangongshe_action.js")
JY_NODE_MODULES_CANDIDATES = [
    os.environ.get("NODE_PATH", ""),
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


def clean_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float) and math.isnan(value):
        return ""
    text = str(value).strip()
    return "" if text.lower() == "nan" else text


def clean_reason_text(value: Any) -> str:
    text = clean_text(value)
    if re.fullmatch(r"\d+\s*/\s*\d+", text):
        return ""
    return text


def to_float(value: Any) -> float | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        if isinstance(value, float) and math.isnan(value):
            return None
        return float(value)
    text = clean_text(value).replace(",", "").replace("%", "")
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def format_price(value: Any) -> str:
    number = to_float(value)
    return "" if number is None else f"{number:.2f}"


def format_percent(value: Any) -> str:
    number = to_float(value)
    return "" if number is None else f"{number:.2f}%"


def format_amount(value: Any) -> str:
    number = to_float(value)
    if number is None:
        return ""
    abs_number = abs(number)
    if abs_number >= 100000000:
        return f"{number / 100000000:.2f}{YI}"
    if abs_number >= 10000:
        return f"{number / 10000:.2f}{WAN}"
    if float(number).is_integer():
        return str(int(number))
    return f"{number:.2f}"


def normalize_code(value: Any) -> str:
    text = clean_text(value)
    digits = "".join(ch for ch in text if ch.isdigit())
    return digits.zfill(6) if digits else text


def stock_record(
    *,
    code: Any = "",
    name: Any = "",
    board_height: Any = "",
    change_percent: Any = "",
    price: Any = "",
    industry: Any = "",
    concept: Any = "",
    amount: Any = "",
    float_market_value: Any = "",
    reason: Any = "",
    seal_amount: Any = "",
    auction_change_percent: Any = "",
    turnover_rate: Any = "",
    amplitude: Any = "",
    open_price: Any = "",
    extra_tag: Any = "",
) -> dict[str, str]:
    return {
        "code": normalize_code(code),
        "name": clean_text(name),
        "boardHeight": clean_text(board_height),
        "changePercent": clean_text(change_percent),
        "price": clean_text(price),
        "industry": clean_text(industry),
        "concept": clean_text(concept),
        "amount": clean_text(amount),
        "floatMarketValue": clean_text(float_market_value),
        "reason": clean_text(reason),
        "sealAmount": clean_text(seal_amount),
        "auctionChangePercent": clean_text(auction_change_percent),
        "turnoverRate": clean_text(turnover_rate),
        "amplitude": clean_text(amplitude),
        "openPrice": clean_text(open_price),
        "extraTag": clean_text(extra_tag),
    }


def stock_change_value(row: dict[str, str]) -> float:
    return to_float(row.get("changePercent")) or 0.0


def build_sector_fallback(
    positive_rows: list[dict[str, str]],
    negative_rows: list[dict[str, str]],
    top: bool,
) -> list[dict[str, str]]:
    bucket: dict[str, dict[str, float]] = {}

    def add_row(name: str, change_value: float, weight: float) -> None:
        if not name:
            return
        item = bucket.setdefault(name, {"score": 0.0, "sum": 0.0, "count": 0.0})
        item["score"] += weight
        item["sum"] += change_value
        item["count"] += 1

    for row in positive_rows:
        sector = row.get("industry") or row.get("concept") or ""
        change_value = stock_change_value(row)
        board_height = int(row.get("boardHeight") or "1")
        add_row(sector, change_value, max(1.0, board_height + 1.0))

    for row in negative_rows:
        sector = row.get("industry") or row.get("concept") or ""
        change_value = stock_change_value(row)
        add_row(sector, change_value, abs(change_value) + 1.0)

    ordered = sorted(
        bucket.items(),
        key=lambda item: (item[1]["score"], item[1]["sum"] / item[1]["count"]),
        reverse=top,
    )
    return [
        {
            "name": name,
            "changePercent": f"{(stats['sum'] / stats['count']) if stats['count'] else 0.0:.2f}%",
            "reason": f"基于已采集个股反推：{int(stats['count'])}只，热度 {stats['score']:.1f}",
        }
        for name, stats in ordered[:10]
    ]


def first_limit_focus(rows: list[dict[str, str]]) -> dict[str, int]:
    counter = Counter()
    for row in rows:
        counter[row.get("industry") or "未分类"] += 1
    return dict(counter.most_common(10))


def resolve_node_executable() -> str | None:
    candidates = [
        os.environ.get("JIUYANGONGSHE_NODE"),
        os.environ.get("NODE"),
        shutil.which("node"),
        r"C:\Program Files\nodejs\node.exe",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return candidate
    return None


def build_node_env() -> dict[str, str]:
    env = os.environ.copy()
    if clean_text(env.get("NODE_PATH")):
        return env
    for candidate in JY_NODE_MODULES_CANDIDATES:
        if candidate and Path(candidate).exists():
            env["NODE_PATH"] = candidate
            return env
    return env


def build_jiuyangongshe_reason_map(trade_date: str) -> dict[str, dict[str, str]]:
    node_executable = resolve_node_executable()
    if node_executable is None or not JY_FETCH_SCRIPT.exists():
        return {}

    try:
        completed = subprocess.run(
            [
                node_executable,
                str(JY_FETCH_SCRIPT),
                "--mode",
                "fetch",
                "--date",
                trade_date,
                "--auto-login",
            ],
            capture_output=True,
            text=True,
            encoding="utf-8",
            env=build_node_env(),
            timeout=180,
            check=False,
        )
    except Exception:
        return {}

    if completed.returncode != 0:
        return {}

    payload_text = clean_text(completed.stdout).splitlines()
    if not payload_text:
        return {}

    try:
        payload = json.loads(payload_text[-1])
    except json.JSONDecodeError:
        return {}

    if not payload.get("ok"):
        return {}

    result: dict[str, dict[str, str]] = {}
    for entry in payload.get("entries", []):
        if not isinstance(entry, dict):
            continue
        stock_name = clean_text(entry.get("stockName"))
        stock_code = normalize_code(entry.get("stockCode"))
        reason = clean_text(entry.get("reason"))
        field_name = clean_text(entry.get("fieldName"))
        if not reason:
            continue
        item = {
            "name": stock_name,
            "code": stock_code,
            "reason": reason,
            "fieldName": field_name,
        }
        if stock_code:
            result[f"code:{stock_code}"] = item
        if stock_name:
            result[f"name:{stock_name}"] = item
    return result
