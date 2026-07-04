import { createApp, h } from 'vue'
import ConfirmDialog from './components/ConfirmDialog.vue'

export function useConfirm(): (message: string, title?: string) => Promise<boolean> {
  return (message: string, title: string = '确认操作'): Promise<boolean> => {
    return new Promise((resolve) => {
      const mount = document.createElement('div')
      document.body.appendChild(mount)

      const app = createApp({
        render() {
          return h(ConfirmDialog, {
            title,
            message,
            onConfirm: () => { destroy(); resolve(true) },
            onCancel: () => { destroy(); resolve(false) }
          })
        }
      })

      function destroy() {
        app.unmount()
        document.body.removeChild(mount)
      }

      app.mount(mount)
    })
  }
}
