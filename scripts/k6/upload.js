// 场景 3：上传 —— Konami 解锁 → multipart 上传（含 409 去重分支验证）
// 注意：压测会真实落库/落盘，仅对测试环境执行；批量上传同样触发 SHA-256 去重
//   k6 run scripts/k6/upload.js
import http from 'k6/http'
import { check } from 'k6'
import { konamiUnlock, makeJpegBytes } from './util.js'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
}

const token = konamiUnlock(BASE)
const payload = { name: `k6-${__VU}-${Date.now()}`, file: http.file(makeJpegBytes(), 'k6.jpg', 'image/jpeg') }

export default function () {
  const res = http.post(`${BASE}/api/v1/photos`, payload, {
    headers: { Authorization: `Bearer ${token}` },
  })
  // 首次 200；同内容重复 → 409（去重分支）
  check(res, {
    'upload 200 或 409': (r) => r.status === 200 || r.status === 409,
    '重复时返回已有照片': (r) => r.status !== 409 || JSON.parse(r.body).data !== undefined,
  })
}
