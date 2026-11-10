import { Link, Route, Routes, useLocation } from 'react-router'
import { useAuth } from './auth/auth'
import { AuthProvider } from './auth/AuthContext'
import RequireLogin from './auth/RequireLogin'
import LiveBroadcastNewPage from './pages/LiveBroadcastNewPage'
import LiveBroadcastSection from './pages/LiveBroadcastSection'
import LiveBroadcastWatchPage from './pages/LiveBroadcastWatchPage'
import LoginPage from './pages/LoginPage'
import RecordingListPage from './pages/RecordingListPage'
import RecordingDetailPage from './pages/RecordingDetailPage'
import RecordingNewPage from './pages/RecordingNewPage'
import SignUpPage from './pages/SignUpPage'

export default function App() {
  return (
    <AuthProvider>
      <header className="site-header">
        <Link className="brand" to="/">
          chzzz
        </Link>
        <nav>
          <Link to="/">목록</Link>
          <Link to="/live/new">방송 시작</Link>
          <Link to="/recordings/new">녹화방송 등록</Link>
        </nav>
        <AccountMenu />
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
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignUpPage />} />
          <Route path="/live/new" element={<LiveBroadcastNewPage />} />
          <Route path="/live/:id" element={<LiveBroadcastWatchPage />} />
          <Route
            path="/recordings/new"
            element={
              <RequireLogin>
                <RecordingNewPage />
              </RequireLogin>
            }
          />
          <Route path="/recordings/:id" element={<RecordingDetailPage />} />
        </Routes>
      </main>
    </AuthProvider>
  )
}

/** 헤더 오른쪽: 로그인하지 않았으면 로그인·회원가입, 로그인했으면 닉네임과 로그아웃. */
function AccountMenu() {
  const { auth, logout } = useAuth()
  const location = useLocation()
  if (auth.status === 'loading') return <div className="account" />
  if (auth.status === 'member') {
    return (
      <div className="account">
        <span>{auth.member.nickname}님</span>
        <button type="button" className="link-button" onClick={logout}>
          로그아웃
        </button>
      </div>
    )
  }
  const onAuthPage = location.pathname === '/login' || location.pathname === '/signup'
  const next = onAuthPage ? '' : `?next=${encodeURIComponent(location.pathname + location.search)}`
  return (
    <div className="account">
      <Link to={`/login${next}`}>로그인</Link>
      <Link to={`/signup${next}`}>회원가입</Link>
    </div>
  )
}
