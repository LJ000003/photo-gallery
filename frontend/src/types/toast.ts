export type ToastType = 'info' | 'success' | 'error'

export interface ToastAction {
  label: string
  onClick: () => void | Promise<void>
}

export interface Toast {
  id: number
  message: string
  type: ToastType
  action?: ToastAction
}
