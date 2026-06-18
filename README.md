# 照片管理器

前后端分离的全栈照片管理应用——朋友间分享照片的私人图库。暗黑玻璃态 UI，Konami 密码门禁联动 JWT 鉴权，支持标签分类筛选、批量操作、缩略图优化和限时分享链接。

---

## 项目技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 3.3.0 |
| 安全框架 | Spring Security + JWT (jjwt) | 0.12 |
| 语言 | Java | 17 |
| ORM | Spring Data JPA + Hibernate | — |
| 数据库 | MySQL + Flyway 迁移 | 8.0+ |
| 参数校验 | Bean Validation | — |
| API 文档 | SpringDoc OpenAPI | 2.5.0 |
| 前端框架 | Vue 3 (Composition API) + Pinia | 3.5 |
| 构建工具 | Vite | 5.4 |
| 动画 | GSAP + Lottie | — |
| 日志 | Logback（控制台 + 按天滚动） | — |
| 部署 | Docker Compose / 传统部署 | — |

---

## 项目功能

- 照片上传（拖拽/粘贴/批量、魔数校验、10MB 限制、自动缩略图）
- 照片浏览（无限滚动分页、3D 倾斜卡片、GSAP 入场动画）
- 标签系统（自定义颜色、多选筛选、行内编辑）
- 分类系统（多选筛选、行内编辑）
- 批量操作（多选、批量删除、批量上传）
- 缩略图（上传时自动生成 400px JPEG，7 天浏览器缓存）
- **Konami 密码门禁** — 方向键序列解锁，联动后端 JWT 鉴权
- **限时分享链接** — 选中照片生成 7 天链接，朋友无需密码即可查看
- Toast 通知、骨架屏加载、自定义确认弹窗
- 移动端响应式适配

---

## 本地快速启动

> 前置条件：Java 17+ / Maven 3.6+ / Node.js 18+ / MySQL 8.0+

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS photodb CHARACTER SET utf8mb4;
```

### 2. 配置环境变量

**Windows (PowerShell):**
```powershell
$env:DB_PASSWORD="你的数据库密码"
$env:ADMIN_PASSWORD="朋友间的共享密码"
```

**Linux / macOS:**
```bash
export DB_PASSWORD=你的数据库密码
export ADMIN_PASSWORD=你们朋友间的共享密码
```

### 3. 启动后端

**Windows (PowerShell):**
```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--DB_PASSWORD=$env:DB_PASSWORD"
```

**Linux / macOS:**
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

后端运行在 `http://localhost:8080`。Flyway 首次启动自动建表。

### 4. 启动前端

**Windows / Linux 通用:**
```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。Vite 自动将 `/api` 代理到 `localhost:8080`。

### 5. 首次使用

1. 打开页面 → Konami 密码门禁
2. 键盘输入 `↑ ↑ ↓ ↓ ← → ← → B A B A`（或点击虚拟 D-Pad）
3. 后台自动用 `ADMIN_PASSWORD` 换取 JWT → 进入管理系统
4. 上传照片、管理标签分类

> 前端密码在 `frontend/src/api.js` 第 4 行，默认 `photoadmin`。构建前改为你的 `ADMIN_PASSWORD`。

---

## 构建

### 传统构建

**Windows (PowerShell):**
```powershell
.\build-traditional.ps1
```

**Linux / macOS:**
```bash
chmod +x build-traditional.sh
./build-traditional.sh
```

输出：`backend/target/demo-backend-0.0.1-SNAPSHOT.jar`

### Docker 构建

**Windows (PowerShell):**
```powershell
.\build-docker.ps1
```

**Linux / macOS:**
```bash
chmod +x build-docker.sh
./build-docker.sh
```

构建脚本自动执行：前端 build → 复制到 `backend/static` → Maven 打包 → `docker compose up -d --build`

### 手动构建

```bash
# 前端
cd frontend
# 编辑 src/api.js，修改 ADMIN_PASSWORD
npm run build

