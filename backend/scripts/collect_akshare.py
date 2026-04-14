from __future__ import annotations

import argparse
import contextlib
import io
import json
import math
import os
import time
from collections import Counter
from datetime import datetime, timezone
from typing import Any, Callable

K_DATE = "\u65e5\u671f"
K_CHANGE = "\u6da8\u8dcc\u5e45"
K_CLOSE = "\u6536\u76d8"
K_LATEST = "\u6700\u65b0\u4ef7"
K_AMOUNT = "\u6210\u4ea4\u989d"
K_AMPLITUDE = "\u632f\u5e45"
K_OPEN = "\u5f00\u76d8"
K_CODE = "\u4ee3\u7801"
K_NAME = "\u540d\u79f0"
K_INDUSTRY = "\u6240\u5c5e\u884c\u4e1a"
K_BOARD_COUNT = "\u8fde\u677f\u6570"
K_FLOAT_MV = "\u6d41\u901a\u5e02\u503c"
K_LIMIT_STAT = "\u6da8\u505c\u7edf\u8ba1"
K_SEAL_FUND = "\u5c01\u677f\u8d44\u91d1"
K_TURNOVER = "\u6362\u624b\u7387"
K_LAST_LIMIT_TIME = "\u6700\u540e\u5c01\u677f\u65f6\u95f4"
K_FIRST_LIMIT_TIME = "\u9996\u6b21\u5c01\u677f\u65f6\u95f4"
K_CONT_DOWN = "\u8fde\u7eed\u8dcc\u505c"
K_OPEN_COUNT = "\u5f00\u677f\u6b21\u6570"
K_PREV_BOARD_COUNT = "\u6628\u65e5\u8fde\u677f\u6570"
K_PREV_LIMIT_TIME = "\u6628\u65e5\u5c01\u677f\u65f6\u95f4"
K_BOARD_NAME = "\u677f\u5757\u540d\u79f0"
K_LEADER = "\u9886\u6da8\u80a1\u7968"
K_LEADER_CHANGE = "\u9886\u6da8\u80a1\u7968-\u6da8\u8dcc\u5e45"
K_STOCK_CODE = "\u80a1\u7968\u4ee3\u7801"
K_STOCK_NAME = "\u80a1\u7968\u7b80\u79f0"
K_STAGE_CHANGE = "\u9636\u6bb5\u6da8\u8dcc\u5e45"
K_TEN_DAY_CHANGE = "10\u65e5\u6da8\u8dcc\u5e45"
K_CONT_TURNOVER = "\u8fde\u7eed\u6362\u624b\u7387"
K_FUND_INFLOW = "\u8d44\u91d1\u6d41\u5165\u51c0\u989d"
TEN_DAY_RANK = "10\u65e5\u6392\u884c"
INSTANT_RANK = "\u5373\u65f6"
TODAY = "\u4eca\u65e5"
CONCEPT_FLOW = "\u6982\u5ff5\u8d44\u91d1\u6d41"
K_TODAY_CHANGE = "\u4eca\u65e5\u6da8\u8dcc\u5e45"
K_MAIN_NET_INFLOW = "\u4e3b\u529b\u51c0\u6d41\u5165-\u51c0\u989d"
YI = "\u4ebf"
WAN = "\u4e07"


# 这一组工具函数负责把 AKShare / 东财的原始字段清洗成统一格式，
# 后面的映射函数就可以只处理一种数据口径。
def clean_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float) and math.isnan(value):
        return ""
    text = str(value).strip()
    return "" if text.lower() == "nan" else text


def normalize_code(value: Any) -> str:
    text = clean_text(value)
    digits = "".join(ch for ch in text if ch.isdigit())
    return digits.zfill(6) if digits else text


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


def parse_board_height_value(value: Any) -> int:
    number = to_float(value)
    return int(number) if number is not None else 0


