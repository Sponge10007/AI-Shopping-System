CREATE TABLE IF NOT EXISTS chat_sessions (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL UNIQUE,
  user_id VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(64) NOT NULL REFERENCES chat_sessions(session_id),
  user_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  content TEXT NOT NULL,
  image_list TEXT,
  link_list TEXT,
  raw_answer TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_behavior_logs_user_created_at
  ON behavior_logs(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_behavior_logs_type_created_at
  ON behavior_logs(event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_orders_user_created_at
  ON orders(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created_at
  ON chat_messages(session_id, created_at);
