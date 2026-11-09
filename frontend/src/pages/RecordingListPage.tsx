import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, type RecordingSummary } from '../api'

export default function RecordingListPage() {
  const [recordings, setRecordings] = useState<RecordingSummary[]>([])

  useEffect(() => {
    api.listRecordings().then(setRecordings)
  }, [])

  return (
    <section>
      <h1>녹화방송</h1>
      <ul>
        {recordings.map((recording) => (
          <li key={recording.id}>
            <Link to={`/recordings/${recording.id}`}>{recording.title}</Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
