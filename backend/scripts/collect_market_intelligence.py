from __future__ import annotations

# AI-READABLE-SCRIPT:
# Primary raw intelligence collector.
# Aggregates hot stocks, keywords, market news, stock news, and radar pages into one JSON payload.

import argparse
import contextlib
import io
import json
import math
import os
import re
import time
from collections import Counter, defaultdict
from datetime import datetime
from typing import Any, Callable

import requests
from bs4 import BeautifulSoup
from curl_cffi import requests as curl_requests

THS_ZHANGTING_URL = "https://yuanchuang.10jqka.com.cn/zhangting/"
THS_ZHANGTING_MORE_URL = "https://comment.10jqka.com.cn/api/zhangting.php"
THS_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
    ),
    "Referer": "https://www.10jqka.com.cn/",
}


def clean_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float) and math.isnan(value):
        return ""
    text = str(value).strip()
    return "" if text.lower() == "nan" else text


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


def normalize_code(value: Any) -> str:
    text = clean_text(value)
    digits = "".join(ch for ch in text if ch.isdigit())
    return digits.zfill(6) if digits else text


def safe_df_records(func: Callable[..., Any], **kwargs: Any) -> list[dict[str, Any]]:
    try:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            df = func(**kwargs)
        if df is None or getattr(df, "empty", False):
            return []
        return json.loads(df.to_json(orient="records", force_ascii=False))
    except Exception:
        return []


def ths_request_text(url: str, **kwargs: Any) -> str:
    response = curl_requests.get(url, headers=THS_HEADERS, impersonate="chrome124", timeout=20, **kwargs)
    response.raise_for_status()
    content = response.content
    meta_match = re.search(br'charset=["\']?([a-zA-Z0-9_-]+)', content[:2000], re.IGNORECASE)
    candidates = []
    if meta_match:
        candidates.append(meta_match.group(1).decode("ascii", errors="ignore"))
    candidates.extend([getattr(response, "apparent_encoding", None), getattr(response, "encoding", None), "gb18030", "utf-8"])
    for encoding in candidates:
        if not encoding:
            continue
        try:
            return content.decode(encoding, errors="ignore")
        except LookupError:
            continue
    return content.decode("utf-8", errors="ignore")


def parse_ths_reason_from_title(title: str, stock_name: str) -> str:
    text = clean_text(title)
    if not text:
        return ""
    text = re.sub(r"^涨停雷达[:：]?\s*", "", text)
    if stock_name:
        text = re.sub(rf"\s*{re.escape(stock_name)}\s*触及涨停\s*$", "", text)
    return clean_text(re.sub(r"\s*触及涨停\s*$", "", text))


def parse_ths_article_date(text: str, default_year: int) -> str:
    value = clean_text(text)
    match = re.search(r"(\d{4}-\d{2}-\d{2})", value)
    if match:
        return match.group(1)
    match = re.search(r"(\d{2})月(\d{2})日", value)
    if match:
        month = int(match.group(1))
        day = int(match.group(2))
        return f"{default_year:04d}-{month:02d}-{day:02d}"
    return ""


def parse_ths_items(html_text: str, default_year: int) -> list[dict[str, str]]:
    soup = BeautifulSoup(html_text, "html.parser")
    items: list[dict[str, str]] = []
    for node in soup.select(".news-list .item, .item"):
        detail_anchor = node.select_one("a.dlink")
        stock_anchor = node.select_one(".stocks a")
        title_node = node.select_one(".title")
        date_node = node.select_one(".date span")
        if not detail_anchor or not stock_anchor or not title_node:
            continue
        stock_href = clean_text(stock_anchor.get("href"))
        stock_text = clean_text(stock_anchor.get_text(" ", strip=True))
        code_match = re.search(r"/(\d{6})/?", stock_href)
        stock_match = re.search(r"(.+?)\((\d{6})\)", stock_text)
        code = code_match.group(1) if code_match else (stock_match.group(2) if stock_match else "")
        name = stock_match.group(1) if stock_match else stock_text
        title = clean_text(title_node.get_text(" ", strip=True))
        date_text = date_node.get_text(" ", strip=True) if date_node else ""
        items.append(
            {
                "title": title,
                "summary": parse_ths_reason_from_title(title, name),
                "source": "同花顺涨停雷达",
                "publishedAt": parse_ths_article_date(date_text, default_year),
                "relatedCode": normalize_code(code),
                "relatedName": name,
                "url": clean_text(detail_anchor.get("href")).replace("http://", "https://"),
                "tags": [tag for tag in [parse_ths_reason_from_title(title, name)] if tag],
                "heat": 70,
                "type": "radar",
            }
        )
    return items


