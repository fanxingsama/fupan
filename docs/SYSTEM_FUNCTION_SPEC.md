# 短线交易辅助平台完整功能接口说明

## 1. 文档目的

本文档用于说明当前平台的完整功能边界、接口、输入来源、输出结构、执行过程、规则算法与 AI 使用方式。

适用对象：

- 平台拥有者
- 后续维护开发者
- 接入本项目继续开发的 AI 编程助手

本文档重点回答 6 个问题：

1. 这个系统现在有哪些功能
2. 每个功能的输入来自哪里
3. 每个功能的输出是什么
4. 每个功能是如何执行的
5. 功能背后是规则、AI 还是测试数据
6. 后续扩展时应该从哪里入手

---

## 2. 系统总体结构

当前项目分为两个主要部分：

- `backend/`
  Spring Boot 后端，负责采集、存储、指标计算、AI 调用、交易日志导入
- `frontend/`
  Vue 2 + Vite 前端，负责页面展示、日历交互、复盘数据查看、AI 面板展示

核心运行链路如下：

1. 前端选择交易日或触发采集
2. 后端调用 Python 脚本从 AKShare / 网页抓取获取盘面与情报
3. 后端将原始复盘结果保存到 SQLite
4. 后端基于复盘结果计算指标与交易计划
5. 后端调用 AI 生成总结、分析、情报简报
6. 前端展示复盘页、AI 情报中心、AI 分析中心、AI 总结、交易日志等内容

---

## 3. 当前功能清单

### 3.1 复盘采集

- 采集指定交易日复盘数据
- 支持当前交易日自动巡检采集
- 支持历史交易日手动补采
- 支持覆盖前后差异确认

### 3.2 复盘详情展示

- 指数快照
- 涨停、跌停、炸板、首板、连板
- 板块涨幅与板块聚焦
- 近几日趋势图

### 3.3 盘面指标计算

- 封板率
- 涨跌停比
- 昨日涨停溢价
- 昨日涨停胜率
- 昨日炸板反馈
- 连板梯队
- 市场情绪阶段
- 风险信号

### 3.4 次日交易计划

- 主线题材评分
- 重点观察票
- 候选池
- 次日关注点
- 风险提醒
- 盘中执行节奏

### 3.5 AI 功能

- `AI Summary`
  面向复盘总结的自然语言摘要
- `AI Insight`
  面向交易决策的结构化分析
- `AI Briefing`
  面向次日准备的情报简报

### 3.6 实时情报中心

- 热股榜
- 热词
- 个股新闻
- 市场新闻
- 同花顺涨停雷达抓取
- 原始情报流聚合
- 来源统计
- 题材簇

### 3.7 交易日志

- CSV / TSV / 分隔符文本导入
- 自动按交易日聚合
- 结合当日市场环境生成交易日上下文

---

## 4. 配置与运行依赖

配置文件：

- [backend/src/main/resources/application.yml](C:\Users\29224\Desktop\fupan\backend\src\main\resources\application.yml)

关键配置项：

### 4.1 `recap.*`

- `storage-root`
  复盘 JSON 存储目录
- `provider`
  当前复盘数据提供者，默认 `akshare`
- `python-executable`
  Python 可执行文件路径
- `collector-script`
  复盘采集脚本路径
- `intelligence-script`
  实时情报采集脚本路径
- `sleep-seconds`
  访问上游接口时的节流秒数

### 4.2 `ai.*`

- `enabled`
  是否启用 AI
- `provider`
  当前 AI 提供商名称，仅作为展示用途
- `api-key`
  AI API Key
- `base-url`
  AI 请求地址
- `model`
  AI 模型名

### 4.3 数据库

数据库为 SQLite：

- `backend/recap.db`

表结构文件：

- [backend/src/main/resources/schema.sql](C:\Users\29224\Desktop\fupan\backend\src\main\resources\schema.sql)

当前表用途：

- `recap_report`
  保存按交易日归档的完整复盘 JSON
- `trade_record`
  保存导入后的逐笔交易记录
- `ai_summary`
  保存 AI 总结缓存
- `ai_insight`
  保存 AI 分析缓存
- `ai_briefing`
  保存 AI 情报简报缓存
- `market_intelligence`
  保存实时情报原始聚合结果

---

## 5. 后端主入口与职责分工

### 5.1 应用启动

- [DailyRecapApplication.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\DailyRecapApplication.java)

职责：

- Spring Boot 启动入口

### 5.2 配置注册

- [RecapConfig.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\config\RecapConfig.java)
- [RecapProperties.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\config\RecapProperties.java)
- [AiProperties.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\config\AiProperties.java)
- [TradeJournalProperties.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\config\TradeJournalProperties.java)

