import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError, type LiveBroadcastDetail } from '../api'
import CommentSection from '../comments/CommentSection'
import { formatDateTime } from '../format'
import { useMediaAdapter } from '../media/MediaAdapter'

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
  const playerRef = useRef<HTMLVideoElement>(null)
  const endedOnServer = broadcast?.status === 'ENDED'
  // 송출이 끊기면 서버가 종료로 바꾸기 전에 시청 연결이 먼저 끊긴다. 그때도 종료로 보여 준다.
  const ended = endedOnServer || disconnected
  const watching = broadcast?.status === 'LIVE' && !disconnected

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
      <h1>{broadcast.title}</h1>
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
      <CommentSection kind="live" id={broadcast.id} pollMs={DETAIL_POLL_MS} />
    </article>
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
