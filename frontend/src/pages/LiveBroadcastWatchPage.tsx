import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError, type LiveBroadcastDetail } from '../api'
import { useMediaAdapter } from '../media/MediaAdapter'

/** 시청 화면: 방송 중인 라이브 방송을 WHEP으로 받아 재생한다. */
export default function LiveBroadcastWatchPage() {
  const { id } = useParams()
  const media = useMediaAdapter()
  const [broadcast, setBroadcast] = useState<LiveBroadcastDetail | null>(null)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [soundBlocked, setSoundBlocked] = useState(false)
  const [connectionFailed, setConnectionFailed] = useState(false)
  const [loadError, setLoadError] = useState<'notFound' | 'failed'>()
  const playerRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    if (!id) return
    api
      .getLiveBroadcast(id)
      .then(setBroadcast)
      .catch((error) => setLoadError(error instanceof ApiError && error.status === 404 ? 'notFound' : 'failed'))
  }, [id])

  useEffect(() => {
    if (broadcast?.status !== 'LIVE') return
    let closed = false
    let close = () => {}
    media.watch(broadcast.whepUrl).then(
      (viewing) => {
        if (closed) return viewing.close()
        close = () => viewing.close()
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
  }, [broadcast?.status, broadcast?.whepUrl, media])

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
    </article>
  )
}
