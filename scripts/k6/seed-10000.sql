-- 压测造数：1 万行 photos + 关联数据（列表接口不读文件，无真实图片也可测查询/缓存路径）
-- 全部 k6- 前缀，可一键清理：DELETE FROM photos WHERE name LIKE 'k6-%'（级联 exif/photo_tags/photo_albums）
--                       + DELETE FROM categories/tags/albums WHERE name LIKE 'k6-%'
-- 用法：docker exec -i photodb mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 photodb < scripts/k6/seed-10000.sql
-- 依赖：SET SESSION cte_max_recursion_depth（MySQL 8 默认仅 1000，递归 1001 次即中止）

SET SESSION cte_max_recursion_depth = 10000;

-- 1) 1 万行照片：created_at 均匀散布约 2 年；file_hash 唯一（V8 UNIQUE 约束）；processing_status=DONE
INSERT INTO photos
    (name, description, file_name, original_file_name, file_size, content_type,
     created_at, processing_status, file_hash)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT
    CONCAT('k6-', n, '-风景照片'),
    CONCAT('压测照片描述 ', n),
    CONCAT('k6-', n, '.jpg'),
    CONCAT('k6-', n, '.jpg'),
    400000 + n * 137,
    'image/jpeg',
    TIMESTAMP('2024-01-01') + INTERVAL (n * 97) MINUTE,
    'DONE',
    SHA2(CONCAT('k6-', n), 256)
FROM seq;

-- 2) 4 个分类 / 6 个标签 / 3 个相册（k6- 前缀，UNIQUE(name) 不冲突）
INSERT INTO categories (name) VALUES
    ('k6-分类1'), ('k6-分类2'), ('k6-分类3'), ('k6-分类4');
INSERT INTO tags (name, color) VALUES
    ('k6-标签1', '#1677ff'), ('k6-标签2', '#52c41a'), ('k6-标签3', '#faad14'),
    ('k6-标签4', '#722ed1'), ('k6-标签5', '#eb2f96'), ('k6-标签6', '#13c2c2');
INSERT INTO albums (name, description, created_at) VALUES
    ('k6-相册1', 'k6', '2024-01-01'), ('k6-相册2', 'k6', '2024-02-01'), ('k6-相册3', 'k6', '2024-03-01');

-- 3) 关联：10% 照片有分类；20% 挂标签；25% 入相册（取模散布，测筛选/批量加载路径）
UPDATE photos p JOIN categories c ON c.name = CONCAT('k6-分类', 1 + MOD(p.id, 4))
SET p.category_id = c.id
WHERE p.name LIKE 'k6-%' AND MOD(p.id, 10) = 0;

INSERT INTO photo_tags (photo_id, tag_id)
SELECT p.id, t.id FROM photos p JOIN tags t ON t.name = CONCAT('k6-标签', 1 + MOD(p.id, 6))
WHERE p.name LIKE 'k6-%' AND MOD(p.id, 5) = 0;

INSERT INTO photo_albums (photo_id, album_id)
SELECT p.id, a.id FROM photos p JOIN albums a ON a.name = CONCAT('k6-相册', 1 + MOD(p.id, 3))
WHERE p.name LIKE 'k6-%' AND MOD(p.id, 4) = 0;

-- 4) 50% 照片带 EXIF（date_taken/相机/坐标，测 @BatchSize 批量加载 + 时间线/地图路径）
INSERT INTO exif_data (photo_id, date_taken, camera_model, lens_model, focal_length, aperture, shutter_speed, iso, latitude, longitude)
SELECT id, created_at, CONCAT('K6-Camera-', MOD(id, 5)), 'K6-Lens', '50mm', 'f/1.8', '1/250',
       100 + MOD(id, 400), 39.90 + MOD(id, 5000) * 0.0001, 116.30 + MOD(id, 5000) * 0.0001
FROM photos WHERE name LIKE 'k6-%' AND MOD(id, 2) = 0;

-- 5) 更新统计信息（否则优化器按空表估行数）
ANALYZE TABLE photos, exif_data, photo_tags, photo_albums;
