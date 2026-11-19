import { screen, waitFor } from '@testing-library/dom'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { AppBridge } from './bridge'
import { renderShell } from './screen'

function fakeBridge(overrides: Partial<AppBridge> = {}): AppBridge {
  return {
    getServerAddress: vi.fn(async () => null),
    checkServer: vi.fn(async () => ({ status: 401 })),
    saveServerAndOpen: vi.fn(async () => {}),
    openServer: vi.fn(async () => {}),
    getPendingCertificate: vi.fn(async () => null),
    trustCertificate: vi.fn(async () => {}),
    ...overrides,
  }
}

async function 주소입력후확인(주소: string) {
  const user = userEvent.setup()
  const input = screen.getByLabelText('서버 주소')
  await user.clear(input)
  await user.type(input, 주소)
  await user.click(screen.getByRole('button', { name: '연결 확인 후 저장' }))
}

describe('시작 화면', () => {
  it('주소 형식이 틀리면 이유를 보여 주고 서버에 묻지 않는다', async () => {
    const bridge = fakeBridge()
    await renderShell(document.body, bridge, 'setup')

    await 주소입력후확인('http://192.168.0.10:3000')

    expect(await screen.findByRole('alert')).toHaveTextContent('http 주소는 localhost·127.0.0.1만')
    expect(bridge.checkServer).not.toHaveBeenCalled()
    expect(bridge.saveServerAndOpen).not.toHaveBeenCalled()
  })

  it('연결 확인이 401이면 출처만 저장하고 서버 화면을 연다', async () => {
    const bridge = fakeBridge()
    await renderShell(document.body, bridge, 'setup')

    await 주소입력후확인('http://localhost:3000/recordings')

    await waitFor(() => expect(bridge.saveServerAndOpen).toHaveBeenCalledWith('http://localhost:3000'))
    expect(bridge.checkServer).toHaveBeenCalledWith('http://localhost:3000')
  })

  it('chzzz 서버가 아니거나 닿지 않으면 저장하지 않고 이유를 보여 준다', async () => {
    const bridge = fakeBridge({ checkServer: vi.fn(async () => ({ status: 404 })) })
    await renderShell(document.body, bridge, 'setup')

    await 주소입력후확인('https://chzzz.example.com')

    expect(await screen.findByRole('alert')).toHaveTextContent('chzzz 서버가 아닙니다. /api/auth/me 응답이 404입니다.')
    expect(bridge.saveServerAndOpen).not.toHaveBeenCalled()
  })

  it('저장한 주소가 있으면 입력란에 채워 둔다(주소 변경)', async () => {
    const bridge = fakeBridge({ getServerAddress: vi.fn(async () => 'https://192.168.0.10:8443') })
    await renderShell(document.body, bridge, 'setup')

    expect(screen.getByLabelText('서버 주소')).toHaveValue('https://192.168.0.10:8443')
  })

  it('연결 실패 화면에서 다시 시도하면 저장한 서버를 다시 연다', async () => {
    const bridge = fakeBridge({ getServerAddress: vi.fn(async () => 'http://localhost:3000') })
    await renderShell(document.body, bridge, 'failed')

    expect(screen.getByRole('heading', { name: '서버에 연결하지 못했습니다' })).toBeInTheDocument()
    expect(screen.getByText('http://localhost:3000')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: '다시 시도' }))

    expect(bridge.openServer).toHaveBeenCalled()
  })

  it('연결 실패 화면에서 서버 주소 변경을 누르면 주소 입력 화면으로 간다', async () => {
    const bridge = fakeBridge({ getServerAddress: vi.fn(async () => 'http://localhost:3000') })
    await renderShell(document.body, bridge, 'failed')

    await userEvent.setup().click(screen.getByRole('button', { name: '서버 주소 변경' }))

    expect(await screen.findByLabelText('서버 주소')).toHaveValue('http://localhost:3000')
  })
})

