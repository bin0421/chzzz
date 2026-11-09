import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api'

export default function RecordingNewPage() {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [url, setUrl] = useState('')

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const form = new FormData()
    form.append('title', title)
    form.append('url', url)
    const recording = await api.registerRecording(form)
    navigate(`/recordings/${recording.id}`)
  }

  return (
    <section>
      <h1>녹화방송 등록</h1>
      <form onSubmit={handleSubmit}>
        <label>
          제목
          <input value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <label>
          영상 URL
          <input type="url" value={url} onChange={(e) => setUrl(e.target.value)} />
        </label>
        <button type="submit">등록</button>
      </form>
    </section>
  )
}
