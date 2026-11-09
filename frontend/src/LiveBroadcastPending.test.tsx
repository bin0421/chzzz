import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createFakeMediaAdapter } from './test/fakeMediaAdapter'
import { stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 대기 = {
  id: 7,
  title: '곧 시작할 라이브',
  status: 'PENDING',
  createdAt: '2026-01-01T00:00:00Z',
  startedAt: null,
  endedAt: null,
  whepUrl: '/media/live/7/whep',
}
const 방송중 = { ...대기, status: 'LIVE', startedAt: '2026-01-01T00:00:20Z' }

describe('시청 화면: 대기 중인 라이브 방송', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('대기면 "곧 방송이 시작됩니다"를 보여 주고, 다시 불러와 방송 중이 되면 시청 연결을 시작한다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    stubFetch([{ path: '/api/live-broadcasts/7', body: 대기 }])
    const media = createFakeMediaAdapter()

    renderApp('/live/7', { media: media.adapter })

    expect(await screen.findByText('곧 방송이 시작됩니다')).toBeInTheDocument()
    expect(media.adapter.watch).not.toHaveBeenCalled()

    stubFetch([{ path: '/api/live-broadcasts/7', body: 방송중 }])
    await vi.advanceTimersByTimeAsync(3000)

    await vi.waitFor(() => expect(media.adapter.watch).toHaveBeenCalledWith('/media/live/7/whep'))
    expect(screen.queryByText('곧 방송이 시작됩니다')).not.toBeInTheDocument()
  })
})
