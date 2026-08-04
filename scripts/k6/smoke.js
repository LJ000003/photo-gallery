// 场景 1：首页 / 静态资源冒烟 —— SPA 入口 + 静态资产加载
// 用法：k6 run scripts/k6/smoke.js
import http from 'k6/http'
import { check } from 'k6'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
}

export default function () {
  // 静态资源允许未经鉴权访问（SecurityConfig permitAll）
  const res = http.get(`${BASE}/`)
  check(res, { 'index 200': (r) => r.status === 200 })
}
