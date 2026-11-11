import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { requestsTo, stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const successPath = '/donations/success?paymentKey=pay_1&orderId=order-1&amount=5000'

describe('확정 결과 화면을 새로고침한 경우', () => {
  it('다시 열어도 같은 확인 요청을 보내고 서버가 돌려준 같은 확정 화면을 보여 준다', async () => {
    // 서버는 이미 확정된 주문의 같은 확인 요청에 승인을 다시 요청하지 않고 같은 결과를 돌려준다(ADR-0008).
    const fetchMock = stubFetch([
      {
        method: 'POST',
        path: '/api/donations/confirm',
        body: {
          id: 1,
          status: 'CONFIRMED',
          amount: 5000,
          streamer: { id: 2, nickname: '방송자' },
          broadcast: { type: 'LIVE_BROADCAST', id: 7 },
          confirmedAt: '2026-11-11T07:00:00Z',
        },
      },
    ])

    const first = renderApp(successPath)
    expect(await screen.findByRole('heading', { name: '후원이 확정되었습니다' })).toBeInTheDocument()
    first.unmount()

    renderApp(successPath)
    expect(await screen.findByRole('heading', { name: '후원이 확정되었습니다' })).toBeInTheDocument()
    expect(screen.getByText('방송자님에게 5,000원을 후원했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '방송으로 돌아가기' })).toHaveAttribute('href', '/live/7')

    const bodies = requestsTo(fetchMock, 'POST', '/api/donations/confirm').map(([, init]) => JSON.parse(init?.body as string))
    expect(bodies).toEqual([
      { paymentKey: 'pay_1', orderId: 'order-1', amount: 5000 },
      { paymentKey: 'pay_1', orderId: 'order-1', amount: 5000 },
    ])
  })
})
