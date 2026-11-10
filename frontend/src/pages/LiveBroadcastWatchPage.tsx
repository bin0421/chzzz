import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { api, ApiError, type LiveBroadcastDetail } from '../api'
import { useAuth } from '../auth/auth'
import { formatDateTime } from '../format'
import { useMediaAdapter } from '../media/MediaAdapter'
import { validateTitle } from '../titleRule'

const DETAIL_POLL_MS = 3000

/** 시청 화면: 대기면 곧 시작한다고 안내하고, 방송 중이면 WHEP으로 받아 재생하고, 종료되면 안내한다. */
export default function LiveBroadcastWatchPage() {
  const { id } = useParams()
  const media = useMediaAdapter()
  const [broadcast, setBroadcast] = useState<LiveBroadcastDetail | null>(null)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [disconnected, setDisconnected] = useState(false)
  const [soundBlocked, setSoundBlocked] = useState(false)
  const [connectionFailed, setConnectionFailed] = useState(false)
  const [loadError, setLoadError] = useState<'notFound' | 'failed'>()
  const [editingTitle, setEditingTitle] = useState(false)
  const [controlError, setControlError] = useState<ApiError>()
  const playerRef = useRef<HTMLVideoElement>(null)
  const endedOnServer = broadcast?.status === 'ENDED'
  // 송출이 끊기면 서버가 종료로 바꾸기 전에 시청 연결이 먼저 끊긴다. 그때도 종료로 보여 준다.
  const ended = endedOnServer || disconnected
  const watching = broadcast?.status === 'LIVE' && !disconnected
  const { auth } = useAuth()
  const navigate = useNavigate()
  // 버튼을 숨기는 것은 안내일 뿐이고, 실제 거절은 서버가 한다(401·403).
  const isOwner = auth.status === 'member' && broadcast?.owner?.id === auth.member.id

  // 상세를 3초마다 다시 불러와 대기 → 방송 중 → 종료 변화를 따라간다.
  // 종료 시각을 받아 오도록, 끊긴 뒤에도 서버가 종료로 바꿀 때까지는 계속 불러온다.
  useEffect(() => {
    if (!id || endedOnServer) return
    let cancelled = false
    let loaded = false
    const load = () =>
      api.getLiveBroadcast(id).then(
        (detail) => {
          loaded = true
          if (!cancelled) setBroadcast(detail)
        },
        (error: unknown) => {
          // 처음 불러오기 실패만 화면에 알린다. 폴링 중 일시적 실패는 다음 폴링에 맡긴다.
          if (cancelled || loaded) return
          setLoadError(error instanceof ApiError && error.status === 404 ? 'notFound' : 'failed')
        },
      )
    load()
    const timer = setInterval(load, DETAIL_POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [id, endedOnServer])

  const whepUrl = broadcast?.whepUrl
  useEffect(() => {
    if (!watching || !whepUrl) return
    let closed = false
    let close = () => {}
    media.watch(whepUrl).then(
      (viewing) => {
        if (closed) return viewing.close()
        close = () => viewing.close()
        viewing.onDisconnect(() => setDisconnected(true))
        setConnectionFailed(false)
        setStream(viewing.stream)
      },
      () => {
        if (!closed) setConnectionFailed(true)
      },
    )
    return () => {
      closed = true
      close()
    }
  }, [watching, whepUrl, media])

  useEffect(() => {
    const player = playerRef.current
    if (!player || !stream) return
    player.srcObject = stream
    let stale = false
    media.startPlayback(player).then((result) => {
      if (!stale) setSoundBlocked(result === 'muted')
    })
    return () => {
      stale = true
    }
  }, [stream, media])

  /** 소유자 제어 요청을 보내고, 서버가 거절하면(401·403·409) 안내로 바꿔 보여 준다. */
  async function control(action: () => Promise<void>) {
    setControlError(undefined)
    try {
      await action()
    } catch (error) {
      if (!(error instanceof ApiError)) throw error
      setControlError(error)
    }
  }

  function handleEnd() {
    if (!broadcast) return
    return control(async () => setBroadcast(await api.endLiveBroadcast(broadcast.id)))
  }

  function handleDelete() {
    if (!broadcast || !window.confirm('이 라이브 방송을 삭제할까요? 삭제하면 되돌릴 수 없습니다.')) return
    return control(async () => {
      await api.deleteLiveBroadcast(broadcast.id)
      navigate('/')
    })
  }

  function turnOnSound() {
    if (playerRef.current) playerRef.current.muted = false
    setSoundBlocked(false)
  }

  if (loadError === 'notFound') {
    return (
      <section>
        <h1>찾을 수 없음</h1>
        <p>요청한 라이브 방송이 없거나 주소가 잘못되었습니다.</p>
        <Link to="/">목록으로 돌아가기</Link>
      </section>
    )
  }

  if (loadError === 'failed') return <p role="alert">라이브 방송을 불러오지 못했습니다.</p>

  if (!broadcast) return <p>불러오는 중…</p>

  return (
    <article>
      {editingTitle ? (
        <TitleEditor
          initialTitle={broadcast.title}
          onSave={async (title) => {
            setControlError(undefined)
            setBroadcast(await api.renameLiveBroadcast(broadcast.id, title))
            setEditingTitle(false)
          }}
          onRejected={setControlError}
          onCancel={() => setEditingTitle(false)}
        />
      ) : (
        <h1>{broadcast.title}</h1>
      )}
      {broadcast.owner && (
        <p className="meta">
          <span className="owner">{broadcast.owner.nickname}</span>
        </p>
      )}
      {isOwner && (
        <div className="owner-actions">
          {!editingTitle && (
            <button type="button" onClick={() => setEditingTitle(true)}>
              제목 수정
            </button>
          )}
          {endedOnServer ? (
            <button type="button" onClick={handleDelete}>
              삭제
            </button>
          ) : (
            <button type="button" onClick={handleEnd}>
              방송 종료
            </button>
          )}
        </div>
      )}
      {controlError && <ControlErrorNotice error={controlError} />}
      {ended ? (
        <>
          <p role="status">방송이 종료되었습니다.</p>
          <dl className="meta">
            {broadcast.startedAt && <TimeEntry label="시작" iso={broadcast.startedAt} />}
            {broadcast.endedAt && <TimeEntry label="종료" iso={broadcast.endedAt} />}
          </dl>
        </>
      ) : (
        <>
          {broadcast.status === 'PENDING' && <p role="status">곧 방송이 시작됩니다</p>}
          <video ref={playerRef} className="live-player" data-testid="live-player" playsInline controls />
          {connectionFailed && (
            <p role="alert" className="notice">
              라이브 영상에 연결하지 못했습니다. 네트워크 연결을 확인한 뒤 새로고침해 주세요.
            </p>
          )}
          {soundBlocked && (
            <button type="button" className="unmute" onClick={turnOnSound}>
              소리 켜기
            </button>
          )}
        </>
      )}
    </article>
  )
}

/** 서버가 소유자 제어를 거절한 이유. 401은 다시 로그인하도록, 403은 권한 없음, 그 밖(409 등)은 서버의 이유. */
function ControlErrorNotice({ error }: { error: ApiError }) {
  const location = useLocation()
  if (error.status === 401) {
    const next = encodeURIComponent(location.pathname + location.search)
    return (
      <p role="alert" className="notice">
        로그인이 필요합니다. <Link to={`/login?next=${next}`}>로그인</Link>한 뒤 다시 시도해 주세요.
      </p>
    )
  }
  return (
    <p role="alert" className="notice">
      {error.status === 403 ? '이 방송을 관리할 권한이 없습니다.' : error.message}
    </p>
  )
}

/** 소유자의 제목 인라인 수정. 서버가 제목을 거절하면 입력란 옆에 이유를 보여 준다. */
function TitleEditor({
  initialTitle,
  onSave,
  onRejected,
  onCancel,
}: {
  initialTitle: string
  onSave: (title: string) => Promise<void>
  /** 입력 문제가 아닌 거절(401·403·409 등). */
  onRejected: (error: ApiError) => void
  onCancel: () => void
}) {
  const [title, setTitle] = useState(initialTitle)
  const [error, setError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const clientError = validateTitle(title)
    setError(clientError)
    if (clientError) return
    setSaving(true)
    try {
      await onSave(title)
    } catch (e) {
      if (!(e instanceof ApiError)) throw e
      setSaving(false)
      if (e.status !== 400) return onRejected(e)
      setError(e.errors.find((fieldError) => fieldError.field === 'title')?.message ?? e.message)
    }
  }

  return (
    <form className="title-editor" onSubmit={handleSubmit} noValidate>
      <label>
        제목
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? 'title-error' : undefined}
        />
      </label>
      {error && (
        <p id="title-error" className="field-error">
          {error}
        </p>
      )}
      <button type="submit" disabled={saving}>
        저장
      </button>
      <button type="button" onClick={onCancel}>
        취소
      </button>
    </form>
  )
}

function TimeEntry({ label, iso }: { label: string; iso: string }) {
  return (
    <>
      <dt>{label}</dt>
      <dd>
        <time dateTime={iso}>{formatDateTime(iso)}</time>
      </dd>
    </>
  )
}
