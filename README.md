# 每日复盘

`每日复盘` 是一个面向 A 股短线复盘的前后端分离项目。

- 后端：Spring Boot 3
- 前端：Vue 2 + Vite

## 项目结构

```text
每日复盘/
  backend/
  frontend/
```

## 当前能力

- 后端提供复盘报告查询、历史列表查询、手动触发采集接口
- 默认使用 AKShare 公开 A 股接口采集真实数据，采集后保存为按日期归档的 JSON 历史报告
- 复盘内容覆盖炸板、连板、首板、跌停、板块强弱、10 日涨幅榜和涨跌家数
- 保留 `mock` 采集器作为离线演示兜底
- 前端使用 Vue 2 Options API 展示复盘总览、板块强弱和历史记录
- AI 调用默认走 OpenAI 兼容网关，可直接接入 LiteLLM 统一管理模型、密钥和后备路由

## 启动

### 后端

```powershell
cd backend
mvn spring-boot:run
```

### 前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认访问 `http://localhost:8080/api`。

## LiteLLM 接入

项目后端现在默认按 LiteLLM 的 OpenAI 兼容入口读取 AI 配置：

- `AI_BASE_URL=http://127.0.0.1:4000/v1`
- `AI_MODEL=recap-main`
- `AI_API_KEY=sk-local-litellm`

推荐做法：

1. 复制 [backend/litellm/config.yaml.example](C:\Users\myl\Desktop\fupan\backend\litellm\config.yaml.example) 为你自己的 `backend/litellm/config.yaml`
2. 在 `backend/.env` 里填好 `LITELLM_MASTER_KEY`、上游模型 `API_BASE`、`API_KEY`
3. 直接运行项目启动脚本，或先启动 LiteLLM 代理再启动 Spring Boot 后端

LiteLLM 代理示例：

```powershell
cd backend
uv tool install "litellm[proxy]"
litellm --config .\litellm\config.yaml
```

如果你平时是双击 [启动每日复盘.bat](C:\Users\myl\Desktop\fupan\启动每日复盘.bat) 或运行 [scripts/start_all.ps1](C:\Users\myl\Desktop\fupan\scripts\start_all.ps1)，现在后端启动脚本会在检测到 `AI_PROVIDER=litellm` 且本地代理未启动时，自动拉起 LiteLLM。

如果你暂时不想用 LiteLLM，也可以继续把 `AI_BASE_URL` 指向任意 OpenAI 兼容接口，后端代码无需再改。

## 真实数据采集要求

- Python 3.10+
- `pip install akshare`
- 默认采集器无需额外 token 或 cookie
- 板块强弱、涨停池、跌停池等数据来自 AKShare 聚合的公开 A 股接口
- 部分涨停池接口受上游站点限制，只能抓取近期交易日数据

## 保存历史

- Spring Boot 后端当前使用 SQLite 保存复盘、AI 摘要、AI 分析和交易日志
- 主要数据库文件为 `backend/recap.db`

## 一键使用

```powershell
cd scripts
.\setup_local_env.ps1
.\register_daily_task.ps1
.\start_all.ps1
```

- `setup_local_env.ps1` 会准备 Maven、Python 虚拟环境、`akshare` 和前端依赖
- `register_daily_task.ps1` 会注册工作日 15:10 的自动采集任务
- `start_all.ps1` 会分别打开后端和前端窗口
- 也可以直接双击项目根目录下的 `启动每日复盘.bat`

## 后续建议

- 根据你实际跑出来的字段再继续微调 AKShare 字段映射
- 增加交易日历和自动调度
- 再补一层 SQLite 或 MySQL 索引，便于多条件检索和复盘筛选
