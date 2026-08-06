# Photo Gallery · 照片管理器

> 极简全栈照片管理应用 — 朋友间的私人图库

[English](README.en.md) | 简体中文

Spring Boot 3 + Vue 3 单页应用——开发期前后端分离（Vite 开发服务器代理 `/api` 到后端），生产环境前端构建产物内嵌于后端 JAR 同源伺服。Konami Challenge-Response 门禁 + JWT 双角色鉴权（viewer 白名单校验）；支持 EXIF 时间线/地图浏览、相册分组、图片编辑、水印、WebP 转换、MySQL FULLTEXT 全文搜索、Redis 分布式缓存与 RabbitMQ 异步图片处理（prod）、Micrometer + Prometheus 监控、限时分享链接、PWA 安装与离线缓存、一键 Docker 部署。

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 17 |
| 后端框架 | Spring Boot | 3.3.13 |
| 安全 | Spring Security + JWT (jjwt) | 0.12.6 |
| ORM | Spring Data JPA + Hibernate | — |
| 数据库 | MySQL + Flyway 迁移（含 FULLTEXT 全文索引） | 8.0 |
| 缓存 | Spring Cache + Caffeine（dev）/ Redis（prod，7-alpine） | — |
| 消息队列 | RabbitMQ（prod，持久队列 + 3 次重试 + DLQ） | 3.13 |
| 搜索 | MySQL FULLTEXT + ngram 中文分词（token 2） | — |
| EXIF | metadata-extractor | 2.19.0 |
| 图片编码 | webp-imageio（JDK ImageIO 插件） | 0.1.6 |
| 监控 | Micrometer + Prometheus（Docker 镜像 prom/prometheus） | v3.2.0 |
| 健康检查 | Spring Boot Actuator（仅 health + prometheus） | — |
| API 文档 | SpringDoc OpenAPI（dev 启用，prod 关闭） | 2.5.0 |
| 前端框架 | Vue 3 (Composition API) | 3.5 |
| 状态管理 | Pinia | 3.x |
| 路由 | vue-router | 4.x |
| 语言 | TypeScript | 6.x |
| 构建 | Vite | 5.4 |
| 虚拟滚动 | @tanstack/vue-virtual | 3.13.31 |
| 地图 | Leaflet + leaflet.markercluster | 1.9.4 / 1.5.3 |
| UI | ant-design-vue（主题令牌 theme.ts 驱动） | 4.2.6 |
| 统计图表 | uPlot | 1.6.32 |
| 国际化 | vue-i18n（前端，zh-CN / en-US） | 11.x |
| PWA | vite-plugin-pwa + Workbox | 1.3 |
| 代码质量 | ESLint 10 + Prettier 3 + SpotBugs + JaCoCo（覆盖率 ≥35%） | — |
| 部署 | Docker Compose / Nginx 反向代理 | — |

---

## 功能

### 照片管理
- **上传** — 拖拽/粘贴/批量（单次最多 50 张），魔数校验（JPEG/PNG/GIF/BMP/WebP），客户端 Canvas 压缩大图（最大 1920px，JPEG 质量 0.85），XHR 实时进度条，SHA-256 去重检测（单张 409 返回已有照片，批量静默跳过）
- **浏览** — 虚拟滚动（DOM 节点数恒定，万张照片不卡）、3D 倾斜卡片、骨架屏加载
- **编辑** — 名称/描述修改、分类/标签/相册分配。`PUT /photos/{id}` 的可选字段语义为「null = 不修改」；需显式清除时用哨兵值 `0`（`categoryId: 0` = 清除分类，与 `albumId=0`「未分配」约定一致，不存在返回 404）
- **批量操作** — 多选、全选、批量删除、批量编辑（`categoryOp` 枚举 NONE/SET/CLEAR 三态）、批量生成分享链接
- **搜索** — MySQL FULLTEXT 全文索引 + ngram 中文分词（双字 token），`MATCH ... AGAINST` 布尔模式搜索名称和描述；单字或非 MySQL 数据库（H2 等）自动降级为 LIKE 子串匹配
- **排序** — 时间/名称/大小，正序倒序自由切换

### 图片处理
- **异步处理管线** — 上传后立即返回，EXIF 提取 → 自动旋转 → 水印 → 缩略图（200px + 400px）→ WebP 由后台完成。dev 环境使用 `@Async` 线程池（队列满丢弃，不在请求线程同步执行），prod 环境切换 RabbitMQ 消息队列（持久队列 + 3 次重试（requeue）+ 死信队列兜底）。照片卡片自动轮询状态更新，无需手动刷新。失败可一键重试（`POST /photos/{id}/retry-processing`），启动时立即恢复一次 + 每 5 分钟定时重扫卡在 PROCESSING 状态的照片
- **响应式缩略图** — 上传自动生成 200px + 400px 双档，前端 `srcset` + `sizes` 按视口和 DPR 自动选择
- **编辑器** — Canvas 全分辨率旋转（任意角度）/镜像（水平/垂直）/裁剪
- **水印** — 右下角半透明白色文字，字号自适应图片宽度，字体/字号比例/透明度可配置（`photo.watermark.*`）
- **WebP** — 上传自动生成 Lossy WebP 副本，运行时检测浏览器支持
- **EXIF 自动旋转** — 根据 Orientation 标签自动纠正方向

