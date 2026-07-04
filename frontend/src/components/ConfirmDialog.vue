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
