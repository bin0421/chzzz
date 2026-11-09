import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router'
import { api, type LiveBroadcastDetail } from '../api'
import { formatDateTime } from '../format'
import { useMediaAdapter } from '../media/MediaAdapter'

const DETAIL_POLL_MS = 3000

/** 시청 화면: 방송 중인 라이브 방송을 WHEP으로 받아 재생하고, 종료되면 안내한다. */
export default function LiveBroadcastWatchPage() {
  const { id } = useParams()
  const media = useMediaAdapter()
  const [broadcast, setBroadcast] = useState<LiveBroadcastDetail | null>(null)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [disconnected, setDisconnected] = useState(false)
  const playerRef = useRef<HTMLVideoElement>(null)
  const endedOnServer = broadcast?.status === 'ENDED'
  // 송출이 끊기면 서버가 종료로 바꾸기 전에 시청 연결이 먼저 끊긴다. 그때도 종료로 보여 준다.
  const ended = endedOnServer || disconnected
  const watching = broadcast?.status === 'LIVE' && !disconnected

  // 종료 시각을 받아 오도록, 끊긴 뒤에도 서버가 종료로 바꿀 때까지는 계속 불러온다.
  useEffect(() => {
    if (!id || endedOnServer) return
    let cancelled = false
    const load = () =>
      api.getLiveBroadcast(id).then((loaded) => {
        if (!cancelled) setBroadcast(loaded)
      })
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
    media.watch(whepUrl).then((viewing) => {
      if (closed) return viewing.close()
      close = () => viewing.close()
      viewing.onDisconnect(() => setDisconnected(true))
      setStream(viewing.stream)
    })
    return () => {
      closed = true
      close()
    }
  }, [watching, whepUrl, media])

  useEffect(() => {
    if (playerRef.current) playerRef.current.srcObject = stream
  }, [stream])

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
        <video ref={playerRef} className="live-player" data-testid="live-player" autoPlay playsInline controls />
      )}
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
