<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useUiStore } from '../stores/ui'

const { t, tm } = useI18n()
const ui = useUiStore()

interface ShortcutItem {
  keys: string
  desc: string
}
interface UsageItem {
  title: string
  desc: string
}

// 数组/对象消息必须用 tm() 获取，t() 只会返回 key 本身
const shortcuts = tm('help.shortcuts') as unknown as ShortcutItem[]
const usage = tm('help.usage') as unknown as UsageItem[]
</script>

<template>
  <div v-if="ui.helpOpen" class="modal" @click.self="ui.helpOpen = false">
    <div class="modal-content modal-help">
      <h3 class="help-title">{{ t('help.title') }}</h3>

      <section class="help-section">
        <h4>{{ t('help.shortcutsTitle') }}</h4>
        <table class="help-table">
          <tbody>
            <tr v-for="(s, i) in shortcuts" :key="i">
              <td class="help-keys"><kbd>{{ s.keys }}</kbd></td>
              <td class="help-desc">{{ s.desc }}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="help-section">
        <h4>{{ t('help.usageTitle') }}</h4>
        <div v-for="(u, i) in usage" :key="i" class="help-usage">
          <strong>{{ u.title }}</strong>
          <p>{{ u.desc }}</p>
        </div>
      </section>

      <p class="help-note">{{ t('help.note') }}</p>

      <button class="modal-close" @click="ui.helpOpen = false">✕</button>
    </div>
  </div>
</template>

<style scoped>
.modal-help {
  width: min(560px, 90vw);
}
.help-title {
  margin: 0 0 16px;
  font-size: 20px;
}
.help-section {
  margin-bottom: 20px;
}
.help-section h4 {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--accent2);
  letter-spacing: 1px;
}
.help-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.help-table tr + tr td {
  border-top: 1px solid var(--border);
}
.help-table td {
  padding: 7px 4px;
  vertical-align: top;
}
.help-keys {
  width: 150px;
  white-space: nowrap;
}
kbd {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid var(--border);
  border-bottom-width: 2px;
  font-family: inherit;
  font-size: 12px;
  color: var(--text);
}
.help-desc {
  color: var(--text-dim);
}
.help-usage {
  margin-bottom: 12px;
}
.help-usage strong {
  font-size: 13px;
  color: var(--accent);
}
.help-usage p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-dim);
}
.help-note {
  margin: 0;
  padding-top: 12px;
  border-top: 1px solid var(--border);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}
</style>
