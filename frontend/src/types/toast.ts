export type ToastType = 'info' | 'success' | 'error'

export interface Toast {
  id: number
  message: string
  type: ToastType
}
