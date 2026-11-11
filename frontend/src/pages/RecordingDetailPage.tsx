import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api, ApiError, type RecordingDetail } from '../api'
import { useAuth } from '../auth/auth'
import CommentSection from '../comments/CommentSection'
import DonationSection from '../donations/DonationSection'
import { formatDateTime } from '../format'
import ActionErrorNotice from './ActionErrorNotice'
import RecordingTitleEditor from './RecordingTitleEditor'

type State =
  | { kind: 'loading' }
  | { kind: 'loaded'; recording: RecordingDetail }
  | { kind: 'notFound' }
  | { kind: 'failed' }

export default function RecordingDetailPage() {
  const { id } = useParams()
  const [state, setState] = useState<State>({ kind: 'loading' })
  const { auth } = useAuth()
  const [editing, setEditing] = useState(false)
  const [actionError, setActionError] = useState<unknown>()
  const navigate = useNavigate()

  useEffect(() => {
    if (!id) return
    api
      .getRecording(id)
      .then((recording) => setState({ kind: 'loaded', recording }))
      .catch((error) =>
        setState(error instanceof ApiError && error.status === 404 ? { kind: 'notFound' } : { kind: 'failed' }),
      )
  }, [id])

  async function handleDelete(recording: RecordingDetail) {
    if (!window.confirm(`"${recording.title}" 녹화방송을 삭제할까요? 삭제하면 되돌릴 수 없습니다.`)) return
    setActionError(undefined)
    try {
      await api.deleteRecording(recording.id)
      navigate('/')
    } catch (error) {
      setActionError(error)
    }
  }

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
  const isOwner = auth.status === 'member' && recording.owner?.id === auth.member.id
  return (
    <article>
      {isOwner && editing ? (
        <RecordingTitleEditor
          recording={recording}
          onSaved={(saved) => {
            setState({ kind: 'loaded', recording: saved })
            setEditing(false)
          }}
          onCancel={() => setEditing(false)}
          onError={setActionError}
        />
      ) : (
        <h1>{recording.title}</h1>
      )}
      {isOwner && !editing && (
        <div className="owner-actions">
          <button
            type="button"
            onClick={() => {
              setActionError(undefined)
              setEditing(true)
            }}
          >
            수정
          </button>
          <button type="button" onClick={() => handleDelete(recording)}>
            삭제
          </button>
        </div>
      )}
      {actionError !== undefined && <ActionErrorNotice error={actionError} />}
      <p className="meta">
        {recording.owner && <span className="owner">{recording.owner.nickname}</span>}
        등록일시 <time dateTime={recording.createdAt}>{formatDateTime(recording.createdAt)}</time>
      </p>
      <Player key={recording.id} src={recording.videoUrl} />
      <DonationSection kind="recording" id={recording.id} streamer={recording.owner} />
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
