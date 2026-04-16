#!/usr/bin/env python3
"""
WeChat mini-program image downloader.

Usage:
  python scripts/wxmp_image_downloader.py --config scripts/wxmp_downloader_config.example.json

The script is intentionally generic:
1. It can page through a captured list API from a mini-program.
2. It extracts image-like URLs recursively from the JSON response.
3. It downloads all unique images to a local folder.

You usually need to obtain the API URL, headers, and any user identifier first
by using a proxy tool such as Charles/Fiddler/mitmproxy.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import re
import sys
import time
from pathlib import Path
from typing import Any
from urllib import error, parse, request


IMAGE_KEYWORDS = (
    "img",
    "image",
    "images",
    "pic",
    "pics",
    "cover",
    "poster",
    "screenshot",
    "avatar",
    "thumb",
    "thumbnail",
    "src",
)

IMAGE_EXTENSIONS = (".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic")


def load_json_file(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def is_image_url(value: str, parent_key: str = "") -> bool:
    lowered = value.lower()
    key = parent_key.lower()
    if lowered.startswith("//"):
        lowered = "https:" + lowered
    if not lowered.startswith(("http://", "https://")):
        return False
    if any(ext in lowered for ext in IMAGE_EXTENSIONS):
        return True
    if any(token in key for token in IMAGE_KEYWORDS):
        return True
    if "image" in lowered or "img" in lowered:
        return True
    return False


def normalize_url(url: str) -> str:
    if url.startswith("//"):
        return "https:" + url
    return url


def collect_image_urls(node: Any, parent_key: str = "") -> list[str]:
    found: list[str] = []
    if isinstance(node, dict):
        for key, value in node.items():
            found.extend(collect_image_urls(value, key))
    elif isinstance(node, list):
        for item in node:
            found.extend(collect_image_urls(item, parent_key))
    elif isinstance(node, str):
        if is_image_url(node, parent_key):
            found.append(normalize_url(node))
    return found


def build_request(
    url: str,
    method: str,
    headers: dict[str, str],
    params: dict[str, Any] | None,
    json_body: dict[str, Any] | None,
) -> request.Request:
    final_url = url
    if method.upper() == "GET" and params:
        query = parse.urlencode(
            {key: value for key, value in params.items() if value is not None},
            doseq=True,
        )
        separator = "&" if "?" in url else "?"
        final_url = f"{url}{separator}{query}"

    body = None
    req_headers = dict(headers)
    if method.upper() != "GET":
        payload = json_body if json_body is not None else (params or {})
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req_headers.setdefault("Content-Type", "application/json")

    return request.Request(final_url, data=body, headers=req_headers, method=method.upper())


def http_json(req: request.Request, timeout: int = 20) -> Any:
    with request.urlopen(req, timeout=timeout) as resp:
        charset = resp.headers.get_content_charset() or "utf-8"
        raw = resp.read().decode(charset, errors="replace")
        return json.loads(raw)


def safe_name(value: str) -> str:
    return re.sub(r"[^0-9A-Za-z._-]+", "_", value).strip("_") or "unknown"


def guess_extension(url: str, content_type: str | None) -> str:
    parsed = parse.urlparse(url)
    suffix = Path(parsed.path).suffix.lower()
    if suffix in IMAGE_EXTENSIONS:
        return suffix
    if content_type:
        guessed = mimetypes.guess_extension(content_type.split(";")[0].strip())
        if guessed:
            return guessed
    return ".jpg"


def download_image(url: str, headers: dict[str, str], output_dir: Path, index: int) -> Path:
    req = request.Request(url, headers=headers, method="GET")
    with request.urlopen(req, timeout=30) as resp:
        content = resp.read()
        ext = guess_extension(url, resp.headers.get("Content-Type"))

    digest = hashlib.md5(url.encode("utf-8")).hexdigest()[:10]
    filename = f"{index:04d}_{digest}{ext}"
    target = output_dir / filename
    with target.open("wb") as fh:
        fh.write(content)
    return target


def build_page_payload(base: dict[str, Any] | None, field: str | None, value: int) -> dict[str, Any]:
    payload = dict(base or {})
    if field:
        payload[field] = value
    return payload


def fetch_pages(config: dict[str, Any]) -> tuple[list[str], list[dict[str, Any]]]:
    api = config.get("api") or {}
    url = api.get("url")
    if not url:
        raise ValueError("config.api.url is required")

    method = str(api.get("method", "GET")).upper()
    headers = {str(k): str(v) for k, v in (api.get("headers") or {}).items()}
    params = api.get("params") or {}
    json_body = api.get("json_body") or {}
    paging = api.get("paging") or {}
    page_field = paging.get("page_field")
    page_start = int(paging.get("page_start", 1))
    max_pages = int(paging.get("max_pages", 1))
    delay_ms = int(api.get("delay_ms", 500))

    all_urls: list[str] = []
    pages_meta: list[dict[str, Any]] = []
    seen = set()

    for page in range(page_start, page_start + max_pages):
        page_params = build_page_payload(params, page_field, page)
        page_json = build_page_payload(json_body, page_field, page)
        req = build_request(url, method, headers, page_params, page_json)
        data = http_json(req)
        urls = [u for u in collect_image_urls(data) if u not in seen]

        for url_item in urls:
            seen.add(url_item)
            all_urls.append(url_item)

        pages_meta.append(
            {
                "page": page,
                "new_image_count": len(urls),
                "total_image_count": len(all_urls),
            }
        )

        print(f"[page {page}] new_images={len(urls)} total={len(all_urls)}")

        if page_field and len(urls) == 0:
            break

        if delay_ms > 0:
            time.sleep(delay_ms / 1000)

    return all_urls, pages_meta


def dump_manifest(output_dir: Path, config: dict[str, Any], urls: list[str], pages_meta: list[dict[str, Any]]) -> None:
    manifest = {
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "output_dir": str(output_dir),
        "image_count": len(urls),
        "images": urls,
        "pages": pages_meta,
        "config_summary": {
            "api_url": config.get("api", {}).get("url"),
            "method": config.get("api", {}).get("method", "GET"),
        },
    }
    with (output_dir / "manifest.json").open("w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)


def main() -> int:
    parser = argparse.ArgumentParser(description="Download screenshots from a captured WeChat mini-program API")
    parser.add_argument("--config", required=True, help="Path to a JSON config file")
    args = parser.parse_args()

    config_path = Path(args.config).resolve()
    if not config_path.exists():
        print(f"Config file not found: {config_path}", file=sys.stderr)
        return 1

    config = load_json_file(config_path)
    output_dir = Path(config.get("output_dir") or "downloads") / safe_name(config.get("task_name", "wxmp"))
    output_dir = output_dir.resolve()
    ensure_dir(output_dir)

    try:
        urls, pages_meta = fetch_pages(config)
    except error.HTTPError as exc:
        print(f"HTTP error: {exc.code} {exc.reason}", file=sys.stderr)
        return 2
    except error.URLError as exc:
        print(f"Network error: {exc.reason}", file=sys.stderr)
        return 3
    except json.JSONDecodeError as exc:
        print(f"Response is not valid JSON: {exc}", file=sys.stderr)
        return 4
    except Exception as exc:
        print(f"Failed: {exc}", file=sys.stderr)
        return 5

    if not urls:
        print("No image URLs found. Check the API response and headers.", file=sys.stderr)
        dump_manifest(output_dir, config, [], pages_meta)
        return 6

    download_headers = {str(k): str(v) for k, v in (config.get("download_headers") or {}).items()}
    saved_files: list[str] = []
    for index, url in enumerate(urls, start=1):
        try:
            target = download_image(url, download_headers, output_dir, index)
            saved_files.append(str(target))
            print(f"[download {index}/{len(urls)}] {target.name}")
        except Exception as exc:
            print(f"[download {index}/{len(urls)}] failed: {url} ({exc})", file=sys.stderr)

    dump_manifest(output_dir, config, urls, pages_meta)
    print(f"Completed. Saved {len(saved_files)} files to {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
