ALTER TABLE photos ADD COLUMN file_hash VARCHAR(64) NULL;
CREATE UNIQUE INDEX idx_photos_file_hash ON photos(file_hash);
