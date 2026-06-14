# 照片管理器

前后端分离的全栈照片管理应用，支持上传、浏览、编辑和删除。前端采用暗黑玻璃态设计风格，后端对文件上传做了完善的安全校验。

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端框架 | Spring Boot | 3.3.0 |
| 语言 | Java | 17 |
| ORM | Spring Data JPA + Hibernate | — |
| 数据库 | MySQL | 8.0+ |
| 数据库迁移 | Flyway | — |
| 参数校验 | Bean Validation (Hibernate Validator) | — |
| 前端框架 | Vue 3 (Composition API) | 3.5 |
| 构建工具 | Vite | 5.4 |
| 动画 | anime.js | 4.4 |
| 文件存储 | 本地文件系统（路径存 MySQL） | — |

## 功能

- 照片上传（实时预览、文件魔数校验、大小限制校验）
- 照片网格浏览（卡片交错入场、3D 倾斜跟随鼠标）
- 照片查看（弹窗缩放动画）
- 照片编辑（名称和描述，Bean Validation 参数校验）
- 照片删除（卡片模糊消散动画）
- 全局异常处理（统一 JSON 响应格式 `{code, message, data}`）
- 文件上传安全校验（魔数识别、文件大小拦截、类型白名单）

## 项目结构

```
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/example/demo/
│   │   ├── DemoApplication.java          # 应用入口
│   │   ├── ApiResponse.java              # 统一响应体
│   │   ├── GlobalExceptionHandler.java   # 全局异常处理
│   │   ├── HelloController.java          # 测试接口
│   │   ├── Photo.java                    # 照片实体（含校验注解）
│   │   ├── PhotoController.java          # REST API
│   │   ├── PhotoService.java             # 业务逻辑 + 文件校验
│   │   ├── PhotoRepository.java          # 数据访问
│   │   ├── InvalidFileTypeException.java # 文件类型异常
│   │   └── FileSizeExceededException.java# 文件大小异常
│   ├── src/main/resources/
│   │   ├── application.properties        # 公共配置
│   │   ├── application-dev.yml           # 开发环境
│   │   ├── application-prod.yml          # 生产环境
│   │   └── db/migration/                 # Flyway 迁移脚本
│   └── pom.xml
│
└── frontend/                         # Vue 3 + Vite 前端
    ├── src/
    │   ├── main.js                     # Vue 入口
    │   ├── App.vue                     # 根组件 + 全局特效
    │   ├── style.css                   # 暗黑玻璃态样式
    │   └── components/
    │       ├── AppHeader.vue           # 渐变标题
    │       ├── UploadCard.vue          # 上传表单
    │       ├── PhotoGallery.vue        # 照片网格
    │       ├── PhotoCard.vue           # 照片卡片（3D 倾斜）
    │       ├── ViewModal.vue           # 查看大图弹窗
    │       └── EditModal.vue           # 编辑信息弹窗
    ├── index.html
    ├── package.json
    └── vite.config.js
```

## 安全校验

| 校验项 | 实现方式 | 说明 |
|--------|---------|------|
| 文件类型 | 魔数（Magic Bytes）校验 | 读取文件头 12 字节，仅放行 JPEG / PNG / GIF / BMP / WebP |
| 文件大小 | 应用层 `file.getSize()` 检查 | 限制 ≤ 10MB，容器层面放宽至 100MB 确保错误响应正常返回 |
| 前端类型提示 | `accept="image/*"` | 文件选择器仅显示图片，不依赖此作为安全措施 |
| 参数校验 | `@NotBlank` / `@Size` + `@Valid` | 编辑时名称不能为空，描述不超过 500 字 |
| 文件名安全 | `UUID_原文件名` | 防止路径遍历和文件名冲突 |

## API 接口

| 方法 | 端点 | 描述 |
|------|------|------|
| GET | `/api/hello` | 健康检查 |
| GET | `/api/photos` | 获取所有照片 |
| GET | `/api/photos/{id}` | 获取单张照片信息 |
| GET | `/api/photos/{id}/file` | 下载照片原文件 |
| POST | `/api/photos` | 上传照片（multipart/form-data） |
| PUT | `/api/photos/{id}` | 更新名称/描述 |
| DELETE | `/api/photos/{id}` | 删除照片 |

所有错误响应格式：`{"code": 400, "message": "错误描述", "data": null}`

## 异常处理覆盖

| 场景 | HTTP 状态码 | 用户提示示例 |
|------|------------|-------------|
| 上传非图片文件 | 400 | 文件格式不支持，请上传常见的图片文件 |
| 上传空文件 | 400 | 无法识别文件内容，请上传图片文件 |
| 上传超大文件 | 400 | 文件过大，请上传小于 10MB 的图片 |
| 编辑时名称为空 | 400 | 照片名称不能为空 |
| 编辑时描述过长 | 400 | 描述不能超过500字 |
| 操作不存在的照片 | 500 | 该照片已被删除或不存在 |
| 服务器内部错误 | 500 | 系统繁忙，请稍后重试 |

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

编辑 `backend/src/main/resources/application-dev.yml`，修改数据库用户名和密码：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/photodb?...
    username: 你的用户名
    password: 你的密码
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
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
cd frontend && npm run build          # 产出 dist/

# 后端（跳过测试，打包为可执行 jar）
cd backend && mvn clean package -DskipTests   # 产出 target/demo-backend-0.0.1-SNAPSHOT.jar
```

### 2. 上传到服务器

```bash
# 前端静态文件
scp -r frontend/dist/* root@<服务器IP>:/opt/app/static/

# 后端 jar 包
scp backend/target/demo-backend-*.jar root@<服务器IP>:/opt/app/
```

### 3. 配置生产环境数据库

编辑 `backend/src/main/resources/application-prod.yml`，配置生产数据库连接后重新打包；或直接在服务器上创建同路径配置文件覆盖。

### 4. Nginx 配置

```nginx
server {
    listen 80;
    server_name 你的域名或IP;

    root /opt/app/static;
    index index.html;

    # 上传文件大小限制（必须 ≥ 后端限制，否则 Nginx 会拦截报错）
    client_max_body_size 20m;

    # API 反向代理到 Spring Boot
    location /api {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 前端 SPA 路由
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

> 务必设置 `client_max_body_size`，否则超过 1MB（Nginx 默认）的请求会被 Nginx 拦截返回 HTML 错误页，前端无法解析。

修改后重载 Nginx：

```bash
nginx -s reload
```

### 5. 启动后端

```bash
ssh root@<服务器IP>
nohup java -jar /opt/app/demo-backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  > /opt/app/app.log 2>&1 &
```

### 6. 验证

浏览器访问 `http://<服务器IP>`，应看到照片管理页面。上传一张图片测试功能正常即可。

## Spring Profile

| 文件 | 环境 | 说明 |
|------|------|------|
| `application.properties` | 公共 | 端口、上传限制、JPA 方言等 |
| `application-dev.yml` | 开发 | 本地数据库连接 |
| `application-prod.yml` | 生产 | 生产数据库连接 |