### 分类体系
- **分类** — 多对一互斥归类
- **标签** — 多对多交叉标记，自定义颜色
- **相册** — 多对多分组，封面卡片网格，时间/名称排序，「未分配」自动汇总

### EXIF 与浏览
- **EXIF 提取** — 拍摄时间、相机型号、镜头型号、焦距、光圈、快门、ISO、GPS
- **EXIF 详情面板** — 照片查看弹窗内可折叠面板，按相机信息/拍摄参数/时间地点三组展示，中英双语
- **时间线** — 按年月分组，正序/倒序
- **地图** — Leaflet 聚合标注，WGS-84 → GCJ-02 坐标转换（服务端非破坏性转换），高德卫星底图 + 道路标注叠加

### 安全
- **Konami 门禁** — Challenge-Response 架构：前端按键只记录不验证 → `GET /api/v1/auth/challenge` 获取一次性 nonce（60s TTL）→ `POST /api/v1/auth/unlock` 提交 nonce + 序列给后端验证，序列仅存后端配置文件。键盘 + 触摸双模式
- **暴力破解防护** — 认证端点 IP 限流（10 次/s）+ 失败计数（Caffeine），5 次错误封禁 15 分钟
- **JWT 双角色** — admin（24h，管理）；分享不再签发 viewer JWT（P0-#6/#7，见下）
- **可撤销分享链接** — 选中照片生成分享链接，朋友无需密码即可查看。凭证为 DB 高熵随机 token（`share_tokens` 表，V10）：同内容重复生成幂等复用同一链接，弹窗一键撤销（`POST /api/v1/share/{token}/revoke`，撤销后旧链接立即失效）；photoIds 白名单 + `permission`（仅 `view`/`download`，非法值 400）编码在 token 记录中，JwtAuthFilter 每个请求查表校验（不缓存 → 撤销即时生效）；legacy viewer JWT 分支仅过渡兼容（存量链接 7 天自然失效、不可撤销）
- **图片短时签名 URL** — `<img>` 无法携带 Authorization 头，图片地址改用 HMAC 短时签名（`photoId.时间桶.hmac`，300s 时间桶 ±1 滑动窗口约 10 分钟，密钥从 JWT_SECRET 单向派生），会话 JWT 不再出现在 URL query string（防日志/历史/Referer 泄漏）；分享响应剥离签名防止借签名越权下载
- **分享权限强制执行** — `/api/v1/photos/{id}/file` 端点强制 `permission=download`：view 权限或缺失一律 403；缩略图/WebP 缺失时按角色回退（admin 回退原图，viewer 一律 404，`w` 白名单 200/400——封堵 view-only 分享借回退下载原图，P0-#3）
- **SHA-256 去重** — 上传时计算文件哈希（事务外计算，不持数据库连接），检测重复上传，单张返回 409 + 已有照片数据，批量静默跳过
- **统一参数校验** — 缺必填参数、参数类型错误、非法枚举统一返回 400 + 参数名（`GlobalExceptionHandler` 兜底，不落 500）
- **缓存策略** — dev 使用 Caffeine 本地缓存（30s TTL），prod 切换 Redis 分布式缓存（JSON 序列化 + PageImpl mixin 反序列化），所有写操作自动驱逐相关缓存
- **软删除 + 回收站** — 删除标记 `deleted_at`（Hibernate `@SQLRestriction` 全局过滤），删除时清空哈希允许重新上传。5 秒 Toast 撤销 + 回收站可恢复/彻底删除，每天凌晨 3 点自动清理 30 天前记录
- **备份导出** — 标题栏一键下载 `photo-gallery-backup-YYYY-MM-DD.zip`：全部原始照片 + 数据库元数据（photos/exif/tags/categories/albums JSON，关联内嵌），java.util.zip 流式打包不占服务器内存（零第三方依赖），预生成缓存 + 数据指纹比对（数据未变直接秒回），可选按相册/分类/日期筛选，响应禁止缓存（仅 admin）
- **前端错误边界** — 组件异常自动捕获，显示降级页面（重试 / 刷新 / 复制错误信息），切换路由自动恢复

### PWA（渐进式 Web 应用）
- 可安装到桌面/主屏幕（Android Chrome + iOS Safari + 桌面 Edge/Chrome）
- Service Worker 离线缓存（Workbox runtime caching）：
  - 静态资源：构建时预缓存（`globPatterns` 含字体）
  - 缩略图 `/api/photos/{id}/thumbnail`：CacheFirst，7 天，最多 500 条
  - 原图/WebP `/api/photos/{id}/file|webp`：NetworkFirst，1 天，最多 100 条
  - API 响应（photos/tags/categories/albums/timeline/map）：NetworkFirst，5 分钟，最多 50 条
- Web App Manifest + iOS `apple-mobile-web-app-capable` 独立窗口

### 国际化
- 前端：`vue-i18n`，zh-CN / en-US 双语，浏览器语言自动检测，localStorage 持久化，顶栏按钮即时切换（antd 文案跟随）

