import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, type RecordingSummary } from '../api'
import { formatDateTime, formatSourceType } from '../format'

export default function RecordingListPage() {
  const [recordings, setRecordings] = useState<RecordingSummary[] | null>(null)

  useEffect(() => {
    api.listRecordings().then(setRecordings)
  }, [])

  if (!recordings) return <p>불러오는 중…</p>

  return (
    <section>
      <h1>녹화방송</h1>
      {recordings.length === 0 ? (
        <div className="empty">
          <p>아직 등록된 녹화방송이 없습니다.</p>
          <Link className="button" to="/recordings/new">
            첫 녹화방송 등록하기
          </Link>
        </div>
      ) : (
        <ul>
          {recordings.map((recording) => (
            <li key={recording.id}>
              <Link to={`/recordings/${recording.id}`}>{recording.title}</Link>
              <span className="meta">
                {recording.owner && <span className="owner">{recording.owner.nickname}</span>}
                <time dateTime={recording.createdAt}>{formatDateTime(recording.createdAt)}</time>
                <span className="badge">{formatSourceType(recording.sourceType)}</span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
