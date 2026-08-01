<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUiStore } from '../stores/ui'

const { t } = useI18n()
const ui = useUiStore()

const clicks = ref(0)
const easterEgg = ref(false)

function onClick(): void {
  clicks.value++
  if (clicks.value >= 30) easterEgg.value = true
}
</script>

<template>
  <header class="header">
    <h1 :class="{ rgb: easterEgg }" @click="onClick">照片管理器</h1>
    <button class="help-btn" :title="t('help.button')" @click="ui.helpOpen = true">
      <span>?</span>
    </button>
  </header>
</template>

<style scoped>
.help-btn {
  position: absolute;
  top: 50%;
  right: 24px;
  transform: translateY(-50%);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--glass);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--border);
  color: var(--text-dim);
  font-size: 18px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.3s;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.help-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  box-shadow: var(--glow-cyan);
}

.header h1.rgb {
  background: linear-gradient(90deg, #f00, #f80, #ff0, #0f0, #08f, #80f, #f00);
  background-size: 300% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: rgb-sweep 2s linear infinite;
}
@keyframes rgb-sweep {
  0% {
    background-position: 0% 50%;
  }
  100% {
    background-position: 200% 50%;
  }
}

@media (max-width: 768px) {
  .help-btn {
    right: 12px;
    width: 32px;
    height: 32px;
    font-size: 16px;
  }
}
</style>
