import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router'
import { api, type LiveBroadcastDetail } from '../api'
import { useMediaAdapter } from '../media/MediaAdapter'

/** 시청 화면: 방송 중인 라이브 방송을 WHEP으로 받아 재생한다. */
export default function LiveBroadcastWatchPage() {
  const { id } = useParams()
  const media = useMediaAdapter()
  const [broadcast, setBroadcast] = useState<LiveBroadcastDetail | null>(null)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const playerRef = useRef<HTMLVideoElement>(null)

  useEffect(() => {
    if (id) api.getLiveBroadcast(id).then(setBroadcast)
  }, [id])

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
      <video ref={playerRef} className="live-player" data-testid="live-player" autoPlay playsInline controls />
    </article>
  )
}
