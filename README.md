# Photo Gallery · 照片管理器

> 暗黑玻璃态全栈照片管理应用 — 朋友间的私人图库

前后端分离，Konami 密码门禁联动 JWT 双角色鉴权，支持 EXIF 时间线/地图浏览、相册分组、图片编辑、水印、WebP 转换、限时分享链接、PWA 离线安装和一键 Docker 部署。

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 17 |
| 后端框架 | Spring Boot | 3.3.0 |
| 安全 | Spring Security + JWT (jjwt) | 0.12.6 |
| ORM | Spring Data JPA + Hibernate | — |
| 数据库 | MySQL + Flyway 迁移 | 8.0+ |
| 缓存 | Spring Cache + Caffeine | — |
| EXIF | metadata-extractor | 2.19.0 |
| 图片编码 | webp-imageio | 0.1.6 |
| 健康检查 | Spring Boot Actuator | — |
| API 文档 | SpringDoc OpenAPI | 2.5.0 |
| 前端框架 | Vue 3 (Composition API) + Pinia | 3.5 |
| 语言 | TypeScript | 5.x |
| 构建 | Vite | 5.4 |
| 虚拟滚动 | @tanstack/vue-virtual | — |
| 地图 | Leaflet + markercluster | 1.9.4 |
| 动画 | GSAP + Lottie | — |
| 国际化 | vue-i18n（前端）+ Spring MessageSource（后端） | — |
| PWA | vite-plugin-pwa + Workbox | — |
| 部署 | Docker Compose / Nginx 反向代理 | — |

---

## 功能

### 照片管理
- **上传** — 拖拽/粘贴/批量（单次最多 50 张），魔数校验（JPEG/PNG/GIF/BMP/WebP），客户端 Canvas 压缩大图（最大 1920px，JPEG 质量 0.85），XHR 实时进度条
- **浏览** — 虚拟滚动（DOM 节点数恒定，万张照片不卡）、3D 倾斜卡片、骨架屏加载
- **编辑** — 名称/描述修改、分类/标签/相册分配
- **批量操作** — 多选、全选、批量删除、批量生成分享链接
- **搜索** — 即时模糊搜索名称和描述
- **排序** — 时间/名称/大小，正序倒序自由切换

### 图片处理
- **异步处理** — 上传后立即返回，EXIF 提取、水印、缩略图、WebP 由线程池后台完成。照片卡片显示"处理中"遮罩，失败可一键重试
- **响应式缩略图** — 上传自动生成 200 px + 400 px 双档，前端 `srcset` + `sizes` 按视口和 DPR 自动选择
- **编辑器** — Canvas 全分辨率旋转（任意角度）/镜像（水平/垂直）/裁剪
- **水印** — 右下角半透明白色文字，字号自适应图片宽度，字体/大小/透明度可配置
- **WebP** — 上传自动生成 Lossy WebP 副本，运行时检测浏览器支持
- **EXIF 自动旋转** — 根据 Orientation 标签自动纠正方向

### 分类体系
- **分类** — 多对一互斥归类
- **标签** — 多对多交叉标记，自定义颜色
- **相册** — 多对多分组，封面卡片网格，时间/名称排序，"未分配"自动汇总

### EXIF 与浏览
- **EXIF 提取** — 拍摄时间、相机型号、镜头型号、焦距、光圈、快门、ISO、GPS
- **时间线** — 按年月分组，正序/倒序
- **地图** — Leaflet 聚合标注，WGS-84 → GCJ-02 坐标转换，高德卫星底图 + 道路标注叠加，移动端自适应高度 + ResizeObserver

### 安全
- **Konami 门禁** — 密码序列解锁（↑↑↓↓←→←→ B A B A），键盘 + 触摸双模式，前端验证通过后调后端签发 admin JWT
- **JWT 双角色** — admin（24h，管理）/ viewer（7 天，分享查看）
- **限时分享链接** — 选中照片生成分享链接，朋友无需密码即可查看
- **IP 限流** — `/api/v1/auth/unlock` 每 IP 每秒最多 10 次（Caffeine 固定窗口计数）
- **缓存一致性** — 所有写操作（照片/相册/标签/分类）自动驱逐列表缓存
- **软删除 + 回收站** — 删除标记 `deleted_at`（Hibernate @SQLRestriction 全局过滤），5 秒 Toast 撤销 + 回收站可恢复/彻底删除，每天凌晨 3 点（Asia/Shanghai）自动清理 30 天前记录
- **前端错误边界** — 组件异常自动捕获，显示降级页面（重试 / 刷新 / 复制错误信息），切换路由自动恢复

