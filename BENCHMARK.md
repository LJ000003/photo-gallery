# k6 压测结果

> 脚本：[scripts/k6/](scripts/k6/)（smoke / photo-list / upload / share 4 场景）
> 原则：只记录真实环境跑出的数据，不提交伪造数字。
> **4 场景全部已实跑（2026-08-06：upload 本地 dev，其余 docker compose 全栈 prod 形态，1 万行数据）。**

## 结果

| 场景 | 形态（缓存态） | 日期 | 环境 | 数据规模（照片数） | VU × 时长 | p95 | 错误率 | 阈值 |
|------|------|------|------|------|-----------|-----|--------|------|
| photo-list | 缓存命中（固定 page=0） | 2026-08-06 | docker 全栈 prod | 10,000（SQL 直插） | 20 × 60s | 10.33ms | 0%（165,469 请求） | p95 < 300ms ✓ |
| photo-list | 随机页 0..499（30s TTL 内混合，回源占比 ≈0.26%） | 2026-08-06 | 同上 | 10,000 | 20 × 60s | 7.96ms | 0%（189,518 请求） | p95 < 300ms ✓ |
| photo-list | 冷回源（FLUSHDB 后随机页首查） | 2026-08-06 | 同上 | 10,000 | 1 × 30 迭代 | 20.6ms | 0% | — |
| photo-list | 深翻页冷回源（page 400–499，OFFSET 8000~10000） | 2026-08-06 | 同上 | 10,000 | 1 × 15 迭代 | 22.5ms | 0% | — |
| photo-list | 搜索 `q=风景`（**无缓存接口**，20 并发全回源） | 2026-08-06 | 同上 | 10,000 | 20 × 60s | **342ms** | 0%（4,176 请求） | p95 < 300ms ✗（两次实测 297.5 / 342ms，**踩线不稳**） |
| photo-list | 搜索单并发 | 2026-08-06 | 同上 | 10,000 | 1 × 30 迭代 | 71.3ms | 0% | — |
| upload | 真实写入吞吐 | 2026-08-06 | docker 全栈 prod | 100（种子化新照片） | 5 VU × 100 迭代（~5s） | 107.5ms | 0%（102/102 请求） | rate < 1% ✓ |
| smoke | SPA 首页 + 静态资源（index.html 引用的 5 个资产逐一下载） | 2026-08-06 | docker 全栈 prod | 不依赖 | 10 × 30s | 50.1ms | 0%（17,868 请求） | p95 < 500ms ✓ |
| share | 分享链路（view 白名单 + 签名剥离）；**setup 内置撤销语义验证** | 2026-08-06 | docker 全栈 prod | 10,000（SQL 直插） | 10 × 30s | 7.47ms | 0%（51,219 请求） | rate < 1% ✓ |

## 说明

- **数据规模口径**：photo-list 的 10,000 行为 SQL 直插造数（`scripts/k6/seed-10000.sql`：1 万行 photos + 5,000 EXIF + 2,000 标签关联 + 2,500 相册关联 + 1,000 带分类，`ANALYZE TABLE` 后执行）——**列表接口不读文件**，验证的是查询/缓存路径；真实上传吞吐由 upload 行给出。跑完按 `DELETE ... WHERE name LIKE 'k6-%'` 清理。
- **缓存命中 vs 回源**：列表 @Cacheable 键含 pageable（`{#tagIds, #categoryIds, #pageable}`），TTL 30s（dev Caffeine 与 prod Redis 一致）；随机页变体在测试首秒即把 500 个键全部回源并填满，60s 内 99.7% 命中——**单看 20 并发混合行（7.8ms）不能证明回源性能，回源延迟以「冷回源」行为准**：1 万行随机页首查 p95 ≈ 32ms（含 4~6 条关联 batch 加载 SQL + JSON 序列化）。
- **深翻页**：page 400–499（OFFSET 8000~10000）与浅页冷回源无差异（p95 32.4ms）——created_at 索引扫描 1 万行本身是毫秒级；offset 分页劣化要到 10 万+ 行才显现（未测，如实说明）。
- **搜索**：`searchResponses` 无 @Cacheable，天然全回源——FULLTEXT MATCH（ngram 双字）+ 随机深页 OFFSET + 关联 batch 加载，20 并发 p95 **两次实测 297.5ms / 342ms（波动，踩 300ms 线不稳，本次超阈值未过）**；单并发 70~71ms，并发放大约 4~5 倍——这是目前列表侧唯一不达标的路径（改进方向：给 searchResponses 加 @Cacheable 或游标分页，尚未实施）。
- **share / smoke 设计**：share.js 的 setup 内置「撤销语义验证」——生成分享 → revoke → 断言 view 401（P0-#6 撤销立即生效，结果并入压测而非单独功能测试）；空库时 setup 直接 fail（原脚本 `if (!photoId) return` 会让空库全绿假阳性）。smoke.js 从 index.html 提取引用的 js/css/svg/webmanifest 逐一下载（静态服务 404 暴露构建问题），非只测首页 200。
- **upload 环境差异**：docker prod 实测 p95 107.5ms（含 RabbitMQ 消息发送 + 容器 I/O）；此前本地 dev 实测 5.58ms（@Async 直处理，无 MQ）——dev/prod 双轨制的真实开销对比，本身就是数据点（上传即返回语义两者一致，用户无感）。
- **压测产出（2026-08-06）**：发现并修复一个 prod 专属缺陷——`PhotoResponse` 直接暴露 Hibernate 懒加载代理（Category/ExifData 实体泄漏，albums 早已 DTO 化），Redis 序列化 `GenericJackson2JsonRedisSerializer` 遇代理即 500；**dev Caffeine（对象直存）+ 空库（无内容可序列化）双重掩盖，仅「有数据 + Redis（prod 形态）」触发**。修复：`CategoryResponse`/`TagResponse`/`ExifDataResponse` DTO 化（JSON 契约不变，前端零改动），后端 421 测试全绿后重建容器复测通过。
- 认证端点限流 10 req/s/IP：每个场景在 `setup()` 中只解锁一次（HTTP 不能在 init context 发起）。
