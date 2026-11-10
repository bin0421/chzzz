import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError, type RecordingDetail } from '../api'
import CommentSection from '../comments/CommentSection'
import { formatDateTime } from '../format'

type State =
  | { kind: 'loading' }
  | { kind: 'loaded'; recording: RecordingDetail }
  | { kind: 'notFound' }
  | { kind: 'failed' }

export default function RecordingDetailPage() {
  const { id } = useParams()
  const [state, setState] = useState<State>({ kind: 'loading' })

  useEffect(() => {
    if (!id) return
    api
      .getRecording(id)
      .then((recording) => setState({ kind: 'loaded', recording }))
      .catch((error) =>
        setState(error instanceof ApiError && error.status === 404 ? { kind: 'notFound' } : { kind: 'failed' }),
      )
  }, [id])

  if (state.kind === 'loading') return <p>불러오는 중…</p>

  if (state.kind === 'notFound') {
    return (
      <section>
        <h1>찾을 수 없음</h1>
        <p>요청한 녹화방송이 없거나 주소가 잘못되었습니다.</p>
        <Link to="/">목록으로 돌아가기</Link>
      </section>
    )
  }

  if (state.kind === 'failed') return <p role="alert">녹화방송을 불러오지 못했습니다.</p>

  const { recording } = state
  return (
    <article>
      <h1>{recording.title}</h1>
      <p className="meta">
        {recording.owner && <span className="owner">{recording.owner.nickname}</span>}
        등록일시 <time dateTime={recording.createdAt}>{formatDateTime(recording.createdAt)}</time>
      </p>
      <Player key={recording.id} src={recording.videoUrl} />
      <CommentSection kind="recording" id={recording.id} />
    </article>
  )
}

function Player({ src }: { src: string }) {
  const [failed, setFailed] = useState(false)

  return (
    <>
      <video data-testid="recording-player" src={src} controls onError={() => setFailed(true)} />
      {failed && (
        <p role="alert" className="notice">
          영상을 재생할 수 없습니다. 영상 주소가 바뀌었거나 브라우저가 지원하지 않는 형식일 수 있습니다.
        </p>
      )}
    </>
  )
}
