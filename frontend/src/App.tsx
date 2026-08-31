import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './hooks/AuthProvider';
import { ProtectedRoute } from './routes/ProtectedRoute';
import { RoleRoute } from './routes/RoleRoute';
import { DashboardRedirect } from './routes/DashboardRedirect';
import { AppLayout } from './components/layout/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { AdminOrganizationsPage } from './pages/AdminOrganizationsPage';
import { WorkspacePage } from './pages/WorkspacePage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/" element={<DashboardRedirect />} />
              <Route path="/app" element={<WorkspacePage />} />
              <Route element={<RoleRoute role="ADMIN" />}>
                <Route path="/admin/users" element={<AdminUsersPage />} />
                <Route path="/admin/organizations" element={<AdminOrganizationsPage />} />
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
