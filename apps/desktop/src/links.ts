// 주소 열기 규칙: 앱 창이 이동하거나 새 창을 열려는 주소를 어디서 열지 정한다.
// - app: 저장한 서버 출처, 토스 결제위젯·결제창(https) → 앱 안에서 연다
// - browser: 그 밖의 http(s) → 기본 브라우저
// - system: 다른 앱을 부르는 스킴(mailto:, tel:, intent:// 등) → 운영체제에 넘긴다
// - block: 기기의 파일·스크립트를 여는 스킴, 해석할 수 없는 주소 → 열지 않는다
export type LinkTarget = 'app' | 'browser' | 'system' | 'block'

// 2026-11-19 결제위젯 테스트 결제에서 실제로 본 도메인(하위 도메인 포함):
// js·api·payment-widget·payment-gateway-sandbox·event·log.tosspayments.com, toss.im·pay.toss.im·static.toss.im
export const TOSS_PAYMENT_DOMAINS = ['tosspayments.com', 'toss.im']

const BLOCKED_SCHEMES = new Set(['file:', 'javascript:', 'data:', 'blob:', 'about:', 'vbscript:'])

export function decideLinkTarget({ url, serverOrigin }: { url: string; serverOrigin: string | null }): LinkTarget {
  let parsed: URL
  try {
    parsed = new URL(url)
  } catch {
    return 'block'
  }

  // blob:은 URL.origin이 만든 쪽 출처라 서버 출처와 같아 보이므로 먼저 막는다.
  if (BLOCKED_SCHEMES.has(parsed.protocol)) return 'block'
  if (serverOrigin !== null && parsed.origin === serverOrigin) return 'app'
  if (parsed.protocol === 'https:' && isTossPaymentHost(parsed.hostname)) return 'app'
  if (parsed.protocol === 'https:' || parsed.protocol === 'http:') return 'browser'
  return 'system'
}

function isTossPaymentHost(hostname: string): boolean {
  return TOSS_PAYMENT_DOMAINS.some((domain) => hostname === domain || hostname.endsWith(`.${domain}`))
}
