-- 描述列宽 255 → 500：实体 @Size(max=500)（Bean Validation）与列宽不一致，
-- 256-500 字描述在 MySQL 严格模式下保存即 500。
-- 不显式指定字符集/排序规则（MODIFY 保留列现有 charset/collation，无漂移）——
-- 曾显式写 utf8mb4_unicode_ci 与 name 列实际 collation 不一致，FULLTEXT 索引
-- （V9 ngram，要求同索引列 collation 一致）拒绝变更 → 错误 1283。
ALTER TABLE photos
    MODIFY description VARCHAR(500);
