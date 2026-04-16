from __future__ import annotations

import argparse
import contextlib
import io
import json
import math
import os
from dataclasses import asdict, dataclass
from typing import Any


def clean_float(value: Any) -> float | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        number = float(value)
        return None if math.isnan(number) else number
    text = str(value).strip().replace(",", "").replace("%", "")
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def format_number(value: float | None, digits: int = 2) -> float | None:
    if value is None:
        return None
    return round(value, digits)


@dataclass
class Bar:
    time: str
    open: float
    close: float
    high: float
    low: float
    volume: float
    amount: float
    changePercent: float | None = None
    amplitudePercent: float | None = None


@contextlib.contextmanager
def without_proxy_env():
    proxy_keys = [
        "HTTP_PROXY",
        "HTTPS_PROXY",
        "ALL_PROXY",
        "http_proxy",
        "https_proxy",
        "all_proxy",
    ]
    backup = {key: os.environ.get(key) for key in proxy_keys}
    no_proxy_backup = os.environ.get("NO_PROXY")
    no_proxy_backup_lower = os.environ.get("no_proxy")
    try:
        for key in proxy_keys:
            os.environ.pop(key, None)
        os.environ["NO_PROXY"] = "*"
        os.environ["no_proxy"] = "*"
        yield
    finally:
        for key, value in backup.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value
        if no_proxy_backup is None:
            os.environ.pop("NO_PROXY", None)
        else:
            os.environ["NO_PROXY"] = no_proxy_backup
        if no_proxy_backup_lower is None:
            os.environ.pop("no_proxy", None)
        else:
            os.environ["no_proxy"] = no_proxy_backup_lower


def safe_call(func, **kwargs):
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        with without_proxy_env():
            return func(**kwargs)


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--code", required=True)
    parser.add_argument("--timeframe", required=True, choices=["1", "5", "15", "30", "60", "day"])
    return parser.parse_args()


def timeframe_meta(timeframe: str) -> tuple[str, int]:
    mapping = {
        "1": ("1分钟", 240),
        "5": ("5分钟", 160),
        "15": ("15分钟", 120),
        "30": ("30分钟", 100),
        "60": ("60分钟", 80),
        "day": ("日K", 90),
    }
    return mapping[timeframe]


def load_stock_name(ak, code: str) -> str:
    info_df = safe_call(ak.stock_individual_info_em, symbol=code)
    if info_df is None or getattr(info_df, "empty", False):
        return ""
    rows = json.loads(info_df.to_json(orient="records", force_ascii=False))
    for row in rows:
        if str(row.get("item", "")).strip() == "股票简称":
            return str(row.get("value", "")).strip()
    return ""


def build_bar_list(ak, code: str, timeframe: str) -> list[Bar]:
    if timeframe == "day":
        df = safe_call(
            ak.stock_zh_a_hist,
            symbol=code,
            period="daily",
            start_date="20240101",
            end_date="20500101",
            adjust="qfq",
            timeout=15,
        )
        if df is None or getattr(df, "empty", False):
            return []
        rows = json.loads(df.to_json(orient="records", force_ascii=False))
        return [
            Bar(
                time=str(row.get("日期", "")),
                open=clean_float(row.get("开盘")) or 0.0,
                close=clean_float(row.get("收盘")) or 0.0,
                high=clean_float(row.get("最高")) or 0.0,
                low=clean_float(row.get("最低")) or 0.0,
                volume=clean_float(row.get("成交量")) or 0.0,
                amount=clean_float(row.get("成交额")) or 0.0,
                changePercent=clean_float(row.get("涨跌幅")),
                amplitudePercent=clean_float(row.get("振幅")),
            )
            for row in rows
        ]

    df = safe_call(ak.stock_zh_a_hist_min_em, symbol=code, period=timeframe, adjust="qfq")
    if df is None or getattr(df, "empty", False):
        return []
    rows = json.loads(df.to_json(orient="records", force_ascii=False))
    return [
        Bar(
            time=str(row.get("时间", "")),
            open=clean_float(row.get("开盘")) or 0.0,
            close=clean_float(row.get("收盘")) or 0.0,
            high=clean_float(row.get("最高")) or 0.0,
            low=clean_float(row.get("最低")) or 0.0,
            volume=clean_float(row.get("成交量")) or 0.0,
            amount=clean_float(row.get("成交额")) or 0.0,
            changePercent=clean_float(row.get("涨跌幅")),
            amplitudePercent=clean_float(row.get("振幅")),
        )
        for row in rows
    ]


