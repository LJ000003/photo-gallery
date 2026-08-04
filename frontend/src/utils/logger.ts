/**
 * 统一错误日志入口 —— 收敛全项目散落的 console.error，预留上报扩展点。
 * 模块捕获异常后调用 logError(err, context)，不要直接 console.error。
 */
export function logError(err: unknown, context?: string): void {
  if (context) {
    console.error(`[${context}]`, err)
  } else {
    console.error(err)
  }
}