职责：

- 绑定 `application.yml`
- 为服务层提供统一配置来源

---

## 6. REST 接口说明

控制器：

- [RecapController.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\controller\RecapController.java)
- [TradeJournalController.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\controller\TradeJournalController.java)

### 6.1 `GET /api/recaps`

功能：

- 获取已存在复盘的交易日列表

输入来源：

- SQLite `recap_report`

输出：

- `RecapListItem[]`

执行方式：

- 纯数据库读取
- 不调用 AI
- 不重新采集

### 6.2 `GET /api/recaps/{tradeDate}`

功能：

- 获取某天完整复盘详情

输入来源：

- SQLite `recap_report`
- 同时加载近 20 个交易日复盘作为指标趋势补充

输出：

- `RecapDetailResponse`
  包含：
  - `report`
  - `indicators`
  - `tradePlan`
  - `trendPoints`

执行方式：

1. 从本地库读取 `DailyRecapReport`
2. 调用 `IndicatorService.calculate`
3. 调用 `TradePlanService.buildPlan`
4. 将近几日复盘转换为 `TrendPoint`

### 6.3 `POST /api/recaps/capture`

功能：

- 触发指定交易日复盘采集

输入：

- JSON：`{ "tradeDate": "YYYY-MM-DD" }`

输入来源：

- 前端用户手动触发
- 或自动巡检逻辑触发

输出：

- `RecapDetailResponse`

执行方式：

1. `RecapCaptureService.capture`
2. 根据 `recap.provider` 选择 provider
3. 当前默认 `AkshareMarketRecapProvider`
4. Java 拉起 Python 脚本 [collect_akshare.py](C:\Users\29224\Desktop\fupan\backend\scripts\collect_akshare.py)
5. Python 输出 JSON
6. Java 反序列化为 `DailyRecapReport`
7. 保存进 SQLite
8. 再计算指标与交易计划并返回

### 6.4 `GET /api/recaps/{tradeDate}/ai-summary`

功能：

- 获取 AI 复盘自然语言总结

输入来源：

- 当日 `DailyRecapReport`
- `IndicatorService` 结果
- `TradePlanService` 结果
- AI 配置

输出：

- `AiSummary`

执行方式：

1. 先查 `ai_summary` 缓存
2. 若无缓存或 `refresh=true`
3. 构造 prompt
4. 调用外部 AI
5. 提取文本内容与 bullet
6. 落库缓存

是否使用算法：

- 无额外算法
- 主要依赖 prompt + 模型生成

### 6.5 `GET /api/recaps/{tradeDate}/ai-insight`

功能：

- 获取结构化 AI 分析

输入来源：

- 当日复盘
- 指标结果
- 次日计划

输出：

- `AiInsight`

执行方式：

1. 查 `ai_insight` 缓存
2. 构造结构化 JSON prompt
3. 要求模型只返回 JSON
4. 解析出：
  - 市场结论
  - 市场风格
  - 关键信号
  - 题材
  - 龙头
  - 动作建议
  - 风险提示
5. 保存缓存

是否使用算法：

- 输入端依赖规则指标
- 输出端依赖 AI 生成

### 6.6 `GET /api/recaps/{tradeDate}/market-intelligence`

功能：

- 获取原始实时情报聚合结果

输入来源：

- AKShare 热股榜
- AKShare 热词
- AKShare 个股新闻
- AKShare 市场新闻
- 同花顺涨停雷达网页抓取

输出：

- `MarketIntelligence`

执行方式：

1. 查 `market_intelligence` 缓存
2. 若无缓存或强制刷新
3. Java 拉起 Python 脚本 [collect_market_intelligence.py](C:\Users\29224\Desktop\fupan\backend\scripts\collect_market_intelligence.py)
4. Python 聚合多源情报输出 JSON
5. Java 反序列化并缓存

是否使用算法：

- 轻规则聚合
- 不直接调用 AI

### 6.7 `GET /api/recaps/{tradeDate}/ai-briefing`

功能：

- 获取 AI 情报简报

输入来源：

- `MarketIntelligence`
- 当日复盘
- 少量近几日复盘
- 指标
- 次日计划

输出：

- `AiBriefing`

执行方式：

1. 先获取 `MarketIntelligence`
2. 再构造面向 AI 的情报整理 prompt
3. 优先使用实时情报，不依赖长历史
4. 输出：
  - headline
  - briefing
  - themePulses
  - stockFocuses
  - timeline
  - tomorrowSignals

是否使用算法：

- 输入端依赖多源采集和轻规则聚合
- 输出端依赖 AI 生成

