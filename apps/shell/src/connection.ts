// 연결 확인: 서버 주소의 /api/auth/me가 200(로그인됨) 또는 401(로그인 안 됨)으로 답하면 chzzz 서버로 본다.
export type ConnectionCheckResult = { status: number } | { error: string }
export type ConnectionVerdict =
  | { kind: 'ok' }
  | { kind: 'not-chzzz'; message: string }
  | { kind: 'unreachable'; message: string }

export function interpretConnectionCheck(result: ConnectionCheckResult): ConnectionVerdict {
  if ('error' in result) {
    return { kind: 'unreachable', message: `서버에 연결하지 못했습니다. (${result.error})` }
  }
  if (result.status === 200 || result.status === 401) return { kind: 'ok' }
  return {
    kind: 'not-chzzz',
    message: `chzzz 서버가 아닙니다. /api/auth/me 응답이 ${result.status}입니다.`,
  }
}
