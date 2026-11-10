import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { createFakeMediaAdapter } from './test/fakeMediaAdapter'
import { requestsTo, stubFetch } from './test/fetchStub'
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

describe('라이브 방송 송출', () => {
  it('제목을 넣고 방송을 시작하면 미리보기를 WHIP으로 송출하고 방송 중이 되면 알려 준다', async () => {
    const fetchMock = stubFetch([
      {
        method: 'POST',
        path: '/api/live-broadcasts',
        status: 201,
        body: { ...대기, streamKey: 'secret-key', whipUrl: '/media/live/7/whip' },
      },
      { path: '/api/live-broadcasts/7', body: 방송중 },
    ])
    const media = createFakeMediaAdapter()
    const user = userEvent.setup()
    renderApp('/live/new', { media: media.adapter })

    await user.type(screen.getByLabelText('제목'), '저녁 산책 라이브')
    await user.click(screen.getByRole('button', { name: '방송 시작' }))

    expect(await screen.findByText('방송 중')).toBeInTheDocument()
    expect(media.adapter.publish).toHaveBeenCalledWith(media.previewStream, '/media/live/7/whip', 'secret-key')
    const startBody = JSON.parse(requestsTo(fetchMock, 'POST', '/api/live-broadcasts')[0][1]?.body as string)
    expect(startBody).toEqual({ title: '저녁 산책 라이브' })
  })
})

describe('라이브 방송 시청', () => {
  it('방송 중이면 WHEP으로 연결해 원격 영상을 플레이어에 붙인다', async () => {
    stubFetch([{ path: '/api/live-broadcasts/7', body: 방송중 }])
    const media = createFakeMediaAdapter()
    renderApp('/live/7', { media: media.adapter })

    expect(await screen.findByRole('heading', { name: '저녁 산책 라이브' })).toBeInTheDocument()
    await vi.waitFor(() => expect(media.adapter.watch).toHaveBeenCalledWith('/media/live/7/whep'))
    const player = screen.getByTestId('live-player') as HTMLVideoElement
    await vi.waitFor(() => expect(player.srcObject).toBe(media.remoteStream))
  })
})

describe('방송 목록의 라이브 방송', () => {
  it('방송 중인 라이브 방송을 배지와 함께 보여 주고 시청 화면으로 연결한다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts', body: [{ id: 7, title: '저녁 산책 라이브', status: 'LIVE', startedAt: '2026-11-09T09:00:05Z', endedAt: null }] },
      { path: '/api/recordings', body: [] },
      { path: '/api/live-broadcasts/7', body: 방송중 },
    ])
    const user = userEvent.setup()
    renderApp('/')

    const section = await screen.findByRole('region', { name: '라이브 방송' })
    const item = await within(section).findByRole('listitem')
    expect(item).toHaveTextContent('저녁 산책 라이브')
    expect(item).toHaveTextContent('방송 중')

    await user.click(within(item).getByRole('link', { name: /저녁 산책 라이브/ }))

    expect(await screen.findByRole('heading', { name: '저녁 산책 라이브' })).toBeInTheDocument()
  })

  it('헤더의 방송 시작 링크로 송출 화면에 간다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts', body: [] },
      { path: '/api/recordings', body: [] },
    ])
    const user = userEvent.setup()
    renderApp('/')

    await user.click(within(screen.getByRole('banner')).getByRole('link', { name: '방송 시작' }))

    expect(await screen.findByRole('heading', { name: '라이브 방송 시작' })).toBeInTheDocument()
  })
})
