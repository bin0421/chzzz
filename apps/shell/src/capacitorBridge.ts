import type { AppBridge } from './bridge'
import type { ConnectionCheckResult, PendingCertificate } from './connection'

// Android 앱(Capacitor)이 시작 화면에 주는 네이티브 다리. @capacitor/core 없이 앱이 넣어 주는 전역 객체만 쓴다.
export interface CapacitorLike {
  nativePromise(plugin: string, method: string, options?: object): Promise<unknown>
}

const PLUGIN = 'ChzzzApp'

// 앱 안의 ChzzzApp 플러그인(apps/android, ChzzzAppPlugin.kt) 호출을 데스크톱과 같은 AppBridge로 바꾼다.
// 플러그인은 값을 객체로만 돌려주므로 { origin }, { certificate } 같은 껍데기를 벗긴다.
export function capacitorAppBridge(cap: CapacitorLike): AppBridge {
  const call = <T>(method: string, options: object = {}) => cap.nativePromise(PLUGIN, method, options) as Promise<T>
  return {
    getServerAddress: async () => (await call<{ origin: string | null }>('getServerAddress')).origin ?? null,
    checkServer: (origin) => call<ConnectionCheckResult>('checkServer', { origin }),
    saveServerAndOpen: async (origin) => {
      await call('saveServerAndOpen', { origin })
    },
    openServer: async () => {
      await call('openServer')
    },
    getPendingCertificate: async () =>
      (await call<{ certificate: PendingCertificate | null }>('getPendingCertificate')).certificate ?? null,
    trustCertificate: async (origin, fingerprint) => {
      await call('trustCertificate', { origin, fingerprint })
    },
  }
}
