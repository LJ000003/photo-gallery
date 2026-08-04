# k6 压测脚本（4 场景）

本目录是 v6 改进方案 #31 的压测脚本。**需要本地安装 k6 后执行**（`winget install k6` 或 https://k6.io/docs/getting-started/installation/）。

## 场景

| 文件 | 场景 | 说明 |
|------|------|------|
| `smoke.js` | 首页/静态资源 | SPA 入口 + 静态资源加载，验证 TLS/静态服务基线 |
| `photo-list.js` | 照片列表 | GET /api/v1/photos（缓存命中/回源两态） |
| `upload.js` | 上传 | Konami 解锁 → POST /photos（含去重 409 分支） |
| `share.js` | 分享链路 | 生成分享 → viewer 查看（白名单 + 签名剥离路径） |

## 运行

前置：后端 dev 或 prod 运行中，且已有数据（photo-list / upload 需要照片库）。

```bash
# 冒烟：10 个 VU × 30s
k6 run --vus 10 --duration 30s scripts/k6/photo-list.js

# 全部场景（默认参数可调）
k6 run scripts/k6/smoke.js
k6 run scripts/k6/upload.js
k6 run scripts/k6/share.js
```

## 产物

跑完按 k6 输出记录到 `BENCHMARK.md`（场景 / VU / 耗时 p95 / 错误率）。本仓库**不提交伪造的压测数字**——有真实 k6 环境跑出的数据再提交。

## 备注

- 上传场景走完整 Challenge-Response：challenge → nonce+keys → unlock → JWT，与前端一致
- 认证端点有 10 req/s/IP 限流，upload.js 的解锁只做 1 次（循环外用）
- 生产注意：压测会真实落库/落盘，仅对测试环境执行