### 6.8 `GET /api/trade-journal`

功能：

- 获取交易日志按日聚合结果

输入来源：

- SQLite `trade_record`
- 若同日存在复盘，会补充当日市场上下文

输出：

- `TradeJournalDay[]`

执行方式：

1. 读取全部交易日期
2. 按日期加载记录
3. 聚合买卖次数、成交金额
4. 若复盘存在，则通过指标与次日计划生成市场上下文

### 6.9 `POST /api/trade-journal/import`

功能：

- 导入交易记录文件

输入来源：

- 用户上传文件

支持：

- CSV
- TSV
- 分号分隔文本

输出：

- `TradeImportResponse`

执行方式：

1. 自动识别分隔符
2. 解析常见字段别名
3. 归一化日期、代码、价格、数量、金额
4. 根据关键字段判断是否重复
5. 保存到 `trade_record`

是否使用算法：

- 规则解析
- 不调用 AI

---

## 7. 数据采集功能说明

### 7.1 复盘采集脚本

文件：

- [collect_akshare.py](C:\Users\29224\Desktop\fupan\backend\scripts\collect_akshare.py)

输入：

- `--date`
- `--sleep`

上游来源：

- AKShare A 股接口
- 雪球个股资料接口
- 同花顺涨停雷达页面

采集内容：

- 指数快照
- 涨停池
- 首板池
- 连板池
- 炸板池
- 跌停池
- 昨日涨停反馈
- 昨日炸板反馈
- 板块涨跌
- 10 日涨幅榜
- 首板题材聚焦

执行方式：

1. 解析交易日
2. 调 AKShare 获取盘面数据
3. 对字段做清洗和归一化
4. 对涨停原因优先用同花顺页面补充
5. 板块榜单不可用时，用已采集个股反推板块热度
6. 输出 `DailyRecapReport`

规则 / 算法性质：

- 主要是数据映射和清洗
- 板块 fallback 使用简单统计反推
- 不调用 AI

### 7.2 实时情报采集脚本

文件：

- [collect_market_intelligence.py](C:\Users\29224\Desktop\fupan\backend\scripts\collect_market_intelligence.py)

输入：

- `--date`
- `--sleep`

上游来源：

- AKShare 热股榜
- AKShare 热词
- AKShare 个股新闻
- AKShare 市场新闻
- 同花顺涨停雷达抓取

输出：

- `MarketIntelligence`

核心字段：

- `sourceStats`
- `topicPulses`
- `hotStocks`
- `themeClusters`
- `marketNews`
- `stockNews`
- `feedItems`

执行方式：

1. 读取热股榜
2. 对前几名热股补抓热词与个股新闻
3. 抓取市场新闻
4. 抓取涨停雷达
5. 合并成统一 `feedItems`
6. 统计来源分布
7. 聚合题材热度
8. 若热词接口不稳定，则用原始情报标签反推 `themeClusters`

规则 / 算法性质：

- 多源数据聚合
- `themeClusters` 为轻规则聚类，不是机器学习模型
- 不直接调用 AI

---

## 8. 指标计算说明

文件：

- [IndicatorService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\IndicatorService.java)

### 8.1 指标输入

输入来源：

- 当日 `DailyRecapReport`
- 最近若干日 `DailyRecapReport`

### 8.2 指标输出

输出对象：

- `MarketIndicators`

包含：

- 封板率
- 涨跌停比
- 涨停总数
- 跌停总数
- 炸板数
- 昨日涨停溢价
- 昨日涨停胜率
- 昨日炸板均值
- 连板最高高度
- 连板梯队
- 情绪阶段
- 情绪标签
- 风险信号
- 投机热度分
- 接力承接分
- 市场宽度分

### 8.3 核心算法

#### 8.3.1 封板率

公式：

- `limitUpTotal / (limitUpTotal + brokenCount)`

#### 8.3.2 涨跌停比

公式：

- `limitUpTotal / limitDownTotal`

#### 8.3.3 昨日涨停溢价

公式：

- `limitUpYesterdayFeedback.changePercent` 均值

#### 8.3.4 情绪阶段 `emotionPhase`

不是 AI，而是规则判断。

基于：

- 最近 3 到 5 天封板率
- 最近连板高度变化
- 涨停家数

阶段包括：

- `ice`
- `retreat`
- `diverge`
- `climax`
- `ferment`
- `repair`
- `unknown`

#### 8.3.5 投机热度分 `speculationScore`

基于以下权重线性缩放：

- 封板率 35%
- 涨停总数 30%
- 连板高度 20%
- 炸板少的奖励 15%

#### 8.3.6 接力承接分 `continuationScore`

