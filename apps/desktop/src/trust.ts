import { createHash } from 'node:crypto'

// 신뢰한 인증서 판단(ADR-0013).
// 앱은 저장한 서버 주소(출처)에서 난 인증서 오류만 다룬다. 사용자가 받아들인 (출처, SHA-256 지문)과 같으면 받아들이고,
// 기록이 없거나 지문이 바뀌었으면 사용자에게 묻고, 다른 출처는 묻지 않고 거절한다.
export type TrustedCertificates = Record<string, string>
export type TrustDecision =
  | { kind: 'accept' }
  | { kind: 'reject' }
  | { kind: 'ask'; origin: string; fingerprint: string; previousFingerprint: string | null }

export function decideCertificateTrust({
  url,
  fingerprint,
  serverOrigin,
  trusted,
}: {
  url: string
  fingerprint: string
  serverOrigin: string | null
  trusted: TrustedCertificates
}): TrustDecision {
  if (serverOrigin === null || originOf(url) !== serverOrigin) return { kind: 'reject' }

  const previous = Object.hasOwn(trusted, serverOrigin) ? trusted[serverOrigin] : null
  if (previous !== null && previous.toUpperCase() === fingerprint.toUpperCase()) return { kind: 'accept' }
  return { kind: 'ask', origin: serverOrigin, fingerprint, previousFingerprint: previous }
}

// 사람이 비교하기 쉬운 형식(AB:CD:...)의 SHA-256 지문. 브라우저 인증서 보기와 같은 형식이다.
export function fingerprintOfPem(pem: string): string {
  const base64 = pem.replace(/-----(BEGIN|END) CERTIFICATE-----/g, '').replace(/\s+/g, '')
  return fingerprintOfDer(Buffer.from(base64, 'base64'))
}

export function fingerprintOfDer(der: Uint8Array): string {
  const hex = createHash('sha256').update(der).digest('hex').toUpperCase()
  return hex.match(/../g)!.join(':')
}

function originOf(url: string): string | null {
  try {
    return new URL(url).origin
  } catch {
    return null
  }
}