### 其他
- 设计令牌单一来源（`theme.ts` 同时驱动 antd ConfigProvider 主题与 CSS 变量，明暗模式自动跟随系统；`styles/tokens.css` 为无 JS 首帧兜底）
- 健康检查端点 `/actuator/health`（公开；dev：DB + 磁盘空间，禁用 Rabbit/Redis 指示器避免误报 DOWN；prod：含 RabbitMQ/Redis）+ Prometheus 指标端点 `/actuator/prometheus`（**Basic Auth**：`MONITORING_USER/MONITORING_PASSWORD`，P0-#4）
- Micrometer 自定义指标：`photo.upload.total`、`photo.upload.bytes`、`photo.processing.total`、`photo.processing.failures`、`@Timed("photo.processing.time")` 处理耗时
- Toast 通知、错误边界（组件级 ErrorBoundary + 全局 errorHandler 兜底页）
- 移动端响应式（底部导航、中央上传按钮、工具栏居中、hover 降级、地图适配）
- 安全响应头：CSP（frame-ancestors 'none'）/ HSTS / nosniff / frame-deny
- SpringDoc `/swagger-ui.html` 交互式 API 文档（开发环境启用，prod 关闭）
- 客户端 IP 解析：仅信任受信头（prod 默认 `Cf-Connecting-Ip`，适配 cloudflared 隧道；XFF 永不信任）

---

## 项目结构

