import type { VideoSourceType } from './api'

/** 서버의 ISO-8601 UTC 시각을 브라우저 로컬 시간으로 표시한다. */
export function formatDateTime(iso: string) {
  return new Date(iso).toLocaleString('ko-KR')
}

const sourceTypeLabels: Record<VideoSourceType, string> = {
  UPLOADED_FILE: '업로드',
  EXTERNAL_URL: 'URL',
}

export function formatSourceType(type: VideoSourceType) {
  return sourceTypeLabels[type]
}
