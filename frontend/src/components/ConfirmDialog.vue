<script setup lang="ts">
import { ref, onMounted } from 'vue'
import gsap from 'gsap'

const props = defineProps<{
  title?: string
  message: string
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const backdrop = ref<HTMLElement | null>(null)
const content = ref<HTMLElement | null>(null)

onMounted(() => {
  gsap.fromTo(backdrop.value, { opacity: 0 }, { opacity: 1, duration: 0.2 })
  gsap.fromTo(
    content.value,
    { scale: 0.9, opacity: 0 },
    { scale: 1, opacity: 1, duration: 0.25, ease: 'expo.out' },
  )
})

function close(confirmed: boolean): void {
  gsap.to(content.value, {
    scale: 0.9,
    opacity: 0,
    duration: 0.15,
    ease: 'power1.in',
    onComplete: () => {
      if (confirmed) emit('confirm')
      else emit('cancel')
    },
  })
  gsap.to(backdrop.value, { opacity: 0, duration: 0.15 })
}
</script>

<template>
  <div ref="backdrop" class="confirm-overlay" @click="close(false)">
    <div ref="content" class="confirm-box" @click.stop>
      <h3>{{ props.title || '确认操作' }}</h3>
      <p>{{ props.message }}</p>
      <div class="confirm-actions">
        <button class="btn-confirm-cancel" @click="close(false)">取消</button>
        <button class="btn-confirm-ok" @click="close(true)">确定</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}
.confirm-box {
  background: rgba(20, 20, 40, 0.95);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 28px;
  width: 360px;
  max-width: 90vw;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}
.confirm-box h3 {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 12px;
  color: var(--text);
}
.confirm-box p {
  font-size: 14px;
  color: var(--text-dim);
  line-height: 1.5;
  margin-bottom: 24px;
}
.confirm-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
.btn-confirm-cancel {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-dim);
  font-size: 13px;
  padding: 9px 20px;
  border-radius: 8px;
}
.btn-confirm-cancel:hover {
  background: rgba(255, 255, 255, 0.12);
  color: var(--text);
}
.btn-confirm-ok {
  background: linear-gradient(135deg, var(--accent), var(--accent2));
  color: #fff;
  font-size: 13px;
  padding: 9px 20px;
  border-radius: 8px;
}
.btn-confirm-ok:hover {
  box-shadow: 0 4px 15px rgba(0, 212, 255, 0.4);
}
</style>
