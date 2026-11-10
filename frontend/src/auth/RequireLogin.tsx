import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from './auth'

/**
 * 로그인이 필요한 화면. 로그인하지 않은 것이 확인되면 로그인 화면으로 보내고, 로그인 뒤 이 화면으로 돌아오게 한다.
 * 화면을 숨기는 것은 안내일 뿐이고, 실제 거절은 서버가 한다(401).
 */
export default function RequireLogin({ children }: { children: ReactNode }) {
  const { auth } = useAuth()
  const location = useLocation()
  if (auth.status === 'anonymous') {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  return children
}
