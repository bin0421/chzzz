import { describe, expect, it } from 'vitest'
import { capacitorAppBridge, type CapacitorLike } from './capacitorBridge'

// Android 앱은 시작 화면에 Capacitor 네이티브 다리(window.Capacitor)만 준다.
// 어댑터는 그 다리의 ChzzzApp 플러그인 호출을 데스크톱과 같은 AppBridge 모양으로 바꾼다.
function fakeCapacitor(responses: Record<string, unknown>) {
  const calls: Array<[string, string, unknown]> = []
  const cap: CapacitorLike = {
    nativePromise: async (plugin, method, options) => {
      calls.push([plugin, method, options])
      return responses[method] as never
    },
  }
  return { cap, calls }
}

describe('Capacitor 앱 다리 어댑터', () => {
  it('저장한 서버 주소를 꺼내고, 없으면 null을 준다', async () => {
    expect(await capacitorAppBridge(fakeCapacitor({ getServerAddress: { origin: 'https://192.168.0.5:8443' } }).cap).getServerAddress()).toBe(
      'https://192.168.0.5:8443',
    )
    expect(await capacitorAppBridge(fakeCapacitor({ getServerAddress: { origin: null } }).cap).getServerAddress()).toBeNull()
  })

  it('연결 확인은 출처를 넘기고 앱의 결과(상태 코드·오류·인증서)를 그대로 준다', async () => {
    const certificate = { origin: 'https://192.168.0.5:8443', fingerprint: 'AA:BB', previousFingerprint: null }
    const { cap, calls } = fakeCapacitor({ checkServer: { certificate } })
    expect(await capacitorAppBridge(cap).checkServer('https://192.168.0.5:8443')).toEqual({ certificate })
    expect(calls).toEqual([['ChzzzApp', 'checkServer', { origin: 'https://192.168.0.5:8443' }]])
  })

  it('신뢰하지 않은 인증서를 꺼내고, 없으면 null을 준다', async () => {
    const certificate = { origin: 'https://192.168.0.5:8443', fingerprint: 'AA:BB', previousFingerprint: 'CC:DD' }
    expect(await capacitorAppBridge(fakeCapacitor({ getPendingCertificate: { certificate } }).cap).getPendingCertificate()).toEqual(
      certificate,
    )
    expect(
      await capacitorAppBridge(fakeCapacitor({ getPendingCertificate: { certificate: null } }).cap).getPendingCertificate(),
    ).toBeNull()
  })

  it('저장·열기·신뢰는 값을 넘겨 앱에 맡긴다', async () => {
    const { cap, calls } = fakeCapacitor({})
    const bridge = capacitorAppBridge(cap)
    await bridge.saveServerAndOpen('https://192.168.0.5:8443')
    await bridge.openServer()
    await bridge.trustCertificate('https://192.168.0.5:8443', 'AA:BB')
    expect(calls).toEqual([
      ['ChzzzApp', 'saveServerAndOpen', { origin: 'https://192.168.0.5:8443' }],
      ['ChzzzApp', 'openServer', {}],
      ['ChzzzApp', 'trustCertificate', { origin: 'https://192.168.0.5:8443', fingerprint: 'AA:BB' }],
    ])
  })
})
