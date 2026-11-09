import { Link, Route, Routes } from 'react-router'
import RecordingListPage from './pages/RecordingListPage'
import RecordingDetailPage from './pages/RecordingDetailPage'
import RecordingNewPage from './pages/RecordingNewPage'

export default function App() {
  return (
    <>
      <header className="site-header">
        <Link className="brand" to="/">
          chzzz
        </Link>
        <nav>
          <Link to="/">목록</Link>
          <Link to="/recordings/new">녹화방송 등록</Link>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<RecordingListPage />} />
          <Route path="/recordings/new" element={<RecordingNewPage />} />
          <Route path="/recordings/:id" element={<RecordingDetailPage />} />
        </Routes>
      </main>
    </>
  )
}
