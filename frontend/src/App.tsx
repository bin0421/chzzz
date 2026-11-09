import { Route, Routes } from 'react-router'
import RecordingListPage from './pages/RecordingListPage'
import RecordingDetailPage from './pages/RecordingDetailPage'
import RecordingNewPage from './pages/RecordingNewPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RecordingListPage />} />
      <Route path="/recordings/new" element={<RecordingNewPage />} />
      <Route path="/recordings/:id" element={<RecordingDetailPage />} />
    </Routes>
  )
}
