// 场景 4：分享链路 —— 生成分享（DB token）→ viewer 查看（白名单 + 签名剥离）
// 说明：P0-#6 后分享凭证为 DB 随机 token（share_tokens 表，非 viewer JWT）；
//       generate 幂等复用（同 photoIds+permission 返回现有 URL/token，见 AuthService.generateShare）
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

// setup 一次性：
//   1. Konami 解锁（认证端点限流 10 req/s/IP，不能每 VU 都解锁；HTTP 不能在 init context 发起）
//   2. 校验照片库非空——空库时 default 的 view 会全部静默跳过造成「全绿假阳性」，这里直接 fail 中止
//   3. 生成分享并验证「revoke 立即生效」（P0-#6 核心语义：撤销后 view 401）
//   4. 重新生成有效 token 供 VU 压测 view 正常路径
export function setup() {
  const adminToken = konamiUnlock(BASE)

  const list = http.get(`${BASE}/api/v1/photos?page=0&size=1`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  })
  const photoId = JSON.parse(list.body).data?.content?.[0]?.id
  if (!photoId) {
    throw new Error('照片库为空，无法测分享链路——先造数（scripts/k6/seed-10000.sql）')
  }

  const gen = (photoIds) => http.post(
    `${BASE}/api/v1/share/generate`,
    JSON.stringify({ photoIds, permission: 'view', expireDays: 7 }),
    { headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' } },
  )

  // 撤销语义验证：撤销前 view 200 → revoke → view 401（分享立即失效）
  const first = JSON.parse(gen([photoId]).body).data
  if (!first?.token) throw new Error('share/generate 响应缺 token')
  const preRevoke = http.get(`${BASE}/api/v1/share/view?page=0&size=20`, {
    headers: { Authorization: `Bearer ${first.token}` },
  })
  if (preRevoke.status !== 200) throw new Error(`revoke 前 view 应 200，实际 ${preRevoke.status}`)

  const revoke = http.post(`${BASE}/api/v1/share/${first.token}/revoke`, null, {
    headers: { Authorization: `Bearer ${adminToken}` },
  })
  if (revoke.status !== 200) throw new Error(`share/revoke failed: ${revoke.status} ${revoke.body}`)
  const afterRevoke = http.get(`${BASE}/api/v1/share/view?page=0&size=20`, {
    headers: { Authorization: `Bearer ${first.token}` },
  })
  if (afterRevoke.status !== 401) {
    throw new Error(`revoke 后 view 应 401，实际 ${afterRevoke.status}（撤销未立即生效？）`)
  }

  // 生成压测用的有效 token（幂等复用：同 photoIds+permission 返回现有未撤销的分享）
  const second = JSON.parse(gen([photoId]).body).data
  return { token: second.token }
}

export default function (data) {
  const { token } = data
  // viewer 查看：DB token 经 JwtAuthFilter 白名单校验（photoId ∈ sharePhotoIds），
  // 响应剥离媒体短时签名（防 view 权限借签名下载原图）
  const view = http.get(`${BASE}/api/v1/share/view?page=0&size=20`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  check(view, { 'share/view 200': (r) => r.status === 200 })
}
