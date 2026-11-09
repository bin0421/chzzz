import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router'
import { api, type LiveBroadcastDetail } from '../api'
import { useMediaAdapter } from '../media/MediaAdapter'

const DETAIL_POLL_MS = 3000

/** 시청 화면:방송 중인 라이브 방송을 WHEP으로 받아 재생한다. */
export default function LiveBroadcastWatchPage() {
  const { id } = useParams()
  const media = useMediaAdapter()
  const [broadcast, setBroadcast] = useState<LiveBroadcastDetail | null>(null)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const playerRef = useRef<HTMLVideoElement>(null)

  const ended = broadcast?.status === 'ENDED'

  // 상세를 3초마다 다시 불러와 대기 → 방송 중 → 종료 변화를 따라간다. 종료되면 멈춘다.
  useEffect(() => {
    if (!id || ended) return
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
  }, [id, ended])

  useEffect(() => {
    if (broadcast?.status !== 'LIVE') return
    let closed = false
    let close = () => {}
    media.watch(broadcast.whepUrl).then((viewing) => {
      if (closed) return viewing.close()
      close = () => viewing.close()
      setStream(viewing.stream)
    })
    return () => {
      closed = true
      close()
    }
  }, [broadcast?.status, broadcast?.whepUrl, media])

  useEffect(() => {
    if (playerRef.current) playerRef.current.srcObject = stream
  }, [stream])

  if (!broadcast) return <p>불러오는 중…</p>

  return (
    <article>
      <h1>{broadcast.title}</h1>
      {broadcast.status === 'PENDING' && <p role="status">곧 방송이 시작됩니다</p>}
      <video ref={playerRef} className="live-player" data-testid="live-player" autoPlay playsInline controls />
    </article>
  )
}
