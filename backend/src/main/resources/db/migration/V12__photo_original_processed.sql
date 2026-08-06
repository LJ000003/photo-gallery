-- 原图「已处理」标记：writeOriginalJpeg 成功后立即落 true，重试/兜底重扫时跳过水印与写回，
-- 避免水印重复叠加进原图（P0 修复：部分失败后重试会二次解码带水印原图再画一层）。
-- DONE 照片已走完处理链，统一置 true；FAILED/PROCESSING 保持 false（可能尚未写回，需重试时重打）。
ALTER TABLE photos ADD COLUMN original_processed TINYINT(1) NOT NULL DEFAULT 0;
UPDATE photos SET original_processed = 1 WHERE processing_status = 'DONE';
