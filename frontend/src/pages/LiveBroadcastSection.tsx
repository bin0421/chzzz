import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, type LiveBroadcastSummary } from '../api'
import { formatDateTime } from '../format'

const LIST_POLL_MS = 5000

/** 방송 목록 위쪽의 "라이브 방송" 섹션. 방송 중과 최근 종료를 보여 주고 5초마다 다시 불러온다. */
export default function LiveBroadcastSection() {
  const [broadcasts, setBroadcasts] = useState<LiveBroadcastSummary[] | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    const load = () =>
      api.listLiveBroadcasts().then(
        (loaded) => {
          if (cancelled) return
          setBroadcasts(loaded)
          setFailed(false)
        },
        () => {
          if (!cancelled) setFailed(true)
        },
      )
    load()
    const timer = setInterval(load, LIST_POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])

  return (
    <section aria-labelledby="live-section-title">
      <h2 id="live-section-title">라이브 방송</h2>
      {failed && !broadcasts ? (
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
          {broadcasts.map((broadcast) => {
            const ended = broadcast.status === 'ENDED'
            const at = ended ? broadcast.endedAt : broadcast.startedAt
            return (
              <li key={broadcast.id}>
                <Link to={`/live/${broadcast.id}`}>{broadcast.title}</Link>
                <span className="meta">
                  {at && <time dateTime={at}>{formatDateTime(at)}</time>}
                  {ended ? <span className="badge ended">종료</span> : <span className="badge live">방송 중</span>}
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