基于以下权重：

- 昨日涨停溢价 45%
- 昨日涨停胜率 35%
- 昨日炸板均值 20%

#### 8.3.7 风险信号

基于阈值规则：

- 封板率过低
- 涨停家数过少
- 涨停溢价转负
- 涨跌停比过弱
- 最高板断层

结论：

指标层主要是规则模型，不是 AI 模型。

---

## 9. 交易计划生成说明

文件：

- [TradePlanService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\TradePlanService.java)

功能：

- 从复盘结果自动生成次日计划

输入：

- `DailyRecapReport`
- `MarketIndicators`

输出：

- `TradePlan`

### 9.1 主线题材评分

输入来源：

- `firstLimitSectorFocus`
- `topUpSectors`

规则：

- 首板聚焦数量提供题材扩散度
- 板块涨幅提供板块热度
- 最终按权重计算题材分数

公式近似：

- `score = firstLimitCount * 12 + sectorHeat * 8`

### 9.2 观察票评分

输入来源：

- 当日连板
- 首板
- 炸板修复票

评分维度：

- 题材分
- 封板时间分
- 换手分
- 连板高度分
- 封单金额分

这是规则打分，不是 AI。

### 9.3 交易模式判断

基于情绪阶段规则映射：

- `ice/retreat -> 空仓等待 / 低位试错`
- `repair -> 低位首板 + 弱转强`
- `ferment -> 主线前排接力`
- `climax -> 去弱留强，不追后排`

### 9.4 输出内容

- headline
- marketBias
- tradeMode
- positionAdvice
- executionSummary
- nextDayFocus
- riskFocus
- primaryThemes
- candidatePools
- watchStocks
- schedule

---

## 10. AI 功能说明

### 10.1 AI Summary

文件：

- [AiSummaryService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\AiSummaryService.java)

定位：

- 面向人类阅读的复盘总结

输入：

- 复盘 JSON
- 指标 JSON
- 交易计划 JSON

输出：

- 一段自然语言总结
- bullet 列表

特点：

- 最适合做“盘后看一眼”
- 可读性强
- 结构化程度最低

### 10.2 AI Insight

文件：

- [AiInsightService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\AiInsightService.java)

定位：

- 面向决策辅助的结构化分析

输入：

- 复盘
- 指标
- 次日计划

输出：

- `marketConclusion`
- `marketStyle`
- `keySignals`
- `themes`
- `leaders`
- `actionPlan`
- `riskAlerts`

特点：

- 比 summary 更结构化
- 仍然依赖 AI 理解和组织表达

### 10.3 AI Briefing

文件：

- [AiBriefingService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\AiBriefingService.java)

定位：

- 面向盘前准备的 AI 情报整理层

输入：

- 实时情报 `MarketIntelligence`
- 当前复盘
- 少量近几日复盘
- 指标
- 次日计划

输出：

- `headline`
- `briefing`
- `themePulses`
- `stockFocuses`
- `timeline`
- `tomorrowSignals`

特点：

- 优先依赖实时情报
- 不依赖你先有很多天历史
- 是当前最接近“AI 信息中台”的模块

### 10.4 AI 缓存逻辑

所有 AI 功能均支持：

- 默认读取 SQLite 缓存
- `refresh=true` 强制重新生成

### 10.5 AI 失败回退

AI 失败时：

- 返回 `status=error`
- 不会阻塞复盘主流程
- 某些模块会用已有规则结果做部分 fallback

---

## 11. 前端页面与功能映射

前端入口：

- [frontend/src/App.vue](C:\Users\29224\Desktop\fupan\frontend\src\App.vue)

### 11.1 页面导航

- `overview`
  总览页
- `broken`
  炸板复盘
- `consecutive`
  连板梯队
- `firstLimit`
  首板池
- `highRank`
  高标观察
- `journal`
  交易日志

### 11.2 总览页

文件：

- [frontend/src/components/OverviewPage.vue](C:\Users\29224\Desktop\fupan\frontend\src\components\OverviewPage.vue)

展示内容：

- 指数卡片
- 风险标签
- 次日作战结论
- 情绪指标卡
- 主线强度
- AI 情报中心
- AI 分析中心
- AI 总结
- 重点票预案
- 候选池
- 盘中节奏
- 趋势图

### 11.3 AI 情报中心

文件：

- [frontend/src/components/AiBriefingPanel.vue](C:\Users\29224\Desktop\fupan\frontend\src\components\AiBriefingPanel.vue)

依赖接口：

- `/ai-briefing`
- `/market-intelligence`

用途：

- 同时展示 AI 简报和原始情报流

### 11.4 AI 分析中心

文件：

