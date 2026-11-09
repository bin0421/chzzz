import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api'
import { useMediaAdapter, type Publication } from '../media/MediaAdapter'

type Phase = 'ready' | 'starting' | 'live' | 'resumed' | 'ended'

/** 송출 중인 라이브 방송을 종료하는 데 필요한 것. */
type OwnBroadcast = { id: number; streamKey: string }

const STATUS_POLL_MS = 3000
/** 탭 단위 저장소 키. 새로고침해도 같은 탭에서는 송출 키로 종료할 수 있게 한다. */
const OWN_BROADCAST_KEY = 'chzzz.ownLiveBroadcast'

/** 송출 화면: 제목 → 카메라·마이크 미리보기 → 방송 시작 → 방송 종료. */
export default function LiveBroadcastNewPage() {
  const media = useMediaAdapter()
  const [title, setTitle] = useState('')
  const [phase, setPhase] = useState<Phase>(() => (loadOwnBroadcast() ? 'resumed' : 'ready'))
  const [preview, setPreview] = useState<MediaStream | null>(null)
  const previewRef = useRef<HTMLVideoElement>(null)
  const publicationRef = useRef<Publication | null>(null)
  const ownRef = useRef<OwnBroadcast | null>(loadOwnBroadcast())

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
    ownRef.current = { id: started.id, streamKey: started.streamKey }
    saveOwnBroadcast(ownRef.current)
    publicationRef.current = await media.publish(preview, started.whipUrl, started.streamKey)
    await waitUntilLive(started.id)
    setPhase('live')
  }

  async function handleEnd() {
    await publicationRef.current?.stop()
    publicationRef.current = null
    const own = ownRef.current
    if (own) await endOrAlreadyEnded(own)
    ownRef.current = null
    saveOwnBroadcast(null)
    preview?.getTracks().forEach((track) => track.stop())
    setPreview(null)
    setPhase('ended')
  }

  return (
    <section>
      <h1>라이브 방송 시작</h1>
      <video ref={previewRef} className="live-preview" data-testid="live-preview" autoPlay muted playsInline />
      {phase === 'ended' ? (
        <p role="status">방송이 종료되었습니다.</p>
      ) : phase === 'live' || phase === 'resumed' ? (
        <>
          {phase === 'live' ? (
            <p className="live-status" role="status">
              <span className="badge live">방송 중</span>
            </p>
          ) : (
            <p>이 탭에서 시작한 라이브 방송이 있습니다.</p>
          )}
          <button type="button" onClick={handleEnd}>
            방송 종료
          </button>
        </>
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

/** 종료 API를 부른다. 끊김으로 이미 종료됐으면(409) 그대로 종료된 것으로 본다. */
async function endOrAlreadyEnded(own: OwnBroadcast) {
  try {
    await api.endLiveBroadcast(own.id, own.streamKey)
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 409)) throw error
  }
}

function loadOwnBroadcast(): OwnBroadcast | null {
  try {
    const saved = sessionStorage.getItem(OWN_BROADCAST_KEY)
    return saved ? (JSON.parse(saved) as OwnBroadcast) : null
  } catch {
    return null
  }
}

function saveOwnBroadcast(own: OwnBroadcast | null) {
  try {
    if (own) sessionStorage.setItem(OWN_BROADCAST_KEY, JSON.stringify(own))
    else sessionStorage.removeItem(OWN_BROADCAST_KEY)
  } catch {
    // 저장소를 못 쓰면 새로고침 뒤 종료만 못 할 뿐이다. 그때는 끊김 훅이 방송을 종료한다.
  }
}

async function waitUntilLive(id: number) {
  for (;;) {
    const broadcast = await api.getLiveBroadcast(id)
    if (broadcast.status === 'LIVE') return
    await new Promise((resolve) => setTimeout(resolve, STATUS_POLL_MS))
  }
}
