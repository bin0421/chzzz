import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api, type VideoSourceType } from '../api'

export default function RecordingNewPage() {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [sourceType, setSourceType] = useState<VideoSourceType>('EXTERNAL_URL')
  const [url, setUrl] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
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
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section>
      <h1>녹화방송 등록</h1>
      <form onSubmit={handleSubmit}>
        <label>
          제목
          <input value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
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
            <input type="url" value={url} onChange={(e) => setUrl(e.target.value)} />
          </label>
        )}
        <button type="submit" disabled={submitting}>
          {submitting ? '등록 중…' : '등록'}
        </button>
      </form>
    </section>
  )
}
