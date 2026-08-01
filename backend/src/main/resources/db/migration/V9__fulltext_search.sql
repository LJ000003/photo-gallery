-- InnoDB FULLTEXT 索引，ngram 分词支持中英文混合搜索
-- ngram_token_size=2：中文双字分词，"北京照片" → "北京"+"京照"+"照片"
ALTER TABLE photos ADD FULLTEXT INDEX ft_photos_search (name, description) WITH PARSER ngram;
