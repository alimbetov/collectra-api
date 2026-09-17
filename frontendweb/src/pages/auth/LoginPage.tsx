import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../shared/api/http-client';
import { useAuth } from '../../features/auth/model/auth-context';
import { useI18n } from '../../shared/i18n/i18n-context';
import { toProblemViewModel } from '../../shared/errors/problem-detail';

interface LoginLocationState {
  from?: string;
}

function safePostLoginPath(state: LoginLocationState | null): string {
  const candidate = state?.from;
  return candidate && candidate.startsWith('/') && !candidate.startsWith('//') ? candidate : '/';
}

export function LoginPage() {
  const { login, status } = useAuth();
  const { t } = useI18n();
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
      await login({ tenantSlug: tenantSlug.trim().toLowerCase(), email: email.trim(), password });
      const state = location.state as LoginLocationState | null;
      navigate(safePostLoginPath(state), { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        const problem = toProblemViewModel(error);
        setErrorMessage(problem.detail ?? (problem.status !== null && problem.status >= 500 ? t('error.server') : t('auth.failed')));
      } else {
        setErrorMessage(t('auth.failed'));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <div className="brand">Collectra</div>
        <p className="eyebrow">{t('auth.workspace')}</p>
        <h1 id="login-title">{t('auth.signIn')}</h1>
        <p className="login-help">{t('auth.help')}</p>

        <form className="login-form" onSubmit={handleSubmit}>
          <label>
            {t('auth.tenantSlug')}
            <input
              name="tenantSlug"
              autoComplete="organization"
              value={tenantSlug}
              onChange={(event) => setTenantSlug(event.target.value.toLowerCase())}
              required
              pattern="[a-z0-9-]{3,80}"
            />
          </label>

          <label>
            {t('auth.email')}
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
            {t('auth.password')}
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
            {submitting ? t('auth.signingIn') : t('auth.signIn')}
          </button>
        </form>
      </section>
    </main>
  );
}
