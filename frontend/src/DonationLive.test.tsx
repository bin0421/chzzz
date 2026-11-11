import { screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 방송자 = { id: 2, nickname: '방송자' }
const 종료된라이브 = {
  id: 7,
  title: '어제의 라이브',
  status: 'ENDED',
  createdAt: '2026-01-01T00:00:00Z',
  startedAt: '2026-01-01T00:00:20Z',
  endedAt: '2026-01-01T01:00:00Z',
  whepUrl: '/media/live/7/whep',
  owner: 방송자,
}
const 방송중 = { ...종료된라이브, status: 'LIVE', endedAt: null }
const 첫후원 = { id: 1, donor: { id: 4, nickname: '첫후원자' }, amount: 1000, confirmedAt: '2026-01-01T00:10:00Z' }
const 새후원 = { id: 2, donor: { id: 5, nickname: '새후원자' }, amount: 50000, confirmedAt: '2026-01-01T00:20:00Z' }

describe('라이브 방송 후원', () => {
  afterEach(() => vi.useRealTimers())

  it('종료된 라이브 방송 시청 화면에도 후원 영역(내역·합계와 후원하기)이 있다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts/7', body: 종료된라이브 },
      { path: '/api/live-broadcasts/7/comments', body: [] },
      { path: '/api/live-broadcasts/7/donations', body: { totalAmount: 1000, count: 1, items: [첫후원] } },
    ])
    renderApp('/live/7')

    const region = within(await screen.findByRole('region', { name: '후원' }))
    expect(await region.findByText('합계 1,000원 · 1건')).toBeInTheDocument()
    expect(region.getByRole('button', { name: '방송자님에게 1,000원 후원하기' })).toBeInTheDocument()
  })

  it('3초마다 다시 불러와 다른 사람의 후원이 내역과 합계에 나타난다', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    stubFetch([
      { path: '/api/live-broadcasts/7', body: 방송중 },
      { path: '/api/live-broadcasts/7/comments', body: [] },
      { path: '/api/live-broadcasts/7/donations', body: { totalAmount: 1000, count: 1, items: [첫후원] } },
    ])
    renderApp('/live/7')
    const region = within(await screen.findByRole('region', { name: '후원' }))
    await region.findByText('합계 1,000원 · 1건')

    stubFetch([
      { path: '/api/live-broadcasts/7', body: 방송중 },
      { path: '/api/live-broadcasts/7/comments', body: [] },
      { path: '/api/live-broadcasts/7/donations', body: { totalAmount: 51000, count: 2, items: [새후원, 첫후원] } },
    ])
    await vi.advanceTimersByTimeAsync(3000)

    expect(await region.findByText('합계 51,000원 · 2건')).toBeInTheDocument()
    expect(region.getByText('새후원자')).toBeInTheDocument()
  })
})
