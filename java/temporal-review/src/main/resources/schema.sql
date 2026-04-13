-- Review Outcome Learning Agent — Database Schema
-- Run against Neon Postgres to initialize all tables

-- ============================================================
-- Event Tables (Raw Facts)
-- ============================================================

CREATE TABLE IF NOT EXISTS pull_requests (
    id              SERIAL PRIMARY KEY,
    repository      VARCHAR(255) NOT NULL,
    pr_number       INTEGER NOT NULL,
    pr_title        TEXT,
    pr_description  TEXT,
    author          VARCHAR(255),
    created_at      TIMESTAMPTZ,
    merged_at       TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    status          VARCHAR(20),
    UNIQUE(repository, pr_number)
);

CREATE TABLE IF NOT EXISTS review_runs (
    id                      SERIAL PRIMARY KEY,
    pull_request_id         INTEGER REFERENCES pull_requests(id),
    review_id               VARCHAR(255) UNIQUE NOT NULL,
    learning_version        INTEGER NOT NULL DEFAULT 0,
    overall_recommendation  VARCHAR(20) NOT NULL,
    agent_results_json      JSONB NOT NULL,
    reviewed_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    took_ms                 BIGINT,
    model                   VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS findings (
    id              SERIAL PRIMARY KEY,
    review_run_id   INTEGER REFERENCES review_runs(id),
    agent_name      VARCHAR(50) NOT NULL,
    risk_level      VARCHAR(10) NOT NULL,
    finding_text    TEXT NOT NULL,
    recommendation  VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS finding_outcomes (
    id              SERIAL PRIMARY KEY,
    finding_id      INTEGER REFERENCES findings(id) UNIQUE,
    disposition     VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    evidence        TEXT,
    determined_at   TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS post_merge_outcomes (
    id                  SERIAL PRIMARY KEY,
    pull_request_id     INTEGER REFERENCES pull_requests(id),
    had_revert          BOOLEAN DEFAULT FALSE,
    revert_pr_number    INTEGER,
    had_follow_up_fixes BOOLEAN DEFAULT FALSE,
    fix_pr_numbers      INTEGER[],
    related_bug_issues  INTEGER[],
    detected_at         TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS review_comments (
    id                  SERIAL PRIMARY KEY,
    pull_request_id     INTEGER REFERENCES pull_requests(id),
    commenter           VARCHAR(255),
    comment_body        TEXT,
    is_system_comment   BOOLEAN DEFAULT FALSE,
    github_comment_id   BIGINT,
    created_at          TIMESTAMPTZ
);

-- ============================================================
-- Learning State Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS learned_heuristics (
    id              SERIAL PRIMARY KEY,
    repository      VARCHAR(255) NOT NULL,
    agent_name      VARCHAR(50),
    heuristic_type  VARCHAR(50) NOT NULL,
    rule            JSONB NOT NULL,
    description     TEXT NOT NULL,
    evidence        TEXT,
    status          VARCHAR(20) DEFAULT 'PROPOSED',
    learning_version INTEGER,
    activated_version INTEGER,
    proposed_at     TIMESTAMPTZ DEFAULT NOW(),
    approved_at     TIMESTAMPTZ,
    approved_by     VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS agent_precision_profiles (
    id                  SERIAL PRIMARY KEY,
    repository          VARCHAR(255) NOT NULL,
    agent_name          VARCHAR(50) NOT NULL,
    learning_version    INTEGER NOT NULL,
    total_findings      INTEGER,
    accepted_findings   INTEGER,
    dismissed_findings  INTEGER,
    deferred_findings   INTEGER,
    precision_rate      DOUBLE PRECISION,
    computed_at         TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(repository, agent_name, learning_version)
);

CREATE TABLE IF NOT EXISTS severity_calibrations (
    id              SERIAL PRIMARY KEY,
    repository      VARCHAR(255) NOT NULL,
    agent_name      VARCHAR(50) NOT NULL,
    category        VARCHAR(100),
    original_level  VARCHAR(10),
    calibrated_level VARCHAR(10),
    confidence      DOUBLE PRECISION,
    sample_size     INTEGER,
    status          VARCHAR(20) DEFAULT 'PROPOSED',
    learning_version INTEGER,
    activated_version INTEGER
);

CREATE TABLE IF NOT EXISTS prompt_patch_suggestions (
    id              SERIAL PRIMARY KEY,
    repository      VARCHAR(255) NOT NULL,
    agent_name      VARCHAR(50) NOT NULL,
    patch_type      VARCHAR(50) NOT NULL,
    description     TEXT NOT NULL,
    patch_content   TEXT NOT NULL,
    evidence        TEXT,
    status          VARCHAR(20) DEFAULT 'PROPOSED',
    learning_version INTEGER,
    activated_version INTEGER,
    proposed_at     TIMESTAMPTZ DEFAULT NOW(),
    approved_at     TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS learning_versions (
    id              SERIAL PRIMARY KEY,
    repository      VARCHAR(255) NOT NULL,
    version         INTEGER NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      VARCHAR(255),
    rollback_of     INTEGER,
    UNIQUE(repository, version)
);

-- ============================================================
-- Evaluation Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS evaluation_snapshots (
    id                          SERIAL PRIMARY KEY,
    repository                  VARCHAR(255) NOT NULL,
    learning_version            INTEGER NOT NULL,
    snapshot_at                 TIMESTAMPTZ DEFAULT NOW(),
    accepted_finding_rate       DOUBLE PRECISION,
    false_positive_rate         DOUBLE PRECISION,
    missed_issue_rate           DOUBLE PRECISION,
    post_merge_bug_rate         DOUBLE PRECISION,
    avg_findings_per_pr         DOUBLE PRECISION,
    high_signal_findings_per_pr DOUBLE PRECISION,
    reviewer_agreement_rate     DOUBLE PRECISION,
    avg_time_to_merge_hours     DOUBLE PRECISION,
    total_reviews               INTEGER,
    total_findings              INTEGER
);

CREATE TABLE IF NOT EXISTS learning_version_deltas (
    id                  SERIAL PRIMARY KEY,
    repository          VARCHAR(255) NOT NULL,
    from_version        INTEGER NOT NULL,
    to_version          INTEGER NOT NULL,
    metric_name         VARCHAR(100) NOT NULL,
    from_value          DOUBLE PRECISION,
    to_value            DOUBLE PRECISION,
    delta               DOUBLE PRECISION,
    improvement         BOOLEAN,
    computed_at         TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS weekly_agent_metrics (
    id              SERIAL PRIMARY KEY,
    repository      VARCHAR(255) NOT NULL,
    agent_name      VARCHAR(50) NOT NULL,
    week_start      DATE NOT NULL,
    learning_version INTEGER,
    precision_rate  DOUBLE PRECISION,
    false_positive_count INTEGER,
    accepted_count  INTEGER,
    missed_count    INTEGER,
    total_findings  INTEGER,
    UNIQUE(repository, agent_name, week_start)
);

CREATE TABLE IF NOT EXISTS weekly_learning_metrics (
    id                      SERIAL PRIMARY KEY,
    repository              VARCHAR(255) NOT NULL,
    week_start              DATE NOT NULL,
    learning_version        INTEGER,
    heuristics_proposed     INTEGER,
    heuristics_approved     INTEGER,
    heuristics_rejected     INTEGER,
    active_heuristics       INTEGER,
    prompt_patches_proposed INTEGER,
    prompt_patches_approved INTEGER,
    UNIQUE(repository, week_start)
);

-- ============================================================
-- Indexes
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_review_runs_pr ON review_runs(pull_request_id);
CREATE INDEX IF NOT EXISTS idx_review_runs_version ON review_runs(learning_version);
CREATE INDEX IF NOT EXISTS idx_findings_review ON findings(review_run_id);
CREATE INDEX IF NOT EXISTS idx_finding_outcomes_finding ON finding_outcomes(finding_id);
CREATE INDEX IF NOT EXISTS idx_heuristics_repo_status ON learned_heuristics(repository, status);
CREATE INDEX IF NOT EXISTS idx_precision_repo ON agent_precision_profiles(repository, agent_name);
CREATE INDEX IF NOT EXISTS idx_eval_repo ON evaluation_snapshots(repository, learning_version);
CREATE INDEX IF NOT EXISTS idx_pr_repo ON pull_requests(repository, pr_number);
