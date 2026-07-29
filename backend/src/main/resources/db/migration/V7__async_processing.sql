ALTER TABLE photos ADD COLUMN processing_status VARCHAR(20) DEFAULT 'DONE';
ALTER TABLE photos ADD COLUMN error_message VARCHAR(500);
CREATE INDEX idx_photos_processing_status ON photos(processing_status);
