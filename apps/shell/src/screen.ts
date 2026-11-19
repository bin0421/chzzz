import { parseServerAddress } from './address'
import type { AppBridge } from './bridge'
import { interpretConnectionCheck, type PendingCertificate } from './connection'

// setup: 서버 주소 입력(처음 실행, 주소 변경). failed: 저장한 서버를 열지 못했을 때.
// trust: 서버 화면을 열다 아직 신뢰하지 않은 인증서를 만났을 때(ADR-0013).
export type ShellMode = 'setup' | 'failed' | 'trust'

export async function renderShell(root: HTMLElement, bridge: AppBridge, mode: ShellMode): Promise<void> {
  const saved = await bridge.getServerAddress()
  if (mode === 'failed' && saved !== null) {
    renderFailed(root, bridge, saved)
  } else if (mode === 'trust' && (await showPendingCertificate(root, bridge, saved))) {
    return
  } else {
    renderSetup(root, bridge, saved)
  }
}

async function showPendingCertificate(root: HTMLElement, bridge: AppBridge, saved: string | null): Promise<boolean> {
  const pending = await bridge.getPendingCertificate()
  if (pending === null) return false
  renderTrust(root, pending, {
    onTrust: async () => {
      await bridge.trustCertificate(pending.origin, pending.fingerprint)
      await bridge.openServer()
    },
    onReject: () => renderSetup(root, bridge, saved),
  })
  return true
}

// connectNow: 지문을 신뢰한 뒤 같은 주소로 연결 확인을 바로 다시 한다.
function renderSetup(root: HTMLElement, bridge: AppBridge, saved: string | null, connectNow = false): void {
  const main = el('main', { className: 'shell' })
  main.append(
    el('h1', { textContent: 'chzzz 서버 연결' }),
    el('p', {
      className: 'hint',
      textContent: '접속할 chzzz 서버 주소를 입력하세요. 예: https://192.168.0.10:8443, 같은 PC는 http://localhost:3000',
    }),
  )

  const form = el('form', { noValidate: true })
  const label = el('label', { htmlFor: 'server-address', textContent: '서버 주소' })
  const input = el('input', {
    id: 'server-address',
    type: 'url',
    placeholder: 'https://192.168.0.10:8443',
    value: saved ?? '',
  })
  const button = el('button', { type: 'submit', textContent: '연결 확인 후 저장' })
  const status = el('p', { className: 'status' })
  form.append(label, input, button, status)
  main.append(form)

  form.addEventListener('submit', async (event) => {
    event.preventDefault()
    const parsed = parseServerAddress(input.value)
    if (!parsed.ok) {
      showError(status, parsed.reason)
      return
    }

    button.disabled = true
    showProgress(status, `${parsed.origin}에 연결하는 중…`)
    try {
      const verdict = interpretConnectionCheck(await bridge.checkServer(parsed.origin))
      if (verdict.kind === 'untrusted-certificate') {
        const { certificate } = verdict
        renderTrust(root, certificate, {
          onTrust: async () => {
            await bridge.trustCertificate(certificate.origin, certificate.fingerprint)
            renderSetup(root, bridge, parsed.origin, true)
          },
          onReject: () => renderSetup(root, bridge, parsed.origin),
        })
        return
      }
      if (verdict.kind !== 'ok') {
        showError(status, verdict.message)
        return
      }
      showProgress(status, '연결했습니다. 서버 화면을 엽니다.')
      await bridge.saveServerAndOpen(parsed.origin)
    } catch (error) {
      showError(status, `앱이 요청을 처리하지 못했습니다. (${String(error)})`)
    } finally {
      button.disabled = false
    }
  })

  root.replaceChildren(main)
  input.focus()
  if (connectNow) form.requestSubmit()
}

// 서버 인증서 지문 확인. 사용자가 신뢰하면 그 (출처, 지문)만 저장한다. 지문이 바뀌었으면 경고한다.
function renderTrust(
  root: HTMLElement,
  certificate: PendingCertificate,
  actions: { onTrust: () => Promise<void>; onReject: () => void },
): void {
  const main = el('main', { className: 'shell' })
  main.append(el('h1', { textContent: '서버 인증서 확인' }))
  if (certificate.previousFingerprint !== null) {
    const warning = el('p', {
      className: 'status error',
      textContent:
        '인증서 지문이 바뀌었습니다. 서버의 인증서를 새로 만든 것(서버 데이터 초기화, 인증서 갱신)이 아니라면 다른 서버일 수 있으니 연결하지 마세요.',
    })
    warning.setAttribute('role', 'alert')
    main.append(warning)
  }
  main.append(
    el('p', {
      className: 'hint',
      textContent:
        '이 서버는 서버가 직접 만든 인증서(Caddy 로컬 CA)를 씁니다. 서버를 운영하는 PC에서 확인한 지문과 같을 때만 신뢰하세요. 신뢰하면 이 주소에서 같은 지문은 다시 묻지 않습니다.',
    }),
  )

  const details = el('dl', { className: 'certificate' })
  const row = (term: string, value: string) => {
    const code = el('code', { textContent: value })
    details.append(el('dt', { textContent: term }), el('dd', {}, code))
  }
  row('서버 주소', certificate.origin)
  row('SHA-256 지문', certificate.fingerprint)
  if (certificate.previousFingerprint !== null) row('전에 신뢰한 지문', certificate.previousFingerprint)
  main.append(details)

  const trust = el('button', { type: 'button', textContent: '신뢰하고 연결' })
  const reject = el('button', { type: 'button', className: 'secondary', textContent: '연결하지 않음' })
  const status = el('p', { className: 'status' })
  main.append(el('div', { className: 'actions' }, trust, reject), status)

  trust.addEventListener('click', async () => {
    trust.disabled = true
    try {
      await actions.onTrust()
    } catch (error) {
      showError(status, `앱이 요청을 처리하지 못했습니다. (${String(error)})`)
    } finally {
      trust.disabled = false
    }
  })
  reject.addEventListener('click', actions.onReject)

  root.replaceChildren(main)
}

function renderFailed(root: HTMLElement, bridge: AppBridge, saved: string): void {
  const main = el('main', { className: 'shell' })
  const retry = el('button', { type: 'button', textContent: '다시 시도' })
  const change = el('button', { type: 'button', className: 'secondary', textContent: '서버 주소 변경' })
  const address = el('p', { className: 'address' })
  address.append(el('code', { textContent: saved }))
  main.append(
    el('h1', { textContent: '서버에 연결하지 못했습니다' }),
    address,
    el('p', { className: 'hint', textContent: '서버가 켜져 있는지, 같은 네트워크에 있는지 확인한 뒤 다시 시도하세요.' }),
    el('div', { className: 'actions' }, retry, change),
  )

  retry.addEventListener('click', () => {
    retry.disabled = true
    void bridge.openServer().finally(() => {
      retry.disabled = false
    })
  })
  change.addEventListener('click', () => renderSetup(root, bridge, saved))

  root.replaceChildren(main)
}

function showError(status: HTMLElement, message: string): void {
  status.setAttribute('role', 'alert')
  status.className = 'status error'
  status.textContent = message
}

function showProgress(status: HTMLElement, message: string): void {
  status.setAttribute('role', 'status')
  status.className = 'status'
  status.textContent = message
}

function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Partial<HTMLElementTagNameMap[K]> = {},
  ...children: HTMLElement[]
): HTMLElementTagNameMap[K] {
  const node = Object.assign(document.createElement(tag), props)
  node.append(...children)
  return node
}
