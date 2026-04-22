# fupan-v2 项目重建方案

## 1. 项目目标

本项目是当前每日复盘项目的重建版本，技术栈选择：

- 后端：FastAPI
- 前端：React + TypeScript
- 数据库：SQLite 起步，预留 PostgreSQL 迁移空间

新项目不应简单复刻旧项目，而应重建为一个长期可维护、方便 AI 协作开发的短线交易辅助系统。

项目核心定位：

> 面向 A 股短线交易者的个人复盘、计划、执行、反馈系统。

所有功能必须服务一个核心问题：

> 这个功能能否帮助我在明天 9:25 到 10:00 之间做出更好的交易决策？

如果不能，应延后开发。

## 2. 重建原则

1. 先设计，后编码。
2. 先完成核心闭环，再迁移旧功能。
3. 后端产出结论，前端负责呈现。
4. 每个业务模块必须有清晰边界。
5. 所有 API 必须有契约文档。
6. AI prompt 必须版本化。
7. 重要计算逻辑必须可测试。
8. 不允许出现新的巨大 `service`、巨大组件、巨大样式文件。
9. 不做顺手功能，每次只完成一个业务闭环。
10. 每次新增或修改核心能力后，同步更新文档。

## 3. 推荐技术栈

### 3.1 后端

建议使用：

- FastAPI
- Pydantic v2
- SQLAlchemy 2.0
- Alembic
- SQLite
- httpx
- pytest
- ruff
- OpenAI-compatible AI client

可选但不在第一阶段引入：

- PostgreSQL
- Redis
- Celery
- APScheduler
- Docker Compose

### 3.2 前端

建议使用：

- React
- TypeScript
- Vite
- React Router
- TanStack Query
- Zustand
- ECharts
- shadcn/ui 或 Radix UI + Tailwind
- Vitest

第一阶段不做复杂移动端适配，不做营销页，不做多主题系统。

## 4. 总体目录结构

```text
fupan-v2/
  backend/
  frontend/
  docs/
  scripts/
  data/
```

说明：

- `backend/` 放 FastAPI 后端。
- `frontend/` 放 React 前端。
- `docs/` 放产品、架构、API、数据模型、AI 协作规范等相关说明书。
- `scripts/` 放项目级脚本，例如数据迁移、启动脚本。
- `data/` 放本地数据文件，默认不让 AI 扫描大文件。

## 5. 后端架构

### 5.1 后端目录结构

```text
backend/
  app/
    main.py

    core/
      config.py
      logging.py
      errors.py
      time.py

    db/
      session.py
      base.py
      migrations/

    shared/
      ai/
        client.py
        schemas.py
        prompts/
          briefing_v1.md
          summary_v1.md
          stock_analysis_v1.md
      tasks/
        scheduler.py
        runner.py
      utils/
        date.py
        files.py
        json.py

    modules/
      recap/
        README.md
        router.py
        service.py
        repository.py
        models.py
        schemas.py
        calculators.py

      trade_plan/
        README.md
        service.py
        schemas.py
        rule_engine.py

      theme/
        README.md
        router.py
        service.py
        repository.py
        models.py
        schemas.py
        lifecycle.py

      journal/
        README.md
        router.py
        service.py
        repository.py
        models.py
        schemas.py
        importer.py

      stock_analysis/
        README.md
        router.py
        service.py
        schemas.py

      ai_assistant/
        README.md
        router.py
        service.py
        schemas.py

      collection/
        README.md
        router.py
        service.py
        providers/
          tushare.py
          akshare.py
        parsers/
        schemas.py

    tests/
      modules/
      integration/
```

### 5.2 后端分层规则

每个业务模块统一遵守：

- `router.py`：只处理 HTTP 入参、出参、依赖注入。
- `service.py`：只做业务流程编排。
- `repository.py`：只负责数据库读写。
- `models.py`：只定义 SQLAlchemy 数据表。
- `schemas.py`：只定义 Pydantic API 契约。
- `calculators.py` / `rule_engine.py`：放可测试的纯计算逻辑。

禁止：

- `router.py` 写业务规则。
- `service.py` 直接拼大量 SQL。
- `repository.py` 调用 AI。
- 前端实现交易规则。
- 随手创建 `utils.py`、`helper.py`、`common.py` 这类垃圾桶文件。

跨模块调用规则：

