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
