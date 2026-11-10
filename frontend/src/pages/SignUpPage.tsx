import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api, ApiError } from '../api'
import { safeNext, useAuth } from '../auth/auth'

type FieldErrors = Partial<Record<'loginId' | 'password' | 'nickname', string>>

export default function SignUpPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const { signedIn } = useAuth()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setErrors({})
    setFormError(undefined)
    try {
      signedIn(await api.signUp(loginId, password, nickname))
      navigate(next, { replace: true })
    } catch (e) {
      if (e instanceof ApiError && e.errors.length > 0) {
        setErrors(Object.fromEntries(e.errors.map((err) => [err.field, err.message])))
      } else {
        setFormError(e instanceof ApiError ? e.message : '가입하지 못했습니다. 잠시 뒤 다시 시도해 주세요.')
      }
      setSubmitting(false)
    }
  }

  return (
    <section className="auth-form">
      <h1>회원가입</h1>
      {formError && (
        <p role="alert" className="notice">
          {formError}
        </p>
      )}
      <form onSubmit={handleSubmit} noValidate>
        <Field
          name="loginId"
          label="아이디"
          hint="영문 소문자, 숫자, _로 4~20자"
          value={loginId}
          onChange={setLoginId}
          error={errors.loginId}
          autoComplete="username"
        />
        <Field
          name="password"
          label="비밀번호"
          hint="8~64자"
          type="password"
          value={password}
          onChange={setPassword}
          error={errors.password}
          autoComplete="new-password"
        />
        <Field
          name="nickname"
          label="닉네임"
          hint="화면에 보이는 이름, 1~20자"
          value={nickname}
          onChange={setNickname}
          error={errors.nickname}
          autoComplete="nickname"
        />
        <button type="submit" disabled={submitting}>
          가입하기
        </button>
      </form>
      <p>
        이미 회원이면 <Link to={`/login?next=${encodeURIComponent(next)}`}>로그인</Link>
      </p>
    </section>
  )
}

function Field(props: {
  name: string
  label: string
  hint: string
  value: string
  onChange: (value: string) => void
  error?: string
  type?: string
  autoComplete?: string
}) {
  const errorId = `${props.name}-error`
  return (
    <>
      <label>
        {props.label}
        <input
          type={props.type ?? 'text'}
          value={props.value}
          onChange={(e) => props.onChange(e.target.value)}
          placeholder={props.hint}
          autoComplete={props.autoComplete}
          aria-invalid={props.error ? true : undefined}
          aria-describedby={props.error ? errorId : undefined}
        />
      </label>
      {props.error && (
        <p id={errorId} className="field-error">
          {props.error}
        </p>
      )}
    </>
  )
}
