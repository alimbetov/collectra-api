--liquibase formatted sql
--changeset collectra:038-contract-validity-indexes

CREATE INDEX idx_contract_tenant_valid_from
    ON contracts (tenant_id, valid_from, id);

CREATE INDEX idx_contract_tenant_valid_to
    ON contracts (tenant_id, valid_to, id)
    WHERE valid_to IS NOT NULL;
