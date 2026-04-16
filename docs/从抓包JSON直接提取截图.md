# 从抓包 JSON 直接提取截图

如果你已经抓到了类似下面这样的接口响应：

- 顶层有 `data.records`
- 每条记录里有 `today_stock_img`
- 每条记录里有 `yesterday_stock_img`

那就不需要继续研究复杂接口了，可以直接把图片批量下载下来。

仓库里新增脚本：

`scripts/wxmp_records_to_images.py`

## 使用方法

1. 把你抓包得到的完整 JSON 保存成一个文件，比如：

`temp/xi_tie_jie_records.json`

2. 执行：

```powershell
python scripts/wxmp_records_to_images.py --input temp/xi_tie_jie_records.json --output downloads/xi_tie_jie
```

3. 下载后的图片会放到：

`downloads/xi_tie_jie/`

同时会生成一个：

`downloads/xi_tie_jie/manifest.json`

## 这个脚本做了什么

- 自动读取 `data.records`
- 提取 `today_stock_img` 和 `yesterday_stock_img`
- 去重
- 批量下载图片
- 按日期和字段命名，方便后续做复盘分析

## 适合你现在这类数据的原因

你贴出来的抓包内容里，图片不是“看不见了”，而是藏在 JSON 的这些字段里：

- `today_stock_img`
- `yesterday_stock_img`

字段值本身就是阿里云 OSS 图片链接，所以可以直接下载。
