import { describe, expect, it } from 'vitest'
import { isPermissionAllowed } from './permissions'

const server = 'http://localhost:3000'

describe('권한 허용 판단', () => {
  it.each([
    ['media', 'http://localhost:3000/live/3/broadcast'],
    ['media', 'http://localhost:3000'],
    ['fullscreen', 'http://localhost:3000/recordings/1'],
  ])('저장한 서버 출처의 %s 요청은 허용한다 (%s)', (permission, requestingUrl) => {
    expect(isPermissionAllowed({ permission, requestingUrl, serverOrigin: server })).toBe(true)
  })

  it.each([
    ['media', 'https://evil.example.com/cam', '다른 호스트'],
    ['media', 'http://localhost:3001/live', '다른 포트'],
    ['media', 'https://localhost:3000/live', '다른 스킴'],
    ['media', 'file:///C:/chzzz/shell/index.html', '시작 화면'],
    ['media', 'https://js.tosspayments.com/v2/standard', '결제위젯'],
    ['media', 'not a url', '잘못된 주소'],
    ['geolocation', 'http://localhost:3000/', '서버의 위치 권한'],
    ['notifications', 'http://localhost:3000/', '서버의 알림 권한'],
    ['clipboard-read', 'http://localhost:3000/', '서버의 클립보드 읽기'],
  ])('%s 요청은 거절한다: %s (%s)', (permission, requestingUrl) => {
    expect(isPermissionAllowed({ permission, requestingUrl, serverOrigin: server })).toBe(false)
  })

  it('저장한 서버 주소가 없으면 모두 거절한다', () => {
    expect(isPermissionAllowed({ permission: 'media', requestingUrl: server, serverOrigin: null })).toBe(false)
  })
})
