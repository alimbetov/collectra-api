import { createBrowserRouter } from 'react-router-dom';
import { App } from './App';
import { RequireAuth } from '../features/auth/ui/RequireAuth';
import { LoginPage } from '../pages/auth/LoginPage';

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
      { path: 'campaigns', element: <PlaceholderPage title="Campaigns" /> },
      { path: 'templates', element: <PlaceholderPage title="Templates" /> },
      { path: 'imports', element: <PlaceholderPage title="Imports" /> },
      { path: 'files', element: <PlaceholderPage title="Files" /> },
    ],
  },
]);
