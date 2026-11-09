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

export type LiveBroadcastStatus = 'PENDING' | 'LIVE' | 'ENDED'

export type LiveBroadcastSummary = {
  id: number
  title: string
  status: LiveBroadcastStatus
  startedAt: string | null
  endedAt: string | null
}

export type LiveBroadcastDetail = LiveBroadcastSummary & {
  createdAt: string
  whepUrl: string
}

/** 시작 응답에만 송출 키와 WHIP 주소가 들어 있다. */
export type StartedLiveBroadcast = {
  id: number
  title: string
  status: LiveBroadcastStatus
  streamKey: string
  whipUrl: string
  createdAt: string
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
  listLiveBroadcasts() {
    return request<LiveBroadcastSummary[]>('/api/live-broadcasts')
  },
  startLiveBroadcast(title: string) {
    return request<StartedLiveBroadcast>('/api/live-broadcasts', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title }),
    })
  },
  getLiveBroadcast(id: string | number) {
    return request<LiveBroadcastDetail>(`/api/live-broadcasts/${id}`)
  },
  /** 송출자 종료. 송출 키를 아는 사람만 종료할 수 있다. */
  endLiveBroadcast(id: number, streamKey: string) {
    return request<LiveBroadcastDetail>(`/api/live-broadcasts/${id}/end`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${streamKey}` },
    })
  },
}
