import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { parseServerAddress } from '../../shell/src/address.js'

// 앱 설정(서버 주소)은 사용자 데이터 폴더의 JSON에 둔다. 규칙에 맞지 않는 값은 저장하지 않은 것으로 본다.
export interface Settings {
  serverOrigin: string | null
}

export function loadSettings(file: string): Settings {
  try {
    const raw = JSON.parse(readFileSync(file, 'utf8')) as { serverOrigin?: unknown }
    if (typeof raw.serverOrigin === 'string') {
      const parsed = parseServerAddress(raw.serverOrigin)
      if (parsed.ok) return { serverOrigin: parsed.origin }
    }
  } catch {
    // 파일이 없거나 깨졌으면 처음 실행으로 본다.
  }
  return { serverOrigin: null }
}

export function saveSettings(file: string, settings: Settings): void {
  mkdirSync(dirname(file), { recursive: true })
  const temp = `${file}.tmp`
  writeFileSync(temp, JSON.stringify(settings, null, 2), 'utf8')
  renameSync(temp, file)
}