def fetch_ths_radar(trade_date: str) -> list[dict[str, Any]]:
    target = trade_date[:10]
    default_year = int(target[:4])
    session = requests.Session()
    session.headers.update(THS_HEADERS)
    results: list[dict[str, Any]] = []
    try:
        for start in range(0, 50, 10):
            try:
                if start == 0:
                    items = parse_ths_items(ths_request_text(THS_ZHANGTING_URL), default_year)
                else:
                    response = curl_requests.get(
                        THS_ZHANGTING_MORE_URL,
                        params={"start": start, "count": 10},
                        headers=THS_HEADERS,
                        impersonate="chrome124",
                        timeout=20,
                    )
                    response.raise_for_status()
                    payload = json.loads(response.text)
                    items = parse_ths_items(clean_text(payload.get("data", {}).get("html")), default_year)
            except Exception:
                break
            if not items:
                break
            matched = [item for item in items if not item["publishedAt"] or item["publishedAt"] == target]
            results.extend(matched)
            if start > 0 and not matched:
                break
    finally:
        session.close()
    return results[:12]


def top_market_news(ak: Any, trade_date: str) -> list[dict[str, Any]]:
    rows = safe_df_records(ak.news_cctv, date=trade_date.replace("-", ""))
    return [
        {
            "title": clean_text(row.get("title")),
            "summary": clean_text(row.get("content"))[:160],
            "source": "央视新闻",
            "publishedAt": clean_text(row.get("date")),
            "relatedCode": "",
            "relatedName": "",
            "url": "",
            "tags": ["宏观"],
            "heat": 55,
            "type": "market_news",
        }
        for row in rows[:6]
    ]


def top_hot_stocks(ak: Any) -> list[dict[str, Any]]:
    rows = safe_df_records(ak.stock_hot_rank_em)
    result = []
    for row in rows[:8]:
        result.append(
            {
                "rank": int(to_float(row.get("当前排名")) or 0),
                "code": normalize_code(row.get("代码")),
                "symbol": clean_text(row.get("代码")),
                "name": clean_text(row.get("股票名称")),
                "price": format_price(row.get("最新价")),
                "changePercent": format_percent(row.get("涨跌幅")),
                "keywords": [],
            }
        )
    return result


def enrich_hot_stocks(ak: Any, hot_stocks: list[dict[str, Any]], sleep_seconds: float) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    topic_heat: dict[str, dict[str, Any]] = defaultdict(lambda: {"heat": 0, "sources": Counter(), "sample_titles": [], "stocks": Counter()})
    stock_news: list[dict[str, Any]] = []
    feed_items: list[dict[str, Any]] = []

    for stock in hot_stocks[:5]:
        symbol = stock.get("symbol", "")
        code = stock.get("code", "")
        name = stock.get("name", "")

        keyword_rows = safe_df_records(ak.stock_hot_keyword_em, symbol=symbol)
        keywords: list[str] = []
        for row in keyword_rows[:4]:
            concept = clean_text(row.get("概念名称"))
            heat = int(to_float(row.get("热度")) or 0)
            if not concept:
                continue
            keywords.append(concept)
            topic_heat[concept]["heat"] += heat
            topic_heat[concept]["sources"]["东方财富热词"] += 1
            topic_heat[concept]["stocks"][name] += 1
        stock["keywords"] = keywords
        feed_items.append(
            {
                "id": f"hot-{code}",
                "type": "hot_stock",
                "title": f"{name} 位于热股榜前列",
                "summary": f"当前排名 {stock.get('rank', 0)}，涨跌幅 {stock.get('changePercent', '')}",
                "source": "东方财富热股榜",
                "publishedAt": "",
                "relatedCode": code,
                "relatedName": name,
                "url": "",
                "tags": keywords,
                "heat": max(0, 100 - stock.get("rank", 0) * 5),
            }
        )
        time.sleep(sleep_seconds)

        news_rows = safe_df_records(ak.stock_news_em, symbol=code)
        for row in news_rows[:3]:
            title = clean_text(row.get("新闻标题"))
            summary = clean_text(row.get("新闻内容"))[:160]
            item = {
                "title": title,
                "summary": summary,
                "source": clean_text(row.get("文章来源")) or "东方财富",
                "publishedAt": clean_text(row.get("发布时间")),
                "relatedCode": code,
                "relatedName": name,
                "url": clean_text(row.get("新闻链接")),
                "tags": keywords[:3],
                "heat": 60,
                "type": "stock_news",
            }
            stock_news.append(item)
            feed_items.append(
                {
                    "id": f"stock-news-{code}-{len(stock_news)}",
                    **item,
                }
            )
            for keyword in keywords[:3]:
                topic_heat[keyword]["heat"] += 25
                topic_heat[keyword]["sources"][item["source"]] += 1
                topic_heat[keyword]["sample_titles"].append(title)
                topic_heat[keyword]["stocks"][name] += 1
        time.sleep(sleep_seconds)

    topic_pulses = []
    theme_clusters = []
    for name, values in sorted(topic_heat.items(), key=lambda item: item[1]["heat"], reverse=True)[:8]:
        sample_stock = values["stocks"].most_common(1)[0][0] if values["stocks"] else ""
        topic_pulses.append(
            {
                "name": name,
                "heat": int(values["heat"]),
                "source": " / ".join(source for source, _ in values["sources"].most_common(2)),
                "sampleStock": sample_stock,
            }
        )
        theme_clusters.append(
            {
                "name": name,
                "heat": int(values["heat"]),
                "sources": [source for source, _ in values["sources"].most_common(3)],
                "sampleTitles": values["sample_titles"][:3],
                "relatedStocks": [stock for stock, _ in values["stocks"].most_common(4)],
            }
        )
    return topic_pulses, stock_news[:12], feed_items, theme_clusters


