export type SortField = 'time' | 'name' | 'size'
export type SortOrder = 'asc' | 'desc'

export interface TimelineExifItem {
  id: number
  photoId: number
  photoName: string
  photoThumbnail: string
  dateTaken: string
  cameraModel?: string
}

export interface MapExifItem {
  photoId: number
  photoName: string
  photoThumbnail: string
  latitude: number
  longitude: number
}
