// k6 共享工具：Konami Challenge-Response 解锁（与前端 requestToken 同构）
import http from 'k6/http'
// 注意：open() 是 k6 全局函数（init context），不能从 'k6' 模块导入；
// 路径相对脚本所在目录（scripts/k6/）解析，与执行时的 cwd 无关

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

/** 真实可解码 JPEG 模板（scripts/k6/test.jpg，4x4 JFIF baseline，ImageIO 可解码） */
// 第二个参数 'b'：二进制模式返回 ArrayBuffer（默认按 UTF-8 读成 string，二进制会丢字节）
const BASE_JPEG = new Uint8Array(open('test.jpg', 'b'))

/**
 * 生成可解码 JPEG：基于模板改动熵编码区 2 个字节（EOI 之前），
 * seed 不同 → 文件字节不同 → SHA-256 不同 → 全部真实新照片（测写入吞吐而非去重路径）。
 * 只动熵数据不碰段结构（SOI/APP0/SOF/SOS），ImageIO 解码不受影响。
 */
export function makeJpegBytes(seed) {
  const bytes = BASE_JPEG.slice()
  bytes[bytes.length - 3] = (bytes[bytes.length - 3] + seed) & 0xff
  bytes[bytes.length - 4] = (bytes[bytes.length - 4] + (seed >> 8)) & 0xff
  // http.file() 只接受 string 或 ArrayBuffer，Uint8Array 会报 "invalid type []uint8"
  return bytes.buffer
}
