<script setup lang="ts">
export interface SortOption {
  key: string
  label: string
}

defineProps<{
  options: SortOption[]
  modelValue: string
  order: string
}>()

const emit = defineEmits<{
  toggle: [key: string]
}>()
</script>

<template>
  <div class="sort-switch">
    <span class="sort-label">{{ $t('gallery.sortBy') }}：</span>
    <div class="sort-track" :class="`sort-${options.length}cols`">
      <div
        class="sort-slider"
        :style="{ transform: `translateX(${options.findIndex((o) => o.key === modelValue) * 100}%)` }"
      ></div>
      <button
        v-for="opt in options"
        :key="opt.key"
        class="sort-opt"
        :class="{ active: modelValue === opt.key }"
        @click="emit('toggle', opt.key)"
      >
        {{ $t(opt.label) }}
        <span v-if="modelValue === opt.key" class="sort-arrows">
          <i
            class="iconfont icon-jiantou_qiehuanxiangshang_o sort-arrow-down"
            :class="{ active: order === 'asc' }"
          ></i>
          <i
            class="iconfont icon-jiantou_qiehuanxiangshang_o"
            :class="{ active: order === 'desc' }"
          ></i>
        </span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.sort-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sort-label {
  font-size: 13px;
  color: var(--text-dim);
  white-space: nowrap;
}
.sort-track {
  position: relative;
  display: flex;
  width: 272px;
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
}
.sort-track.sort-2cols {
  width: 182px;
}
.sort-slider {
  position: absolute;
  top: 0;
  left: 0;
  width: 90px;
  height: 100%;
  background: rgba(0, 212, 255, 0.12);
  border-radius: 10px;
  transition: transform 0.3s ease;
  pointer-events: none;
}
.sort-opt {
  position: relative;
  z-index: 1;
  width: 90px;
  flex: 0 0 90px;
  padding: 9px 0;
  font-size: 14px;
  color: var(--text-dim);
  background: transparent;
  border: none;
  border-right: 1px solid var(--border);
  cursor: pointer;
  transition: color 0.3s;
  text-align: center;
}
.sort-opt:last-child {
  border-right: none;
}
.sort-opt.active {
  color: var(--accent);
}
.sort-opt:hover {
  color: var(--text);
}
.sort-arrows {
  position: absolute;
  display: inline-flex;
  font-size: 14px;
  top: 50%;
  transform: translateY(-50%);
}
.sort-arrows i {
  display: inline-block;
  opacity: 0.25;
  transition: opacity 0.2s;
}
.sort-arrows i:last-child {
  margin-left: -8px;
}
.sort-arrows i.active {
  opacity: 1;
  color: var(--accent);
}
.sort-arrow-down {
  transform: rotate(180deg);
}

@media (max-width: 768px) {
  .sort-switch {
    margin-left: 0;
  }
  .sort-track {
    width: 227px;
  }
  .sort-track.sort-2cols {
    width: 152px;
  }
  .sort-opt {
    width: 75px;
    flex: 0 0 75px;
    font-size: 12px;
  }
  .sort-slider {
    width: 75px;
  }
}
</style>
