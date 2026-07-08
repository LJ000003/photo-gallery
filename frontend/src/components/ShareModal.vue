<script setup lang="ts">
defineProps<{
  photoCount: number
  loading: boolean
  url: string
}>()

const emit = defineEmits<{
  close: []
  copy: []
}>()
</script>

<template>
  <div class="modal" @click.self="emit('close')">
    <div class="modal-content modal-small">
      <h3>分享 {{ photoCount }} 张照片</h3>
      <p class="share-hint">链接 7 天内有效，拿到链接的人可直接查看</p>
      <div v-if="loading" class="share-loading">生成中...</div>
      <div v-else-if="url" class="share-row">
        <input
          :value="url"
          readonly
          class="share-input"
          @focus="($event.target as HTMLInputElement).select()"
        />
        <button class="btn-primary" @click="emit('copy')">复制链接</button>
      </div>
      <button class="modal-close" @click="emit('close')">✕</button>
    </div>
  </div>
</template>

<style scoped>
.share-hint {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 14px;
}
.share-loading {
  text-align: center;
  color: var(--accent);
  padding: 20px 0;
}
.share-row {
  display: flex;
  gap: 10px;
}
.share-input {
  flex: 1;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid var(--border);
  color: var(--text);
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  outline: none;
}
.share-input:focus {
  border-color: var(--accent2);
}
</style>
