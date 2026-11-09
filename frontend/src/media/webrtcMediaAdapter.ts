import {
  MediaAccessError,
  type CameraFacing,
  type MediaAdapter,
  type PlaybackResult,
  type Publication,
  type Viewing,
} from './MediaAdapter'

const ICE_GATHERING_TIMEOUT_MS = 2000

/** 브라우저 표준 API(getUserMedia, RTCPeerConnection)와 WHIP/WHEP(HTTP + SDP)으로 구현한 미디어 어댑터. */
export const webrtcMediaAdapter: MediaAdapter = {
  async openPreview(facing: CameraFacing) {
    // HTTPS(또는 localhost)가 아니면 브라우저가 navigator.mediaDevices 자체를 숨긴다.
    if (!window.isSecureContext || !navigator.mediaDevices) throw new MediaAccessError('insecure-context')
    try {
      return await navigator.mediaDevices.getUserMedia({
        video: { facingMode: facing, width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: true,
      })
    } catch (error) {
      throw toMediaAccessError(error)
    }
  },

  async publish(stream, whipUrl, streamKey): Promise<Publication> {
    const pc = new RTCPeerConnection()
    for (const track of stream.getTracks()) {
      pc.addTransceiver(track, { direction: 'sendonly', streams: [stream] })
    }
    const { answer, location } = await exchangeSdp(pc, whipUrl, { Authorization: `Bearer ${streamKey}` })
    await pc.setRemoteDescription({ type: 'answer', sdp: answer })
    return {
      async stop() {
        pc.close()
        if (location) await fetch(location, { method: 'DELETE' }).catch(() => {})
      },
    }
  },

  startPlayback,

  async watch(whepUrl): Promise<Viewing> {
    const pc = new RTCPeerConnection()
    pc.addTransceiver('video', { direction: 'recvonly' })
    pc.addTransceiver('audio', { direction: 'recvonly' })
    const stream = new MediaStream()
    pc.addEventListener('track', (event) => stream.addTrack(event.track))
    const { answer, location } = await exchangeSdp(pc, whepUrl, {})
    await pc.setRemoteDescription({ type: 'answer', sdp: answer })

    const listeners: Array<() => void> = []
    let disconnected = false
    const notifyDisconnect = () => {
      if (disconnected) return
      disconnected = true
      listeners.forEach((listener) => listener())
    }
    pc.addEventListener('connectionstatechange', () => {
      if (pc.connectionState === 'failed' || pc.connectionState === 'closed' || pc.connectionState === 'disconnected') {
        notifyDisconnect()
      }
    })

    return {
      stream,
      onDisconnect(listener) {
        listeners.push(listener)
      },
      close() {
        disconnected = true
        pc.close()
        if (location) fetch(location, { method: 'DELETE' }).catch(() => {})
      },
    }
  },
}

/** 소리를 켠 채 재생하고, 자동재생 정책(NotAllowedError)에 막히면 음소거로 다시 재생한다. */
async function startPlayback(video: HTMLVideoElement): Promise<PlaybackResult> {
  video.muted = false
  try {
    await video.play()
    return 'audible'
  } catch (error) {
    if (!(error instanceof DOMException && error.name === 'NotAllowedError')) throw error
    video.muted = true
    await video.play()
    return 'muted'
  }
}

/** getUserMedia의 DOMException 이름을 화면이 안내할 이유로 바꾼다. 모르는 오류는 그대로 둔다. */
function toMediaAccessError(error: unknown): unknown {
  if (!(error instanceof DOMException)) return error
  switch (error.name) {
    case 'NotAllowedError':
    case 'SecurityError': // 브라우저·문서 정책이 카메라를 막았을 때
      return new MediaAccessError('permission-denied', { cause: error })
    case 'NotFoundError':
    case 'NotReadableError': // 다른 앱이 카메라를 쓰고 있을 때
    case 'OverconstrainedError':
      return new MediaAccessError('no-device', { cause: error })
    default:
      return error
  }
}

/** 오퍼를 만들고 ICE 후보를 모은 뒤 WHIP/WHEP 엔드포인트에 보내 응답 SDP와 세션 주소를 받는다. */
async function exchangeSdp(pc: RTCPeerConnection, url: string, headers: Record<string, string>) {
  await pc.setLocalDescription(await pc.createOffer())
  await waitForIceGathering(pc)
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/sdp', ...headers },
    body: pc.localDescription!.sdp,
  })
  if (!response.ok) {
    pc.close()
    throw new Error(`미디어 서버가 연결을 거절했습니다 (${response.status}).`)
  }
  const location = response.headers.get('Location')
  return {
    answer: await response.text(),
    // 세션 주소는 미디어 서버 기준 상대 경로로 오므로 요청한 주소를 기준으로 푼다.
    location: location ? new URL(location, new URL(url, window.location.href)).href : null,
  }
}

function waitForIceGathering(pc: RTCPeerConnection) {
  if (pc.iceGatheringState === 'complete') return Promise.resolve()
  return new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ICE_GATHERING_TIMEOUT_MS)
    pc.addEventListener('icegatheringstatechange', () => {
      if (pc.iceGatheringState === 'complete') {
        clearTimeout(timer)
        resolve()
      }
    })
  })
}
