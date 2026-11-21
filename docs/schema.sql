-- I-ITSM database schema (SQLite). Generated from SqliteRepository.SCHEMA
CREATE TABLE IF NOT EXISTS users (
  id            TEXT PRIMARY KEY,
  role          TEXT NOT NULL CHECK (role IN ('EMPLOYEE','TECHNICIAN','ADMIN')),
  name          TEXT NOT NULL,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS technician_profiles (
  user_id          TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  experience_years INTEGER NOT NULL CHECK (experience_years >= 0),
  max_load         INTEGER NOT NULL CHECK (max_load > 0),
  available        INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS technician_skills (
  user_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category TEXT NOT NULL,
  PRIMARY KEY (user_id, category)
);
CREATE TABLE IF NOT EXISTS technician_service_experience (
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  service_name TEXT NOT NULL,
  years        INTEGER NOT NULL,
  PRIMARY KEY (user_id, service_name)
);
CREATE TABLE IF NOT EXISTS it_services (
  name        TEXT PRIMARY KEY,
  criticality INTEGER NOT NULL CHECK (criticality BETWEEN 1 AND 3),
  description TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS sla_policies (
  priority           TEXT PRIMARY KEY CHECK (priority IN ('P1','P2','P3','P4')),
  response_minutes   INTEGER NOT NULL CHECK (response_minutes > 0),
  resolution_minutes INTEGER NOT NULL CHECK (resolution_minutes >= response_minutes)
);
CREATE TABLE IF NOT EXISTS service_sla (
  service_name       TEXT NOT NULL REFERENCES it_services(name) ON DELETE CASCADE,
  priority           TEXT NOT NULL CHECK (priority IN ('P1','P2','P3','P4')),
  response_minutes   INTEGER NOT NULL,
  resolution_minutes INTEGER NOT NULL,
  PRIMARY KEY (service_name, priority)
);
CREATE TABLE IF NOT EXISTS incidents (
  id               TEXT PRIMARY KEY,
  reporter_id      TEXT NOT NULL REFERENCES users(id),
  title            TEXT NOT NULL,
  description      TEXT NOT NULL,
  service_name     TEXT NOT NULL REFERENCES it_services(name),
  impact           INTEGER NOT NULL CHECK (impact BETWEEN 1 AND 3),
  urgency          INTEGER NOT NULL CHECK (urgency BETWEEN 1 AND 3),
  affected_users   INTEGER NOT NULL,
  created_at       TEXT NOT NULL,
  category         TEXT,
  status           TEXT NOT NULL CHECK (status IN ('NEW','CLASSIFIED','ASSIGNED','ACKNOWLEDGED','IN_PROGRESS','RESOLVED','VERIFIED','CLOSED')),
  priority         TEXT CHECK (priority IN ('P1','P2','P3','P4')),
  technician_id    TEXT REFERENCES users(id),
  response_due     TEXT,
  resolve_due      TEXT,
  acknowledged_at  TEXT,
  resolved_at      TEXT,
  closed_at        TEXT,
  resolution       TEXT,
  feedback_rating  INTEGER CHECK (feedback_rating BETWEEN 1 AND 5),
  feedback_comment TEXT,
  escalation_level INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_incidents_status  ON incidents(status);
CREATE INDEX IF NOT EXISTS idx_incidents_tech    ON incidents(technician_id);
CREATE INDEX IF NOT EXISTS idx_incidents_service ON incidents(service_name);
CREATE INDEX IF NOT EXISTS idx_incidents_prio    ON incidents(priority);
CREATE TABLE IF NOT EXISTS incident_notes (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  incident_id TEXT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
  noted_at    TEXT NOT NULL,
  author      TEXT NOT NULL,
  kind        TEXT NOT NULL,
  body        TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS incident_sla_flags (
  incident_id TEXT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
  flag        TEXT NOT NULL,
  PRIMARY KEY (incident_id, flag)
);
CREATE TABLE IF NOT EXISTS related_incidents (
  incident_id TEXT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
  related_id  TEXT NOT NULL,
  score       REAL NOT NULL,
  position    INTEGER NOT NULL,
  PRIMARY KEY (incident_id, related_id)
);
CREATE TABLE IF NOT EXISTS audit_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  at          TEXT NOT NULL,
  actor       TEXT NOT NULL,
  action      TEXT NOT NULL,
  incident_id TEXT NOT NULL,
  old_status  TEXT NOT NULL,
  new_status  TEXT NOT NULL,
  description TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_audit_incident ON audit_log(incident_id);
CREATE TABLE IF NOT EXISTS notifications (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  channel TEXT NOT NULL,
  subject TEXT NOT NULL,
  body    TEXT NOT NULL,
  at      TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
