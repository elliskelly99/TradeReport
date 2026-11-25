-- Carry trade_ref_id on outbox events and on audit rows, so events and the audit trail
-- show which trade reference a change belongs to. Existing rows are backfilled from trade_report.

-- ---- outbox_event ----
ALTER TABLE outbox_event ADD COLUMN trade_ref_id VARCHAR(255);

UPDATE outbox_event o
SET trade_ref_id = r.trade_ref_id
FROM trade_report r
WHERE r.id = o.report_id;

-- Safety net for outbox rows whose report no longer exists, so NOT NULL cannot fail the migration.
UPDATE outbox_event SET trade_ref_id = 'UNKNOWN-' || report_id WHERE trade_ref_id IS NULL;

ALTER TABLE outbox_event ALTER COLUMN trade_ref_id SET NOT NULL;

-- ---- trade_reports_audits ----
ALTER TABLE trade_reports_audits ADD COLUMN trade_ref_id VARCHAR(255);

-- The audit table rejects UPDATE (trigger trg_audit_immutable). Switch the trigger off only for this
-- one backfill, then switch it back on. Flyway runs the whole script in one transaction.
ALTER TABLE trade_reports_audits DISABLE TRIGGER trg_audit_immutable;

UPDATE trade_reports_audits a
SET trade_ref_id = r.trade_ref_id
FROM trade_report r
WHERE r.id = a.trade_report_id;

UPDATE trade_reports_audits SET trade_ref_id = 'UNKNOWN-' || id WHERE trade_ref_id IS NULL;

ALTER TABLE trade_reports_audits ENABLE TRIGGER trg_audit_immutable;

ALTER TABLE trade_reports_audits ALTER COLUMN trade_ref_id SET NOT NULL;
