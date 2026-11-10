import { vi, type Mock } from 'vitest'

type Route = {
  method?: string
  path: string
  status?: number
  body?: unknown
}

/** 로그인하지 않은 상태. 라우트 목록에 넣으면 기본 로그인 회원 대신 쓰인다. */
export const anonymous: Route = {
  path: '/api/auth/me',
  status: 401,
  body: { title: '로그인 필요', detail: '로그인이 필요합니다.' },
}

/** 라우트에 `/api/auth/me`가 없으면 이 회원으로 로그인되어 있다고 본다(기존 화면 테스트가 로그인 상태에서 돌도록). */
export const defaultMember = { id: 1, nickname: '테스터' }

/** 네트워크 경계(전역 fetch)만 스텁한다. 등록되지 않은 요청은 실패시킨다. */
export function stubFetch(routes: Route[]) {
  const allRoutes = routes.some((r) => r.path === '/api/auth/me')
    ? routes
    : [...routes, { path: '/api/auth/me', body: defaultMember }]
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    const method = (init?.method ?? 'GET').toUpperCase()
    const route = allRoutes.find((r) => (r.method ?? 'GET') === method && r.path === url)
    if (!route) throw new Error(`스텁되지 않은 요청: ${method} ${url}`)
    const status = route.status ?? 200
    return new Response(route.body === undefined || status === 204 ? null : JSON.stringify(route.body), {
      status,
      headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** 특정 메서드·경로로 나간 요청들의 [주소, 옵션]. */
export function requestsTo(fetchMock: Mock, method: string, path: string): [string, RequestInit | undefined][] {
  return (fetchMock.mock.calls as [string, RequestInit | undefined][]).filter(
    ([url, init]) => url === path && (init?.method ?? 'GET').toUpperCase() === method,
  )
}
