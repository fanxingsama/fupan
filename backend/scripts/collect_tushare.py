from __future__ import annotations

import argparse
import contextlib
import io
import json
from datetime import datetime, timedelta
from typing import Any, Callable

from recap_collect_common import (
    build_jiuyangongshe_reason_map,
    build_sector_fallback,
    clean_reason_text,
    clean_text,
    first_limit_focus,
    format_amount,
    format_percent,
    format_price,
    stock_change_value,
    stock_record,
)
from tushare_client import get_tushare_pro, ts_code_to_symbol

INDEX_TARGETS = [
    ("mainBoard", "主板(上证指数)", "000001.SH"),
    ("chiNext", "创业板(创业板指)", "399006.SZ"),
    ("starBoard", "科创板(科创50)", "000688.SH"),
]


def safe_df_records(func: Callable[..., Any], **kwargs: Any) -> list[dict[str, Any]]:
    try:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            df = func(**kwargs)
        if df is None or getattr(df, "empty", False):
            return []
        return json.loads(df.to_json(orient="records", force_ascii=False))
    except Exception:
        return []


def format_tushare_time(value: Any) -> str:
    text = clean_text(value)
    if not text:
        return ""
    digits = "".join(ch for ch in text if ch.isdigit())
    if not digits:
        return text
    if len(digits) <= 6:
        digits = digits.zfill(6)
        return f"{digits[:2]}:{digits[2:4]}:{digits[4:6]}"
    if len(digits) >= 14:
        return f"{digits[8:10]}:{digits[10:12]}:{digits[12:14]}"
    return text


def amount_from_thousand(value: Any) -> Any:
    number = value if isinstance(value, (int, float)) else None
    return None if number is None else float(number) * 1000


def amount_from_ten_thousand(value: Any) -> Any:
    number = value if isinstance(value, (int, float)) else None
    return None if number is None else float(number) * 10000


def resolve_trade_dates(pro: Any, trade_date: str) -> tuple[str, str | None]:
    target = datetime.strptime(trade_date[:10], "%Y-%m-%d").date()
    start = (target - timedelta(days=40)).strftime("%Y%m%d")
    end = target.strftime("%Y%m%d")
    rows = safe_df_records(pro.trade_cal, exchange="SSE", start_date=start, end_date=end)
    open_dates = sorted(row["cal_date"] for row in rows if clean_text(row.get("is_open")) == "1")
    if not open_dates:
        return end, None
    eligible = [item for item in open_dates if item <= end]
    target_date = eligible[-1] if eligible else open_dates[-1]
    index = open_dates.index(target_date)
    previous = open_dates[index - 1] if index > 0 else None
    return target_date, previous


def load_stock_basic_map(pro: Any) -> dict[str, dict[str, str]]:
    rows = safe_df_records(
        pro.stock_basic,
        exchange="",
        list_status="L",
        fields="ts_code,symbol,name,industry",
    )
    return {
        clean_text(row.get("ts_code")).upper(): {
            "code": ts_code_to_symbol(row.get("ts_code")),
            "name": clean_text(row.get("name")),
            "industry": clean_text(row.get("industry")),
        }
        for row in rows
        if clean_text(row.get("ts_code"))
    }


def load_market_snapshot(pro: Any, trade_date: str, stock_basic_map: dict[str, dict[str, str]]) -> list[dict[str, Any]]:
    daily_rows = safe_df_records(pro.daily, trade_date=trade_date)
    basic_rows = safe_df_records(pro.daily_basic, trade_date=trade_date)
    basic_map = {clean_text(row.get("ts_code")).upper(): row for row in basic_rows}

    snapshot: list[dict[str, Any]] = []
    for row in daily_rows:
        ts_code = clean_text(row.get("ts_code")).upper()
        basic = basic_map.get(ts_code, {})
        info = stock_basic_map.get(ts_code, {})
        snapshot.append(
            {
                "tsCode": ts_code,
                "code": info.get("code") or ts_code_to_symbol(ts_code),
                "name": info.get("name", ""),
                "industry": info.get("industry", ""),
                "changePercent": row.get("pct_chg"),
                "price": row.get("close"),
                "openPrice": row.get("open"),
                "amount": amount_from_thousand(row.get("amount")),
                "floatMarketValue": amount_from_ten_thousand(basic.get("circ_mv")),
                "turnoverRate": basic.get("turnover_rate"),
                "amplitude": ((float(row["high"]) - float(row["low"])) / float(row["pre_close"]) * 100)
                if row.get("high") is not None and row.get("low") is not None and row.get("pre_close")
                else None,
            }
        )
    return snapshot


