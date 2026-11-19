import { describe, expect, it } from 'vitest'
import { parseServerAddress } from './address'

describe('서버 주소 검증', () => {
  it.each([
    ['https://192.168.0.10:8443', 'https://192.168.0.10:8443'],
    ['https://chzzz.example.com', 'https://chzzz.example.com'],
    ['http://localhost:3000', 'http://localhost:3000'],
    ['http://127.0.0.1:3000', 'http://127.0.0.1:3000'],
    ['http://localhost', 'http://localhost'],
    ['  HTTPS://Chzzz.Example.com:443/  ', 'https://chzzz.example.com'],
    ['https://192.168.0.10:8443/recordings/3?x=1#top', 'https://192.168.0.10:8443'],
  ])('%s → 출처 %s만 저장한다', (input, origin) => {
    expect(parseServerAddress(input)).toEqual({ ok: true, origin })
  })

  it.each([
    ['', '서버 주소를 입력하세요.'],
    ['   ', '서버 주소를 입력하세요.'],
    ['192.168.0.10:8443', '주소는 https:// 또는 http://로 시작해야 합니다.'],
    ['localhost:3000', '주소는 https:// 또는 http://로 시작해야 합니다.'],
    ['ftp://192.168.0.10', '지원하지 않는 주소 형식입니다. https:// 또는 http://localhost만 쓸 수 있습니다.'],
    ['file:///C:/chzzz', '지원하지 않는 주소 형식입니다. https:// 또는 http://localhost만 쓸 수 있습니다.'],
    ['http://192.168.0.10:3000', 'http 주소는 localhost·127.0.0.1만 쓸 수 있습니다. 다른 기기의 서버는 https://로 입력하세요.'],
    ['http://localhost.example.com', 'http 주소는 localhost·127.0.0.1만 쓸 수 있습니다. 다른 기기의 서버는 https://로 입력하세요.'],
    ['https://', '주소 형식이 올바르지 않습니다.'],
    ['https://user:pw@chzzz.example.com', '주소에 사용자 정보(아이디·비밀번호)를 넣을 수 없습니다.'],
  ])('%j는 거절하고 이유를 알려 준다', (input, reason) => {
    expect(parseServerAddress(input)).toEqual({ ok: false, reason })
  })
})
