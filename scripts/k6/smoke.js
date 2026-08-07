// 场景 1：首页 / 静态资源冒烟 —— SPA 入口 + 入口引用的静态资产加载
// 说明：从 index.html 提取引用的 js/css/svg/webmanifest 逐一加载——静态服务 404 会直接暴露构建/部署问题
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
  if (res.status !== 200) return

  // 提取 index.html 引用的静态资产（modulepreload 的 href、script src、link href）
  const re = /(?:src|href)="([^"]+\.(?:js|css|svg|webmanifest))"/g
  let m
  const assets = []
  while ((m = re.exec(res.body)) !== null) assets.push(m[1])
  for (const asset of assets) {
    const url = asset.startsWith('http') ? asset : `${BASE}${asset}`
    const a = http.get(url)
    check(a, { [asset + ' 200']: (r) => r.status === 200 })
  }
}
