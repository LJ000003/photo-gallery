import { defineStore } from 'pinia'
import { ref, type Ref } from 'vue'
import type { Toast, ToastAction, ToastType } from '../types/toast'

export const useToastStore = defineStore('toast', () => {
  const toasts: Ref<Toast[]> = ref([])
  const timers = new Map<number, ReturnType<typeof setTimeout>>()
  let id = 0

  function add(
    message: string,
    type: ToastType = 'info',
    duration: number = 3000,
    action?: ToastAction,
  ): void {
    const item: Toast = { id: ++id, message, type, action }
    toasts.value.push(item)
    if (duration > 0) {
      const timer = setTimeout(() => {
        remove(item.id)
      }, duration)
      timers.set(item.id, timer)
    }
  }

  function remove(rid: number): void {
    const timer = timers.get(rid)
    if (timer) {
      clearTimeout(timer)
      timers.delete(rid)
    }
    toasts.value = toasts.value.filter((t) => t.id !== rid)
  }

  function success(msg: string): void {
    add(msg, 'success')
  }
  function error(msg: string): void {
    add(msg, 'error')
  }
  function info(msg: string): void {
    add(msg, 'info')
  }

  return { toasts, success, error, info, add, remove }
})
