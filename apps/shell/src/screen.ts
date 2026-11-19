import { parseServerAddress } from './address'
import type { AppBridge } from './bridge'
import { interpretConnectionCheck } from './connection'

// setup: 서버 주소 입력(처음 실행, 주소 변경). failed: 저장한 서버를 열지 못했을 때.
export type ShellMode = 'setup' | 'failed'

export async function renderShell(root: HTMLElement, bridge: AppBridge, mode: ShellMode): Promise<void> {
  const saved = await bridge.getServerAddress()
  if (mode === 'failed' && saved !== null) {
    renderFailed(root, bridge, saved)
  } else {
    renderSetup(root, bridge, saved)
  }
}

function renderSetup(root: HTMLElement, bridge: AppBridge, saved: string | null): void {
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
