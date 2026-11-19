import { describe, expect, it } from 'vitest'
import { parseSettings } from './settings'

const FP = Array.from({ length: 32 }, () => 'AB').join(':')

describe('앱 설정 읽기', () => {
  it('서버 주소와 신뢰한 인증서를 읽는다', () => {
    expect(
      parseSettings({ serverOrigin: 'https://192.168.0.5:8443', trustedCertificates: { 'https://192.168.0.5:8443': FP } }),
    ).toEqual({ serverOrigin: 'https://192.168.0.5:8443', trustedCertificates: { 'https://192.168.0.5:8443': FP } })
  })

  it('티켓 01 형식(신뢰한 인증서 없음)도 읽는다', () => {
    expect(parseSettings({ serverOrigin: 'http://localhost:3000' })).toEqual({
      serverOrigin: 'http://localhost:3000',
      trustedCertificates: {},
    })
  })

  it('출처가 아닌 키, 지문 형식이 아닌 값은 버린다', () => {
    expect(
      parseSettings({
        serverOrigin: 'https://192.168.0.5:8443/path',
        trustedCertificates: {
          'https://192.168.0.5:8443/path': FP,
          'http://192.168.0.5:8443': FP,
          'https://192.168.0.7:8443': 'not-a-fingerprint',
          'https://192.168.0.9:8443': FP,
        },
      }),
    ).toEqual({ serverOrigin: 'https://192.168.0.5:8443', trustedCertificates: { 'https://192.168.0.9:8443': FP } })
  })

  it.each([null, 'text', 42, { serverOrigin: 'ftp://x' }])('형식이 틀리면 처음 실행으로 본다(%s)', (raw) => {
    expect(parseSettings(raw)).toEqual({ serverOrigin: null, trustedCertificates: {} })
  })
})
