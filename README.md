# 照片管理器

前后端分离的全栈照片管理应用，支持上传、浏览、编辑和删除，配备标签/分类筛选、批量操作、缩略图优化和移动端适配。前端采用暗黑玻璃态设计风格。

## 技术栈

| 层         | 技术                                  | 版本  |
| ---------- | ------------------------------------- | ----- |
| 后端框架   | Spring Boot                           | 3.3.0 |
| 语言       | Java                                  | 17    |
| ORM        | Spring Data JPA + Hibernate           | —     |
| 数据库     | MySQL                                 | 8.0+  |
| 数据库迁移 | Flyway                                | —     |
| 参数校验   | Bean Validation (Hibernate Validator) | —     |
| 前端框架   | Vue 3 (Composition API)               | 3.5   |
| 构建工具   | Vite                                  | 5.4   |
| 动画       | GSAP + Lottie                         | —     |
| 日志       | Logback（控制台 + 按天滚动文件）      | —     |
| 文件存储   | 本地文件系统（年/月子目录 + 缩略图）  | —     |

## 功能

- 照片上传（实时预览、魔数校验、大小限制、多文件批量上传）
- 照片网格浏览（卡片交错入场、3D 倾斜跟随鼠标、无限滚动分页）
- 照片查看（弹窗缩放动画）
- 照片编辑（名称、描述、标签、分类）
- 照片删除（卡片模糊消散动画、批量删除）
- 标签系统（新建、重命名、删除、多选筛选、自定义颜色）
- 分类系统（新建、重命名、删除、多选筛选）
- 缩略图自动生成（400px 宽、JPEG 75% 质量、7 天浏览器缓存）
- 全局异常处理（统一 JSON 响应 `{code, message, data}`）
- 自定义确认弹窗（暗黑玻璃风格）
- 移动端响应式适配（侧边栏滑入、触控优化）
- 回到顶部按钮

## 项目结构

```
├── backend/                              # Spring Boot 后端
│   ├── src/main/java/com/example/demo/
│   │   ├── DemoApplication.java              # 应用入口
│   │   ├── ApiResponse.java                  # 统一响应体
│   │   ├── CorsConfig.java                   # 跨域配置
│   │   ├── GlobalExceptionHandler.java       # 全局异常处理
│   │   ├── HelloController.java              # 测试接口
│   │   ├── Photo.java                        # 照片实体
│   │   ├── PhotoController.java              # 照片 + 标签/分类 API
│   │   ├── PhotoService.java                 # 业务逻辑 + 缩略图
│   │   ├── PhotoRepository.java              # 数据访问（含筛选查询）
│   │   ├── Tag.java                          # 标签实体
│   │   ├── TagRepository.java                # 标签数据访问
│   │   ├── Category.java                     # 分类实体
│   │   ├── CategoryRepository.java           # 分类数据访问
│   │   ├── InvalidFileTypeException.java     # 文件类型异常
│   │   └── FileSizeExceededException.java    # 文件大小异常
│   ├── src/main/resources/
│   │   ├── application.properties            # 公共配置
│   │   ├── application-dev.yml               # 开发环境
│   │   ├── application-prod.yml              # 生产环境
│   │   ├── logback-spring.xml                # 日志配置
│   │   └── db/migration/                     # Flyway 迁移脚本
│   └── pom.xml
│
└── frontend/                             # Vue 3 + Vite 前端
    ├── src/
    │   ├── main.js                         # Vue 入口
    │   ├── App.vue                         # 根组件 + 全局特效
    │   ├── style.css                       # 暗黑玻璃态 + 响应式
    │   ├── store.js                        # 标签/分类共享状态
    │   ├── useConfirm.js                   # 自定义确认弹窗
    │   └── components/
    │       ├── AppHeader.vue               # 渐变标题（含彩蛋）
    │       ├── UploadCard.vue               # 上传表单（批量 + 标签/分类）
    │       ├── PhotoGallery.vue             # 照片网格（无限滚动 + 批量操作）
    │       ├── PhotoCard.vue                # 照片卡片（3D 倾斜 + 复选框）
    │       ├── ViewModal.vue                # 查看大图弹窗
    │       ├── EditModal.vue                # 编辑信息弹窗（含标签/分类）
    │       ├── FilterSidebar.vue            # 标签/分类筛选侧边栏
    │       ├── ConfirmDialog.vue            # 自定义确认弹窗
    │       └── LottieLoader.vue             # Lottie 动画加载器
    ├── index.html
    ├── package.json
    └── vite.config.js
```

