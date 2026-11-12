-- V1__create_initial_schema.sql
-- Initial domain schema for ReviewPilot (PostgreSQL 17 & H2 PostgreSQL-mode compatible)

CREATE TABLE repositories (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    external_repository_id BIGINT NOT NULL,
    owner VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    default_branch VARCHAR(255) NOT NULL,
    sonar_project_key VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_repositories_full_name UNIQUE (full_name),
    CONSTRAINT uk_repositories_provider_external_id UNIQUE (provider, external_repository_id)
);

CREATE INDEX idx_repositories_active ON repositories (active);

CREATE TABLE pull_requests (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL,
    external_id BIGINT NOT NULL,
    number INTEGER NOT NULL,
    title VARCHAR(500) NOT NULL,
    author_login VARCHAR(255) NOT NULL,
    html_url TEXT NOT NULL,
    base_ref VARCHAR(255) NOT NULL,
    head_ref VARCHAR(255) NOT NULL,
    head_sha VARCHAR(40) NOT NULL,
    state VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_pull_requests_repository FOREIGN KEY (repository_id) REFERENCES repositories (id),
    CONSTRAINT uk_pull_requests_repository_number UNIQUE (repository_id, number)
);

CREATE INDEX idx_pull_requests_repository_id ON pull_requests (repository_id);
CREATE INDEX idx_pull_requests_head_sha ON pull_requests (head_sha);

CREATE TABLE review_executions (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL,
    pull_request_id UUID NOT NULL,
    head_sha VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    model_name VARCHAR(128),
    prompt_version VARCHAR(32),
    schema_version VARCHAR(32),
    summary TEXT,
    limitations_json TEXT,
    input_tokens BIGINT,
    output_tokens BIGINT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_code VARCHAR(128),
    failure_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_review_executions_repository FOREIGN KEY (repository_id) REFERENCES repositories (id),
    CONSTRAINT fk_review_executions_pull_request FOREIGN KEY (pull_request_id) REFERENCES pull_requests (id),
    CONSTRAINT uk_review_executions_repo_pr_sha UNIQUE (repository_id, pull_request_id, head_sha)
);

CREATE INDEX idx_review_executions_repository_id ON review_executions (repository_id);
CREATE INDEX idx_review_executions_pull_request_id ON review_executions (pull_request_id);
CREATE INDEX idx_review_executions_status ON review_executions (status);
CREATE INDEX idx_review_executions_created_at ON review_executions (created_at);

CREATE TABLE findings (
    id UUID PRIMARY KEY,
    review_execution_id UUID NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    category VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    file_path TEXT,
    start_line INTEGER,
    end_line INTEGER,
    evidence TEXT NOT NULL,
    recommendation TEXT NOT NULL,
    confidence NUMERIC(4, 3),
    origin VARCHAR(32) NOT NULL,
    external_reference VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_findings_review_execution FOREIGN KEY (review_execution_id) REFERENCES review_executions (id) ON DELETE CASCADE,
    CONSTRAINT uk_findings_execution_fingerprint UNIQUE (review_execution_id, fingerprint)
);

CREATE INDEX idx_findings_review_execution_id ON findings (review_execution_id);
CREATE INDEX idx_findings_severity ON findings (severity);
CREATE INDEX idx_findings_category ON findings (category);
CREATE INDEX idx_findings_fingerprint ON findings (fingerprint);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    delivery_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    repository_full_name VARCHAR(255) NOT NULL,
    pull_request_number INTEGER,
    processing_status VARCHAR(32) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_webhook_deliveries_delivery_id UNIQUE (delivery_id)
);

CREATE INDEX idx_webhook_deliveries_repo_pr ON webhook_deliveries (repository_full_name, pull_request_number);

CREATE TABLE review_actions (
    id UUID PRIMARY KEY,
    review_execution_id UUID NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    decision VARCHAR(32),
    actor VARCHAR(255),
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_review_actions_review_execution FOREIGN KEY (review_execution_id) REFERENCES review_executions (id) ON DELETE CASCADE
);

CREATE INDEX idx_review_actions_review_execution_id ON review_actions (review_execution_id);
CREATE INDEX idx_review_actions_decision ON review_actions (decision);

CREATE TABLE tool_executions (
    id UUID PRIMARY KEY,
    review_execution_id UUID NOT NULL,
    server_name VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    duration_ms BIGINT NOT NULL,
    arguments_summary TEXT,
    error_code VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tool_executions_review_execution FOREIGN KEY (review_execution_id) REFERENCES review_executions (id) ON DELETE CASCADE
);

CREATE INDEX idx_tool_executions_review_execution_id ON tool_executions (review_execution_id);

