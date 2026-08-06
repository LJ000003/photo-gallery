-- 水印参数落库：重试/兜底重扫（retryProcessing / recoverStuckProcessing）补发处理消息时
-- 从 DB 读取水印，避免消息丢失后照片"静默无印"标 DONE（P0 修复）
ALTER TABLE photos ADD COLUMN watermark VARCHAR(255);