## 安全校验

| 校验项       | 实现方式                         | 说明                                                     |
| ------------ | -------------------------------- | -------------------------------------------------------- |
| 文件类型     | 魔数（Magic Bytes）校验          | 读取文件头 12 字节，仅放行 JPEG / PNG / GIF / BMP / WebP |
| 文件大小     | 应用层 `file.getSize()` 检查     | 限制 ≤ 10MB，容器层面放宽至 100MB                       |
| 前端类型提示 | `accept="image/*"`               | 文件选择器仅显示图片，不依赖此作为安全措施               |
| 参数校验     | `@NotBlank` / `@Size` + `@Valid` | 编辑时名称不能为空，描述不超过 500 字                    |
| 文件名安全   | `年/月/UUID_原文件名`            | 防止路径遍历、文件名冲突及单目录文件堆积                 |
| 缩略图       | 上传时自动生成 400px JPEG        | 网格列表用缩略图，查看弹窗才用原图                       |
| 跨域         | `CorsConfig`                     | 全局允许 `http://localhost:5173`，生产需改为实际域名     |
| 数据库凭证   | 环境变量 `${DB_USERNAME}` `${DB_PASSWORD}` | 不写入配置文件，通过启动参数传入                         |

## API 接口

### 照片

| 方法   | 端点                          | 描述                                  |
| ------ | ----------------------------- | ------------------------------------- |
| GET    | `/api/photos`                 | 分页获取照片（可选 `tagIds` `categoryIds` 筛选） |
| GET    | `/api/photos/{id}`            | 获取单张照片信息                      |
| GET    | `/api/photos/{id}/file`       | 下载原文件                            |
| GET    | `/api/photos/{id}/thumbnail`  | 获取缩略图（无缩略图时降级返回原图）    |
| POST   | `/api/photos`                 | 上传单张照片                          |
| POST   | `/api/photos/batch`           | 批量上传                              |
| PUT    | `/api/photos/{id}`            | 更新名称/描述/标签/分类               |
| DELETE | `/api/photos/{id}`            | 删除照片                              |
| DELETE | `/api/photos/batch`           | 批量删除                              |
| POST   | `/api/photos/migrate-thumbnails` | 为旧照片补充生成缩略图              |

### 标签

| 方法   | 端点             | 描述           |
| ------ | ---------------- | -------------- |
| GET    | `/api/tags`      | 获取所有标签   |
| POST   | `/api/tags`      | 新建标签       |
| PUT    | `/api/tags/{id}` | 更新标签名称/颜色 |
| DELETE | `/api/tags/{id}` | 删除标签       |

### 分类

| 方法   | 端点                  | 描述           |
| ------ | --------------------- | -------------- |
| GET    | `/api/categories`     | 获取所有分类   |
| POST   | `/api/categories`     | 新建分类       |
| PUT    | `/api/categories/{id}` | 更新分类名称   |
| DELETE | `/api/categories/{id}` | 删除分类       |

所有错误响应格式：`{"code": 400, "message": "错误描述", "data": null}`

## 异常处理覆盖

| 场景             | HTTP 状态码 | 用户提示示例                         |
| ---------------- | ----------- | ------------------------------------ |
| 上传非图片文件   | 400         | 文件格式不支持，请上传常见的图片文件 |
| 上传空文件       | 400         | 无法识别文件内容，请上传图片文件     |
| 上传超大文件     | 400         | 文件过大，请上传小于 10MB 的图片     |
| 编辑时名称为空   | 400         | 照片名称不能为空                     |
| 编辑时描述过长   | 400         | 描述不能超过500字                    |
| 操作不存在的照片 | 500         | 该照片已被删除或不存在               |
| 服务器内部错误   | 500         | 系统繁忙，请稍后重试                 |

## 本地快速启动

### 前置条件

