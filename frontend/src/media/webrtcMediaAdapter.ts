import type { CameraFacing, MediaAdapter, Publication, Viewing } from './MediaAdapter'

const ICE_GATHERING_TIMEOUT_MS = 2000

/** 브라우저 표준 API(getUserMedia, RTCPeerConnection)와 WHIP/WHEP(HTTP + SDP)으로 구현한 미디어 어댑터. */
export const webrtcMediaAdapter: MediaAdapter = {
  async openPreview(facing: CameraFacing) {
    return navigator.mediaDevices.getUserMedia({
      video: { facingMode: facing, width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: true,
    })
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
