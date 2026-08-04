// k6 共享工具：Konami Challenge-Response 解锁（与前端 requestToken 同构）
import http from 'k6/http'
import { check } from 'k6'

// 与后端 application.properties auth.konami-sequence 一致（测试环境可改）
const SEQUENCE = ['up', 'up', 'down', 'down', 'left', 'right', 'left', 'right', 'B', 'A', 'B', 'A']

/** 解锁并返回 admin JWT（一次性调用，避开认证端点 10 req/s/IP 限流） */
export function konamiUnlock(base) {
  const challenge = http.get(`${base}/api/v1/auth/challenge`)
  if (challenge.status !== 200) throw new Error(`challenge failed: ${challenge.status}`)
  const nonce = JSON.parse(challenge.body).data.nonce

  const unlock = http.post(
    `${base}/api/v1/auth/unlock`,
    JSON.stringify({ nonce, keys: SEQUENCE }),
    { headers: { 'Content-Type': 'application/json' } },
  )
  if (unlock.status !== 200) throw new Error(`unlock failed: ${unlock.status} ${unlock.body}`)
  return JSON.parse(unlock.body).data.token
}

/** 上传一张内存图片（固定 1x1 JPEG，注意 SHA-256 去重：同内容第二次返回 409） */
export function makeJpegBytes() {
  // 最小合法 JPEG（FF D8 ... FFD9）
  const bytes = new Uint8Array([
    0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01,
    0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0xff, 0xd9,
  ])
  return bytes
}

export function checkOk(res, name) {
  check(res, { [name]: (r) => r.status === 200 })
}
