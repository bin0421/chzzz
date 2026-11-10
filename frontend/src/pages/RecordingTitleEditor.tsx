import { useState, type FormEvent } from 'react'
import { api, ApiError, type RecordingDetail } from '../api'

type Props = {
  recording: RecordingDetail
  onSaved: (recording: RecordingDetail) => void
  onCancel: () => void
  /** 입력란 오류가 아닌 거절(401·403 등)은 화면이 안내한다. */
  onError: (error: unknown) => void
}

/** 소유자의 녹화방송 제목 인라인 수정. 제목 규칙 위반은 입력란 옆에 보여 준다. */
export default function RecordingTitleEditor({ recording, onSaved, onCancel, onError }: Props) {
  const [title, setTitle] = useState(recording.title)
  const [titleError, setTitleError] = useState<string>()
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setTitleError(undefined)
    try {
      onSaved(await api.updateRecordingTitle(recording.id, title))
    } catch (error) {
      const fieldError = error instanceof ApiError ? error.errors.find((e) => e.field === 'title') : undefined
      if (fieldError) setTitleError(fieldError.message)
      else onError(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="title-editor" onSubmit={handleSubmit}>
      <label>
        제목
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          aria-invalid={titleError !== undefined}
          // 수정을 누르면 바로 고칠 수 있게 입력란으로 포커스를 옮긴다.
          autoFocus
        />
      </label>
      {titleError && <p className="field-error">{titleError}</p>}
      <div className="owner-actions">
        <button type="submit" disabled={saving}>
          저장
        </button>
        <button type="button" onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  )
}
