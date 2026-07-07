-- 照片表：按分类筛选 + 按时间排序
CREATE INDEX idx_photos_category_id ON photos(category_id);
CREATE INDEX idx_photos_created_at ON photos(created_at);

-- 标签 / 分类 / 相册：name 列用于唯一查找
CREATE UNIQUE INDEX idx_tags_name ON tags(name);
CREATE UNIQUE INDEX idx_categories_name ON categories(name);
CREATE UNIQUE INDEX idx_albums_name ON albums(name);

-- EXIF：时间线排序 + 地图坐标筛选
CREATE INDEX idx_exif_date_taken ON exif_data(date_taken);
CREATE INDEX idx_exif_location ON exif_data(latitude, longitude);
