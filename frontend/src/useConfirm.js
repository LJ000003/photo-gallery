import { createApp, h } from 'vue';
import ConfirmDialog from './components/ConfirmDialog.vue';

export function useConfirm() {
  return (message, title = '确认操作') => {
    return new Promise((resolve) => {
      const mount = document.createElement('div');
      document.body.appendChild(mount);

      const app = createApp({
        render() {
          return h(ConfirmDialog, {
            title,
            message,
            onConfirm: () => { destroy(); resolve(true); },
            onCancel: () => { destroy(); resolve(false); }
          });
        }
      });

      function destroy() {
        app.unmount();
        document.body.removeChild(mount);
      }

      app.mount(mount);
    });
  };
}
