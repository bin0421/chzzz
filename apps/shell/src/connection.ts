// 연결 확인: 서버 주소의 /api/auth/me가 200(로그인됨) 또는 401(로그인 안 됨)으로 답하면 chzzz 서버로 본다.
// 서버 인증서를 아직 신뢰하지 않았으면 앱이 그 인증서의 출처와 지문을 돌려준다(ADR-0013).
export interface PendingCertificate {
  origin: string
  fingerprint: string
  /** 같은 출처에서 전에 신뢰한 지문. 있으면 지문이 바뀐 것이다. */
  previousFingerprint: string | null
}

export type ConnectionCheckResult = { status: number } | { error: string } | { certificate: PendingCertificate }
export type ConnectionVerdict =
  | { kind: 'ok' }
  | { kind: 'untrusted-certificate'; certificate: PendingCertificate }
  | { kind: 'not-chzzz'; message: string }
  | { kind: 'unreachable'; message: string }

export function interpretConnectionCheck(result: ConnectionCheckResult): ConnectionVerdict {
  if ('certificate' in result) return { kind: 'untrusted-certificate', certificate: result.certificate }
  if ('error' in result) {
    return { kind: 'unreachable', message: `서버에 연결하지 못했습니다. (${result.error})` }
  }
  if (result.status === 200 || result.status === 401) return { kind: 'ok' }
  return {
    kind: 'not-chzzz',
    message: `chzzz 서버가 아닙니다. /api/auth/me 응답이 ${result.status}입니다.`,
  }
}
