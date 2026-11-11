import { json, request, type Member } from '../api'

/** 후원 대상 방송. 녹화방송과 라이브 방송은 식별자가 겹칠 수 있어 종류로 구분한다. */
export type DonationTarget = { kind: 'recording' | 'live'; id: string | number }

export type StartedDonation = {
  orderId: string
  amount: number
  orderName: string
  streamer: Member
  customerKey: string
}

export type DonationStatus = 'AWAITING_PAYMENT' | 'CONFIRMED' | 'FAILED' | 'CANCELED' | 'NEEDS_REVIEW'

export type DonationResult = {
  id: number
  status: DonationStatus
  amount: number
  streamer: Member
  broadcast: { type: 'RECORDING' | 'LIVE_BROADCAST'; id: number }
  confirmedAt: string | null
}

export type DonationList = {
  totalAmount: number
  count: number
  items: { id: number; donor: Member; amount: number; confirmedAt: string }[]
}

function donationsPath(target: DonationTarget) {
  const broadcasts = target.kind === 'recording' ? 'recordings' : 'live-broadcasts'
  return `/api/${broadcasts}/${target.id}/donations`
}

export const donationApi = {
  config() {
    return request<{ clientKey: string }>('/api/payments/config')
  },
  list(target: DonationTarget) {
    return request<DonationList>(donationsPath(target))
  },
  start(target: DonationTarget, amount: number) {
    return request<StartedDonation>(donationsPath(target), json('POST', { amount }))
  },
  confirm(paymentKey: string, orderId: string, amount: number) {
    return request<DonationResult>('/api/donations/confirm', json('POST', { paymentKey, orderId, amount }))
  },
}

/** 확정 결과에서 후원한 방송 화면의 주소. */
export function broadcastPath(broadcast: DonationResult['broadcast']) {
  return broadcast.type === 'RECORDING' ? `/recordings/${broadcast.id}` : `/live/${broadcast.id}`
}

export function formatWon(amount: number) {
  return `${amount.toLocaleString('ko-KR')}원`
}
