-- xechat 账号体系 schema (SQLite)
-- 时间戳统一用 INTEGER(epoch ms)避免类型处理器复杂度

-- 账号表
CREATE TABLE IF NOT EXISTS accounts (
    account_id        INTEGER PRIMARY KEY,        -- 雪花 ID,永久不变
    account           TEXT    NOT NULL,           -- 登录账号 [a-zA-Z0-9_]{4,20}
    nickname          TEXT    NOT NULL,           -- 显示昵称,≤12,唯一
    password_hash     TEXT    NOT NULL,           -- Argon2id 编码串(含 salt)
    avatar_version    INTEGER NOT NULL DEFAULT 0, -- 头像版本,换头像 +1
    role              TEXT    NOT NULL DEFAULT 'USER',   -- ADMIN / USER
    permit            INTEGER NOT NULL DEFAULT 0,
    status            TEXT    NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / FROZEN / DELETED
    deleted_at        INTEGER,                    -- 软删时间
    created_at        INTEGER NOT NULL,
    created_ip        TEXT,
    last_login_at     INTEGER,
    last_login_ip     TEXT,
    -- E2EE 阶段新增
    e2ee_salt         TEXT,                       -- 客户端派生 masterKey 的 salt(base64url 16B)
    identity_pub_key  TEXT                        -- X25519 身份公钥(base64url 32B)
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

-- E2EE 密钥信封(主密钥包裹后的身份私钥);type='IDENTITY' 一行/账号
CREATE TABLE IF NOT EXISTS key_envelopes (
    account_id  INTEGER NOT NULL,             -- 拥有此 envelope 的账号
    type        TEXT    NOT NULL,             -- 'IDENTITY'(身份私钥);未来可加 'SESSION:<peerId>'
    envelope    TEXT    NOT NULL,             -- master 包裹后的密文(base64url iv||ciphertext)
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    PRIMARY KEY (account_id, type),
    FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);

-- 私聊密文(E2EE 信封,服务端只看密文);分表方案 vs messages_public
CREATE TABLE IF NOT EXISTS messages_private (
    id                    INTEGER PRIMARY KEY,    -- 雪花 ID
    created_at            INTEGER NOT NULL,
    sender_account_id     INTEGER NOT NULL,
    recipient_account_id  INTEGER NOT NULL,
    -- 会话对维度(min,max),便于按 (a,b) 双向查
    conv_min              INTEGER NOT NULL,       -- min(sender, recipient)
    conv_max              INTEGER NOT NULL,       -- max(sender, recipient)
    iv                    TEXT    NOT NULL,       -- AES-GCM IV(base64url 12B)
    ciphertext            TEXT    NOT NULL,       -- AES-256-GCM 密文(base64url,含 tag)
    version               TEXT    NOT NULL DEFAULT 'v1', -- 信封版本
    recalled_at           INTEGER
);
CREATE INDEX IF NOT EXISTS idx_messages_private_conv ON messages_private(conv_min, conv_max, id);

-- 公共频道聊天记录(分表方案:私聊密文落 messages_private,见 e2ee-and-history.md)
CREATE TABLE IF NOT EXISTS messages_public (
    id                  INTEGER PRIMARY KEY,          -- 雪花 ID,全局有序
    created_at          INTEGER NOT NULL,             -- epoch ms
    sender_account_id   INTEGER,                      -- 注册用户填,游客为 null
    sender_guest_uuid   TEXT,                         -- 游客 client uuid,注册用户为 null
    sender_nickname     TEXT    NOT NULL,             -- 冗余存当时昵称
    msg_type            TEXT    NOT NULL,             -- 'TEXT' / 'IMAGE'
    content             TEXT    NOT NULL,
    quote_json          TEXT,
    recalled_at         INTEGER
);
CREATE INDEX IF NOT EXISTS idx_messages_public_created ON messages_public(created_at);
