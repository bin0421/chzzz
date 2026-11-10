import { Link, useLocation } from 'react-router'
import { ApiError } from '../api'

/**
 * 소유자 전용 작업(수정·삭제 등)이 서버에서 거절됐을 때의 안내.
 * 버튼을 숨기는 것은 보조이고 서버 검사가 기준이므로, 401이면 로그인 안내, 403이면 권한 없음 안내를 보여 준다.
 */
export default function ActionErrorNotice({ error }: { error: unknown }) {
  const location = useLocation()

  if (error instanceof ApiError && error.status === 401) {
    const next = encodeURIComponent(location.pathname + location.search)
    return (
      <p role="alert" className="notice">
        로그인이 필요합니다. <Link to={`/login?next=${next}`}>로그인하기</Link>
      </p>
    )
  }
  if (error instanceof ApiError && error.status === 403) {
    return (
      <p role="alert" className="notice">
        권한이 없습니다. {error.message}
      </p>
    )
  }
  return (
    <p role="alert" className="notice">
      {error instanceof ApiError ? error.message : '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'}
    </p>
  )
}