- [frontend/src/components/AiInsightPanel.vue](C:\Users\29224\Desktop\fupan\frontend\src\components\AiInsightPanel.vue)

依赖接口：

- `/ai-insight`

### 11.5 AI 总结

文件：

- [frontend/src/components/AiSummaryPanel.vue](C:\Users\29224\Desktop\fupan\frontend\src\components\AiSummaryPanel.vue)

依赖接口：

- `/ai-summary`

### 11.6 API 封装

文件：

- [frontend/src/api.js](C:\Users\29224\Desktop\fupan\frontend\src\api.js)

职责：

- 封装前端对后端 REST API 的调用

---

## 12. 哪些是规则，哪些是 AI，哪些是测试值

### 12.1 规则逻辑

以下功能主要靠规则，不靠 AI：

- 复盘数据采集与清洗
- 板块 fallback 反推
- 实时情报聚合
- 情绪周期判断
- 风险信号判断
- 题材评分
- 观察票打分
- 交易模式判断
- 交易日志导入解析

### 12.2 AI 逻辑

以下功能主要靠大模型：

- AI Summary
- AI Insight
- AI Briefing

### 12.3 测试 / Mock

当前存在：

- [MockMarketRecapProvider.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\provider\MockMarketRecapProvider.java)

用途：

- 本地演示或上游失效时的备用 provider

是否默认使用：

- 否，当前默认 `akshare`

---

## 13. 当前系统的已知边界

### 13.1 新闻源仍不算全面

当前多源情报虽然比单 AKShare 更强，但仍然不是全市场覆盖。

当前已接入：

- AKShare 若干接口
- 同花顺涨停雷达网页

尚未系统接入：

- 龙虎榜
- 巨潮公告
- 财联社快讯
- 券商研报
- 社媒讨论源

### 13.2 AI 依然是“整理器”，不是预测器

当前 AI 最适合做：

- 信息压缩
- 关系归纳
- 风险提示
- 次日观察点总结

当前 AI 不适合被理解为：

- 自动选股器
- 确定性预测器
- 下单策略引擎

### 13.3 题材簇不是语义大聚类模型

当前 `themeClusters` 属于轻规则聚类：

- 来自热词
- 来自原始情报标签
- 来自标题归并

还不是 embedding / 向量检索 / 图谱级聚类。

---

## 14. 面向后续 AI 开发的代码阅读建议

如果后续要让 AI 继续开发本项目，建议按如下顺序读：

### 主链路

1. [frontend/src/App.vue](C:\Users\29224\Desktop\fupan\frontend\src\App.vue)
2. [frontend/src/components/OverviewPage.vue](C:\Users\29224\Desktop\fupan\frontend\src\components\OverviewPage.vue)
3. [backend/src/main/java/com/meirifupan/backend/controller/RecapController.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\controller\RecapController.java)
4. [backend/src/main/java/com/meirifupan/backend/service/RecapCaptureService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\RecapCaptureService.java)
5. [backend/src/main/java/com/meirifupan/backend/provider/AkshareMarketRecapProvider.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\provider\AkshareMarketRecapProvider.java)
6. [backend/scripts/collect_akshare.py](C:\Users\29224\Desktop\fupan\backend\scripts\collect_akshare.py)

### 指标与计划

1. [IndicatorService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\IndicatorService.java)
2. [TradePlanService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\TradePlanService.java)

### AI 链路

1. [MarketIntelligenceService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\MarketIntelligenceService.java)
2. [collect_market_intelligence.py](C:\Users\29224\Desktop\fupan\backend\scripts\collect_market_intelligence.py)
3. [AiBriefingService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\AiBriefingService.java)
4. [AiInsightService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\AiInsightService.java)
5. [AiSummaryService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\AiSummaryService.java)

### 交易日志

1. [TradeJournalController.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\controller\TradeJournalController.java)
2. [TradeJournalService.java](C:\Users\29224\Desktop\fupan\backend\src\main\java\com\meirifupan\backend\service\TradeJournalService.java)

---

## 15. 建议增加的后续能力

建议的下一批扩展点：

1. 接入公告源和龙虎榜源
2. 引入更强的新闻去重与聚类
3. 给情报加“利多/利空/中性”和“影响题材/影响个股”标签
4. 加一个题材图谱页而不是只展示 feed
5. 加一个“AI 复盘自己交易”的教练模块

---

## 16. 文档维护建议

后续如果新增功能，建议同步更新以下内容：

- 功能名称
- 入口接口
- 输入源
- 输出结构
- 规则还是 AI
- 缓存位置
- 前端展示位置

建议把本文档作为平台的总说明文档长期维护。

