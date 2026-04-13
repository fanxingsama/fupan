from __future__ import annotations

import argparse
import json
import time
from collections import Counter
from datetime import datetime
from typing import Any


def clean_text(value: Any) -> str:
    if value is None:
        return ""
    text = str(value).strip()
    return "" if text == "nan" else text


def pick_value(row: dict[str, Any], aliases: list[str]) -> str:
    if not aliases:
        return ""
    lowered = {str(key).lower(): key for key in row.keys()}
    for alias in aliases:
        alias_lower = alias.lower()
        for candidate, original_key in lowered.items():
            if alias_lower in candidate:
                return clean_text(row[original_key])
    return ""


def normalize_rows(rows: list[dict[str, Any]], aliases_by_field: dict[str, list[str]]) -> list[dict[str, str]]:
    return [{field: pick_value(row, aliases) for field, aliases in aliases_by_field.items()} for row in rows]


def sort_by_board_height(rows: list[dict[str, str]]) -> list[dict[str, str]]:
    def score(item: dict[str, str]) -> int:
        text = item.get("boardHeight", "").replace("板", "").strip()
        if not text:
            return -1
        try:
            return int(float(text))
        except ValueError:
            return -1

    return sorted(rows, key=score, reverse=True)


QUERY_SPECS = [
    {
        "key": "brokenLimitToday",
        "query": "{trade_date}炸板股票，非ST，代码，简称，涨跌幅，最新价，所属行业，所属概念，成交额，流通市值",
        "per_page": 100,
        "loop": True,
        "fields": {
            "code": ["股票代码", "code"],
            "name": ["股票简称", "简称", "名称"],
            "boardHeight": [],
            "changePercent": ["涨跌幅", "最新涨跌幅"],
            "price": ["现价", "最新价", "收盘价"],
            "industry": ["所属同花顺行业", "所属行业", "行业"],
            "concept": ["所属概念", "概念"],
            "amount": ["成交额"],
            "floatMarketValue": ["a股市值(不含限售股)", "流通市值", "自由流通市值"],
            "reason": [],
            "sealAmount": [],
            "auctionChangePercent": [],
            "turnoverRate": [],
            "amplitude": [],
            "openPrice": [],
            "extraTag": ["炸板时间", "首次炸板时间"],
        },
    },
    {
        "key": "brokenLimitYesterdayFeedback",
        "query": "{trade_date}昨日炸板股票今日表现，代码，简称，涨跌幅，最新价，成交额，今开，振幅",
        "per_page": 100,
        "loop": True,
        "fields": {
            "code": ["股票代码", "code"],
            "name": ["股票简称", "简称", "名称"],
            "boardHeight": [],
            "changePercent": ["涨跌幅", "今日涨跌幅"],
            "price": ["现价", "最新价"],
            "industry": [],
            "concept": [],
            "amount": ["成交额"],
            "floatMarketValue": [],
            "reason": [],
            "sealAmount": [],
            "auctionChangePercent": [],
            "turnoverRate": [],
            "amplitude": ["振幅"],
            "openPrice": ["今开", "开盘价"],
            "extraTag": [],
        },
    },
    {
        "key": "limitUpToday",
        "query": "{trade_date}连板股票，代码，简称，几连板，涨停原因类别，最新价，所属概念，成交额，流通市值，换手率，封单额，竞价涨幅",
        "per_page": 100,
        "loop": True,
        "fields": {
            "code": ["股票代码", "code"],
            "name": ["股票简称", "简称", "名称"],
            "boardHeight": ["几连板", "连续涨停天数", "连板数"],
            "changePercent": ["涨跌幅", "最新涨跌幅"],
            "price": ["现价", "最新价"],
            "industry": [],
            "concept": ["所属概念", "概念"],
            "amount": ["成交额"],
            "floatMarketValue": ["a股市值(不含限售股)", "流通市值"],
            "reason": ["涨停原因类别", "涨停原因"],
            "sealAmount": ["封单额"],
            "auctionChangePercent": ["竞价涨幅"],
            "turnoverRate": ["换手率"],
            "amplitude": [],
            "openPrice": [],
            "extraTag": [],
        },
        "postprocess": sort_by_board_height,
    },
    {
        "key": "limitUpYesterdayFeedback",
        "query": "{trade_date}昨日连板股票今日表现，是否继续连板，代码，简称，所属概念，最新价，成交额，流通市值，封单额，竞价涨幅，换手率",
        "per_page": 100,
        "loop": True,
        "fields": {
            "code": ["股票代码", "code"],
            "name": ["股票简称", "简称", "名称"],
            "boardHeight": ["是否继续连板", "今日是否连板"],
            "changePercent": ["涨跌幅", "今日涨跌幅"],
            "price": ["现价", "最新价"],
            "industry": [],
            "concept": ["所属概念", "概念"],
            "amount": ["成交额"],
            "floatMarketValue": ["a股市值(不含限售股)", "流通市值"],
            "reason": [],
            "sealAmount": ["封单额"],
            "auctionChangePercent": ["竞价涨幅"],
            "turnoverRate": ["换手率"],
            "amplitude": [],
            "openPrice": [],
            "extraTag": [],
        },
    },
    {
        "key": "firstLimitToday",
        "query": "{trade_date}首板股票，非ST，代码，简称，涨停原因类别，所属概念，成交额，封单额，最新价，流通市值，所属行业",
        "per_page": 200,
        "loop": True,
        "fields": {
            "code": ["股票代码", "code"],
            "name": ["股票简称", "简称", "名称"],
            "boardHeight": [],
            "changePercent": ["涨跌幅", "最新涨跌幅"],
            "price": ["现价", "最新价"],
            "industry": ["所属同花顺行业", "所属行业", "行业"],
            "concept": ["所属概念", "概念"],
            "amount": ["成交额"],
            "floatMarketValue": ["a股市值(不含限售股)", "流通市值"],
            "reason": ["涨停原因类别", "涨停原因"],
            "sealAmount": ["封单额"],
            "auctionChangePercent": [],
            "turnoverRate": [],
            "amplitude": [],
            "openPrice": [],
            "extraTag": [],
        },
    },
    {
        "key": "limitDownToday",
        "query": "{trade_date}跌停股票，非ST，代码，简称，跌停原因，所属概念",
        "per_page": 100,
        "loop": True,
        "fields": {
            "code": ["股票代码", "code"],
            "name": ["股票简称", "简称", "名称"],
            "boardHeight": [],
            "changePercent": ["涨跌幅", "最新涨跌幅"],
            "price": ["现价", "最新价"],
            "industry": [],
            "concept": ["所属概念", "概念"],
            "amount": [],
            "floatMarketValue": [],
            "reason": ["跌停原因", "跌停原因类别"],
            "sealAmount": [],
            "auctionChangePercent": [],
            "turnoverRate": [],
            "amplitude": [],
            "openPrice": [],
            "extraTag": [],
        },
    },
    {
        "key": "top10DayGainGemStar",
        "query": "{trade_date}创业板或科创板股票按近10日涨跌幅从高到低排序前40，非ST，代码，简称，最新价，所属概念，成交额，流通市值，近10日涨跌幅",
        "per_page": 40,
        "loop": False,
        "fields": {
            "code": ["股票代码", "code"],
            "name": ["股票简称", "简称", "名称"],
            "boardHeight": [],
            "changePercent": ["近10日涨跌幅", "10日涨跌幅"],
            "price": ["现价", "最新价"],
            "industry": [],
            "concept": ["所属概念", "概念"],
            "amount": ["成交额"],
            "floatMarketValue": ["a股市值(不含限售股)", "流通市值"],
            "reason": [],
            "sealAmount": [],
            "auctionChangePercent": [],
            "turnoverRate": [],
            "amplitude": [],
            "openPrice": [],
            "extraTag": [],
        },
    },
    {
        "key": "top10DayGainMainBoard",
        "query": "{trade_date}主板股票按近10日涨跌幅从高到低排序前40，非ST，代码，简称，最新价，所属概念，成交额，流通市值，近10日涨跌幅",
        "per_page": 40,
        "loop": False,
        "fields": {
            "code": ["股票代码", "code"],
            "name": ["股票简称", "简称", "名称"],
            "boardHeight": [],
            "changePercent": ["近10日涨跌幅", "10日涨跌幅"],
            "price": ["现价", "最新价"],
            "industry": [],
            "concept": ["所属概念", "概念"],
            "amount": ["成交额"],
            "floatMarketValue": ["a股市值(不含限售股)", "流通市值"],
            "reason": [],
            "sealAmount": [],
            "auctionChangePercent": [],
            "turnoverRate": [],
            "amplitude": [],
            "openPrice": [],
            "extraTag": [],
        },
    },
]


