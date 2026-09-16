import { Link } from 'react-router-dom';

export function ForbiddenPage() {
  return (
    <section className="system-page" aria-labelledby="forbidden-title">
      <p className="eyebrow">403</p>
      <h1 id="forbidden-title">Access denied</h1>
      <p>Your account does not have permission to open this workspace section.</p>
      <Link to="/">Return to dashboard</Link>
    </section>
  );
}
