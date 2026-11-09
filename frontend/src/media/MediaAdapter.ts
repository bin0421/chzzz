import { createContext, useContext } from 'react'

/** 전면(user) 또는 후면(environment) 카메라. */
export type CameraFacing = 'user' | 'environment'

/** 카메라·마이크를 열지 못한 이유. 이유마다 해결 방법이 다르다. */
export type MediaAccessFailure = 'permission-denied' | 'no-device' | 'insecure-context'

/** 미리보기를 열지 못했을 때 어댑터가 내는 오류. */
export class MediaAccessError extends Error {
  readonly reason: MediaAccessFailure

  constructor(reason: MediaAccessFailure, options?: ErrorOptions) {
    super(`카메라·마이크를 열지 못했습니다: ${reason}`, options)
    this.reason = reason
  }
}

/** 송출 중인 연결. */
export interface Publication {
  /** 송출을 멈추고 연결을 닫는다. */
  stop(): Promise<void>
}

/** 시청 중인 연결. */
export interface Viewing {
  /** 받은 원격 영상·소리. */
  stream: MediaStream
  /** 연결이 끊기면 한 번 불린다. */
  onDisconnect(listener: () => void): void
  close(): void
}

/**
 * 브라우저 미디어 API(카메라·마이크, WebRTC WHIP/WHEP)를 감싸는 얇은 경계.
 * 화면은 이 인터페이스만 쓰고, 테스트는 가짜 구현을 주입한다.
 */
export interface MediaAdapter {
  /** 카메라·마이크 미리보기를 연다. */
  openPreview(facing: CameraFacing): Promise<MediaStream>
  /** 미리보기 스트림을 WHIP으로 송출한다. 송출 키는 Bearer로 보낸다. */
  publish(stream: MediaStream, whipUrl: string, streamKey: string): Promise<Publication>
  /** WHEP으로 라이브 방송을 받는다. */
  watch(whepUrl: string): Promise<Viewing>
  /**
   * 소리를 켠 채 재생을 시도한다. 브라우저 자동재생 정책이 소리를 막으면
   * 음소거로 재생하고 'muted'를 돌려준다(사용자가 눌러야 소리를 켤 수 있다).
   */
  startPlayback(video: HTMLVideoElement): Promise<PlaybackResult>
}

export type PlaybackResult = 'audible' | 'muted'

export const MediaAdapterContext = createContext<MediaAdapter | null>(null)

export function useMediaAdapter(): MediaAdapter {
  const adapter = useContext(MediaAdapterContext)
  if (!adapter) throw new Error('MediaAdapterContext가 없습니다.')
  return adapter
}
