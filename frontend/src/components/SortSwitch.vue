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
