# Photo Gallery · 照片管理器

> 暗黑玻璃态全栈照片管理应用 — 朋友间的私人图库

前后端分离，Konami 密码门禁联动 JWT 双角色鉴权，支持 EXIF 时间线/地图浏览、相册分组、图片编辑、水印、WebP 转换、限时分享链接和一键 Docker 部署。

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 运行时 | Java | 17 |
| 后端框架 | Spring Boot | 3.3.0 |
| 安全 | Spring Security + JWT (jjwt) + BCrypt | 0.12.6 |
| ORM | Spring Data JPA + Hibernate | — |
| 数据库 | MySQL + Flyway 迁移 | 8.0+ |
| 缓存 | Spring Cache + Caffeine | — |
| EXIF | metadata-extractor | 2.19.0 |
| 图片编码 | webp-imageio | 0.1.6 |
| API 文档 | SpringDoc OpenAPI | 2.5.0 |
| 前端框架 | Vue 3 (Composition API) + Pinia | 3.5 |
| 构建 | Vite | 5.4 |
| 地图 | Leaflet + markercluster | 1.9.4 |
| 动画 | GSAP + Lottie | — |
| 部署 | Docker Compose / Nginx 反向代理 | — |

---

## 功能

### 照片管理
- **上传** — 拖拽/粘贴/批量，魔数校验（JPEG/PNG/GIF/BMP/WebP），应用层 10 MB 校验
- **浏览** — 无限滚动分页、3D 倾斜卡片、GSAP 入场动画、骨架屏加载
- **编辑** — 名称/描述修改、分类/标签/相册分配
- **批量操作** — 多选、全选、批量删除、批量上传、批量生成分享链接
- **搜索** — 即时模糊搜索名称和描述，大小写不敏感
- **排序** — 时间/名称/大小，正序倒序自由切换

### 图片处理
- **缩略图** — 上传自动生成 400 px JPEG，浏览器缓存 7 天
- **编辑器** — Canvas 全分辨率旋转/镜像/裁剪，上传前可编辑，已上传可服务端保存
- **水印** — 右下角半透明白色文字，字号自适应图片宽度
- **WebP** — 上传自动生成 80% Lossy WebP 副本，省 30–50% 存储
- **EXIF 自动旋转** — 根据 Orientation 标签自动纠正方向

### 分类体系
- **分类** — 多对一互斥归类
- **标签** — 多对多交叉标记，自定义颜色
- **相册** — 多对多分组，封面卡片网格，时间/名称排序，"未分配"自动汇总

### EXIF 与浏览
- **EXIF 提取** — 拍摄时间、相机型号、焦距、光圈、快门、ISO、GPS
- **时间线** — 按年月分组，正序/倒序
- **地图** — Leaflet 聚合标注，WGS-84 → GCJ-02 坐标转换，高德卫星底图

### 安全与分享
- **Konami 门禁** — 密码序列解锁（↑↑↓↓←→←→ B A B A），键盘 + 触摸双模式
- **JWT 双角色** — admin（管理） / viewer（分享查看）
- **限时分享链接** — 选中照片生成 7 天链接，朋友无需密码即可查看
- **分享落地面** — 纯查看模式，无管理按钮

### 其他
- Toast 通知、自定义确认弹窗、Lottie 加载动画
- 排序方式切换、回到顶部、光标拖尾、波纹效果
- 移动端响应式（侧边抽屉、弹性网格、触摸优化）
- SpringDoc `/swagger-ui.html` 交互式 API 文档

---

## 项目结构