# 复制到后端静态目录
rm -rf ../backend/src/main/resources/static/*
cp -r dist/* ../backend/src/main/resources/static/

# 后端打包
cd ../backend
mvn clean package -DskipTests
```

---

## 云端部署

### 方式一：传统部署（Nginx + Java）

#### 1. 上传

```bash
scp -r frontend/dist/* root@<IP>:/opt/app/static/
scp backend/target/demo-backend-*.jar root@<IP>:/opt/app/
```

#### 2. 创建数据库及用户

```sql
CREATE DATABASE IF NOT EXISTS photodb CHARACTER SET utf8mb4;
CREATE USER 'app_user'@'localhost' IDENTIFIED BY '生产密码';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, REFERENCES ON photodb.* TO 'app_user'@'localhost';
FLUSH PRIVILEGES;
```

#### 3. Nginx 配置

```nginx
server {
    listen 80;
    server_name 你的域名;

    root /opt/app/static;
    index index.html;

    client_max_body_size 20m;

    location /api {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /share {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

```bash
nginx -s reload
```

#### 4. 启动后端

```bash
nohup java -jar /opt/app/demo-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_USERNAME=app_user \
  --DB_PASSWORD=生产密码 \
  --ADMIN_PASSWORD=你们朋友间的共享密码 \
  > /opt/app/app.log 2>&1 &
```

#### 5. 验证

访问 `http://<IP>` → Konami 门禁 → 解锁 → 进入管理页。

---

### 方式二：Docker Compose 部署

#### 1. 上传

```bash
scp docker-compose.yml root@<IP>:/opt/app/
scp -r backend/target/demo-backend-*.jar root@<IP>:/opt/app/
scp -r backend/Dockerfile root@<IP>:/opt/app/
scp -r backend/src/main/resources/static root@<IP>:/opt/app/
```

#### 2. 修改环境变量

编辑服务器上的 `docker-compose.yml`：

```yaml
environment:
  SPRING_PROFILES_ACTIVE: prod
  DB_HOST: mysql
  DB_USERNAME: root
  DB_PASSWORD: 生产密码
  ADMIN_PASSWORD: 朋友间的共享密码
```

#### 3. 启动

```bash
cd /opt/app
docker compose up -d
```

#### 4. 管理

```bash
docker compose ps              # 查看状态
docker compose logs -f app     # 查看日志
docker compose down            # 停止
```

---

## 鉴权方法

不设用户注册登录。**知道 Konami + 管理密码的人就是管理员。**

```
Konami 解锁（前端）
  → POST /api/auth/unlock（带 ADMIN_PASSWORD）
  → 后端 BCrypt 验密 → 签发 24h admin JWT（role: admin）
  → 前端写 localStorage，后续所有请求自动带 Authorization

分享链接
  → 管理员选中照片 → 生成分享链接 → 签发 7 天 viewer JWT
  → JWT claims 编码：role: viewer + photos: [id1, id2, ...] + 过期时间
  → 朋友点链接即看 ShareViewer 落地页，仅显示指定照片
```

| 入口 | JWT 角色 | 权限 |
|------|---------|------|
| Konami 解锁 | `role: admin` | 上传、编辑、删除、生成分享 |
| 分享链接 | `role: viewer` | 仅查看指定照片 |

**关键配置项：**

| 配置 | 说明 |
|------|------|
| `ADMIN_PASSWORD` | 管理密码，前后端需一致 |
| `JWT_SECRET` | JWT 签名密钥，至少 32 字节，生产环境必设 |

---

## 项目结构

```
├── backend/
│   ├── src/main/java/com/example/demo/
│   │   ├── DemoApplication.java          # 应用入口
│   │   ├── ApiResponse.java              # 统一响应体
│   │   ├── SecurityConfig.java           # Spring Security + CORS
│   │   ├── JwtUtil.java                  # JWT 签发与验签
│   │   ├── JwtAuthFilter.java            # JWT 认证过滤器
│   │   ├── AuthController.java           # 解锁 + 生成分享链接
│   │   ├── ShareController.java          # 分享入口
│   │   ├── Photo.java / Tag.java / Category.java
│   │   ├── PhotoController.java          # 照片 + 标签/分类 API
│   │   ├── PhotoService.java             # 业务逻辑 + 缩略图
│   │   ├── PhotoRepository.java          # 分页 + 筛选查询
│   │   ├── *Repository.java              # 标签/分类数据访问
│   │   ├── GlobalExceptionHandler.java   # 全局异常处理
│   │   └── *Exception.java               # 自定义异常
│   ├── src/main/resources/
│   │   ├── application.properties        # 公共配置
│   │   ├── application-dev.yml           # 开发环境
│   │   ├── application-prod.yml          # 生产环境
│   │   ├── logback-spring.xml            # 日志配置
│   │   └── db/migration/                 # Flyway 迁移
│   └── Dockerfile
│
├── frontend/
│   └── src/
│       ├── main.js / App.vue / api.js / style.css
│       ├── store.js / useConfirm.js
│       ├── stores/                       # Pinia（photo / toast / ui）
│       └── components/
│           ├── KonamiGate.vue            # 密码门禁
│           ├── ShareViewer.vue           # 分享落地页
│           ├── AppHeader.vue / UploadCard.vue
│           ├── PhotoGallery.vue / PhotoCard.vue
│           ├── ViewModal.vue / EditModal.vue
│           ├── FilterSidebar.vue / ToastProvider.vue
│           ├── ConfirmDialog.vue / LottieLoader.vue
│
├── docker-compose.yml
├── build-docker.ps1 / build-docker.sh
└── build-traditional.ps1 / build-traditional.sh
```
