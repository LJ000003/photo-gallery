ALTER TABLE photos ADD COLUMN deleted_at DATETIME NULL;
ALTER TABLE albums ADD COLUMN deleted_at DATETIME NULL;
CREATE INDEX idx_photos_deleted ON photos(deleted_at);
CREATE INDEX idx_albums_deleted ON albums(deleted_at);
