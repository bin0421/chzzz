import './style.css'
import type { AppBridge } from './bridge'
import { capacitorAppBridge, type CapacitorLike } from './capacitorBridge'
import { renderShell, type ShellMode } from './screen'

declare global {
  interface Window {
    /** 데스크톱 앱(Electron preload)이 주는 다리 */
    chzzzApp?: AppBridge
    /** Android 앱(Capacitor)이 주는 네이티브 다리 */
    Capacitor?: CapacitorLike & { isNativePlatform?: () => boolean }
  }
}

// 앱이 시작 화면을 열 때 #failed(연결 실패), #trust(인증서 확인) 또는 #setup(주소 입력)을 붙인다.
const modes: Record<string, ShellMode> = { '#failed': 'failed', '#trust': 'trust' }
const mode: ShellMode = modes[location.hash] ?? 'setup'
const root = document.getElementById('root')!

function appBridge(): AppBridge | null {
  if (window.chzzzApp) return window.chzzzApp
  if (window.Capacitor?.isNativePlatform?.()) return capacitorAppBridge(window.Capacitor)
  return null
}

const bridge = appBridge()
if (bridge) {
  void renderShell(root, bridge, mode)
} else {
  root.textContent = '이 화면은 chzzz 앱 안에서만 동작합니다.'
}