```
photo-gallery/
├── backend/
│   ├── src/main/java/com/hape/photogallery/
│   │   ├── PhotoGalleryApplication.java        # @SpringBootApplication + @EnableCaching
│   │   ├── ApiResponse.java                    # 统一响应体 {code, message, data}
│   │   ├── controller/
│   │   │   ├── AuthController.java             # POST /api/v1/auth/unlock + 分享链接生成
│   │   │   ├── ShareController.java            # 分享落地面 + 查看 API
│   │   │   ├── PhotoController.java            # 照片 REST API（上传/编辑/搜索/重试/迁移端点）
│   │   │   ├── TagController.java              # 标签 CRUD
│   │   │   ├── CategoryController.java         # 分类 CRUD
│   │   │   ├── AlbumController.java            # 相册 CRUD
│   │   │   ├── TrashController.java            # 回收站 API
│   │   │   ├── BackupController.java           # 备份导出（POST /api/v1/backup/export，zip + 预生成缓存）
│   │   │   └── StatsController.java            # 统计面板（GET /api/v1/stats）
│   │   ├── service/
│   │   │   ├── PhotoService.java               # 核心业务逻辑（上传/搜索/软删除/批量/去重/定时清理与恢复）
│   │   │   ├── PhotoProcessor.java             # 图片处理管线（EXIF→旋转→水印→缩略图→WebP），纯 JDK 图像操作
│   │   │   ├── PhotoTransformService.java      # 图片变换（事务边界 + 失败备份补偿）
│   │   │   ├── FilePathResolver.java           # 文件路径解析/产物路径/物理删除
│   │   │   ├── MigrationService.java           # 数据迁移（缩略图/WebP 补转、EXIF 补提取，分页游标 + 幂等）
│   │   │   ├── AuthService.java                # 解锁校验/分享签发（Controller 瘦身）
│   │   │   ├── StatsService.java               # 统计聚合（4 查询 + 30s 缓存）
│   │   │   ├── BackupScheduler.java            # 备份预生成定时任务（默认每天 3:05）
│   │   │   ├── TagService.java                 # 标签服务
│   │   │   ├── CategoryService.java            # 分类服务
│   │   │   ├── AlbumService.java               # 相册服务
│   │   │   ├── AsyncImageProcessor.java        # dev 环境 @Async 图片处理发送者（委托 PhotoProcessor）
│   │   │   ├── ImageProcessingService.java     # 缩略图(多档)/WebP/水印/旋转/镜像/魔数校验
│   │   │   ├── ExifService.java                # metadata-extractor 集成
│   │   │   ├── StorageService.java             # 存储接口（可扩展不同后端）
│   │   │   ├── LocalStorageService.java        # 本地文件系统存储实现（路径穿越防护）
│   │   │   └── BackupService.java              # 备份导出（事务内收集元数据 + 流式 zip 打包 + 预生成缓存/指纹）
│   │   ├── messaging/
│   │   │   ├── ProcessingMessageSender.java    # 处理消息发送者接口
│   │   │   ├── ProcessingMessage.java          # 消息体 POJO（photoId/路径/水印）
│   │   │   ├── RabbitProcessingSender.java     # prod 环境 RabbitMQ 发送者实现
│   │   │   └── PhotoProcessingConsumer.java    # RabbitMQ 消费者（3 次重试 → DLQ）
│   │   ├── repository/
│   │   │   ├── PhotoRepository.java            # JPQL 分页 + 筛选 + FULLTEXT 搜索
│   │   │   ├── TagRepository.java
│   │   │   ├── CategoryRepository.java
│   │   │   ├── AlbumRepository.java
│   │   │   └── ExifDataRepository.java
│   │   ├── entity/
│   │   │   ├── Photo.java                      # 照片实体（软删除，LAZY 关联 + @BatchSize）
│   │   │   ├── ProcessingStatus.java           # 处理状态枚举（PROCESSING/DONE/FAILED）
│   │   │   ├── Tag.java                        # 标签实体
│   │   │   ├── Category.java                   # 分类实体
│   │   │   ├── Album.java                      # 相册实体（软删除）
│   │   │   └── ExifData.java                   # EXIF 元数据实体（OneToOne）
│   │   ├── dto/
│   │   │   ├── PhotoResponse.java              # 照片响应 DTO
│   │   │   ├── PhotoUpdateRequest.java         # 更新请求体（null=不修改 / 0=清除）
│   │   │   ├── TimelineItem.java               # 时间线项
│   │   │   ├── MapItem.java                    # 地图项（含 GCJ-02 坐标）
│   │   │   ├── ShareGenerateRequest.java       # 分享链接生成请求（permission 仅 view/download）
│   │   │   ├── TransformRequest.java           # 图片变换参数
│   │   │   ├── BackupExportRequest.java        # 备份导出筛选参数
│   │   │   ├── AlbumRequest.java               # 相册创建/更新请求
│   │   │   ├── AlbumResponse.java              # 相册响应 DTO（含 photoCount/mediaToken）
│   │   │   ├── BatchPhotoUpdateRequest.java    # 批量编辑请求（添加/移除 + categoryOp 三态）
│   │   │   ├── StatsResponse.java              # 统计面板响应
│   │   │   └── UploadParams.java               # 上传参数封装（record）
│   │   ├── config/
│   │   │   ├── AsyncConfig.java                # @EnableAsync + 图片处理线程池（DiscardPolicy）+ MDC 传播
│   │   │   ├── RedisConfig.java                # Redis 缓存配置（JSON 序列化 + PageImpl mixin）
│   │   │   ├── RabbitMQConfig.java             # Queue/Exchange/DLQ 拓扑 + MANUAL ack
│   │   │   ├── SecurityConfig.java             # SecurityFilterChain + CORS 白名单 + CSP
│   │   │   ├── JwtService.java                 # HS256 JWT 签发（admin/viewer）与验签（≥32 字节校验）
│   │   │   ├── MediaSignatureService.java      # 图片短时签名（HMAC 时间桶，JWT 不进 URL）
│   │   │   ├── ClientIpResolver.java           # 受信头 IP 解析（Cf-Connecting-Ip，XFF 永不信任）
│   │   │   ├── ProdSecurityValidator.java      # prod 启动强校验（Redis/Rabbit 密码非空）
│   │   │   ├── JwtAuthFilter.java              # OncePerRequestFilter + 图片签名优先 / JWT 白名单回落
│   │   │   ├── RateLimitFilter.java            # 认证端点限流（Caffeine，10 req/s/IP）
│   │   │   ├── NonceStore.java                 # 一次性 nonce（Caffeine 60s TTL）
│   │   │   ├── FailedAttemptStore.java         # IP 失败计数（5 次封 15 分钟）
│   │   │   ├── TraceIdFilter.java              # 全链路 traceId（MDC + 响应头）
│   │   │   └── CacheControlFilter.java         # 全局 Cache-Control 头
│   │   ├── exception/
│   │   │   ├── BusinessException.java          # 业务异常
│   │   │   ├── DuplicateException.java         # 重复文件异常（409）
│   │   │   ├── FileSizeExceededException.java  # 文件大小异常
│   │   │   ├── InvalidFileTypeException.java   # 文件格式异常
│   │   │   └── GlobalExceptionHandler.java     # @RestControllerAdvice
│   │   └── util/
│   │       └── CoordUtil.java                  # WGS-84 → GCJ-02 坐标转换
│   ├── src/main/resources/
│   │   ├── application.properties              # 公共配置（端口/上传目录/水印/Konami 序列，默认 dev profile）
│   │   ├── application-dev.yml                 # 开发环境（ddl-auto: update，禁用 Rabbit/Redis 健康指示器）
│   │   ├── application-prod.yml                # 生产环境（Redis/RabbitMQ/限流受信头，ddl-auto: validate）
│   │   ├── application-local.yml.example       # 本地敏感配置模板（密码/JWT 密钥，gitignored）
│   │   ├── logback-spring.xml                  # 控制台 + 按天滚动文件（30/90 天保留）
│   │   ├── db/migration/                       # Flyway 迁移脚本 V1–V9（含 file_hash 去重 + FULLTEXT/ngram 索引）
│   │   └── static/                             # 前端构建产物 (SPA，npm run build 自动复制，不入库)
│   ├── Dockerfile                              # JRE 17 Alpine + 文泉驿字体 + curl
│   └── pom.xml                                 # Maven 配置（JaCoCo ≥35% + SpotBugs 门禁）
│
├── frontend/
│   ├── vite.config.js                          # Vite + PWA(Workbox) + 手动分包（产物复制见 scripts/copy-dist.mjs）
│   ├── vitest.config.ts                        # Vitest（happy-dom + 覆盖率）
│   ├── playwright.config.ts                    # Playwright E2E
│   ├── tsconfig.json                           # TypeScript 严格模式
│   ├── index.html                              # 入口 + iOS PWA meta 标签
│   ├── public/
│   │   └── pwa-icon.svg                        # PWA 图标
│   ├── scripts/
│   │   ├── copy-dist.mjs                       # 构建产物复制（dist → backend static，npm run build 自动执行）
│   │   ├── verify.mjs                          # 客观设计验证（设计令牌/对比度/响应式，Playwright 驱动）
│   │   └── capture.mjs                         # 截图捕获（各页面/明暗主题，产物 frontend/.shots）
│   ├── e2e/                                    # Playwright E2E 用例（konami/核心流程/搜索回收站等 5 个 spec）
│   └── src/
│       ├── main.ts                             # 入口（Pinia + Router + i18n + 全局错误处理）
│       ├── App.vue                             # 根组件（错误边界 + RouterView + 语言切换）
│       ├── theme.ts                            # 设计令牌单一来源（antd 主题 + CSS 变量）
│       ├── api.ts                              # fetch 封装 + JWT 注入 + 短时签名拼接
│       ├── i18n.ts                             # vue-i18n 配置（浏览器语言检测 + localStorage）
│       ├── upload.ts                           # 客户端压缩 + XHR 进度上传
│       ├── router/index.ts                     # Vue Router（AppShell 子路由 + 404 兜底重定向）
│       ├── styles/
│       │   ├── tokens.css                      # 设计令牌静态兜底（运行时由 theme.ts 内联覆盖）
│       │   └── base.css                        # reset / 排版 / 焦点 / 无障碍 / 滚动条
│       ├── stores/                             # photo / ui / toast / data（Pinia）
│       ├── types/                              # TypeScript 类型定义
│       ├── locales/                            # zh-CN / en-US 语言文件
│       ├── utils/                              # token / format / error / escape / clipboard / logger / webp
│       ├── composables/                        # usePhotoActions / useViewerControls / useKeyboardShortcuts / useImageEditorCanvas
│       ├── layouts/
│       │   └── AppShell.vue                    # 主布局（Konami 门禁 / 顶栏 / 路由出口）
│       └── components/
│           ├── auth/                           # KonamiGate（红白机解锁屏）+ ArcadePanel
│           ├── gallery/                        # PhotosView + PhotoGrid（虚拟滚动）+ PhotoTile + GridSkeleton + SelectionBar
│           ├── viewer/                         # PhotoViewer + ViewerStage + ViewerBottom + ExifPanel
│           ├── upload/                         # UploadDrawer（拖拽/粘贴/压缩/去重）
│           ├── editor/                         # ImageEditor + EditorToolbar + PhotoEditDrawer + BatchEditDrawer
│           ├── albums/                         # AlbumsView + AlbumDetail + AlbumEditDrawer
│           ├── timeline/                       # TimelineView
│           ├── map/                            # MapView（Leaflet 聚合 + 高德瓦片）
│           ├── stats/                          # StatsView + useTrendChart（uPlot）
│           ├── trash/                          # TrashView
│           ├── share/                          # ShareViewer（公开落地面）
│           ├── topbar/                         # TopBar + ModeTabs + MobileTabBar + FilterPanel + FilterPanelContent + CornerMenu + HelpModal
│           └── common/                         # ErrorBoundary + ToastStack + ShareDialog + EmptyState
│
├── prometheus/
│   ├── prometheus.yml                          # Prometheus 采集配置（15s scrape：app:8080 + rabbitmq:15692）
│   └── grafana-provisioning/                   # Grafana provisioning（P2-#16）
│       ├── datasources/                        # Prometheus 数据源（uid 固定 prometheus-main）
│       ├── dashboards/                         # photo-gallery.json 运行面板（JVM/HTTP/上传/队列）
│       └── alerting/                           # 告警规则（App down / 5xx 速率，单轨制）
├── scripts/                                    # 构建/开发脚本（任意位置可运行）
│   ├── build-docker.sh / .ps1                  # Docker 一键构建 + 启动
│   ├── build-traditional.sh / .ps1             # 传统 JAR 一键构建（前端内嵌）
│   ├── dev-start.sh / .ps1                     # 一键启动前后端开发环境
│   └── k6/                                     # k6 压测脚本（smoke/upload/photo-list/share 4 场景）
├── .env.example                                # Docker Compose 环境变量模板
├── .github/workflows/ci.yml                    # CI：backend / frontend / docker / e2e 四并行 job
└── docker-compose.yml                          # App + MySQL + Redis + RabbitMQ + Prometheus + Grafana
```

