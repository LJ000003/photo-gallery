<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import '@fontsource/press-start-2p'
import ArcadePanel from './ArcadePanel.vue'

const { t } = useI18n()

const KEY_COUNT = 12

const props = defineProps<{
  resetTrigger: number
  successTrigger: number
}>()

const emit = defineEmits<{
  unlocked: [keys: string[]]
}>()

const pressed = ref<string[]>([])
const activeBtn = ref<string | null>(null)
const shake = ref(false)
const verifying = ref(false)
const success = ref(false)
const history = ref<string[]>([])

function press(dir: string): void {
  if (success.value || verifying.value || shake.value) return
  activeBtn.value = dir
  setTimeout(() => {
    activeBtn.value = null
  }, 150)

  pressed.value.push(dir)
  history.value = [...pressed.value]

  if (pressed.value.length === KEY_COUNT) {
    verifying.value = true
    emit('unlocked', [...pressed.value])
  }
}

// 后端验证通过，父组件递增 successTrigger
watch(
  () => props.successTrigger,
  () => {
    verifying.value = false
    success.value = true
  },
)

// 后端验证失败，父组件递增 resetTrigger
watch(
  () => props.resetTrigger,
  () => {
    verifying.value = false
    success.value = false
    shake.value = true
    setTimeout(() => {
      shake.value = false
      pressed.value = []
      history.value = []
    }, 600)
  },
)

function handleKey(e: KeyboardEvent): void {
  const map: Record<string, string> = {
    ArrowUp: 'up',
    ArrowDown: 'down',
    ArrowLeft: 'left',
    ArrowRight: 'right',
    b: 'B',
    B: 'B',
    a: 'A',
    A: 'A',
  }
  const dir = map[e.key]
  if (dir) {
    e.preventDefault()
    press(dir)
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKey)
})
onUnmounted(() => {
  window.removeEventListener('keydown', handleKey)
})
</script>

<template>
  <div class="arcade-gate" :class="{ shake, success }">
    <!-- CRT 扫描线 -->
    <div class="scanlines" aria-hidden="true"></div>

    <div class="arcade-screen">
      <div class="famicom-badge">FAMICOM</div>
      <div class="arcade-title">
        {{ success ? 'CONGRATULATIONS' : verifying ? 'CHECKING...' : 'PRESS START' }}
      </div>
      <div v-if="success" class="hp-popup">HP +30 &#9829;</div>
      <div v-if="verifying" class="hp-popup verifying">...</div>
      <div v-if="!success && !verifying" class="progress-bar">
        <span
          v-for="i in KEY_COUNT"
          :key="i"
          class="progress-dot"
          :class="{
            filled: history.length >= i,
            wrong: shake,
          }"
        >
          {{
            history[i - 1] === 'up'
              ? '▲'
              : history[i - 1] === 'down'
                ? '▼'
                : history[i - 1] === 'left'
                  ? '◀'
                  : history[i - 1] === 'right'
                    ? '▶'
                    : history[i - 1] || '·'
          }}
        </span>
      </div>
    </div>

    <ArcadePanel :active-btn="activeBtn" @press="press" />

    <div class="arcade-footer">{{ t('auth.keyboardHint') }}</div>
  </div>
</template>

<style scoped>
/* 千禧年红白机像素风：Famicom 红 #e60012 + 米白 + 近黑，Press Start 2P 像素字体 */
.arcade-gate {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 40px;
  background: #101014;
  font-family: 'Press Start 2P', monospace;
  user-select: none;
  -webkit-user-select: none;
  color: #f0e9d8;
}

/* CRT 扫描线 */
.scanlines {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    rgba(0, 0, 0, 0) 0px,
    rgba(0, 0, 0, 0) 3px,
    rgba(0, 0, 0, 0.22) 3px,
    rgba(0, 0, 0, 0.22) 4px
  );
}

.arcade-screen {
  text-align: center;
  position: relative;
}

.famicom-badge {
  font-size: 10px;
  letter-spacing: 6px;
  color: #f0e9d8;
  opacity: 0.55;
  margin-bottom: 28px;
}

.arcade-title {
  font-size: 26px;
  letter-spacing: 4px;
  color: #ffffff;
  text-shadow: 4px 4px 0 rgba(230, 0, 18, 0.9);
  animation: title-pulse 1.6s steps(2) infinite;
}
@keyframes title-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.55;
  }
}

/* 进度点：像素方块，实心红 = 已输入 */
.progress-bar {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 28px;
}
.progress-dot {
  width: 32px;
  height: 32px;
  border: 3px solid #3a3a42;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #5a5a64;
  transition: all 0.15s steps(2);
}
.progress-dot.filled {
  border-color: #e60012;
  background: #e60012;
  color: #101014;
}
.progress-dot.wrong {
  border-color: #f0e9d8;
  background: #f0e9d8;
  color: #101014;
}

.arcade-footer {
  color: #5a5a64;
  font-size: 9px;
  letter-spacing: 2px;
}

/* 输错抖动 */
.arcade-gate.shake {
  animation: gate-shake 0.5s steps(8);
}
@keyframes gate-shake {
  0%,
  100% {
    transform: translateX(0);
  }
  10% {
    transform: translateX(-8px);
  }
  30% {
    transform: translateX(8px);
  }
  50% {
    transform: translateX(-6px);
  }
  70% {
    transform: translateX(6px);
  }
  90% {
    transform: translateX(-2px);
  }
}

/* 成功 */
.arcade-gate.success .arcade-title {
  color: #ff2e3d;
  text-shadow: 4px 4px 0 #101014;
  animation: title-win 0.4s steps(2) 4;
}
@keyframes title-win {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(1.12);
  }
}
.hp-popup {
  margin-top: 20px;
  font-size: 14px;
  color: #ff2e3d;
  text-shadow: 2px 2px 0 #101014;
  animation: hp-pop 0.6s steps(6);
}
.hp-popup.verifying {
  color: #f0e9d8;
  font-size: 14px;
  letter-spacing: 6px;
  animation: verifying-blink 0.5s steps(2) infinite alternate;
}
@keyframes verifying-blink {
  from {
    opacity: 0.3;
  }
  to {
    opacity: 1;
  }
}
@keyframes hp-pop {
  0% {
    opacity: 0;
    transform: translateY(20px) scale(0.5);
  }
  60% {
    opacity: 1;
    transform: translateY(-4px) scale(1.1);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 768px) {
  .arcade-gate {
    gap: 32px;
    padding: 24px 12px;
  }
  .arcade-title {
    font-size: 14px;
    letter-spacing: 2px;
    text-shadow: 2px 2px 0 rgba(230, 0, 18, 0.9);
  }
  .famicom-badge {
    font-size: 8px;
    margin-bottom: 20px;
  }
  .progress-bar {
    display: grid;
    grid-template-columns: repeat(4, 26px);
    grid-template-rows: repeat(3, 26px);
    gap: 6px;
    justify-content: center;
    margin-top: 22px;
  }
  .progress-dot {
    width: 26px;
    height: 26px;
    font-size: 10px;
  }
  .arcade-footer {
    font-size: 8px;
  }
}
</style>