```
photo-gallery/
├── backend/
│   ├── src/main/java/com/hape/photogallery/
│   │   ├── PhotoGalleryApplication.java        # 应用入口 (@EnableCaching)
│   │   ├── ApiResponse.java                    # 统一响应体 {code, message, data}
│   │   ├── SecurityConfig.java                 # SecurityFilterChain + CORS
│   │   ├── JwtUtil.java                        # HS256 JWT 签发与验签
│   │   ├── JwtAuthFilter.java                  # OncePerRequestFilter
│   │   ├── AuthController.java                 # POST /api/auth/unlock
│   │   ├── ShareController.java                # 分享链接 + 落地面
│   │   ├── Photo.java / Tag.java / Category.java / Album.java
│   │   ├── ExifData.java                       # EXIF 元数据实体
│   │   ├── PhotoController.java                # 照片 + 标签 + 分类 + 相册 API
│   │   ├── PhotoService.java                   # 业务逻辑 + 缩略图 + 水印 + WebP
│   │   ├── PhotoRepository.java                # JPQL 分页 + 筛选 + 搜索
│   │   ├── ExifService.java                    # metadata-extractor 集成
│   │   ├── ExifDataRepository.java
│   │   ├── CoordUtil.java                      # WGS-84 → GCJ-02 坐标转换
│   │   ├── CacheControlFilter.java             # 全局 Cache-Control 头
│   │   ├── GlobalExceptionHandler.java         # @RestControllerAdvice
│   │   └── *Exception.java                     # 自定义异常
│   ├── src/main/resources/
│   │   ├── application.properties              # 公共配置 + Caffeine
│   │   ├── application-dev.yml                 # 开发环境
│   │   ├── application-prod.yml                # 生产环境
│   │   ├── logback-spring.xml                  # 控制台 + 按天滚动 + 错误分离
│   │   ├── db/migration/                       # Flyway V1–V4
│   │   └── static/                             # 前端构建产物 (SPA)
│   └── Dockerfile                              # JRE 17 Alpine
│
├── frontend/
│   ├── .env                                    # 前端环境变量（本地开发用，不提交）
│   ├── .env.example                            # 前端环境变量模板
│   └── src/
│       ├── main.js                             # 入口
│       ├── App.vue                             # 根组件 (路由分发)
│       ├── api.js                              # fetch 封装 + JWT 注入
│       ├── style.css                           # 全局样式 (暗色毛玻璃)
│       ├── store.js                            # 标签/分类/相册数据
│       ├── useConfirm.js                       # 确认弹窗 composable
│       ├── stores/
│       │   ├── photo.js                        # 照片数据 + 分页 + 排序 + 搜索
│       │   ├── toast.js                        # 通知队列
│       │   └── ui.js                           # JWT + 解锁状态 + 弹窗状态
│       └── components/
│           ├── KonamiGate.vue                  # Konami 密码门禁
│           ├── AppHeader.vue                   # 渐变标题
│           ├── UploadCard.vue                  # 上传区域 (拖拽/粘贴/编辑)
│           ├── PhotoGallery.vue                # 视图切换 + 搜索 + 排序
│           ├── PhotoCard.vue                   # 3D 倾斜卡片
│           ├── ViewModal.vue                   # 大图查看
│           ├── EditModal.vue                   # 编辑信息 + 分配相册
│           ├── FilterSidebar.vue               # 分类/标签筛选
│           ├── AlbumView.vue                   # 相册网格 + 详情
│           ├── AlbumEditModal.vue              # 相册编辑 + 照片选择器
│           ├── TimelineView.vue                # EXIF 时间线
│           ├── MapView.vue                     # 地图聚合标注
│           ├── ImageEditor.vue                 # Canvas 图片编辑器
│           ├── ShareViewer.vue                 # 分享落地面
│           ├── ToastProvider.vue               # Toast 容器
│           ├── ConfirmDialog.vue               # 确认弹窗
│           └── LottieLoader.vue                # Lottie 动画
│
├── .env                                        # Docker Compose 环境变量（不提交）
├── .env.example                                # Docker Compose 环境变量模板
├── docker-compose.yml                          # MySQL + App + 数据卷
├── build-docker.ps1 / build-docker.sh          # Docker 构建脚本
└── build-traditional.ps1 / build-traditional.sh  # 传统构建脚本
```

---

## 本地快速启动

### 前置条件

- Java 17+ / Maven 3.6+ / Node.js 18+ / MySQL 8.0+

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS photodb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 设置环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | **必填，无默认值** |
| `JWT_SECRET` | JWT HS256 签名密钥 | **必填，无默认值** |
| `ADMIN_PASSWORD` | Konami 解锁后的管理密码 | `photoadmin` |
| `VITE_ADMIN_PASSWORD` | 前端密码（构建时注入） | 自动继承 `ADMIN_PASSWORD` |

> `JWT_SECRET` 在 `JwtUtil.java` 启动时通过 `requireEnv()` 强制读取，未设置会直接终止启动。

**Windows (PowerShell):**
```powershell
$env:DB_PASSWORD="你的数据库密码"
$env:JWT_SECRET="$(openssl rand -base64 32)"   # 或手动指定一段随机字符串
$env:ADMIN_PASSWORD="你们朋友间的共享密码"
```
>注意前端发送的是VITE_ADMIN_PASSWORD，此项应在frontend文件夹中建立.env文件配置

**Linux / macOS:**
```bash
export DB_PASSWORD=你的数据库密码
export JWT_SECRET=$(openssl rand -base64 32)
export ADMIN_PASSWORD=你们朋友间的共享密码
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

后端运行在 `http://localhost:8080`，Flyway 首次启动自动建表。
`/swagger-ui.html` 可查看 API 文档。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。Vite 自动将 `/api` 和 `/share` 代理到 `localhost:8080`。

### 5. 首次使用

1. 打开页面 → 看到 Konami 街机界面
2. 键盘输入 **`↑ ↑ ↓ ↓ ← → ← → B A B A`**（或点击虚拟方向键 + A/B 按钮）
3. 输入管理密码 → 后端 BCrypt 验密 → 签发 24h admin JWT
4. 进入管理系统，开始上传照片

> **密码配置：** 前端密码通过 `VITE_ADMIN_PASSWORD` 环境变量注入（Vite 构建时静态替换）。开发时可直接修改 `frontend/.env` 文件，生产构建时通过构建脚本自动从 `ADMIN_PASSWORD` 继承。详见 `frontend/.env.example`。

