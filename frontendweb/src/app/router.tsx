import { createBrowserRouter } from 'react-router-dom';
import { App } from './App';
import { RequireAuth } from '../features/auth/ui/RequireAuth';
import { RequirePermission } from '../features/auth/ui/RequirePermission';
import { LoginPage } from '../pages/auth/LoginPage';
import { ForbiddenPage } from '../pages/system/ForbiddenPage';
import { NotFoundPage } from '../pages/system/NotFoundPage';

function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="placeholder-page">
      <p className="eyebrow">Frontend work slice</p>
      <h1>{title}</h1>
      <p>
        Route boundary is ready. Business queries and mutations are intentionally deferred to their
        frontend slices.
      </p>
    </div>
  );
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <App />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <PlaceholderPage title="Dashboard" /> },
      { path: 'customers', element: <PlaceholderPage title="Customers" /> },
      { path: 'receivables', element: <PlaceholderPage title="Receivables" /> },
      { path: 'collections', element: <PlaceholderPage title="Collections" /> },
      {
        path: 'campaigns',
        element: (
          <RequirePermission permission="CAMPAIGN_READ">
            <PlaceholderPage title="Campaigns" />
          </RequirePermission>
        ),
      },
      {
        path: 'templates',
        element: (
          <RequirePermission permission="TEMPLATE_READ">
            <PlaceholderPage title="Templates" />
          </RequirePermission>
        ),
      },
      {
        path: 'imports',
        element: (
          <RequirePermission permission="DOCUMENT_READ">
            <PlaceholderPage title="Imports" />
          </RequirePermission>
        ),
      },
      {
        path: 'files',
        element: (
          <RequirePermission permission="FILE_READ">
            <PlaceholderPage title="Files" />
          </RequirePermission>
        ),
      },
      { path: 'forbidden', element: <ForbiddenPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
