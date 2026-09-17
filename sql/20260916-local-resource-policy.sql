-- Apply once after 20260916-korean-card-availability.sql, unless schema update is automatic.
ALTER TABLE card_model ADD COLUMN translation_misses INT NULL DEFAULT 0;
ALTER TABLE card_model ADD COLUMN next_translation_check_at DATETIME(6) NULL;
CREATE INDEX idx_card_translation_check ON card_model(next_translation_check_at);
