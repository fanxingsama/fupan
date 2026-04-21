#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
微信小程序实盘截图下载脚本

支持两种方式：
1. 传入一个 JSON 文件路径
   python wxmp_download_images.py --input C:\path\to\response.json

2. 直接运行脚本后输入 JSON 文件路径
   python wxmp_download_images.py

脚本会自动：
- 解析 data.records
- 提取 today_stock_img / yesterday_stock_img
- 创建下载文件夹
- 批量下载图片
- 生成 manifest.json 方便后续核对
"""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import re
import sys
import time
from pathlib import Path
from typing import Any
from urllib import parse, request


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def safe_name(value: str) -> str:
    text = re.sub(r"[^\w\u4e00-\u9fff.-]+", "_", value, flags=re.UNICODE).strip("._")
    return text or "wxmp_images"


def find_json_in_script_dir() -> Path:
    script_dir = Path(__file__).resolve().parent
    candidates = [
        path for path in script_dir.glob("*.json")
        if path.is_file() and path.name.lower() != "manifest.json"
    ]
    if not candidates:
        raise FileNotFoundError(f"脚本所在目录没有找到 JSON 文件：{script_dir}")
    if len(candidates) > 1:
        names = ", ".join(path.name for path in candidates)
        raise ValueError(f"脚本所在目录有多个 JSON 文件，请用 --input 指定：{names}")
    return candidates[0]


def resolve_input_path(input_path: str | None) -> Path:
    if input_path:
        path = Path(input_path).expanduser()
    else:
        path = find_json_in_script_dir()
        print(f"未传入 --input，已自动找到 JSON 文件：{path}")

    path = path.resolve()
    if not path.exists():
        raise FileNotFoundError(f"文件不存在：{path}")
    if not path.is_file():
        raise ValueError(f"不是文件：{path}")
    return path


def read_payload(input_path: str | None) -> Any:
    path = resolve_input_path(input_path)
    print(f"正在读取文件：{path}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    print("读取成功，正在解析 JSON...")
    return payload


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
            pass
        return [value]
    return [value]


def get_records(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        data = payload.get("data")
        if isinstance(data, dict) and isinstance(data.get("records"), list):
            return [item for item in data["records"] if isinstance(item, dict)]
        if isinstance(payload.get("records"), list):
            return [item for item in payload["records"] if isinstance(item, dict)]
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    raise ValueError("JSON 里没有找到 data.records")


def get_user_name(payload: Any, records: list[dict[str, Any]]) -> str:
    if isinstance(payload, dict):
        data = payload.get("data")
        if isinstance(data, dict):
            userinfo = data.get("userinfo")
            if isinstance(userinfo, dict):
                username = str(userinfo.get("username", "")).strip()
                if username:
                    return username
    for record in records:
        username = str(record.get("username", "")).strip()
        if username:
            return username
    return "实盘截图"


def get_user_id(payload: Any, records: list[dict[str, Any]]) -> str:
    if isinstance(payload, dict):
        data = payload.get("data")
        if isinstance(data, dict):
            userinfo = data.get("userinfo")
            if isinstance(userinfo, dict):
                user_id = str(userinfo.get("id", "")).strip()
                if user_id:
                    return user_id
    for record in records:
        user_id = str(record.get("user_id", "")).strip()
        if user_id:
            return user_id
    return "unknown"


def collect_images(records: list[dict[str, Any]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    seen = set()

    for record in records:
        stock_date = str(record.get("stock_date", "unknown-date"))
        record_id = str(record.get("id", "unknown-id"))
        for field in ("today_stock_img", "yesterday_stock_img"):
            for url in as_list(record.get(field)):
                if not isinstance(url, str):
                    continue
                if not url.startswith(("http://", "https://")):
                    continue
                if url in seen:
                    continue
                seen.add(url)
                result.append(
                    {
                        "stock_date": stock_date,
                        "record_id": record_id,
                        "field": field,
                        "url": url,
                    }
                )
    return result


def guess_extension(url: str, content_type: str | None) -> str:
    suffix = Path(parse.urlparse(url).path).suffix.lower()
    if suffix:
        return suffix
    if content_type:
        guessed = mimetypes.guess_extension(content_type.split(";")[0].strip())
        if guessed:
            return guessed
    return ".jpg"


def strip_oss_watermark(url: str) -> tuple[str, bool]:
    """去掉阿里云 OSS x-oss-process 中的 /watermark,... 段，保留其余处理参数。"""
    parsed = parse.urlparse(url)
    params = parse.parse_qs(parsed.query)

    oss_process = params.get("x-oss-process", [""])[0]
    if not oss_process or "watermark" not in oss_process:
        return url, False

    # x-oss-process 格式：image/auto-orient,1/quality,q_90/watermark,text_xxx,...
    # 每个 / 分隔的段以操作名开头，去掉以 watermark 开头的段
    segments = oss_process.split("/")
    kept = [s for s in segments if not s.startswith("watermark")]

    if len(kept) == len(segments):
        return url, False

    if kept:
        new_process = "/".join(kept)
        new_query = parse.urlencode({"x-oss-process": new_process})
    else:
        new_query = ""

    return parse.urlunparse(parsed._replace(query=new_query)), True


def download_image(url: str, target_without_ext: Path) -> tuple[str, bool]:
    final_url, removed_watermark = strip_oss_watermark(url)
    req = request.Request(final_url, headers={"User-Agent": "Mozilla/5.0"}, method="GET")
    with request.urlopen(req, timeout=30) as resp:
        content = resp.read()
        ext = guess_extension(final_url, resp.headers.get("Content-Type"))
    final_target = target_without_ext.with_suffix(ext)
    final_target.write_bytes(content)
    return final_target.name, removed_watermark


def main() -> int:
    parser = argparse.ArgumentParser(description="下载微信小程序抓包结果中的实盘截图")
    parser.add_argument("--input", help="抓包 JSON 文件路径")
    parser.add_argument(
        "--output-root",
        default=str(Path(__file__).resolve().parent),
        help="下载目录根路径，默认是脚本所在目录",
    )
    args = parser.parse_args()

    try:
        payload = read_payload(args.input)
        records = get_records(payload)
    except Exception as exc:
        print(f"读取 JSON 失败：{exc}", file=sys.stderr)
        return 1

    username = get_user_name(payload, records)
    user_id = get_user_id(payload, records)
    task_time = time.strftime("%Y%m%d_%H%M%S")
    folder_name = safe_name(f"{username}_{user_id}_{task_time}")
    output_dir = Path(args.output_root).resolve() / folder_name
    ensure_dir(output_dir)

    images = collect_images(records)
    if not images:
        print("没有找到 today_stock_img 或 yesterday_stock_img 图片链接。", file=sys.stderr)
        return 2

    manifest: dict[str, Any] = {
        "username": username,
        "user_id": user_id,
        "created_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "record_count": len(records),
        "image_count": len(images),
        "watermark_removed_count": 0,
        "images": [],
    }

    print(f"用户：{username}  user_id={user_id}")
    print(f"记录数：{len(records)}  图片数：{len(images)}")
    print(f"下载目录：{output_dir}")
    print("开始下载图片...")

    success_count = 0
    watermark_removed_count = 0
    for index, item in enumerate(images, start=1):
        digest = hashlib.md5(item["url"].encode("utf-8")).hexdigest()[:10]
        basename = f"{item['stock_date']}_{item['field']}_{index:03d}_{digest}"
        try:
            saved_name, removed_watermark = download_image(item["url"], output_dir / basename)
            success_count += 1
            if removed_watermark:
                watermark_removed_count += 1
                print(f"[{index}/{len(images)}] 已下载 {saved_name}  已移除 OSS 水印参数")
            else:
                print(f"[{index}/{len(images)}] 已下载 {saved_name}")
            manifest["images"].append(
                {
                    "stock_date": item["stock_date"],
                    "record_id": item["record_id"],
                    "field": item["field"],
                    "url": item["url"],
                    "saved_as": saved_name,
                    "removed_oss_watermark": removed_watermark,
                }
            )
        except Exception as exc:
            print(f"[{index}/{len(images)}] 下载失败：{item['url']} ({exc})", file=sys.stderr)

    manifest["watermark_removed_count"] = watermark_removed_count
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"完成：成功下载 {success_count}/{len(images)} 张图片")
    print(f"其中自动移除 OSS 水印参数的图片数：{watermark_removed_count}")
    print(f"清单文件：{manifest_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
