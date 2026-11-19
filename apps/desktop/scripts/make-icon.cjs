// 앱 아이콘(assets/icon.png, 512x512)을 만든다. 실행: npx electron scripts/make-icon.cjs
// electron-builder가 이 PNG로 Windows 아이콘(.ico)을 만든다.
const { app, BrowserWindow } = require('electron')
const { writeFileSync } = require('node:fs')
const { join } = require('node:path')

const SIZE = 512
const html = `<!doctype html><html><body style="margin:0;width:${SIZE}px;height:${SIZE}px;background:transparent">
<div style="width:${SIZE}px;height:${SIZE}px;border-radius:${SIZE * 0.22}px;
  background:linear-gradient(135deg,#8b5cff,#5b21d6);display:flex;align-items:center;justify-content:center;
  font:900 ${SIZE * 0.5}px 'Segoe UI',sans-serif;color:#fff;letter-spacing:-${SIZE * 0.02}px">
  <span style="margin-top:-${SIZE * 0.06}px">z</span>
  <span style="position:absolute;margin:${SIZE * 0.42}px 0 0 ${SIZE * 0.42}px;width:0;height:0;
    border-left:${SIZE * 0.1}px solid #fff;border-top:${SIZE * 0.065}px solid transparent;border-bottom:${SIZE * 0.065}px solid transparent"></span>
</div></body></html>`

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: SIZE, height: SIZE, show: false, frame: false, transparent: true, useContentSize: true,
    webPreferences: { offscreen: true },
  })
  await win.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(html)}`)
  await new Promise((resolve) => setTimeout(resolve, 300))
  const image = await win.webContents.capturePage({ x: 0, y: 0, width: SIZE, height: SIZE })
  writeFileSync(join(__dirname, '..', 'assets', 'icon.png'), image.resize({ width: SIZE, height: SIZE }).toPNG())
  app.quit()
})
