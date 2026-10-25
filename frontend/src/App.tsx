import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './hooks/AuthProvider';
import { ThemeProvider } from './hooks/ThemeProvider';
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
import { ScenarioConfigurationPage } from './pages/ScenarioConfigurationPage';
import { ScenariosPage } from './pages/ScenariosPage';
import { EconomicsPage } from './pages/EconomicsPage';
import { EditorPage } from './pages/EditorPage';
import { ProfilePage } from './pages/ProfilePage';
import { OrganizationTeamPage } from './pages/OrganizationTeamPage';
import { ProjectTeamPage } from './pages/ProjectTeamPage';
import { VersionsPage } from './pages/VersionsPage';
import { VersionsIndexPage } from './pages/VersionsIndexPage';
import { OrganizationOwnerRoute } from './routes/OrganizationOwnerRoute';

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<AppLayout />}>
                {/* Full-bleed pages */}
                <Route path="/editor" element={<EditorPage />} />
                {/* Regular padded pages */}
                <Route element={<PaddedMain />}>
                  <Route path="/" element={<DashboardRedirect />} />
                  <Route path="/app" element={<WorkspacePage />} />
                  <Route path="/projects" element={<ProjectsPage />} />
                  <Route path="/projects/:projectId/team" element={<ProjectTeamPage />} />
                  <Route path="/projects/:projectId/scenario-configurations" element={<ScenarioPage configuration />} />
                  <Route path="/projects/:projectId/versions" element={<VersionsPage />} />
                  <Route path="/projects/:projectId/versions/:versionId/economics" element={<ScenarioConfigurationPage />} />
                  <Route path="/projects/:projectId/versions/:versionId/economics/configuration" element={<ScenarioCOnfigurationPage />} />
                  <Route path="/projects/:projectId/versions/:versionId/economics/:tab?/:evaluationId?" element={<EconomicsPage />} />
                  <Route path="/versions" element={<VersionsIndexPage />} />
                  <Route path="/profile" element={<ProfilePage />} />
                  <Route
                    path="/settings"
                    element={<Navigate to="/profile#configuracion" replace />}
                  />
                  <Route element={<OrganizationOwnerRoute />}>
                    <Route
                      path="/organizations/:organizationId"
                      element={<OrganizationTeamPage />}
                    />
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
    </ThemeProvider>
  );
}
