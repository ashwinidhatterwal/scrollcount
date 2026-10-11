CREATE TABLE IF NOT EXISTS profiles(id TEXT PRIMARY KEY, token_hash TEXT NOT NULL UNIQUE, name TEXT NOT NULL, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS invites(code TEXT PRIMARY KEY, owner TEXT NOT NULL, expires_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS friends(a TEXT NOT NULL,b TEXT NOT NULL,created_at INTEGER NOT NULL,PRIMARY KEY(a,b),CHECK(a<b));
CREATE INDEX IF NOT EXISTS friends_b ON friends(b);
CREATE TABLE IF NOT EXISTS signals(id INTEGER PRIMARY KEY AUTOINCREMENT,sender TEXT NOT NULL,recipient TEXT NOT NULL,payload TEXT NOT NULL,expires_at INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS signals_recipient ON signals(recipient,id);
CREATE INDEX IF NOT EXISTS signals_expiry ON signals(expires_at);
CREATE TABLE IF NOT EXISTS profile_keys(profile TEXT PRIMARY KEY,public_key TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS snapshots(sender TEXT NOT NULL,recipient TEXT NOT NULL,iv TEXT NOT NULL,ciphertext TEXT NOT NULL,updated_at INTEGER NOT NULL,PRIMARY KEY(sender,recipient));
CREATE INDEX IF NOT EXISTS snapshots_recipient ON snapshots(recipient);

CREATE TABLE IF NOT EXISTS deletion_keys(profile TEXT PRIMARY KEY,key_hash TEXT NOT NULL UNIQUE);
CREATE INDEX IF NOT EXISTS snapshots_expiry ON snapshots(updated_at);
CREATE INDEX IF NOT EXISTS invites_expiry ON invites(expires_at);
