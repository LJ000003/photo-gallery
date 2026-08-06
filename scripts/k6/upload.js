// 场景 3：上传 —— Konami 解锁 → multipart 上传（种子化真实照片，测写入吞吐）
// 注意：压测会真实落库/落盘，仅对测试环境执行
//   k6 run scripts/k6/upload.js
import http from 'k6/http'
import { check, sleep } from 'k6'
import { konamiUnlock, makeJpegBytes } from './util.js'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'

export const options = {
  vus: 5,
  iterations: 100, // 5 VU 合计 100 张（每轮不同 seed → 全部真实新照片）
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
}

// setup 在 init 之后、迭代前运行一次：HTTP 只能在此阶段或 default 中发起（init context 禁止）
export function setup() {
  return { token: konamiUnlock(BASE) }
}

export default function (data) {
  const { token } = data
  // 每 VU 每轮独立 seed：文件字节不同 → SHA-256 不同，不走去重路径
  const seed = __VU * 1000 + __ITER
  const payload = {
    name: `k6-${__VU}-${__ITER}`,
    file: http.file(makeJpegBytes(seed), 'k6.jpg', 'image/jpeg'),
  }
  const res = http.post(`${BASE}/api/v1/photos`, payload, {
    headers: { Authorization: `Bearer ${token}` },
  })
  check(res, { 'upload 200': (r) => r.status === 200 })
  sleep(0.2) // 平缓节奏，避免瞬时打爆图片处理队列（core=2/max=4/queue=100 + DiscardPolicy）
}
