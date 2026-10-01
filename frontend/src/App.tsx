import { createBrowserRouter, createRoutesFromElements, RouterProvider, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './hooks/AuthProvider';
import { ProtectedRoute } from './routes/ProtectedRoute';
import { RoleRoute } from './routes/RoleRoute';
import { DashboardRedirect } from './routes/DashboardRedirect';
import { AppLayout } from './components/layout/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { AdminOrganizationsPage } from './pages/AdminOrganizationsPage';
import { WorkspacePage } from './pages/WorkspacePage';
import { ProjectsPage } from './pages/ProjectsPage';
import { ScenariosPage } from './pages/ScenariosPage';
import { EconomicsPage } from './pages/EconomicsPage';

const router = createBrowserRouter(createRoutesFromElements(<>
  <Route path="/login" element={<LoginPage />} />
  <Route element={<ProtectedRoute />}>
    <Route element={<AppLayout />}>
      <Route path="/" element={<DashboardRedirect />} />
      <Route path="/app" element={<WorkspacePage />} />
      <Route path="/projects" element={<ProjectsPage />} />
      <Route path="/projects/:projectId/versions" element={<ScenariosPage />} />
      <Route path="/projects/:projectId/versions/:versionId/economics/:tab?/:evaluationId?" element={<EconomicsPage />} />
      <Route element={<RoleRoute role="ADMIN" />}>
        <Route path="/admin/users" element={<AdminUsersPage />} />
        <Route path="/admin/organizations" element={<AdminOrganizationsPage />} />
      </Route>
    </Route>
  </Route>
  <Route path="*" element={<Navigate to="/" replace />} />
</>));

export default function App() {
  return <AuthProvider><RouterProvider router={router} /></AuthProvider>;
}
