import { Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { LogOut, Sparkles } from 'lucide-react';
import { clearAuth, getAccessToken, getStoredUser } from './lib/storage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import EditorPage from './pages/EditorPage';
import AgentPage from './pages/AgentPage';

function ProtectedRoute({ children }: { children: JSX.Element }) {
  if (!getAccessToken()) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

function AppShell({ children }: { children: JSX.Element }) {
  const navigate = useNavigate();
  const user = getStoredUser();

  function logout() {
    clearAuth();
    navigate('/login', { replace: true });
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/app/presentations" className="brand">
          <Sparkles size={22} />
          <span>PPT Agent</span>
        </Link>
        <div className="topbar-actions">
          <span className="muted">{user?.username || user?.email}</span>
          <button className="icon-button" onClick={logout} title="退出登录" aria-label="退出登录">
            <LogOut size={18} />
          </button>
        </div>
      </header>
      {children}
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/app/presentations" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/app/presentations"
        element={
          <ProtectedRoute>
            <AppShell>
              <DashboardPage />
            </AppShell>
          </ProtectedRoute>
        }
      />
      <Route
        path="/app/presentations/:id/editor"
        element={
          <ProtectedRoute>
            <AppShell>
              <EditorPage />
            </AppShell>
          </ProtectedRoute>
        }
      />
      <Route
        path="/app/presentations/:id/agent"
        element={
          <ProtectedRoute>
            <AppShell>
              <AgentPage />
            </AppShell>
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}
