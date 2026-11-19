import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { parseServerAddress } from '../../shell/src/address.js'

// 앱 설정(서버 주소, 신뢰한 인증서)은 사용자 데이터 폴더의 JSON에 둔다. 규칙에 맞지 않는 값은 저장하지 않은 것으로 본다.
export interface Settings {
  serverOrigin: string | null
  /** 사용자가 받아들인 서버 출처 → SHA-256 지문(ADR-0013). */
  trustedCertificates: Record<string, string>
}

const FINGERPRINT = /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/i

export function parseSettings(raw: unknown): Settings {
  const settings: Settings = { serverOrigin: null, trustedCertificates: {} }
  if (typeof raw !== 'object' || raw === null) return settings
  const { serverOrigin, trustedCertificates } = raw as { serverOrigin?: unknown; trustedCertificates?: unknown }

  if (typeof serverOrigin === 'string') {
    const parsed = parseServerAddress(serverOrigin)
    if (parsed.ok) settings.serverOrigin = parsed.origin
  }
  if (typeof trustedCertificates === 'object' && trustedCertificates !== null) {
    for (const [origin, fingerprint] of Object.entries(trustedCertificates)) {
      const parsed = parseServerAddress(origin)
      if (parsed.ok && parsed.origin === origin && typeof fingerprint === 'string' && FINGERPRINT.test(fingerprint)) {
        settings.trustedCertificates[origin] = fingerprint
      }
    }
  }
  return settings
}

export function loadSettings(file: string): Settings {
  try {
    return parseSettings(JSON.parse(readFileSync(file, 'utf8')))
  } catch {
    // 파일이 없거나 깨졌으면 처음 실행으로 본다.
    return parseSettings(null)
  }
}

export function saveSettings(file: string, settings: Settings): void {
  mkdirSync(dirname(file), { recursive: true })
  const temp = `${file}.tmp`
  writeFileSync(temp, JSON.stringify(settings, null, 2), 'utf8')
  renameSync(temp, file)
}
