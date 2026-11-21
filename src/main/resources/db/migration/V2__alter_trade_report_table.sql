ALTER TABLE trade_report ADD COLUMN trade_ref_id  VARCHAR(255);

update trade_report set trade_ref_id = 'LEGACY ROW-' || id where trade_ref_id is null;

ALTER TABLE trade_report ALTER COLUMN trade_ref_id SET NOT NULL;

ALTER TABLE trade_report ADD CONSTRAINT uq_trade_report_firm_ref UNIQUE (firm, trade_ref_id);

