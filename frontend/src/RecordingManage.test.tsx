import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { anonymous, defaultMember, requestsTo, stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

/** 기본 로그인 회원(테스터)이 소유자인 녹화방송. */
const 내방송 = {
  id: 7,
  title: '내 방송',
  sourceType: 'EXTERNAL_URL',
  videoUrl: 'https://cdn.example.com/mine.mp4',
  createdAt: '2026-11-09T06:00:00Z',
  owner: defaultMember,
}

describe('녹화방송 상세 화면의 소유자 관리 버튼', () => {
  it('소유자에게는 수정·삭제 버튼이 보인다', async () => {
    stubFetch([{ path: '/api/recordings/7', body: 내방송 }])
    renderApp('/recordings/7')

    await screen.findByRole('heading', { name: '내 방송' })
    expect(await screen.findByRole('button', { name: '수정' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument()
  })

  it('다른 회원에게는 수정·삭제 버튼이 보이지 않는다', async () => {
    stubFetch([
      { path: '/api/auth/me', body: { id: 2, nickname: '다른회원' } },
      { path: '/api/recordings/7', body: 내방송 },
    ])
    renderApp('/recordings/7')

    await screen.findByRole('heading', { name: '내 방송' })
    await screen.findByText('다른회원님')
    expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
  })

  it('비로그인에게는 수정·삭제 버튼이 보이지 않는다', async () => {
    stubFetch([anonymous, { path: '/api/recordings/7', body: 내방송 }])
    renderApp('/recordings/7')

    await screen.findByRole('heading', { name: '내 방송' })
    // 댓글 영역에도 로그인 링크가 있어 헤더에서 찾는다.
    await within(screen.getByRole('banner')).findByRole('link', { name: '로그인' })
    expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
  })

  it('소유자 없는 기존 녹화방송에는 누구에게도 버튼이 보이지 않는다', async () => {
    stubFetch([{ path: '/api/recordings/7', body: { ...내방송, owner: null } }])
    renderApp('/recordings/7')

    await screen.findByRole('heading', { name: '내 방송' })
    await screen.findByText('테스터님')
    expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument()
  })
})

describe('녹화방송 제목 수정', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('소유자가 제목을 고쳐 저장하면 CSRF 헤더와 함께 PATCH를 보내고 새 제목을 보여 준다', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-1; path=/'
    const fetchMock = stubFetch([
      { path: '/api/recordings/7', body: 내방송 },
      { method: 'PATCH', path: '/api/recordings/7', body: { ...내방송, title: '바뀐 제목' } },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/7')

    await user.click(await screen.findByRole('button', { name: '수정' }))
    const input = screen.getByRole('textbox', { name: '제목' })
    expect(input).toHaveValue('내 방송')
    await user.clear(input)
    await user.type(input, '바뀐 제목')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('heading', { name: '바뀐 제목' })).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '제목' })).not.toBeInTheDocument()
    const [[, init]] = requestsTo(fetchMock, 'PATCH', '/api/recordings/7')
    expect(JSON.parse(init?.body as string)).toEqual({ title: '바뀐 제목' })
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('csrf-1')
  })

  it('취소하면 요청 없이 원래 제목으로 돌아간다', async () => {
    const fetchMock = stubFetch([{ path: '/api/recordings/7', body: 내방송 }])
    const user = userEvent.setup()
    renderApp('/recordings/7')

    await user.click(await screen.findByRole('button', { name: '수정' }))
    await user.type(screen.getByRole('textbox', { name: '제목' }), ' 고치다 만 것')
    await user.click(screen.getByRole('button', { name: '취소' }))

    expect(screen.getByRole('heading', { name: '내 방송' })).toBeInTheDocument()
    expect(requestsTo(fetchMock, 'PATCH', '/api/recordings/7')).toHaveLength(0)
  })

  it('서버가 제목 규칙 위반(400)을 주면 입력란 옆에 이유를 보여 주고 수정 상태를 유지한다', async () => {
    stubFetch([
      { path: '/api/recordings/7', body: 내방송 },
      {
        method: 'PATCH',
        path: '/api/recordings/7',
        status: 400,
        body: { detail: '입력값을 확인해 주세요.', errors: [{ field: 'title', message: '제목을 입력해 주세요.' }] },
      },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/7')

    await user.click(await screen.findByRole('button', { name: '수정' }))
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('제목을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '제목' })).toBeInTheDocument()
  })

  it('서버가 401을 주면 로그인 안내와 로그인 링크를 보여 준다', async () => {
    stubFetch([
      { path: '/api/recordings/7', body: 내방송 },
      { method: 'PATCH', path: '/api/recordings/7', status: 401, body: { detail: '로그인이 필요합니다.' } },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/7')

    await user.click(await screen.findByRole('button', { name: '수정' }))
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('로그인이 필요합니다.')
    expect(screen.getByRole('link', { name: '로그인하기' })).toHaveAttribute(
      'href',
      '/login?next=%2Frecordings%2F7',
    )
  })

  it('서버가 403을 주면 권한 없음 안내를 보여 준다', async () => {
    stubFetch([
      { path: '/api/recordings/7', body: 내방송 },
      { method: 'PATCH', path: '/api/recordings/7', status: 403, body: { detail: '이 방송의 소유자만 할 수 있습니다.' } },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/7')

    await user.click(await screen.findByRole('button', { name: '수정' }))
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('권한이 없습니다. 이 방송의 소유자만 할 수 있습니다.')
  })
})

describe('녹화방송 삭제', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('소유자가 삭제를 확인하면 DELETE를 보내고 목록으로 이동한다', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const fetchMock = stubFetch([
      { path: '/api/recordings/7', body: 내방송 },
      { method: 'DELETE', path: '/api/recordings/7', status: 204 },
      { path: '/api/recordings', body: [] },
      { path: '/api/live-broadcasts', body: [] },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/7')

    await user.click(await screen.findByRole('button', { name: '삭제' }))

    expect(confirm).toHaveBeenCalledWith('"내 방송" 녹화방송을 삭제할까요? 삭제하면 되돌릴 수 없습니다.')
    expect(await screen.findByText('아직 등록된 녹화방송이 없습니다.')).toBeInTheDocument()
    expect(requestsTo(fetchMock, 'DELETE', '/api/recordings/7')).toHaveLength(1)
  })

  it('삭제 확인을 취소하면 요청을 보내지 않는다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const fetchMock = stubFetch([{ path: '/api/recordings/7', body: 내방송 }])
    const user = userEvent.setup()
    renderApp('/recordings/7')

    await user.click(await screen.findByRole('button', { name: '삭제' }))

    expect(screen.getByRole('heading', { name: '내 방송' })).toBeInTheDocument()
    expect(requestsTo(fetchMock, 'DELETE', '/api/recordings/7')).toHaveLength(0)
  })

  it('서버가 삭제를 403으로 거절하면 권한 없음 안내를 보여 주고 화면에 남는다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    stubFetch([
      { path: '/api/recordings/7', body: 내방송 },
      { method: 'DELETE', path: '/api/recordings/7', status: 403, body: { detail: '이 방송의 소유자만 할 수 있습니다.' } },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/7')

    await user.click(await screen.findByRole('button', { name: '삭제' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('권한이 없습니다.')
    expect(screen.getByRole('heading', { name: '내 방송' })).toBeInTheDocument()
  })
})