---

## 本地快速启动

### 前置条件

Java 17+ / Maven 3.6+ / Node.js 18+ / MySQL 8.0+

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS photodb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置本地密钥

敏感配置（数据库密码、JWT 密钥）存放在 `application-local.yml`，该文件已在 `.gitignore` 中，不会提交：

```bash
cd backend/src/main/resources

# 复制模板（只需做一次）
cp application-local.yml.example application-local.yml

# 编辑 application-local.yml，填入你的本地 MySQL 密码
# 如果密码含特殊字符（@ ` ? { } 等），请用引号包裹，例如 DB_PASSWORD: "@my-p@ss"
```

模板默认内容：

```yaml
DB_PASSWORD: "your-password-here"
JWT_SECRET: dev-secret-do-not-use-in-production
```

`application-dev.yml` 通过 `spring.config.import: optional:classpath:application-local.yml` 自动加载该文件（文件不存在时跳过，回退到环境变量）。

> 环境变量仍可用于 CI/CD 等场景，优先级高于配置文件。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

后端运行在 `http://localhost:8080`，Flyway 首次启动自动建表迁移。dev profile 默认使用 Caffeine 本地缓存 + `@Async` 线程池处理图片，无需 Redis/RabbitMQ（如需本地联调，可在 `application-local.yml` 中取消注释 Redis / RabbitMQ 配置覆盖默认行为）。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。

