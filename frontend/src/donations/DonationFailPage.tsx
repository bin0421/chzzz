import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import ActionErrorNotice from '../pages/ActionErrorNotice'
import { broadcastPath, donationApi, type DonationFailureResult } from './donationApi'

/** 결제창에서 실패·취소하고 돌아오는 화면. 서버에 알려 후원을 취소·실패로 바꾸고 그 결과를 보여 준다. */
export default function DonationFailPage() {
  const [params] = useSearchParams()
  const code = params.get('code') ?? ''
  const message = params.get('message') ?? ''
  const orderId = params.get('orderId') ?? ''
  const [result, setResult] = useState<DonationFailureResult>()
  const [error, setError] = useState<unknown>()

  useEffect(() => {
    let cancelled = false
    donationApi.reportFailure(orderId, code, message).then(
      (reported) => !cancelled && setResult(reported),
      (e: unknown) => !cancelled && setError(e),
    )
    return () => {
      cancelled = true
    }
  }, [orderId, code, message])

  if (error) {
    return (
      <section>
        <h1>후원 결과를 반영하지 못했습니다</h1>
        <ActionErrorNotice error={error} />
        <Link to="/">목록으로</Link>
      </section>
    )
  }

  if (!result) return <p role="status">결과를 확인하는 중…</p>

  return (
    <section>
      {result.status === 'CANCELED' ? (
        <h1>후원을 취소했습니다</h1>
      ) : (
        <>
          <h1>후원에 실패했습니다</h1>
          <p className="notice">{message}</p>
        </>
      )}
      <Link to={broadcastPath(result.broadcast)}>방송으로 돌아가기</Link>
    </section>
  )
}
