import { describe, expect, it } from 'vitest'
import { decideLinkTarget } from './links'

const SERVER = 'https://192.168.0.5:8443'

// 주소 열기 규칙 표. Android 앱(티켓 03)도 같은 표를 쓴다.
describe('주소 열기 규칙', () => {
  it.each([
    // 앱 안: 저장한 서버 출처
    [`${SERVER}/`, SERVER, 'app'],
    [`${SERVER}/recordings/1?x=1#c`, SERVER, 'app'],
    ['http://localhost:3000/live/3', 'http://localhost:3000', 'app'],
    // 앱 안: 토스 결제위젯·결제창(https만, 하위 도메인 포함)
    ['https://payment-gateway-sandbox.tosspayments.com/pc?x=1', SERVER, 'app'],
    ['https://js.tosspayments.com/v2/standard', SERVER, 'app'],
    ['https://tosspayments.com/', SERVER, 'app'],
    ['https://toss.im/account-pay/demo', SERVER, 'app'],
    ['https://pay.toss.im/', SERVER, 'app'],
    // 기본 브라우저: 그 밖의 http(s)
    ['https://example.com/', SERVER, 'browser'],
    ['http://example.com/', SERVER, 'browser'],
    ['https://192.168.0.5:9443/', SERVER, 'browser'],
    ['http://192.168.0.5:8443/', SERVER, 'browser'],
    ['http://js.tosspayments.com/', SERVER, 'browser'],
    ['https://tosspayments.com.evil.example/', SERVER, 'browser'],
    ['https://eviltosspayments.com/', SERVER, 'browser'],
    ['https://nottoss.im/', SERVER, 'browser'],
    ['https://192.168.0.5:8443/', null, 'browser'],
    // 운영체제: 다른 앱을 부르는 스킴
    ['mailto:someone@example.com', SERVER, 'system'],
    ['tel:010-0000-0000', SERVER, 'system'],
    ['intent://pay#Intent;scheme=supertoss;package=viva.republica.toss;end', SERVER, 'system'],
    ['supertoss://pay?x=1', SERVER, 'system'],
    // 막음: 기기의 파일·스크립트를 여는 스킴, 해석할 수 없는 주소
    ['file:///C:/Windows/System32/calc.exe', SERVER, 'block'],
    ['javascript:alert(1)', SERVER, 'block'],
    ['data:text/html,<script>alert(1)</script>', SERVER, 'block'],
    ['blob:https://192.168.0.5:8443/1234', SERVER, 'block'],
    ['about:blank', SERVER, 'block'],
    ['not a url', SERVER, 'block'],
  ] as const)('%s (서버 %s) → %s', (url, serverOrigin, expected) => {
    expect(decideLinkTarget({ url, serverOrigin })).toBe(expected)
  })
})