### 5. 首次使用

1. 打开页面 → Konami 街机界面
2. 输入 **`↑ ↑ ↓ ↓ ← → ← → B A B A`**（键盘方向键 + 字母键，或点击虚拟按键）
3. 后端 Challenge-Response 验证通过后签发 24h admin JWT
4. 进入管理系统，开始上传照片

---

## 构建

### 一键构建

```bash
./scripts/build-traditional.sh     # 传统 JAR（前端内嵌，Nginx + JAR 部署用）
./scripts/build-docker.sh          # Docker 镜像 + docker compose 启动
```

构建脚本自动完成：安装依赖（仅缺 node_modules 时执行 `npm ci`）→ `npm run build`（vue-tsc 类型检查 + 构建 + 自动复制到 `backend/static`）→ `mvn clean package -DskipTests` →（docker 版）`docker compose up -d --build`。Windows 用户用同名的 `.ps1` 版本。

开发环境一键启动前后端（后端 :8080 + 前端 :5173，Ctrl+C 全部停止并清理 8080 端口残留进程）：

```bash
./scripts/dev-start.sh     # 或 Windows: ./scripts/dev-start.ps1
```

> 所有脚本自动向上查找项目根目录（以 `frontend/package.json` 为准），可在任意位置调用。

### 手动构建

```bash
# 前端（vue-tsc 类型检查 + 构建 + 自动复制到 backend/src/main/resources/static/）
cd frontend
npm ci && npm run build

# 后端
cd ../backend
mvn clean package -DskipTests
```

> 产物复制由 `frontend/scripts/copy-dist.mjs` 完成（整体清空再复制，避免残留带 hash 的死文件），无需手工 `cp`。

---

## 部署

### Docker Compose

#### 1. 创建 .env

```bash
cp .env.example .env   # 然后编辑填入密码
```

`.env.example` 中需填写的变量：

```bash
MYSQL_ROOT_PASSWORD=你的MySQL密码        # MySQL 容器 root 密码
MYSQL_DATABASE=photodb
DB_HOST=mysql
DB_USERNAME=root
DB_PASSWORD=${MYSQL_ROOT_PASSWORD}
JWT_SECRET=$(openssl rand -base64 32)    # 生产环境必须替换
REDIS_PASSWORD=你的Redis密码             # Redis 密码（prod 启动强校验非空）
RABBIT_USER=你的RabbitMQ用户名           # RabbitMQ 用户名（prod 强校验不能为默认 admin）
RABBIT_PASS=你的RabbitMQ密码             # RabbitMQ 密码（prod 启动强校验：Redis/Rabbit 密码非空）
MONITORING_USER=你的监控用户名           # /actuator/prometheus Basic Auth 用户名（prod 强校验非空，P0-#4）
MONITORING_PASSWORD=你的监控密码         # 同上，密码（prometheus.yml basic_auth 用 ${} 内插引用，需保持一致）
```

#### 2. 构建并启动

```bash
# 在项目根目录执行
docker compose up -d --build
```

其中包含 6 个服务：`app`（Spring Boot，prod profile）、`mysql`、`redis`、`rabbitmq`、`prometheus`、`grafana`（P2-#16，面板/告警 provisioning 自动加载）。Compose 内 `app` 固定运行 prod profile（Redis 缓存 + RabbitMQ 处理）；本地开发（非 Docker）只需 MySQL，dev profile 使用 Caffeine + `@Async` 线程池。

访问 `http://localhost:8080`（端口仅绑定 127.0.0.1，推荐通过 Nginx 或 cloudflared 反向代理对外暴露）。

#### 3. 容器资源配置

| 容器 | 内存限制 | 说明 |
|------|---------|------|
| App | 768M | JVM 堆 448M + Metaspace 128M（G1GC） |
| MySQL | 512M | 8.0，InnoDB buffer pool 128M |
| Redis | 256M | 7 Alpine，maxmemory 128M + allkeys-lru 淘汰 + AOF |
| RabbitMQ | 256M | 3.13 management-alpine（内置 Prometheus 插件，15692 仅容器网络内访问） |
| Prometheus | 128M | v3.2.0，15 天保留，每 15s scrape |
| Grafana | 256M | 13.1.0，仅绑定 127.0.0.1:3000 |

所有容器均配置了 Docker healthcheck（`restart: always` 保证容器异常退出时自动拉起）。内存上限合计约 1.9GB，2GB 内存服务器上建议同时关闭本地其他服务。

#### 4. 常用命令

```bash
docker compose ps              # 查看状态
docker compose logs -f app     # 查看日志
docker compose restart app     # 重启应用
docker compose down            # 停止
```

### Nginx 反向代理（HTTPS 推荐）