- 跨模块只能通过对方模块的 service 调用。
- 不允许直接访问其他模块的 repository。
- 不允许多个模块互相循环依赖。

## 6. 后端业务模块

### 6.1 recap

每日复盘主模块。

负责：

- 查询复盘列表。
- 查询单日复盘详情。
- 保存原始复盘数据。
- 生成市场统计快照。
- 向交易计划、题材跟踪、AI 简报提供基础数据。

不负责：

- AI 文本生成。
- 个人交易日志。
- 题材生命周期长期跟踪。

### 6.2 trade_plan

交易计划模块。

负责：

- 根据每日复盘数据生成次日交易计划。
- 输出市场倾向、主做模式、仓位建议。
- 输出重点方向、风险提醒、交易节奏。

这是项目核心模块之一。

### 6.3 theme

题材跟踪模块。

负责：

- 题材强度。
- 题材阶段。
- 题材活跃天数。
- 龙头、补涨、后排关系。
- 题材生命周期变化。

该模块从 `recap` 数据中派生，不反向污染 `recap`。

### 6.4 journal

个人交易日志模块。

负责：

- 导入交易记录。
- 记录买卖原因。
- 标记是否计划内交易。
- 统计交易模式表现。
- 连接市场环境与个人行为。

### 6.5 stock_analysis

个股和截图分析模块。

负责：

- 上传图片。
- 调用 AI 分析。
- 返回结构化分析结论。

该模块不要和每日复盘混在一起。

### 6.6 ai_assistant

AI 问答模块。

负责：

- 通用问答。
- 基于上下文的复盘对话。
- 管理会话历史。

第一阶段可以不做。

### 6.7 collection

数据采集模块。

负责：

- 调用 akshare / tushare / 外部数据源。
- 清洗原始数据。
- 统一数据源适配。
- 原始数据落盘。

## 7. 前端架构

### 7.1 前端目录结构

```text
frontend/
  src/
    main.tsx

    app/
      App.tsx
      router.tsx
      providers.tsx

    api/
      client.ts
      recap.ts
      theme.ts
      journal.ts
      ai.ts
      stockAnalysis.ts

    features/
      recap/
        README.md
        pages/
          RecapDashboardPage.tsx
          BrokenLimitPage.tsx
          ConsecutiveLimitPage.tsx
          HighRankPage.tsx
        components/
          RecapCalendar.tsx
          MarketOverview.tsx
          TradePlanPanel.tsx
          TrendChart.tsx
        hooks/
          useRecap.ts
        types.ts

      theme/
        README.md
        pages/
          ThemeTrackingPage.tsx
        components/
          ThemeStrengthTable.tsx
          ThemeLifecyclePanel.tsx
          ThemeDetailDrawer.tsx
        hooks/
          useThemeTracking.ts
        types.ts

      journal/
        README.md
        pages/
          TradeJournalPage.tsx
        components/
          TradeImportPanel.tsx
          JournalStatsPanel.tsx
          TradeRecordTable.tsx
        hooks/
          useTradeJournal.ts
        types.ts

      ai/
        README.md
        pages/
          AiChatPage.tsx
        components/
          AiBriefingPanel.tsx
          AiInsightPanel.tsx
        hooks/
          useAiChat.ts
        types.ts

      stock-analysis/
        README.md
        pages/
          StockAnalysisPage.tsx
        components/
          ImageUploadPanel.tsx
          StockAnalysisResult.tsx
        hooks/
          useStockAnalysis.ts
        types.ts

    shared/
      components/
        DataTable.tsx
        EmptyState.tsx
        LoadingState.tsx
        ErrorState.tsx
        PageHeader.tsx
        ConfirmDialog.tsx
      hooks/
      utils/
        date.ts
        format.ts
        trading.ts
      styles/
```

### 7.2 前端规则

1. 所有后端请求只能从 `src/api/` 发出。
2. 页面组件只负责布局和组合。
3. 数据请求统一使用 TanStack Query。
4. 跨页面轻状态优先放 URL 参数。
5. 全局状态只放用户设置、主题、当前交易日等。
6. 不在组件里写复杂交易计算。
7. 每个 feature 内部自带 components、hooks、types。
9. 不允许把所有样式堆进一个巨大 CSS 文件。
10. 表格列定义必须独立放置。

## 8. 数据模型初稿

### 8.1 recap_report

每日复盘原始报告。

```text
id
trade_date
source
raw_payload_json
created_at
updated_at
```

