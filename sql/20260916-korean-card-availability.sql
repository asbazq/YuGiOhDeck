-- Apply once before deploying this version when Hibernate ddl-auto does not update schema.
-- No Korean release date is inferred from translation data.
ALTER TABLE card_model
    ADD COLUMN korean_release_status VARCHAR(16) NULL DEFAULT 'UNKNOWN';

-- Existing translation status remains derived from kor_name/kor_desc.
-- Do not overwrite has_kor_name/has_kor_desc if your deployment uses generated columns.
