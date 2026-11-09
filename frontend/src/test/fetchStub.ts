import { vi } from 'vitest'

type Route = {
  method?: string
  path: string
  status?: number
  body?: unknown
}

/** 네트워크 경계(전역 fetch)만 스텁한다. 등록되지 않은 요청은 실패시킨다. */
export function stubFetch(routes: Route[]) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    const method = (init?.method ?? 'GET').toUpperCase()
    const route = routes.find((r) => (r.method ?? 'GET') === method && r.path === url)
    if (!route) throw new Error(`스텁되지 않은 요청: ${method} ${url}`)
    const status = route.status ?? 200
    return new Response(route.body === undefined ? null : JSON.stringify(route.body), {
      status,
      headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}
