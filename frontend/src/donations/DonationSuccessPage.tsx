import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../api'
import { broadcastPath, donationApi, formatWon, type DonationResult } from './donationApi'

/**
 * 결제 후 돌아오는 성공 화면. 주소의 값은 그대로 믿지 않고 서버에 승인 확인을 보내며,
 * 서버가 확정했다고 답할 때만 확정으로 보여 준다(ADR-0007).
 */
export default function DonationSuccessPage() {
  const [params] = useSearchParams()
  const paymentKey = params.get('paymentKey') ?? ''
  const orderId = params.get('orderId') ?? ''
  const amount = Number(params.get('amount'))
  const [result, setResult] = useState<DonationResult>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    let cancelled = false
    donationApi.confirm(paymentKey, orderId, amount).then(
      (confirmed) => !cancelled && setResult(confirmed),
      (e) => !cancelled && setError(e instanceof ApiError ? e.message : '후원을 확인하지 못했습니다.'),
    )
    return () => {
      cancelled = true
    }
  }, [paymentKey, orderId, amount])

  if (error) {
    return (
      <section>
        <h1>후원을 확인하지 못했습니다</h1>
        <p role="alert" className="notice">
          {error}
        </p>
        <Link to="/">목록으로</Link>
      </section>
    )
  }

  if (!result) return <p role="status">후원을 확인하는 중…</p>

  return (
    <section>
      <h1>후원이 확정되었습니다</h1>
      <p>
        {result.streamer.nickname}님에게 {formatWon(result.amount)}을 후원했습니다.
      </p>
      <Link to={broadcastPath(result.broadcast)}>방송으로 돌아가기</Link>
    </section>
  )
}
