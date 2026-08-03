/**
 * 构建产物复制：frontend/dist → backend/src/main/resources/static
 * 使单 SPA 能随 Spring Boot JAR 伺服；产物不入库（.gitignore），
 * CI 的 docker/e2e job 与本地部署均通过 npm run build 产出。
 * 整体清空再复制：vite 产物带内容 hash，增量复制会残留死文件。
 */
import { cpSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const dist = resolve(root, 'dist')
const target = resolve(root, '..', 'backend', 'src', 'main', 'resources', 'static')

rmSync(target, { recursive: true, force: true })
mkdirSync(target, { recursive: true })
cpSync(dist, target, { recursive: true })
console.log(`[copy-dist] ${dist} → ${target}`)
