import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api, ApiError, type VideoSourceType } from '../api'

type FieldErrors = Partial<Record<string, string>>

const TITLE_MAX_LENGTH = 100

/** 입력란 옆에 표시하는 필드. 그 밖의 필드 오류(예: source)는 폼 위 알림에 모은다. */
const INPUT_FIELDS = ['title', 'url']

function validate(title: string, sourceType: VideoSourceType, url: string): FieldErrors {
  const errors: FieldErrors = {}
  const trimmedTitle = title.trim()
  if (trimmedTitle.length === 0) errors.title = '제목을 입력해 주세요.'
  else if (trimmedTitle.length > TITLE_MAX_LENGTH) errors.title = `제목은 ${TITLE_MAX_LENGTH}자 이하로 입력해 주세요.`
  if (sourceType === 'EXTERNAL_URL') {
    const urlError = validateUrl(url.trim())
    if (urlError) errors.url = urlError
  }
  return errors
}

// 브라우저의 URL 파서는 'https:///a.mp4'를 호스트 'a.mp4'로 고쳐 해석하므로, 서버와 같은 판단을 하도록 원문을 검사한다.
function validateUrl(url: string): string | undefined {
  if (url.length === 0) return '영상 URL을 입력해 주세요.'
  const scheme = /^([a-z][a-z0-9+.-]*):/i.exec(url)?.[1].toLowerCase()
  if (scheme !== 'http' && scheme !== 'https') return 'http 또는 https로 시작하는 영상 주소를 입력해 주세요.'
  if (!/^https?:\/\/[^/?#\s]+/i.test(url)) return '영상 주소에 호스트가 없습니다. 주소를 확인해 주세요.'
  return undefined
}

export default function RecordingNewPage() {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [sourceType, setSourceType] = useState<VideoSourceType>('EXTERNAL_URL')
  const [url, setUrl] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const clientErrors = validate(title, sourceType, url)
    setErrors(clientErrors)
    setFormError(undefined)
    if (Object.keys(clientErrors).length > 0) return

    const form = new FormData()
    form.append('title', title)
    if (sourceType === 'UPLOADED_FILE') {
      if (file) form.append('file', file)
    } else {
      form.append('url', url)
    }
    setSubmitting(true)
    try {
      const recording = await api.registerRecording(form)
      navigate(`/recordings/${recording.id}`)
    } catch (error) {
      if (!(error instanceof ApiError)) throw error
      setErrors(Object.fromEntries(error.errors.map((e) => [e.field, e.message])))
      setFormError(error.message)
    } finally {
      setSubmitting(false)
    }
  }

  const otherMessages = Object.entries(errors)
    .filter(([field]) => !INPUT_FIELDS.includes(field))
    .map(([, message]) => message)

  return (
    <section>
      <h1>녹화방송 등록</h1>
      {(formError || otherMessages.length > 0) && (
        <div role="alert">
          {formError && <p>{formError}</p>}
          {otherMessages.length > 0 && (
            <ul>
              {otherMessages.map((message) => (
                <li key={message}>{message}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      <form onSubmit={handleSubmit} noValidate>
        <label>
          제목
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            aria-invalid={errors.title ? true : undefined}
            aria-describedby={errors.title ? 'title-error' : undefined}
          />
        </label>
        {errors.title && <p id="title-error">{errors.title}</p>}
        <fieldset>
          <legend>영상 출처</legend>
          <label>
            <input
              type="radio"
              name="sourceType"
              checked={sourceType === 'UPLOADED_FILE'}
              onChange={() => setSourceType('UPLOADED_FILE')}
            />
            업로드 파일
          </label>
          <label>
            <input
              type="radio"
              name="sourceType"
              checked={sourceType === 'EXTERNAL_URL'}
              onChange={() => setSourceType('EXTERNAL_URL')}
            />
            외부 URL
          </label>
        </fieldset>
        {/* key: 출처 종류가 바뀌면 같은 <input>을 재사용하지 않고 새로 만든다(제어/비제어 전환 경고 방지). */}
        {sourceType === 'UPLOADED_FILE' ? (
          <label key="file">
            영상 파일
            <input
              type="file"
              accept="video/mp4,video/webm,.mp4,.webm"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
        ) : (
          <label key="url">
            영상 URL
            <input
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              aria-invalid={errors.url ? true : undefined}
              aria-describedby={errors.url ? 'url-error' : undefined}
            />
          </label>
        )}
        {sourceType === 'EXTERNAL_URL' && errors.url && <p id="url-error">{errors.url}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? '등록 중…' : '등록'}
        </button>
      </form>
    </section>
  )
}
