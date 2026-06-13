# 照片管理系统

一个前后端分离的全栈应用，提供照片上传、浏览、编辑和删除功能。

## 技术栈

- **后端**: Java 17 + Spring Boot 3.3 + JPA + MySQL
- **数据库迁移**: Flyway
- **前端**: JavaScript + Vite
- **文件存储**: 本地文件系统（路径存储于 MySQL）

## 项目结构

```
├── backend/              # Spring Boot 后端服务
│   ├── src/main/java/    # 源代码
│   │   └── com/example/demo/
│   │       ├── DemoApplication.java     # 应用入口
│   │       ├── HelloController.java     # 测试端点
│   │       ├── PhotoController.java     # REST API 控制器
│   │       ├── PhotoService.java        # 业务逻辑层
│   │       ├── PhotoRepository.java     # 数据访问层
│   │       └── Photo.java               # 数据实体
│   ├── src/main/resources/
│   │   ├── application.properties       # 公共配置
│   │   ├── application-dev.yml          # 开发环境配置
│   │   ├── application-prod.yml         # 生产环境配置
│   │   ├── db/migration/                # Flyway 迁移脚本
│   │   │   └── V1__create_photos.sql
│   │   └── static/                      # 前端构建产物（部署用）
│   ├── uploads/                         # 上传的图片存储目录
│   └── pom.xml                          # Maven 配置
│
└── frontend/             # Vite + JavaScript 前端
    ├── src/
    │   ├── main.js       # 应用入口、事件处理
    │   ├── style.css     # 样式
    │   └── index.html    # HTML 模板
    ├── package.json      # NPM 配置
    └── vite.config.js    # Vite 配置
```

## API 接口

| 方法 | 端点 | 描述 |
|------|------|------|
| GET | `/api/hello` | 健康检查 |
| GET | `/api/photos` | 获取所有照片列表 |
| GET | `/api/photos/{id}` | 获取照片元数据 |
| GET | `/api/photos/{id}/file` | 下载照片文件 |
| POST | `/api/photos` | 上传新照片 |
| PUT | `/api/photos/{id}` | 更新照片信息（名称/描述） |
| DELETE | `/api/photos/{id}` | 删除照片 |

## 快速开始

### 前置条件

- Java 17+
- Node.js 16+
- Maven 3.6+
- MySQL 8.0+

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS photodb CHARACTER SET utf8mb4;
```

### 2. 配置数据库连接

按需修改 `backend/src/main/resources/application-dev.yml` 中的数据库用户名和密码。

### 3. 运行后端

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

后端将在 `http://localhost:8080` 启动。Flyway 会在首次启动时自动建表。

### 4. 运行前端

```bash
cd frontend
npm install
npm run dev
```

按 Vite 提示打开浏览器访问前端应用。

## 功能特性

- 照片上传（支持文件预览）
- 照片浏览（网格布局展示）
- 照片编辑（修改名称和描述）
- 照片删除
- 文件下载
- 数据持久化（MySQL + 本地文件系统）
- 数据库版本管理（Flyway）
- 多环境配置（dev / prod）
- 限制上传文件大小（10MB）

## Spring Profile 配置

项目使用多环境配置隔离：

| 文件 | 用途 | ddl-auto |
|------|------|----------|
| `application.properties` | 公共配置（端口、驱动等） | - |
| `application-dev.yml` | 开发环境 | `validate` |
| `application-prod.yml` | 生产环境 | `validate` |

启动时指定 profile：

```bash
# 开发
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 生产
java -jar demo-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

## 开发和构建

### 构建前端生产版本

```bash
cd frontend
npm run build
```

### 部署到后端静态目录

```bash
cp -r frontend/dist/* backend/src/main/resources/static/
```

### 构建后端 JAR 包

```bash
cd backend
mvn clean package -DskipTests
```

生成的 JAR 包位于 `backend/target/demo-backend-0.0.1-SNAPSHOT.jar`

## 部署到服务器

```bash
# 1. 上传 JAR 到服务器
scp target/demo-backend-0.0.1-SNAPSHOT.jar root@服务器IP:/opt/demo/

# 2. 确保服务器 MySQL 已创建 photodb 库并配置好 application-prod.yml 的数据库连接

# 3. 后台启动
ssh root@服务器IP "nohup java -jar /opt/demo/demo-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > /opt/demo/app.log 2>&1 &"
```

## 数据库迁移

表结构变更由 Flyway 管理。需要修改表结构时：

1. 在 `backend/src/main/resources/db/migration/` 下创建新脚本（如 `V2__add_tags.sql`）
2. 重启应用，Flyway 自动执行未跑过的脚本

> **注意**：已执行过的迁移脚本不可修改，否则 Flyway 会因 checksum 不匹配而拒绝启动。