### PWA（渐进式 Web 应用）
- 可安装到桌面/主屏幕（Android Chrome + iOS Safari + 桌面 Edge/Chrome）
- Service Worker 离线缓存：静态资源预缓存 + 缩略图 CacheFirst（7 天）+ API NetworkFirst（5 分钟）
- Web App Manifest + iOS `apple-mobile-web-app-capable` 独立窗口

### 国际化
- 前端：`vue-i18n`，zh-CN / en-US 双语，浏览器语言自动检测，localStorage 可覆盖
- 后端：Spring MessageSource，`messages.properties` 双语错误消息

### 其他
- 前端组件样式分量管理：组件独有样式使用 Vue `<style scoped>`，跨组件共享样式（弹窗骨架/按钮/移动端适配）保留全局 CSS
- 健康检查端点 `/actuator/health`（DB + 磁盘空间）
- Toast 通知、自定义确认弹窗、Lottie 加载动画
- 回到顶部、光标拖尾、波纹效果
- 移动端响应式（侧边抽屉、工具栏居中、hover 降级、地图适配）
- SpringDoc `/swagger-ui.html` 交互式 API 文档（开发环境启用）

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
│   │   │   ├── PhotoController.java            # 照片 REST API
│   │   │   ├── TagController.java              # 标签 CRUD
│   │   │   ├── CategoryController.java         # 分类 CRUD
│   │   │   ├── AlbumController.java            # 相册 CRUD
│   │   │   ├── TrashController.java            # 回收站 API
│   │   │   └── HelloController.java            # 根端点
│   │   ├── service/
│   │   │   ├── PhotoService.java               # 核心业务逻辑（异步图片处理）
│   │   │   ├── TagService.java                 # 标签服务
│   │   │   ├── CategoryService.java            # 分类服务
│   │   │   ├── AlbumService.java               # 相册服务
│   │   │   ├── AsyncImageProcessor.java        # @Async 异步图片处理（EXIF/水印/缩略图/WebP）
│   │   │   ├── ImageProcessingService.java     # 缩略图(多档)/WebP/水印/旋转/镜像
│   │   │   ├── ExifService.java                # metadata-extractor 集成
│   │   │   ├── StorageService.java             # 存储接口（可扩展不同后端）
│   │   │   └── LocalStorageService.java        # 本地文件系统存储实现
│   │   ├── repository/
│   │   │   ├── PhotoRepository.java            # JPQL 分页 + 筛选 + 搜索
│   │   │   ├── TagRepository.java
│   │   │   ├── CategoryRepository.java
│   │   │   ├── AlbumRepository.java
│   │   │   └── ExifDataRepository.java
│   │   ├── entity/
│   │   │   ├── Photo.java                      # 照片实体（软删除）
│   │   │   ├── Tag.java                        # 标签实体
│   │   │   ├── Category.java                   # 分类实体
│   │   │   ├── Album.java                      # 相册实体（软删除）
│   │   │   └── ExifData.java                   # EXIF 元数据实体（OneToOne）
│   │   ├── dto/
│   │   │   ├── PhotoResponse.java              # 照片响应 DTO
│   │   │   ├── PhotoUpdateRequest.java         # 更新请求体
│   │   │   ├── TimelineItem.java               # 时间线项
│   │   │   ├── MapItem.java                    # 地图项（含 GCJ-02 坐标）
│   │   │   ├── ShareGenerateRequest.java       # 分享链接生成请求
│   │   │   ├── TransformRequest.java           # 图片变换参数
│   │   │   └── AlbumRequest.java               # 相册创建/更新请求
│   │   ├── config/
│   │   │   ├── AsyncConfig.java                # @EnableAsync + 图片处理线程池
│   │   │   ├── SecurityConfig.java             # SecurityFilterChain + CORS
│   │   │   ├── JwtService.java                 # HS256 JWT 签发与验签
│   │   │   ├── JwtAuthFilter.java              # OncePerRequestFilter
│   │   │   ├── RateLimitFilter.java            # IP 固定窗口限流（Caffeine）
│   │   │   └── CacheControlFilter.java         # 全局 Cache-Control 头
│   │   ├── exception/
│   │   │   ├── BusinessException.java          # 业务异常
│   │   │   ├── FileSizeExceededException.java  # 文件大小异常
│   │   │   ├── InvalidFileTypeException.java   # 文件格式异常
│   │   │   └── GlobalExceptionHandler.java     # @RestControllerAdvice
│   │   └── util/
│   │       └── CoordUtil.java                  # WGS-84 → GCJ-02 坐标转换
│   ├── src/main/resources/
│   │   ├── application.properties              # 公共配置 + Caffeine + i18n + 水印
│   │   ├── application-dev.yml                 # 开发环境 (ddl-auto: update)
│   │   ├── application-prod.yml                # 生产环境 (ddl-auto: validate)
│   │   ├── application-local.yml.example       # 本地敏感配置模板（密码/JWT 密钥）
│   │   ├── logback-spring.xml                  # 控制台 + 按天滚动文件（30/90 天保留）
│   │   ├── i18n/
│   │   │   ├── messages.properties             # 中文错误消息
│   │   │   └── messages_en_US.properties       # 英文错误消息
│   │   ├── db/migration/                       # Flyway 迁移脚本 V1–V7
│   │   └── static/                             # 前端构建产物 (SPA)
│   ├── Dockerfile                              # JRE 17 Alpine + 文泉驿字体 + curl
│   └── pom.xml                                 # Maven 配置
│
├── frontend/
│   ├── vite.config.js                          # Vite + PWA + 手动分包
│   ├── tsconfig.json                           # TypeScript 严格模式
│   ├── index.html                              # 入口 + iOS PWA meta 标签
│   ├── public/
│   │   └── pwa-icon.svg                        # PWA 图标
│   └── src/
│       ├── main.ts                             # 入口（Pinia + Router + i18n + 全局错误处理）
│       ├── App.vue                             # 根组件（错误边界 + RouterView）
│       ├── api.ts                              # fetch 封装 + JWT 注入
│       ├── i18n.ts                             # vue-i18n 配置（浏览器语言检测）
│       ├── upload.ts                           # 客户端压缩 + XHR 进度上传
│       ├── webp.ts                             # WebP 检测 + 响应式缩略图 srcset
│       ├── style.css                           # 全局样式（CSS 变量 + 跨组件共享样式）
│       ├── useConfirm.ts                       # 确认弹窗 composable
│       ├── router/
│       │   └── index.ts                        # Vue Router（SPA + MainLayout 子路由）
│       ├── stores/
│       │   ├── photo.ts                        # 照片数据 + 分页 + 排序 + 搜索
│       │   ├── ui.ts                           # JWT + 解锁状态 + 弹窗状态
│       │   ├── toast.ts                        # Toast 通知队列
│       │   └── data.ts                         # 标签/分类/相册缓存
│       ├── types/                              # TypeScript 类型定义
│       ├── utils/
│       │   ├── format.ts                       # 格式化工具函数
│       │   └── token.ts                        # JWT 解析工具
│       ├── composables/
│       │   ├── useAppEffects.ts                # 背景光球 + 光标拖尾 + 入场动画
│       │   └── usePhotoActions.ts              # 照片操作（删除/批量/分享/复制）
│       ├── layouts/
│       │   └── MainLayout.vue                  # 主布局（Konami 门禁 / 侧边栏 / 事件）
│       ├── pages/
│       │   ├── GalleryPage.vue                 # 网格主页面（虚拟滚动 + 骨架屏）
│       │   ├── AlbumsPage.vue                  # 相册页面
│       │   ├── TimelinePage.vue                # 时间线页面
│       │   ├── MapPage.vue                     # 地图页面
│       │   └── TrashPage.vue                   # 回收站页面
│       └── components/
│           ├── KonamiGate.vue                  # Konami 密码门禁（键盘 + 触摸）
│           ├── AppHeader.vue                   # 渐变标题
│           ├── UploadCard.vue                  # 上传区域（进度条/压缩/拖拽/粘贴）
│           ├── PhotoCard.vue                   # 3D 倾斜卡片 + srcset
│           ├── ViewModal.vue                   # 大图查看（WebP 优先）
│           ├── EditModal.vue                   # 编辑信息 + 分配分类/标签/相册
│           ├── FilterSidebar.vue               # 分类/标签筛选侧边栏
│           ├── AlbumView.vue                   # 相册网格 + 详情
│           ├── AlbumEditModal.vue              # 相册编辑 + 照片选择器
│           ├── TimelineView.vue                # EXIF 时间线
│           ├── MapView.vue                     # 地图聚合标注（ResizeObserver 自适应）
│           ├── ImageEditor.vue                 # Canvas 图片编辑器
│           ├── ShareModal.vue                  # 分享弹窗
│           ├── ShareViewer.vue                 # 分享落地面
│           ├── SortSwitch.vue                  # 排序切换器
│           ├── ViewSwitcher.vue                # 视图切换器
│           ├── ToastProvider.vue               # Toast 容器
│           ├── ConfirmDialog.vue               # 确认弹窗
│           ├── LottieLoader.vue                # Lottie 动画
│           └── ErrorFallback.vue               # 错误降级页面
│
├── .env.example                                # Docker Compose 环境变量模板
├── docker-compose.yml                          # MySQL + App + 健康检查 + 内存限制
├── build-docker.ps1 / build-docker.sh          # Docker 一键构建
└── build-traditional.ps1 / build-traditional.sh  # 传统 JAR 一键构建
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

