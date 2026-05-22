-- xechat 账号体系 schema (SQLite)
-- 时间戳统一用 INTEGER(epoch ms)避免类型处理器复杂度

-- 账号表
CREATE TABLE IF NOT EXISTS accounts (
    account_id     INTEGER PRIMARY KEY,        -- 雪花 ID,永久不变
    account        TEXT    NOT NULL,           -- 登录账号 [a-zA-Z0-9_]{4,20}
    nickname       TEXT    NOT NULL,           -- 显示昵称,≤12,唯一
    password_hash  TEXT    NOT NULL,           -- Argon2id 编码串(含 salt)
    avatar_version INTEGER NOT NULL DEFAULT 0, -- 头像版本,换头像 +1
    role           TEXT    NOT NULL DEFAULT 'USER',   -- ADMIN / USER
    permit         INTEGER NOT NULL DEFAULT 0,
    status         TEXT    NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / FROZEN / DELETED
    deleted_at     INTEGER,                    -- 软删时间
    created_at     INTEGER NOT NULL,
    created_ip     TEXT,
    last_login_at  INTEGER,
    last_login_ip  TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_account  ON accounts(account);
CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_nickname ON accounts(nickname);

-- 会话表
CREATE TABLE IF NOT EXISTS sessions (
    token        TEXT    PRIMARY KEY,           -- base64url 43 字符
    account_id   INTEGER NOT NULL,
    platform     TEXT    NOT NULL,              -- DESKTOP / IDEA / WEB
    client_uuid  TEXT,
    created_at   INTEGER NOT NULL,
    last_used_at INTEGER NOT NULL,
    expires_at   INTEGER NOT NULL,              -- 默认 last_used_at + 30d
    revoked      INTEGER NOT NULL DEFAULT 0,
    ip           TEXT,
    FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);
CREATE INDEX IF NOT EXISTS idx_sessions_account ON sessions(account_id);
CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at);

-- 邀请码表
CREATE TABLE IF NOT EXISTS invite_codes (
    code        TEXT    PRIMARY KEY,            -- 区分大小写
    created_by  INTEGER,                        -- null = 系统生成(setup-token)
    created_at  INTEGER NOT NULL,
    expires_at  INTEGER,                        -- null = 永久
    max_uses    INTEGER NOT NULL DEFAULT 1,     -- 0 = 无限
    used_count  INTEGER NOT NULL DEFAULT 0,
    used_by     INTEGER,                        -- 一次性使用后回填
    used_at     INTEGER,
    revoked     INTEGER NOT NULL DEFAULT 0,
    note        TEXT
);

-- 登录日志(建表先行,第一版不接 UI)
CREATE TABLE IF NOT EXISTS login_logs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id  INTEGER,
    ip          TEXT,
    region      TEXT,
    platform    TEXT,
    success     INTEGER NOT NULL,
    fail_reason TEXT,
    created_at  INTEGER NOT NULL
);

-- 系统单值状态(key-value)
CREATE TABLE IF NOT EXISTS system_state (
    key   TEXT PRIMARY KEY,
    value TEXT
);
