import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import type { Member } from '../api'
import { ApiError } from '../api'
import { useAuth } from '../auth/auth'
import { formatDateTime } from '../format'
import { usePaymentAdapter, type PaymentWidget } from '../payment/PaymentAdapter'
import { checkDonationAmount } from './donationAmountRule'
import { donationApi, formatWon, type DonationList, type DonationTarget, type StartedDonation } from './donationApi'

const METHODS_ID = 'donation-payment-methods'
const AGREEMENT_ID = 'donation-agreement'
const QUICK_AMOUNTS = [1000, 5000, 10000, 50000]

/**
 * 방송 화면 아래의 후원 영역. 확정된 후원 합계·내역을 보여 주고, 금액을 골라 후원을 시작한다.
 * 서버가 후원(결제 대기)을 만든 뒤 그 금액으로 결제위젯을 그리고, 결제하기는 서버의 주문번호로 요청한다.
 * 확정은 결제 후 돌아온 화면이 서버에 확인한다(ADR-0007).
 */
export default function DonationSection({
  kind,
  id,
  streamer,
  pollMs,
}: DonationTarget & { streamer?: Member | null; pollMs?: number }) {
  const payment = usePaymentAdapter()
  const { auth } = useAuth()
  const location = useLocation()
  // 버튼을 숨기는 것은 보조이고, 소유자 없음·자기 방송은 서버가 409로 거절한다.
  const canDonate = !!streamer && auth.status === 'member' && auth.member.id !== streamer.id
  const [list, setList] = useState<DonationList | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)
  const [amount, setAmount] = useState('1000')
  const [submitting, setSubmitting] = useState(false)
  const [order, setOrder] = useState<{ started: StartedDonation; clientKey: string } | null>(null)
  const [widget, setWidget] = useState<PaymentWidget | null>(null)
  const [error, setError] = useState<string>()
  const [amountError, setAmountError] = useState<string>()

  // pollMs가 있으면(라이브 방송) 주기적으로 다시 불러와 다른 사람의 후원을 보여 준다.
  useEffect(() => {
    let cancelled = false
    let loaded = false
    const load = () =>
      donationApi.list({ kind, id }).then(
        (list) => {
          loaded = true
          if (cancelled) return
          setList(list)
          setLoadFailed(false)
        },
        () => {
          // 처음 불러오기 실패만 알린다. 폴링 중 일시적 실패는 다음 폴링에 맡긴다.
          if (!cancelled && !loaded) setLoadFailed(true)
        },
      )
    load()
    const timer = pollMs ? setInterval(load, pollMs) : undefined
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [kind, id, pollMs])

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
    const problem = checkDonationAmount(amount)
    setAmountError(problem)
    setError(undefined)
    if (problem) return
    setSubmitting(true)
    try {
      const [{ clientKey }, started] = await Promise.all([donationApi.config(), donationApi.start({ kind, id }, value)])
      setOrder({ started, clientKey })
    } catch (e) {
      // 401이면 로그인 상태가 비워져 로그인 안내가 나오고, 400은 금액 칸에, 그 밖(409 등)은 서버의 이유를 보여 준다.
      const amountProblem = e instanceof ApiError ? e.errors.find((err) => err.field === 'amount')?.message : undefined
      if (amountProblem) setAmountError(amountProblem)
      else setError(e instanceof ApiError ? e.message : '후원을 시작하지 못했습니다. 다시 시도해 주세요.')
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
      {canDonate && !order && (
        <form className="donation-form" onSubmit={handleStart} noValidate>
          <p className="donation-quick">
            {QUICK_AMOUNTS.map((quick) => (
              <button key={quick} type="button" onClick={() => setAmount(String(quick))}>
                {formatWon(quick)}
              </button>
            ))}
          </p>
          <label>
            후원 금액(원)
            <input
              type="number"
              inputMode="numeric"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              aria-invalid={amountError ? true : undefined}
              aria-describedby={amountError ? 'donation-amount-error' : undefined}
            />
          </label>
          {amountError && (
            <p id="donation-amount-error" className="field-error">
              {amountError}
            </p>
          )}
          <button type="submit" disabled={submitting}>
            {streamer.nickname}님에게 {formatWon(value)} 후원하기
          </button>
        </form>
      )}
      {streamer && auth.status === 'anonymous' && (
        <p>
          <span>로그인하면 후원할 수 있습니다.</span>{' '}
          <Link to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}>로그인</Link>
        </p>
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
