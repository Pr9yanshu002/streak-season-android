-- Arc — shared schema (v0)
--
-- This file is the source of truth for both clients.
-- Room (Android) and GRDB (iOS) must produce structurally identical databases.
--
-- Sync-readiness rules that apply to EVERY table:
--   * id is a UUID v4 string generated on device — never an auto-increment int
--   * created_at / updated_at / deleted_at / synced_at are epoch millis (INTEGER)
--   * every write bumps updated_at
--   * nothing is ever hard-deleted; set deleted_at instead
--   * synced_at is null until the row has been pushed to the server
--
-- Dates are stored as 'YYYY-MM-DD' local-date strings, never timestamps.
-- This is deliberate: a user who flies Mumbai -> New York mid-arc must not
-- gain or lose a day.


-- ---------------------------------------------------------------------------
-- arcs
-- ---------------------------------------------------------------------------
CREATE TABLE arcs (
    id                  TEXT     PRIMARY KEY NOT NULL,
    name                TEXT     NOT NULL,

    start_date          TEXT     NOT NULL,          -- YYYY-MM-DD, inclusive
    end_date            TEXT     NOT NULL,          -- YYYY-MM-DD, inclusive

    status              TEXT     NOT NULL,          -- draft | active | completed | failed | archived
    strictness          TEXT     NOT NULL,          -- strict | standard | chill

    rest_tokens_per_week INTEGER NOT NULL DEFAULT 1,-- standard mode only; 0 in strict
    backfill_days       INTEGER  NOT NULL DEFAULT 2,-- how many past days may still be edited
    day_boundary_hour   INTEGER  NOT NULL DEFAULT 4,-- 0-23, local; the day rolls over here

    metaphor            TEXT     NOT NULL DEFAULT 'tree',
    accent_color        TEXT,                       -- '#RRGGBB'
    cover_seed          TEXT,                       -- seeds the generated gradient
    intention           TEXT,                       -- the day-1 "why"

    failed_on_date      TEXT,                       -- YYYY-MM-DD, set when status = failed
    completed_at        INTEGER,                    -- epoch millis, set when status = completed
    remixed_from_arc_id TEXT,                       -- provenance when created via Remix

    created_at          INTEGER  NOT NULL,
    updated_at          INTEGER  NOT NULL,
    deleted_at          INTEGER,
    synced_at           INTEGER,

    FOREIGN KEY (remixed_from_arc_id) REFERENCES arcs(id)
);

