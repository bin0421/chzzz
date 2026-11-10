import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { anonymous, defaultMember, requestsTo, stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 녹화방송 = {
  id: 1,
  title: '첫 방송 다시보기',
  sourceType: 'EXTERNAL_URL',
  videoUrl: 'https://cdn.example.com/a.mp4',
  createdAt: '2026-11-09T06:00:00Z',
  owner: { id: 9, nickname: '방송주인' },
}

const 첫댓글 = {
  id: 10,
  content: '잘 봤습니다',
  author: { id: 2, nickname: '시청자A' },
  createdAt: '2026-11-09T07:00:00Z',
  updatedAt: null,
}

const 고친댓글 = {
  id: 11,
  content: '고쳐 쓴 댓글',
  author: { id: 3, nickname: '시청자B' },
  createdAt: '2026-11-09T07:05:00Z',
  updatedAt: '2026-11-09T07:10:00Z',
}

describe('녹화방송 댓글', () => {
  it('상세 화면 아래에 댓글을 오래된 순으로 닉네임·작성 시각·수정됨과 함께 보여 준다', async () => {
    stubFetch([
      { path: '/api/recordings/1', body: 녹화방송 },
      { path: '/api/recordings/1/comments', body: [첫댓글, 고친댓글] },
    ])
    renderApp('/recordings/1')

    const region = await screen.findByRole('region', { name: '댓글' })
    const items = await within(region).findAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('시청자A')
    expect(items[0]).toHaveTextContent('잘 봤습니다')
    expect(items[0]).toHaveTextContent(localTime('2026-11-09T07:00:00Z'))
    expect(items[0]).not.toHaveTextContent('수정됨')
    expect(items[1]).toHaveTextContent('시청자B')
    expect(items[1]).toHaveTextContent('수정됨')
  })

  it('비로그인이면 작성란 대신 로그인 안내와 돌아올 주소가 담긴 로그인 링크를 보여 준다', async () => {
    stubFetch([
      anonymous,
      { path: '/api/recordings/1', body: 녹화방송 },
      { path: '/api/recordings/1/comments', body: [첫댓글] },
    ])
    renderApp('/recordings/1')

    const region = await screen.findByRole('region', { name: '댓글' })
    expect(await within(region).findByText('로그인하면 댓글을 쓸 수 있습니다.')).toBeInTheDocument()
    expect(within(region).getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      '/login?next=%2Frecordings%2F1',
    )
    expect(within(region).queryByRole('textbox')).not.toBeInTheDocument()
  })

  it('로그인한 회원이 쓴 댓글은 CSRF 헤더와 함께 보내지고 바로 목록에 나타난다', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-token-1'
    const 새댓글 = { ...첫댓글, id: 12, content: '새 댓글', author: defaultMember }
    const fetchMock = stubFetch([
      { path: '/api/recordings/1', body: 녹화방송 },
      { path: '/api/recordings/1/comments', body: [첫댓글] },
      { method: 'POST', path: '/api/recordings/1/comments', status: 201, body: 새댓글 },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/1')

    const region = await screen.findByRole('region', { name: '댓글' })
    await user.type(await within(region).findByRole('textbox', { name: '댓글 쓰기' }), '새 댓글')
    await user.click(within(region).getByRole('button', { name: '등록' }))

    expect(await within(region).findByText('새 댓글')).toBeInTheDocument()
    expect(within(region).getAllByRole('listitem')).toHaveLength(2)
    expect(within(region).getByRole('textbox', { name: '댓글 쓰기' })).toHaveValue('')
    const [[, init]] = requestsTo(fetchMock, 'POST', '/api/recordings/1/comments')
    expect(JSON.parse(init!.body as string)).toEqual({ content: '새 댓글' })
    expect(new Headers(init!.headers).get('X-XSRF-TOKEN')).toBe('csrf-token-1')
  })

  it('서버가 내용 규칙 위반(400)을 알리면 작성란 옆에 이유를 보여 준다', async () => {
    stubFetch([
      { path: '/api/recordings/1', body: 녹화방송 },
      { path: '/api/recordings/1/comments', body: [] },
      {
        method: 'POST',
        path: '/api/recordings/1/comments',
        status: 400,
        body: { detail: '입력값을 확인해 주세요.', errors: [{ field: 'content', message: '댓글은 500자 이하로 입력해 주세요.' }] },
      },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/1')

    const region = await screen.findByRole('region', { name: '댓글' })
    await user.type(await within(region).findByRole('textbox', { name: '댓글 쓰기' }), '너무 긴 댓글')
    await user.click(within(region).getByRole('button', { name: '등록' }))

    expect(await within(region).findByText('댓글은 500자 이하로 입력해 주세요.')).toBeInTheDocument()
    expect(within(region).getByRole('textbox', { name: '댓글 쓰기' })).toHaveValue('너무 긴 댓글')
  })

  it('빈 댓글은 보내지 않고 안내한다', async () => {
    const fetchMock = stubFetch([
      { path: '/api/recordings/1', body: 녹화방송 },
      { path: '/api/recordings/1/comments', body: [] },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/1')

    const region = await screen.findByRole('region', { name: '댓글' })
    await user.type(await within(region).findByRole('textbox', { name: '댓글 쓰기' }), '   ')
    await user.click(within(region).getByRole('button', { name: '등록' }))

    expect(await within(region).findByText('댓글 내용을 입력해 주세요.')).toBeInTheDocument()
    expect(requestsTo(fetchMock, 'POST', '/api/recordings/1/comments')).toHaveLength(0)
  })
})

describe('댓글 수정·삭제', () => {
  const 내댓글 = { ...첫댓글, id: 20, content: '내가 쓴 댓글', author: defaultMember }
  const 남의댓글 = { ...첫댓글, id: 21, content: '남이 쓴 댓글', author: { id: 2, nickname: '시청자A' } }

  function stubWith(extra: Parameters<typeof stubFetch>[0] = []) {
    return stubFetch([
      { path: '/api/recordings/1', body: 녹화방송 },
      { path: '/api/recordings/1/comments', body: [내댓글, 남의댓글] },
      ...extra,
    ])
  }

  async function commentItem(text: string) {
    const region = await screen.findByRole('region', { name: '댓글' })
    return (await within(region).findByText(text)).closest('li') as HTMLElement
  }

  afterEach(() => vi.restoreAllMocks())

  it('작성자에게만 수정·삭제 버튼이 보인다', async () => {
    stubWith()
    renderApp('/recordings/1')

    const mine = await commentItem('내가 쓴 댓글')
    const others = await commentItem('남이 쓴 댓글')
    expect(within(mine).getByRole('button', { name: '수정' })).toBeInTheDocument()
    expect(within(mine).getByRole('button', { name: '삭제' })).toBeInTheDocument()
    expect(within(others).queryByRole('button')).not.toBeInTheDocument()
  })

  it('비로그인이면 어떤 댓글에도 수정·삭제 버튼이 없다', async () => {
    stubWith([anonymous])
    renderApp('/recordings/1')

    const region = await screen.findByRole('region', { name: '댓글' })
    await within(region).findByText('로그인하면 댓글을 쓸 수 있습니다.')
    expect(within(region).queryByRole('button')).not.toBeInTheDocument()
  })

  it('수정을 누르면 그 자리에서 고치고, 저장하면 고친 내용과 수정됨이 보인다', async () => {
    const fetchMock = stubWith([
      {
        method: 'PATCH',
        path: '/api/comments/20',
        body: { ...내댓글, content: '고친 내 댓글', updatedAt: '2026-11-09T08:00:00Z' },
      },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/1')

    const mine = await commentItem('내가 쓴 댓글')
    await user.click(within(mine).getByRole('button', { name: '수정' }))
    const editor = within(mine).getByRole('textbox', { name: '댓글 수정' })
    expect(editor).toHaveValue('내가 쓴 댓글')
    expect(editor).toHaveFocus()
    await user.clear(editor)
    await user.type(editor, '고친 내 댓글')
    await user.click(within(mine).getByRole('button', { name: '저장' }))

    expect(await within(mine).findByText('고친 내 댓글')).toBeInTheDocument()
    expect(mine).toHaveTextContent('수정됨')
    expect(within(mine).queryByRole('textbox')).not.toBeInTheDocument()
    const [[, init]] = requestsTo(fetchMock, 'PATCH', '/api/comments/20')
    expect(JSON.parse(init!.body as string)).toEqual({ content: '고친 내 댓글' })
  })

  it('수정 중 취소하면 원래 내용으로 돌아간다', async () => {
    stubWith()
    const user = userEvent.setup()
    renderApp('/recordings/1')

    const mine = await commentItem('내가 쓴 댓글')
    await user.click(within(mine).getByRole('button', { name: '수정' }))
    await user.type(within(mine).getByRole('textbox', { name: '댓글 수정' }), ' 덧붙임')
    await user.click(within(mine).getByRole('button', { name: '취소' }))

    expect(within(mine).getByText('내가 쓴 댓글')).toBeInTheDocument()
  })

  it('삭제는 확인한 뒤에만 요청하고, 지우면 목록에서 사라진다', async () => {
    const fetchMock = stubWith([{ method: 'DELETE', path: '/api/comments/20', status: 204 }])
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const user = userEvent.setup()
    renderApp('/recordings/1')

    const mine = await commentItem('내가 쓴 댓글')
    await user.click(within(mine).getByRole('button', { name: '삭제' }))
    expect(requestsTo(fetchMock, 'DELETE', '/api/comments/20')).toHaveLength(0)

    await user.click(within(mine).getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(screen.queryByText('내가 쓴 댓글')).not.toBeInTheDocument())
    expect(confirm).toHaveBeenCalledTimes(2)
    expect(requestsTo(fetchMock, 'DELETE', '/api/comments/20')).toHaveLength(1)
  })

  it('서버가 작성자가 아니라고(403) 거절하면 권한 없음 안내를 보여 주고 댓글은 남는다', async () => {
    stubWith([
      { method: 'DELETE', path: '/api/comments/20', status: 403, body: { detail: '댓글 작성자만 할 수 있습니다.' } },
    ])
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    renderApp('/recordings/1')

    const mine = await commentItem('내가 쓴 댓글')
    await user.click(within(mine).getByRole('button', { name: '삭제' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('댓글 작성자만 수정·삭제할 수 있습니다.')
    expect(screen.getByText('내가 쓴 댓글')).toBeInTheDocument()
  })

  it('세션이 끝나 서버가 401로 거절하면 로그인 안내로 바뀐다', async () => {
    stubWith([
      { method: 'PATCH', path: '/api/comments/20', status: 401, body: { detail: '로그인이 필요합니다.' } },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/1')

    const mine = await commentItem('내가 쓴 댓글')
    await user.click(within(mine).getByRole('button', { name: '수정' }))
    await user.click(within(mine).getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('로그인이 필요합니다. 다시 로그인해 주세요.')
    expect(screen.getByText('로그인하면 댓글을 쓸 수 있습니다.')).toBeInTheDocument()
    expect(screen.getByText('내가 쓴 댓글')).toBeInTheDocument()
  })
})

describe('라이브 방송 댓글', () => {
  const 종료된라이브 = {
    id: 7,
    title: '어제의 라이브',
    status: 'ENDED',
    createdAt: '2026-01-01T00:00:00Z',
    startedAt: '2026-01-01T00:00:20Z',
    endedAt: '2026-01-01T01:00:00Z',
    whepUrl: '/media/live/7/whep',
  }
  const 방송중 = { ...종료된라이브, status: 'LIVE', endedAt: null }

  afterEach(() => vi.useRealTimers())

  it('종료된 라이브 방송 시청 화면에도 댓글 목록과 작성란이 있다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts/7', body: 종료된라이브 },
      { path: '/api/live-broadcasts/7/comments', body: [첫댓글] },
    ])
    renderApp('/live/7')

    const region = await screen.findByRole('region', { name: '댓글' })
    expect(await within(region).findByText('잘 봤습니다')).toBeInTheDocument()
    expect(within(region).getByRole('textbox', { name: '댓글 쓰기' })).toBeInTheDocument()
  })

  it('3초마다 다시 불러와 다른 사람이 쓴 댓글이 나타난다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    stubFetch([
      { path: '/api/live-broadcasts/7', body: 방송중 },
      { path: '/api/live-broadcasts/7/comments', body: [첫댓글] },
    ])
    renderApp('/live/7')
    const region = await screen.findByRole('region', { name: '댓글' })
    await within(region).findByText('잘 봤습니다')

    stubFetch([
      { path: '/api/live-broadcasts/7', body: 방송중 },
      { path: '/api/live-broadcasts/7/comments', body: [첫댓글, 고친댓글] },
    ])
    await vi.advanceTimersByTimeAsync(3000)

    expect(await within(region).findByText('고쳐 쓴 댓글')).toBeInTheDocument()
  })
})

function localTime(iso: string) {
  return new Date(iso).toLocaleString('ko-KR')
}
