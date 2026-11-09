import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api } from '../api'
import { useMediaAdapter, type Publication } from '../media/MediaAdapter'

type Phase = 'ready' | 'starting' | 'live'

const STATUS_POLL_MS = 3000

/** 송출 화면: 제목 → 카메라·마이크 미리보기 → 방송 시작. */
export default function LiveBroadcastNewPage() {
  const media = useMediaAdapter()
  const [title, setTitle] = useState('')
  const [phase, setPhase] = useState<Phase>('ready')
  const [preview, setPreview] = useState<MediaStream | null>(null)
  const previewRef = useRef<HTMLVideoElement>(null)
  const publicationRef = useRef<Publication | null>(null)

  useEffect(() => {
    let cancelled = false
    media.openPreview('user').then((stream) => {
      if (!cancelled) setPreview(stream)
    })
    return () => {
      cancelled = true
    }
  }, [media])

  useEffect(() => {
    if (previewRef.current) previewRef.current.srcObject = preview
  }, [preview])

  async function handleStart(event: FormEvent) {
    event.preventDefault()
    if (!preview) return
    setPhase('starting')
    const started = await api.startLiveBroadcast(title)
    publicationRef.current = await media.publish(preview, started.whipUrl, started.streamKey)
    await waitUntilLive(started.id)
    setPhase('live')
  }

  return (
    <section>
      <h1>라이브 방송 시작</h1>
      <video ref={previewRef} className="live-preview" data-testid="live-preview" autoPlay muted playsInline />
      {phase === 'live' ? (
        <p className="live-status" role="status">
          <span className="badge live">방송 중</span>
        </p>
      ) : (
        <form onSubmit={handleStart}>
          <label>
            제목
            <input value={title} onChange={(e) => setTitle(e.target.value)} />
          </label>
          <button type="submit" disabled={!preview || phase === 'starting'}>
            {phase === 'starting' ? '연결 중…' : '방송 시작'}
          </button>
        </form>
      )}
    </section>
  )
}

async function waitUntilLive(id: number) {
  for (;;) {
    const broadcast = await api.getLiveBroadcast(id)
    if (broadcast.status === 'LIVE') return
    await new Promise((resolve) => setTimeout(resolve, STATUS_POLL_MS))
  }
}
