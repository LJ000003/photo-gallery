<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import gsap from 'gsap'

const emit = defineEmits<{
  unlocked: []
}>()

const TARGET: string[] = [
  'up',
  'up',
  'down',
  'down',
  'left',
  'right',
  'left',
  'right',
  'B',
  'A',
  'B',
  'A',
]
const pressed = ref<string[]>([])
const activeBtn = ref<string | null>(null)
const shake = ref(false)
const success = ref(false)
const history = ref<string[]>([])

function press(dir: string): void {
  if (success.value) return
  activeBtn.value = dir
  setTimeout(() => {
    activeBtn.value = null
  }, 150)

  pressed.value.push(dir)

  const idx = pressed.value.length - 1
  if (pressed.value[idx] !== TARGET[idx]) {
    shake.value = true
    setTimeout(() => {
      shake.value = false
    }, 500)
    pressed.value = []
    history.value = []
    return
  }

  history.value = [...pressed.value]

  if (pressed.value.length === TARGET.length) {
    success.value = true
    setTimeout(() => {
      emit('unlocked')
    }, 1500)
  }
}

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
    <div class="arcade-screen">
      <div class="arcade-title">{{ success ? 'CONGRATULATIONS' : 'PRESS START' }}</div>
      <div v-if="success" class="hp-popup">HP +30 ♥</div>
      <div v-else class="progress-bar">
        <span
          v-for="i in TARGET.length"
          :key="i"
          class="progress-dot"
          :class="{
            filled: history.length >= i,
            wrong: shake && i === history.length + 1,
          }"
        >
          {{
            history[i - 1] === 'up'
              ? '↑'
              : history[i - 1] === 'down'
                ? '↓'
                : history[i - 1] === 'left'
                  ? '←'
                  : history[i - 1] === 'right'
                    ? '→'
                    : history[i - 1] || '·'
          }}
        </span>
      </div>
    </div>

    <div class="arcade-panel">
      <div class="dpad">
        <button
          class="arcade-btn dir up"
          :class="{ active: activeBtn === 'up' }"
          @pointerdown.prevent="press('up')"
        >
          ▲
        </button>
        <button
          class="arcade-btn dir left"
          :class="{ active: activeBtn === 'left' }"
          @pointerdown.prevent="press('left')"
        >
          ◀
        </button>
        <div class="dpad-center"></div>
        <button
          class="arcade-btn dir right"
          :class="{ active: activeBtn === 'right' }"
          @pointerdown.prevent="press('right')"
        >
          ▶
        </button>
        <button
          class="arcade-btn dir down"
          :class="{ active: activeBtn === 'down' }"
          @pointerdown.prevent="press('down')"
        >
          ▼
        </button>
      </div>

      <div class="ab-btns">
        <button
          class="arcade-btn ab b-btn"
          :class="{ active: activeBtn === 'B' }"
          @pointerdown.prevent="press('B')"
        >
          B
        </button>
        <button
          class="arcade-btn ab a-btn"
          :class="{ active: activeBtn === 'A' }"
          @pointerdown.prevent="press('A')"
        >
          A
        </button>
      </div>
    </div>

    <div class="arcade-footer">也支持键盘：↑ ↓ ← → 方向键 + B / A</div>
  </div>
</template>

<style scoped>
.arcade-gate {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 32px;
  background: radial-gradient(ellipse at center, #0a0a14 0%, #020208 100%);
  font-family: 'Courier New', monospace;
  user-select: none;
  -webkit-user-select: none;
}
.arcade-screen {
  text-align: center;
}
.arcade-title {
  font-size: 48px;
  font-weight: 900;
  letter-spacing: 8px;
  color: #00ff88;
  text-shadow:
    0 0 20px #00ff8866,
    0 0 60px #00ff8833,
    0 0 100px #00ff8822;
  animation: title-pulse 2s ease-in-out infinite;
}
@keyframes title-pulse {
  0%,
  100% {
    opacity: 0.8;
  }
  50% {
    opacity: 1;
  }
}
.arcade-hint {
  margin-top: 16px;
  font-size: 18px;
  color: #ffffff66;
  letter-spacing: 4px;
}
.progress-bar {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 20px;
}
.progress-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid #ffffff15;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #ffffff30;
  transition: all 0.2s;
}
.progress-dot.filled {
  border-color: #00ff88;
  color: #00ff88;
  background: #00ff8810;
  box-shadow: 0 0 12px #00ff8840;
}
.progress-dot.wrong {
  border-color: #ff3366;
  color: #ff3366;
  background: #ff336610;
  box-shadow: 0 0 12px #ff336640;
}