describe('인증서 지문 확인 화면', () => {
  const SERVER = 'https://192.168.0.5:8443'
  const 새지문 = 'AB:CD:EF:01'
  const 이전지문 = '12:34:56:78'

  it('연결 확인에서 신뢰하지 않은 인증서를 만나면 주소와 지문을 보여 주고, 신뢰하면 다시 확인한 뒤 저장한다', async () => {
    const checkServer = vi
      .fn<AppBridge['checkServer']>()
      .mockResolvedValueOnce({ certificate: { origin: SERVER, fingerprint: 새지문, previousFingerprint: null } })
      .mockResolvedValueOnce({ status: 401 })
    const bridge = fakeBridge({ checkServer })
    await renderShell(document.body, bridge, 'setup')

    await 주소입력후확인(SERVER)

    expect(await screen.findByRole('heading', { name: '서버 인증서 확인' })).toBeInTheDocument()
    expect(screen.getByText(SERVER)).toBeInTheDocument()
    expect(screen.getByText(새지문)).toBeInTheDocument()
    expect(bridge.saveServerAndOpen).not.toHaveBeenCalled()

    await userEvent.setup().click(screen.getByRole('button', { name: '신뢰하고 연결' }))

    await waitFor(() => expect(bridge.saveServerAndOpen).toHaveBeenCalledWith(SERVER))
    expect(bridge.trustCertificate).toHaveBeenCalledWith(SERVER, 새지문)
    expect(checkServer).toHaveBeenCalledTimes(2)
  })

  it('연결 확인에서 신뢰를 거절하면 저장하지 않고 주소 입력 화면으로 돌아간다', async () => {
    const bridge = fakeBridge({
      checkServer: vi.fn(async () => ({ certificate: { origin: SERVER, fingerprint: 새지문, previousFingerprint: null } })),
    })
    await renderShell(document.body, bridge, 'setup')
    await 주소입력후확인(SERVER)

    await userEvent.setup().click(await screen.findByRole('button', { name: '연결하지 않음' }))

    expect(await screen.findByLabelText('서버 주소')).toHaveValue(SERVER)
    expect(bridge.trustCertificate).not.toHaveBeenCalled()
    expect(bridge.saveServerAndOpen).not.toHaveBeenCalled()
  })

  it('서버 화면을 열다 지문이 바뀐 것을 알게 되면 경고와 함께 이전·새 지문을 보여 주고, 신뢰하면 서버를 다시 연다', async () => {
    const bridge = fakeBridge({
      getServerAddress: vi.fn(async () => SERVER),
      getPendingCertificate: vi.fn(async () => ({ origin: SERVER, fingerprint: 새지문, previousFingerprint: 이전지문 })),
    })
    await renderShell(document.body, bridge, 'trust')

    expect(screen.getByRole('alert')).toHaveTextContent('인증서 지문이 바뀌었습니다')
    expect(screen.getByText(이전지문)).toBeInTheDocument()
    expect(screen.getByText(새지문)).toBeInTheDocument()

    await userEvent.setup().click(screen.getByRole('button', { name: '신뢰하고 연결' }))

    await waitFor(() => expect(bridge.openServer).toHaveBeenCalled())
    expect(bridge.trustCertificate).toHaveBeenCalledWith(SERVER, 새지문)
  })

  it('서버 화면을 열다 만난 인증서를 거절하면 서버를 열지 않고 주소 입력 화면으로 간다', async () => {
    const bridge = fakeBridge({
      getServerAddress: vi.fn(async () => SERVER),
      getPendingCertificate: vi.fn(async () => ({ origin: SERVER, fingerprint: 새지문, previousFingerprint: null })),
    })
    await renderShell(document.body, bridge, 'trust')

    await userEvent.setup().click(screen.getByRole('button', { name: '연결하지 않음' }))

    expect(await screen.findByLabelText('서버 주소')).toHaveValue(SERVER)
    expect(bridge.openServer).not.toHaveBeenCalled()
    expect(bridge.trustCertificate).not.toHaveBeenCalled()
  })

  it('확인할 인증서가 없으면 주소 입력 화면을 보여 준다', async () => {
    const bridge = fakeBridge({ getServerAddress: vi.fn(async () => SERVER) })
    await renderShell(document.body, bridge, 'trust')

    expect(screen.getByLabelText('서버 주소')).toHaveValue(SERVER)
  })
})
