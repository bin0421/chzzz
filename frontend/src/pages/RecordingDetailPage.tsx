import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { api, type RecordingDetail } from '../api'

export default function RecordingDetailPage() {
  const { id } = useParams()
  const [recording, setRecording] = useState<RecordingDetail | null>(null)

  useEffect(() => {
    if (id) api.getRecording(id).then(setRecording)
  }, [id])

  if (!recording) return <p>불러오는 중…</p>

  return (
    <article>
      <h1>{recording.title}</h1>
      <video data-testid="recording-player" src={recording.videoUrl} controls />
    </article>
  )
}