借鉴 `.env` / `.env.example` 模式，敏感配置（数据库密码、JWT 密钥）存放在 `application-local.yml`，该文件已在 `.gitignore` 中，不会提交：

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

后端运行在 `http://localhost:8080`，Flyway 首次启动自动建表。

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
3. 前端验证通过后自动调后端签发 24h admin JWT
4. 进入管理系统，开始上传照片

---

## 构建

### 一键构建

```bash
./build-traditional.sh     # 传统 JAR
./build-docker.sh          # Docker 镜像
```

构建脚本自动完成：`npm ci` → 前端构建 → 复制到 `backend/static` → Maven 打包 → Docker 启动。

### 手动构建

```bash
# 前端
cd frontend
npm ci && npm run build

# 复制到后端
rm -rf ../backend/src/main/resources/static/*
cp -r dist/* ../backend/src/main/resources/static/

# 后端
cd ../backend
mvn clean package -DskipTests
```

---

## 部署

### Docker Compose

#### 1. 创建 .env

```bash
MYSQL_ROOT_PASSWORD=你的MySQL密码
MYSQL_DATABASE=photodb
DB_HOST=mysql
DB_USERNAME=root
DB_PASSWORD=${MYSQL_ROOT_PASSWORD}
JWT_SECRET=$(openssl rand -base64 32)
```

