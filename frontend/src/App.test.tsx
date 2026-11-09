import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 첫방송 = {
  id: 1,
  title: '첫 방송 다시보기',
  sourceType: 'EXTERNAL_URL',
  videoUrl: 'https://cdn.example.com/videos/first.mp4',
  createdAt: '2026-11-09T06:00:00Z',
}

describe('녹화방송 등록', () => {
  it('외부 URL로 등록하면 상세 화면으로 이동해 그 URL을 재생한다', async () => {
    const fetchMock = stubFetch([
      { method: 'POST', path: '/api/recordings', status: 201, body: 첫방송 },
      { path: '/api/recordings/1', body: 첫방송 },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.type(screen.getByLabelText('제목'), '첫 방송 다시보기')
    await user.type(screen.getByLabelText('영상 URL'), 'https://cdn.example.com/videos/first.mp4')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByRole('heading', { name: '첫 방송 다시보기' })).toBeInTheDocument()
    expect(screen.getByTestId('recording-player')).toHaveAttribute(
      'src',
      'https://cdn.example.com/videos/first.mp4',
    )
    const body = fetchMock.mock.calls[0][1]?.body as FormData
    expect(body.get('title')).toBe('첫 방송 다시보기')
    expect(body.get('url')).toBe('https://cdn.example.com/videos/first.mp4')
  })
})

describe('녹화방송 목록', () => {
  it('등록된 녹화방송 제목을 보여 주고 누르면 상세로 이동한다', async () => {
    stubFetch([
      {
        path: '/api/recordings',
        body: [
          { id: 2, title: '두 번째 방송', sourceType: 'EXTERNAL_URL', createdAt: '2026-11-09T07:00:00Z' },
          { id: 1, title: '첫 방송 다시보기', sourceType: 'EXTERNAL_URL', createdAt: '2026-11-09T06:00:00Z' },
        ],
      },
      { path: '/api/recordings/1', body: 첫방송 },
    ])
    const user = userEvent.setup()
    renderApp('/')

    const items = await screen.findAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('두 번째 방송')
    expect(items[1]).toHaveTextContent('첫 방송 다시보기')

    await user.click(screen.getByRole('link', { name: /첫 방송 다시보기/ }))

    expect(await screen.findByRole('heading', { name: '첫 방송 다시보기' })).toBeInTheDocument()
  })
})