def build_market_index(rows: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    return {clean_text(row.get("code")): row for row in rows if clean_text(row.get("code"))}


def map_limit_rows(rows: list[dict[str, Any]]) -> list[dict[str, str]]:
    mapped: list[dict[str, str]] = []
    for row in rows:
        code = ts_code_to_symbol(row.get("ts_code"))
        board_height = str(int(float(row.get("limit_times") or 0))) if row.get("limit_times") is not None else ""
        industry = clean_text(row.get("industry"))
        mapped.append(
            stock_record(
                code=code,
                name=row.get("name"),
                board_height=board_height,
                change_percent=format_percent(row.get("pct_chg")),
                price=format_price(row.get("close")),
                industry=industry,
                concept=industry,
                amount=format_amount(row.get("amount")),
                float_market_value=format_amount(row.get("float_mv")),
                reason=clean_reason_text(row.get("up_stat")),
                seal_amount=format_amount(row.get("fd_amount")),
                turnover_rate=format_percent(row.get("turnover_ratio")),
                extra_tag=format_tushare_time(row.get("last_time") or row.get("first_time")),
            )
        )
    mapped.sort(key=lambda item: int(item.get("boardHeight") or "0"), reverse=True)
    return mapped


def map_first_limit(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    return [row for row in rows if row.get("boardHeight") == "1"]


def map_consecutive_limit(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    return [row for row in rows if int(row.get("boardHeight") or "0") >= 2]


def map_broken_limit(rows: list[dict[str, Any]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for row in rows:
        industry = clean_text(row.get("industry"))
        result.append(
            stock_record(
                code=ts_code_to_symbol(row.get("ts_code")),
                name=row.get("name"),
                change_percent=format_percent(row.get("pct_chg")),
                price=format_price(row.get("close")),
                industry=industry,
                concept=industry,
                amount=format_amount(row.get("amount")),
                float_market_value=format_amount(row.get("float_mv")),
                turnover_rate=format_percent(row.get("turnover_ratio")),
                extra_tag=format_tushare_time(row.get("first_time")),
            )
        )
    return result


def map_limit_down(rows: list[dict[str, Any]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for row in rows:
        industry = clean_text(row.get("industry"))
        open_times = clean_text(row.get("open_times"))
        result.append(
            stock_record(
                code=ts_code_to_symbol(row.get("ts_code")),
                name=row.get("name"),
                board_height=str(int(float(row.get("limit_times") or 0))) if row.get("limit_times") is not None else "",
                change_percent=format_percent(row.get("pct_chg")),
                price=format_price(row.get("close")),
                industry=industry,
                concept=industry,
                amount=format_amount(row.get("amount")),
                float_market_value=format_amount(row.get("float_mv")),
                reason=f"开板次数 {open_times}" if open_times else "",
                seal_amount=format_amount(row.get("fd_amount")),
                turnover_rate=format_percent(row.get("turnover_ratio")),
                extra_tag=format_tushare_time(row.get("last_time")),
            )
        )
    return result


def map_previous_limit_up(rows: list[dict[str, Any]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for row in rows:
        industry = clean_text(row.get("industry"))
        result.append(
            stock_record(
                code=ts_code_to_symbol(row.get("ts_code")),
                name=row.get("name"),
                board_height=str(int(float(row.get("limit_times") or 0))) if row.get("limit_times") is not None else "",
                change_percent=format_percent(row.get("pct_chg")),
                price=format_price(row.get("close")),
                industry=industry,
                concept=industry,
                amount=format_amount(row.get("amount")),
                float_market_value=format_amount(row.get("float_mv")),
                reason=clean_reason_text(row.get("up_stat")),
                turnover_rate=format_percent(row.get("turnover_ratio")),
                extra_tag=format_tushare_time(row.get("last_time")),
            )
        )
    return result


def map_previous_broken_limit(rows: list[dict[str, Any]], market_index: dict[str, dict[str, Any]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for row in rows:
        code = ts_code_to_symbol(row.get("ts_code"))
        quote = market_index.get(code, {})
        industry = clean_text(quote.get("industry") or row.get("industry"))
        result.append(
            stock_record(
                code=code,
                name=quote.get("name") or row.get("name"),
                change_percent=format_percent(quote.get("changePercent") if quote else row.get("pct_chg")),
                price=format_price(quote.get("price") if quote else row.get("close")),
                industry=industry,
                concept=industry,
                amount=format_amount(quote.get("amount") if quote else row.get("amount")),
                float_market_value=format_amount(quote.get("floatMarketValue") if quote else row.get("float_mv")),
                turnover_rate=format_percent(quote.get("turnoverRate") if quote else row.get("turnover_ratio")),
                amplitude=format_percent(quote.get("amplitude")),
                open_price=format_price(quote.get("openPrice")),
                extra_tag="昨日炸板池跟踪",
            )
        )
    return result


def map_previous_limit_feedback(rows: list[dict[str, Any]], market_index: dict[str, dict[str, Any]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for row in rows:
        code = ts_code_to_symbol(row.get("ts_code"))
        quote = market_index.get(code, {})
        industry = clean_text(quote.get("industry") or row.get("industry"))
        board_height = str(int(float(row.get("limit_times") or 0))) if row.get("limit_times") is not None else ""
        result.append(
            stock_record(
                code=code,
                name=quote.get("name") or row.get("name"),
                board_height=board_height,
                change_percent=format_percent(quote.get("changePercent") if quote else row.get("pct_chg")),
                price=format_price(quote.get("price") if quote else row.get("close")),
                industry=industry,
                concept=industry,
                amount=format_amount(quote.get("amount") if quote else row.get("amount")),
                float_market_value=format_amount(quote.get("floatMarketValue") if quote else row.get("float_mv")),
                reason=clean_reason_text(row.get("up_stat")),
                turnover_rate=format_percent(quote.get("turnoverRate") if quote else row.get("turnover_ratio")),
                amplitude=format_percent(quote.get("amplitude")),
                open_price=format_price(quote.get("openPrice")),
                extra_tag="昨日连板跟踪",
            )
        )
    return result


def enrich_rows_with_jiuyangongshe_reason(rows: list[dict[str, str]], reason_map: dict[str, dict[str, str]]) -> list[dict[str, str]]:
    enriched: list[dict[str, str]] = []
    for row in rows:
        current = dict(row)
        code_key = f"code:{clean_text(current.get('code'))}"
        name_key = f"name:{clean_text(current.get('name'))}"
        reason_item = reason_map.get(code_key) or reason_map.get(name_key)
        if reason_item and clean_text(reason_item.get("reason")):
            current["reason"] = clean_text(reason_item["reason"])
        enriched.append(current)
    return enriched


def map_sector_rows(rows: list[dict[str, Any]]) -> list[dict[str, str]]:
    ordered = sorted(rows, key=lambda item: int(item.get("rank") or 9999))
    result: list[dict[str, str]] = []
    for row in ordered[:10]:
        reason = f"连板高度 {clean_text(row.get('up_stat'))} / 涨停 {clean_text(row.get('up_nums'))} / 连板家数 {clean_text(row.get('cons_nums'))}"
        result.append(
            {
                "name": clean_text(row.get("name")),
                "changePercent": format_percent(row.get("pct_chg")),
                "reason": reason,
            }
        )
    return result


def build_board_indexes(pro: Any, trade_date: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for key, label, ts_code in INDEX_TARGETS:
        rows = safe_df_records(pro.index_daily, ts_code=ts_code, start_date=trade_date, end_date=trade_date)
        if not rows:
            continue
        row = rows[0]
        result.append(
            {
                "key": key,
                "label": label,
                "code": f"{ts_code[-2:].lower()}{ts_code[:6]}",
                "latest": format_price(row.get("close")),
                "changeAmount": format_price(row.get("change")),
                "changePercent": format_percent(row.get("pct_chg")),
            }
        )
    return result


def market_stats_from_rows(rows: list[dict[str, Any]], first_limit_count: int) -> dict[str, Any]:
    up_count = 0
    down_count = 0
    flat_count = 0
    total_turnover = 0.0
    for row in rows:
        amount = row.get("amount")
        if isinstance(amount, (int, float)):
            total_turnover += float(amount)
        change = row.get("changePercent")
        if not isinstance(change, (int, float)):
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
        "totalTurnover": format_amount(total_turnover),
    }


def is_gem_star(code: str) -> bool:
    return code.startswith(("300", "301", "688"))


def is_main_board(code: str) -> bool:
    return code.startswith(("000", "001", "002", "003", "600", "601", "603", "605"))


def build_recent_trade_dates(pro: Any, trade_date: str, count: int) -> list[str]:
    target = datetime.strptime(trade_date, "%Y%m%d").date()
    start = (target - timedelta(days=60)).strftime("%Y%m%d")
    rows = safe_df_records(pro.trade_cal, exchange="SSE", start_date=start, end_date=trade_date)
    open_dates = sorted(row["cal_date"] for row in rows if clean_text(row.get("is_open")) == "1" and row.get("cal_date") <= trade_date)
    return open_dates[-count:]


def load_history_change_map(pro: Any, trade_dates: list[str]) -> dict[str, dict[str, Any]]:
    history: dict[str, list[dict[str, Any]]] = {}
    for trade_date in trade_dates:
        for row in safe_df_records(pro.daily, trade_date=trade_date):
            ts_code = clean_text(row.get("ts_code")).upper()
            history.setdefault(ts_code, []).append(row)

    result: dict[str, dict[str, Any]] = {}
    for ts_code, rows in history.items():
        if len(rows) < 2:
            continue
        ordered = sorted(rows, key=lambda item: clean_text(item.get("trade_date")))
        first = ordered[0]
        last = ordered[-1]
        first_close = first.get("close")
        last_close = last.get("close")
        if not isinstance(first_close, (int, float)) or not isinstance(last_close, (int, float)) or not first_close:
            continue
        result[ts_code] = {
            "changeValue": ((float(last_close) - float(first_close)) / float(first_close)) * 100,
        }
    return result


def map_top_10_day(
    history_change_map: dict[str, dict[str, Any]],
    matcher: Callable[[str], bool],
    market_index: dict[str, dict[str, Any]],
) -> list[dict[str, str]]:
    candidates: list[dict[str, Any]] = []
    for code, row in market_index.items():
        ts_code = clean_text(row.get("tsCode")).upper()
        change = history_change_map.get(ts_code)
        if not change or not matcher(code):
            continue
        candidates.append(
            {
                "code": code,
                "name": row.get("name", ""),
                "industry": row.get("industry", ""),
                "changeValue": change["changeValue"],
                "changePercent": f"{change['changeValue']:.2f}%",
                "price": row.get("price"),
                "amount": row.get("amount"),
                "floatMarketValue": row.get("floatMarketValue"),
                "turnoverRate": row.get("turnoverRate"),
            }
        )
    ordered = sorted(candidates, key=lambda item: item["changeValue"], reverse=True)[:10]
    return [
        stock_record(
            code=item["code"],
            name=item["name"],
            change_percent=item["changePercent"],
            price=format_price(item["price"]),
            industry=item["industry"],
            concept=item["industry"],
            amount=format_amount(item["amount"]),
            float_market_value=format_amount(item["floatMarketValue"]),
            turnover_rate=format_percent(item["turnoverRate"]),
        )
        for item in ordered
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--date", required=True)
    parser.add_argument("--sleep", type=float, default=0.0)
    args = parser.parse_args()

    pro = get_tushare_pro()
    trade_date_em, previous_trade_date_em = resolve_trade_dates(pro, args.date)
    stock_basic_map = load_stock_basic_map(pro)
    market_rows = load_market_snapshot(pro, trade_date_em, stock_basic_map)
    market_index = build_market_index(market_rows)

    limit_up_rows = safe_df_records(pro.limit_list_d, trade_date=trade_date_em, limit_type="U")
    previous_limit_up_rows = safe_df_records(pro.limit_list_d, trade_date=previous_trade_date_em, limit_type="U") if previous_trade_date_em else []
    broken_limit_rows = safe_df_records(pro.limit_list_d, trade_date=trade_date_em, limit_type="Z")
    limit_down_rows = safe_df_records(pro.limit_list_d, trade_date=trade_date_em, limit_type="D")
    concept_rows = safe_df_records(pro.limit_cpt_list, trade_date=trade_date_em)

    previous_broken_rows: list[dict[str, Any]] = []
    if previous_trade_date_em:
        previous_broken_rows = safe_df_records(pro.limit_list_d, trade_date=previous_trade_date_em, limit_type="Z")

    jiuyangongshe_reason_map = build_jiuyangongshe_reason_map(
        f"{trade_date_em[:4]}-{trade_date_em[4:6]}-{trade_date_em[6:]}"
    )

    limit_up_all = map_limit_rows(limit_up_rows)
    first_limit_today = map_first_limit(limit_up_all)
    limit_up_today = map_consecutive_limit(limit_up_all)
    broken_limit_today = map_broken_limit(broken_limit_rows)
    broken_limit_yesterday_feedback = map_previous_broken_limit(previous_broken_rows, market_index)
    limit_down_today = map_limit_down(limit_down_rows)
    previous_limit_feedback_all = map_previous_limit_feedback(previous_limit_up_rows, market_index)
    previous_limit_up_feedback = map_consecutive_limit(previous_limit_feedback_all)
    previous_first_limit_feedback = map_first_limit(previous_limit_feedback_all)

    limit_up_today = enrich_rows_with_jiuyangongshe_reason(limit_up_today, jiuyangongshe_reason_map)
    first_limit_today = enrich_rows_with_jiuyangongshe_reason(first_limit_today, jiuyangongshe_reason_map)
    previous_first_limit_feedback = enrich_rows_with_jiuyangongshe_reason(
        previous_first_limit_feedback,
        jiuyangongshe_reason_map,
    )

    top_up_sectors = map_sector_rows(concept_rows)
    negative_rows = [row for row in broken_limit_yesterday_feedback if stock_change_value(row) < 0] + limit_down_today
    top_down_sectors = build_sector_fallback(
        positive_rows=negative_rows,
        negative_rows=[],
        top=False,
    )

    recent_trade_dates = build_recent_trade_dates(pro, trade_date_em, 11)
    history_change_map = load_history_change_map(pro, recent_trade_dates)

    report = {
        "tradeDate": f"{trade_date_em[:4]}-{trade_date_em[4:6]}-{trade_date_em[6:]}",
        "createdAt": datetime.now().astimezone().isoformat(),
        "marketStats": market_stats_from_rows(market_rows, len(first_limit_today)),
        "boardIndexes": build_board_indexes(pro, trade_date_em),
        "brokenLimitToday": broken_limit_today,
        "brokenLimitYesterdayFeedback": broken_limit_yesterday_feedback,
        "limitUpToday": limit_up_today,
        "limitUpYesterdayFeedback": previous_limit_up_feedback,
        "firstLimitToday": first_limit_today,
        "firstLimitYesterdayFeedback": previous_first_limit_feedback,
        "limitDownToday": limit_down_today,
        "topUpSectors": top_up_sectors,
        "topDownSectors": top_down_sectors,
        "top10DayGainGemStar": map_top_10_day(history_change_map, is_gem_star, market_index),
        "top10DayGainMainBoard": map_top_10_day(history_change_map, is_main_board, market_index),
        "firstLimitSectorFocus": first_limit_focus(first_limit_today),
        "dataSource": "tushare+jiuyangongshe",
        "notes": "行情主数据来自 Tushare；涨停原因优先通过韭研公社已登录会话抓取“全部异动解析”页数据；若韭研会话过期，需要重新登录专用会话。分钟级个股分析接口未切换，因为当前购买权限不包含 Tushare 分钟线接口。",
    }
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