- Java 17+
- Maven 3.6+
- Node.js 18+
- MySQL 8.0+

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS photodb CHARACTER SET utf8mb4;
```

### 2. 配置数据库连接

数据库用户名和密码通过环境变量传入，不写在配置文件中。开发环境默认使用 `root` 用户，只需传入密码：

```bash
export DB_PASSWORD=你的密码
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--DB_PASSWORD=$DB_PASSWORD"
```

后端运行在 `http://localhost:8080`，Flyway 会在首次启动时自动建表。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。Vite 开发服务器自动将 `/api` 请求代理到后端 8080 端口。

## 构建与云端部署

采用前后端分离部署：Nginx 托管前端静态文件并反向代理 API，Java 运行后端 jar 包。

### 1. 构建

```bash
# 前端
cd frontend && npm run build

# 后端（跳过测试，打包为可执行 jar）
cd backend && mvn clean package -DskipTests
```

### 2. 上传到服务器

```bash
scp -r frontend/dist/* root@<服务器IP>:/opt/app/static/
scp backend/target/demo-backend-*.jar root@<服务器IP>:/opt/app/
```

### 3. 配置生产环境数据库

在 MySQL 中创建专用账户（含 Flyway 迁移所需 DDL 权限）：

```sql
CREATE USER 'app_user'@'localhost' IDENTIFIED BY '生产密码';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, REFERENCES ON photodb.* TO 'app_user'@'localhost';
FLUSH PRIVILEGES;
```

数据库连接信息通过启动参数传入（见第 5 步）。

### 4. Nginx 配置

```nginx
server {
    listen 80;
    server_name 你的域名或IP;

    root /opt/app/static;
    index index.html;

    client_max_body_size 20m;

    location /api {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

> 务必设置 `client_max_body_size`，否则超过 1MB（Nginx 默认）的请求会被 Nginx 拦截。

修改后重载 Nginx：

```bash
nginx -s reload
```

### 5. 启动后端

```bash
ssh root@<服务器IP>
nohup java -jar /opt/app/demo-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --DB_USERNAME=app_user \
  --DB_PASSWORD=生产密码 \
  > /opt/app/app.log 2>&1 &
```

首次部署需执行缩略图迁移（为已有照片生成缩略图）：

```bash
curl -X POST http://localhost:8080/api/photos/migrate-thumbnails
```

### 6. 验证

浏览器访问 `http://<服务器IP>`，应看到照片管理页面。上传一张图片测试功能正常即可。

## Spring Profile

| 文件                     | 环境 | 说明                                       |
| ------------------------ | ---- | ------------------------------------------ |
| `application.properties` | 公共 | 端口、上传限制、JPA 方言、上传目录默认值   |
| `application-dev.yml`    | 开发 | 数据库连接（`${DB_USERNAME:root}` / `${DB_PASSWORD}`） |
| `application-prod.yml`   | 生产 | 数据库连接（`${DB_USERNAME}` / `${DB_PASSWORD}`）+ 上传目录 `/data/photo-uploads` |
| `logback-spring.xml`     | 公共 | 控制台彩色输出 + 按天滚动文件（30 天），ERROR 单独记录（90 天） |

### 上传目录说明

默认上传目录为 `${user.home}/photo-uploads`，实际路径随操作系统变化：

| 操作系统 | 开发环境                         | 生产环境              |
| -------- | -------------------------------- | --------------------- |
| Windows  | `C:\Users\<用户名>\photo-uploads` | —                     |
| macOS    | `/Users/<用户名>/photo-uploads`   | —                     |
| Linux    | `/home/<用户名>/photo-uploads`    | `/data/photo-uploads` |

上传的图片按年月自动分子目录存储，并同时生成缩略图：

```
~/photo-uploads/2026/06/
├── a1b2c3_照片.jpg           ← 原图
└── thumbnails/
    └── a1b2c3_照片.jpg       ← 缩略图（400px 宽）
```

### 标签和分类

- 与照片多对多（标签）/ 多对一（分类）关联
- 侧边栏支持筛选（多选取并集，标签+分类取交集）
- 支持新建、行内重命名、单删、批量删除
- 数据库中间表 `photo_tags` 由 JPA 自动维护