```nginx
server {
    listen 80;
    server_name 你的域名;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

```bash
nginx -t && systemctl reload nginx
certbot --nginx -d 你的域名   # 免费 SSL
```

> 客户端 IP 解析（限流/封禁）默认信任 `Cf-Connecting-Ip`（cloudflared 隧道）。改走 Nginx 后需在 `application-prod.yml` 中将 `security.trusted-proxy-header` 改为 `X-Real-IP`，否则取不到真实客户端 IP。

---

## PWA 安装

生产环境部署到 HTTPS 后，浏览器地址栏出现安装按钮：

| 平台 | 安装方式 |
|------|---------|
| Android Chrome | 地址栏「安装」横幅 |
| iOS Safari | 分享按钮 → 「添加到主屏幕」 |
| 桌面 Chrome/Edge | 地址栏右侧安装图标 |

图标替换：编辑 `frontend/public/pwa-icon.svg`，重新构建即可。

---

## 语言切换

浏览器语言自动检测（`navigator.language`）：

| 浏览器语言 | 界面语言 |
|-----------|---------|
| `zh-*` | 简体中文 |
| 其他 | English |

手动切换：`localStorage.setItem('locale', 'en-US')` 或 `'zh-CN'`，刷新页面。

---

## 鉴权

```
┌──────────────────────────────────────────────────────────┐
│  Konami 解锁 (Challenge-Response)                         │
│    → 前端 Konami 按键只记录不验证（序列不在前端代码中）      │
│    → GET /api/v1/auth/challenge → 获取一次性 nonce (60s)  │
│    → POST /api/v1/auth/unlock {nonce, keys}               │
│    → 后端比对序列 + nonce 验证（一次性消费）                 │
│    → 认证端点限流（challenge+unlock，10次/s/IP）+ 失败计数   │
│    → 签发 24h admin JWT (role: admin)                     │
│                                                          │
│  分享链接                                                  │
│    → POST /api/v1/share/generate {photoIds, permission}  │
│    → 签发 7 天 viewer JWT (role: viewer + photos 白名单)    │
│    → /share/{token} → ShareViewer 落地面                 │
│    → SecurityConfig 限制 viewer 仅访问指定端点              │
│    → JwtAuthFilter 对每个图片请求校验 photoId 在白名单内     │
└──────────────────────────────────────────────────────────┘
```

| 入口 | JWT Claim | 权限 |
|------|-----------|------|
| Konami 解锁 | `role: admin` | 上传、编辑、删除、生成分享链接、管理分类/标签/相册 |
| 分享链接 | `role: viewer`, `photos: [...]` | 仅查看 JWT 中编码的指定照片 |

| 请求 | 权限 |
|------|------|
| `GET /api/v1/share/view` | `ROLE_admin` 或 `ROLE_viewer`（仅返回 JWT 中 `photoIds` 白名单内的照片） |
| `GET /api/v1/photos/{id}/thumbnail\|webp\|file` | 图片短时签名优先（无效/photoId 不符 403），无签名回落 `ROLE_admin` 或 `ROLE_viewer`（viewer 需 ID 在 JWT 白名单内；`/file` 另强制 `permission=download`） |
| `GET /api/v1/**`（其他） | `ROLE_admin`（viewer 无权访问列表、时间线、地图等） |
| `POST /api/v1/backup/export` | `ROLE_admin`（流式下载 zip 备份，禁止缓存） |
| `POST/PUT/DELETE /api/v1/**`（其他） | `ROLE_admin` |
| `GET /api/v1/auth/challenge`、`POST /api/v1/auth/unlock` | 公开（认证端点 10 次/s/IP 限流；unlock 另有 5 次失败封禁 15 分钟） |
| `GET /share/**` | 公开（转发到 SPA 落地面） |
| `/actuator/health` | 公开 |
| `/actuator/prometheus` | Basic Auth（`MONITORING_USER/MONITORING_PASSWORD`，MONITOR 角色） |
| `/swagger-ui/**`、`/v3/api-docs/**` | 公开（仅开发环境，prod 已禁用 springdoc） |
| 静态资源 | 公开 |

---

## 健康检查与监控

```
GET /actuator/health
→ {"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}   # dev；prod 另含 redis/rabbitmq

GET /actuator/prometheus   # 需 Basic Auth（MONITORING_USER/MONITORING_PASSWORD）
→ # HELP photo_upload_total ...
→ # HELP photo_processing_time_seconds ...
```

Docker 中 `app` 容器 healthcheck 每 15 秒执行 `curl /actuator/health`（3 次重试、45s 启动宽限），异常时标记 unhealthy；`restart: always` 保证进程崩溃退出后自动拉起。

**监控可视化（P2-#16）**：Prometheus 每 15 秒 scrape `/actuator/prometheus`（`prometheus/prometheus.yml`，Basic Auth）与 `rabbitmq:15692`（RabbitMQ 内置 Prometheus 插件，3.8+ 默认启用，端口不映射宿主机）。`docker compose up -d grafana` 后访问 `http://127.0.0.1:3000`（管理员密码 = `.env` 的 `GF_SECURITY_ADMIN_PASSWORD`，匿名登录已关闭）——数据源、`Photo Gallery` 面板（JVM 内存 / HTTP 请求量 / 上传总量 / RabbitMQ 队列深度）与告警规则（App down、5xx 速率）均由 `prometheus/grafana-provisioning/` 自动加载，无需手动配置。告警通知渠道未接（单机自用，规则已就位，按需在 Grafana UI 挂 webhook）；内存紧张时可 `docker compose stop grafana` 随时停用。

---

## 备份与恢复

### 应用内备份导出（推荐）

解锁后点击标题栏右侧 **⤓** 按钮，一键下载 `photo-gallery-backup-YYYY-MM-DD.zip`，包含全部原始照片和数据库元数据：

```
photo-gallery-backup-2026-08-02.zip
├── database/                 # 数据库元数据（JSON，可离线查看/恢复）
│   ├── metadata.json         #   导出版本、时间、照片数、筛选参数
│   ├── photos.json           #   照片 + 分类/标签/相册关联
│   ├── exif.json             #   EXIF 拍摄信息
│   ├── tags.json / categories.json / albums.json
└── photos/                   # 原始照片文件，保持服务器目录结构
    └── 2024/01/uuid_xxx.jpg
```

API：`POST /api/v1/backup/export`（仅 admin），可选 JSON 参数按需筛选：

```bash
curl -X POST http://localhost:8080/api/v1/backup/export \
  -H "Authorization: Bearer $JWT" -H "Content-Type: application/json" \
  -d '{"albumId":3,"categoryId":5,"dateFrom":"2026-01-01","dateTo":"2026-07-31"}'
```

- `albumId=0` 表示「未分配任何相册」的照片；字段省略即不限
- 流式打包不占服务器内存；`Cache-Control: no-store` 禁止缓存
- 每日 3:05 定时预生成全量 zip 缓存（`backup.auto-cron` 可配），导出时比对数据指纹（照片增删/标签/相册/分类计数），未变化直接下载缓存免实时打包

### 手动备份脚本

> 应用内**不集成** mysqldump 定时任务（应用级 BackupScheduler 已覆盖每日预生成 + 指纹缓存），
> 此段为手工示例仅供参考。数据库层面建议定期 `mysqldump`（应用导出不含数据库原始表结构）：

```bash
#!/bin/bash
# 手动备份脚本（Docker Compose 部署，MySQL 容器名为 photodb）
BACKUP_DIR="/tmp/photo-backup-$(date +%Y%m%d)"
mkdir -p "$BACKUP_DIR"
docker exec photodb mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" photodb \
  | gzip > "$BACKUP_DIR/database.sql.gz"
cp -r /data/photo-uploads "$BACKUP_DIR/photos"
tar -czf "photo-backup-$(date +%Y%m%d).tar.gz" -C "$BACKUP_DIR" .
rm -rf "$BACKUP_DIR"
echo "备份完成: photo-backup-$(date +%Y%m%d).tar.gz"
```

恢复时：解包后先还原照片文件到 `photo.upload-dir`（prod 容器内为 `/data/photo-uploads`），再导入数据库 dump：

```bash
docker exec -i photodb mysql -u root -p"$MYSQL_ROOT_PASSWORD" photodb < database.sql
```

---

## 测试

### 后端（JUnit + Spring Boot Test，H2 内存库）

```bash
cd backend
mvn test                       # 全部单测
mvn test -Dtest=PhotoServiceTest   # 单类测试
mvn spotbugs:check             # SpotBugs 静态分析（Max effort / Low threshold）
```

- `maven-surefire-plugin` 自动注入测试用 `JWT_SECRET` 环境变量
- JaCoCo 门禁：指令覆盖率最低 35%（`backend/target/site/jacoco/index.html` 查看报告）

### 前端（Vitest）

```bash
cd frontend
npm run type-check             # vue-tsc --noEmit
npm test                       # Vitest 单元测试（happy-dom + @pinia/testing）
npm run lint                   # ESLint
npm run format:check           # Prettier 检查
```

### E2E（Playwright，需后端运行）

```bash
cd frontend
npm run test:e2e               # 5 个 spec：解锁门禁/核心流程/相册/排序/搜索筛选回收站
```

`frontend/scripts/verify.mjs` 另提供客观设计验证（设计令牌/对比度 AA/响应式断点，无视觉通道时的回归替代）。

### CI（`.github/workflows/ci.yml`）

push / PR 到 main 时四个并行 job：

1. **backend** — `mvn test` + `spotbugs:check` + `mvn package`
2. **frontend** — `npm install` + `npm test` + `npm run build`
3. **docker** — 构建前端（自动复制 static）→ 打包 JAR → 构建 Docker 镜像
4. **e2e** — 构建 JAR → H2 内存库启动后端 → Playwright 全量 E2E

---

## 工程规范

- **Git hooks（Husky）**：`pre-commit` 运行 lint-staged（Prettier + ESLint 修复暂存的 `.ts`/`.vue`/`.css`/`.md`）；`commit-msg` 运行 commitlint，提交信息须符合 Conventional Commits（`feat`/`fix`/`docs`/`style`/`refactor`/`perf`/`test`/`chore`/`ci`/`build`）
- **脚本约定**：所有构建/开发脚本位于 `scripts/`（bash + PowerShell 双版本），自动向上查找项目根目录，任意位置可运行；前端构建产物复制由 `frontend/scripts/copy-dist.mjs` 完成
- **构建产物不入库**：`backend/src/main/resources/static/`、`frontend/dist/`、`backend/target/`、`node_modules/` 均被 `.gitignore` 忽略，CI 与本地均从源码产出
