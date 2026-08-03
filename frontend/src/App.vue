<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { App as AntApp, ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import enUS from 'ant-design-vue/es/locale/en_US'
import { useI18n } from 'vue-i18n'
import { applyCssVars, getThemeConfig, isSystemDark, watchColorScheme } from './theme'
import ErrorBoundary from './components/common/ErrorBoundary.vue'

const { locale } = useI18n()

// 明暗跟随系统，变化时同步 antd 主题 + 自定义 CSS 变量
const isDark = ref(isSystemDark())
let unsubscribe: (() => void) | null = null
onMounted(() => {
  unsubscribe = watchColorScheme((d) => {
    isDark.value = d
    applyCssVars(d)
  })
})
onUnmounted(() => {
  unsubscribe?.()
})

const antdLocale = computed(() => (locale.value === 'zh-CN' ? zhCN : enUS))
</script>

<template>
  <ConfigProvider :locale="antdLocale" :theme="getThemeConfig(isDark)">
    <AntApp>
      <ErrorBoundary>
        <RouterView />
      </ErrorBoundary>
    </AntApp>
  </ConfigProvider>
</template>