### 8.2 market_snapshot

市场统计快照。

```text
id
trade_date
up_count
down_count
limit_up_count
limit_down_count
broken_limit_count
first_limit_count
highest_board
market_temperature
breadth_score
created_at
```

### 8.3 stock_snapshot

个股当日快照。

```text
id
trade_date
code
name
sector
limit_status
board_count
is_first_limit
is_broken_limit
change_percent
turnover_rate
amount
reason
raw_json
```

### 8.4 theme_daily

题材每日强度。

```text
id
trade_date
theme_name
strength_score
stage
active_stock_count
leader_stock_code
leader_stock_name
description
created_at
```

### 8.5 theme_lifecycle

题材生命周期。

```text
id
theme_name
start_date
last_active_date
current_stage
active_days
peak_strength
status
updated_at
```

### 8.6 trade_plan

每日交易计划。

```text
id
trade_date
headline
market_bias
trade_mode
position_advice
execution_summary
risk_focus
next_day_focus_json
schedule_json
created_at
```

### 8.7 ai_artifact

AI 产物统一表。

```text
id
trade_date
artifact_type
model
prompt_version
input_hash
content_json
created_at
```

`artifact_type` 示例：

```text
summary
insight
briefing
stock_analysis
chat_context
```

### 8.8 trade_record

个人交易记录。

```text
id
trade_date
code
name
side
price
quantity
amount
fee
source_file
imported_at
```

### 8.9 trade_review

个人交易复盘记录。

```text
id
trade_record_id
setup_type
buy_reason
sell_reason
planned
mistake_type
emotion_state
profit_loss
max_drawdown
review_note
created_at
```

## 9. API 契约初稿

### 9.1 复盘

```text
GET    /api/recaps
GET    /api/recaps/{trade_date}
POST   /api/recaps/{trade_date}/collect
GET    /api/recaps/{trade_date}/market-snapshot
GET    /api/recaps/{trade_date}/trend
```

### 9.2 交易计划

```text
GET    /api/trade-plans/{trade_date}
POST   /api/trade-plans/{trade_date}/generate
```

### 9.3 题材跟踪

```text
GET    /api/themes?trade_date=YYYY-MM-DD
GET    /api/themes/{theme_name}
GET    /api/themes/{theme_name}/history
POST   /api/themes/{trade_date}/refresh
```

### 9.4 AI

```text
GET    /api/ai/{trade_date}/summary
POST   /api/ai/{trade_date}/summary/regenerate

GET    /api/ai/{trade_date}/insight
POST   /api/ai/{trade_date}/insight/regenerate

GET    /api/ai/{trade_date}/briefing
POST   /api/ai/{trade_date}/briefing/regenerate

POST   /api/ai/chat
```

### 9.5 交易日志

```text
GET    /api/journal
POST   /api/journal/import
GET    /api/journal/stats
PATCH  /api/journal/records/{id}/review
```

### 9.6 个股分析

```text
POST   /api/stock-analysis/image
POST   /api/stock-analysis/kline
```

## 10. 第一阶段 MVP

第一阶段只完成主闭环：

```text
采集数据 -> 每日复盘 -> 交易计划 -> AI 简报 -> 前端驾驶舱
```

### 10.1 MVP 包含

1. 日期选择。
2. 复盘列表。
3. 单日复盘详情。
4. 市场情绪指标。
5. 涨停、炸板、连板、首板列表。
6. 交易计划。
7. AI 简报。
8. 题材强度列表基础版。
9. 数据重新采集。
10. 清晰错误提示。

### 10.2 MVP 不包含

第一阶段暂不开发：

```text
AI 聊天
截图分析
交易日志复杂统计
盘中提醒
多用户
权限系统
策略回测
移动端深度适配
```

## 11. 开发阶段计划

### 第 0 阶段：项目规范

先建立文档，不写业务代码。

必须创建：

```text
docs/product-brief.md
docs/architecture.md
docs/api-contract.md
docs/data-model.md
docs/ai-coding-guide.md
docs/roadmap.md
```

### 第 1 阶段：后端骨架

完成：

```text
FastAPI app
配置系统
数据库连接
Alembic
统一错误格式
日志
健康检查
测试框架
ruff
```

### 第 2 阶段：复盘数据模型

完成：

```text
recap_report
market_snapshot
stock_snapshot
旧 JSON 导入脚本
复盘查询 API
```

