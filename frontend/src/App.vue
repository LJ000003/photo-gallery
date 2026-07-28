<script setup lang="ts">
import { ref, watch, onErrorCaptured } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import ErrorFallback from './components/ErrorFallback.vue'

const router = useRouter()

interface CapturedError {
  error: Error
  componentName: string
}

const captured = ref<CapturedError | null>(null)
const errorKey = ref(0)

onErrorCaptured((err, instance, info) => {
  let name = 'unknown'
  if (instance) {
    name = instance.$options?.name || instance.$.type?.name || 'App'
  }
  captured.value = {
    error: err instanceof Error ? err : new Error(String(err)),
    componentName: name,
  }
  console.error(`[ErrorBoundary] ${info}:`, err)
  return false
})

function handleRecover() {
  captured.value = null
  errorKey.value++
}

// 切换路由自动重置错误
watch(
  () => router.currentRoute.value.fullPath,
  () => {
    captured.value = null
  },
)
</script>

<template>
  <ErrorFallback
    v-if="captured"
    :error="captured.error"
    :component-name="captured.componentName"
    @recover="handleRecover"
  />
  <RouterView v-else :key="errorKey" />
</template>
