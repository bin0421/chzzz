import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { anonymous, defaultMember, requestsTo, stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 소유자 = defaultMember
const 다른회원 = { id: 2, nickname: '다른회원' }

const 대기 = {
  id: 7,
  title: '저녁 산책 라이브',
  status: 'PENDING',
  createdAt: '2026-11-09T09:00:00Z',
  startedAt: null,
  endedAt: null,
  whepUrl: '/media/live/7/whep',
  owner: 소유자,
}
const 방송중 = { ...대기, status: 'LIVE', startedAt: '2026-11-09T09:00:05Z' }
const 종료 = { ...방송중, status: 'ENDED', endedAt: '2026-11-09T09:30:00Z' }

function buttonNames() {
  return screen.queryAllByRole('button').map((button) => button.textContent)
}

describe('라이브 시청 화면의 소유자 관리 버튼', () => {
  it('소유자 닉네임을 보여 주고, 소유자에게는 대기·방송 중에 제목 수정과 방송 종료가 보인다', async () => {
    for (const detail of [대기, 방송중]) {
      stubFetch([{ path: '/api/live-broadcasts/7', body: detail }])
      const view = renderApp('/live/7')

      expect(await screen.findByRole('heading', { name: '저녁 산책 라이브' })).toBeInTheDocument()
      expect(screen.getByText(소유자.nickname, { selector: '.owner' })).toBeInTheDocument()
      expect(await screen.findByRole('button', { name: '방송 종료' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '제목 수정' })).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
      view.unmount()
    }
  })

  it('종료된 방송에서는 소유자에게 제목 수정과 삭제가 보이고 방송 종료는 없다', async () => {
    stubFetch([{ path: '/api/live-broadcasts/7', body: 종료 }])
    renderApp('/live/7')

    expect(await screen.findByRole('button', { name: '삭제' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '제목 수정' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '방송 종료' })).not.toBeInTheDocument()
  })

  it('소유자가 송출 탭이 아닌 시청 화면에서 방송 종료를 누르면 세션으로 종료하고 삭제 버튼이 나온다', async () => {
    const fetchMock = stubFetch([
      { path: '/api/live-broadcasts/7', body: 방송중 },
      { method: 'POST', path: '/api/live-broadcasts/7/end', body: 종료 },
    ])
    const user = userEvent.setup()
    renderApp('/live/7')

    await user.click(await screen.findByRole('button', { name: '방송 종료' }))

    expect(await screen.findByText('방송이 종료되었습니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument()
    const [[, init]] = requestsTo(fetchMock, 'POST', '/api/live-broadcasts/7/end')
    expect(new Headers(init?.headers).get('Authorization')).toBeNull()
  })

  it('소유자가 제목 수정에서 새 제목을 저장하면 PATCH를 보내고 제목이 바뀐다', async () => {
    const fetchMock = stubFetch([
      { path: '/api/live-broadcasts/7', body: 방송중 },
      { method: 'PATCH', path: '/api/live-broadcasts/7', body: { ...방송중, title: '새벽 산책 라이브' } },
    ])
    const user = userEvent.setup()
    renderApp('/live/7')

    await user.click(await screen.findByRole('button', { name: '제목 수정' }))
    const input = screen.getByLabelText('제목')
    expect(input).toHaveValue('저녁 산책 라이브')
    expect(input).toHaveFocus()
    await user.clear(input)
    await user.type(input, '새벽 산책 라이브')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('heading', { name: '새벽 산책 라이브' })).toBeInTheDocument()
    expect(screen.queryByLabelText('제목')).not.toBeInTheDocument()
    const [[, init]] = requestsTo(fetchMock, 'PATCH', '/api/live-broadcasts/7')
    expect(JSON.parse(init?.body as string)).toEqual({ title: '새벽 산책 라이브' })
  })

  it('서버가 제목을 거절하면(400) 입력란 옆에 이유를 보여 주고 제목은 그대로다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts/7', body: 방송중 },
      {
        method: 'PATCH',
        path: '/api/live-broadcasts/7',
        status: 400,
        body: { detail: '입력값을 확인해 주세요.', errors: [{ field: 'title', message: '제목은 100자 이하로 입력해 주세요.' }] },
      },
    ])
    const user = userEvent.setup()
    renderApp('/live/7')

    await user.click(await screen.findByRole('button', { name: '제목 수정' }))
    await user.type(screen.getByLabelText('제목'), ' 2부')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('제목은 100자 이하로 입력해 주세요.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '취소' }))
    expect(screen.getByRole('heading', { name: '저녁 산책 라이브' })).toBeInTheDocument()
  })

  it('소유자가 종료된 방송을 삭제하면 확인을 받은 뒤 DELETE를 보내고 목록으로 간다', async () => {
    const fetchMock = stubFetch([
      { path: '/api/live-broadcasts/7', body: 종료 },
      { method: 'DELETE', path: '/api/live-broadcasts/7', status: 204 },
      { path: '/api/live-broadcasts', body: [] },
      { path: '/api/recordings', body: [] },
    ])
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const user = userEvent.setup()
    renderApp('/live/7')

    await user.click(await screen.findByRole('button', { name: '삭제' }))
    expect(requestsTo(fetchMock, 'DELETE', '/api/live-broadcasts/7')).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: '삭제' }))

    expect(await screen.findByRole('region', { name: '라이브 방송' })).toBeInTheDocument()
    expect(requestsTo(fetchMock, 'DELETE', '/api/live-broadcasts/7')).toHaveLength(1)
    expect(confirm).toHaveBeenCalledTimes(2)
    confirm.mockRestore()
  })

  it('서버가 403으로 거절하면 권한 없음 안내를 보여 주고 방송은 그대로다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts/7', body: 방송중 },
      {
        method: 'POST',
        path: '/api/live-broadcasts/7/end',
        status: 403,
        body: { status: 403, detail: '이 방송의 소유자만 할 수 있습니다.' },
      },
    ])
    const user = userEvent.setup()
    renderApp('/live/7')

    await user.click(await screen.findByRole('button', { name: '방송 종료' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이 방송을 관리할 권한이 없습니다.')
    expect(screen.queryByText('방송이 종료되었습니다.')).not.toBeInTheDocument()
  })

  it('서버가 401로 거절하면(세션 만료) 로그인 안내와 이 화면으로 돌아오는 로그인 링크를 보여 준다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts/7', body: 종료 },
      {
        method: 'DELETE',
        path: '/api/live-broadcasts/7',
        status: 401,
        body: { status: 401, detail: '로그인이 필요합니다.' },
      },
    ])
    vi.spyOn(window, 'confirm').mockReturnValueOnce(true)
    const user = userEvent.setup()
    renderApp('/live/7')

    await user.click(await screen.findByRole('button', { name: '삭제' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('로그인이 필요합니다.')
    expect(within(alert).getByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login?next=%2Flive%2F7')
    expect(screen.getByRole('heading', { name: '저녁 산책 라이브' })).toBeInTheDocument()
  })

  it('제목 수정을 서버가 403으로 거절해도 같은 권한 없음 안내를 보여 준다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts/7', body: 방송중 },
      {
        method: 'PATCH',
        path: '/api/live-broadcasts/7',
        status: 403,
        body: { status: 403, detail: '이 방송의 소유자만 할 수 있습니다.' },
      },
    ])
    const user = userEvent.setup()
    renderApp('/live/7')

    await user.click(await screen.findByRole('button', { name: '제목 수정' }))
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이 방송을 관리할 권한이 없습니다.')
  })

  it('서버가 409로 거절하면 서버의 이유를 보여 준다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts/7', body: 종료 },
      {
        method: 'DELETE',
        path: '/api/live-broadcasts/7',
        status: 409,
        body: { status: 409, detail: '방송을 먼저 종료해 주세요.' },
      },
    ])
    vi.spyOn(window, 'confirm').mockReturnValueOnce(true)
    const user = userEvent.setup()
    renderApp('/live/7')

    await user.click(await screen.findByRole('button', { name: '삭제' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('방송을 먼저 종료해 주세요.')
  })

  it('다른 회원과 비로그인에게는 관리 버튼이 보이지 않는다', async () => {
    for (const me of [{ path: '/api/auth/me', body: 다른회원 }, anonymous]) {
      stubFetch([me, { path: '/api/live-broadcasts/7', body: 방송중 }])
      const view = renderApp('/live/7')

      expect(await screen.findByText(소유자.nickname, { selector: '.owner' })).toBeInTheDocument()
      expect(buttonNames()).not.toEqual(expect.arrayContaining(['제목 수정']))
      expect(buttonNames()).not.toEqual(expect.arrayContaining(['방송 종료']))
      expect(buttonNames()).not.toEqual(expect.arrayContaining(['삭제']))
      view.unmount()
    }
  })
})
