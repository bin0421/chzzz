import { app, BrowserWindow, ipcMain, Menu, net, session, type IpcMainInvokeEvent } from 'electron'
import { join } from 'node:path'
import { parseServerAddress } from '../../shell/src/address.js'
import type { ConnectionCheckResult } from '../../shell/src/connection.js'
import { persistentCookieFor } from './cookies.js'
import { isPermissionAllowed } from './permissions.js'
import { loadSettings, saveSettings, type Settings } from './settings.js'

const isDev = !app.isPackaged
// 개발 실행은 설치한 앱과 설정·쿠키를 나누지 않도록 다른 사용자 데이터 폴더를 쓴다.
if (isDev) app.setPath('userData', `${app.getPath('userData')}-dev`)

const settingsFile = join(app.getPath('userData'), 'settings.json')
const shellIndex = isDev
  ? join(app.getAppPath(), '..', 'shell', 'dist', 'index.html')
  : join(process.resourcesPath, 'shell', 'index.html')
const preload = join(import.meta.dirname, 'preload.cjs')

let settings: Settings = { serverOrigin: null }
let win: BrowserWindow | null = null

if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (win === null) return
    if (win.isMinimized()) win.restore()
    win.focus()
  })
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

function openShell(mode: 'setup' | 'failed'): void {
  void win?.loadFile(shellIndex, { hash: mode })
}

// 서버 화면을 열지 못하면(서버 꺼짐, 닿지 않음) 연결 실패 화면을 보여 준다.
function watchServerLoadFailure(window: BrowserWindow): void {
  window.webContents.on('did-fail-load', (_event, errorCode, _description, validatedURL, isMainFrame) => {
    if (!isMainFrame || errorCode === -3 /* ERR_ABORTED: 다른 이동으로 바뀜 */) return
    if (settings.serverOrigin !== null && originOf(validatedURL) === settings.serverOrigin) {
      openShell('failed')
    }
  })
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
    settings = { serverOrigin: validOrigin(origin) }
    saveSettings(settingsFile, settings)
    openServer()
  })
  ipcMain.handle('shell:open-server', (event) => {
    assertFromShell(event)
    openServer()
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
    return { error: error instanceof Error ? error.message : String(error) }
  }
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
