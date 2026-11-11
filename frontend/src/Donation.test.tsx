import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { createFakePaymentAdapter } from './test/fakePaymentAdapter'
import { requestsTo, stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 방송자 = { id: 2, nickname: '방송자' }
const 녹화방송 = {
  id: 3,
  title: '좋은 방송',
  sourceType: 'EXTERNAL_URL',
  videoUrl: 'https://cdn.example.com/a.mp4',
  createdAt: '2026-11-10T06:00:00Z',
  owner: 방송자,
}
const 빈내역 = { totalAmount: 0, count: 0, items: [] }

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

function donationRegion() {
  return within(screen.getByRole('region', { name: '후원' }))
}

describe('후원하기', () => {
  it('금액을 넣고 후원하기를 누르면 서버가 만든 금액으로 결제위젯을 그리고, 결제하기는 서버의 주문번호로 결제를 요청한다', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-1; path=/'
    const payment = createFakePaymentAdapter()
    const fetchMock = stubFetch([
      { path: '/api/recordings/3', body: 녹화방송 },
      { path: '/api/recordings/3/comments', body: [] },
      { path: '/api/recordings/3/donations', body: 빈내역 },
      { path: '/api/payments/config', body: { clientKey: 'test_gck_client' } },
      {
        method: 'POST',
        path: '/api/recordings/3/donations',
        status: 201,
        body: {
          orderId: 'order-1',
          amount: 5000,
          orderName: '좋은 방송 후원',
          streamer: 방송자,
          customerKey: 'donor-order-1',
        },
      },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/3', { payment: payment.adapter })

    await screen.findByRole('region', { name: '후원' })
    await user.clear(donationRegion().getByLabelText('후원 금액(원)'))
    await user.type(donationRegion().getByLabelText('후원 금액(원)'), '5000')
    await user.click(donationRegion().getByRole('button', { name: '방송자님에게 5,000원 후원하기' }))

    const [, init] = requestsTo(fetchMock, 'POST', '/api/recordings/3/donations')[0]
    expect(JSON.parse(init?.body as string)).toEqual({ amount: 5000 })
    expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe('csrf-1')
    await waitFor(() =>
      expect(payment.mountWidget).toHaveBeenCalledWith({
        clientKey: 'test_gck_client',
        customerKey: 'donor-order-1',
        amount: 5000,
        methodsSelector: '#donation-payment-methods',
        agreementSelector: '#donation-agreement',
      }),
    )

    await user.click(await donationRegion().findByRole('button', { name: '5,000원 결제하기' }))

    expect(payment.requestPayment).toHaveBeenCalledWith({
      orderId: 'order-1',
      orderName: '좋은 방송 후원',
      successUrl: `${window.location.origin}/donations/success`,
      failUrl: `${window.location.origin}/donations/fail`,
    })
  })

  it('방송의 확정된 후원 합계·건수와 내역을 보여 준다', async () => {
    stubFetch([
      { path: '/api/recordings/3', body: 녹화방송 },
      { path: '/api/recordings/3/comments', body: [] },
      {
        path: '/api/recordings/3/donations',
        body: {
          totalAmount: 6000,
          count: 2,
          items: [
            { id: 2, donor: { id: 5, nickname: '두번째' }, amount: 5000, confirmedAt: '2026-11-10T07:00:00Z' },
            { id: 1, donor: { id: 4, nickname: '첫번째' }, amount: 1000, confirmedAt: '2026-11-10T06:30:00Z' },
          ],
        },
      },
    ])
    renderApp('/recordings/3')

    const region = await screen.findByRole('region', { name: '후원' })
    expect(await within(region).findByText('합계 6,000원 · 2건')).toBeInTheDocument()
    const items = within(region).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('두번째')
    expect(items[0]).toHaveTextContent('5,000원')
    expect(items[1]).toHaveTextContent('첫번째')
  })
})

describe('결제 후 돌아온 화면', () => {
  it('성공 주소로 돌아오면 서버에 승인 확인을 보내고 확정 결과를 보여 준다', async () => {
    const fetchMock = stubFetch([
      {
        method: 'POST',
        path: '/api/donations/confirm',
        body: {
          id: 1,
          status: 'CONFIRMED',
          amount: 5000,
          streamer: 방송자,
          broadcast: { type: 'RECORDING', id: 3 },
          confirmedAt: '2026-11-10T07:00:00Z',
        },
      },
    ])
    renderApp('/donations/success?paymentKey=pay_1&orderId=order-1&amount=5000')

    expect(await screen.findByRole('heading', { name: '후원이 확정되었습니다' })).toBeInTheDocument()
    expect(screen.getByText('방송자님에게 5,000원을 후원했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '방송으로 돌아가기' })).toHaveAttribute('href', '/recordings/3')
    const [, init] = requestsTo(fetchMock, 'POST', '/api/donations/confirm')[0]
    expect(JSON.parse(init?.body as string)).toEqual({ paymentKey: 'pay_1', orderId: 'order-1', amount: 5000 })
  })
})
