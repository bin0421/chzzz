// Android 앱 아이콘을 만든다. 데스크톱 아이콘(apps/desktop/scripts/make-icon.cjs)과 같은 그림이다.
// 실행(apps/android에서): ../desktop/node_modules/.bin/electron scripts/make-icons.cjs
// - mipmap-*/ic_launcher.png(둥근 사각형), ic_launcher_round.png(원): Android 7 이하 런처용
// - mipmap-*/ic_launcher_foreground.png: Android 8 이상 적응형 아이콘의 앞면(108dp, 가운데 66dp 안에 그림)
//   뒷면은 res/drawable/ic_launcher_background.xml의 보라색 그라데이션이다.
const { app, BrowserWindow } = require('electron')
const { mkdirSync, writeFileSync } = require('node:fs')
const { join } = require('node:path')

const RES = join(__dirname, '..', 'android', 'app', 'src', 'main', 'res')
const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 }
const RENDER = 512

function glyph(size) {
  return `<span style="font:900 ${size * 0.5}px 'Segoe UI',sans-serif;color:#fff;margin-top:-${size * 0.06}px">z</span>
  <span style="position:absolute;margin:${size * 0.42}px 0 0 ${size * 0.42}px;width:0;height:0;
    border-left:${size * 0.1}px solid #fff;border-top:${size * 0.065}px solid transparent;border-bottom:${size * 0.065}px solid transparent"></span>`
}

function page(shape) {
  const background = 'linear-gradient(135deg,#8b5cff,#5b21d6)'
  const radius = { square: `${RENDER * 0.22}px`, round: '50%', foreground: '0' }[shape]
  // 적응형 앞면은 배경 없이 그림만, 108dp 중 66dp 안전 영역에 맞춰 작게 그린다.
  const inner = shape === 'foreground' ? RENDER * (66 / 108) : RENDER
  return `<!doctype html><html><body style="margin:0;width:${RENDER}px;height:${RENDER}px;background:transparent;
    display:flex;align-items:center;justify-content:center">
  <div style="width:${RENDER}px;height:${RENDER}px;border-radius:${radius};
    background:${shape === 'foreground' ? 'transparent' : background};display:flex;align-items:center;justify-content:center">
    <div style="width:${inner}px;height:${inner}px;display:flex;align-items:center;justify-content:center">${glyph(inner)}</div>
  </div></body></html>`
}

async function render(win, shape) {
  await win.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(page(shape))}`)
  await new Promise((resolve) => setTimeout(resolve, 300))
  return win.webContents.capturePage({ x: 0, y: 0, width: RENDER, height: RENDER })
}

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: RENDER, height: RENDER, show: false, frame: false, transparent: true, useContentSize: true,
    webPreferences: { offscreen: true },
  })
  const images = {
    ic_launcher: [await render(win, 'square'), 48],
    ic_launcher_round: [await render(win, 'round'), 48],
    ic_launcher_foreground: [await render(win, 'foreground'), 108],
  }
  for (const [density, scale] of Object.entries(DENSITIES)) {
    const dir = join(RES, `mipmap-${density}`)
    mkdirSync(dir, { recursive: true })
    for (const [name, [image, dp]] of Object.entries(images)) {
      const size = Math.round(dp * scale)
      writeFileSync(join(dir, `${name}.png`), image.resize({ width: size, height: size, quality: 'best' }).toPNG())
    }
  }
  app.quit()
})
