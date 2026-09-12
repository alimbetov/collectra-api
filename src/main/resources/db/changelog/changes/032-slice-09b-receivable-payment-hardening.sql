--liquibase formatted sql
--changeset collectra:032-slice-09b-receivable-payment-hardening

ALTER TABLE invoices
    ADD CONSTRAINT fk_invoice_contract
        FOREIGN KEY (contract_id) REFERENCES contracts(id);

ALTER TABLE payment_allocations
    ADD COLUMN command_id UUID,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN reversed_at TIMESTAMPTZ,
    ADD COLUMN reversed_by VARCHAR(200),
    ADD COLUMN reversal_reason VARCHAR(200);

UPDATE payment_allocations
   SET command_id = gen_random_uuid()
 WHERE command_id IS NULL;

ALTER TABLE payment_allocations
    ALTER COLUMN command_id SET NOT NULL;

ALTER TABLE payment_allocations
    ADD CONSTRAINT uk_payment_allocation_tenant_command UNIQUE (tenant_id, command_id),
    ADD CONSTRAINT chk_payment_allocation_status CHECK (status IN ('ACTIVE', 'REVERSED')),
    ADD CONSTRAINT chk_payment_allocation_reversal CHECK (
        (status = 'ACTIVE' AND reversed_at IS NULL)
        OR (status = 'REVERSED' AND reversed_at IS NOT NULL AND reversal_reason IS NOT NULL)
    );

CREATE INDEX idx_invoices_tenant_created
    ON invoices(tenant_id, created_at DESC, id DESC);
CREATE INDEX idx_invoices_tenant_status_created
    ON invoices(tenant_id, payment_status, created_at DESC, id DESC);
CREATE INDEX idx_invoices_tenant_contract_created
    ON invoices(tenant_id, contract_id, created_at DESC, id DESC);
CREATE INDEX idx_payments_tenant_created
    ON payments(tenant_id, created_at DESC, id DESC);
CREATE INDEX idx_payments_tenant_customer_created
    ON payments(tenant_id, customer_id, created_at DESC, id DESC);
CREATE INDEX idx_payments_tenant_date
    ON payments(tenant_id, payment_date DESC, id DESC);
CREATE INDEX idx_payment_alloc_active_payment
    ON payment_allocations(tenant_id, payment_id, status);
CREATE INDEX idx_payment_alloc_active_invoice
    ON payment_allocations(tenant_id, invoice_id, status);
