import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { createFakePaymentAdapter } from './test/fakePaymentAdapter'
import { anonymous, requestsTo, stubFetch } from './test/fetchStub'
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

function recordingRoutes(recording: object = 녹화방송) {
  return [
    { path: '/api/recordings/3', body: recording },
    { path: '/api/recordings/3/comments', body: [] },
    { path: '/api/recordings/3/donations', body: 빈내역 },
  ]
}

async function donationRegion() {
  return within(await screen.findByRole('region', { name: '후원' }))
}

afterEach(() => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('후원 금액 고르기', () => {
  it('빠른 선택 버튼(1,000·5,000·10,000·50,000원)을 누르면 그 금액이 후원 버튼에 나온다', async () => {
    stubFetch(recordingRoutes())
    const user = userEvent.setup()
    renderApp('/recordings/3')
    const region = await donationRegion()

    for (const label of ['1,000원', '5,000원', '10,000원', '50,000원']) {
      expect(await region.findByRole('button', { name: label })).toBeInTheDocument()
    }
    await user.click(region.getByRole('button', { name: '10,000원' }))

    expect(region.getByLabelText('후원 금액(원)')).toHaveValue(10000)
    expect(region.getByRole('button', { name: '방송자님에게 10,000원 후원하기' })).toBeInTheDocument()
  })

  it.each(['999', '1000001', '1500.5', ''])(
    '직접 넣은 금액 "%s"이 1,000~1,000,000원 정수가 아니면 이유를 보여 주고 후원을 시작하지 않는다',
    async (typed) => {
      const payment = createFakePaymentAdapter()
      const fetchMock = stubFetch(recordingRoutes())
      const user = userEvent.setup()
      renderApp('/recordings/3', { payment: payment.adapter })
      const region = await donationRegion()

      await user.clear(region.getByLabelText('후원 금액(원)'))
      if (typed) await user.type(region.getByLabelText('후원 금액(원)'), typed)
      await user.click(region.getByRole('button', { name: /후원하기$/ }))

      expect(await region.findByText('후원 금액은 1,000원 이상 1,000,000원 이하의 정수로 입력해 주세요.')).toBeInTheDocument()
      expect(requestsTo(fetchMock, 'POST', '/api/recordings/3/donations')).toHaveLength(0)
      expect(payment.mountWidget).not.toHaveBeenCalled()
    },
  )
})

describe('후원 버튼을 보여 줄지', () => {
  const 내역있음 = {
    totalAmount: 5000,
    count: 1,
    items: [{ id: 1, donor: { id: 9, nickname: '누군가' }, amount: 5000, confirmedAt: '2026-11-10T07:00:00Z' }],
  }

  it('비로그인이면 후원 버튼 대신 로그인 안내와 돌아올 주소가 담긴 로그인 링크를 보여 주고, 내역·합계는 보인다', async () => {
    stubFetch([anonymous, ...recordingRoutes().slice(0, 2), { path: '/api/recordings/3/donations', body: 내역있음 }])
    renderApp('/recordings/3')
    const region = await donationRegion()

    expect(await region.findByText('로그인하면 후원할 수 있습니다.')).toBeInTheDocument()
    expect(region.getByRole('link', { name: '로그인' })).toHaveAttribute('href', `/login?next=${encodeURIComponent('/recordings/3')}`)
    expect(region.queryByRole('button', { name: /후원하기$/ })).not.toBeInTheDocument()
    expect(region.queryByLabelText('후원 금액(원)')).not.toBeInTheDocument()
    expect(region.getByText('합계 5,000원 · 1건')).toBeInTheDocument()
  })

  it('자기 방송에는 후원 버튼이 없고 내역·합계는 보인다', async () => {
    stubFetch([
      { path: '/api/auth/me', body: 방송자 },
      ...recordingRoutes().slice(0, 2),
      { path: '/api/recordings/3/donations', body: 내역있음 },
    ])
    renderApp('/recordings/3')
    const region = await donationRegion()

    expect(await region.findByText('합계 5,000원 · 1건')).toBeInTheDocument()
    expect(region.queryByRole('button', { name: /후원하기$/ })).not.toBeInTheDocument()
    expect(region.queryByLabelText('후원 금액(원)')).not.toBeInTheDocument()
    expect(region.queryByText('로그인하면 후원할 수 있습니다.')).not.toBeInTheDocument()
  })

  it('소유자가 없는 방송에는 후원 버튼이 없고 내역·합계는 보인다', async () => {
    stubFetch(recordingRoutes({ ...녹화방송, owner: null }))
    renderApp('/recordings/3')
    const region = await donationRegion()

    expect(await region.findByText('합계 0원 · 0건')).toBeInTheDocument()
    expect(region.queryByRole('button', { name: /후원하기$/ })).not.toBeInTheDocument()
  })
})

describe('서버가 후원 시작을 거절하면', () => {
  function rejectedStart(status: number, body: object) {
    return { method: 'POST', path: '/api/recordings/3/donations', status, body }
  }

  async function startDonation(routes: Parameters<typeof stubFetch>[0]) {
    const payment = createFakePaymentAdapter()
    stubFetch(routes)
    const user = userEvent.setup()
    renderApp('/recordings/3', { payment: payment.adapter })
    const region = await donationRegion()
    await user.click(await region.findByRole('button', { name: '방송자님에게 1,000원 후원하기' }))
    return { region, payment }
  }

  it('400이면 금액에 대한 서버의 이유를 보여 준다', async () => {
    const { region, payment } = await startDonation([
      ...recordingRoutes(),
      { path: '/api/payments/config', body: { clientKey: 'test_gck_client' } },
      rejectedStart(400, {
        detail: '입력값을 확인해 주세요.',
        errors: [{ field: 'amount', message: '서버가 정한 금액 규칙 안내' }],
      }),
    ])

    expect(await region.findByText('서버가 정한 금액 규칙 안내')).toBeInTheDocument()
    expect(payment.mountWidget).not.toHaveBeenCalled()
  })

  it('401이면 로그인 안내와 로그인 링크를 보여 준다', async () => {
    const { region, payment } = await startDonation([
      ...recordingRoutes(),
      { path: '/api/payments/config', body: { clientKey: 'test_gck_client' } },
      rejectedStart(401, { detail: '로그인이 필요합니다.' }),
    ])

    expect(await region.findByText('로그인하면 후원할 수 있습니다.')).toBeInTheDocument()
    expect(region.getByRole('link', { name: '로그인' })).toHaveAttribute('href', `/login?next=${encodeURIComponent('/recordings/3')}`)
    expect(payment.mountWidget).not.toHaveBeenCalled()
  })

  it('409이면 후원할 수 없는 이유를 보여 준다', async () => {
    const { region, payment } = await startDonation([
      ...recordingRoutes(),
      { path: '/api/payments/config', body: { clientKey: 'test_gck_client' } },
      rejectedStart(409, { detail: '후원할 수 없는 방송입니다.' }),
    ])

    expect((await region.findByRole('alert')).textContent).toBe('후원할 수 없는 방송입니다.')
    expect(payment.mountWidget).not.toHaveBeenCalled()
  })
})
