import { describe, expect, it } from 'vitest'
import { persistentCookieFor, type ObservedCookie } from './cookies'

const server = 'http://localhost:3000'
const now = Date.UTC(2026, 10, 19, 0, 0, 0)
const 세션쿠키: ObservedCookie = {
  name: 'JSESSIONID',
  value: 'abc123',
  domain: 'localhost',
  path: '/',
  secure: false,
  httpOnly: true,
  sameSite: 'lax',
  session: true,
}

describe('로그인 유지: 서버 세션 쿠키를 앱 재시작 뒤에도 남긴다', () => {
  it('저장한 서버의 세션 쿠키는 같은 값으로 30일 뒤 만료되는 쿠키로 다시 저장한다', () => {
    expect(persistentCookieFor({ cookie: 세션쿠키, removed: false, serverOrigin: server, now })).toEqual({
      url: 'http://localhost:3000/',
      name: 'JSESSIONID',
      value: 'abc123',
      path: '/',
      secure: false,
      httpOnly: true,
      sameSite: 'lax',
      expirationDate: now / 1000 + 30 * 24 * 60 * 60,
    })
  })

  it('HTTPS 서버면 https 주소로 다시 저장한다', () => {
    const cookie = { ...세션쿠키, domain: '192.168.0.10', secure: true }
    expect(
      persistentCookieFor({ cookie, removed: false, serverOrigin: 'https://192.168.0.10:8443', now })?.url,
    ).toBe('https://192.168.0.10:8443/')
  })

  it.each([
    ['이미 만료 시각이 있는 쿠키', { ...세션쿠키, session: false }, false],
    ['지워진 쿠키(로그아웃, 덮어쓰기)', 세션쿠키, true],
    ['다른 사이트의 쿠키(결제위젯 등)', { ...세션쿠키, domain: '.tosspayments.com' }, false],
    ['비슷한 이름의 다른 호스트', { ...세션쿠키, domain: 'localhost.example.com' }, false],
  ])('%s는 그대로 둔다', (_name, cookie, removed) => {
    expect(persistentCookieFor({ cookie, removed, serverOrigin: server, now })).toBeNull()
  })

  it('저장한 서버 주소가 없으면 그대로 둔다', () => {
    expect(persistentCookieFor({ cookie: 세션쿠키, removed: false, serverOrigin: null, now })).toBeNull()
  })
})
