// 서버의 로그인 쿠키(JSESSIONID)는 만료 시각이 없는 세션 쿠키라 앱을 닫으면 Chromium이 버린다.
// 저장한 서버 호스트의 세션 쿠키가 생기면 같은 값으로 만료 시각을 붙여 다시 저장해 재시작 뒤에도 남긴다.
// 로그인이 실제로 유효한지는 여전히 서버 세션(만료·로그아웃)이 정한다.
export interface ObservedCookie {
  name: string
  value: string
  domain?: string
  path?: string
  secure?: boolean
  httpOnly?: boolean
  sameSite: 'unspecified' | 'no_restriction' | 'lax' | 'strict'
  session?: boolean
}

export interface PersistentCookie {
  url: string
  name: string
  value: string
  path: string
  secure: boolean
  httpOnly: boolean
  sameSite: ObservedCookie['sameSite']
  expirationDate: number
}

const KEEP_SECONDS = 30 * 24 * 60 * 60

export function persistentCookieFor({
  cookie,
  removed,
  serverOrigin,
  now,
}: {
  cookie: ObservedCookie
  removed: boolean
  serverOrigin: string | null
  now: number
}): PersistentCookie | null {
  if (removed || cookie.session !== true || serverOrigin === null) return null
  if (cookie.domain?.replace(/^\./, '') !== new URL(serverOrigin).hostname) return null

  const path = cookie.path ?? '/'
  return {
    url: `${serverOrigin}${path}`,
    name: cookie.name,
    value: cookie.value,
    path,
    secure: cookie.secure ?? false,
    httpOnly: cookie.httpOnly ?? false,
    sameSite: cookie.sameSite,
    expirationDate: now / 1000 + KEEP_SECONDS,
  }
}
