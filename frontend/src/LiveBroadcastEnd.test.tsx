import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createFakeMediaAdapter } from './test/fakeMediaAdapter'
import { stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 대기 = {
  id: 7,
  title: '저녁 산책 라이브',
  status: 'PENDING',
  createdAt: '2026-11-09T09:00:00Z',
  startedAt: null,
  endedAt: null,
  whepUrl: '/media/live/7/whep',
}
const 방송중 = { ...대기, status: 'LIVE', startedAt: '2026-11-09T09:00:05Z' }
const 종료 = { ...방송중, status: 'ENDED', endedAt: '2026-11-09T09:30:00Z' }

beforeEach(() => {
  sessionStorage.clear()
})

function endRequests(fetchMock: ReturnType<typeof stubFetch>) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => url === '/api/live-broadcasts/7/end' && init?.method === 'POST',
  )
}

/** 송출 화면에서 방송을 시작해 방송 중 상태까지 간다. */
async function startBroadcast(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('제목'), '저녁 산책 라이브')
  await user.click(screen.getByRole('button', { name: '방송 시작' }))
  await screen.findByText('방송 중')
}

// 3단계(ADR-0006): 종료는 송출 키가 아니라 로그인한 소유자 세션으로 한다.
// 그래서 송출 키 헤더와 탭 저장소(sessionStorage) 보관·재개 표시를 확인하던 2단계 테스트를 새 동작으로 바꿨다.
describe('송출 화면의 방송 종료', () => {
  it('방송 종료를 누르면 송출을 멈추고 송출 키 없이 세션으로 종료 API를 부르고 카메라를 끈다', async () => {
    const fetchMock = stubFetch([
      {
        method: 'POST',
        path: '/api/live-broadcasts',
        status: 201,
        body: { ...대기, streamKey: 'secret-key', whipUrl: '/media/live/7/whip' },
      },
      { path: '/api/live-broadcasts/7', body: 방송중 },
      { method: 'POST', path: '/api/live-broadcasts/7/end', body: 종료 },
    ])
    const media = createFakeMediaAdapter()
    const user = userEvent.setup()
    renderApp('/live/new', { media: media.adapter })
    await user.type(screen.getByLabelText('제목'), '저녁 산책 라이브')
    await user.click(screen.getByRole('button', { name: '방송 시작' }))
    await screen.findByText('방송 중')

    await user.click(screen.getByRole('button', { name: '방송 종료' }))

    expect(await screen.findByText('방송이 종료되었습니다.')).toBeInTheDocument()
    expect(media.publication.stop).toHaveBeenCalled()
    const [[, init]] = endRequests(fetchMock)
    expect(new Headers(init?.headers).get('Authorization')).toBeNull()
    for (const track of media.previewStream.getTracks()) expect(track.stop).toHaveBeenCalled()
    expect((screen.getByTestId('live-preview') as HTMLVideoElement).srcObject).toBeNull()
  })

  it('송출 키를 탭 저장소에 남기지 않고, 새로고침하면 "이 탭에서 시작한" 재개 표시 없이 시작 화면이 나온다', async () => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/live-broadcasts',
        status: 201,
        body: { ...대기, streamKey: 'secret-key', whipUrl: '/media/live/7/whip' },
      },
      { path: '/api/live-broadcasts/7', body: 방송중 },
    ])
    const user = userEvent.setup()
    const first = renderApp('/live/new')
    await startBroadcast(user)

    expect(JSON.stringify({ ...sessionStorage })).not.toContain('secret-key')
    first.unmount() // 새로고침: 화면 상태가 사라진다. 종료는 시청 화면에서 소유자로 한다.
    renderApp('/live/new')

    expect(await screen.findByRole('button', { name: '방송 시작' })).toBeInTheDocument()
    expect(screen.queryByText(/이 탭에서 시작한/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '방송 종료' })).not.toBeInTheDocument()
  })

  it('끊김으로 이미 종료된 방송을 종료하려 하면(409) 종료된 것으로 보여 준다', async () => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/live-broadcasts',
        status: 201,
        body: { ...대기, streamKey: 'secret-key', whipUrl: '/media/live/7/whip' },
      },
      { path: '/api/live-broadcasts/7', body: 방송중 },
      {
        method: 'POST',
        path: '/api/live-broadcasts/7/end',
        status: 409,
        body: { status: 409, title: '상태 충돌', detail: '이미 종료된 라이브 방송입니다.' },
      },
    ])
    const user = userEvent.setup()
    renderApp('/live/new')
    await startBroadcast(user)

    await user.click(screen.getByRole('button', { name: '방송 종료' }))

    expect(await screen.findByText('방송이 종료되었습니다.')).toBeInTheDocument()
  })
})