/* D-Pad */
.arcade-panel {
  display: flex;
  align-items: center;
  gap: 80px;
}
.dpad {
  display: grid;
  grid-template-columns: 70px 70px 70px;
  grid-template-rows: 70px 70px 70px;
  gap: 0;
}
.dpad-center {
  grid-column: 2;
  grid-row: 2;
  background: #ffffff06;
  border-radius: 50%;
}
.arcade-btn {
  border: none;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.1s;
  outline: none;
}
.arcade-btn.dir {
  background: linear-gradient(180deg, #2a2a3a, #151520);
  border: 2px solid #3a3a4a;
  color: #888;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  line-height: 1;
}
.arcade-btn.dir:hover {
  border-color: #666;
  color: #ccc;
}
.arcade-btn.dir:active,
.arcade-btn.dir.active {
  background: linear-gradient(180deg, #1a3a2a, #0a1a10);
  border-color: #00ff88;
  color: #00ff88;
  box-shadow:
    0 0 20px #00ff8840,
    inset 0 0 20px #00ff8810;
}
.dpad .up {
  grid-column: 2;
  grid-row: 1;
  border-radius: 12px 12px 4px 4px;
}
.dpad .left {
  grid-column: 1;
  grid-row: 2;
  border-radius: 12px 4px 4px 12px;
}
.dpad .right {
  grid-column: 3;
  grid-row: 2;
  border-radius: 4px 12px 12px 4px;
}
.dpad .down {
  grid-column: 2;
  grid-row: 3;
  border-radius: 4px 4px 12px 12px;
}

/* A/B */
.ab-btns {
  display: flex;
  gap: 24px;
  align-items: center;
}
.arcade-btn.ab {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  font-size: 22px;
  font-weight: 900;
  letter-spacing: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  line-height: 1;
}
.b-btn {
  background: linear-gradient(180deg, #4a3a2a, #2a1a10);
  border: 3px solid #ff880066;
  color: #ff8800;
  box-shadow: 0 0 20px #ff880020;
}
.b-btn:active,
.b-btn.active {
  border-color: #ff8800;
  box-shadow: 0 0 30px #ff880060;
}
.a-btn {
  background: linear-gradient(180deg, #2a3a4a, #101a2a);
  border: 3px solid #ff336666;
  color: #ff3366;
  box-shadow: 0 0 20px #ff336620;
}
.a-btn:active,
.a-btn.active {
  border-color: #ff3366;
  box-shadow: 0 0 30px #ff336660;
}

.arcade-footer {
  color: #ffffff20;
  font-size: 13px;
  letter-spacing: 2px;
}

/* 输错抖动 */
.arcade-gate.shake {
  animation: gate-shake 0.5s ease;
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
  color: #ffdd00;
  text-shadow:
    0 0 30px #ffdd0066,
    0 0 80px #ffdd0033;
  animation: title-win 0.4s ease 4;
}
@keyframes title-win {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.6;
    transform: scale(1.15);
  }
}
.hp-popup {
  margin-top: 16px;
  font-size: 22px;
  font-weight: 900;
  color: #ff4466;
  text-shadow:
    0 0 12px #ff446688,
    0 0 30px #ff446644;
  animation: hp-pop 0.6s ease;
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
    gap: 20px;
    padding: 24px 12px;
  }
  .arcade-title {
    font-size: 22px;
    letter-spacing: 3px;
    text-shadow: 0 0 10px #00ff8866;
    animation: title-pulse-simple 2s ease-in-out infinite;
  }
  @keyframes title-pulse-simple {
    0%,
    100% {
      opacity: 0.9;
    }
    50% {
      opacity: 1;
    }
  }
  .arcade-panel {
    gap: 24px;
  }
  .dpad {
    grid-template-columns: 56px 56px 56px;
    grid-template-rows: 56px 56px 56px;
  }
  .arcade-btn.dir {
    font-size: 18px;
  }
  .arcade-btn.ab {
    width: 58px;
    height: 58px;
    font-size: 18px;
  }
  .ab-btns {
    gap: 18px;
  }
  .progress-bar {
    display: grid;
    grid-template-columns: repeat(4, 28px);
    grid-template-rows: repeat(3, 28px);
    gap: 6px;
    justify-content: center;
  }
  .progress-dot {
    width: 28px;
    height: 28px;
    font-size: 13px;
  }
  .hp-popup {
    font-size: 18px;
  }
  .arcade-footer {
    font-size: 11px;
    margin-top: 8px;
  }
  .arcade-gate.success .arcade-title {
    text-shadow: 0 0 15px #ffdd0066;
    animation: title-win-simple 0.5s ease 3;
  }
  @keyframes title-win-simple {
    0%,
    100% {
      opacity: 1;
      transform: scale(1);
    }
    50% {
      opacity: 0.7;
      transform: scale(1.08);
    }
  }
  .arcade-btn.dir.active,
  .arcade-btn.dir:active {
    box-shadow:
      0 0 10px #00ff8840,
      inset 0 0 10px #00ff8810;
  }
  .arcade-btn {
    transform: translateZ(0);
  }
}
</style>