### 第 3 阶段：交易计划

完成：

```text
trade_plan 数据结构
规则引擎
交易计划生成 API
核心规则测试
```

### 第 4 阶段：前端驾驶舱

完成：

```text
React 项目
路由
API client
日期选择
复盘首页
交易计划面板
核心表格
错误和加载状态
```

### 第 5 阶段：AI 简报

完成：

```text
AI client
prompt version
AI artifact cache
AI briefing API
前端 AI 简报面板
```

### 第 6 阶段：题材跟踪

完成：

```text
theme_daily
theme_lifecycle
题材强度计算
题材详情页
```

### 第 7 阶段：旧功能迁移

再考虑：

```text
交易日志
个股分析
截图分析
AI 聊天
```

## 12. AI 协作开发规则

新项目必须创建 `AGENTS.md`，建议包含以下规则：

1. AI 接手项目时先读 `AGENTS.md`。
2. 后端任务先读对应 `backend/app/modules/{module}/README.md`。
3. 前端任务先读对应 `frontend/src/features/{feature}/README.md`。
4. 不允许默认全仓库扫描。
5. 不允许读取 `data/raw` 大文件，除非用户明确要求。
6. 修改 API 必须同步更新 `docs/api-contract.md`。
7. 修改数据库必须生成 Alembic migration。
8. 修改 AI prompt 必须增加 prompt version。
9. 新增功能必须先写 feature spec。
10. 完成后必须运行对应测试。

新增功能前必须创建：

```text
docs/features/YYYY-MM-DD-feature-name.md
```

内容必须包括：

```text
目标
用户流程
数据模型变化
API 变化
前端页面变化
不做什么
验收标准
```

## 13. 文件规模限制

建议强制遵守：

```text
后端 service 不超过 300 行
前端组件不超过 300 行
CSS 单文件不超过 500 行
```

超过限制时必须拆分。

拆分方向：

- 纯计算逻辑拆到 calculator / rule_engine。
- 数据访问拆到 repository。
- 页面局部 UI 拆到 components。
- 请求逻辑拆到 hooks 或 api。
- 通用展示组件拆到 shared/components。

## 14. AI Prompt 管理

所有 prompt 放在：

```text
backend/app/shared/ai/prompts/
```

文件命名：

```text
briefing_v1.md
summary_v1.md
insight_v1.md
stock_analysis_v1.md
```

AI 产物必须记录：

```text
model
prompt_version
input_hash
created_at
```

目的：

- 知道某个结论由哪个 prompt 生成。
- 支持后续重新生成。
- 支持比较不同 prompt 版本效果。

## 15. 前端页面设计原则

新前端不是营销页，第一屏就是工具。

建议主导航：

```text
交易驾驶舱
题材跟踪
交易日志
AI 分析
系统设置
```

交易驾驶舱布局：

```text
顶部：日期、采集状态、刷新按钮
第一层：市场状态、情绪分数、仓位建议
第二层：次日交易计划
第三层：主线强度
第四层：重点股票池
第五层：涨停 / 炸板 / 连板表格
右侧：AI 简报 / 风险提醒
```

核心原则：

```text
结论优先，数据辅助。
```

不要把用户淹没在表格里。

## 16. 不建议第一阶段引入的内容

第一阶段不要做：

```text
微服务
Spring Cloud
Celery 分布式队列
Redis
权限系统
Docker Compose 大全
复杂主题系统
复杂低代码配置
移动端完整适配
策略回测
多账户
```

这些不是当前最重要的问题。

## 17. 验收标准

第一阶段完成时，应满足：

1. 后端可启动。
2. 前端可启动。
3. 数据库迁移可执行。
4. 可导入或采集至少一天复盘数据。
5. 可查看每日复盘详情。
6. 可生成交易计划。
7. 可生成或读取 AI 简报。
8. 前端驾驶舱可展示核心结论。
9. 核心计算逻辑有测试。
10. 文档包含产品说明、架构说明、API 契约、数据模型、AI 协作规则。

## 18. 最终建议

本项目可以直接重建为：

```text
FastAPI + React + TypeScript + SQLite
```

但必须坚持：

```text
先文档
再骨架
再核心闭环
再迁移旧功能
```

这次重建的成败不取决于框架本身，而取决于是否持续遵守模块边界、API 契约、prompt 版本化、测试和文档更新规则。