def build_source_stats(market_news: list[dict[str, Any]], stock_news: list[dict[str, Any]], radar_news: list[dict[str, Any]], hot_stocks: list[dict[str, Any]]) -> list[dict[str, Any]]:
    counter: dict[tuple[str, str], int] = Counter()
    for item in market_news:
        counter[(item["source"], "market_news")] += 1
    for item in stock_news:
        counter[(item["source"], "stock_news")] += 1
    for item in radar_news:
        counter[(item["source"], "radar")] += 1
    if hot_stocks:
        counter[("东方财富热股榜", "hot_stock")] += len(hot_stocks)
    return [
        {"source": source, "category": category, "itemCount": count}
        for (source, category), count in sorted(counter.items(), key=lambda item: (-item[1], item[0][0]))
    ]


def fallback_theme_clusters(feed_items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    buckets: dict[str, dict[str, Any]] = defaultdict(lambda: {"heat": 0, "sources": Counter(), "sampleTitles": [], "relatedStocks": Counter()})
    for item in feed_items:
        tags = item.get("tags") or []
        for tag in tags:
            tag_text = clean_text(tag)
            if not tag_text:
                continue
            buckets[tag_text]["heat"] += max(20, int(item.get("heat", 0)))
            buckets[tag_text]["sources"][item.get("source", "")] += 1
            if item.get("title"):
                buckets[tag_text]["sampleTitles"].append(item["title"])
            if item.get("relatedName"):
                buckets[tag_text]["relatedStocks"][item["relatedName"]] += 1
    return [
        {
            "name": name,
            "heat": values["heat"],
            "sources": [source for source, _ in values["sources"].most_common(3) if source],
            "sampleTitles": values["sampleTitles"][:3],
            "relatedStocks": [stock for stock, _ in values["relatedStocks"].most_common(4)],
        }
        for name, values in sorted(buckets.items(), key=lambda item: item[1]["heat"], reverse=True)[:8]
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--date", required=True)
    parser.add_argument("--sleep", type=float, default=1.0)
    args = parser.parse_args()

    try:
        import akshare as ak  # type: ignore
    except ImportError as exc:
        raise SystemExit("akshare is required. Install it with pip install akshare") from exc

    hot_stocks = top_hot_stocks(ak)
    topic_pulses, stock_news, hot_feed_items, theme_clusters = enrich_hot_stocks(ak, hot_stocks, args.sleep)
    market_news = top_market_news(ak, args.date)
    radar_news = fetch_ths_radar(args.date)

    feed_items = hot_feed_items + [
        {
            "id": f"market-{index}",
            **item,
        }
        for index, item in enumerate(market_news, start=1)
    ] + [
        {
            "id": f"radar-{index}",
            **item,
        }
        for index, item in enumerate(radar_news, start=1)
    ]

    if not theme_clusters:
        theme_clusters = fallback_theme_clusters(feed_items)

    source_stats = build_source_stats(market_news, stock_news, radar_news, hot_stocks)

    result = {
        "tradeDate": args.date[:10],
        "generatedAt": datetime.now().astimezone().isoformat(),
        "sourceStats": source_stats,
        "topicPulses": topic_pulses,
        "hotStocks": [
            {
                "rank": item.get("rank", 0),
                "code": item.get("code", ""),
                "name": item.get("name", ""),
                "price": item.get("price", ""),
                "changePercent": item.get("changePercent", ""),
                "keywords": item.get("keywords", []),
            }
            for item in hot_stocks
        ],
        "themeClusters": theme_clusters,
        "marketNews": market_news,
        "stockNews": (stock_news + radar_news)[:16],
        "feedItems": sorted(feed_items, key=lambda item: item.get("heat", 0), reverse=True)[:30],
    }
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    for key in ["HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy"]:
        os.environ.pop(key, None)
    os.environ["NO_PROXY"] = "*"
    os.environ["no_proxy"] = "*"
    main()
