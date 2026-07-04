<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useUiStore } from '../stores/ui'
import { api } from '../api'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import 'leaflet.markercluster'
import type { MapExifItem } from '../types/view'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
delete (L.Icon.Default.prototype as any)._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
})

const emit = defineEmits<{ view: [p: object] }>()
const ui = useUiStore()

const items = ref<MapExifItem[]>([])
const loading = ref(true)
const mapContainer = ref<HTMLElement | null>(null)
let map: L.Map | null = null
let mapResizeObs: ResizeObserver | null = null

function tokenParam(): string {
  const t = ui.token
  return t ? `?token=${t}` : ''
}

onMounted(async () => {
  try {
    const res = await api('/api/photos/map')
    const data = await res.json()
    if (data.code === 200) items.value = data.data || []
  } catch (e) {
    console.error('Failed to load map data', e)
  } finally {
    loading.value = false
    await nextTick()
    if (items.value.length > 0) initMap()
  }
})

function initMap(): void {
  if (!mapContainer.value) return
  map = L.map(mapContainer.value, {
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
  L.tileLayer(
    'https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
    {
      attribution: '&copy; 高德地图',
      subdomains: '1234',
      maxZoom: 18,
    },
  ).addTo(map)

  const clusterGroup = L.markerClusterGroup({
    maxClusterRadius: 60,
    spiderfyOnMaxZoom: true,
    showCoverageOnHover: false,
    zoomToBoundsOnClick: true,
  })

  const markers: L.LatLngTuple[] = []
  for (const exif of items.value) {
    if (exif.latitude == null || exif.longitude == null) continue
    const latlng: L.LatLngTuple = [exif.latitude, exif.longitude]
    markers.push(latlng)
    const marker = L.marker(latlng)
    marker.bindPopup(`
      <div style="text-align:center;max-width:200px;">
        <img src="${exif.photoThumbnail}${tokenParam()}" alt="${exif.photoName}"
          style="width:100%;max-width:160px;height:auto;aspect-ratio:3/2;object-fit:cover;border-radius:6px;margin-bottom:4px;" />
        <p style="margin:0;font-size:13px;word-break:break-all;">${exif.photoName}</p>
      </div>
    `)
    marker.on('click', () => {
      emit('view', { id: exif.photoId, name: exif.photoName })
    })
    clusterGroup.addLayer(marker)
  }

  map.addLayer(clusterGroup)

  if (markers.length > 0) {
    map.fitBounds(markers, { padding: [40, 40] })
  }

  if (mapContainer.value) {
    mapResizeObs = new ResizeObserver(() => {
      map?.invalidateSize()
    })
    mapResizeObs.observe(mapContainer.value)
  }

  requestAnimationFrame(() => {
    map?.invalidateSize()
  })
}

onUnmounted(() => {
  mapResizeObs?.disconnect()
  map?.remove()
  map = null
})
</script>

<template>
  <div class="map-wrap">
    <div v-if="loading" class="map-loading">加载中...</div>
    <div v-else-if="items.length === 0" class="map-empty">
      没有包含 GPS 位置信息的照片，请上传带有 EXIF GPS 数据的 JPEG/WebP 图片
    </div>
    <div ref="mapContainer" class="map-container"></div>
  </div>
</template>
