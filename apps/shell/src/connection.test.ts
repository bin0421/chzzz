import { describe, expect, it } from 'vitest'
import { interpretConnectionCheck } from './connection'

describe('연결 확인 결과 해석 (/api/auth/me)', () => {
  it.each([200, 401])('응답 %i이면 chzzz 서버로 본다', (status) => {
    expect(interpretConnectionCheck({ status })).toEqual({ kind: 'ok' })
  })

  it.each([404, 403, 302, 500, 502])('응답 %i이면 chzzz 서버가 아니라고 알려 준다', (status) => {
    expect(interpretConnectionCheck({ status })).toEqual({
      kind: 'not-chzzz',
      message: `chzzz 서버가 아닙니다. /api/auth/me 응답이 ${status}입니다.`,
    })
  })

  it('요청 자체가 실패하면 서버에 닿지 않았다고 알려 준다', () => {
    expect(interpretConnectionCheck({ error: 'net::ERR_CONNECTION_REFUSED' })).toEqual({
      kind: 'unreachable',
      message: '서버에 연결하지 못했습니다. (net::ERR_CONNECTION_REFUSED)',
    })
  })

  it('서버 인증서를 아직 신뢰하지 않았으면 지문 확인이 필요하다고 알려 준다', () => {
    const certificate = { origin: 'https://192.168.0.5:8443', fingerprint: 'AA:BB', previousFingerprint: null }
    expect(interpretConnectionCheck({ certificate })).toEqual({ kind: 'untrusted-certificate', certificate })
  })
})