def summarize_bars(bars: list[Bar]) -> dict[str, Any]:
    closes = [item.close for item in bars]
    highs = [item.high for item in bars]
    lows = [item.low for item in bars]
    volumes = [item.volume for item in bars]
    latest = bars[-1]
    first = bars[0]
    recent_count = max(3, min(12, len(bars) // 5 or 1))
    recent_volumes = volumes[-recent_count:]
    previous_volumes = volumes[-recent_count * 2:-recent_count] or volumes[:-recent_count]
    recent_avg = sum(recent_volumes) / len(recent_volumes)
    previous_avg = sum(previous_volumes) / len(previous_volumes) if previous_volumes else recent_avg
    volume_ratio = recent_avg / previous_avg if previous_avg else 1.0
    return {
        "windowStart": first.time,
        "windowEnd": latest.time,
        "latestPrice": format_number(latest.close),
        "periodChangePercent": format_number(((latest.close - first.open) / first.open) * 100 if first.open else 0.0),
        "rangeHigh": format_number(max(highs)),
        "rangeLow": format_number(min(lows)),
        "averageVolume": format_number(sum(volumes) / len(volumes), 0),
        "recentVolumeRatio": format_number(volume_ratio),
    }


def build_signals(bars: list[Bar]) -> list[str]:
    if len(bars) < 5:
        return []
    latest = bars[-1]
    previous = bars[-2]
    recent = bars[-5:]
    recent_high = max(item.high for item in recent[:-1]) if len(recent) > 1 else latest.high
    recent_low = min(item.low for item in recent[:-1]) if len(recent) > 1 else latest.low
    avg_volume = sum(item.volume for item in recent[:-1]) / max(1, len(recent) - 1)
    signals: list[str] = []
    if latest.close > recent_high and latest.volume > avg_volume * 1.2:
        signals.append("最近一根K线放量突破短线区间高点，属于偏强的价格接受。")
    if latest.close < recent_low and latest.volume > avg_volume * 1.2:
        signals.append("最近一根K线放量跌破短线区间低点，说明抛压释放更主动。")
    if latest.close > latest.open and latest.close >= latest.high - (latest.high - latest.low) * 0.2:
        signals.append("K线收在高位区域，买方在本周期结束前保持了主导。")
    if latest.close < latest.open and latest.close <= latest.low + (latest.high - latest.low) * 0.2:
        signals.append("K线收在低位区域，说明尾段承接偏弱。")
    if latest.high > previous.high and latest.close < previous.close:
        signals.append("出现冲高回落迹象，追价的性价比下降，需要等待再次确认。")
    if latest.low < previous.low and latest.close > previous.close:
        signals.append("下探后被快速收回，说明低位承接存在。")
    return signals[:5]


def main():
    args = parse_args()
    code = args.code.strip()
    if not code.isdigit() or len(code) != 6:
        raise SystemExit("stock code must be 6 digits")

    import akshare as ak  # type: ignore

    label, limit = timeframe_meta(args.timeframe)
    stock_name = load_stock_name(ak, code)
    bars = build_bar_list(ak, code, args.timeframe)
    if not bars:
        raise SystemExit("no bars found")

    clipped = bars[-limit:]
    payload = {
        "stockCode": code,
        "stockName": stock_name,
        "timeframe": args.timeframe,
        "timeframeLabel": label,
        "source": "akshare",
        "analyzedBars": len(clipped),
        "signals": build_signals(clipped),
        "metrics": summarize_bars(clipped),
        "bars": [asdict(item) for item in clipped],
    }
    print(json.dumps(payload, ensure_ascii=False))


if __name__ == "__main__":
    main()
