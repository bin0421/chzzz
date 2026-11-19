import { app, BrowserWindow, ipcMain, Menu, net, session, shell, type IpcMainInvokeEvent, type WebContents } from 'electron'
import { request as httpsRequest } from 'node:https'
import { join } from 'node:path'
import type { TLSSocket } from 'node:tls'
import { parseServerAddress } from '../../shell/src/address.js'
import type { ConnectionCheckResult, PendingCertificate } from '../../shell/src/connection.js'
import { persistentCookieFor } from './cookies.js'
import { decideLinkTarget } from './links.js'
import { isPermissionAllowed } from './permissions.js'
import { loadSettings, saveSettings, type Settings } from './settings.js'
import { decideCertificateTrust, fingerprintOfDer, fingerprintOfPem } from './trust.js'

const isDev = !app.isPackaged
// 개발 실행은 설치한 앱과 설정·쿠키를 나누지 않도록 다른 사용자 데이터 폴더를 쓴다.
if (isDev) app.setPath('userData', `${app.getPath('userData')}-dev`)

const settingsFile = join(app.getPath('userData'), 'settings.json')
const shellIndex = isDev
  ? join(app.getAppPath(), '..', 'shell', 'dist', 'index.html')
  : join(process.resourcesPath, 'shell', 'index.html')
const preload = join(import.meta.dirname, 'preload.cjs')

let settings: Settings = { serverOrigin: null, trustedCertificates: {} }
let win: BrowserWindow | null = null
// 서버 화면을 열다 만난, 아직 신뢰하지 않은 인증서. 지문 확인 화면(#trust)이 읽는다.
let pendingCertificate: PendingCertificate | null = null
// 사용자에게 보여 준 (출처 → 지문). 시작 화면이 신뢰하겠다고 보낸 값이 실제로 본 인증서인지 확인한다.
const shownFingerprints = new Map<string, string>()

if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (win === null) return
    if (win.isMinimized()) win.restore()
    win.focus()
  })
  app.on('certificate-error', handleCertificateError)
  app.on('web-contents-created', (_event, contents) => applyLinkRules(contents))
  app.whenReady().then(start)
  app.on('window-all-closed', () => app.quit())
}

function start(): void {
  settings = loadSettings(settingsFile)
  restrictPermissions()
  keepLoginCookies()
  registerShellBridge()
  Menu.setApplicationMenu(buildMenu())

  win = new BrowserWindow({
    width: 1280,
    height: 860,
    title: 'chzzz',
    webPreferences: { preload, contextIsolation: true, sandbox: true, nodeIntegration: false },
  })
  watchServerLoadFailure(win)

  if (settings.serverOrigin === null) openShell('setup')
  else openServer()
}

function openServer(): void {
  if (win === null) return
  if (settings.serverOrigin === null) return openShell('setup')
  // 캐시에 남은 화면을 그대로 띄우지 않도록 서버에 다시 확인한다. 서버가 닿지 않으면 did-fail-load가 난다.
  win.loadURL(settings.serverOrigin, { extraHeaders: 'Cache-Control: no-cache\n' }).catch(() => {
    // 실패는 did-fail-load에서 연결 실패 화면으로 처리한다.
  })
}

function openShell(mode: 'setup' | 'failed' | 'trust'): void {
  void win?.loadFile(shellIndex, { hash: mode })
}

// 서버 화면을 열지 못하면(서버 꺼짐, 닿지 않음) 연결 실패 화면을 보여 준다.
function watchServerLoadFailure(window: BrowserWindow): void {
  window.webContents.on('did-fail-load', (_event, errorCode, _description, validatedURL, isMainFrame) => {
    if (!isMainFrame || errorCode === -3 /* ERR_ABORTED: 다른 이동으로 바뀜 */) return
    // 인증서 오류(-200~-299)는 certificate-error에서 지문 확인 화면으로 처리한다.
    if (pendingCertificate !== null && errorCode <= -200 && errorCode > -300) return
    if (settings.serverOrigin !== null && originOf(validatedURL) === settings.serverOrigin) {
      openShell('failed')
    }
  })
}

