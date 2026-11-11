import { useEffect, useState, type FormEvent } from 'react'
import type { Member } from '../api'
import { ApiError } from '../api'
import { formatDateTime } from '../format'
import { usePaymentAdapter, type PaymentWidget } from '../payment/PaymentAdapter'
import { donationApi, formatWon, type DonationList, type DonationTarget, type StartedDonation } from './donationApi'

const METHODS_ID = 'donation-payment-methods'
const AGREEMENT_ID = 'donation-agreement'

/**
 * 방송 화면 아래의 후원 영역. 확정된 후원 합계·내역을 보여 주고, 금액을 골라 후원을 시작한다.
 * 서버가 후원(결제 대기)을 만든 뒤 그 금액으로 결제위젯을 그리고, 결제하기는 서버의 주문번호로 요청한다.
 * 확정은 결제 후 돌아온 화면이 서버에 확인한다(ADR-0007).
 */
export default function DonationSection({ kind, id, streamer }: DonationTarget & { streamer?: Member | null }) {
  const payment = usePaymentAdapter()
  const [list, setList] = useState<DonationList | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)
  const [amount, setAmount] = useState('1000')
  const [submitting, setSubmitting] = useState(false)
  const [order, setOrder] = useState<{ started: StartedDonation; clientKey: string } | null>(null)
  const [widget, setWidget] = useState<PaymentWidget | null>(null)
  const [error, setError] = useState<string>()

  useEffect(() => {
    let cancelled = false
    donationApi.list({ kind, id }).then(
      (loaded) => !cancelled && setList(loaded),
      () => !cancelled && setLoadFailed(true),
    )
    return () => {
      cancelled = true
    }
  }, [kind, id])

  // 주문이 생기면 위젯 자리(요소)가 그려진 뒤에 서버가 정한 금액으로 위젯을 그린다.
  useEffect(() => {
    if (!order) return
    let cancelled = false
    let mounted: PaymentWidget | null = null
    payment
      .mountWidget({
        clientKey: order.clientKey,
        customerKey: order.started.customerKey,
        amount: order.started.amount,
        methodsSelector: `#${METHODS_ID}`,
        agreementSelector: `#${AGREEMENT_ID}`,
      })
      .then(
        (created) => {
          mounted = created
          if (cancelled) created.destroy()
          else setWidget(created)
        },
        () => !cancelled && setError('결제 화면을 불러오지 못했습니다. 다시 시도해 주세요.'),
      )
    return () => {
      cancelled = true
      mounted?.destroy()
    }
  }, [order, payment])

  const value = Number(amount)

  async function handleStart(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(undefined)
    try {
      const [{ clientKey }, started] = await Promise.all([donationApi.config(), donationApi.start({ kind, id }, value)])
      setOrder({ started, clientKey })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '후원을 시작하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handlePay() {
    if (!order || !widget) return
    setError(undefined)
    try {
      await widget.requestPayment({
        orderId: order.started.orderId,
        orderName: order.started.orderName,
        successUrl: `${window.location.origin}/donations/success`,
        failUrl: `${window.location.origin}/donations/fail`,
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : '결제를 요청하지 못했습니다.')
    }
  }

  return (
    <section aria-labelledby="donations-heading" className="donations">
      <h2 id="donations-heading">후원</h2>
      {loadFailed && <p className="notice">후원 내역을 불러오지 못했습니다.</p>}
      {list && (
        <>
          <p className="donation-total">
            합계 {formatWon(list.totalAmount)} · {list.count}건
          </p>
          {list.items.length > 0 && (
            <ul>
              {list.items.map((item) => (
                <li key={item.id}>
                  <span className="owner">{item.donor.nickname}</span>
                  <span>{formatWon(item.amount)}</span>
                  <time dateTime={item.confirmedAt}>{formatDateTime(item.confirmedAt)}</time>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      {error && (
        <p role="alert" className="notice">
          {error}
        </p>
      )}
      {streamer && !order && (
        <form className="donation-form" onSubmit={handleStart} noValidate>
          <label>
            후원 금액(원)
            <input type="number" inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </label>
          <button type="submit" disabled={submitting}>
            {streamer.nickname}님에게 {formatWon(value)} 후원하기
          </button>
        </form>
      )}
      {order && (
        <div className="donation-checkout">
          <p>
            {order.started.streamer.nickname}님에게 {formatWon(order.started.amount)}을 후원합니다. 결제수단을 고르고 결제해
            주세요.
          </p>
          <div id={METHODS_ID} />
          <div id={AGREEMENT_ID} />
          <button type="button" onClick={handlePay} disabled={!widget}>
            {formatWon(order.started.amount)} 결제하기
          </button>
        </div>
      )}
    </section>
  )
}
