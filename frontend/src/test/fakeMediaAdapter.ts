import { vi } from 'vitest'
import type { CameraFacing, MediaAdapter, Publication, Viewing } from '../media/MediaAdapter'

/** jsdom에는 MediaStream이 없으므로 식별용 가짜 객체를 쓴다. 트랙은 끄기(stop)만 기록한다. */
export function fakeStream(label: string): MediaStream {
  const tracks = [{ kind: 'video', stop: vi.fn() }, { kind: 'audio', stop: vi.fn() }]
  return { id: label, getTracks: () => tracks } as unknown as MediaStream
}

/** 카메라·WebRTC를 흉내 내는 미디어 어댑터. 호출은 vi.fn으로 기록한다. */
export function createFakeMediaAdapter() {
  const previewStream = fakeStream('preview')
  const remoteStream = fakeStream('remote')
  const publication: Publication = { stop: vi.fn(async () => {}) }
  let disconnectListener: (() => void) | null = null
  const viewing: Viewing = {
    stream: remoteStream,
    onDisconnect: (listener) => {
      disconnectListener = listener
    },
    close: vi.fn(),
  }

  const adapter = {
    openPreview: vi.fn(async (_facing: CameraFacing) => previewStream),
    publish: vi.fn(async (_stream: MediaStream, _whipUrl: string, _streamKey: string) => publication),
    watch: vi.fn(async (_whepUrl: string) => viewing),
  } satisfies MediaAdapter

  return {
    adapter,
    previewStream,
    remoteStream,
    publication,
    viewing,
    /** 시청 연결이 끊긴 것처럼 만든다. */
    disconnect: () => disconnectListener?.(),
  }
}
