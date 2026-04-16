#!/usr/bin/env python3
"""
Extract image URLs from a captured mini-program records response and download them.

Usage:
  python scripts/wxmp_records_to_images.py --input response.json --output downloads/xitiejie

The input JSON can be:
1. The raw API response object pasted into a file.
2. A top-level list of record objects.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import sys
from pathlib import Path
from typing import Any
from urllib import parse, request


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def as_list(value: Any) -> list[Any]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return []
        try:
            parsed = json.loads(text)
            if isinstance(parsed, list):
                return parsed
        except json.JSONDecodeError:
            return [value]
    return [value]


def get_records(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if isinstance(payload, dict):
        data = payload.get("data")
        if isinstance(data, dict) and isinstance(data.get("records"), list):
            return [item for item in data["records"] if isinstance(item, dict)]
        if isinstance(payload.get("records"), list):
            return [item for item in payload["records"] if isinstance(item, dict)]
    raise ValueError("Could not find records in input JSON")


def collect_record_images(record: dict[str, Any]) -> list[tuple[str, str, str]]:
    stock_date = str(record.get("stock_date", "unknown-date"))
    record_id = str(record.get("id", "unknown-id"))
    image_urls: list[tuple[str, str, str]] = []

    for field in ("today_stock_img", "yesterday_stock_img"):
        for url in as_list(record.get(field)):
            if isinstance(url, str) and url.startswith(("http://", "https://")):
                image_urls.append((field, stock_date, url))

    return image_urls


def guess_extension(url: str, content_type: str | None) -> str:
    suffix = Path(parse.urlparse(url).path).suffix.lower()
    if suffix:
        return suffix
    if content_type:
        guessed = mimetypes.guess_extension(content_type.split(";")[0].strip())
        if guessed:
            return guessed
    return ".jpg"


def download(url: str, target: Path) -> None:
    req = request.Request(url, headers={"User-Agent": "Mozilla/5.0"}, method="GET")
    with request.urlopen(req, timeout=30) as resp:
        content = resp.read()
        ext = guess_extension(url, resp.headers.get("Content-Type"))

    final_target = target.with_suffix(ext)
    final_target.write_bytes(content)


def main() -> int:
    parser = argparse.ArgumentParser(description="Download image URLs from mini-program records JSON")
    parser.add_argument("--input", required=True, help="Path to the captured JSON response")
    parser.add_argument("--output", required=True, help="Directory for downloaded images")
    args = parser.parse_args()

    input_path = Path(args.input).resolve()
    output_dir = Path(args.output).resolve()
    ensure_dir(output_dir)

    try:
        payload = load_json(input_path)
        records = get_records(payload)
    except Exception as exc:
        print(f"Failed to parse input: {exc}", file=sys.stderr)
        return 1

    urls: list[tuple[str, str, str]] = []
    seen = set()
    for record in records:
        for item in collect_record_images(record):
            if item[2] not in seen:
                seen.add(item[2])
                urls.append(item)

    if not urls:
        print("No image URLs found in records.", file=sys.stderr)
        return 2

    manifest: list[dict[str, str]] = []
    for index, (field, stock_date, url) in enumerate(urls, start=1):
        digest = hashlib.md5(url.encode("utf-8")).hexdigest()[:10]
        target = output_dir / f"{stock_date}_{field}_{index:03d}_{digest}"
        try:
            download(url, target)
            manifest.append(
                {
                    "stock_date": stock_date,
                    "field": field,
                    "url": url,
                    "saved_as": target.name,
                }
            )
            print(f"[{index}/{len(urls)}] {stock_date} {field}")
        except Exception as exc:
            print(f"[{index}/{len(urls)}] failed: {url} ({exc})", file=sys.stderr)

    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Completed. Downloaded {len(manifest)} images to {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
