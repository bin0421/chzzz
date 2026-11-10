export type VideoSourceType = 'EXTERNAL_URL' | 'UPLOADED_FILE'

/** 화면에 보이는 회원 정보. 아이디는 서버가 내려 주지 않는다. */
export type Member = {
  id: number
  nickname: string
}

export type RecordingSummary = {
  id: number
  title: string
  sourceType: VideoSourceType
  createdAt: string
  /** 소유자. 회원 기능 이전에 등록된 녹화방송은 null이다. */
  owner?: Member | null
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

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS']

/** 서버가 내려 준 CSRF 토큰 쿠키 값(ADR-0005). */
function csrfToken(): string | undefined {
  const match = /(?:^|;\s*)XSRF-TOKEN=([^;]*)/.exec(document.cookie)
  return match ? decodeURIComponent(match[1]) : undefined
}

let onUnauthorized: () => void = () => {}

/** 서버가 401(로그인 필요)을 주면 부를 함수. 로그인 상태를 비우는 데 쓴다. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

/** 모든 API 요청이 지나는 곳. 상태를 바꾸는 요청에는 CSRF 토큰을 헤더로 싣는다. */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase()
  const headers = new Headers(init?.headers)
  const token = csrfToken()
  if (!SAFE_METHODS.includes(method) && token) headers.set('X-XSRF-TOKEN', token)
  const response = await fetch(path, { ...init, headers })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    if (response.status === 401) onUnauthorized()
    throw new ApiError(response.status, problem.detail ?? '요청을 처리하지 못했습니다.', problem.errors ?? [])
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function json(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}

export const api = {
  /** 로그인 중인 회원. 로그인하지 않았으면 401(ApiError). */
  me() {
    return request<Member>('/api/auth/me')
  },
  signUp(loginId: string, password: string, nickname: string) {
    return request<Member>('/api/auth/signup', json('POST', { loginId, password, nickname }))
  },
  login(loginId: string, password: string) {
    return request<Member>('/api/auth/login', json('POST', { loginId, password }))
  },
  logout() {
    return request<void>('/api/auth/logout', { method: 'POST' })
  },
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
    return request<StartedLiveBroadcast>('/api/live-broadcasts', json('POST', { title }))
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
