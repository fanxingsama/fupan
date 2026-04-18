CREATE TABLE IF NOT EXISTS recap_report (
    trade_date TEXT PRIMARY KEY,
    report_json TEXT NOT NULL,
    created_at TEXT
);

CREATE TABLE IF NOT EXISTS trade_record (
    id TEXT PRIMARY KEY,
    trade_date TEXT NOT NULL,
    code TEXT NOT NULL,
    name TEXT,
    side TEXT,
    price REAL,
    quantity INTEGER,
    amount REAL,
    fee REAL,
    source_file TEXT,
    imported_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_trade_record_date ON trade_record(trade_date);
CREATE INDEX IF NOT EXISTS idx_trade_record_code_date ON trade_record(code, trade_date);
CREATE INDEX IF NOT EXISTS idx_trade_record_imported_at ON trade_record(imported_at);

CREATE TABLE IF NOT EXISTS ai_summary (
    trade_date TEXT PRIMARY KEY,
    summary_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_insight (
    trade_date TEXT PRIMARY KEY,
    insight_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_briefing (
    trade_date TEXT PRIMARY KEY,
    briefing_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS market_intelligence (
    trade_date TEXT PRIMARY KEY,
    payload_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_request_cache (
    cache_key TEXT PRIMARY KEY,
    scenario TEXT NOT NULL,
    response_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_request_cache_scenario_created_at
    ON ai_request_cache(scenario, created_at DESC);
