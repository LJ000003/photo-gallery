import 'leaflet'

declare module 'leaflet' {
  function markerClusterGroup(options?: MarkerClusterGroupOptions): MarkerClusterGroup

  interface MarkerClusterGroupOptions {
    maxClusterRadius?: number
    spiderfyOnMaxZoom?: boolean
    showCoverageOnHover?: boolean
    zoomToBoundsOnClick?: boolean
    disableClusteringAtZoom?: number
    chunkedLoading?: boolean
    iconCreateFunction?: (cluster: MarkerCluster) => L.Icon | L.DivIcon
    spiderLegPolylineOptions?: L.PolylineOptions
    polygonOptions?: L.PolylineOptions
  }

  class MarkerClusterGroup extends L.FeatureGroup {
    addLayer(layer: L.Layer): this
    removeLayer(layer: L.Layer): this
    clearLayers(): this
    getLayers(): L.Layer[]
    getVisibleParent(marker: L.Marker): L.Marker | MarkerCluster | null
    refreshClusters(): this
    zoomToShowLayer(layer: L.Layer, callback?: () => void): void
  }

  class MarkerCluster extends L.Marker {
    getAllChildMarkers(): L.Marker[]
    getChildCount(): number
    spiderfy(): void
    unspiderfy(): void
  }
}
