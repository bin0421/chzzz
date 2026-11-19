import type { ConnectionCheckResult, PendingCertificate } from './connection'

// 시작 화면이 앱(데스크톱·Android)에 맡기는 일. 서버 요청은 CORS를 피하려고 앱 쪽에서 한다.
export interface AppBridge {
  getServerAddress(): Promise<string | null>
  checkServer(origin: string): Promise<ConnectionCheckResult>
  saveServerAndOpen(origin: string): Promise<void>
  openServer(): Promise<void>
  /** 서버 화면을 열다 만난, 아직 신뢰하지 않은 인증서. 없으면 null. */
  getPendingCertificate(): Promise<PendingCertificate | null>
  /** 사용자가 확인한 (출처, 지문)을 신뢰한 인증서로 저장한다. */
  trustCertificate(origin: string, fingerprint: string): Promise<void>
}