describe('시청 화면의 방송 종료', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('3초마다 상세를 다시 불러와 종료로 바뀌면 연결을 닫고 종료 안내를 보여 준다', async () => {
    const detail = { path: '/api/live-broadcasts/7', body: 방송중 as object }
    stubFetch([detail])
    const media = createFakeMediaAdapter()
    renderApp('/live/7', { media: media.adapter })
    await vi.waitFor(() => expect(media.adapter.watch).toHaveBeenCalled())

    detail.body = 종료
    await vi.advanceTimersByTimeAsync(3000)

    expect(await screen.findByText('방송이 종료되었습니다.')).toBeInTheDocument()
    await vi.waitFor(() => expect(media.viewing.close).toHaveBeenCalled())
    expect(screen.queryByTestId('live-player')).not.toBeInTheDocument()
  })

  it('시청 연결이 끊기면 연결을 닫고 종료 안내를 보여 준다', async () => {
    stubFetch([{ path: '/api/live-broadcasts/7', body: 방송중 }])
    const media = createFakeMediaAdapter()
    renderApp('/live/7', { media: media.adapter })
    await vi.waitFor(() => expect(screen.getByTestId('live-player')).toHaveProperty('srcObject', media.remoteStream))

    act(() => media.disconnect())

    expect(await screen.findByText('방송이 종료되었습니다.')).toBeInTheDocument()
    await vi.waitFor(() => expect(media.viewing.close).toHaveBeenCalled())
  })

  it('종료된 방송을 처음 열면 연결하지 않고 종료 안내와 시작·종료 시각을 보여 준다', async () => {
    stubFetch([{ path: '/api/live-broadcasts/7', body: 종료 }])
    const media = createFakeMediaAdapter()
    renderApp('/live/7', { media: media.adapter })

    expect(await screen.findByText('방송이 종료되었습니다.')).toBeInTheDocument()
    expect(screen.getByText('시작').nextElementSibling?.querySelector('time')).toHaveAttribute(
      'datetime',
      '2026-11-09T09:00:05Z',
    )
    expect(screen.getByText('종료').nextElementSibling?.querySelector('time')).toHaveAttribute(
      'datetime',
      '2026-11-09T09:30:00Z',
    )
    expect(media.adapter.watch).not.toHaveBeenCalled()
  })
})

describe('방송 목록의 종료된 라이브 방송', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('종료된 라이브 방송에 종료 배지와 종료 시각을 보여 주고 5초마다 다시 불러온다', async () => {
    const 방송중항목 = { id: 8, title: '아침 라이브', status: 'LIVE', startedAt: '2026-11-09T10:00:00Z', endedAt: null }
    const 종료항목 = { id: 7, title: '저녁 산책 라이브', status: 'ENDED', startedAt: 방송중.startedAt, endedAt: 종료.endedAt }
    const list = { path: '/api/live-broadcasts', body: [종료항목] as object[] }
    stubFetch([list, { path: '/api/recordings', body: [] }])
    renderApp('/')

    const section = await screen.findByRole('region', { name: '라이브 방송' })
    const ended = await within(section).findByRole('listitem')
    expect(ended).toHaveTextContent('저녁 산책 라이브')
    expect(ended).toHaveTextContent('종료')
    expect(ended).not.toHaveTextContent('방송 중')
    expect(ended.querySelector('time')).toHaveAttribute('datetime', '2026-11-09T09:30:00Z')

    list.body = [방송중항목, 종료항목]
    await vi.advanceTimersByTimeAsync(5000)

    await vi.waitFor(() => expect(within(section).getAllByRole('listitem')).toHaveLength(2))
    expect(within(section).getAllByRole('listitem')[0]).toHaveTextContent('아침 라이브')
  })
})