---

## 构建

### 一键构建（推荐）

**Windows:**
```powershell
.\build-traditional.ps1    # 传统 JAR 构建
.\build-docker.ps1         # Docker 镜像构建
```

**Linux / macOS:**
```bash
./build-traditional.sh     # 传统 JAR 构建
./build-docker.sh          # Docker 镜像构建
```

构建脚本自动执行：前端 `npm run build` → 复制到 `backend/static` → Maven 打包。

### 手动构建

```bash
# 1. 设置前端密码（二选一）
#    方式A: 编辑 frontend/.env，修改 VITE_ADMIN_PASSWORD
#    方式B: export VITE_ADMIN_PASSWORD=你的密码

# 2. 构建前端
cd frontend
npm run build

# 3. 复制到后端静态目录
rm -rf ../backend/src/main/resources/static/*
cp -r dist/* ../backend/src/main/resources/static/

# 4. 打包后端
cd ../backend
mvn clean package -DskipTests
```

输出 `backend/target/photo-gallery-0.0.1-SNAPSHOT.jar`。

---

## 部署

### 方式一：Docker Compose（推荐）

#### 1. 创建 .env 文件

参考 `.env.example` 模板在项目根目录创建 `.env`：

```bash
MYSQL_ROOT_PASSWORD=你的MySQL密码
MYSQL_DATABASE=photodb
DB_HOST=mysql
DB_USERNAME=root
DB_PASSWORD=${MYSQL_ROOT_PASSWORD}
JWT_SECRET=$(openssl rand -base64 32)
ADMIN_PASSWORD=你们朋友间的共享密码
```

#### 2. 上传文件到服务器

```bash
ssh root@<IP> "mkdir -p /opt/photo-gallery/backend/target"
scp docker-compose.yml .env root@<IP>:/opt/photo-gallery/
scp backend/Dockerfile root@<IP>:/opt/photo-gallery/backend/
scp backend/target/photo-gallery-*.jar root@<IP>:/opt/photo-gallery/backend/target/
```

#### 3. 启动

```bash
ssh root@<IP>
cd /opt/photo-gallery
docker compose up -d --build
```

首次启动 Flyway 自动建表。访问 `http://<IP>`（docker-compose 端口映射 `80:8080`）。

#### 4. 常用命令

```bash
docker compose ps              # 查看状态
docker compose logs -f app     # 查看应用日志
docker compose restart app     # 重启应用
docker compose down            # 停止并删除容器
```

#### 5. Nginx 反向代理（可选，配合 HTTPS 推荐）

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

# 免费 SSL 证书
certbot --nginx -d 你的域名
```

---

## 鉴权

本应用**不设用户注册/登录系统**。身份由 JWT role claim 区分：

```
┌─────────────────────────────────────────────────────┐
│  Konami 解锁 (前端交互)                               │
│    → POST /api/auth/unlock (ADMIN_PASSWORD)         │
│    → 后端 BCrypt 验密                                │
│    → 签发 24h admin JWT (role: admin)               │
│    → 前端 localStorage 持久化                        │
│                                                     │
│  分享链接 (管理员生成)                                │
│    → POST /api/share/generate {photoIds}            │
│    → 签发 7 天 viewer JWT (role: viewer + photos)   │
│    → /share/{token} → ShareViewer 落地面            │
└─────────────────────────────────────────────────────┘
```

| 入口 | JWT Claim | 权限 |
|------|-----------|------|
| Konami 解锁 | `role: admin` | 上传、编辑、删除、生成分享链接、管理分类/标签/相册 |
| 分享链接 | `role: viewer`, `photos: [...]` | 仅查看 JWT 中编码的指定照片 |

**SecurityFilterChain 规则：**

| 请求 | 权限要求 |
|------|---------|
| `GET /api/**` | `ROLE_admin` 或 `ROLE_viewer` |
| `POST/PUT/DELETE /api/**` | `ROLE_admin` |
| `POST /api/auth/unlock` | 公开 |
| `GET /share/**` | 公开 |
| `/swagger-ui/**`, `/v3/api-docs/**` | 公开 |
| 静态资源 (`/`, `/assets/**`) | 公开 |

**关键安全配置：**

| 配置 | 位置 | 说明 |
|------|------|------|
| `ADMIN_PASSWORD` | 环境变量 | 管理密码，前后端需一致 |
| `JWT_SECRET` | 环境变量 | HS256 签名密钥，`JwtUtil.java` 启动时从环境变量读取 |
| JWT 有效期 admin | 24 小时 | `JwtUtil.java` `issueAdmin(24*60*60*1000)` |
| JWT 有效期 viewer | 7 天 | `AuthController.java` 默认 7 天 |
| BCrypt | `SecurityConfig.java` | 密码存储使用 BCrypt 哈希 |
