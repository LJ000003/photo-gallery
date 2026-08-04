// 场景 2：照片列表 API —— GET /api/v1/photos（30s 缓存命中/回源两态）
// 用法：BASE_URL 指向带数据的后端；先解锁拿到 admin JWT
//   k6 run scripts/k6/photo-list.js
import http from 'k6/http'
import { check } from 'k6'
import { konamiUnlock } from './util.js'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'

export const options = {
  vus: 20,
  duration: '60s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
}

// 循环外执行一次 Konami Challenge-Response（认证端点限流 10 req/s/IP，不能每 VU 都解锁）
const token = konamiUnlock(BASE)

export default function () {
  const params = { headers: { Authorization: `Bearer ${token}` } }
  const res = http.get(`${BASE}/api/v1/photos?page=0&size=20&sort=createdAt,desc`, params)
  check(res, {
    'photos 200': (r) => r.status === 200,
    '响应含 data': (r) => JSON.parse(r.body).data !== undefined,
  })
}
