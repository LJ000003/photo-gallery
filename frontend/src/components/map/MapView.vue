<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUiStore } from '../../stores/ui'
import { usePhotoStore } from '../../stores/photo'
import { appendMediaParams } from '../../utils/token'
import { escapeHtml } from '../../utils/escape'
import { api } from '../../api'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import 'leaflet.markercluster'
import type { MapExifItem } from '../../types/view'
import type { Photo } from '../../types/photo'

/**
 * 地图：Leaflet + 高德瓦片，按视野范围拉取照片（debounce 300ms）
 * 标记为自绘 SVG（无 CDN 热链）；暗色主题下瓦片加滤镜缓释（已知取舍）
 */
const { t } = useI18n()
const ui = useUiStore()
const photo = usePhotoStore()

const loading = ref(true)
const mapContainer = ref<HTMLElement | null>(null)
let map: L.Map | null = null
let mapResizeObs: ResizeObserver | null = null
let fixSize: (() => void) | null = null
let clusterGroup: L.MarkerClusterGroup | null = null
const markersById = new Map<number, L.Marker>()
let fetchId = 0

// 删除同步：照片被删除后移除对应 marker（photo store 统一记录 deletedIds）
watch(
  () => [...photo.deletedIds],
  (ids) => {
    if (!clusterGroup || ids.length === 0) return
    for (const id of ids) {
      const marker = markersById.get(id)
      if (marker) {
        clusterGroup.removeLayer(marker)
        markersById.delete(id)
      }
    }
  },
)

/** 自绘 SVG 标记（主色圆点 + 白边），替代 unpkg 热链默认图标 */
const svgIcon = L.divIcon({
  className: 'photo-marker',
  html: `
    <svg width="26" height="34" viewBox="0 0 26 34" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M13 1C6.9 1 2 5.9 2 12c0 8 11 21 11 21s11-13 11-21C24 5.9 19.1 1 13 1z" fill="var(--c-accent, #2563eb)" stroke="#ffffff" stroke-width="1.6"/>
      <circle cx="13" cy="12" r="4" fill="#ffffff"/>
    </svg>
  `,
  iconSize: [26, 34],
  iconAnchor: [13, 33],
  popupAnchor: [0, -32],
})

/**
 * 查询范围下限：高 zoom 时视野极小（zoom 18 ≈ 50m），照片点会因鼠标锚点
 * 或坐标转换的微小偏差瞬间出视野，查询返回空后全部标签被清掉（用户感知
 * 为「放大后标签消失」）。钳制到 ~0.05°（约 zoom 13.5 的视野）后，
 * 高 zoom 下返回的点即使暂时在视野外也会保留，滚动回来依然可见可点。
 */
const MIN_QUERY_SPAN = 0.05

function boundsToParams(): string {
  if (!map) return ''
  const b = map.getBounds()
  const centerLat = (b.getNorth() + b.getSouth()) / 2
  const centerLng = (b.getEast() + b.getWest()) / 2
  const halfLat = Math.max((b.getNorth() - b.getSouth()) / 2, MIN_QUERY_SPAN / 2)
  const halfLng = Math.max((b.getEast() - b.getWest()) / 2, MIN_QUERY_SPAN / 2)
  return `swLat=${(centerLat - halfLat).toFixed(6)}&swLng=${(centerLng - halfLng).toFixed(6)}&neLat=${(centerLat + halfLat).toFixed(6)}&neLng=${(centerLng + halfLng).toFixed(6)}`
}

async function fetchMarkers(): Promise<void> {
  if (!map || !clusterGroup) return
  const myId = ++fetchId
  try {
    const res = await api(`/api/photos/map?${boundsToParams()}`)
    const json = await res.json()
    if (json.code !== 200 || myId !== fetchId) return
    const data: MapExifItem[] = json.data || []

    clusterGroup.clearLayers()
    markersById.clear()
    for (const exif of data) {
      if (exif.latitude == null || exif.longitude == null) continue
      const marker = L.marker([exif.latitude, exif.longitude], { icon: svgIcon })
      // 悬浮显示缩略图（tooltip），点击直接查看大图（灯箱）——不再用 popup
      marker.bindTooltip(
        `
        <div class="photo-popup">
          <img src="${escapeHtml(appendMediaParams(exif.photoThumbnail, exif))}" alt="${escapeHtml(exif.photoName)}" />
          <p>${escapeHtml(exif.photoName)}</p>
        </div>
        `,
        { direction: 'top', offset: [0, -10], opacity: 1 },
      )
      marker.on('click', () => {
        // mediaToken 必须带上：灯箱大图鉴权用 per-photo 签名，残缺对象不带会 401
        const partial = { id: exif.photoId, name: exif.photoName, mediaToken: exif.mediaToken } as Photo
        ui.openViewer(partial, [partial])
      })
      markersById.set(exif.photoId, marker)
      clusterGroup.addLayer(marker)
    }
  } catch (e) {
    console.error('Failed to load map data', e)
  } finally {
    if (myId === fetchId) loading.value = false
  }
}

let moveTimer: ReturnType<typeof setTimeout> | null = null
function onMoveEnd(): void {
  if (moveTimer) clearTimeout(moveTimer)
  moveTimer = setTimeout(() => void fetchMarkers(), 300)
}

