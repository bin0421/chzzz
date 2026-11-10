import { createContext, useContext } from 'react'
import type { Member } from '../api'

/** 앱 전체의 로그인 상태. 서버 세션이 기준이고, 화면은 앱이 뜰 때 `me`로 확인한다. */
export type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'member'; member: Member }

export type AuthContextValue = {
  auth: AuthState
  /** 가입·로그인에 성공한 회원으로 상태를 바꾼다. */
  signedIn: (member: Member) => void
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider 안에서만 쓸 수 있습니다.')
  return value
}

/** 로그인 뒤 돌아갈 주소. 서비스 안의 경로만 허용해 다른 사이트로 보내는 것을 막는다. */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return '/'
  return next
}
