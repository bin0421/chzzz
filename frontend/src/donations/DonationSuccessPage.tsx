import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../api'
import { broadcastPath, donationApi, formatWon, type DonationResult } from './donationApi'

type ConfirmError = { reason: string; retryable: boolean }

/**
 * 결제 후 돌아오는 성공 화면. 주소의 값은 그대로 믿지 않고 서버에 승인 확인을 보내며,
 * 서버가 확정했다고 답할 때만 확정으로 보여 준다(ADR-0007).
 */
export default function DonationSuccessPage() {
  const [params] = useSearchParams()
  const paymentKey = params.get('paymentKey') ?? ''
  const orderId = params.get('orderId') ?? ''
  const amount = Number(params.get('amount'))
  const [attempt, setAttempt] = useState(0)
  const [result, setResult] = useState<DonationResult>()
  const [error, setError] = useState<ConfirmError>()

  useEffect(() => {
    let cancelled = false
    donationApi.confirm(paymentKey, orderId, amount).then(
      (confirmed) => !cancelled && setResult(confirmed),
      (e) => !cancelled && setError(confirmError(e)),
    )
    return () => {
      cancelled = true
    }
  }, [paymentKey, orderId, amount, attempt])

  function retry() {
    setError(undefined)
    setAttempt((n) => n + 1)
  }

  if (error) {
    return (
      <section>
        <h1>후원을 확인하지 못했습니다</h1>
        <p role="alert" className="notice">
          {error.reason}
        </p>
        {error.retryable && (
          <button type="button" onClick={retry}>
            다시 확인
          </button>
        )}
        <Link to="/">목록으로</Link>
      </section>
    )
  }

  if (!result) return <p role="status">후원을 확인하는 중…</p>

  if (result.status !== 'CONFIRMED') {
    return (
      <section>
        <h1>{result.status === 'FAILED' ? '후원에 실패했습니다' : '후원을 확정하지 못했습니다'}</h1>
        <p className="notice">{result.failure?.message}</p>
        <Link to={broadcastPath(result.broadcast)}>방송으로 돌아가기</Link>
      </section>
    )
  }

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

/** 서버 거절의 이유. 결제 서비스와 통신하지 못한 502만 같은 요청으로 다시 확인할 수 있다. */
function confirmError(e: unknown): ConfirmError {
  if (!(e instanceof ApiError)) return { reason: '후원을 확인하지 못했습니다.', retryable: true }
  return { reason: e.errors[0]?.message ?? e.message, retryable: e.status === 502 }
}
