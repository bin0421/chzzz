import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { api, ApiError } from '../api'
import {
  MediaAccessError,
  useMediaAdapter,
  type CameraFacing,
  type MediaAccessFailure,
  type Publication,
} from '../media/MediaAdapter'
import { validateTitle } from '../titleRule'

type Phase = 'ready' | 'starting' | 'live'

const STATUS_POLL_MS = 3000

const CAMERAS: Array<{ facing: CameraFacing; label: string }> = [
  { facing: 'user', label: '전면 카메라' },
  { facing: 'environment', label: '후면 카메라' },
]

/** 미리보기 실패 이유별 안내: [무슨 일인지, 어떻게 고치는지]. */
const PREVIEW_ERROR_GUIDES: Record<MediaAccessFailure | 'unknown', [string, string]> = {
  'permission-denied': [
    '카메라·마이크 권한이 거부되었습니다.',
    '브라우저 주소창의 자물쇠(사이트 설정)에서 카메라와 마이크를 허용한 뒤 새로고침해 주세요.',
  ],
  'no-device': [
    '카메라나 마이크를 찾지 못했습니다.',
    '기기가 연결되어 있는지, 다른 앱이 카메라를 쓰고 있지 않은지 확인한 뒤 새로고침해 주세요.',
  ],
  'insecure-context': [
    'HTTPS 주소가 아니라서 브라우저가 카메라를 막았습니다.',
    'https:// 로 시작하는 주소(예: https://<PC의 LAN IP>:8443)로 다시 접속해 주세요.',
  ],
  unknown: ['카메라·마이크를 열지 못했습니다.', '새로고침하거나 다른 브라우저로 다시 시도해 주세요.'],
}

const CONNECTION_FAILED: [string, string] = [
  '송출 연결에 실패했습니다.',
  '네트워크 연결을 확인하고, PC에서 서버를 띄웠다면 방화벽에서 미디어 포트(기본 8189, UDP·TCP)가 열려 있는지 확인한 뒤 다시 시작해 주세요.',
]

function stopTracks(stream: MediaStream) {
  stream.getTracks().forEach((track) => track.stop())
}

/** 송출 화면: 제목 → 카메라·마이크 미리보기 → 방송 시작. */
export default function LiveBroadcastNewPage() {
  const media = useMediaAdapter()
  const [title, setTitle] = useState('')
  const [titleError, setTitleError] = useState<string>()
  const [phase, setPhase] = useState<Phase>('ready')
  const [facing, setFacing] = useState<CameraFacing>('user')
  const [preview, setPreview] = useState<MediaStream | null>(null)
  const [previewError, setPreviewError] = useState<MediaAccessFailure | 'unknown'>()
  const [startError, setStartError] = useState<[string, string]>()
  const [liveId, setLiveId] = useState<number>()
  const previewRef = useRef<HTMLVideoElement>(null)
  const publicationRef = useRef<Publication | null>(null)

  // 카메라를 바꾸면 이전 카메라를 끄고 새로 연다. 휴대폰은 두 카메라를 동시에 열지 못한다.
  useEffect(() => {
    let cancelled = false
    let opened: MediaStream | null = null
    media.openPreview(facing).then(
      (stream) => {
        if (cancelled) return stopTracks(stream)
        opened = stream
        setPreviewError(undefined)
        setPreview(stream)
      },
      (error: unknown) => {
        if (cancelled) return
        setPreview(null)
        setPreviewError(error instanceof MediaAccessError ? error.reason : 'unknown')
      },
    )
    return () => {
      cancelled = true
      if (opened) stopTracks(opened)
    }
  }, [media, facing])

  useEffect(() => {
    if (previewRef.current) previewRef.current.srcObject = preview
  }, [preview])

  async function handleStart(event: FormEvent) {
    event.preventDefault()
    if (!preview) return
    const clientError = validateTitle(title)
    setTitleError(clientError)
    if (clientError) return
    setStartError(undefined)
    setPhase('starting')
    let started
    try {
      started = await api.startLiveBroadcast(title)
    } catch (error) {
      if (!(error instanceof ApiError)) throw error
      setTitleError(error.errors.find((e) => e.field === 'title')?.message)
      setPhase('ready')
      return
    }
    try {
      publicationRef.current = await media.publish(preview, started.whipUrl, started.streamKey)
    } catch {
      setStartError(CONNECTION_FAILED)
      setPhase('ready')
      return
    }
    if ((await waitUntilLiveOrEnded(started.id)) === 'ENDED') {
      // 영상이 서버에 닿기 전에 대기 시간이 지났다.
      await publicationRef.current.stop()
      publicationRef.current = null
      setStartError(CONNECTION_FAILED)
      setPhase('ready')
      return
    }
    setLiveId(started.id)
    setPhase('live')
  }

  return (
    <section>
      <h1>라이브 방송 시작</h1>
      {previewError && <Guide guide={PREVIEW_ERROR_GUIDES[previewError]} />}
      {startError && <Guide guide={startError} />}
      <video
        ref={previewRef}
        // 전면 카메라만 거울처럼 보여 준다(송출되는 영상은 뒤집지 않는다).
        className={facing === 'user' ? 'live-preview mirrored' : 'live-preview'}
        data-testid="live-preview"
        autoPlay
        muted
        playsInline
      />
      {phase === 'live' ? (
        <>
          <p className="live-status" role="status">
            <span className="badge live">방송 중</span>
          </p>
          <p className="watch-link">
            시청 화면: <Link to={`/live/${liveId}`}>{`${window.location.origin}/live/${liveId}`}</Link>
          </p>
        </>
      ) : (
        <form className="live-form" onSubmit={handleStart} noValidate>
          <label>
            제목
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              aria-invalid={titleError ? true : undefined}
              aria-describedby={titleError ? 'title-error' : undefined}
            />
          </label>
          {titleError && (
            <p id="title-error" className="field-error">
              {titleError}
            </p>
          )}
          <fieldset disabled={phase !== 'ready'}>
            <legend>카메라</legend>
            {CAMERAS.map((camera) => (
              <label key={camera.facing}>
                <input
                  type="radio"
                  name="facing"
                  checked={facing === camera.facing}
                  onChange={() => setFacing(camera.facing)}
                />
                {camera.label}
              </label>
            ))}
          </fieldset>
          <button type="submit" disabled={!preview || phase === 'starting'}>
            {phase === 'starting' ? '연결 중…' : '방송 시작'}
          </button>
        </form>
      )}
    </section>
  )
}

/** 무슨 일이 생겼는지와 어떻게 고치는지를 함께 보여 주는 안내. */
function Guide({ guide: [cause, remedy] }: { guide: [string, string] }) {
  return (
    <div role="alert" className="notice">
      <p>
        <strong>{cause}</strong>
      </p>
      <p>{remedy}</p>
    </div>
  )
}

async function waitUntilLiveOrEnded(id: number) {
  for (;;) {
    const broadcast = await api.getLiveBroadcast(id)
    if (broadcast.status !== 'PENDING') return broadcast.status
    await new Promise((resolve) => setTimeout(resolve, STATUS_POLL_MS))
  }
}