// 신뢰한 인증서 판단(ADR-0013): 저장한 서버 출처에서 신뢰한 지문이면 받아들이고, 처음 보거나 바뀐 지문이면
// 연결하지 않고 지문 확인 화면을 연다. 다른 출처의 인증서 오류는 묻지 않고 거절한다(Chromium 기본 동작).
function handleCertificateError(
  event: Electron.Event,
  _webContents: WebContents,
  url: string,
  _error: string,
  certificate: Electron.Certificate,
  callback: (isTrusted: boolean) => void,
): void {
  const fingerprint = fingerprintOfPem(certificate.data)
  const decision = decideCertificateTrust({
    url,
    fingerprint,
    serverOrigin: settings.serverOrigin,
    trusted: settings.trustedCertificates,
  })
  if (decision.kind === 'accept') {
    event.preventDefault()
    callback(true)
    return
  }
  callback(false)
  if (decision.kind === 'ask') {
    const alreadyShown = pendingCertificate?.fingerprint === fingerprint && win?.webContents.getURL().endsWith('#trust')
    pendingCertificate = { origin: decision.origin, fingerprint, previousFingerprint: decision.previousFingerprint }
    shownFingerprints.set(decision.origin, fingerprint)
    if (!alreadyShown) openShell('trust')
  }
}

// 주소 열기 규칙: 서버 출처와 토스 결제창은 앱 안, 다른 http(s)는 기본 브라우저, 그 밖의 스킴은 운영체제로 넘긴다.
// 링크 이동(will-navigate)과 새 창(window.open, target=_blank) 모두에 적용한다.
function applyLinkRules(contents: WebContents): void {
  contents.on('will-navigate', (event) => {
    if (!event.url.startsWith('file:') && !openInApp(event.url)) event.preventDefault()
  })
  contents.setWindowOpenHandler(({ url }) => ({ action: openInApp(url) ? 'allow' : 'deny' }))
}

// 앱 안에서 열 주소면 true. 아니면 규칙대로 밖으로 넘기거나 막고 false.
function openInApp(url: string): boolean {
  const target = decideLinkTarget({ url, serverOrigin: settings.serverOrigin })
  if (target === 'browser' || target === 'system') {
    shell.openExternal(url).catch(() => {})
  }
  return target === 'app'
}

function restrictPermissions(): void {
  const ses = session.defaultSession
  ses.setPermissionRequestHandler((_webContents, permission, callback, details) => {
    callback(isPermissionAllowed({ permission, requestingUrl: details.requestingUrl, serverOrigin: settings.serverOrigin }))
  })
  ses.setPermissionCheckHandler((_webContents, permission, requestingOrigin) =>
    isPermissionAllowed({ permission, requestingUrl: requestingOrigin, serverOrigin: settings.serverOrigin }),
  )
}

// 앱을 다시 열어도 로그인이 유지되도록 서버의 세션 쿠키에 만료 시각을 붙여 디스크에 남긴다.
function keepLoginCookies(): void {
  const cookies = session.defaultSession.cookies
  cookies.on('changed', (_event, cookie, _cause, removed) => {
    const persistent = persistentCookieFor({ cookie, removed, serverOrigin: settings.serverOrigin, now: Date.now() })
    if (persistent !== null) cookies.set(persistent).catch(() => {})
  })
}

function registerShellBridge(): void {
  ipcMain.handle('shell:get-server', (event) => {
    assertFromShell(event)
    return settings.serverOrigin
  })
  ipcMain.handle('shell:check-server', (event, origin: unknown) => {
    assertFromShell(event)
    return checkServer(validOrigin(origin))
  })
  ipcMain.handle('shell:save-and-open', (event, origin: unknown) => {
    assertFromShell(event)
    settings = { ...settings, serverOrigin: validOrigin(origin) }
    saveSettings(settingsFile, settings)
    openServer()
  })
  ipcMain.handle('shell:open-server', (event) => {
    assertFromShell(event)
    openServer()
  })
  ipcMain.handle('shell:get-pending-certificate', (event) => {
    assertFromShell(event)
    return pendingCertificate
  })
  ipcMain.handle('shell:trust-certificate', (event, origin: unknown, fingerprint: unknown) => {
    assertFromShell(event)
    const valid = validOrigin(origin)
    if (typeof fingerprint !== 'string' || shownFingerprints.get(valid) !== fingerprint) {
      throw new Error('확인한 인증서와 다릅니다.')
    }
    settings = { ...settings, trustedCertificates: { ...settings.trustedCertificates, [valid]: fingerprint } }
    saveSettings(settingsFile, settings)
    pendingCertificate = null
  })
}