SECTOR_UP_FIELDS = {
    "name": ["指数简称", "概念名称", "板块名称", "简称"],
    "changePercent": ["涨跌幅"],
    "reason": ["上涨原因", "板块异动原因"],
}

SECTOR_DOWN_FIELDS = {
    "name": ["指数简称", "概念名称", "板块名称", "简称"],
    "changePercent": ["涨跌幅"],
    "reason": ["下跌原因", "板块异动原因"],
}


def run_query(client: Any, query: str, cookie: str, per_page: int, loop: bool | int) -> list[dict[str, Any]]:
    data_frame = client.get(query=query, cookie=cookie, perpage=per_page, loop=loop)
    if data_frame is None:
        return []
    return json.loads(data_frame.to_json(orient="records", force_ascii=False))


def sector_rows(client: Any, trade_date: str, cookie: str, query_text: str, aliases: dict[str, list[str]], sleep_seconds: float) -> list[dict[str, str]]:
    rows = run_query(client, query_text.format(trade_date=trade_date), cookie, 10, False)
    time.sleep(sleep_seconds)
    return [{field: pick_value(row, names) for field, names in aliases.items()} for row in rows]


def first_limit_focus(rows: list[dict[str, str]]) -> dict[str, int]:
    counter = Counter()
    for row in rows:
        counter[row.get("industry") or "未识别板块"] += 1
    return dict(counter.most_common(10))


