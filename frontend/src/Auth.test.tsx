import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { anonymous, requestsTo, stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 스트리머 = { id: 7, nickname: '스트리머' }

function header() {
  return within(screen.getByRole('banner'))
}

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('회원가입', () => {
  it('아이디·비밀번호·닉네임으로 가입하면 바로 로그인되어 헤더에 닉네임이 보이고 목록으로 간다', async () => {
    const fetchMock = stubFetch([
      anonymous,
      { method: 'POST', path: '/api/auth/signup', status: 201, body: 스트리머 },
      { path: '/api/live-broadcasts', body: [] },
      { path: '/api/recordings', body: [] },
    ])
    const user = userEvent.setup()
    renderApp('/signup')

    await user.type(await screen.findByLabelText('아이디'), 'streamer')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('닉네임'), '스트리머')
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await header().findByText('스트리머님')).toBeInTheDocument()
    expect(header().getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '녹화방송' })).toBeInTheDocument()
    const [, init] = requestsTo(fetchMock, 'POST', '/api/auth/signup')[0]
    expect(JSON.parse(init?.body as string)).toEqual({
      loginId: 'streamer',
      password: 'password123',
      nickname: '스트리머',
    })
  })

  it('서버가 아이디·닉네임 중복을 알려 주면 각 입력란 옆에 보여 준다', async () => {
    stubFetch([
      anonymous,
      {
        method: 'POST',
        path: '/api/auth/signup',
        status: 409,
        body: {
          errors: [
            { field: 'loginId', message: '이미 사용 중인 아이디입니다.' },
            { field: 'nickname', message: '이미 사용 중인 닉네임입니다.' },
          ],
        },
      },
    ])
    const user = userEvent.setup()
    renderApp('/signup')

    await user.type(await screen.findByLabelText('아이디'), 'streamer')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.type(screen.getByLabelText('닉네임'), '스트리머')
    await user.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText('이미 사용 중인 아이디입니다.')).toBeInTheDocument()
    expect(screen.getByLabelText('아이디')).toHaveAccessibleDescription('이미 사용 중인 아이디입니다.')
    expect(screen.getByLabelText('닉네임')).toHaveAccessibleDescription('이미 사용 중인 닉네임입니다.')
    expect(header().getByRole('link', { name: '로그인' })).toBeInTheDocument()
  })
})

describe('로그인·로그아웃', () => {
  it('로그인하면 헤더가 닉네임과 로그아웃으로 바뀐다', async () => {
    stubFetch([
      anonymous,
      { method: 'POST', path: '/api/auth/login', body: 스트리머 },
      { path: '/api/live-broadcasts', body: [] },
      { path: '/api/recordings', body: [] },
    ])
    const user = userEvent.setup()
    renderApp('/login')

    expect(await header().findByRole('link', { name: '회원가입' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('아이디'), 'streamer')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await header().findByText('스트리머님')).toBeInTheDocument()
    expect(header().queryByRole('link', { name: '로그인' })).not.toBeInTheDocument()
  })

  it('아이디나 비밀번호가 틀리면 한 가지 안내를 보여 준다', async () => {
    stubFetch([
      anonymous,
      {
        method: 'POST',
        path: '/api/auth/login',
        status: 401,
        body: { detail: '아이디 또는 비밀번호가 올바르지 않습니다.' },
      },
    ])
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText('아이디'), 'streamer')
    await user.type(screen.getByLabelText('비밀번호'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('아이디 또는 비밀번호가 올바르지 않습니다.')
  })

  it('로그아웃하면 서버 세션을 끝내고 헤더가 로그인·회원가입으로 바뀐다', async () => {
    const fetchMock = stubFetch([
      { path: '/api/auth/me', body: 스트리머 },
      { method: 'POST', path: '/api/auth/logout', status: 204 },
      { path: '/api/live-broadcasts', body: [] },
      { path: '/api/recordings', body: [] },
    ])
    const user = userEvent.setup()
    renderApp('/')

    await user.click(await header().findByRole('button', { name: '로그아웃' }))

    expect(await header().findByRole('link', { name: '로그인' })).toBeInTheDocument()
    expect(header().getByRole('link', { name: '회원가입' })).toBeInTheDocument()
    expect(requestsTo(fetchMock, 'POST', '/api/auth/logout')).toHaveLength(1)
  })
})

describe('로그인이 필요한 화면', () => {
  it('비로그인으로 녹화방송 등록에 들어가면 로그인 화면으로 가고, 로그인하면 돌아온다', async () => {
    stubFetch([anonymous, { method: 'POST', path: '/api/auth/login', body: 스트리머 }])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('아이디'), 'streamer')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('heading', { name: '녹화방송 등록' })).toBeInTheDocument()
  })

  it('다른 사이트 주소를 next로 넘겨도 로그인 뒤에는 서비스 안으로만 간다', async () => {
    stubFetch([
      anonymous,
      { method: 'POST', path: '/api/auth/login', body: 스트리머 },
      { path: '/api/live-broadcasts', body: [] },
      { path: '/api/recordings', body: [] },
    ])
    const user = userEvent.setup()
    renderApp('/login?next=//evil.example.com')

    await user.type(await screen.findByLabelText('아이디'), 'streamer')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('heading', { name: '녹화방송' })).toBeInTheDocument()
  })
})

describe('CSRF 토큰', () => {
  it('상태를 바꾸는 요청에 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 싣는다', async () => {
    document.cookie = 'XSRF-TOKEN=token-from-cookie; path=/'
    const fetchMock = stubFetch([
      { path: '/api/auth/me', body: 스트리머 },
      { method: 'POST', path: '/api/auth/logout', status: 204 },
      { path: '/api/live-broadcasts', body: [] },
      { path: '/api/recordings', body: [] },
    ])
    const user = userEvent.setup()
    renderApp('/')

    await user.click(await header().findByRole('button', { name: '로그아웃' }))

    await waitFor(() => expect(requestsTo(fetchMock, 'POST', '/api/auth/logout')).toHaveLength(1))
    const [, init] = requestsTo(fetchMock, 'POST', '/api/auth/logout')[0]
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('token-from-cookie')
  })
})

describe('방송 소유자 표시', () => {
  it('녹화방송 목록과 상세에 소유자 닉네임을 보여 준다', async () => {
    const recording = {
      id: 3,
      title: '내 방송',
      sourceType: 'EXTERNAL_URL',
      createdAt: '2026-11-10T06:00:00Z',
      owner: 스트리머,
    }
    stubFetch([
      { path: '/api/live-broadcasts', body: [] },
      { path: '/api/recordings', body: [recording, { ...recording, id: 1, title: '옛 방송', owner: null }] },
      { path: '/api/recordings/3', body: { ...recording, videoUrl: 'https://cdn.example.com/mine.mp4' } },
    ])
    const user = userEvent.setup()
    renderApp('/')

    const items = await screen.findAllByRole('listitem')
    expect(items[0]).toHaveTextContent('스트리머')
    expect(items[1]).not.toHaveTextContent('스트리머')

    await user.click(screen.getByRole('link', { name: '내 방송' }))
    expect(await screen.findByRole('heading', { name: '내 방송' })).toBeInTheDocument()
    expect(screen.getByText('스트리머')).toBeInTheDocument()
  })
})
