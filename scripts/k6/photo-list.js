// 场景 2：照片列表 API —— GET /api/v1/photos
// MODE 变体（--env MODE=...，默认 hit；对应 BENCHMARK.md 各行的「缓存态」列）：
//   hit     固定 page=0 —— 缓存命中态（列表 @Cacheable 30s TTL，同键请求全命中）
//   miss    随机 page 0..499 —— 回源态（每键首次请求打 DB，测查询真实性能）
//   deep    随机 page 400..499 —— 深翻页（OFFSET 8000~10000，offset 分页最差路径；随机键保证持续回源）
//   search  q=风景 + 随机 page —— FULLTEXT 1 万行搜索
// 用法：BASE_URL 指向带数据的后端；先解锁拿到 admin JWT
//   k6 run scripts/k6/photo-list.js
//   k6 run --env MODE=miss scripts/k6/photo-list.js
import http from 'k6/http'
import { check } from 'k6'
import { konamiUnlock } from './util.js'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const MODE = __ENV.MODE || 'hit'
const TOTAL_PAGES = 500 // 1 万行 ÷ size=20

export const options = {
  vus: 20,
  duration: '60s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    // hit/miss/search 目标 p95<300ms；deep 深翻页预期最差（无游标分页），先放宽记录真实数据
    http_req_duration: MODE === 'deep' ? ['p(95)<1000'] : ['p(95)<300'],
  },
}

// setup 执行一次 Konami Challenge-Response（认证端点限流 10 req/s/IP，不能每 VU 都解锁；
// 且 HTTP 不能在 init context 发起）
export function setup() {
  return { token: konamiUnlock(BASE) }
}

export default function (data) {
  const { token } = data
  const params = { headers: { Authorization: `Bearer ${token}` } }
  const page =
    MODE === 'hit' ? 0
    : MODE === 'deep' ? 400 + Math.floor(Math.random() * 100)
    : Math.floor(Math.random() * TOTAL_PAGES)
  let url = `${BASE}/api/v1/photos?page=${page}&size=20&sort=createdAt,desc`
  if (MODE === 'search') {
    url += `&q=${encodeURIComponent('风景')}`
  }
  const res = http.get(url, params)
  check(res, {
    'photos 200': (r) => r.status === 200,
    // ApiResponse 成功码：仅 HTTP 200 不够（错误响应体也有 data:null 字段，!== undefined 恒真）
    '业务 code=200': (r) => JSON.parse(r.body).code === 200,
  })
}
