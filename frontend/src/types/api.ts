export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  last: boolean
  size: number
  number: number
}

export interface ApiError {
  code: number
  message: string
}
