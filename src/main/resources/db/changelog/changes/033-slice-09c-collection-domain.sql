--liquibase formatted sql
--changeset collectra:033-slice-09c-collection-domain

CREATE TABLE collection_cases (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    assigned_to UUID,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    close_reason VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_collection_case_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_collection_case_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT chk_collection_case_status CHECK (status IN ('OPEN','IN_PROGRESS','ON_HOLD','CLOSED')),
    CONSTRAINT chk_collection_case_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT chk_collection_case_close_reason CHECK (
        close_reason IS NULL OR close_reason IN ('PAID','SETTLED','WRITTEN_OFF','DUPLICATE','CANCELLED','OTHER')
    ),
    CONSTRAINT chk_collection_case_closed CHECK (
        (status = 'CLOSED' AND closed_at IS NOT NULL AND close_reason IS NOT NULL)
        OR (status <> 'CLOSED' AND closed_at IS NULL AND close_reason IS NULL)
    )
);

CREATE UNIQUE INDEX uk_collection_case_active_invoice
    ON collection_cases(tenant_id, invoice_id)
    WHERE status IN ('OPEN','IN_PROGRESS','ON_HOLD');
CREATE INDEX idx_collection_case_tenant_created
    ON collection_cases(tenant_id, created_at DESC, id DESC);
CREATE INDEX idx_collection_case_tenant_status_created
    ON collection_cases(tenant_id, status, created_at DESC, id DESC);
CREATE INDEX idx_collection_case_assignee_status
    ON collection_cases(tenant_id, assigned_to, status, created_at DESC, id DESC);

CREATE TABLE promises_to_pay (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    case_id UUID NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    promised_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_promise_case FOREIGN KEY (case_id) REFERENCES collection_cases(id),
    CONSTRAINT chk_promise_amount CHECK (amount > 0),
    CONSTRAINT chk_promise_status CHECK (status IN ('ACTIVE','FULFILLED','BROKEN','CANCELLED')),
    CONSTRAINT chk_promise_resolved CHECK (
        (status = 'ACTIVE' AND resolved_at IS NULL)
        OR (status <> 'ACTIVE' AND resolved_at IS NOT NULL)
    )
);
CREATE INDEX idx_promise_case_created
    ON promises_to_pay(tenant_id, case_id, created_at DESC, id DESC);
CREATE INDEX idx_promise_active_date
    ON promises_to_pay(tenant_id, status, promised_date, id);

CREATE TABLE collection_disputes (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    case_id UUID NOT NULL,
    reason VARCHAR(80) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    resolution_code VARCHAR(80),
    resolution_summary VARCHAR(1000),
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_dispute_case FOREIGN KEY (case_id) REFERENCES collection_cases(id),
    CONSTRAINT chk_dispute_status CHECK (status IN ('OPEN','RESOLVED','CANCELLED')),
    CONSTRAINT chk_dispute_resolved CHECK (
        (status = 'OPEN' AND resolved_at IS NULL)
        OR (status <> 'OPEN' AND resolved_at IS NOT NULL)
    )
);
CREATE INDEX idx_dispute_case_created
    ON collection_disputes(tenant_id, case_id, created_at DESC, id DESC);

CREATE TABLE collection_actions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    case_id UUID NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    description VARCHAR(1000),
    due_at TIMESTAMPTZ NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_collection_action_case FOREIGN KEY (case_id) REFERENCES collection_cases(id),
    CONSTRAINT chk_collection_action_priority CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    CONSTRAINT chk_collection_action_status CHECK (status IN ('PENDING','COMPLETED','CANCELLED'))
);
CREATE INDEX idx_collection_action_queue
    ON collection_actions(tenant_id, status, due_at, priority DESC, id ASC);
CREATE INDEX idx_collection_action_case_due
    ON collection_actions(tenant_id, case_id, due_at, id);

CREATE TABLE collection_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    case_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    event_at TIMESTAMPTZ NOT NULL,
    actor VARCHAR(200),
    summary VARCHAR(1000),
    CONSTRAINT fk_collection_event_case FOREIGN KEY (case_id) REFERENCES collection_cases(id)
);
CREATE INDEX idx_collection_event_timeline
    ON collection_events(tenant_id, case_id, event_at DESC, id DESC);
