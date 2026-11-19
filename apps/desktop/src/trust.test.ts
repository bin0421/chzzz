import { describe, expect, it } from 'vitest'
import { decideCertificateTrust, fingerprintOfPem } from './trust'

const SERVER = 'https://192.168.0.5:8443'
const FP_A = 'AA:BB:CC'
const FP_B = '11:22:33'

describe('신뢰한 인증서 판단', () => {
  it('저장한 출처에서 신뢰한 지문과 같으면 받아들인다', () => {
    expect(
      decideCertificateTrust({ url: `${SERVER}/recordings/1`, fingerprint: FP_A, serverOrigin: SERVER, trusted: { [SERVER]: FP_A } }),
    ).toEqual({ kind: 'accept' })
  })

  it('저장한 출처인데 신뢰 기록이 없으면 주소와 지문을 묻는다', () => {
    expect(decideCertificateTrust({ url: `${SERVER}/`, fingerprint: FP_A, serverOrigin: SERVER, trusted: {} })).toEqual({
      kind: 'ask',
      origin: SERVER,
      fingerprint: FP_A,
      previousFingerprint: null,
    })
  })

  it('저장한 출처에서 지문이 바뀌었으면 이전 지문과 함께 다시 묻는다(경고)', () => {
    expect(
      decideCertificateTrust({ url: `${SERVER}/media/live/x/whep`, fingerprint: FP_B, serverOrigin: SERVER, trusted: { [SERVER]: FP_A } }),
    ).toEqual({ kind: 'ask', origin: SERVER, fingerprint: FP_B, previousFingerprint: FP_A })
  })

  it('지문 비교는 대소문자를 가리지 않는다', () => {
    expect(
      decideCertificateTrust({ url: SERVER, fingerprint: 'aa:bb:cc', serverOrigin: SERVER, trusted: { [SERVER]: FP_A } }),
    ).toEqual({ kind: 'accept' })
  })

  it.each([
    ['다른 호스트', 'https://evil.example.com/'],
    ['같은 호스트의 다른 포트', 'https://192.168.0.5:9443/'],
    ['결제위젯 도메인', 'https://js.tosspayments.com/v2/standard'],
    ['잘못된 주소', 'not a url'],
  ])('저장한 출처가 아니면(%s) 신뢰 기록이 있어도 묻지 않고 거절한다', (_name, url) => {
    expect(
      decideCertificateTrust({ url, fingerprint: FP_A, serverOrigin: SERVER, trusted: { [SERVER]: FP_A, [url]: FP_A } }),
    ).toEqual({ kind: 'reject' })
  })

  it('저장한 서버 주소가 없으면 거절한다', () => {
    expect(decideCertificateTrust({ url: SERVER, fingerprint: FP_A, serverOrigin: null, trusted: { [SERVER]: FP_A } })).toEqual({
      kind: 'reject',
    })
  })

  it('이전 서버 주소의 신뢰 기록은 새 서버 주소에 쓰지 않는다', () => {
    const old = 'https://192.168.0.7:8443'
    expect(decideCertificateTrust({ url: SERVER, fingerprint: FP_A, serverOrigin: SERVER, trusted: { [old]: FP_A } })).toEqual({
      kind: 'ask',
      origin: SERVER,
      fingerprint: FP_A,
      previousFingerprint: null,
    })
  })
})

describe('인증서 지문', () => {
  it('PEM 본문(DER)의 SHA-256을 콜론으로 나눈 대문자 16진수로 만든다', () => {
    // DER 바이트 00 01 02의 SHA-256
    const pem = '-----BEGIN CERTIFICATE-----\nAAEC\n-----END CERTIFICATE-----\n'
    expect(fingerprintOfPem(pem)).toBe(
      'AE:4B:32:80:E5:6E:2F:AF:83:F4:14:A6:E3:DA:BE:9D:5F:BE:18:97:65:44:C0:5F:ED:12:1A:CC:B8:5B:53:FC',
    )
  })
})
