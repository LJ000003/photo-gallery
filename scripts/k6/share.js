// 场景 4：分享链路 —— 生成分享 → viewer 查看（白名单 + 签名剥离路径）
//   k6 run scripts/k6/share.js
import http from 'k6/http'
import { check } from 'k6'
import { konamiUnlock } from './util.js'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
}

const token = konamiUnlock(BASE)

export default function () {
  // 1. 取一张照片（分享需要真实 photoId）
  const list = http.get(`${BASE}/api/v1/photos?page=0&size=1`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  const photoId = JSON.parse(list.body).data?.content?.[0]?.id
  if (!photoId) return // 空库跳过本轮

  // 2. 生成分享（viewer JWT）
  const gen = http.post(
    `${BASE}/api/v1/share/generate`,
    JSON.stringify({ photoIds: [photoId], permission: 'view', expireDays: 7 }),
    {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    },
  )
  const viewerToken = JSON.parse(gen.body).data?.token
  if (!viewerToken) return

  // 3. viewer 查看（JWT 白名单路径）
  const view = http.get(`${BASE}/api/v1/share/view?page=0&size=20`, {
    headers: { Authorization: `Bearer ${viewerToken}` },
  })
  check(view, { 'share/view 200': (r) => r.status === 200 })
}
