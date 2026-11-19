import type { ConnectionCheckResult } from './connection'

// 시작 화면이 앱(데스크톱·Android)에 맡기는 일. 서버 요청은 CORS를 피하려고 앱 쪽에서 한다.
export interface AppBridge {
  getServerAddress(): Promise<string | null>
  checkServer(origin: string): Promise<ConnectionCheckResult>
  saveServerAndOpen(origin: string): Promise<void>
  openServer(): Promise<void>
}
