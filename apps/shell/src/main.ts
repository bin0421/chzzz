import './style.css'
import type { AppBridge } from './bridge'
import { renderShell, type ShellMode } from './screen'

declare global {
  interface Window {
    chzzzApp?: AppBridge
  }
}

// 앱이 시작 화면을 열 때 #failed(연결 실패), #trust(인증서 확인) 또는 #setup(주소 입력)을 붙인다.
const modes: Record<string, ShellMode> = { '#failed': 'failed', '#trust': 'trust' }
const mode: ShellMode = modes[location.hash] ?? 'setup'
const root = document.getElementById('root')!

if (window.chzzzApp) {
  void renderShell(root, window.chzzzApp, mode)
} else {
  root.textContent = '이 화면은 chzzz 앱 안에서만 동작합니다.'
}
