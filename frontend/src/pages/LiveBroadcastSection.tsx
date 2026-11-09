import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, type LiveBroadcastSummary } from '../api'
import { formatDateTime } from '../format'

/** 방송 목록 위쪽의 "라이브 방송" 섹션. */
export default function LiveBroadcastSection() {
  const [broadcasts, setBroadcasts] = useState<LiveBroadcastSummary[] | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    api.listLiveBroadcasts().then(setBroadcasts, () => setFailed(true))
  }, [])

  return (
    <section aria-labelledby="live-section-title">
      <h2 id="live-section-title">라이브 방송</h2>
      {failed ? (
        <p>라이브 방송을 불러오지 못했습니다.</p>
      ) : !broadcasts ? (
        <p>불러오는 중…</p>
      ) : broadcasts.length === 0 ? (
        <div className="empty">
          <p>지금 방송 중인 라이브 방송이 없습니다.</p>
          <Link className="button" to="/live/new">
            라이브 방송 시작하기
          </Link>
        </div>
      ) : (
        <ul>
          {broadcasts.map((broadcast) => (
            <li key={broadcast.id}>
              <Link to={`/live/${broadcast.id}`}>{broadcast.title}</Link>
              <span className="meta">
                {broadcast.startedAt && (
                  <time dateTime={broadcast.startedAt}>{formatDateTime(broadcast.startedAt)}</time>
                )}
                <span className="badge live">방송 중</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
