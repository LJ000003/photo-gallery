<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { App as AntApp, ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import enUS from 'ant-design-vue/es/locale/en_US'
import { useI18n } from 'vue-i18n'
import { applyCssVars, getThemeConfig, isSystemDark, watchColorScheme } from './theme'
import i18n from './i18n'
import { useUiStore } from './stores/ui'
import ErrorBoundary from './components/common/ErrorBoundary.vue'

const { locale } = useI18n()
const route = useRoute()

// 语言切换后立即重算 document.title（router.beforeEach 只在路由变化时执行）
watch(locale, () => {
  const t = i18n.global.t
  if (route.path.startsWith('/share/')) {
    document.title = t('share.viewer')
    return
  }
  if (!useUiStore().unlocked) {
    document.title = `${t('app.name')} · ${t('auth.locked')}`
    return
  }
  const key = route.meta.titleKey as string | undefined
  document.title = key ? `${t(key)} · ${t('app.name')}` : t('app.name')
})

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