async function checkServer(origin: string): Promise<ConnectionCheckResult> {
  try {
    const response = await net.fetch(`${origin}/api/auth/me`, {
      redirect: 'manual',
      signal: AbortSignal.timeout(5000),
    })
    return { status: response.status }
  } catch (error) {
    // https 서버가 공인 인증서가 아니면(Caddy 로컬 CA) 인증서를 직접 받아 신뢰한 인증서 판단을 거친다.
    if (origin.startsWith('https:')) return checkServerWithPinnedCertificate(origin)
    return { error: error instanceof Error ? error.message : String(error) }
  }
}

// 확인하려는 주소를 서버 출처로 보고 판단한다: 신뢰한 지문이면 응답 코드를, 아니면 지문 확인에 필요한 값을 돌려준다.
function checkServerWithPinnedCertificate(origin: string): Promise<ConnectionCheckResult> {
  return new Promise((resolve) => {
    const request = httpsRequest(
      `${origin}/api/auth/me`,
      { rejectUnauthorized: false, agent: false, timeout: 5000 },
      (response) => {
        response.resume()
        const fingerprint = fingerprintOfDer((response.socket as TLSSocket).getPeerCertificate().raw)
        const decision = decideCertificateTrust({
          url: origin,
          fingerprint,
          serverOrigin: origin,
          trusted: settings.trustedCertificates,
        })
        if (decision.kind === 'accept') {
          resolve({ status: response.statusCode ?? 0 })
        } else if (decision.kind === 'ask') {
          shownFingerprints.set(origin, fingerprint)
          resolve({ certificate: { origin, fingerprint, previousFingerprint: decision.previousFingerprint } })
        } else {
          resolve({ error: '인증서를 거절했습니다.' })
        }
      },
    )
    request.on('timeout', () => request.destroy(new Error('응답 시간이 지났습니다.')))
    request.on('error', (error) => resolve({ error: error.message }))
    request.end()
  })
}

// 다리는 시작 화면(file://)에서 온 요청만 받는다.
function assertFromShell(event: IpcMainInvokeEvent): void {
  const url = event.senderFrame?.url ?? ''
  if (!url.startsWith('file:')) throw new Error('허용하지 않은 요청입니다.')
}

function validOrigin(value: unknown): string {
  const parsed = parseServerAddress(typeof value === 'string' ? value : '')
  if (!parsed.ok) throw new Error(parsed.reason)
  return parsed.origin
}

function buildMenu(): Menu {
  return Menu.buildFromTemplate([
    {
      label: 'chzzz',
      submenu: [
        { label: '서버 주소 변경', accelerator: 'CmdOrCtrl+,', click: () => openShell('setup') },
        { label: '다시 불러오기', accelerator: 'CmdOrCtrl+R', click: reload },
        ...(isDev
          ? [{ label: '개발자 도구', accelerator: 'CmdOrCtrl+Shift+I', click: () => win?.webContents.toggleDevTools() }]
          : []),
        { type: 'separator' },
        { label: '종료', role: 'quit' },
      ],
    },
    {
      label: '편집',
      submenu: [
        { label: '실행 취소', role: 'undo' },
        { label: '다시 실행', role: 'redo' },
        { type: 'separator' },
        { label: '잘라내기', role: 'cut' },
        { label: '복사', role: 'copy' },
        { label: '붙여넣기', role: 'paste' },
        { label: '모두 선택', role: 'selectAll' },
      ],
    },
  ])
}

// 서버 화면이면 새로 고치고, 연결 실패 화면이면 저장한 서버를 다시 연다.
function reload(): void {
  if (win === null) return
  const current = win.webContents.getURL()
  if (current.startsWith('file:') && current.endsWith('#failed')) openServer()
  else win.webContents.reloadIgnoringCache()
}

function originOf(url: string): string | null {
  try {
    return new URL(url).origin
  } catch {
    return null
  }
}
