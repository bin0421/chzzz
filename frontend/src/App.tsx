import { Link, Route, Routes } from 'react-router'
import LiveBroadcastNewPage from './pages/LiveBroadcastNewPage'
import LiveBroadcastSection from './pages/LiveBroadcastSection'
import LiveBroadcastWatchPage from './pages/LiveBroadcastWatchPage'
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
          <Link to="/live/new">방송 시작</Link>
          <Link to="/recordings/new">녹화방송 등록</Link>
        </nav>
      </header>
      <main>
        <Routes>
          <Route
            path="/"
            element={
              <>
                <LiveBroadcastSection />
                <RecordingListPage />
              </>
            }
          />
          <Route path="/live/new" element={<LiveBroadcastNewPage />} />
          <Route path="/live/:id" element={<LiveBroadcastWatchPage />} />
          <Route path="/recordings/new" element={<RecordingNewPage />} />
          <Route path="/recordings/:id" element={<RecordingDetailPage />} />
        </Routes>
      </main>
    </>
  )
}