def count_query(client: Any, trade_date: str, cookie: str, condition: str, sleep_seconds: float) -> int:
    rows = run_query(client, f"{trade_date}A股非ST股票{condition}", cookie, 5000, True)
    time.sleep(sleep_seconds)
    return len(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--date", required=True)
    parser.add_argument("--cookie", required=True)
    parser.add_argument("--sleep", type=float, default=1.2)
    args = parser.parse_args()

    try:
        import pywencai  # type: ignore
    except ImportError as exc:
        raise SystemExit("pywencai is required. Install it with pip install pywencai") from exc

    datasets: dict[str, list[dict[str, str]]] = {}
    for spec in QUERY_SPECS:
        rows = run_query(
            pywencai,
            spec["query"].format(trade_date=args.date),
            args.cookie,
            spec["per_page"],
            spec["loop"],
        )
        normalized = normalize_rows(rows, spec["fields"])
        postprocess = spec.get("postprocess")
        if callable(postprocess):
            normalized = postprocess(normalized)
        datasets[spec["key"]] = normalized
        time.sleep(args.sleep)

    top_up_sectors = sector_rows(
        pywencai,
        args.date,
        args.cookie,
        "{trade_date}概念板块涨幅前10名，板块名称，涨跌幅，上涨原因",
        SECTOR_UP_FIELDS,
        args.sleep,
    )
    top_down_sectors = sector_rows(
        pywencai,
        args.date,
        args.cookie,
        "{trade_date}概念板块跌幅前10名，板块名称，涨跌幅，下跌原因",
        SECTOR_DOWN_FIELDS,
        args.sleep,
    )

    up_count = count_query(pywencai, args.date, args.cookie, "涨跌幅大于0", args.sleep)
    down_count = count_query(pywencai, args.date, args.cookie, "涨跌幅小于0", args.sleep)
    flat_count = count_query(pywencai, args.date, args.cookie, "涨跌幅等于0", args.sleep)

    report = {
        "tradeDate": args.date,
        "createdAt": datetime.now().astimezone().isoformat(),
        "marketStats": {
            "upCount": up_count,
            "downCount": down_count,
            "flatCount": flat_count,
            "firstLimitCount": len(datasets["firstLimitToday"]),
        },
        "brokenLimitToday": datasets["brokenLimitToday"],
        "brokenLimitYesterdayFeedback": datasets["brokenLimitYesterdayFeedback"],
        "limitUpToday": datasets["limitUpToday"],
        "limitUpYesterdayFeedback": datasets["limitUpYesterdayFeedback"],
        "firstLimitToday": datasets["firstLimitToday"],
        "limitDownToday": datasets["limitDownToday"],
        "topUpSectors": top_up_sectors,
        "topDownSectors": top_down_sectors,
        "top10DayGainGemStar": datasets["top10DayGainGemStar"],
        "top10DayGainMainBoard": datasets["top10DayGainMainBoard"],
        "firstLimitSectorFocus": first_limit_focus(datasets["firstLimitToday"]),
        "dataSource": "wencai",
        "notes": "Collected from Wencai. Some aliases may require tuning on specific trade dates.",
    }
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
