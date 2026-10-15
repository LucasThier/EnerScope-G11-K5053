import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './hooks/AuthProvider';
import { ProtectedRoute } from './routes/ProtectedRoute';
import { RoleRoute } from './routes/RoleRoute';
import { DashboardRedirect } from './routes/DashboardRedirect';
import { AppLayout } from './components/layout/AppLayout';
import { PaddedMain } from './components/layout/PaddedMain';
import { LoginPage } from './pages/LoginPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { AdminOrganizationsPage } from './pages/AdminOrganizationsPage';
import { WorkspacePage } from './pages/WorkspacePage';
import { ProjectsPage } from './pages/ProjectsPage';
import { EditorPage } from './pages/EditorPage';
import { ProfilePage } from './pages/ProfilePage';
import { OrganizationTeamPage } from './pages/OrganizationTeamPage';
import { ProjectTeamPage } from './pages/ProjectTeamPage';
import { OrganizationOwnerRoute } from './routes/OrganizationOwnerRoute';
import { ActiveProjectRedirect } from './routes/ActiveProjectRedirect';
import { ScenariosPage } from './pages/ScenariosPage';
import { SimulationResultPage } from './pages/SimulationResultPage';
import { ComparePage } from './pages/ComparePage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              {/* Full-bleed pages */}
              <Route path="/projects/:projectId/editor" element={<EditorPage />} />
              <Route path="/editor" element={<ActiveProjectRedirect section="editor" />} />
              {/* Regular padded pages */}
              <Route element={<PaddedMain />}>
                <Route path="/" element={<DashboardRedirect />} />
                <Route path="/app" element={<WorkspacePage />} />
                <Route path="/projects" element={<ProjectsPage />} />
                <Route path="/projects/:projectId/team" element={<ProjectTeamPage />} />
                <Route path="/projects/:projectId/scenarios" element={<ScenariosPage />} />
                <Route
                  path="/projects/:projectId/scenarios/:versionId/runs/:simulationId"
                  element={<SimulationResultPage />}
                />
                <Route path="/projects/:projectId/compare" element={<ComparePage />} />
                <Route path="/scenarios" element={<ActiveProjectRedirect section="scenarios" />} />
                <Route path="/compare" element={<ActiveProjectRedirect section="compare" />} />
                <Route path="/profile" element={<ProfilePage />} />
                <Route element={<OrganizationOwnerRoute />}>
                  <Route path="/organizations/:organizationId" element={<OrganizationTeamPage />} />
                </Route>
                <Route element={<RoleRoute role="ADMIN" />}>
                  <Route path="/admin/users" element={<AdminUsersPage />} />
                  <Route path="/admin/organizations" element={<AdminOrganizationsPage />} />
                </Route>
              </Route>
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
