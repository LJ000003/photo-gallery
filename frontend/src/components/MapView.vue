<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useUiStore } from '../stores/ui'
import { tokenParam } from '../utils/token'
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

const loading = ref(true)
const mapContainer = ref<HTMLElement | null>(null)
let map: L.Map | null = null
let mapResizeObs: ResizeObserver | null = null
let fixSize: (() => void) | null = null
let clusterGroup: L.MarkerClusterGroup | null = null
let fetchId = 0

function boundsToParams(): string {
  if (!map) return ''
  const b = map.getBounds()
  return `swLat=${b.getSouthWest().lat.toFixed(6)}&swLng=${b.getSouthWest().lng.toFixed(6)}&neLat=${b.getNorthEast().lat.toFixed(6)}&neLng=${b.getNorthEast().lng.toFixed(6)}`
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
    const markers: L.LatLngTuple[] = []
    for (const exif of data) {
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
      clusterGroup!.addLayer(marker)
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
  moveTimer = setTimeout(() => fetchMarkers(), 300)
}

onMounted(async () => {
  await nextTick()
  initMap()
})

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

  L.tileLayer(
    'https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
    {
      attribution: '&copy; 高德地图',
      subdomains: '1234',
      maxZoom: 18,
    },
  ).addTo(map)

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
    spiderfyOnMaxZoom: true,
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
    const mainContent = mapContainer.value.closest('.main-content')
    if (mainContent) mapResizeObs.observe(mainContent)
  }

  fixSize = () => {
    if (mapContainer.value) {
      mapContainer.value.style.width = '100%'
    }
    map?.invalidateSize({ animate: false })
  }
  requestAnimationFrame(() => requestAnimationFrame(fixSize!))
  setTimeout(fixSize, 100)
  setTimeout(fixSize, 300)
  setTimeout(fixSize, 600)

  window.addEventListener('resize', fixSize)

  // 地图就绪后首次加载
  fetchMarkers()
}

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
  <div class="map-wrap">
    <div v-if="loading" class="map-loading">加载中...</div>
    <div ref="mapContainer" class="map-container"></div>
  </div>
</template>