def first_present(row: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key not in row:
            continue
        value = row[key]
        if clean_text(value):
            return value
    return None


def df_to_rows(data_frame: Any) -> list[dict[str, Any]]:
    if data_frame is None or getattr(data_frame, "empty", False):
        return []
    return json.loads(data_frame.to_json(orient="records", force_ascii=False))


@contextlib.contextmanager
def without_proxy_env() -> Any:
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


# 所有上游接口都通过 safe_call 进入，单个接口失败时返回空列表而不是直接中断整份复盘。
def safe_call(func: Callable[..., Any], bypass_proxy: bool = False, **kwargs: Any) -> list[dict[str, Any]]:
    try:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            if bypass_proxy:
                with without_proxy_env():
                    return df_to_rows(func(**kwargs))
            return df_to_rows(func(**kwargs))
    except Exception:
        return []


def normalize_trade_date_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        number = float(value)
        if math.isnan(number):
            return ""
        if number > 100000000000:
            return datetime.fromtimestamp(number / 1000, tz=timezone.utc).date().isoformat()
        if number > 10000000:
            text = str(int(number))
            if len(text) == 8:
                return f"{text[:4]}-{text[4:6]}-{text[6:]}"
    text = clean_text(value)
    if text.isdigit():
        if len(text) >= 12:
            return datetime.fromtimestamp(int(text) / 1000, tz=timezone.utc).date().isoformat()
        if len(text) == 8:
            return f"{text[:4]}-{text[4:6]}-{text[6:]}"
    return text[:10]


def parse_trade_dates(rows: list[dict[str, Any]]) -> list[str]:
    result: list[str] = []
    for row in rows:
        for key in ("trade_date", "date", K_DATE):
            text = normalize_trade_date_value(row.get(key))
            if text:
                result.append(text)
                break
    return sorted(set(result))


def resolve_trade_dates(ak: Any, trade_date: str) -> tuple[str, str | None]:
    rows = safe_call(ak.tool_trade_date_hist_sina)
    trade_dates = parse_trade_dates(rows)
    if not trade_dates:
        return trade_date.replace("-", ""), None

    normalized = trade_date[:10]
    eligible = [item for item in trade_dates if item <= normalized]
    target = eligible[-1] if eligible else trade_dates[-1]
    index = trade_dates.index(target)
    previous = trade_dates[index - 1] if index > 0 else None
    return target.replace("-", ""), None if previous is None else previous.replace("-", "")


# 10 日排行接口本身字段不完整，所以这里额外补雪球的实时行情和个股资料。
def load_daily_quote(ak: Any, code: str, trade_date_em: str) -> dict[str, str]:
    rows = safe_call(
        ak.stock_zh_a_hist,
        bypass_proxy=True,
        symbol=code,
        period="daily",
        start_date=trade_date_em,
        end_date=trade_date_em,
        adjust="",
    )
    if not rows:
        return {}
    row = rows[-1]
    return {
        "changePercent": format_percent(first_present(row, K_CHANGE)),
        "price": format_price(first_present(row, K_CLOSE, K_LATEST)),
        "amount": format_amount(first_present(row, K_AMOUNT)),
        "turnoverRate": format_percent(first_present(row, K_TURNOVER)),
        "amplitude": format_percent(first_present(row, K_AMPLITUDE)),
        "openPrice": format_price(first_present(row, K_OPEN)),
    }


def to_xq_symbol(code: str) -> str:
    if code.startswith(("600", "601", "603", "605", "688")):
        return f"SH{code}"
    return f"SZ{code}"


def load_xq_quote(ak: Any, code: str) -> dict[str, str]:
    rows = safe_call(ak.stock_individual_spot_xq, bypass_proxy=True, symbol=to_xq_symbol(code))
    if not rows:
        return {}
    quote = {clean_text(row.get("item")): row.get("value") for row in rows}
    return {
        "price": format_price(first_present(quote, "\u73b0\u4ef7")),
        "amount": format_amount(first_present(quote, "\u6210\u4ea4\u989d")),
        "floatMarketValue": format_amount(first_present(quote, "\u6d41\u901a\u503c")),
        "turnoverRate": format_percent(first_present(quote, "\u5468\u8f6c\u7387")),
    }


def load_xq_profile(ak: Any, code: str) -> dict[str, str]:
    rows = safe_call(ak.stock_individual_basic_info_xq, bypass_proxy=True, symbol=to_xq_symbol(code))
    if not rows:
        return {}
    profile = {clean_text(row.get("item")): row.get("value") for row in rows}
    affiliate_industry = profile.get("affiliate_industry")
    if isinstance(affiliate_industry, dict):
        industry = clean_text(affiliate_industry.get("ind_name"))
    else:
        industry = clean_text(affiliate_industry)
    return {"industry": industry, "concept": industry}


def build_market_rows(ak: Any) -> list[dict[str, Any]]:
    rows = safe_call(ak.stock_fund_flow_individual, symbol=INSTANT_RANK)
    if rows:
        return rows
    return safe_call(ak.stock_zh_a_spot_em, bypass_proxy=True)


def build_spot_index(rows: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for row in rows:
        code = normalize_code(first_present(row, K_CODE, K_STOCK_CODE))
        if code:
            result[code] = row
    return result


def quote_from_market_row(row: dict[str, Any]) -> dict[str, str]:
    if not row:
        return {}
    return {
        "changePercent": format_percent(first_present(row, K_CHANGE)),
        "price": format_price(first_present(row, K_LATEST)),
        "amount": format_amount(first_present(row, K_AMOUNT)),
        "floatMarketValue": format_amount(first_present(row, K_FLOAT_MV)),
        "turnoverRate": format_percent(first_present(row, K_TURNOVER, K_CONT_TURNOVER)),
    }


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


# 以下几组 map_* 方法把不同榜单统一映射成前端需要的 StockRecord / SectorRecord 结构。
def map_limit_up(rows: list[dict[str, Any]]) -> list[dict[str, str]]:
    mapped: list[dict[str, str]] = []
    for row in rows:
        industry = clean_text(first_present(row, K_INDUSTRY))
        mapped.append(
            stock_record(
                code=first_present(row, K_CODE),
                name=first_present(row, K_NAME),
                board_height=clean_text(first_present(row, K_BOARD_COUNT)),
                change_percent=format_percent(first_present(row, K_CHANGE)),
                price=format_price(first_present(row, K_LATEST)),
                industry=industry,
                concept=industry,
                amount=format_amount(first_present(row, K_AMOUNT)),
                float_market_value=format_amount(first_present(row, K_FLOAT_MV)),
                reason=clean_text(first_present(row, K_LIMIT_STAT)),
                seal_amount=format_amount(first_present(row, K_SEAL_FUND)),
                turnover_rate=format_percent(first_present(row, K_TURNOVER)),
                extra_tag=clean_text(first_present(row, K_LAST_LIMIT_TIME, K_FIRST_LIMIT_TIME)),
            )
        )
    mapped.sort(key=lambda item: int(item["boardHeight"] or "0"), reverse=True)
    return mapped


def map_first_limit(limit_up_rows: list[dict[str, str]]) -> list[dict[str, str]]:
    return [row for row in limit_up_rows if row.get("boardHeight") == "1"]


def map_consecutive_limit(limit_up_rows: list[dict[str, str]]) -> list[dict[str, str]]:
    return [row for row in limit_up_rows if parse_board_height_value(row.get("boardHeight")) >= 2]


def map_broken_limit(rows: list[dict[str, Any]]) -> list[dict[str, str]]:
    mapped: list[dict[str, str]] = []
    for row in rows:
        industry = clean_text(first_present(row, K_INDUSTRY))
        mapped.append(
            stock_record(
                code=first_present(row, K_CODE),
                name=first_present(row, K_NAME),
                change_percent=format_percent(first_present(row, K_CHANGE)),
                price=format_price(first_present(row, K_LATEST)),
                industry=industry,
                concept=industry,
                amount=format_amount(first_present(row, K_AMOUNT)),
                float_market_value=format_amount(first_present(row, K_FLOAT_MV)),
                turnover_rate=format_percent(first_present(row, K_TURNOVER)),
                amplitude=format_percent(first_present(row, K_AMPLITUDE)),
                extra_tag=clean_text(first_present(row, K_FIRST_LIMIT_TIME)),
            )
        )
    return mapped


def map_limit_down(rows: list[dict[str, Any]]) -> list[dict[str, str]]:
    mapped: list[dict[str, str]] = []
    for row in rows:
        industry = clean_text(first_present(row, K_INDUSTRY))
        open_count = clean_text(first_present(row, K_OPEN_COUNT))
        reason = f"\u5f00\u677f\u6b21\u6570 {open_count}" if open_count else ""
        mapped.append(
            stock_record(
                code=first_present(row, K_CODE),
                name=first_present(row, K_NAME),
                board_height=clean_text(first_present(row, K_CONT_DOWN)),
                change_percent=format_percent(first_present(row, K_CHANGE)),
                price=format_price(first_present(row, K_LATEST)),
                industry=industry,
                concept=industry,
                amount=format_amount(first_present(row, K_AMOUNT)),
                float_market_value=format_amount(first_present(row, K_FLOAT_MV)),
                reason=reason,
                seal_amount=format_amount(first_present(row, K_SEAL_FUND)),
                turnover_rate=format_percent(first_present(row, K_TURNOVER)),
                extra_tag=clean_text(first_present(row, K_LAST_LIMIT_TIME)),
            )
        )
    return mapped


def map_previous_limit_up(rows: list[dict[str, Any]]) -> list[dict[str, str]]:
    mapped: list[dict[str, str]] = []
    for row in rows:
        industry = clean_text(first_present(row, K_INDUSTRY))
        mapped.append(
            stock_record(
                code=first_present(row, K_CODE),
                name=first_present(row, K_NAME),
                board_height=clean_text(first_present(row, K_PREV_BOARD_COUNT)),
                change_percent=format_percent(first_present(row, K_CHANGE)),
                price=format_price(first_present(row, K_LATEST)),
                industry=industry,
                concept=industry,
                amount=format_amount(first_present(row, K_AMOUNT)),
                float_market_value=format_amount(first_present(row, K_FLOAT_MV)),
                reason=clean_text(first_present(row, K_LIMIT_STAT)),
                turnover_rate=format_percent(first_present(row, K_TURNOVER)),
                amplitude=format_percent(first_present(row, K_AMPLITUDE)),
                extra_tag=clean_text(first_present(row, K_PREV_LIMIT_TIME)),
            )
        )
    return mapped


def map_previous_broken_limit(
    ak: Any,
    rows: list[dict[str, Any]],
    trade_date_em: str,
    market_index: dict[str, dict[str, Any]],
) -> list[dict[str, str]]:
    mapped: list[dict[str, str]] = []
    for row in rows:
        code = normalize_code(first_present(row, K_CODE))
        # 优先用实时行情补字段，拿不到时保留炸板池原始值，避免页面出现空列。
        quote = quote_from_market_row(market_index.get(code, {}))
        quote = {
            "changePercent": quote.get("changePercent") or format_percent(first_present(row, K_CHANGE)),
            "price": quote.get("price") or format_price(first_present(row, K_LATEST, K_CLOSE)),
            "amount": quote.get("amount") or format_amount(first_present(row, K_AMOUNT)),
            "floatMarketValue": quote.get("floatMarketValue") or format_amount(first_present(row, K_FLOAT_MV)),
            "turnoverRate": quote.get("turnoverRate") or format_percent(first_present(row, K_TURNOVER)),
            "amplitude": format_percent(first_present(row, K_AMPLITUDE)),
            "openPrice": "",
        }
        industry = clean_text(first_present(row, K_INDUSTRY))
        mapped.append(
            stock_record(
                code=code,
                name=first_present(row, K_NAME),
                change_percent=quote.get("changePercent", ""),
                price=quote.get("price", ""),
                industry=industry,
                concept=industry,
                amount=quote.get("amount", ""),
                float_market_value=quote.get("floatMarketValue", ""),
                turnover_rate=quote.get("turnoverRate", ""),
                amplitude=quote.get("amplitude", ""),
                open_price=quote.get("openPrice", ""),
                extra_tag="\u6628\u65e5\u70b8\u677f\u6c60\u8ddf\u8e2a",
            )
        )
    return mapped


def map_sector_rows(rows: list[dict[str, Any]], top: bool) -> list[dict[str, str]]:
    if rows and K_TODAY_CHANGE in rows[0]:
        def fund_score(item: dict[str, Any]) -> float:
            number = to_float(first_present(item, K_TODAY_CHANGE))
            return number if number is not None else -9999.0

        ordered = sorted(rows, key=fund_score, reverse=top)
        return [
            {
                "name": clean_text(first_present(row, K_NAME)),
                "changePercent": format_percent(first_present(row, K_TODAY_CHANGE)),
                "reason": f"\u4e3b\u529b\u51c0\u6d41\u5165 {format_amount(first_present(row, K_MAIN_NET_INFLOW))}",
            }
            for row in ordered[:10]
        ]

    def score(item: dict[str, Any]) -> float:
        number = to_float(first_present(item, K_CHANGE))
        return number if number is not None else -9999.0

    ordered = sorted(rows, key=score, reverse=top)
    result: list[dict[str, str]] = []
    for row in ordered[:10]:
        leader = clean_text(first_present(row, K_LEADER))
        leader_change = format_percent(first_present(row, K_LEADER_CHANGE))
        reason = f"\u9886\u6da8\u80a1 {leader} {leader_change}".strip()
        result.append(
            {
                "name": clean_text(first_present(row, K_BOARD_NAME, K_NAME)),
                "changePercent": format_percent(first_present(row, K_CHANGE)),
                "reason": reason,
            }
        )
    return result


def stock_change_value(row: dict[str, str]) -> float:
    return to_float(row.get("changePercent")) or 0.0


# 当板块榜单接口不可用时，用已采集到的强弱个股反推板块热度，保证页面不空白。
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
        reverse=True,
    )
    result: list[dict[str, str]] = []
    for name, stats in ordered[:10]:
        average_change = stats["sum"] / stats["count"] if stats["count"] else 0.0
        result.append(
            {
                "name": name,
                "changePercent": f"{average_change:.2f}%",
                "reason": (
                    "\u57fa\u4e8e\u5df2\u91c7\u96c6\u4e2a\u80a1\u53cd\u63a8\uff1a"
                    f"{int(stats['count'])}\u53ea\uff0c\u70ed\u5ea6 {stats['score']:.1f}"
                ),
            }
        )
    return result


def is_gem_star(code: str) -> bool:
    return code.startswith(("300", "301", "688"))


def is_main_board(code: str) -> bool:
    return code.startswith(("000", "001", "002", "003", "600", "601", "603", "605"))


# 10 日涨幅榜先按排行挑选，再对入榜个股补齐价格、成交额和流通值。
def map_top_10_day(
    rows: list[dict[str, Any]],
    matcher: Callable[[str], bool],
    ak: Any,
    trade_date_em: str,
) -> list[dict[str, str]]:
    filtered: list[dict[str, Any]] = []
    for row in rows:
        code = normalize_code(first_present(row, K_STOCK_CODE, K_CODE))
        if not code or not matcher(code):
            continue
        change_value = to_float(first_present(row, K_STAGE_CHANGE, K_TEN_DAY_CHANGE))
        filtered.append(
            {
                "code": code,
                "name": clean_text(first_present(row, K_STOCK_NAME, K_NAME)),
                "changeValue": change_value if change_value is not None else -9999.0,
                "changePercent": format_percent(first_present(row, K_STAGE_CHANGE, K_TEN_DAY_CHANGE)),
                "turnoverRate": format_percent(first_present(row, K_CONT_TURNOVER)),
            }
        )
    filtered.sort(key=lambda item: item["changeValue"], reverse=True)
    top_items = filtered[:10]
    quote_cache: dict[str, dict[str, str]] = {}
    profile_cache: dict[str, dict[str, str]] = {}
    result: list[dict[str, str]] = []
    for item in top_items:
        code = item["code"]
        if code not in quote_cache:
            quote_cache[code] = load_xq_quote(ak, code)
        if code not in profile_cache:
            profile_cache[code] = load_xq_profile(ak, code)
        quote = quote_cache[code]
        profile = profile_cache[code]
        result.append(
            stock_record(
                code=code,
                name=item["name"],
                change_percent=item["changePercent"],
                price=quote.get("price", ""),
                industry=profile.get("industry", ""),
                concept=profile.get("concept", ""),
                amount=quote.get("amount", ""),
                float_market_value=quote.get("floatMarketValue", ""),
                turnover_rate=quote.get("turnoverRate", "") or item["turnoverRate"],
            )
        )
    return result


def first_limit_focus(rows: list[dict[str, str]]) -> dict[str, int]:
    counter = Counter()
    for row in rows:
        counter[row.get("industry") or "\u672a\u5206\u7c7b"] += 1
    return dict(counter.most_common(10))


def market_stats_from_rows(rows: list[dict[str, Any]], first_limit_count: int) -> dict[str, int]:
    up_count = 0
    down_count = 0
    flat_count = 0
    for row in rows:
        change = to_float(first_present(row, K_CHANGE))
        if change is None:
            continue
        if change > 0:
            up_count += 1
        elif change < 0:
            down_count += 1
        else:
            flat_count += 1
    return {
        "upCount": up_count,
        "downCount": down_count,
        "flatCount": flat_count,
        "firstLimitCount": first_limit_count,
    }


# 采集主流程：解析交易日 -> 调用各榜单接口 -> 做字段补全 -> 组装成最终 report。
def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--date", required=True)
    parser.add_argument("--sleep", type=float, default=1.2)
    args = parser.parse_args()

    try:
        import akshare as ak  # type: ignore
    except ImportError as exc:
        raise SystemExit("akshare is required. Install it with pip install akshare") from exc

    trade_date_em, previous_trade_date_em = resolve_trade_dates(ak, args.date)
    market_rows = build_market_rows(ak)
    market_index = build_spot_index(market_rows)

    limit_up_rows = safe_call(ak.stock_zt_pool_em, date=trade_date_em)
    time.sleep(args.sleep)
    previous_limit_up_rows = safe_call(ak.stock_zt_pool_previous_em, date=trade_date_em)
    time.sleep(args.sleep)
    broken_limit_rows = safe_call(ak.stock_zt_pool_zbgc_em, date=trade_date_em)
    time.sleep(args.sleep)
    limit_down_rows = safe_call(ak.stock_zt_pool_dtgc_em, date=trade_date_em)
    time.sleep(args.sleep)
    concept_rows = safe_call(
        ak.stock_sector_fund_flow_rank,
        bypass_proxy=True,
        indicator=TODAY,
        sector_type=CONCEPT_FLOW,
    )
    if not concept_rows:
        concept_rows = safe_call(ak.stock_board_concept_name_em, bypass_proxy=True)
    time.sleep(args.sleep)
    ten_day_rows = safe_call(ak.stock_fund_flow_individual, symbol=TEN_DAY_RANK)

    previous_broken_rows: list[dict[str, Any]] = []
    if previous_trade_date_em:
        time.sleep(args.sleep)
        previous_broken_rows = safe_call(ak.stock_zt_pool_zbgc_em, date=previous_trade_date_em)

    limit_up_all = map_limit_up(limit_up_rows)
    first_limit_today = map_first_limit(limit_up_all)
    limit_up_today = map_consecutive_limit(limit_up_all)
    broken_limit_today = map_broken_limit(broken_limit_rows)
    broken_limit_yesterday_feedback = map_previous_broken_limit(ak, previous_broken_rows, trade_date_em, market_index)
    limit_down_today = map_limit_down(limit_down_rows)
    previous_limit_up_feedback = map_consecutive_limit(map_previous_limit_up(previous_limit_up_rows))

    top_up_sectors = map_sector_rows(concept_rows, top=True)
    top_down_sectors = map_sector_rows(concept_rows, top=False)
    if not top_up_sectors:
        top_up_sectors = build_sector_fallback(
            positive_rows=limit_up_today + first_limit_today + broken_limit_today,
            negative_rows=[],
            top=True,
        )
    if not top_down_sectors:
        negative_rows = [row for row in broken_limit_yesterday_feedback if stock_change_value(row) < 0] + limit_down_today
        top_down_sectors = build_sector_fallback(
            positive_rows=negative_rows,
            negative_rows=[],
            top=False,
        )

    report = {
        "tradeDate": f"{trade_date_em[:4]}-{trade_date_em[4:6]}-{trade_date_em[6:]}",
        "createdAt": datetime.now().astimezone().isoformat(),
        "marketStats": market_stats_from_rows(market_rows, len(first_limit_today)),
        "brokenLimitToday": broken_limit_today,
        "brokenLimitYesterdayFeedback": broken_limit_yesterday_feedback,
        "limitUpToday": limit_up_today,
        "limitUpYesterdayFeedback": previous_limit_up_feedback,
        "firstLimitToday": first_limit_today,
        "limitDownToday": limit_down_today,
        "topUpSectors": top_up_sectors,
        "topDownSectors": top_down_sectors,
        "top10DayGainGemStar": map_top_10_day(ten_day_rows, is_gem_star, ak, trade_date_em),
        "top10DayGainMainBoard": map_top_10_day(ten_day_rows, is_main_board, ak, trade_date_em),
        "firstLimitSectorFocus": first_limit_focus(first_limit_today),
        "dataSource": "akshare",
        "notes": "\u6570\u636e\u6765\u81ea AKShare \u516c\u5f00 A \u80a1\u63a5\u53e3\uff1b\u5f53\u4e0a\u6e38\u677f\u5757\u699c\u5355\u4e0d\u53ef\u7528\u65f6\uff0c\u4f1a\u7528\u5df2\u91c7\u96c6\u7684\u4e2a\u80a1\u6570\u636e\u53cd\u63a8\u677f\u5757\u5f3a\u5f31\u3002",
    }
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