CREATE INDEX idx_arcs_status     ON arcs(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_arcs_updated_at ON arcs(updated_at);


-- ---------------------------------------------------------------------------
-- commitments
-- ---------------------------------------------------------------------------
-- active_from / active_until exist so that adding a commitment on day 20
-- counts from day 20 and never rewrites history, and removing one hides it
-- from Today without destroying its past entries.
-- ---------------------------------------------------------------------------
CREATE TABLE commitments (
    id              TEXT     PRIMARY KEY NOT NULL,
    arc_id          TEXT     NOT NULL,

    name            TEXT     NOT NULL,
    icon            TEXT,

    type            TEXT     NOT NULL,              -- binary | counter | duration | log
    target_value    REAL,                           -- null for binary and log
    unit            TEXT,                           -- 'litres' | 'reps' | 'minutes' | 'pages' | ...

    is_required     INTEGER  NOT NULL DEFAULT 1,    -- 0 = bonus, 1 = required
    schedule_type   TEXT     NOT NULL,              -- daily | weekdays
    schedule_days   TEXT,                           -- '1,3,5' ISO weekday numbers, Mon=1 .. Sun=7
                                                    -- required when schedule_type = 'weekdays'

    active_from     TEXT     NOT NULL,              -- YYYY-MM-DD
    active_until    TEXT,                           -- YYYY-MM-DD, null = still active

    sort_order      INTEGER  NOT NULL DEFAULT 0,

    created_at      INTEGER  NOT NULL,
    updated_at      INTEGER  NOT NULL,
    deleted_at      INTEGER,
    synced_at       INTEGER,

    FOREIGN KEY (arc_id) REFERENCES arcs(id)
);

CREATE INDEX idx_commitments_arc        ON commitments(arc_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_commitments_updated_at ON commitments(updated_at);


-- ---------------------------------------------------------------------------
-- days
-- ---------------------------------------------------------------------------
-- A day row exists only once the user has touched that date (an entry, a note,
-- a photo, or a rest token). Absence of a row is meaningful: nothing logged.
--
-- Day STATE is never stored. It is always derived from entries — see rules.md.
-- rest_token_used is stored because spending a token is a user decision,
-- not a derivation.
-- ---------------------------------------------------------------------------
CREATE TABLE days (
    id              TEXT     PRIMARY KEY NOT NULL,
    arc_id          TEXT     NOT NULL,
    date            TEXT     NOT NULL,              -- YYYY-MM-DD, local

    note            TEXT,
    photo_path      TEXT,
    rest_token_used INTEGER  NOT NULL DEFAULT 0,

    created_at      INTEGER  NOT NULL,
    updated_at      INTEGER  NOT NULL,
    deleted_at      INTEGER,
    synced_at       INTEGER,

    FOREIGN KEY (arc_id) REFERENCES arcs(id)
);

CREATE UNIQUE INDEX idx_days_arc_date   ON days(arc_id, date);
CREATE INDEX        idx_days_updated_at ON days(updated_at);


-- ---------------------------------------------------------------------------
-- entries
-- ---------------------------------------------------------------------------
-- One row per (day, commitment) that the user has logged.
-- value is a REAL for all four types:
--   binary   -> 0 or 1
--   counter  -> the count      (e.g. 3 litres)
--   duration -> minutes        (e.g. 45)
--   log      -> the raw number (e.g. 72.4 kg) — never pass/fail
-- ---------------------------------------------------------------------------
CREATE TABLE entries (
    id            TEXT     PRIMARY KEY NOT NULL,
    day_id        TEXT     NOT NULL,
    commitment_id TEXT     NOT NULL,

    value         REAL     NOT NULL,

    created_at    INTEGER  NOT NULL,
    updated_at    INTEGER  NOT NULL,
    deleted_at    INTEGER,
    synced_at     INTEGER,

    FOREIGN KEY (day_id)        REFERENCES days(id),
    FOREIGN KEY (commitment_id) REFERENCES commitments(id)
);

CREATE UNIQUE INDEX idx_entries_day_commitment ON entries(day_id, commitment_id);
CREATE INDEX        idx_entries_updated_at     ON entries(updated_at);


-- ---------------------------------------------------------------------------
-- milestones
-- ---------------------------------------------------------------------------
-- 'start' is created with the arc. p25/p50/p75 are created lazily when the
-- threshold is first crossed. 'final' is created when the arc completes or fails.
-- acknowledged_at = null means the full-screen card is still pending.
-- ---------------------------------------------------------------------------
CREATE TABLE milestones (
    id                TEXT     PRIMARY KEY NOT NULL,
    arc_id            TEXT     NOT NULL,

    kind              TEXT     NOT NULL,            -- start | p25 | p50 | p75 | final
    triggered_on_date TEXT,                         -- YYYY-MM-DD
    reflection        TEXT,
    photo_path        TEXT,
    acknowledged_at   INTEGER,

    created_at        INTEGER  NOT NULL,
    updated_at        INTEGER  NOT NULL,
    deleted_at        INTEGER,
    synced_at         INTEGER,

    FOREIGN KEY (arc_id) REFERENCES arcs(id)
);

CREATE UNIQUE INDEX idx_milestones_arc_kind   ON milestones(arc_id, kind);
CREATE INDEX        idx_milestones_updated_at ON milestones(updated_at);
