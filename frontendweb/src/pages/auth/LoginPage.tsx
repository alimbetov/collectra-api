import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../shared/api/http-client';
import { useAuth } from '../../features/auth/model/auth-context';

interface LoginLocationState {
  from?: string;
}

export function LoginPage() {
  const { login, status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [tenantSlug, setTenantSlug] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'authenticated') {
    return <Navigate to="/" replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setSubmitting(true);

    try {
      await login({ tenantSlug: tenantSlug.trim(), email: email.trim(), password });
      const state = location.state as LoginLocationState | null;
      navigate(state?.from || '/', { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(error.problem?.detail ?? error.problem?.title ?? 'Unable to sign in');
      } else {
        setErrorMessage('Unable to sign in');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <div className="brand">Collectra</div>
        <p className="eyebrow">Tenant workspace</p>
        <h1 id="login-title">Sign in</h1>
        <p className="login-help">Use your workspace slug and account credentials.</p>

        <form className="login-form" onSubmit={handleSubmit}>
          <label>
            Workspace slug
            <input
              name="tenantSlug"
              autoComplete="organization"
              value={tenantSlug}
              onChange={(event) => setTenantSlug(event.target.value)}
              required
              pattern="[a-z0-9-]{3,80}"
            />
          </label>

          <label>
            Email
            <input
              type="email"
              name="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>

          <label>
            Password
            <input
              type="password"
              name="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>

          {errorMessage ? (
            <div className="form-error" role="alert">
              {errorMessage}
            </div>
          ) : null}

          <button type="submit" disabled={submitting || status === 'loading'}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </section>
    </main>
  );
}
