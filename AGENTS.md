# AGENTS.md — AI 编码助手项目指引

> 本文件供所有 AI 编码工具（GitHub Copilot、Claude、Codex、Cursor、Windsurf 等）读取，以快速理解项目并减少不必要的文件扫描。

## 推荐阅读顺序

首次接手本项目时，建议按以下顺序读取，避免无关扫描：

1. 先读本文件 `AGENTS.md`
2. 再读 `backend/src/main/resources/application.yml` 与 `backend/src/main/resources/schema.sql`
3. 后端任务再进入 `controller/`、`service/`、`model/`、`config/`
4. 前端任务先看 `frontend/src/api.js`，再进入 `frontend/src/components/`、`frontend/src/utils/columns.js`
5. 仅在涉及数据采集时读取 `backend/scripts/`

## 项目入口

- 后端启动入口：`backend/src/main/java/com/meirifupan/backend/BackendApplication.java`
- 前端入口：`frontend/src/main.js`
- 前端根组件：`frontend/src/App.vue`

## 快速定位指南

| 我要改什么 | 去哪里找 |
|-----------|---------|
| 新增/修改 REST API | `backend/.../controller/` |
| 修改业务逻辑 | `backend/.../service/` |
| 新增/修改数据模型 | `backend/.../model/` (Java record) |
| 修改数据库表结构 | `backend/src/main/resources/schema.sql` |
| 修改 Spring 配置 | `backend/src/main/resources/application.yml` + `backend/.../config/` |
| 新增前端页面 | `frontend/src/components/` (Vue 2 Options API) |
| 修改前端 API 调用 | `frontend/src/api.js` (唯一出口) |
| 修改前端表格列定义 | `frontend/src/utils/columns.js` |
| 修改数据采集逻辑 | `backend/scripts/` (Python/Node.js) |

## 默认不要主动读取的路径

以下路径多为二进制、缓存、构建产物或大型数据文件。默认不要主动扫描，除非用户明确要求，或在排查构建/运行问题时确有必要：

```
backend/.cache/         # Python/脚本缓存
backend/.venv/          # Python 虚拟环境
backend/target/          # Java 编译产物
backend/recap.db         # 本地 SQLite 数据库
backend/data/*.json      # 每日采集原始数据（大文件）
backend/out.json         # 临时调试输出
frontend/node_modules/   # npm 依赖
frontend/dist/           # 前端构建产物
tools/                   # Maven 二进制工具包
*.log                    # 日志文件
__pycache__/             # Python 缓存
*.jar / *.zip / *.class  # 二进制文件
```

## 编码规范（必须遵守）

1. 后端 model 全部用 **Java record**
2. 数据库用 **JdbcTemplate**，禁止 JPA/MyBatis
3. AI 调用走 OpenAI 兼容接口，通过 `AiEndpointResolver` 解析 URL
4. 前端用 **Vue 2 Options API**，禁止 Composition API
5. 前端所有后端调用集中在 `api.js`，组件不直接 fetch
6. Java 17 语法，可用 text blocks、pattern matching 等

## 工作约定

- 文件默认使用 **UTF-8** 编码
- 优先做最小必要修改，不要顺手重构无关模块
- 未经用户要求，不要批量扫描 `docs/`、`tools/`、构建产物或采集数据
- 需要排查问题时，可有针对性读取日志、数据库或构建输出，但应只读取当前问题所需部分

## 修改后验证

- 后端改动后优先执行：`cd backend && mvn test`
- 需要本地启动后端时执行：`cd backend && mvn spring-boot:run`
- 前端改动后优先执行：`cd frontend && npm run build`
- 涉及采集脚本时，仅运行当前改动相关脚本，不要全量跑任务

## 技术栈

- 后端: Java 17 + Spring Boot 3 + SQLite (JDBC) + Maven
- 前端: Vue 2.7 + Vite 5 + ECharts 6
- 采集: Python 3.12 (akshare) + Node.js (Playwright)
