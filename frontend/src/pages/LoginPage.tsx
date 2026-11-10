import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api, ApiError } from '../api'
import { safeNext, useAuth } from '../auth/auth'

export default function LoginPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const { signedIn } = useAuth()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(undefined)
    try {
      signedIn(await api.login(loginId, password))
      navigate(next, { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '로그인하지 못했습니다. 잠시 뒤 다시 시도해 주세요.')
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-form">
      <h1>로그인</h1>
      {error && (
        <p role="alert" className="notice">
          {error}
        </p>
      )}
      <form onSubmit={handleSubmit} noValidate>
        <label>
          아이디
          <input value={loginId} onChange={(e) => setLoginId(e.target.value)} autoComplete="username" />
        </label>
        <label>
          비밀번호
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </label>
        <button type="submit" disabled={submitting}>
          로그인
        </button>
      </form>
      <p>
        아직 회원이 아니면 <Link to={`/signup?next=${encodeURIComponent(next)}`}>회원가입</Link>
      </p>
    </section>
  )
}