function initMap(): void {
  if (!mapContainer.value) return
  const container = mapContainer.value as HTMLElement
  container.style.width = '100%'

  map = L.map(container, {
    center: [35, 105],
    zoom: 4,
    worldCopyJump: false,
    maxBounds: [
      [-85, -180],
      [85, 180],
    ],
    maxBoundsViscosity: 1.0,
    minZoom: 2,
  })

  L.tileLayer('https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}', {
    attribution: '&copy; 高德地图',
    subdomains: '1234',
    maxZoom: 18,
  }).addTo(map)

  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    {
      subdomains: '1234',
      maxZoom: 18,
      className: 'gaode-road-overlay',
    },
  ).addTo(map)

  clusterGroup = L.markerClusterGroup({
    maxClusterRadius: 60,
    // zoom >= 13 时彻底解除聚合（与 MIN_QUERY_SPAN 0.05° 视野衔接）：
    // 放大后标签不再折叠消失，单个 marker 始终可见可点
    disableClusteringAtZoom: 13,
    // 聚合在最高级用蜘蛛展开对 divIcon 有交互兼容问题，直接随 disableClusteringAtZoom 失效
    spiderfyOnMaxZoom: false,
    showCoverageOnHover: false,
    zoomToBoundsOnClick: true,
  })

  map.addLayer(clusterGroup)
  map.on('moveend', onMoveEnd)

  if (mapContainer.value) {
    mapResizeObs = new ResizeObserver(() => {
      map?.invalidateSize({ animate: false })
    })
    mapResizeObs.observe(mapContainer.value)
  }

  fixSize = () => {
    if (mapContainer.value) mapContainer.value.style.width = '100%'
    map?.invalidateSize({ animate: false })
  }
  requestAnimationFrame(() => requestAnimationFrame(fixSize!))
  setTimeout(fixSize, 100)
  setTimeout(fixSize, 300)
  setTimeout(fixSize, 600)
  window.addEventListener('resize', fixSize)

  void fetchMarkers()
}

onMounted(() => {
  initMap()
})

onUnmounted(() => {
  mapResizeObs?.disconnect()
  if (fixSize) {
    window.removeEventListener('resize', fixSize)
    fixSize = null
  }
  if (moveTimer) clearTimeout(moveTimer)
  map?.remove()
  map = null
})
</script>

<template>
  <div class="map-view">
    <h2 class="page-title">{{ t('nav.map') }}</h2>
    <div class="map-wrap">
      <div v-if="loading" class="map-loading" role="status">
        <span class="loading-spinner"></span>
      </div>
      <div ref="mapContainer" class="map-container"></div>
    </div>
  </div>
</template>

<style scoped>
.map-view {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}
.page-title {
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--c-text);
  margin-bottom: 16px;
}
.map-wrap {
  min-height: 400px;
  width: 100%;
  position: relative;
}
.map-loading {
  position: absolute;
  inset: 0;
  z-index: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--c-surface);
  border-radius: 16px;
}
.loading-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--c-surface-2);
  border-top-color: var(--c-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.map-container {
  width: 100%;
  height: calc(100dvh - 200px);
  min-height: 420px;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--c-border);
}

/* Leaflet 内部 DOM 不受 scoped 约束，用 :deep 覆盖 */
.map-container :deep(.photo-marker) {
  background: none;
  border: none;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
}
/* 悬浮缩略图卡片：去掉 Leaflet 默认白框，图片即卡片；不拦截鼠标（hover 时可直达 marker） */
.map-container :deep(.leaflet-tooltip) {
  background: transparent;
  border: none;
  box-shadow: none;
  padding: 0;
  pointer-events: none;
}
.map-container :deep(.photo-popup) {
  text-align: center;
  max-width: 180px;
}
.map-container :deep(.photo-popup img) {
  width: 100%;
  max-width: 160px;
  height: auto;
  aspect-ratio: 3 / 2;
  object-fit: cover;
  border-radius: 8px;
  margin-bottom: 6px;
}
.map-container :deep(.photo-popup p) {
  margin: 0;
  font-size: 12px;
  color: var(--c-text);
  word-break: break-all;
}
/* 悬浮卡片内名字：深色胶囊，浅色瓦片上可读 */
.map-container :deep(.leaflet-tooltip .photo-popup p) {
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  border-radius: 999px;
  padding: 2px 8px;
  margin-top: 4px;
  display: inline-block;
  max-width: 160px;
}
/* 聚合点配色（主色） */
.map-container :deep(.marker-cluster div) {
  background: color-mix(in srgb, var(--c-accent) 55%, transparent);
  border: 2px solid #fff;
  border-radius: 50%;
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}
.map-container :deep(.marker-cluster-small div),
.map-container :deep(.marker-cluster-medium div),
.map-container :deep(.marker-cluster-large div) {
  background: color-mix(in srgb, var(--c-accent) 55%, transparent);
}
.map-container :deep(.gaode-road-overlay) {
  mix-blend-mode: multiply;
}
/* 暗色下瓦片整体压暗（已知取舍：高德瓦片是浅色图源） */
@media (prefers-color-scheme: dark) {
  .map-container {
    filter: brightness(0.92) saturate(0.85);
  }
}

@media (max-width: 768px) {
  .map-view {
    padding: 14px 12px;
  }
  .map-container {
    height: calc(100dvh - 190px);
    min-height: 300px;
    border-radius: 12px;
  }
}
</style>
