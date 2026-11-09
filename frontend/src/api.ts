export type VideoSourceType = 'EXTERNAL_URL' | 'UPLOADED_FILE'

export type RecordingSummary = {
  id: number
  title: string
  sourceType: VideoSourceType
  createdAt: string
}

export type RecordingDetail = RecordingSummary & {
  videoUrl: string
}

export type FieldError = { field: string; message: string }

/** 서버가 RFC 9457 Problem Details로 거절한 요청. */
export class ApiError extends Error {
  readonly status: number
  readonly errors: FieldError[]

  constructor(status: number, detail: string, errors: FieldError[]) {
    super(detail)
    this.status = status
    this.errors = errors
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init)
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(response.status, problem.detail ?? '요청을 처리하지 못했습니다.', problem.errors ?? [])
  }
  return response.json() as Promise<T>
}

export const api = {
  listRecordings() {
    return request<RecordingSummary[]>('/api/recordings')
  },
  registerRecording(form: FormData) {
    return request<RecordingDetail>('/api/recordings', { method: 'POST', body: form })
  },
  getRecording(id: string) {
    return request<RecordingDetail>(`/api/recordings/${id}`)
  },
}
