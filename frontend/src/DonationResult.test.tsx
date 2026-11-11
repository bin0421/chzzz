import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { requestsTo, stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

describe('결제창에서 실패·취소하고 돌아온 화면', () => {
  it('사용자가 취소했으면 서버에 실패 보고를 보내고 취소 결과와 방송으로 돌아가는 링크를 보여 준다', async () => {
    const fetchMock = stubFetch([
      {
        method: 'POST',
        path: '/api/donations/fail',
        body: { status: 'CANCELED', broadcast: { type: 'RECORDING', id: 3 } },
      },
    ])
    renderApp(
      `/donations/fail?code=PAY_PROCESS_CANCELED&message=${encodeURIComponent('결제가 사용자에 의해 취소되었습니다.')}&orderId=order-1`,
    )

    expect(await screen.findByRole('heading', { name: '후원을 취소했습니다' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '방송으로 돌아가기' })).toHaveAttribute('href', '/recordings/3')
    const [, init] = requestsTo(fetchMock, 'POST', '/api/donations/fail')[0]
    expect(JSON.parse(init?.body as string)).toEqual({
      orderId: 'order-1',
      code: 'PAY_PROCESS_CANCELED',
      message: '결제가 사용자에 의해 취소되었습니다.',
    })
  })

  it('취소가 아닌 실패면 "후원에 실패했습니다"와 결제창이 알려 준 이유를 보여 준다', async () => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/donations/fail',
        body: { status: 'FAILED', broadcast: { type: 'LIVE_BROADCAST', id: 7 } },
      },
    ])
    renderApp(`/donations/fail?code=REJECT_CARD_COMPANY&message=${encodeURIComponent('카드사에서 거절했습니다.')}&orderId=order-1`)

    expect(await screen.findByRole('heading', { name: '후원에 실패했습니다' })).toBeInTheDocument()
    expect(screen.getByText('카드사에서 거절했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '방송으로 돌아가기' })).toHaveAttribute('href', '/live/7')
  })

  it('서버가 실패 보고를 거절하면 그 이유를 보여 준다', async () => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/donations/fail',
        status: 409,
        body: { title: '상태 충돌', detail: '이미 확정된 후원입니다.' },
      },
    ])
    renderApp('/donations/fail?code=PAY_PROCESS_CANCELED&message=x&orderId=order-1')

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 확정된 후원입니다.')
  })
})

const 방송자 = { id: 2, nickname: '방송자' }
const successPath = '/donations/success?paymentKey=pay_1&orderId=order-1&amount=5000'

describe('결제 후 성공 주소로 돌아왔지만 서버가 확정하지 않은 경우', () => {
  it('금액이 달라 서버가 400으로 거절하면 그 이유를 보여 준다', async () => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/donations/confirm',
        status: 400,
        body: {
          title: '잘못된 요청',
          detail: '입력값을 확인해 주세요.',
          errors: [{ field: 'amount', message: '결제 금액이 후원 금액과 다릅니다.' }],
        },
      },
    ])
    renderApp(successPath)

    expect(await screen.findByRole('heading', { name: '후원을 확인하지 못했습니다' })).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('결제 금액이 후원 금액과 다릅니다.')
  })

  it.each([
    [403, '자기 후원만 확인할 수 있습니다.'],
    [409, '방송이 사라졌거나 방송자가 바뀌어 후원할 수 없습니다.'],
  ])('서버가 %i로 거절하면 그 이유를 보여 준다', async (status, detail) => {
    stubFetch([{ method: 'POST', path: '/api/donations/confirm', status, body: { title: '거절', detail } }])
    renderApp(successPath)

    expect(await screen.findByRole('alert')).toHaveTextContent(detail)
    expect(screen.queryByRole('button', { name: '다시 확인' })).not.toBeInTheDocument()
  })

  it.each([
    ['FAILED', '후원에 실패했습니다', '한도초과 혹은 잔액부족으로 결제에 실패했습니다.'],
    ['NEEDS_REVIEW', '후원을 확정하지 못했습니다', '결제 서비스의 승인 내용이 후원과 맞지 않아 확인이 필요합니다.'],
  ])('서버가 %s로 답하면 확정으로 보여 주지 않고 이유를 보여 준다', async (status, heading, reason) => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/donations/confirm',
        body: {
          id: 1,
          status,
          amount: 5000,
          streamer: 방송자,
          broadcast: { type: 'RECORDING', id: 3 },
          confirmedAt: null,
          failure: { code: 'X', message: reason },
        },
      },
    ])
    renderApp(successPath)

    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '후원이 확정되었습니다' })).not.toBeInTheDocument()
    expect(screen.getByText(reason)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '방송으로 돌아가기' })).toHaveAttribute('href', '/recordings/3')
  })

  it('결제 서비스와 통신하지 못해 502면 이유와 "다시 확인" 버튼을 보여 주고, 누르면 다시 확인해 확정 결과를 보여 준다', async () => {
    const confirmRoute = {
      method: 'POST',
      path: '/api/donations/confirm',
      status: 502,
      body: { title: '바깥 서비스 통신 실패', detail: '결제 서비스와 연결하지 못했습니다. 잠시 후 다시 확인해 주세요.' } as unknown,
    }
    const fetchMock = stubFetch([confirmRoute])
    const user = userEvent.setup()
    renderApp(successPath)

    expect(await screen.findByRole('alert')).toHaveTextContent('결제 서비스와 연결하지 못했습니다.')
    confirmRoute.status = 200
    confirmRoute.body = {
      id: 1,
      status: 'CONFIRMED',
      amount: 5000,
      streamer: 방송자,
      broadcast: { type: 'RECORDING', id: 3 },
      confirmedAt: '2026-11-10T07:00:00Z',
      failure: null,
    }
    await user.click(screen.getByRole('button', { name: '다시 확인' }))

    expect(await screen.findByRole('heading', { name: '후원이 확정되었습니다' })).toBeInTheDocument()
    const requests = requestsTo(fetchMock, 'POST', '/api/donations/confirm')
    expect(requests).toHaveLength(2)
    expect(JSON.parse(requests[1][1]?.body as string)).toEqual({ paymentKey: 'pay_1', orderId: 'order-1', amount: 5000 })
  })
})