#### 2. 构建并启动

```bash
# 在项目根目录执行
docker compose up -d --build
```

访问 `http://localhost:8080`（端口仅绑定 127.0.0.1，推荐通过 Nginx 或 cloudflared 反向代理对外暴露）。

#### 3. 容器资源配置

应用容器限制 768M 内存（JVM 堆 448M + Metaspace 128M），MySQL 容器限制 512M（InnoDB buffer pool 128M）。两者均配置了 Docker healthcheck，异常时自动重启。适用于 2GB 内存服务器。

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

---

## PWA 安装

生产环境部署到 HTTPS 后，浏览器地址栏出现安装按钮：

| 平台 | 安装方式 |
|------|---------|
| Android Chrome | 地址栏"安装"横幅 |
| iOS Safari | 分享按钮 → "添加到主屏幕" |
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
┌─────────────────────────────────────────────────────┐
│  Konami 解锁 (前端交互)                               │
│    → 输入 ↑↑↓↓←→←→ B A B A 密码序列（前端验证）       │
│    → POST /api/v1/auth/unlock                       │
│    → IP 固定窗口限流（每 IP 每秒 ≤ 10 次）            │
│    → 签发 24h admin JWT (role: admin)               │
│                                                     │
│  分享链接                                             │
│    → POST /api/v1/share/generate {photoIds}         │
│    → 签发 7 天 viewer JWT (role: viewer + photos)   │
│    → /share/{token} → ShareViewer 落地面            │
└─────────────────────────────────────────────────────┘
```

| 入口 | JWT Claim | 权限 |
|------|-----------|------|
| Konami 解锁 | `role: admin` | 上传、编辑、删除、生成分享链接、管理分类/标签/相册 |
| 分享链接 | `role: viewer`, `photos: [...]` | 仅查看 JWT 中编码的指定照片 |

| 请求 | 权限 |
|------|------|
| `GET /api/v1/**` | `ROLE_admin` 或 `ROLE_viewer` |
| `POST/PUT/DELETE /api/v1/**` | `ROLE_admin` |
| `GET /api/v1/trash/**` | `ROLE_admin` 或 `ROLE_viewer`（仅浏览） |
| `POST/DELETE /api/v1/trash/**` | `ROLE_admin`（恢复/彻底删除） |
| `POST /api/v1/auth/unlock` | 公开（含限流） |
| `GET /share/**` | 公开 |
| `/actuator/health` | 公开 |
| `/swagger-ui/**` | 公开（仅开发环境） |
| 静态资源 | 公开 |

---

## 健康检查

```
GET /actuator/health
→ {"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}
```

Docker 容器通过 `curl http://localhost:8080/actuator/health` 每 15 秒探测一次，连续 3 次失败自动重启容器。
