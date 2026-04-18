# Copilot Instructions — 短线交易每日复盘辅助平台

## 项目概述
A股短线交易每日复盘平台，前后端分离架构。

## 技术栈
- **后端**: Java 17 + Spring Boot 3 + SQLite (JDBC) + Maven
- **前端**: Vue 2.7 + Vite 5 + ECharts 6
- **数据采集**: Python 3.12 (akshare) + Node.js (Playwright)

## 项目结构索引

### 后端 (backend/src/main/java/com/meirifupan/backend/)
| 目录 | 职责 |
|------|------|
| `config/` | Spring 配置类: AiProperties, RecapProperties, RecapConfig, TradeJournalProperties |
| `controller/` | REST API: RecapController, AiChatController, StockAiAnalysisController, TradeJournalController, UserStockAnalysisController |
| `service/` | 业务逻辑: RecapStorageService(核心存取), AiInsightService/AiBriefingService/AiSummaryService/AiChatService(AI分析), TradePlanService, TradeJournalService, IndicatorService, StockAiAnalysisService, UserStockAnalysisService, MarketIntelligenceService |
| `model/` | 数据模型 (均为 Java record): DailyRecapReport, AiInsight, TradePlan, StockRecord, SectorRecord 等 |
| `provider/` | 数据源: MarketRecapProvider(接口), AkshareMarketRecapProvider(实现), MockMarketRecapProvider |
| `DailyRecapApplication.java` | Spring Boot 入口 |

### 后端资源
- `backend/src/main/resources/application.yml` — Spring 配置
- `backend/src/main/resources/schema.sql` — SQLite 建表语句

### 前端 (frontend/src/)
| 文件/目录 | 职责 |
|-----------|------|
| `App.vue` | 根组件，路由/布局 |
| `api.js` | 所有后端 API 调用 |
| `components/OverviewPage.vue` | 主复盘总览页 |
| `components/TabbedTablePage.vue` | 涨停/跌停/炸板等数据表 |
| `components/HighRankPage.vue` | 高位股排行 |
| `components/AiInsightPanel.vue` | AI 结构化分析面板 |
| `components/AiBriefingPanel.vue` | AI 简报面板 |
| `components/AiSummaryPanel.vue` | AI 摘要面板 |
| `components/AiChatPage.vue` | AI 对话页 |
| `components/TradeJournalPage.vue` | 交易日志 |
| `components/StockAiAnalysisPage.vue` | 个股AI分析 |
| `components/UserStockAnalysisPage.vue` | 用户自选分析 |
| `components/RecapCalendar.vue` | 日历选择器 |
| `components/DataTable.vue` | 通用表格组件 |
| `utils/` | 工具函数: columns.js(列定义), format.js(格式化), chart.js(图表), trading.js(交易计算) |

### 数据采集脚本 (backend/scripts/)
- `collect_akshare.py` — 通过 akshare 采集A股数据
- `collect_market_intelligence.py` — 采集市场情报
- `collect_stock_bars.py` — 采集K线数据
- `fetch_jiuyangongshe_action.js` — Playwright 抓取韭研公社
- `setup_jiuyangongshe_session.py` — 韭研公社登录会话

## 编码规范
1. 后端 model 层全部使用 **Java record**，不用 class
2. 数据库操作使用 **JdbcTemplate**，不用 JPA/MyBatis
3. AI 调用统一走 OpenAI 兼容接口 (chat/completions)，通过 `AiEndpointResolver` 解析 URL
4. 前端使用 **Vue 2 Options API**，不用 Composition API
5. 前端 API 调用集中在 `api.js`，组件不直接 fetch

## 绝对不要读取的文件
- `backend/target/` — 编译产物
- `backend/data/*.json` — 每日采集的原始数据
- `backend/recap.db` — SQLite 数据库二进制
- `backend/out.json` — 临时输出
- `frontend/node_modules/` — npm 依赖
- `tools/` — Maven 二进制工具包
- `*.log` — 日志文件
- `__pycache__/` — Python 缓存
