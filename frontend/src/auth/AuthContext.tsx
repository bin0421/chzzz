import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, type Member } from '../api'
import { AuthContext, type AuthState } from './auth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState>({ status: 'loading' })

  useEffect(() => {
    let cancelled = false
    api.me().then(
      (member) => !cancelled && setAuth({ status: 'member', member }),
      () => !cancelled && setAuth({ status: 'anonymous' }),
    )
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    // 세션이 끝난 뒤 보낸 요청이 401이면 화면의 로그인 상태도 비운다.
    setUnauthorizedHandler(() => setAuth({ status: 'anonymous' }))
    return () => setUnauthorizedHandler(() => {})
  }, [])

  const signedIn = useCallback((member: Member) => setAuth({ status: 'member', member }), [])
  const logout = useCallback(async () => {
    await api.logout().catch(() => {})
    setAuth({ status: 'anonymous' })
  }, [])

  const value = useMemo(() => ({ auth, signedIn, logout }), [auth, signedIn, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
